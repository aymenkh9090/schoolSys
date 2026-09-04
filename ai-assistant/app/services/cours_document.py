"""
Conversation autour d'un cours que l'enseignant a déposé.

L'enseignant attache le chapitre qu'il va traiter, puis demande ce dont il a
besoin : « explique la partie sur la réciproque », « propose quatre exercices »,
« fais-moi un QCM de cinq questions avec le corrigé ». Le service ne décide pas
à sa place de ce qu'il faut produire — il fournit le texte du cours au modèle et
lui transmet la demande.

**Le document est gardé en mémoire, jamais sur disque.** Le déposer à chaque
question ferait remonter plusieurs mégaoctets par tour sur un Wi-Fi
d'établissement et recommencerait l'extraction à chaque fois. Il est donc lu une
fois, gardé le temps d'une préparation, et **rattaché au compte qui l'a
déposé** : un identifiant de document ne suffit pas à le relire, il faut être
celui qui l'a envoyé. Sans ce couplage, un identifiant deviné ouvrirait le cours
d'un collègue.

Trois garde-fous sur l'entrée, chacun contre une défaillance réelle :

**1. Un PDF scanné est refusé, pas deviné.** Beaucoup de manuels tunisiens
circulent en photocopie scannée : aucune couche de texte, `extract_text()` rend
deux lignes de bruit. Sans contrôle, le modèle recevrait ce bruit et inventerait
un cours plausible sur un chapitre qu'il n'a jamais lu — la pire sortie
possible, parce qu'elle est crédible. On mesure le texte extrait par page et on
refuse en nommant la cause. L'OCR n'est pas dans le périmètre du PFE.

**2. L'entrée est bornée, et la troncature est dite.** Un chapitre de 40 pages
dépasse la fenêtre de contexte utile du modèle. On coupe à `MAX_CARACTERES`, sur
une frontière de paragraphe, et le dépôt annonce `tronque` : l'enseignant sait
que la fin de son document n'entrera pas dans les réponses. Une troncature
silencieuse serait un mensonge par omission.

**3. Le modèle répond à partir du cours, ou dit qu'il n'y est pas.** C'est la
seule contrainte imposée à la génération. Ce que l'enseignant demande — un
résumé, un exercice, un corrigé de QCM — ne regarde pas le service ; d'où le
document vient la réponse, si.
"""

import io
import logging
import time
import uuid
from dataclasses import dataclass

from app.clients.ollama import OllamaClient
from app.models import ChatResponse, DocumentDepose

logger = logging.getLogger(__name__)

# Environ 2 500 jetons d'entrée : de quoi couvrir un chapitre, sans faire
# grimper la durée de génération au-delà de ce qu'on attend devant un écran.
MAX_CARACTERES = 8_000

# En dessous, le PDF n'a pas de couche de texte exploitable. Le seuil est par
# page : un cours de 10 pages qui ne rend que 200 caractères est un scan.
MIN_CARACTERES_PAR_PAGE = 120

# Un QCM corrigé de dix questions dépasse largement les 300 jetons du défaut.
NUM_PREDICT = 1_100

# À 27 jetons/s mesurés sur le 7B, 1 100 jetons demandent ~40 s ; on garde de la
# marge pour la lecture du prompt et un premier chargement du modèle.
TIMEOUT_SECONDES = 240.0

# Les échanges précédents transmis avec la question. Deux allers-retours
# suffisent à « et maintenant explique-moi la deuxième partie » ; au-delà, on
# rallonge le prompt sans rien gagner.
HISTORIQUE_MAX = 4

SYSTEME = """Tu es l'assistant pédagogique d'un enseignant du collège tunisien.

Tu reçois le TEXTE D'UN COURS, puis une demande de l'enseignant. Tu y réponds
en français, directement, sans préambule ni formule de politesse.

RÈGLES :
- Appuie-toi UNIQUEMENT sur le texte du cours fourni. N'ajoute aucune notion
  qui n'y figure pas.
- Si la demande porte sur quelque chose d'absent du cours, dis-le clairement
  au lieu de l'inventer.
- Fais exactement ce qui est demandé : un résumé si on demande un résumé, des
  exercices si on demande des exercices, un corrigé si on demande un corrigé.
- Structure ta réponse avec des titres courts et des listes numérotées quand
  cela aide à la lire. Pas de tableau.
- Si on te demande un corrigé, place-le à la FIN, sous un titre « Corrigé ».
  Ne signale JAMAIS la bonne réponse dans l'énoncé lui-même, ni en gras, ni
  autrement : un QCM dont les réponses sont visibles ne peut pas être projeté.
- Tout doit être directement utilisable en classe."""


class PreparationImpossible(Exception):
    """Le document ou la demande ne permettent pas d'aboutir — la cause est dans le message."""


def extraire_texte(contenu: bytes) -> tuple[str, int]:
    """
    Texte du PDF et nombre de pages.

    Lève `PreparationImpossible` avec une cause nommée plutôt que de rendre une
    chaîne vide : en aval, « pas de texte » et « texte inutilisable » appellent
    des messages différents pour l'enseignant.
    """
    try:
        from pypdf import PdfReader

        lecteur = PdfReader(io.BytesIO(contenu))
    except Exception as exc:  # noqa: BLE001 — tout échec de lecture = fichier inexploitable
        raise PreparationImpossible(
            "Ce fichier n'a pas pu être ouvert comme PDF."
        ) from exc

    if lecteur.is_encrypted:
        raise PreparationImpossible(
            "Ce PDF est protégé par mot de passe : son texte ne peut pas être lu."
        )

    pages = lecteur.pages
    if not pages:
        raise PreparationImpossible("Ce PDF ne contient aucune page.")

    morceaux = []
    for page in pages:
        try:
            morceaux.append(page.extract_text() or "")
        except Exception:  # noqa: BLE001 — une page illisible n'annule pas le document
            morceaux.append("")

    texte = "\n".join(morceaux).strip()

    if len(texte) < MIN_CARACTERES_PAR_PAGE * len(pages):
        raise PreparationImpossible(
            f"Ce PDF ne contient presque pas de texte ({len(texte)} caractères "
            f"pour {len(pages)} page(s)) : c'est un document scanné. "
            "La reconnaissance de caractères n'est pas disponible — "
            "déposez une version dont le texte est sélectionnable."
        )

    return texte, len(pages)


def tronquer(texte: str) -> tuple[str, bool]:
    """Coupe sur une frontière de paragraphe pour ne pas trancher une phrase en deux."""
    if len(texte) <= MAX_CARACTERES:
        return texte, False

    coupe = texte[:MAX_CARACTERES]
    frontiere = coupe.rfind("\n\n")
    if frontiere < MAX_CARACTERES // 2:
        frontiere = coupe.rfind("\n")
    if frontiere < MAX_CARACTERES // 2:
        frontiere = coupe.rfind(". ")
    return (coupe[:frontiere] if frontiere > 0 else coupe), True


@dataclass
class DocumentCours:
    identifiant: str
    nom_fichier: str
    pages: int
    texte: str
    tronque: bool
    depose_a: float


class DocumentStore:
    """
    Les cours déposés, en mémoire, le temps d'une préparation.

    Bornée dans les deux sens : par l'âge (`ttl_seconds`) et par le nombre
    (`max_documents`, le plus ancien part en premier). Un service qui garde tout
    ce qu'on lui envoie finit par tomber pour une raison sans rapport avec son
    métier.
    """

    def __init__(self, ttl_seconds: float = 3600.0, max_documents: int = 20):
        self._ttl = ttl_seconds
        self._max = max_documents
        self._documents: dict[tuple[str, str], DocumentCours] = {}

    def _purger(self) -> None:
        limite = time.monotonic() - self._ttl
        for cle in [c for c, d in self._documents.items() if d.depose_a < limite]:
            del self._documents[cle]

        while len(self._documents) > self._max:
            plus_ancien = min(self._documents, key=lambda c: self._documents[c].depose_a)
            del self._documents[plus_ancien]

    def deposer(self, cle_utilisateur: str, contenu: bytes, nom_fichier: str) -> DocumentCours:
        texte, pages = extraire_texte(contenu)
        extrait, coupe = tronquer(texte)

        document = DocumentCours(
            identifiant=uuid.uuid4().hex,
            nom_fichier=nom_fichier,
            pages=pages,
            texte=extrait,
            tronque=coupe,
            depose_a=time.monotonic(),
        )
        self._documents[(cle_utilisateur, document.identifiant)] = document
        self._purger()
        return document

    def lire(self, cle_utilisateur: str, identifiant: str) -> DocumentCours:
        self._purger()
        document = self._documents.get((cle_utilisateur, identifiant))
        if document is None:
            # Même message que le document soit expiré ou qu'il appartienne à
            # quelqu'un d'autre : distinguer les deux confirmerait l'existence
            # du document d'un collègue.
            raise PreparationImpossible(
                "Ce document n'est plus disponible. Déposez-le à nouveau."
            )
        return document

    def __len__(self) -> int:
        return len(self._documents)


class CoursDocumentService:
    def __init__(self, ollama: OllamaClient, store: DocumentStore | None = None):
        self._ollama = ollama
        self._store = store or DocumentStore()

    def deposer(self, cle_utilisateur: str, contenu: bytes, nom_fichier: str) -> DocumentDepose:
        document = self._store.deposer(cle_utilisateur, contenu, nom_fichier)
        logger.info(
            "Cours déposé : %s — %d page(s), %d caractères%s",
            nom_fichier, document.pages, len(document.texte),
            " (tronqué)" if document.tronque else "",
        )
        return DocumentDepose(
            document_id=document.identifiant,
            nom_fichier=document.nom_fichier,
            pages=document.pages,
            caracteres_lus=len(document.texte),
            tronque=document.tronque,
        )

    async def repondre(
        self,
        cle_utilisateur: str,
        document_id: str,
        message: str,
        historique: list[dict] | None = None,
    ) -> ChatResponse:
        debut = time.monotonic()
        document = self._store.lire(cle_utilisateur, document_id)

        messages = [
            {"role": "system", "content": SYSTEME},
            {
                "role": "user",
                "content": f"Texte du cours « {document.nom_fichier} » :\n\n{document.texte}",
            },
            {
                "role": "assistant",
                "content": "J'ai lu le cours. Que voulez-vous que j'en fasse ?",
            },
        ]
        # Les échanges précédents arrivent APRÈS le cours : le modèle voit le
        # document d'abord, la conversation ensuite, comme elle s'est déroulée.
        messages += (historique or [])[-HISTORIQUE_MAX:]
        messages.append({"role": "user", "content": message})

        try:
            reponse = await self._ollama.chat(
                messages,
                tools=None,
                options={"temperature": 0.3, "num_predict": NUM_PREDICT},
                timeout=TIMEOUT_SECONDES,
            )
        except Exception as exc:  # noqa: BLE001 — panne du modèle, pas du document
            logger.exception("Échec de l'appel à Ollama")
            raise PreparationImpossible(f"Le modèle est indisponible ({exc}).") from exc

        texte = (reponse.get("content") or "").strip()
        if not texte:
            raise PreparationImpossible("Le modèle n'a rien répondu. Réessayez.")

        return ChatResponse(
            answer=texte,
            tools_used=["cours_depose"],
            duration_ms=int((time.monotonic() - debut) * 1000),
        )
