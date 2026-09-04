"""
Question libre sur la circulaire ministérielle n°66/2024.

« Combien d'heures de maths en 8ᵉ ? », « puis-je mettre cinq heures d'affilée à
un enseignant le samedi ? » — des questions dont la réponse existe, noir sur
blanc, dans un scan de sept pages en arabe que personne ne relit.

**Pas de boucle d'outils ici, et c'est un écart assumé** avec les trois autres
assistants du service. Eux donnent au modèle plusieurs outils et le laissent
choisir ; la règle la plus importante de leur prompt est d'ailleurs « utilise
TOUJOURS les outils », parce qu'un modèle de 7B qui a le choix finit par
répondre de mémoire avec des chiffres plausibles.

Ce service n'a qu'une opération, et elle est toujours nécessaire : chercher dans
le corpus. Laisser le modèle décider s'il cherche n'ajouterait donc qu'une
décision qu'il peut rater. La recherche a lieu d'abord, systématiquement ; les
articles entrent dans le prompt ; le modèle ne peut que reformuler ce qu'on lui
a donné. On supprime le mode de défaillance au lieu de l'interdire par une
phrase — et on économise au passage un aller-retour de génération, qui coûte de
5 à 30 secondes sur CPU.

Deuxième conséquence du même raisonnement : **quand la recherche ne rend rien,
le modèle n'est pas appelé du tout.** Pas de génération, donc pas d'invention
possible, et une réponse immédiate. C'est le cas d'une question hors sujet, et
le plancher du RAG a précisément été calibré pour le reconnaître.

Contrairement à l'ancrage de la traduction DSL, la recherche n'est **pas**
filtrée aux articles porteurs d'une portée. Les deux usages n'ont pas les mêmes
besoins : une contrainte de solveur ne peut rien tirer d'un tableau de volumes
horaires, alors qu'ici ce tableau EST la réponse à « combien d'heures d'arabe en
7ᵉ ».
"""

import logging
import time

from app.clients.ollama import OllamaClient
from app.models import ConsigneChatResponse
from app.services.consigne_retrieval import ArticleConsigne, ConsigneRetriever

logger = logging.getLogger(__name__)


SYSTEM_PROMPT = """Tu réponds aux questions d'un directeur de collège tunisien sur
la circulaire n°66 du 04/09/2024 du ministère de l'Éducation, qui fixe les règles
d'élaboration des emplois du temps.

RÈGLES :
- Réponds UNIQUEMENT à partir des extraits ci-dessous. Tu n'as aucune autre
  connaissance de cette circulaire, et tu ne dois pas t'appuyer sur ce que tu
  crois savoir des programmes scolaires.
- Cite l'article à l'appui de CHAQUE affirmation, entre crochets et sous la forme
  exacte qui t'est donnée : [§ II.2, p. 2]. Une phrase qui avance un chiffre ou
  une règle sans crochets est une réponse incomplète, même si elle est juste.
- Si les extraits ne répondent pas à la question, dis-le franchement. Ne comble
  jamais un silence de la circulaire par une règle vraisemblable : un directeur
  qui applique une règle inventée le découvre à l'inspection.
- N'invente JAMAIS un chiffre, un volume horaire ou un numéro d'article.
- NE CALCULE JAMAIS. N'additionne rien, ne convertis rien. Chaque cellule des
  tableaux porte déjà son total, après le point médian : « 2+1+1+1 · 5 h » veut
  dire cinq heures par semaine, découpées en une séance de deux heures et trois
  d'une heure. Lis le total, recopie-le. Le découpage sert à construire l'emploi
  du temps, pas à calculer un volume.
  « (2) » et « (3) » désignent une séance hebdomadaire en système de groupes,
  « ① » une séance de quinzaine pour la classe entière.
- Ne cite QUE les articles présents dans les extraits ci-dessous. Si le tableau
  du type d'établissement demandé ne t'est pas fourni, dis-le au lieu d'en citer
  un autre sous son numéro.
- Les trois tableaux de volumes horaires portent sur des types d'établissement
  DIFFÉRENTS : § T.1 les collèges, § T.2 les collèges techniques, § T.3 les
  collèges pilotes. Nomme donc toujours le type d'établissement À CÔTÉ de sa
  citation, et donne la valeur des autres tableaux quand elle diffère. Modèle à
  suivre : « En collège ordinaire, 4 heures [§ T.1, p. 5] ; en collège pilote,
  5 heures [§ T.3, p. 7]. » Un directeur de collège ordinaire à qui l'on annonce
  le volume d'un collège pilote applique le mauvais programme toute l'année.
- Réponds en français, de façon concise : 5 phrases maximum.
"""

# Réponse rendue sans appeler le modèle quand la recherche ne remonte rien. La
# formulation dit ce qui s'est passé — le corpus ne couvre pas le sujet — et non
# « je n'ai pas compris » : la nuance compte pour un utilisateur qui cherche à
# savoir si la règle existe.
AUCUN_ARTICLE = (
    "La circulaire n°66/2024 ne traite pas ce sujet, ou la question s'en éloigne "
    "trop pour que j'y retrouve un article. Elle couvre l'organisation des emplois "
    "du temps : durées journalières, coupures, alternance des enseignants, "
    "répartition des matières, salles spécialisées, et les volumes horaires par "
    "niveau. Reformulez en vous rapprochant de l'un de ces thèmes."
)

MODELE_INDISPONIBLE = (
    "Le service de rédaction est momentanément indisponible. Les articles trouvés "
    "sont affichés ci-dessous : ils répondent peut-être directement à votre question."
)


class ConsigneAssistantService:
    def __init__(self, ollama: OllamaClient, consigne: ConsigneRetriever):
        self._ollama = ollama
        self._consigne = consigne

    async def ask(self, question: str) -> ConsigneChatResponse:
        debut = time.monotonic()

        try:
            resultats = await self._consigne.rechercher(question)
        except Exception as exc:  # noqa: BLE001 — dégradation, pas panne
            logger.warning("Recherche consigne indisponible : %s", exc)
            resultats = []

        articles = _completer_tableaux(
            [article for article, _ in resultats], self._consigne.articles
        )

        if not articles:
            # Aucun appel au modèle : rien à reformuler, donc rien à inventer.
            return ConsigneChatResponse(
                answer=AUCUN_ARTICLE, sources=[], duration_ms=_ecoule(debut)
            )

        messages = [
            {"role": "system", "content": SYSTEM_PROMPT + _extraits(articles)},
            {"role": "user", "content": question},
        ]

        try:
            message = await self._ollama.chat(messages)
            reponse = (message.get("content") or "").strip()
        except Exception:  # noqa: BLE001
            logger.exception("Échec de l'appel à Ollama sur /api/consigne/chat")
            reponse = ""

        # Les articles sont rendus même sans rédaction : le RAG a fait son
        # travail, et un extrait cité vaut mieux qu'un message d'erreur seul.
        return ConsigneChatResponse(
            answer=reponse or MODELE_INDISPONIBLE,
            sources=[a.to_dict() for a in articles],
            duration_ms=_ecoule(debut),
        )


# Les trois tableaux de volumes horaires se lisent ensemble : ils décrivent le
# même programme pour trois types d'établissement. En citer un sans les autres
# invite à appliquer le mauvais — c'est arrivé en essai, le modèle ayant reçu le
# seul tableau des collèges pilotes et INVENTÉ une citation au § T.1 pour
# répondre sur un collège ordinaire. Plutôt que d'élargir les seuils de la
# recherche, on complète la fratrie : c'est déterministe, et ça n'affecte que
# ce service — l'ancrage de la traduction DSL, lui, écarte les tableaux.
_TABLEAUX = ("T.1", "T.2", "T.3")


def _completer_tableaux(
    articles: list[ArticleConsigne], corpus: list[ArticleConsigne]
) -> list[ArticleConsigne]:
    """Si un tableau de volumes est retrouvé, joint ses deux frères."""
    if not any(a.id in _TABLEAUX for a in articles):
        return articles

    presents = {a.id for a in articles}
    manquants = [a for a in corpus if a.id in _TABLEAUX and a.id not in presents]
    return articles + manquants


def _extraits(articles: list[ArticleConsigne]) -> str:
    """
    Met les articles en forme pour le prompt, avec la citation TOUTE FAITE.

    Le modèle ne compose pas « [§ II.2, p. 2] » à partir d'un numéro et d'une
    page : la chaîne lui est donnée telle qu'il doit la recopier. Un 7B qui
    assemble une référence se trompe de page une fois sur quelques-unes, et une
    citation fausse est pire qu'une absence de citation — elle a l'apparence
    d'une preuve, et c'est justement ce que cette fonctionnalité existe pour
    fournir.
    """
    blocs = ["", "EXTRAITS DE LA CIRCULAIRE :"]
    for article in articles:
        blocs.append(f"\n[§ {article.id}, p. {article.page}] — {article.section}")
        blocs.append(article.texte)
    return "\n".join(blocs)


def _ecoule(debut: float) -> int:
    return int((time.monotonic() - debut) * 1000)
