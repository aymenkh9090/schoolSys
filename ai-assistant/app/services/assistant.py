"""
Orchestration de la conversation : boucle LLM ↔ outils.

C'est ici que se joue la qualité perçue du produit.
"""

import inspect
import json
import logging
import time

from app.clients.ollama import OllamaClient
from app.models import ChatResponse
from app.tools.definitions import TOOL_DEFINITIONS
from app.tools.handlers import ToolHandlers

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# Prompt système
# ─────────────────────────────────────────────────────────────────────────────
# Chaque règle corrige un défaut OBSERVÉ des petits modèles :
#
#  - COURT : un 3B se noie dans un prompt de 2 000 tokens. Chaque phrase compte.
#  - « utilise TOUJOURS les outils » : sans cette ligne, le modèle invente des
#    chiffres plausibles. C'est la phrase la plus importante du prompt.
#  - « n'invente jamais » : renforce la précédente, et les modèles répondent
#    bien aux interdits explicites sur ce point précis.
#  - « dis-le clairement » : sans cela, une donnée absente devient « tout va bien ».
#  - « en français » : le modèle raisonne sur des outils anglais mais s'adresse
#    à un utilisateur francophone.
#  - « 4 phrases maximum » : un 3B part en digression sans borne explicite.

SYSTEM_PROMPT = """Tu es l'assistant technique de la plateforme SmartSchool.
Tu aides le super administrateur à comprendre l'état technique de la plateforme
et l'activité de chaque établissement.

RÈGLES :
- Utilise TOUJOURS les outils disponibles pour obtenir des chiffres réels.
- N'invente JAMAIS une valeur. Si un outil renvoie "unavailable", dis-le clairement.
- Ne décris JAMAIS l'outil que tu vas utiliser : appelle-le, puis donne le résultat.
- Pour une question d'historique, de tendance ou d'évolution, utilise
  get_metric_history.
- Si la question porte sur un établissement précis (utilisateurs, élèves,
  classes, plannings générés), utilise get_school_metrics avec son nom.
- Réponds en français, de façon concise : 4 phrases maximum.
- Donne d'abord le constat chiffré, puis son interprétation.
- Si quelque chose est anormal, propose une piste de diagnostic concrète.
"""


def _accepted_args(handler, raw_args: dict, name: str) -> dict:
    """
    Ne garde que les paramètres réellement déclarés par le handler.

    Un modèle 3B ajoute spontanément des arguments plausibles mais non
    déclarés — typiquement `window` sur un outil qui n'en prend pas. Sans ce
    filtrage, l'appel lève TypeError, le modèle reçoit une erreur, et il
    répond en commentant l'échec au lieu de donner les chiffres.
    """
    if not isinstance(raw_args, dict):
        return {}
    accepted = set(inspect.signature(handler).parameters)
    extra = set(raw_args) - accepted
    if extra:
        logger.info("Arguments ignorés pour %s : %s", name, sorted(extra))
    return {k: v for k, v in raw_args.items() if k in accepted}


class ToolLoop:
    """
    Boucle générique « le modèle demande un outil, on l'exécute, il recommence ».

    Extraite de l'assistant d'observabilité pour être réutilisée par l'assistant
    Planning : les deux ont des outils et des prompts différents, mais exactement
    les mêmes pathologies de petit modèle à corriger — outil halluciné, arguments
    en trop, réponse inventée sans avoir consulté la moindre donnée. Ces
    correctifs ont coûté assez cher à mettre au point pour ne pas être réécrits
    en double, où ils divergeraient.
    """

    def __init__(self, ollama: OllamaClient, max_iterations: int = 3):
        self._ollama = ollama
        self._max_iterations = max_iterations

    async def run(
        self,
        question: str,
        system_prompt: str,
        tool_definitions: list[dict],
        registry: dict,
        no_tool_message: str,
        exhausted_message: str,
    ) -> ChatResponse:
        started = time.monotonic()
        messages: list[dict] = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": question},
        ]
        tools_used: list[str] = []

        # Boucle bornée : protège contre un modèle qui rappellerait
        # indéfiniment le même outil (comportement observé sur les petits modèles).
        for _iteration in range(self._max_iterations):
            try:
                message = await self._ollama.chat(messages, tool_definitions)
            except Exception:
                logger.exception("Échec de l'appel à Ollama")
                return ChatResponse(
                    answer=(
                        "L'assistant est momentanément indisponible. "
                        "Vérifie qu'Ollama est démarré (http://localhost:11434)."
                    ),
                    tools_used=tools_used,
                    duration_ms=_elapsed(started),
                )

            tool_calls = message.get("tool_calls") or []

            # Pas d'appel d'outil → c'est la réponse finale.
            if not tool_calls:
                answer = (message.get("content") or "").strip()

                # Garde-fou : si le modèle répond sans avoir consulté la moindre
                # donnée, sa réponse est nécessairement inventée. On préfère
                # une réponse honnête à une réponse fausse mais fluide.
                # Le critère est « aucun outil n'a abouti », à n'importe quel
                # tour : un premier appel raté suivi d'une paraphrase du modèle
                # ne vaut pas mieux qu'une réponse sortie de nulle part.
                if not tools_used:
                    logger.warning(
                        "Le modèle a répondu sans appeler d'outil : %r", question
                    )
                    answer = no_tool_message

                return ChatResponse(
                    answer=answer,
                    tools_used=tools_used,
                    duration_ms=_elapsed(started),
                )

            # Conserver la demande d'appel dans l'historique : le modèle doit
            # voir sa propre décision au tour suivant, sinon il la répète.
            messages.append(message)

            for call in tool_calls:
                name = call.get("function", {}).get("name", "")
                raw_args = call.get("function", {}).get("arguments", {})

                # Ollama renvoie parfois les arguments sous forme de chaîne JSON
                # plutôt que d'objet, selon le modèle. On gère les deux.
                if isinstance(raw_args, str):
                    try:
                        raw_args = json.loads(raw_args)
                    except json.JSONDecodeError:
                        raw_args = {}

                handler = registry.get(name)
                if handler is None:
                    # Le modèle a halluciné un nom d'outil. On le lui dit
                    # explicitement : il corrige généralement au tour suivant.
                    logger.warning("Outil inconnu demandé : %r", name)
                    result = (
                        f"Error: unknown tool '{name}'. "
                        f"Available tools: {', '.join(registry)}"
                    )
                else:
                    args = _accepted_args(handler, raw_args, name)
                    try:
                        result = await handler(**args)
                        tools_used.append(name)
                        logger.info("Outil %s(%s) exécuté", name, args)
                    except TypeError as exc:
                        # Filet de sécurité : ne devrait plus se produire
                        # depuis _accepted_args, mais un plantage ici coûterait
                        # toute la réponse.
                        logger.warning("Arguments invalides pour %s : %s", name, exc)
                        result = f"Error: invalid arguments for '{name}'."

                # tool_name : sans lui, le modèle ne sait pas à quel appel
                # correspond ce résultat et a tendance à redemander le même outil.
                messages.append(
                    {"role": "tool", "tool_name": name, "content": result}
                )

        # Boucle épuisée sans réponse finale.
        logger.warning("max_tool_iterations atteint pour : %r", question)
        return ChatResponse(
            answer=exhausted_message,
            tools_used=tools_used,
            duration_ms=_elapsed(started),
        )


def _elapsed(started: float) -> int:
    return int((time.monotonic() - started) * 1000)


class AssistantService:
    """Assistant d'observabilité : le prompt et les outils de la plateforme."""

    NO_TOOL_MESSAGE = (
        "Je n'ai pas pu consulter les métriques pour cette question. "
        "Reformule en précisant ce que tu veux vérifier : l'état de la "
        "plateforme (mémoire, latence, erreurs, base de données), ou "
        "l'activité d'un établissement (utilisateurs, élèves, classes, "
        "plannings générés)."
    )

    EXHAUSTED_MESSAGE = (
        "Je n'ai pas réussi à formuler une réponse complète. "
        "Essaie une question plus précise, par exemple "
        "« quelle est la latence de la dernière heure ? »."
    )

    def __init__(
        self,
        ollama: OllamaClient,
        handlers: ToolHandlers,
        max_iterations: int = 3,
    ):
        self._loop = ToolLoop(ollama, max_iterations)
        self._registry = handlers.as_registry()

    async def ask(self, question: str) -> ChatResponse:
        return await self._loop.run(
            question=question,
            system_prompt=SYSTEM_PROMPT,
            tool_definitions=TOOL_DEFINITIONS,
            registry=self._registry,
            no_tool_message=self.NO_TOOL_MESSAGE,
            exhausted_message=self.EXHAUSTED_MESSAGE,
        )
