"""
Assistant conversationnel du module Planning.

Deux capacités, volontairement séparées :

  - `ask` : répondre à une question sur l'emploi du temps existant. Lecture
    seule, outils bornés, chiffres issus du solveur.
  - `propose_constraint` : traduire une demande en règle DSL validée, et la
    RENDRE à l'utilisateur. Aucune écriture.

La séparation n'est pas cosmétique. Un modèle qui peut à la fois discuter et
écrire en base finira par écrire pendant qu'il discute — c'est la panne classique
des agents à outils, et elle est d'autant plus probable ici que le modèle tourne
sur CPU et se trompe régulièrement d'intention. En retirant l'écriture de la
boucle d'outils, la question ne se pose plus : l'enregistrement est une route
HTTP distincte, déclenchée par un clic de l'utilisateur sur une règle affichée
mot pour mot.
"""

import logging

from app.clients.backend import BackendClient
from app.models import ChatResponse
from app.services.assistant import ToolLoop
from app.services.dsl_translator import ConstraintProposal, DslTranslator
from app.tools.planning_definitions import TOOL_DEFINITIONS
from app.tools.planning_handlers import PlanningToolHandlers

logger = logging.getLogger(__name__)


SYSTEM_PROMPT = """Tu es l'assistant du module Emploi du temps de SmartSchool.
Tu aides le directeur d'établissement à comprendre et à améliorer son planning.

RÈGLES :
- Utilise TOUJOURS les outils pour obtenir des chiffres réels.
- N'invente JAMAIS un score, un nombre de violations ou un nom d'enseignant.
- Ne décris JAMAIS l'outil que tu vas utiliser : appelle-le, puis donne le résultat.
- Pour « pourquoi le planning n'est pas bon », utilise explain_violations.
- Tu ne peux RIEN modifier : pas de contrainte créée, pas de génération lancée.
  Si l'utilisateur demande une modification, explique qu'il doit la confirmer
  dans l'interface.
- Réponds en français, de façon concise : 5 phrases maximum.
- Donne d'abord le constat chiffré, puis ce qu'il faut en faire.
"""

NO_TOOL_MESSAGE = (
    "Je n'ai pas pu consulter les données du planning pour cette question. "
    "Reformule en précisant ce que tu veux savoir (score, conflits, "
    "contraintes actives, suggestions d'amélioration)."
)

EXHAUSTED_MESSAGE = (
    "Je n'ai pas réussi à formuler une réponse complète. Essaie une question "
    "plus précise, par exemple « pourquoi cet emploi du temps n'est-il pas optimal ? »."
)


class PlanningAssistantService:
    def __init__(
        self,
        loop: ToolLoop,
        backend: BackendClient,
        translator: DslTranslator,
    ):
        self._loop = loop
        self._backend = backend
        self._translator = translator

    # ── conversation ──────────────────────────────────────────────────────────

    async def ask(
        self,
        question: str,
        token: str,
        school_year_id: int | None = None,
        profile_id: int | None = None,
    ) -> ChatResponse:
        # Handlers construits par requête, liés au jeton de l'appelant : le
        # tenant n'est jamais un paramètre que le modèle pourrait choisir.
        handlers = PlanningToolHandlers(
            self._backend, token, school_year_id, profile_id
        )
        return await self._loop.run(
            question=question,
            system_prompt=SYSTEM_PROMPT,
            tool_definitions=TOOL_DEFINITIONS,
            registry=handlers.as_registry(),
            no_tool_message=NO_TOOL_MESSAGE,
            exhausted_message=EXHAUSTED_MESSAGE,
        )

    # ── proposition de règle ──────────────────────────────────────────────────

    async def propose_constraint(
        self,
        request_text: str,
        token: str,
        school_year_id: int | None = None,
        profile_id: int | None = None,
    ) -> ConstraintProposal:
        """
        Traduit une phrase en règle DSL validée par le backend, et s'arrête là.

        Le résultat contient tout ce qu'il faut à l'écran de confirmation : la
        règle brute, son résumé en français, l'impact mesuré et les conflits
        éventuels. Rien n'est enregistré à ce stade.
        """
        return await self._translator.translate(
            request_text, token, school_year_id, profile_id
        )
