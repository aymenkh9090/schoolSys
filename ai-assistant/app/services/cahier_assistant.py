"""
Assistant du cahier de séance — un index, deux rôles.

L'enseignant y interroge sa propre mémoire de classe (« qu'ai-je fait avec la
8ème A la semaine dernière ? »), le directeur le pilotage pédagogique de son
établissement (« quelles classes ont pris du retard ? »). Ce sont deux produits
pour l'utilisateur, et un seul mécanisme pour le code : mêmes outils, même
boucle, même index. Seuls changent le périmètre du corpus — décidé par le
backend à partir du compte, jamais ici — et quelques lignes de prompt.

Écrire deux services aurait doublé la surface à maintenir pour une différence
qui tient en un adjectif : « tes séances » ou « les séances de l'établissement ».
"""

import logging

from app.clients.backend import BackendClient
from app.models import ChatResponse
from app.services.assistant import ToolLoop
from app.services.retrieval import CahierRetriever
from app.tools.cahier_definitions import TOOL_DEFINITIONS
from app.tools.cahier_handlers import CahierToolHandlers

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# Prompts
# ─────────────────────────────────────────────────────────────────────────────
# Le tronc commun porte les règles anti-invention, déjà éprouvées sur les deux
# autres assistants. La règle propre au RAG est la dernière : un extrait cité
# est vérifiable, une synthèse sans source ne l'est pas.

_COMMUN = """
RÈGLES :
- Utilise TOUJOURS les outils. Tu n'as aucune connaissance des séances en dehors
  de ce qu'ils renvoient.
- N'invente JAMAIS un chapitre, une date, une classe ou un devoir.
- Ne confonds jamais ces deux situations, elles appellent des actions opposées :
  · un outil répond "unavailable" → le service est en panne : dis-le, invite à
    réessayer plus tard ;
  · un outil dit qu'aucune séance n'est renseignée → rien n'est en panne, le
    cahier est vide. Dis-le tel quel et n'invite SURTOUT PAS à réessayer.
- Quand tu t'appuies sur un EXTRAIT de séance, cite sa source entre crochets,
  telle que l'outil te l'a donnée : [12/03/2026 — 7B — Mathématiques].
- Le tableau de couverture se lit tel quel : reprends-en les chiffres et les
  dates sans les arrondir, et n'invente pas de crochets pour ses lignes.
- Ne mentionne jamais le score de pertinence : il t'aide à choisir, il n'intéresse
  pas l'utilisateur.
- Ne décris JAMAIS l'outil que tu vas utiliser : appelle-le, puis donne le résultat.
- Pour l'avancement, le retard ou l'état général, utilise get_couverture_programme.
  Pour le contenu d'une séance, utilise search_cahier_seances.
- Tu ne peux RIEN modifier : le cahier de séance se remplit dans l'interface.
- Réponds en français, de façon concise : 5 phrases maximum.
"""

PROMPT_ENSEIGNANT = (
    """Tu es l'assistant du cahier de séance d'un enseignant de SmartSchool.
Tu l'aides à retrouver ce qu'il a enseigné, à quelles classes, et ce qu'il a
demandé comme travail. Tu ne vois QUE ses propres séances.
"""
    + _COMMUN
    + "- Adresse-toi à lui à la deuxième personne : « tu as traité… », « ta classe… ».\n"
)

PROMPT_DIRECTION = (
    """Tu es l'assistant de pilotage pédagogique d'un directeur d'établissement
SmartSchool. Tu l'aides à savoir ce qui est réellement enseigné dans son
établissement, classe par classe et matière par matière, à partir des cahiers de
séance remplis par les enseignants.
"""
    + _COMMUN
    + """- Nomme toujours la classe, la matière et l'enseignant concernés : un constat
  sans destinataire n'est pas actionnable.
- Un écart d'avancement est un constat, pas un jugement sur l'enseignant : reste
  factuel et propose la vérification à faire.
"""
)

NO_TOOL_MESSAGE = (
    "Je n'ai pas pu consulter les cahiers de séance pour cette question. "
    "Reformule en précisant ce que tu cherches : le contenu d'une séance "
    "(chapitre, activités, travail donné), ou l'avancement d'une classe."
)

EXHAUSTED_MESSAGE = (
    "Je n'ai pas réussi à formuler une réponse complète. Essaie une question "
    "plus précise, par exemple « qu'est-ce qui a été traité en maths en 7B "
    "depuis janvier ? »."
)


class CahierAssistantService:
    def __init__(
        self,
        loop: ToolLoop,
        backend: BackendClient,
        retriever: CahierRetriever,
    ):
        self._loop = loop
        self._backend = backend
        self._retriever = retriever

    async def ask(
        self, question: str, token: str, cle: str, direction: bool
    ) -> ChatResponse:
        """
        Répond à une question sur le cahier de séance.

        `cle` identifie le CACHE D'INDEX de l'appelant : elle doit être propre au
        compte, jamais à l'établissement. Le corpus dépendant du compte, une clé
        partagée servirait à un enseignant l'index d'un collègue — une fuite
        indétectable, puisque tout continuerait de fonctionner.

        `direction` ne change que le prompt. Il ne donne accès à rien : c'est le
        backend qui a déjà décidé, en construisant le corpus, ce que cet appelant
        avait le droit de lire.
        """
        handlers = CahierToolHandlers(self._retriever, cle, token)
        return await self._loop.run(
            question=question,
            system_prompt=PROMPT_DIRECTION if direction else PROMPT_ENSEIGNANT,
            tool_definitions=TOOL_DEFINITIONS,
            registry=handlers.as_registry(),
            no_tool_message=NO_TOOL_MESSAGE,
            exhausted_message=EXHAUSTED_MESSAGE,
        )
