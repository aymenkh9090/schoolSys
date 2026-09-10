"""
Catalogue des requêtes PromQL et calcul des verdicts.

RÈGLE ABSOLUE : les requêtes sont figées ici, dans des constantes.
Le modèle de langage ne compose JAMAIS de PromQL — il choisit un outil
parmi une liste, rien de plus. Voir §10.
"""

import logging

from app.clients.actuator import ActuatorClient
from app.clients.prometheus import PrometheusClient
from app.models import HealthSnapshot, ResourceForecast
from app.services.prevision import prevoir

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# Catalogue PromQL
# ─────────────────────────────────────────────────────────────────────────────

# ── Mémoire JVM ──────────────────────────────────────────────────────────────
HEAP_USED = 'sum(jvm_memory_used_bytes{area="heap"})'
HEAP_MAX = 'sum(jvm_memory_max_bytes{area="heap"})'

# ── CPU ──────────────────────────────────────────────────────────────────────
# process_cpu_usage = charge CPU du processus JVM (0.0 à 1.0).
# system_cpu_usage serait la charge de toute la machine — moins pertinent
# ici, car on veut savoir si c'est NOTRE application qui consomme.
CPU_USAGE = "process_cpu_usage"

# ── HTTP ─────────────────────────────────────────────────────────────────────
# p95 : 95 % des requêtes sont plus rapides que cette valeur.
# Bien meilleur indicateur que la moyenne, qui masque totalement les pics.
# `{window}` est substitué par du code Python, JAMAIS par le LLM.
LATENCY_P95 = (
    "histogram_quantile(0.95, "
    "sum by (le) (rate(http_server_requests_seconds_bucket[{window}])))"
)

# Débit en requêtes par seconde.
THROUGHPUT = "sum(rate(http_server_requests_seconds_count[{window}]))"

# Taux d'erreur 5xx. `or vector(0)` est indispensable : sans erreur du tout,
# la partie gauche ne renvoie AUCUNE série, et la division donne un résultat
# vide — que l'on interpréterait à tort comme « pas de donnée ».
ERROR_RATE = (
    '(sum(rate(http_server_requests_seconds_count{{status=~"5.."}}[{window}])) '
    "or vector(0)) "
    "/ sum(rate(http_server_requests_seconds_count[{window}]))"
)

# Les 5 endpoints les plus lents, en latence moyenne.
SLOWEST_ENDPOINTS = (
    "topk(5, sum by (uri) (rate(http_server_requests_seconds_sum[{window}])) "
    "/ sum by (uri) (rate(http_server_requests_seconds_count[{window}])))"
)

# Les endpoints qui produisent le plus d'erreurs 5xx.
TOP_ERROR_ENDPOINTS = (
    'topk(5, sum by (uri) '
    '(rate(http_server_requests_seconds_count{{status=~"5.."}}[{window}])))'
)

# ── Historique (tendance sur une fenêtre) ────────────────────────────────────
# Ces expressions sont évaluées via des sous-requêtes `(expr)[fenêtre:pas]`,
# ce qui permet min/avg/max SANS rapatrier la série complète : trois nombres
# au lieu de plusieurs centaines de points. Voir §10.5.
HISTORY_METRICS: dict[str, tuple[str, str, str]] = {
    # clé LLM : (libellé lisible, unité, expression PromQL instantanée)
    "memory": (
        "Heap memory",
        "%",
        '100 * sum(jvm_memory_used_bytes{area="heap"}) '
        '/ sum(jvm_memory_max_bytes{area="heap"})',
    ),
    "cpu": ("CPU load", "%", "100 * process_cpu_usage"),
    "latency": (
        "HTTP p95 latency",
        " ms",
        "1000 * histogram_quantile(0.95, "
        "sum by (le) (rate(http_server_requests_seconds_bucket[5m])))",
    ),
    "errors": (
        "HTTP error rate",
        "%",
        '100 * ((sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) '
        "or vector(0)) / sum(rate(http_server_requests_seconds_count[5m])))",
    ),
    "throughput": (
        "Throughput",
        " req/s",
        "sum(rate(http_server_requests_seconds_count[5m]))",
    ),
}

# Pas d'échantillonnage de la sous-requête, choisi par fenêtre.
# Trop fin sur 24 h ferait évaluer 1 440 points pour trois chiffres.
SUBQUERY_STEP = {"5m": "15s", "1h": "1m", "24h": "5m"}

# Durée de chaque fenêtre, pour la requête de plage.
WINDOW_MINUTES = {"5m": 5, "1h": 60, "24h": 24 * 60}

# Points d'une courbe de tendance.
#
# Volontairement peu : une vignette de deux centimètres ne dit pas « voici la
# valeur à 14 h 07 », elle dit « ça monte » ou « c'est plat ». Douze points
# rendent cette forme-là ; deux cents la rendraient illisible et coûteraient une
# évaluation cent fois plus lourde à Prometheus pour le même message.
TREND_POINTS = 12


# ── Prévision (régression linéaire) ──────────────────────────────────────────
# Les séries régressées, et le seuil que chacune menace. Seules les ressources
# qui se consomment : latence et taux d'erreur ne se remplissent pas.
#
# ⚠️ LA MÉMOIRE RÉGRESSÉE N'EST PAS CELLE DE LA TUILE. La heap est une dent de
# scie : le ramasse-miettes la fait monter puis chuter en permanence, et une
# droite tirée à travers mesure le rythme du GC, pas la consommation (R² de 0,00
# à 0,34 mesuré sur la heap brute, le 10/09/2026). On régresse sur son
# PLANCHER — le minimum sur 5 min glissantes, soit la heap juste après un
# passage du GC. C'est ce plancher qui monte lors d'une fuite mémoire.
FORECAST_METRICS: dict[str, tuple[str, str, str]] = {
    # clé de la tuile : (série régressée, clé de THRESHOLDS, expression PromQL)
    "memory": (
        "Heap memory floor (5 min minimum)",
        "heap_percent",
        f"min_over_time(({HISTORY_METRICS['memory'][2]})[5m:15s])",
    ),
    "cpu": ("CPU load", "cpu_percent", HISTORY_METRICS["cpu"][2]),
}

# Points d'une régression : un par minute sur une heure. Bien plus que les
# douze d'une courbe — la vignette montre une forme, la régression mesure une
# pente, et la précision d'une pente croît avec le nombre de points.
FORECAST_POINTS = 60


# ── Base de données ──────────────────────────────────────────────────────────
# `pending` est l'indicateur le PLUS PRÉDICTIF d'une saturation :
# il monte avant que la latence n'explose. Un pool saturé fait
# attendre les threads, ce qui se traduit ensuite en latence HTTP.
DB_ACTIVE = "hikaricp_connections_active"
DB_PENDING = "hikaricp_connections_pending"
DB_MAX = "hikaricp_connections_max"


# ── Métriques métier, par établissement ──────────────────────────────────────
# Publiées par SchoolMetricsPublisher côté Spring Boot. Elles portent deux
# labels : `tenant` (identifiant stable) et `school` (code lisible).
#
# ⚠️ `{school}` est substitué par du code Python avec une valeur RÉSOLUE au
# préalable contre la liste réelle des établissements — jamais avec la chaîne
# brute reçue du modèle. Sans cette résolution, un nom d'école inventé par le
# LLM se retrouverait injecté dans un sélecteur PromQL.

# Liste des établissements connus : sert aussi bien à répondre « quelles écoles
# existent ? » qu'à résoudre un nom approximatif donné par l'utilisateur.
SCHOOL_LIST = "smartschool_school_info"

SCHOOL_USERS_BY_ROLE = 'sum by (role) (smartschool_school_users{{school="{school}"}})'
SCHOOL_STUDENTS = 'smartschool_school_students{{school="{school}"}}'
SCHOOL_CLASSES = 'smartschool_school_classes{{school="{school}"}}'
SCHOOL_TEACHERS = 'smartschool_school_teachers{{school="{school}"}}'

SCHOOL_PLANNING_BY_STATUS = (
    'sum by (status) (smartschool_planning_jobs{{school="{school}"}})'
)
SCHOOL_LAST_GENERATION = (
    'smartschool_planning_last_generation_seconds{{school="{school}"}}'
)


# ─────────────────────────────────────────────────────────────────────────────
# Seuils
# ─────────────────────────────────────────────────────────────────────────────
# Point de départ raisonnable, à calibrer avec l'expérience terrain.
# Les centraliser ici évite d'avoir des seuils dispersés et contradictoires.

THRESHOLDS = {
    "heap_percent":    {"warning": 75.0,  "critical": 90.0},
    "cpu_percent":     {"warning": 70.0,  "critical": 85.0},
    "latency_p95_ms":  {"warning": 1000.0, "critical": 3000.0},
    "error_rate_pct":  {"warning": 1.0,   "critical": 5.0},
    "db_pending":      {"warning": 1.0,   "critical": 5.0},
}


class MetricsService:
    def __init__(self, prometheus: PrometheusClient, actuator: ActuatorClient):
        self._prom = prometheus
        self._actuator = actuator

    # ── Tendances ───────────────────────────────────────────────────────────

    async def get_trends(self, window: str = "1h") -> dict[str, list[float]]:
        """
        La forme récente de chaque mesure — d'où l'on vient, pas où l'on en est.

        Réutilise les expressions de `HISTORY_METRICS`, déjà normalisées dans
        l'unité d'affichage (pourcentage, millisecondes) et déjà employées pour
        les min/avg/max. Écrire une seconde version des mêmes requêtes finirait
        par produire une courbe qui contredit le chiffre posé au-dessus d'elle.

        Une métrique sans donnée est **absente** du dictionnaire plutôt que
        présente et vide : l'interface n'affiche alors aucune courbe, au lieu
        d'une ligne plate qui se lirait comme une mesure stable.
        """
        minutes = WINDOW_MINUTES.get(window, 60)
        pas = max(15, minutes * 60 // TREND_POINTS)

        tendances: dict[str, list[float]] = {}
        for cle, (_libelle, _unite, expression) in HISTORY_METRICS.items():
            points = await self._prom.query_range(expression, minutes, pas)
            if points:
                tendances[cle] = points
        return tendances

    # ── Prévision ───────────────────────────────────────────────────────────

    async def get_forecast(self, window: str = "1h") -> dict[str, ResourceForecast]:
        """
        Où va chaque ressource — par régression sur l'historique de la fenêtre.

        Le calcul est dans `services/prevision.py` ; ici, on lit la série et on
        nomme ce qui a été régressé. Prometheus ne prédit rien, ce service ne
        stocke rien : la droite est recalculée à chaque demande.
        """
        minutes = WINDOW_MINUTES.get(window, 60)
        pas = max(15, minutes * 60 // FORECAST_POINTS)
        # Ce que Prometheus rend sans aucun trou : un point à chaque pas, bornes comprises.
        attendus = minutes * 60 // pas + 1

        previsions: dict[str, ResourceForecast] = {}
        for cle, (serie, cle_seuil, expression) in FORECAST_METRICS.items():
            seuils = THRESHOLDS[cle_seuil]
            points = await self._prom.query_range_points(expression, minutes, pas)
            p = prevoir(points, seuils, attendus)
            previsions[cle] = ResourceForecast(
                verdict=p.verdict,
                series=serie,
                unit="%",
                warning_threshold=seuils["warning"],
                critical_threshold=seuils["critical"],
                points=p.n,
                r2=p.r2,
                slope_per_hour=p.pente_par_heure,
                current=p.valeur_actuelle,
                at_horizon=p.valeur_horizon,
                horizon_minutes=p.horizon_min,
                minutes_to_warning=p.minutes_avant_alerte,
                minutes_to_critical=p.minutes_avant_incident,
                # Les points mêmes qui ont servi au calcul : le graphique trace
                # ce qui a été régressé, pas une série voisine.
                history=points,
            )
        return previsions

    # ── Instantané global ───────────────────────────────────────────────────

    async def get_snapshot(self, window: str = "5m") -> HealthSnapshot:
        """
        Agrège Actuator + Prometheus en un seul objet.

        C'est l'appel principal : il répond à « comment va la plateforme ? »
        et sert de base à la plupart des outils du LLM.
        """
        health = await self._actuator.health()
        app_status = health.get("status", "UNKNOWN")
        db_status = (
            health.get("components", {}).get("db", {}).get("status", "UNKNOWN")
        )

        heap_used = await self._prom.query_scalar(HEAP_USED)
        heap_max = await self._prom.query_scalar(HEAP_MAX)
        cpu = await self._prom.query_scalar(CPU_USAGE)
        p95 = await self._prom.query_scalar(LATENCY_P95.format(window=window))
        error_rate = await self._prom.query_scalar(ERROR_RATE.format(window=window))
        throughput = await self._prom.query_scalar(THROUGHPUT.format(window=window))
        db_active = await self._prom.query_scalar(DB_ACTIVE)
        db_pending = await self._prom.query_scalar(DB_PENDING)

        heap_percent = (
            (heap_used / heap_max * 100)
            if heap_used is not None and heap_max not in (None, 0)
            else None
        )

        snapshot = HealthSnapshot(
            status="UNKNOWN",
            app_status=app_status,
            db_status=db_status,
            # Prometheus renvoie des octets → mégaoctets
            heap_used_mb=heap_used / 1_048_576 if heap_used is not None else None,
            heap_max_mb=heap_max / 1_048_576 if heap_max is not None else None,
            heap_percent=heap_percent,
            # process_cpu_usage est un ratio 0-1 → pourcentage
            cpu_percent=cpu * 100 if cpu is not None else None,
            # Prometheus renvoie des secondes → millisecondes
            latency_p95_ms=p95 * 1000 if p95 is not None else None,
            error_rate_percent=error_rate * 100 if error_rate is not None else None,
            requests_per_second=throughput,
            db_connections_active=db_active,
            db_connections_pending=db_pending,
        )

        snapshot.status, snapshot.issues = self._evaluate(snapshot)
        return snapshot

    # ── Évaluation ──────────────────────────────────────────────────────────

    def _evaluate(self, s: HealthSnapshot) -> tuple[str, list[str]]:
        """
        Détermine le statut global et la liste des anomalies.

        Cette logique est en Python, PAS dans le prompt du LLM. Raisons :
         - elle est déterministe et testable unitairement,
         - un modèle 3B est incapable de comparer des seuils de façon fiable,
         - le LLM reçoit ainsi un verdict déjà calculé, et se contente de
           l'expliquer — ce qu'il fait bien.
        """
        issues: list[str] = []
        level = 0  # 0=HEALTHY, 1=WARNING, 2=CRITICAL

        def check(value: float | None, key: str, label: str, unit: str) -> None:
            nonlocal level
            if value is None:
                return
            t = THRESHOLDS[key]
            if value >= t["critical"]:
                issues.append(f"{label} critique : {value:.1f}{unit}")
                level = max(level, 2)
            elif value >= t["warning"]:
                issues.append(f"{label} élevé : {value:.1f}{unit}")
                level = max(level, 1)

        # L'application injoignable prime sur tout le reste.
        if s.app_status == "DOWN":
            return "CRITICAL", ["L'application est DOWN"]
        if s.db_status == "DOWN":
            return "CRITICAL", ["La base de données est injoignable"]

        check(s.heap_percent, "heap_percent", "Mémoire heap", " %")
        check(s.cpu_percent, "cpu_percent", "CPU", " %")
        check(s.latency_p95_ms, "latency_p95_ms", "Latence p95", " ms")
        check(s.error_rate_percent, "error_rate_pct", "Taux d'erreur", " %")
        check(s.db_connections_pending, "db_pending", "Connexions DB en attente", "")

        # Aucune donnée du tout : ne PAS annoncer « tout va bien ».
        if s.heap_percent is None and s.latency_p95_ms is None:
            return "UNKNOWN", ["Aucune métrique disponible (Prometheus injoignable ?)"]

        return ["HEALTHY", "WARNING", "CRITICAL"][level], issues

    # ── Requêtes ciblées ────────────────────────────────────────────────────

    async def get_slowest_endpoints(self, window: str = "1h") -> dict[str, float]:
        """{uri: latence_moyenne_en_ms}, les 5 plus lents."""
        raw = await self._prom.query_by_label(
            SLOWEST_ENDPOINTS.format(window=window), "uri"
        )
        return {uri: seconds * 1000 for uri, seconds in raw.items()}

    async def get_top_error_endpoints(self, window: str = "1h") -> dict[str, float]:
        """{uri: erreurs_par_seconde}, les 5 plus touchés."""
        return await self._prom.query_by_label(
            TOP_ERROR_ENDPOINTS.format(window=window), "uri"
        )

    # ── Métier : par établissement ──────────────────────────────────────────

    async def list_schools(self) -> list[str]:
        """
        Codes des établissements présents dans les métriques.

        Liste vide = les métriques métier ne sont pas publiées (backend d'une
        version antérieure, ou relevé désactivé). L'appelant doit distinguer ce
        cas de « l'établissement demandé n'existe pas » : les deux se ressemblent
        et n'appellent pas du tout la même réponse.
        """
        found = await self._prom.query_by_label(SCHOOL_LIST, "school")
        return sorted(found)

    async def get_school_metrics(self, school: str) -> dict:
        """
        Instantané métier d'un établissement.

        `school` doit être un code EXACT, déjà résolu contre list_schools().
        """
        users = await self._prom.query_by_label(
            SCHOOL_USERS_BY_ROLE.format(school=school), "role"
        )
        jobs = await self._prom.query_by_label(
            SCHOOL_PLANNING_BY_STATUS.format(school=school), "status"
        )
        return {
            "school": school,
            "users_by_role": users,
            "users_total": sum(users.values()) if users else None,
            "students": await self._prom.query_scalar(
                SCHOOL_STUDENTS.format(school=school)
            ),
            "classes": await self._prom.query_scalar(
                SCHOOL_CLASSES.format(school=school)
            ),
            "teachers": await self._prom.query_scalar(
                SCHOOL_TEACHERS.format(school=school)
            ),
            "planning_by_status": jobs,
            "planning_total": sum(jobs.values()) if jobs else None,
            "last_generation_seconds": await self._prom.query_scalar(
                SCHOOL_LAST_GENERATION.format(school=school)
            ),
        }

    async def get_trend(self, metric: str, window: str = "1h") -> dict | None:
        """
        Min / moyenne / max / valeur courante d'une métrique sur une fenêtre.

        Répond aux questions d'historique (« la mémoire monte-t-elle ? »)
        sans jamais renvoyer de série temporelle au modèle : quatre nombres
        et un mot de tendance suffisent à répondre, et tiennent en 30 tokens.

        Renvoie None si la métrique est inconnue ou sans donnée.
        """
        entry = HISTORY_METRICS.get(metric)
        if entry is None:
            return None
        label, unit, expr = entry
        step = SUBQUERY_STEP.get(window, "1m")
        sub = f"({expr})[{window}:{step}]"

        minimum = await self._prom.query_scalar(f"min_over_time({sub})")
        average = await self._prom.query_scalar(f"avg_over_time({sub})")
        maximum = await self._prom.query_scalar(f"max_over_time({sub})")
        current = await self._prom.query_scalar(expr)

        if average is None and current is None:
            return None

        # Tendance déduite de l'écart à la moyenne : pas de régression
        # linéaire, inutile ici et impossible à expliquer à l'utilisateur.
        direction = "stable"
        if average not in (None, 0) and current is not None:
            if current > average * 1.15:
                direction = "rising"
            elif current < average * 0.85:
                direction = "falling"

        return {
            "label": label,
            "unit": unit,
            "min": minimum,
            "avg": average,
            "max": maximum,
            "current": current,
            "direction": direction,
        }