"""
Recherche sémantique sur la circulaire ministérielle n°66 du 04/09/2024.

Le document qui régit réellement la construction des emplois du temps en
Tunisie est un PDF scanné de sept pages, en arabe, qu'aucun logiciel ne lit.
Ses quinze recommandations vivent donc dans la tête du directeur : ni
consultables, ni traçables, ni reliées aux contraintes réellement activées dans
le solveur. Ce module est ce qui les rend interrogeables.

**Ce n'est pas un second RAG « comme l'autre ».** Il se distingue du RAG cahier
(`retrieval.py`) sur tout ce qui compte, et les différences sont des décisions,
pas des raccourcis :

| | RAG Cahier | RAG Consigne (ici) |
|---|---|---|
| Corpus | dynamique, propre à chaque utilisateur | **statique, versionné, public** |
| Périmètre | cloisonné par jeton | **aucun cloisonnement : le texte est réglementaire** |
| Index | construit à la 1ʳᵉ question, TTL 10 min, éviction LRU | **construit une fois, jamais invalidé** |
| Taille | jusqu'à 500 séances | 22 chunks |
| Citation | date — classe — matière | **page — article** |

Il n'y a **pas de cloisonnement multi-tenant** ici, et c'est volontaire : la
circulaire est le même texte pour tous les établissements du pays. Un index
unique, partagé, chargé une fois — le cloisonner par tenant reviendrait à
recopier vingt-deux fois le même vecteur pour protéger un document publié au
Journal officiel.

À 22 chunks, aucune base vectorielle n'est nécessaire : 22 produits scalaires
sur des vecteurs de 768 flottants coûtent moins qu'un aller-retour réseau vers
un service dédié. La complexité d'un Qdrant ou d'un pgvector se paierait sans
rien acheter.

**La recherche est hybride, et c'est une mesure qui l'a imposé.** En dense seul,
6 questions sur 12 seulement plaçaient le bon article en tête. La raison tient
à la nature du corpus : vingt-deux articles du même texte, sur le même sujet,
dans la même langue administrative — ils se ressemblent tous. « Combien
d'heures de maths en 8ᵉ ? » tombait sur l'article I.2 (six heures par jour)
plutôt que sur le tableau des volumes horaires, parce que les deux parlent
d'heures et de classes.

Un second canal, purement lexical (recouvrement de termes pondéré par l'IDF),
tranche là où la sémantique hésite : « mathématiques » et « 8ᵉ » ne figurent
littéralement que dans les tableaux. Le score final est
`dense + λ · lexical`, avec λ = 0,20. Mesuré sur les 17 questions de
`tests/test_consigne_rag.py` : 6 → **9 bons articles en tête sur 12**, et
10 → **11 dans le top-3**. λ a un **plateau** entre 0,10 et 0,30 — le résultat
ne dépend donc pas d'un réglage fin, ce qui est précisément ce qu'on veut
pouvoir dire d'un hyperparamètre choisi sur douze questions.

**Le plancher, lui, reste appliqué au score dense seul.** Les deux canaux ne
répondent pas à la même question : le dense dit *« le corpus a-t-il quelque
chose à dire sur ce sujet ? »*, le lexical dit *« lequel de ces articles,
d'abord ? »*. Filtrer sur le score combiné laisserait passer une question hors
sujet qui partage par hasard un terme rare avec un article.
"""

import asyncio
import logging
import math
import re
import time
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

from app.clients.ollama import OllamaClient
from app.services.retrieval import _normalise

logger = logging.getLogger(__name__)

# Racine du service : app/services/consigne_retrieval.py → ai-assistant/
_RACINE = Path(__file__).resolve().parents[2]

# Le corpus est une suite de blocs délimités par des lignes contenant
# exactement « --- ». Le découpage donne : [en-tête, méta_1, corps_1,
# méta_2, corps_2, …]. C'est déterministe tant qu'aucun corps ne contient de
# ligne « --- » isolée — contrainte documentée en tête du fichier de corpus, et
# vérifiée par le test de parsing.
_SEPARATEUR = re.compile(r"^---[ \t]*$", re.MULTILINE)

_SOURCE = re.compile(r"^\*\*Source\*\*\s*—\s*(.+)$", re.MULTILINE)
_ORIGINAL = re.compile(r"^\*\*Texte original\*\*\s*—\s*(.+)$", re.MULTILINE)

# Ce qui suit ce marqueur est notre analyse, pas la circulaire. La frontière est
# explicite dans le corpus parce qu'elle est structurante : elle décide de ce
# qui est vectorisé (voir ArticleConsigne.indexable).
_COMMENTAIRE = re.compile(r"\*\*Lecture pour le solveur\*\*\s*—\s*", re.MULTILINE)

# Mots vides du canal lexical. Volontairement courte et manuelle : une liste
# générique retirerait « heures », « jour » ou « classe », qui sont ici les
# termes les plus discriminants du corpus.
_MOTS_VIDES = frozenset(
    """le la les un une des du de au aux et ou ni mais donc or car que qui quoi dont
    est sont etre ont avoir dans sur sous pour par avec sans entre vers chez ce cet
    cette ces il elle ils elles on nous vous suis peut peuvent doit doivent faut
    fait faire quel quelle quels quelles combien comment pourquoi quand son sa ses
    leur leurs mon ma mes ton ta tes notre votre pas plus moins tres bien""".split()
)

_MOT = re.compile(r"[a-z0-9]+")


def _jetons(texte: str) -> list[str]:
    """
    Découpe en termes comparables : minuscules, accents retirés, mots vides ôtés.

    Retirer les accents n'est pas cosmétique — un directeur écrit « mathematiques »
    ou « 8eme » aussi souvent qu'avec les accents, et sans cette normalisation le
    canal lexical ne servirait que les questions bien orthographiées.
    """
    plie = unicodedata.normalize("NFD", texte.lower())
    plie = "".join(c for c in plie if unicodedata.category(c) != "Mn")
    return [m for m in _MOT.findall(plie) if len(m) >= 3 and m not in _MOTS_VIDES]


# ─────────────────────────────────────────────────────────────────────────────
# Document
# ─────────────────────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class ArticleConsigne:
    """
    Un article de la circulaire, sous les formes dont on a besoin : un texte
    français à vectoriser, un texte arabe à citer, et de quoi remonter au scan.

    `portee` et `severite` pré-mappent l'article sur le DSL du solveur
    (`DslScope`, `DslSeverity`). C'est ce qui permet de proposer une règle en un
    clic depuis l'article — mais ce n'est qu'une proposition : la circulaire ne
    dit ni « CLASS_DAY » ni « HARD », c'est notre lecture, et un humain tranche.
    Les articles qui ne se traduisent pas en contrainte de solveur (affectation
    des enseignants, constitution des classes, tableaux de volumes) portent
    `portee = None` : ils restent consultables et citables sans jamais produire
    de règle.
    """

    id: str
    page: int
    section: str
    texte: str
    texte_ar: str
    portee: str | None
    severite: str | None
    commentaire: str = ""

    @property
    def citation(self) -> str:
        """« Circulaire n°66/2024, p. 2, § II.2 » — ce qui s'affiche à l'écran."""
        return f"Circulaire n°66/2024, p. {self.page}, § {self.id}"

    @property
    def indexable(self) -> str:
        """
        Ce qui part au modèle d'embedding : l'article, et rien d'autre.

        L'identifiant et la section sont inclus DANS le texte vectorisé, comme
        les libellés de classe et de matière le sont pour les cahiers : sans
        eux, « que dit l'article II.2 ? » n'aurait aucun mot en commun avec le
        chunk correspondant. Le texte arabe est exclu — mélanger les deux
        écritures dans un même vecteur brouillerait la similarité sans rien
        apporter.

        `commentaire` est exclu lui aussi, et c'est la décision la plus
        rentable du module. Mesuré sur les 22 chunks et 17 questions de
        `tests/test_consigne_rag.py` : en vectorisant l'article AVEC notre
        analyse, tous les chunks finissaient par se ressembler — ils partagent
        le même vocabulaire de solveur — et une question hors sujet (« une
        recette de couscous ») atteignait 0,639, au-dessus de la pire question
        légitime (0,625). Les deux populations se chevauchaient : aucun
        plancher ne pouvait plus les séparer. En n'indexant que l'article, le
        hors-sujet retombe à 0,585 et le pire cas légitime monte à 0,637.
        """
        return f"§ {self.id} — {self.section}\n{self.texte}"

    def to_dict(self) -> dict:
        """Forme exposée par l'API et affichée par l'écran « Contraintes officielles »."""
        return {
            "id": self.id,
            "page": self.page,
            "section": self.section,
            "texte": self.texte,
            "texte_ar": self.texte_ar,
            "commentaire": self.commentaire,
            "portee": self.portee,
            "severite": self.severite,
            "citation": self.citation,
        }


# ─────────────────────────────────────────────────────────────────────────────
# Lecture du corpus
# ─────────────────────────────────────────────────────────────────────────────

def _metadonnees(bloc: str) -> dict[str, str | None]:
    """Les lignes `clé: valeur` d'un bloc de métadonnées. `null` devient None."""
    valeurs: dict[str, str | None] = {}
    for ligne in bloc.strip().splitlines():
        if ":" not in ligne:
            continue
        cle, _, brut = ligne.partition(":")
        brut = brut.strip()
        valeurs[cle.strip()] = None if brut in ("null", "") else brut
    return valeurs


def _corps(bloc: str) -> tuple[str, str, str]:
    """
    Découpe un corps de chunk en (article, commentaire, texte arabe).

    Trois parties, trois usages distincts :

      - l'**article** est la traduction de la circulaire. Lui seul est
        vectorisé, et lui seul s'affiche comme texte officiel ;
      - le **commentaire** (après `**Lecture pour le solveur** —`) est notre
        analyse : il explique le mapping vers le DSL, il s'affiche en second
        plan, il n'est jamais indexé ;
      - les deux lignes de traçabilité `**Source**` et `**Texte original**` sont
        retirées du texte. Ne pas les vectoriser n'est pas un détail : la source
        est identique à quelques caractères près sur les vingt-deux chunks, elle
        les rapprocherait tous artificiellement.

    Les chunks de tableaux (§ T.1 à T.3) et la légende (§ N.1) n'ont pas de
    marqueur de commentaire : ils sont de la transcription de bout en bout, et
    ressortent donc entièrement comme article.
    """
    original = _ORIGINAL.search(bloc)
    texte_ar = original.group(1).strip() if original else ""

    utile = _ORIGINAL.sub("", _SOURCE.sub("", bloc)).strip()

    coupure = _COMMENTAIRE.search(utile)
    if coupure is None:
        return utile, "", texte_ar
    return utile[: coupure.start()].strip(), utile[coupure.end() :].strip(), texte_ar


def charger_corpus(chemin: str | Path) -> list[ArticleConsigne]:
    """
    Lit et découpe le fichier de corpus. Un chunk = un article.

    Lève `FileNotFoundError` si le fichier manque : c'est un défaut de
    déploiement, pas une dégradation acceptable. Un corpus vide ferait répondre
    l'assistant sans jamais citer, ce qui est pire qu'une erreur au démarrage.
    """
    fichier = Path(chemin)
    if not fichier.is_absolute():
        fichier = _RACINE / fichier

    contenu = fichier.read_text(encoding="utf-8")
    parties = _SEPARATEUR.split(contenu)

    # parties[0] est l'en-tête du fichier ; ensuite les blocs vont par paires.
    articles: list[ArticleConsigne] = []
    for meta_brut, corps_brut in zip(parties[1::2], parties[2::2]):
        meta = _metadonnees(meta_brut)
        identifiant = meta.get("id")
        if not identifiant:
            continue

        texte, commentaire, texte_ar = _corps(corps_brut)
        if not texte:
            continue

        articles.append(
            ArticleConsigne(
                id=identifiant,
                page=int(meta.get("page") or 0),
                section=meta.get("section") or "",
                texte=texte,
                texte_ar=texte_ar,
                portee=meta.get("portee"),
                severite=meta.get("severite_suggeree"),
                commentaire=commentaire,
            )
        )

    if not articles:
        raise ValueError(f"Corpus consigne vide ou illisible : {fichier}")
    return articles


# ─────────────────────────────────────────────────────────────────────────────
# Index
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class ConsigneIndex:
    """Les articles, leurs vecteurs et leurs sacs de termes, appariés par position."""

    articles: list[ArticleConsigne] = field(default_factory=list)
    vecteurs: list[list[float]] = field(default_factory=list)
    sacs: list[frozenset[str]] = field(default_factory=list)
    idf: dict[str, float] = field(default_factory=dict)

    def __len__(self) -> int:
        return len(self.articles)

    @classmethod
    def construire(
        cls, articles: list[ArticleConsigne], vecteurs: list[list[float]]
    ) -> "ConsigneIndex":
        """Assemble l'index et calcule l'IDF du canal lexical sur les 22 chunks."""
        sacs = [frozenset(_jetons(a.indexable)) for a in articles]

        occurrences: dict[str, int] = {}
        for sac in sacs:
            for jeton in sac:
                occurrences[jeton] = occurrences.get(jeton, 0) + 1

        # IDF lissé : un terme présent dans un seul article pèse ~3,8, un terme
        # présent partout pèse ~0. C'est ce qui fait que « mathématiques » décide
        # et que « heures », commun à presque tous les articles, ne décide pas.
        total = len(articles)
        idf = {j: math.log((total + 1) / (n + 0.5)) for j, n in occurrences.items()}

        return cls(articles=articles, vecteurs=vecteurs, sacs=sacs, idf=idf)

    def _score_lexical(self, jetons_question: list[str]) -> list[float]:
        """
        Part de la « masse IDF » de la question que chaque article couvre.

        Le résultat est dans [0, 1] et non dans l'échelle d'un BM25 : il doit
        s'additionner à un cosinus, donc être borné de la même façon. Un terme
        absent du corpus reçoit l'IDF maximal — il compte dans le dénominateur,
        et pénalise donc tous les articles également, ce qui est le comportement
        voulu : une question pleine de mots inconnus ne doit favoriser personne.
        """
        if not jetons_question:
            return [0.0] * len(self.articles)

        idf_max = math.log((len(self.articles) + 1) / 0.5)
        poids = [self.idf.get(j, idf_max) for j in jetons_question]
        masse = sum(poids) or 1.0

        return [
            sum(p for j, p in zip(jetons_question, poids) if j in sac) / masse
            for sac in self.sacs
        ]

    def rechercher(
        self,
        vecteur_question: list[float],
        question: str,
        top_k: int,
        plancher: float,
        marge: float,
        poids_lexical: float,
    ) -> list[tuple[ArticleConsigne, float]]:
        """
        Les articles les plus proches, en trois temps.

        **1. Le plancher juge la question, sur le score dense seul.** Si même le
        meilleur article reste sous ce niveau, la circulaire n'a rien à dire sur
        le sujet et on renvoie une liste vide plutôt que l'article le moins
        mauvais. Le canal lexical est délibérément tenu à l'écart de cette
        décision : une question hors sujet qui partage par hasard un terme rare
        avec un article ne doit pas franchir la porte.

        **2. Le classement, lui, combine les deux canaux** — `dense + λ·lexical`.
        C'est là que le lexical sert : départager vingt-deux articles du même
        texte, que la sémantique seule confond.

        **3. La marge juge les suivants**, relativement au meilleur, et évite de
        compléter une réponse exacte par deux articles qui n'ont fait que passer
        le plancher.

        Les seuils viennent de `retrieval.py` (RAG cahier) et sont réutilisés
        sciemment : ils décrivent le comportement de `nomic-embed-text`, qui
        comprime toutes ses similarités dans une bande étroite, et non celui d'un
        corpus particulier. Seul le plancher est abaissé à 0,60 — la circulaire
        est écrite dans une langue administrative que les questions d'un
        directeur ne reprennent jamais mot pour mot.
        """
        question_normalisee = _normalise(vecteur_question)
        dense = [
            sum(a * b for a, b in zip(question_normalisee, vecteur))
            for vecteur in self.vecteurs
        ]
        if not dense or max(dense) < plancher:
            return []

        lexical = self._score_lexical(_jetons(question))
        combine = [d + poids_lexical * l for d, l in zip(dense, lexical)]

        classement = sorted(
            zip(self.articles, combine), key=lambda paire: paire[1], reverse=True
        )
        limite_basse = classement[0][1] - marge
        return [(a, s) for a, s in classement[:top_k] if s >= limite_basse]

    def par_id(self, identifiant: str) -> ArticleConsigne | None:
        return next((a for a in self.articles if a.id == identifiant), None)


# ─────────────────────────────────────────────────────────────────────────────
# Façade
# ─────────────────────────────────────────────────────────────────────────────

class ConsigneRetriever:
    """
    Une question, des articles cités.

    L'index est construit **une fois** — au démarrage si Ollama répond déjà,
    sinon à la première question. Il n'expire jamais : le corpus est un fichier
    versionné, il ne change qu'à un déploiement. Un TTL n'aurait rien à
    rafraîchir.
    """

    def __init__(
        self,
        ollama: OllamaClient,
        chemin_corpus: str | Path,
        top_k: int = 3,
        plancher: float = 0.60,
        marge: float = 0.08,
        poids_lexical: float = 0.20,
        batch_size: int = 32,
    ):
        self._ollama = ollama
        self._chemin = chemin_corpus
        self._top_k = top_k
        self._plancher = plancher
        self._marge = marge
        self._poids_lexical = poids_lexical
        self._batch_size = batch_size

        # Le corpus est lu tout de suite : un fichier manquant doit faire échouer
        # la construction du service, pas la première question d'un directeur.
        self._articles = charger_corpus(chemin_corpus)
        self._index: ConsigneIndex | None = None
        self._verrou = asyncio.Lock()

    @property
    def articles(self) -> list[ArticleConsigne]:
        """Les articles dans l'ordre du document — alimente l'écran « Contraintes officielles »."""
        return self._articles

    def article(self, identifiant: str) -> ArticleConsigne | None:
        return next((a for a in self._articles if a.id == identifiant), None)

    async def prechauffer(self) -> int:
        """
        Vectorise le corpus. Appelée au démarrage, tolérante à l'échec.

        Retourne le nombre d'articles indexés, 0 si Ollama n'est pas joignable —
        auquel cas la première question réessaiera. Faire échouer le démarrage
        du service parce qu'un modèle d'embedding n'est pas encore chargé serait
        rendre indisponibles les routes de monitoring, qui n'en ont pas besoin.
        """
        try:
            return len(await self._obtenir())
        except Exception as exc:  # noqa: BLE001 — le boot ne doit jamais échouer ici
            logger.warning(
                "Corpus consigne non indexé au démarrage (%s) : "
                "l'index sera construit à la première question.",
                exc,
            )
            return 0

    async def rechercher(self, question: str) -> list[tuple[ArticleConsigne, float]]:
        index = await self._obtenir()
        if not len(index):
            return []
        vecteur = (await self._ollama.embed([question]))[0]
        return index.rechercher(
            vecteur,
            question,
            self._top_k,
            self._plancher,
            self._marge,
            self._poids_lexical,
        )

    async def _obtenir(self) -> ConsigneIndex:
        if self._index is not None:
            return self._index

        async with self._verrou:
            # Seconde vérification sous verrou : deux questions simultanées au
            # démarrage ne doivent pas vectoriser le corpus deux fois.
            if self._index is not None:
                return self._index
            self._index = await self._construire()
            return self._index

    async def _construire(self) -> ConsigneIndex:
        debut = time.monotonic()
        vecteurs: list[list[float]] = []
        for depart in range(0, len(self._articles), self._batch_size):
            lot = self._articles[depart : depart + self._batch_size]
            bruts = await self._ollama.embed([a.indexable for a in lot])
            vecteurs.extend(_normalise(v) for v in bruts)

        logger.info(
            "Corpus consigne : %s articles indexés en %s ms",
            len(self._articles),
            int((time.monotonic() - debut) * 1000),
        )
        return ConsigneIndex.construire(list(self._articles), vecteurs)
