"""
Point d'entrée du service.

Assemble les clients, expose les routes, gère le cycle de vie.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.auth import (
    AuthenticatedUser,
    init_jwks_client,
    require_cahier_user,
    require_planning_user,
    require_super_admin,
)
from app.clients.actuator import ActuatorClient
from app.clients.backend import BackendClient, BackendError
from app.clients.ollama import OllamaClient
from app.clients.prometheus import PrometheusClient
from app.config import get_settings
from app.models import (
    CahierChatRequest,
    ChatRequest,
    ChatResponse,
    ConstraintConfirmRequest,
    ConstraintProposalRequest,
    ConstraintProposalResponse,
    HealthSnapshot,
    PlanningChatRequest,
)
from app.services.assistant import AssistantService, ToolLoop
from app.services.cahier_assistant import CahierAssistantService
from app.services.dsl_translator import DslTranslator
from app.services.metrics import MetricsService
from app.services.planning_assistant import PlanningAssistantService
from app.services.retrieval import CahierIndexStore, CahierRetriever
from app.tools.handlers import ToolHandlers

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s | %(message)s",
)
logger = logging.getLogger(__name__)

settings = get_settings()

# Conteneur d'objets partagés, construits au démarrage.
# Pas de framework d'injection : sur un service de cette taille,
# un dictionnaire explicite est plus lisible qu'une abstraction.
state: dict = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Cycle de vie : construction au démarrage, fermeture propre à l'arrêt.

    Créer les clients HTTP ici (et non par requête) permet de réutiliser
    les connexions TCP. Les fermer évite les avertissements de sockets
    laissées ouvertes à l'arrêt.
    """
    logger.info("Démarrage du service AI Assistant")

    prometheus = PrometheusClient(settings.prometheus_url, settings.http_timeout_seconds)
    actuator = ActuatorClient(settings.actuator_url, settings.http_timeout_seconds)
    ollama = OllamaClient(
        settings.ollama_url,
        settings.ollama_model,
        settings.ollama_temperature,
        settings.ollama_num_predict,
        settings.ollama_timeout_seconds,
        settings.embedding_model,
        settings.embedding_timeout_seconds,
    )

    backend = BackendClient(settings.backend_url, settings.backend_timeout_seconds)

    metrics = MetricsService(prometheus, actuator)
    handlers = ToolHandlers(metrics)
    assistant = AssistantService(ollama, handlers, settings.max_tool_iterations)

    translator = DslTranslator(
        ollama,
        backend,
        schema_ttl_seconds=settings.dsl_schema_ttl_seconds,
        temperature=settings.dsl_temperature,
        num_predict=settings.dsl_num_predict,
        max_repair_attempts=settings.dsl_max_repair_attempts,
    )
    planning_assistant = PlanningAssistantService(
        ToolLoop(ollama, settings.max_tool_iterations), backend, translator
    )

    # Index sémantique des cahiers de séance. Rien n'est construit ici : le
    # premier index d'un utilisateur naît de sa première question, avec SON
    # jeton. Précharger au démarrage supposerait un compte de service capable de
    # lire les séances de tout le monde — exactement ce que l'architecture évite.
    index_store = CahierIndexStore(
        backend,
        ollama,
        ttl_seconds=settings.cahier_index_ttl_seconds,
        corpus_days=settings.cahier_corpus_days,
        corpus_limit=settings.cahier_corpus_limit,
        batch_size=settings.embedding_batch_size,
        max_entries=settings.cahier_index_max_entries,
    )
    retriever = CahierRetriever(
        index_store, ollama, settings.rag_top_k,
        settings.rag_score_floor, settings.rag_score_margin,
    )
    cahier_assistant = CahierAssistantService(
        ToolLoop(ollama, settings.max_tool_iterations), backend, retriever
    )

    state.update(
        prometheus=prometheus, actuator=actuator, ollama=ollama, backend=backend,
        metrics=metrics, assistant=assistant, planning=planning_assistant,
        cahier=cahier_assistant,
    )

    if settings.auth_enabled:
        init_jwks_client(settings)

    # Diagnostic au démarrage : mieux vaut voir les dépendances manquantes
    # dans les logs de boot que lors de la première question de l'utilisateur.
    logger.info("Prometheus disponible : %s", await prometheus.is_available())
    logger.info("Ollama disponible     : %s", await ollama.is_available())
    # Le modèle d'embedding se télécharge séparément (`ollama pull`). Sans lui,
    # tout fonctionne SAUF la recherche sur les cahiers, et l'erreur ne
    # surgirait qu'à la première question d'un enseignant.
    logger.info(
        "Modèle d'embedding    : %s (%s)",
        settings.embedding_model,
        await ollama.is_embedding_model_available(),
    )

    yield

    logger.info("Arrêt du service")
    await prometheus.close()
    await actuator.close()
    await ollama.close()
    await backend.close()


app = FastAPI(
    title="SmartSchool AI Assistant",
    description=(
        "Assistant d'observabilité pour le super administrateur, "
        "assistant Planning pour les établissements, et recherche sémantique "
        "sur les cahiers de séance pour les enseignants et la direction"
    ),
    version="0.3.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)


# ── Routes techniques (non protégées) ───────────────────────────────────────

@app.get("/health", tags=["technique"])
async def health():
    """
    Santé DU SERVICE PYTHON lui-même.
    À ne pas confondre avec /actuator/health, qui concerne le backend Java.
    Utile pour un healthcheck Docker.
    """
    return {
        "status": "UP",
        "prometheus": await state["prometheus"].is_available(),
        "ollama": await state["ollama"].is_available(),
        "model": settings.ollama_model,
        "embedding_model": settings.embedding_model,
        "embeddings": await state["ollama"].is_embedding_model_available(),
        "backend_url": settings.backend_url,
    }


# ── Routes métier (protégées) ───────────────────────────────────────────────

@app.get("/api/monitoring/health", response_model=HealthSnapshot, tags=["monitoring"])
async def platform_health(
    window: str = "5m",
    user: dict = Depends(require_super_admin),
):
    """
    Instantané de la plateforme, SANS passer par le LLM.

    Cette route alimente les cartes KPI du dashboard : elle répond en
    quelques dizaines de millisecondes, là où l'assistant met plusieurs
    secondes. Ne fais jamais transiter un affichage temps réel par le LLM.
    """
    return await state["metrics"].get_snapshot(window)


@app.get("/api/monitoring/slowest-endpoints", tags=["monitoring"])
async def slowest_endpoints(
    window: str = "1h",
    user: dict = Depends(require_super_admin),
):
    return await state["metrics"].get_slowest_endpoints(window)


@app.post("/api/assistant/chat", response_model=ChatResponse, tags=["assistant"])
async def chat(
    request: ChatRequest,
    user: dict = Depends(require_super_admin),
):
    """Pose une question en langage naturel sur l'état de la plateforme."""
    logger.info(
        "Question de %s : %r",
        user.get("preferred_username", "?"),
        request.message,
    )
    return await state["assistant"].ask(request.message)


# ── Module Planning (protégé — rôle établissement) ──────────────────────────
#
# Ces routes n'écrivent jamais rien SAUF /constraints/confirm, qui exige que le
# client renvoie la règle exacte qu'il a affichée. Le découpage propose /
# confirme est le cœur du contrat : l'IA rédige, l'humain décide, le backend
# valide et enregistre.


@app.post("/api/planning/assistant/chat", response_model=ChatResponse, tags=["planning"])
async def planning_chat(
    request: PlanningChatRequest,
    user: AuthenticatedUser = Depends(require_planning_user),
):
    """
    Question en langage naturel sur l'emploi du temps de son établissement.

    Lecture seule : les outils accessibles au modèle ne savent que lire.
    """
    logger.info("Question Planning de %s : %r", user.username, request.message)
    return await state["planning"].ask(
        request.message, user.token, request.school_year_id, request.profile_id
    )


@app.post(
    "/api/planning/assistant/constraints/propose",
    response_model=ConstraintProposalResponse,
    tags=["planning"],
)
async def propose_constraint(
    request: ConstraintProposalRequest,
    user: AuthenticatedUser = Depends(require_planning_user),
):
    """
    Traduit une demande en français en règle DSL **validée par le backend**.

    Rien n'est enregistré : la réponse est une proposition, à confirmer via
    /constraints/confirm après relecture par l'utilisateur.
    """
    logger.info("Proposition de contrainte par %s : %r", user.username, request.message)

    proposal = await state["planning"].propose_constraint(
        request.message, user.token, request.school_year_id, request.profile_id
    )

    return ConstraintProposalResponse(
        valid=proposal.valid,
        dsl=proposal.dsl,
        summary=proposal.summary,
        verdict=proposal.verdict,
        message=proposal.message,
        errors=proposal.errors,
        warnings=proposal.warnings,
        feasible=proposal.feasible,
        conflicts=proposal.conflicts,
        matched_lessons=proposal.matched_lessons,
        total_lessons=proposal.total_lessons,
        examples=proposal.examples,
        attempts=proposal.attempts,
        duration_ms=proposal.duration_ms,
        requires_confirmation=True,
    )


@app.post(
    "/api/planning/assistant/constraints/confirm",
    status_code=201,
    tags=["planning"],
)
async def confirm_constraint(
    request: ConstraintConfirmRequest,
    user: AuthenticatedUser = Depends(require_planning_user),
):
    """
    Enregistre la règle, après confirmation explicite de l'utilisateur.

    L'écriture est faite par le BACKEND, avec le jeton de l'utilisateur : c'est
    lui qui revalide le DSL, applique les droits et rattache la règle au bon
    tenant. Ce service ne fait que relayer — il n'a aucun privilège propre, et
    une règle refusée ici l'aurait été aussi depuis l'interface.
    """
    payload = {
        "constraintProfileId": request.constraint_profile_id,
        "code": request.code,
        "name": request.name,
        "description": request.description,
        "dsl": request.dsl,
        "enabled": True,
        # Trace l'origine : cette règle vient d'une phrase, traduite par le
        # modèle, puis confirmée par un humain. Utile en audit, et honnête.
        "source": "AI_TRANSLATED",
        "naturalLanguageRequest": request.natural_language_request,
    }

    try:
        created = await state["backend"].create_custom_constraint(user.token, payload)
    except BackendError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail)

    logger.info("Contrainte %s créée par %s", request.code, user.username)
    return created


# ── Cahier de séance (protégé — enseignant ou direction) ────────────────────
#
# Une seule route pour deux usages : l'enseignant y interroge ses propres
# séances, le directeur celles de son établissement. Ce n'est pas ce service qui
# arbitre — le backend construit le corpus à partir du compte porté par le
# jeton, et l'assistant ne peut indexer que ce qu'il a reçu.


@app.post("/api/cahier/assistant/chat", response_model=ChatResponse, tags=["cahier"])
async def cahier_chat(
    request: CahierChatRequest,
    user: AuthenticatedUser = Depends(require_cahier_user),
):
    """
    Question en langage naturel sur les cahiers de séance.

    Lecture seule. Le cahier de séance fait foi sur ce qui a été enseigné et se
    verrouille : aucun outil du modèle ne sait y écrire.
    """
    logger.info("Question Cahier de %s : %r", user.username, request.message)

    # SCHOOL_ADMIN l'emporte quand un compte porte les deux rôles : le corpus
    # qu'il recevra sera celui de l'établissement, autant que le prompt le sache.
    direction = "SCHOOL_ADMIN" in user.roles

    return await state["cahier"].ask(
        request.message, user.token, user.cache_key, direction
    )
