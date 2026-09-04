from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


    # Prometheus
    prometheus_url: str = "http://localhost:9090"

    # Spring Boot Actuator — management.server.port (9091), PAS le port API 8080
    actuator_url: str = "http://localhost:9091"

    # HTTP
    http_timeout_seconds: float = 5.0 

    # Ollama 
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:7b"
    ollama_temperature: float = 0.1
    ollama_num_predict: int = 300
    ollama_timeout_seconds: float = 90.0

    # Tool calling
    max_tool_iterations: int = 3

    # Backend Spring Boot — API métier (port 8080), PAS l'Actuator (9091).
    # L'assistant y accède TOUJOURS avec le jeton de l'utilisateur qui lui parle :
    # il n'a pas de compte de service, donc pas de privilège propre. L'isolation
    # multi-tenant est ainsi celle du backend, sans code de sécurité dupliqué ici.
    backend_url: str = "http://localhost:8080"
    backend_timeout_seconds: float = 20.0

    # Durée de cache du catalogue DSL. Il ne change qu'à un déploiement du
    # backend : le relire à chaque question serait un aller-retour réseau gratuit.
    dsl_schema_ttl_seconds: float = 300.0

    # Génération de DSL : température nulle (on veut du JSON déterministe, pas de
    # créativité) et une limite de tokens plus généreuse qu'une réponse de chat,
    # une règle à quatre conditions dépassant facilement 300 tokens.
    dsl_temperature: float = 0.0
    dsl_num_predict: int = 700

    # Nombre de corrections proposées au modèle quand le backend rejette son DSL.
    # 1 suffit dans l'immense majorité des cas : au-delà, le modèle boucle sur la
    # même erreur et il vaut mieux rendre la main à l'utilisateur.
    dsl_max_repair_attempts: int = 1

    # ── Recherche sémantique sur les cahiers de séance (RAG) ────────────────
    # Modèle d'embedding, servi par la même instance Ollama que le chat.
    # nomic-embed-text pèse ~300 Mo : contrairement à un second modèle de chat,
    # il cohabite sans peine avec qwen2.5:7b dans 6 Go de VRAM.
    embedding_model: str = "nomic-embed-text"
    # Nombre de textes envoyés par appel à /api/embed. Trop grand, la requête
    # dépasse le timeout sur une première indexation ; trop petit, on multiplie
    # les allers-retours HTTP pour rien.
    embedding_batch_size: int = 32
    embedding_timeout_seconds: float = 120.0

    # Durée de vie d'un index. Un cahier de séance est saisi une fois puis
    # verrouillé : le corpus bouge de quelques séances par jour, pas par minute.
    # Dix minutes évitent de réindexer à chaque question sans jamais montrer des
    # données de la veille.
    cahier_index_ttl_seconds: float = 600.0
    # Profondeur d'historique indexée. Au-delà d'une année scolaire, les séances
    # concernent d'autres classes et d'autres programmes : elles bruitent la
    # recherche plus qu'elles ne l'enrichissent.
    cahier_corpus_days: int = 365
    cahier_corpus_limit: int = 500
    # Nombre d'index gardés en mémoire simultanément. Chaque index coûte quelques
    # mégaoctets ; sans borne, un service qui tourne des semaines finirait par
    # tous les conserver.
    cahier_index_max_entries: int = 32

    # Extraits rendus au modèle par recherche. Cinq séances tiennent dans le
    # contexte d'un 7B tout en laissant de la place à sa réponse.
    rag_top_k: int = 5
    # Plancher sous lequel on considère que le cahier n'a rien à dire sur la
    # question. MESURÉ, non deviné : nomic-embed-text comprime toutes ses
    # similarités entre 0,52 et 0,69 sur ce corpus francophone. Sur l'échantillon
    # de calibration, les questions pertinentes plafonnaient à 0,65-0,68 et les
    # questions hors sujet à 0,57-0,60 ; 0,63 sépare les deux. À revoir sur des
    # données réelles : l'échantillon était petit, et un corpus plus dense
    # resserrera l'écart.
    rag_score_floor: float = 0.63
    # Écart maximal toléré avec le meilleur extrait. Sans lui, une réponse exacte
    # se retrouve noyée dans trois séances qui n'ont fait que passer le plancher.
    rag_score_margin: float = 0.03

    # ── RAG « consigne ministérielle » ──────────────────────────────────────
    # Corpus statique et public : la circulaire n°66/2024 est le même texte pour
    # tous les établissements. Un seul index partagé, chargé au démarrage, jamais
    # invalidé — contrairement aux cahiers, il n'y a rien à cloisonner ni à
    # rafraîchir. Chemin relatif à la racine du service.
    consigne_corpus_path: str = "data/corpus-consigne-2024.md"
    # 3 articles suffisent à ancrer une règle et tiennent dans le contexte du 7B
    # à côté du catalogue DSL.
    consigne_top_k: int = 3
    # Plancher plus bas que celui des cahiers (0,63), et pour une raison
    # mesurable : la circulaire est écrite dans une langue administrative que les
    # questions d'un directeur ne reprennent jamais mot pour mot. Sur les 17
    # questions de calibration, les pertinentes plafonnent à 0,633 au pire et les
    # hors-sujet à 0,574 au mieux — 0,60 tombe dans l'écart.
    consigne_score_floor: float = 0.60
    # Marge plus large que celle des cahiers : ici on cherche à en citer deux ou
    # trois quand plusieurs articles se répondent (I.2 et II.2, par exemple),
    # pas à isoler la meilleure séance.
    consigne_score_margin: float = 0.08
    # Poids du canal lexical dans le score final (dense + λ·lexical). MESURÉ :
    # 22 articles du même texte se ressemblent trop pour que le dense seul les
    # départage — 6 bons articles en tête sur 12 sans lui, 9 avec. λ a un plateau
    # entre 0,10 et 0,30, ce qui veut dire que le résultat ne tient pas à un
    # réglage fin sur douze questions.
    consigne_lexical_weight: float = 0.20

    # Rôles admis sur les routes du cahier de séance. Le périmètre réel (ses
    # propres séances ou tout l'établissement) est décidé par le BACKEND à partir
    # du compte : ce n'est pas une information que l'assistant choisit.
    cahier_roles: str = "TEACHER,SCHOOL_ADMIN"

    # Keycloak
    keycloak_url: str = "http://localhost:8081"
    keycloak_realm: str = "smartschool"
    # Émetteur attendu dans le claim `iss`. Vide → dérivé de keycloak_url.
    # À renseigner uniquement en conteneur : keycloak_url y vaut
    # http://keycloak:8081 (réseau Docker) alors que le token émis porte
    # iss=http://localhost:8081/realms/... (l'URL vue par le navigateur).
    keycloak_issuer_url: str = ""
    required_role: str = "PLATFORM_SUPER_ADMIN"

    # Rôles admis sur les routes Planning. Le super admin plateforme y a accès
    # pour le support, mais l'usage normal est celui du directeur d'établissement.
    planning_roles: str = "SCHOOL_ADMIN,PLATFORM_SUPER_ADMIN"

    auth_enabled: bool = True

    # CORS
    cors_origins: str = "http://localhost:5173,http://localhost:3000"

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def planning_roles_list(self) -> list[str]:
        return [r.strip() for r in self.planning_roles.split(",") if r.strip()]

    @property
    def cahier_roles_list(self) -> list[str]:
        return [r.strip() for r in self.cahier_roles.split(",") if r.strip()]

    @property
    def keycloak_issuer(self) -> str:
        """L'émetteur attendu dans le claim `iss` du JWT."""
        if self.keycloak_issuer_url:
            return self.keycloak_issuer_url.rstrip("/")
        return f"{self.keycloak_url}/realms/{self.keycloak_realm}"

    @property
    def keycloak_jwks_url(self) -> str:
        """
        URL des clés publiques du realm, pour vérifier la signature.

        Construite depuis keycloak_url (adresse RÉSEAU joignable par le service)
        et non depuis l'issuer (identité logique inscrite dans le token) :
        les deux diffèrent dès qu'on tourne en conteneur.
        """
        return (
            f"{self.keycloak_url}/realms/{self.keycloak_realm}"
            "/protocol/openid-connect/certs"
        )


@lru_cache
def get_settings() -> Settings:
    """
    Mis en cache : la configuration est lue une seule fois au démarrage.
    lru_cache la rend aussi injectable comme dépendance FastAPI.
    """
    return Settings()
