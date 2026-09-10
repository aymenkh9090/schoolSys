from typing import Literal
from pydantic import BaseModel, Field

# Fenêtres temporelles autorisées.
# Literal les impose au niveau du type ET les documente dans OpenAPI.
TimeWindow = Literal["5m", "1h", "24h"]

class ChatRequest(BaseModel):
    """Question posée par le super admin."""

    # max_length=500 : borne l'entrée. Un message de 10 000 caractères
    # ferait exploser le temps de génération sur CPU et pourrait dépasser
    # la fenêtre de contexte du modèle.

    message: str = Field(...,min_length=1,max_length=500)

class ChatResponse(BaseModel):

    answer: str

     # Les outils réellement appelés. Exposés à l'UI pour la transparence :
    # l'utilisateur voit sur quelles données la réponse s'appuie.

    tools_used: list[str] = []
    duration_ms: int

class HealthSnapshot(BaseModel):

    """Instantané de l'etat de la platforme, agrégé depuis toutes les sources."""

    status: Literal["HEALTHY","WARNING","CRITICAL","UNKNOWN"]

    #Actuator
    app_status: str   # UP/ DOWN/ UNKNOWN
    db_status: str

    # Prometheus — None quand la métrique est absente.
    # On distingue explicitement « zéro » de « pas de donnée » : afficher 0 %
    # d'erreurs alors que Prometheus est injoignable serait un mensonge.

    heap_used_mb: float | None = None
    heap_max_mb: float | None = None
    heap_percent: float | None = None
    cpu_percent: float | None = None
    latency_p95_ms: float | None = None
    error_rate_percent: float | None = None
    requests_per_second: float | None = None
    db_connections_active: float | None = None
    db_connections_pending: float | None = None


    # Messages lisibles expliqueant le statut ("Latence p95 élevée : 4200 ms")
    issues: list[str] = []


class MetricTrends(BaseModel):
    """
    La forme récente des mesures, pour la vignette de tendance des tuiles KPI.

    Un chiffre seul ne dit pas s'il monte. « 78 % de mémoire » se lit tout
    autrement selon qu'on venait de 40 % ou de 82 % : dans le premier cas
    quelque chose se passe, dans le second cela redescend.

    Les points sont dans l'unité d'affichage de la tuile — pourcentage,
    millisecondes — et non dans celle de Prometheus : la courbe et le chiffre
    posé au-dessus d'elle doivent parler de la même chose.

    Une métrique sans donnée est **absente** de `trends`, jamais présente avec
    une liste vide. L'interface n'affiche alors aucune courbe, au lieu d'une
    ligne plate qui se lirait comme une mesure stable.
    """

    window: TimeWindow

    # Clés : memory, cpu, latency, errors, throughput — celles de HISTORY_METRICS.
    trends: dict[str, list[float]] = {}


class ResourceForecast(BaseModel):
    """
    Où va une ressource, et quand elle touchera son seuil — par régression linéaire.

    Le verdict gouverne la lecture de tout le reste :
      - `hausse`      : seuil d'alerte atteint avant l'horizon, durées renseignées ;
      - `stable`      : aucun seuil atteint avant l'horizon ;
      - `baisse`      : la ressource se libère ;
      - `incertain`   : la droite n'explique pas la série, aucune échéance ;
      - `insuffisant` : trop peu d'historique pour tracer une droite.

    Les durées ne sont JAMAIS renseignées hors `hausse` : une échéance que les
    données ne soutiennent pas fait intervenir pour rien, puis ignorer la
    suivante.

    ⚠️ `series` nomme ce qui a été régressé, qui n'est pas toujours ce que la
    tuile affiche : pour la mémoire, c'est le plancher de la heap après passage
    du ramasse-miettes, et non la heap brute.
    """

    verdict: Literal["insuffisant", "incertain", "hausse", "stable", "baisse"]
    series: str
    unit: str
    warning_threshold: float
    critical_threshold: float

    points: int
    # Part de la variation expliquée par la droite, de 0 (bruit) à 1 (droite).
    r2: float | None = None
    slope_per_hour: float | None = None
    # Valeur de la droite maintenant, puis au bout de l'horizon. `at_horizon`
    # n'est rendu que si la droite explique la série (R² ≥ 0,5) : sa présence
    # est ce qui autorise l'écran à prolonger la courbe en pointillé.
    current: float | None = None
    at_horizon: float | None = None
    # La durée observée : on ne projette jamais plus loin qu'on a regardé.
    horizon_minutes: float | None = None
    # 0 = déjà franchi ; None = pas atteint avant l'horizon, ou verdict sans échéance.
    minutes_to_warning: float | None = None
    minutes_to_critical: float | None = None


class ResourceForecasts(BaseModel):
    """
    Les prévisions des ressources qui se consomment : mémoire et CPU.

    Les clés sont celles des tuiles (`memory`, `cpu`), pour que l'écran pose
    chaque prévision sous la bonne mesure. Chaque ressource est toujours
    présente : sans historique, son verdict dit `insuffisant`, plutôt qu'une
    absence qu'on pourrait lire comme un oubli.
    """

    window: TimeWindow
    forecasts: dict[str, ResourceForecast] = {}


# ─────────────────────────────────────────────────────────────────────────────
# Module Planning
# ─────────────────────────────────────────────────────────────────────────────

class CitedSession(BaseModel):
    """
    Une séance que l'assistant désigne — pas qu'il décrit.

    Tous les champs viennent du backend, recopiés tels quels. Le modèle n'y
    touche pas : il n'a jamais vu cette structure, et c'est délibéré (voir
    `CitedConflict`).
    """

    session_id: int | None = None
    lesson_id: int | None = None
    subject_name: str | None = None
    class_name: str | None = None
    teacher_name: str | None = None
    room_code: str | None = None
    day: str | None = None
    start_time: str | None = None
    # 0 = classe entière, 1 = demi-groupe A, 2 = demi-groupe B.
    group_index: int = 0


class CitedRelocation(BaseModel):
    """
    Un déplacement possible, calculé par le solveur — jamais rédigé par le modèle.

    Le créneau d'arrivée a été vérifié libre pour l'enseignant, pour la classe,
    et une salle du bon type y est disponible. `session_id` est la cible du
    PATCH que l'interface déclenchera si le directeur accepte : rien ici n'est
    appliqué, c'est une proposition.
    """

    session_id: int | None = None
    to_day: str | None = None
    to_start_time: str | None = None
    to_slot_id: int | None = None
    to_room_code: str | None = None
    # La phrase construite côté Java, affichée telle quelle. Trois consommateurs
    # la reformuleraient autrement, et une même proposition dite de trois façons
    # devient trois propositions aux yeux de qui la lit.
    text: str


class CitedConflict(BaseModel):
    """
    Un conflit désigné : la règle enfreinte, les séances en cause, le remède.

    **Cette structure n'entre jamais dans le prompt.** Un modèle de 7 milliards
    de paramètres qui navigue dans un objet imbriqué se trompe de champ et
    annonce un score qui n'existe pas — c'est la raison d'être du texte
    pré-interprété que les handlers rendent au modèle. Elle est remplie par le
    code Python, en marge de la boucle d'outils, à partir de la réponse du
    backend.

    Le modèle raconte ; le code désigne.
    """

    # Le libellé lisible de la contrainte, ex. « Conflit d'enseignant ».
    constraint: str
    # HARD, MEDIUM ou SOFT — seul le premier rend un planning irremettable.
    severity: str
    # La phrase de l'occurrence, celle que le solveur produit.
    label: str
    sessions: list[CitedSession] = []
    # Absent quand aucun créneau ne convient : fréquent sur un établissement
    # saturé, et c'est une information en soi — le conflit ne se règle pas en
    # déplaçant une case.
    relocation: CitedRelocation | None = None


class PlanningChatRequest(BaseModel):
    """Question sur l'emploi du temps de SON établissement."""

    message: str = Field(..., min_length=1, max_length=500)

    # Le tenant n'est PAS un champ : il vient du jeton. L'exposer ici
    # permettrait à un utilisateur de demander le planning d'une autre école.
    school_year_id: int | None = None
    profile_id: int | None = None


class ConstraintProposalRequest(BaseModel):
    """Demande de règle exprimée en français."""

    # 500 caractères : une règle d'emploi du temps tient en une phrase. Au-delà,
    # c'est un cahier des charges, et le modèle en extrait n'importe quoi.
    message: str = Field(..., min_length=1, max_length=500)

    school_year_id: int | None = None
    profile_id: int | None = None


class ConstraintProposalResponse(BaseModel):
    """
    Ce que l'assistant propose — jamais ce qu'il a fait.

    `dsl` est la règle exacte qui sera enregistrée si l'utilisateur confirme :
    l'interface l'affiche, et c'est elle qui est renvoyée telle quelle à la
    confirmation. Aucune reformulation entre l'écran et la base.
    """

    valid: bool
    dsl: dict | None = None

    # Résumé produit par le COMPILATEUR Java, pas par le modèle : ce qui est
    # montré est ce que le moteur appliquera.
    summary: str | None = None
    verdict: str | None = None
    message: str = ""

    errors: list[str] = []
    warnings: list[str] = []

    feasible: bool = True
    conflicts: list[dict] = []

    matched_lessons: int | None = None
    total_lessons: int | None = None
    examples: list[str] = []

    attempts: int = 0
    duration_ms: int = 0

    # Articles de la circulaire n°66/2024 sur lesquels la proposition s'appuie.
    # Chaque entrée porte {id, page, citation, extrait, extrait_ar, portee,
    # severite, concordance}. `concordance` distingue l'article dont la portée
    # est bien celle de la règle produite de ceux qui ne font que voisiner :
    # l'interface doit écrire « cette règle correspond au § II.2 » dans le
    # premier cas seulement.
    #
    # Liste vide = aucun article ne couvre cette demande. Ce n'est pas un échec,
    # c'est le cas normal d'une règle propre à l'établissement, et l'interface
    # gagne à le dire plutôt qu'à laisser croire à un fondement réglementaire.
    sources: list[dict] = []

    # Toujours vrai tant que l'utilisateur n'a pas confirmé. Rendu explicite
    # dans la réponse pour que l'interface ne puisse pas se tromper d'état.
    requires_confirmation: bool = True


class ConstraintConfirmRequest(BaseModel):
    """
    Confirmation explicite : l'utilisateur a vu la règle et l'accepte.

    Le DSL est renvoyé par le client plutôt que conservé côté serveur entre les
    deux appels. C'est délibéré : ce qui est enregistré est exactement ce qui a
    été affiché, et le service reste sans état — deux propriétés qu'une session
    intermédiaire ferait perdre toutes les deux.
    """

    constraint_profile_id: int
    code: str = Field(..., min_length=1, max_length=100)
    name: str = Field(..., min_length=1, max_length=180)
    description: str | None = None
    dsl: dict
    natural_language_request: str | None = None


# ─────────────────────────────────────────────────────────────────────────────
# Circulaire ministérielle
# ─────────────────────────────────────────────────────────────────────────────

class ConsigneArticle(BaseModel):
    """
    Un article de la circulaire n°66/2024, tel que l'écran le montre.

    `texte` est la traduction de l'article — c'est lui qui s'affiche en grand et
    lui seul qui est vectorisé. `commentaire` est NOTRE lecture, celle qui
    explique le rattachement au solveur : l'écran doit la présenter comme telle
    et jamais comme une parole du ministère.
    """

    id: str
    page: int
    section: str
    texte: str
    texte_ar: str
    commentaire: str

    # Non nuls seulement pour les articles qui se traduisent en contrainte de
    # solveur. Les tableaux de volumes horaires, la légende et les articles
    # d'affectation restent consultables mais ne proposent aucune règle : leur
    # « activer » n'aurait rien à activer.
    portee: str | None = None
    severite: str | None = None

    citation: str


class ConsigneChatRequest(BaseModel):
    """
    Question libre sur la circulaire.

    Aucun identifiant d'établissement : la circulaire est un texte national,
    identique pour tous. Un champ de périmètre laisserait croire à un
    cloisonnement qui n'existe pas et n'aurait rien à filtrer.
    """

    message: str = Field(..., min_length=1, max_length=500)


class ConsigneChatResponse(BaseModel):
    """
    Une réponse rédigée, et les articles sur lesquels elle s'appuie.

    Les sources ne sont pas décoratives : elles sont ce qui permet à
    l'utilisateur de ne pas croire le modèle sur parole. Elles sont rendues même
    quand la rédaction échoue — le RAG a fait son travail, et un extrait cité
    vaut mieux qu'un message d'erreur seul.

    Pas de champ `tools_used` comme dans `ChatResponse` : ce service n'a pas
    d'outils. La recherche a toujours lieu, avant la génération, et il n'y a
    donc rien à déclarer que `sources` ne dise déjà mieux.
    """

    answer: str
    sources: list[ConsigneArticle] = []
    duration_ms: int


class ConsigneArticlesResponse(BaseModel):
    """
    Le corpus entier, en une fois.

    Vingt-deux articles tiennent dans une seule réponse : paginer un texte
    réglementaire que l'utilisateur veut parcourir de bout en bout coûterait des
    allers-retours sans rien économiser.
    """

    reference: str
    articles: list[ConsigneArticle]


# ─────────────────────────────────────────────────────────────────────────────
# Cahier de séance
# ─────────────────────────────────────────────────────────────────────────────

class CahierChatRequest(BaseModel):
    """
    Question sur le cahier de séance.

    Aucun identifiant d'enseignant ni d'établissement : le périmètre interrogé
    découle du jeton, et il n'existe donc pas de champ par lequel demander les
    séances de quelqu'un d'autre.
    """

    message: str = Field(..., min_length=1, max_length=500)


# ── Cours déposé et conversation autour de ce cours ─────────────────────────


class DocumentDepose(BaseModel):
    """
    Accusé de dépôt : ce que le service a réellement lu du document.

    `caracteres_lus` et `tronque` ne sont pas décoratifs — ils disent à
    l'enseignant que la fin de son chapitre n'entrera pas dans les réponses.
    Une troncature silencieuse serait un mensonge par omission.
    """

    document_id: str
    nom_fichier: str
    pages: int
    caracteres_lus: int
    tronque: bool


class CoursChatMessage(BaseModel):
    """Un tour de la conversation, renvoyé par le client pour le contexte."""

    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1, max_length=4000)


class CoursChatRequest(BaseModel):
    """Demande de l'enseignant à propos d'un cours qu'il a déposé."""

    document_id: str = Field(..., min_length=1, max_length=64)
    # Plus long que les 500 caractères des autres assistants : « fais un QCM de
    # 10 questions sur la partie 3, avec le corrigé, niveau 8e » tient large,
    # mais une consigne pédagogique détaillée peut être longue.
    message: str = Field(..., min_length=1, max_length=1000)
    historique: list[CoursChatMessage] = []


class PlanningChatResponse(ChatResponse):
    """
    La réponse du chat planning : la phrase du modèle, plus ce que le code désigne.

    Distincte de `ChatResponse` plutôt qu'un champ de plus dessus : l'assistant
    de supervision partage ce modèle et n'a aucun conflit d'emploi du temps à
    porter. Un champ toujours vide chez l'un des deux consommateurs finit par
    être lu comme un oubli.
    """

    conflicts: list[CitedConflict] = []
