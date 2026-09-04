"""
Recherche sémantique sur les cahiers de séance.

Un cahier de séance est du texte libre écrit par un enseignant : « révision des
identités remarquables, exercices 12 à 18 », « les élèves bloquent sur la
factorisation ». Aucune requête SQL ne répond à « quelles classes ont pris du
retard » ou « quand ai-je traité Pythagore » : ces notions ne sont pas des
colonnes, elles sont dans les mots. D'où la recherche par similarité.

Le même index sert deux usages, et c'est délibéré :

  - l'enseignant interroge SES séances (« qu'ai-je fait avec la 8ème A ? ») ;
  - le directeur interroge CELLES DE L'ÉTABLISSEMENT (« où en est la 7ème B ? »).

Ce n'est pas le service Python qui choisit lequel : le backend construit le
corpus à partir du compte porté par le jeton. Ici, on indexe ce qu'on reçoit,
sans jamais pouvoir en demander davantage.
"""

import asyncio
import logging
import math
import time
from dataclasses import dataclass, field
from datetime import date, timedelta

from app.clients.backend import BackendClient, BackendError
from app.clients.ollama import OllamaClient

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# Document
# ─────────────────────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class SeanceDocument:
    """
    Une séance, sous les deux formes dont on a besoin : un texte à vectoriser et
    de quoi citer la source.

    La citation n'est pas un ornement. Une réponse d'assistant sur le contenu
    pédagogique d'une classe n'a de valeur que si l'utilisateur peut remonter à
    la séance qui l'a produite — sinon elle ne se distingue pas d'une invention.
    """

    seance_id: int
    texte: str
    date_seance: str
    classe: str
    matiere: str
    enseignant: str
    chapitre: str
    travail_demande: str
    date_echeance: str

    @property
    def citation(self) -> str:
        """En-tête lisible d'un extrait : « 12/03/2026 — 7B — Mathématiques »."""
        parties = [p for p in (self.date_seance, self.classe, self.matiere) if p]
        return " — ".join(parties) if parties else f"séance {self.seance_id}"


def _texte(entry: dict) -> str:
    """
    Ce qui part au modèle d'embedding.

    Les libellés (classe, matière, enseignant) sont inclus DANS le texte indexé,
    pas seulement gardés en métadonnée. C'est ce qui fait que « où en est la
    7ème B en SVT ? » ressemble aux séances de la 7ème B en SVT : sans eux, la
    question et les documents n'auraient aucun mot en commun, et la recherche se
    rabattrait sur la seule ressemblance thématique.
    """
    champs = [
        entry.get("classeCode"),
        entry.get("matiereLibelle"),
        entry.get("enseignantNom"),
        entry.get("sujet"),
        entry.get("chapitre"),
        entry.get("activites"),
        entry.get("remarques"),
        entry.get("travailDemande"),
    ]
    return " | ".join(str(c).strip() for c in champs if c and str(c).strip())


def _to_document(entry: dict) -> SeanceDocument | None:
    texte = _texte(entry)
    if not texte:
        return None
    return SeanceDocument(
        seance_id=entry.get("id") or entry.get("seanceAppelId") or 0,
        texte=texte,
        date_seance=str(entry.get("dateSeance") or ""),
        classe=str(entry.get("classeCode") or ""),
        matiere=str(entry.get("matiereLibelle") or ""),
        enseignant=str(entry.get("enseignantNom") or ""),
        chapitre=str(entry.get("chapitre") or ""),
        travail_demande=str(entry.get("travailDemande") or ""),
        date_echeance=str(entry.get("dateEcheance") or ""),
    )


# ─────────────────────────────────────────────────────────────────────────────
# Index
# ─────────────────────────────────────────────────────────────────────────────

def _normalise(vecteur: list[float]) -> list[float]:
    """
    Ramène le vecteur à la norme 1, une fois pour toutes à l'indexation.

    Le produit scalaire de deux vecteurs normalisés EST leur cosinus. Diviser par
    les normes à chaque comparaison referait le même calcul des centaines de fois
    par question, pour un résultat identique.
    """
    norme = math.sqrt(sum(v * v for v in vecteur))
    if norme == 0.0:
        return vecteur
    return [v / norme for v in vecteur]


@dataclass
class CahierIndex:
    """Les documents d'un appelant et leurs vecteurs, appariés par position."""

    documents: list[SeanceDocument] = field(default_factory=list)
    vecteurs: list[list[float]] = field(default_factory=list)
    construit_a: float = 0.0

    def __len__(self) -> int:
        return len(self.documents)

    def rechercher(
        self,
        vecteur_question: list[float],
        top_k: int,
        plancher: float,
        marge: float,
    ) -> list[tuple[SeanceDocument, float]]:
        """
        Les séances les plus proches, filtrées en deux temps.

        Un seul seuil absolu ne suffit pas, et c'est une mesure, pas une
        intuition : sur ce corpus, nomic-embed-text place TOUTES les paires
        question/séance entre 0,52 et 0,69, y compris des questions sans le
        moindre rapport avec l'école. Un seuil bas ne rejette donc jamais rien,
        et un seuil haut couperait aussi les bonnes réponses. D'où deux filtres
        qui ne font pas le même travail :

          - `plancher` juge la QUESTION. Si même la meilleure séance reste sous
            ce niveau, le cahier n'a rien à dire sur le sujet et on renvoie une
            liste vide plutôt que la séance la moins mauvaise.
          - `marge` juge les SUIVANTES, relativement à la meilleure. C'est ce qui
            évite de compléter une réponse exacte avec trois séances qui n'ont
            fait que passer le plancher.
        """
        question = _normalise(vecteur_question)
        scores = [
            (doc, sum(a * b for a, b in zip(question, vecteur)))
            for doc, vecteur in zip(self.documents, self.vecteurs)
        ]
        scores.sort(key=lambda paire: paire[1], reverse=True)

        if not scores or scores[0][1] < plancher:
            return []

        limite_basse = scores[0][1] - marge
        return [(doc, score) for doc, score in scores[:top_k] if score >= limite_basse]

    def couverture(self) -> list[dict]:
        """
        Dernière séance connue par (classe, matière), et leur nombre.

        Agrégation déterministe, calculée sur l'index déjà chargé : c'est elle
        qui répond à « quelles classes ont pris du retard », pas la similarité.
        Une date et un compte se lisent, ils ne s'estiment pas — laisser un modèle
        de 7 milliards de paramètres les déduire d'extraits serait accepter qu'il
        se trompe sur le seul point que l'utilisateur vérifiera.
        """
        par_couple: dict[tuple[str, str], dict] = {}
        for doc in self.documents:
            cle = (doc.classe, doc.matiere)
            entree = par_couple.setdefault(
                cle,
                {
                    "classe": doc.classe,
                    "matiere": doc.matiere,
                    "seances": 0,
                    "derniere_date": "",
                    "dernier_chapitre": "",
                },
            )
            entree["seances"] += 1
            # Les dates sont au format ISO : l'ordre lexicographique est l'ordre
            # chronologique, pas besoin de les convertir pour les comparer.
            if doc.date_seance > entree["derniere_date"]:
                entree["derniere_date"] = doc.date_seance
                entree["dernier_chapitre"] = doc.chapitre or doc.texte[:80]

        return sorted(
            par_couple.values(),
            key=lambda e: (e["classe"], e["matiere"]),
        )


# ─────────────────────────────────────────────────────────────────────────────
# Construction et cache
# ─────────────────────────────────────────────────────────────────────────────

class CahierIndexStore:
    """
    Construit les index à la demande et les garde quelques minutes.

    **La clé de cache est le compte appelant, jamais l'établissement seul.** Le
    périmètre du corpus étant décidé par le backend à partir du compte, deux
    utilisateurs d'un même établissement n'obtiennent pas les mêmes séances : un
    cache partagé par tenant servirait à un enseignant l'index d'un collègue ou
    du directeur. C'est le genre de fuite qui ne se voit pas en test, parce que
    tout fonctionne — simplement, la mauvaise personne lit.
    """

    def __init__(
        self,
        backend: BackendClient,
        ollama: OllamaClient,
        ttl_seconds: float = 600.0,
        corpus_days: int = 365,
        corpus_limit: int = 500,
        batch_size: int = 32,
        max_entries: int = 32,
    ):
        self._backend = backend
        self._ollama = ollama
        self._ttl = ttl_seconds
        self._corpus_days = corpus_days
        self._corpus_limit = corpus_limit
        self._batch_size = batch_size
        self._max_entries = max_entries

        self._index: dict[str, CahierIndex] = {}
        # Un verrou par clé : deux questions simultanées du même utilisateur ne
        # doivent pas déclencher deux indexations complètes en parallèle.
        self._verrous: dict[str, asyncio.Lock] = {}

    async def obtenir(self, cle: str, token: str) -> CahierIndex:
        index = self._index.get(cle)
        if index is not None and (time.monotonic() - index.construit_a) < self._ttl:
            return index

        verrou = self._verrous.setdefault(cle, asyncio.Lock())
        async with verrou:
            # Une seconde vérification sous verrou : pendant l'attente, une autre
            # requête a pu construire l'index.
            index = self._index.get(cle)
            if index is not None and (time.monotonic() - index.construit_a) < self._ttl:
                return index

            index = await self._construire(token)
            self._ranger(cle, index)
            return index

    def invalider(self, cle: str) -> None:
        self._index.pop(cle, None)

    async def _construire(self, token: str) -> CahierIndex:
        depuis = (date.today() - timedelta(days=self._corpus_days)).isoformat()
        entrees = await self._backend.get_cahier_corpus(
            token, depuis=depuis, limite=self._corpus_limit
        )

        documents = [d for d in (_to_document(e) for e in entrees or []) if d is not None]
        if not documents:
            logger.info("Corpus de cahiers vide : aucun index construit.")
            return CahierIndex(construit_a=time.monotonic())

        debut = time.monotonic()
        vecteurs: list[list[float]] = []
        for depart in range(0, len(documents), self._batch_size):
            lot = documents[depart : depart + self._batch_size]
            bruts = await self._ollama.embed([d.texte for d in lot])
            vecteurs.extend(_normalise(v) for v in bruts)

        logger.info(
            "Index construit : %s séances vectorisées en %s ms",
            len(documents),
            int((time.monotonic() - debut) * 1000),
        )
        return CahierIndex(
            documents=documents, vecteurs=vecteurs, construit_a=time.monotonic()
        )

    def _ranger(self, cle: str, index: CahierIndex) -> None:
        self._index[cle] = index
        # Éviction du plus ancien : un service qui tourne des semaines finirait
        # sinon par garder l'index de chaque enseignant qui a posé une question.
        while len(self._index) > self._max_entries:
            plus_ancienne = min(self._index, key=lambda k: self._index[k].construit_a)
            self._index.pop(plus_ancienne, None)
            self._verrous.pop(plus_ancienne, None)


class CahierRetriever:
    """Façade utilisée par les outils : une question, des extraits cités."""

    def __init__(
        self,
        store: CahierIndexStore,
        ollama: OllamaClient,
        top_k: int = 5,
        plancher: float = 0.63,
        marge: float = 0.03,
    ):
        self._store = store
        self._ollama = ollama
        self._top_k = top_k
        self._plancher = plancher
        self._marge = marge

    async def rechercher(
        self, cle: str, token: str, question: str
    ) -> list[tuple[SeanceDocument, float]]:
        index = await self._store.obtenir(cle, token)
        if not len(index):
            return []
        vecteur = (await self._ollama.embed([question]))[0]
        return index.rechercher(vecteur, self._top_k, self._plancher, self._marge)

    async def couverture(self, cle: str, token: str) -> list[dict]:
        index = await self._store.obtenir(cle, token)
        return index.couverture()

    async def taille(self, cle: str, token: str) -> int:
        return len(await self._store.obtenir(cle, token))
