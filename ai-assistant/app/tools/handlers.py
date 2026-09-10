"""
Implémentations des outils.

Chaque fonction renvoie une CHAÎNE COURTE ET DÉJÀ INTERPRÉTÉE, destinée à
être relue par le modèle. Jamais de JSON brut, jamais de série temporelle.
"""

import logging
import unicodedata

from app.models import ResourceForecast
from app.services.metrics import MetricsService
from app.services.prevision import COUVERTURE_MIN, R2_MIN

logger = logging.getLogger(__name__)

ALLOWED_WINDOWS = {"5m", "1h", "24h"}

ALLOWED_METRICS = {"memory", "cpu", "latency", "errors", "throughput"}

# Distinguer « pas de métriques métier publiées » de « école introuvable » :
# le premier cas se corrige côté serveur, le second côté question.
_NO_SCHOOL_METRICS = (
    "Per-school business metrics are not available. The backend may be running "
    "a version that does not publish them yet, or the collector is disabled. "
    "Tell the user the data is not published, do NOT answer with a number."
)



def _sanitize_window(window: str | None) -> str:
    """
    Le modèle enverra tôt ou tard "30 minutes", "last hour" ou None.
    On ne fait jamais confiance à un argument venant du LLM : on valide,
    on replie sur une valeur sûre, et on trace pour pouvoir ajuster le prompt.
    """
    if window not in ALLOWED_WINDOWS:
        logger.info("Fenêtre invalide du LLM : %r → repli sur 5m", window)
        return "5m"
    return window


def _sanitize_metric(metric: str | None) -> str:
    """Même principe que pour les fenêtres : on ne fait jamais confiance au LLM."""
    if metric not in ALLOWED_METRICS:
        logger.info("Métrique invalide du LLM : %r → repli sur memory", metric)
        return "memory"
    return metric


def _normalize_school(value: str) -> str:
    """
    Réduit un nom d'établissement à ses lettres et chiffres, en minuscules.

    L'utilisateur écrit « l'école Ibn Khaldoun », le label vaut
    « IBN_KHALDOUN » : sans cette normalisation, aucune des deux formes ne
    rapproche l'une de l'autre. Accents, tirets, underscores et espaces sont
    tous supprimés, ce qui règle d'un coup « Ibn-Khaldoun », « ibn khaldoun »
    et « IBN_KHALDOUN ».
    """
    folded = unicodedata.normalize("NFD", value or "")
    stripped = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return "".join(c for c in stripped.lower() if c.isalnum())


def _match_school(requested: str, known: list[str]) -> str | None:
    """
    Résout le nom donné par le LLM en un code d'établissement RÉEL.

    C'est la contrepartie de _sanitize_window pour un paramètre qui ne peut pas
    être une énumération figée : la liste des établissements change en
    production. Le principe reste le même — aucune chaîne venue du modèle
    n'atteint une requête PromQL ; seule une valeur retrouvée dans la liste
    réelle y entre.

    Trois passes, de la plus stricte à la plus permissive : égalité, puis
    inclusion dans un sens, puis dans l'autre. « Ibn Khaldoun » doit trouver
    « IBN_KHALDOUN », et « college » doit trouver « college-mh ».
    """
    target = _normalize_school(requested)
    if not target:
        return None

    normalized = {code: _normalize_school(code) for code in known}

    for code, norm in normalized.items():
        if norm == target:
            return code
    for code, norm in normalized.items():
        if target in norm:
            return code
    for code, norm in normalized.items():
        if norm in target:
            return code
    return None


def _fmt(value: float | None, unit: str = "", digits: int = 1) -> str:
    """Formate une valeur, avec un repli explicite quand la donnée manque."""
    if value is None:
        return "unavailable"
    return f"{value:.{digits}f}{unit}"


# ── Prévision ───────────────────────────────────────────────────────────────

# Libellés vus par le modèle. Pour la mémoire, le libellé dit ce qui a été
# régressé : le plancher de la heap, pas la heap brute. Sans cette précision, le
# modèle attribuerait l'échéance au chiffre de la tuile — qui, lui, touche le
# seuil plus tôt, à chaque pic entre deux passages du ramasse-miettes.
_FORECAST_LABELS = {
    "memory": "Memory (heap remaining in use after garbage collection)",
    "cpu": "CPU load",
    # « Whole server » : sans cela, le modèle confond les deux CPU et répond
    # sur celui de l'API quand on l'interroge sur la machine.
    "system_cpu": "Host machine CPU load (whole server, including the AI model)",
    "disk": "Disk space used",
    "db_pool": "Database connection pool usage",
}


def _duration(minutes: float) -> str:
    """
    « 45 minutes », « 2 h 10 min ». Arrondi à cinq minutes au-delà d'une heure
    et demie : la pente est calculée sur une heure de points, elle ne vaut pas
    la minute près à deux heures de distance.

    Jamais « 130 minutes » : le modèle le recopie tel quel, et l'utilisateur
    fait la division.
    """
    arrondi = round(minutes)
    if arrondi < 90:
        return f"{arrondi} minutes"
    heures, reste = divmod(round(minutes / 5) * 5, 60)
    return f"{heures} hours" if reste == 0 else f"{heures} h {reste:02d} min"


def _in(minutes: float) -> str:
    return "in less than a minute" if minutes < 1 else f"in about {_duration(minutes)}"


def _next(minutes: float | None) -> str:
    """
    « the next hour » plutôt que « the next 60 minutes » : le modèle traduisait
    « 60 minutes » en « les prochaines heures ». Une heure se dit une heure.
    """
    if minutes is None or 55 <= minutes <= 65:
        return "the next hour"
    return f"the next {_duration(minutes)}"


def _describe_forecast(label: str, f: ResourceForecast) -> str:
    """
    Une prévision, en une phrase que le modèle n'a plus qu'à traduire.

    Le verdict est en MAJUSCULES en tête, comme la tendance de
    get_metric_history : c'est ce que le modèle reprend en premier. Pour les
    verdicts sans échéance, l'interdit est écrit en toutes lettres — un modèle
    qui lit « no reliable trend » et une pente produit volontiers une durée de
    lui-même, par une division qu'on vient justement de refuser de faire.
    """
    u = f.unit
    alerte = f"{f.warning_threshold:.0f}{u}"
    incident = f"{f.critical_threshold:.0f}{u}"
    horizon = _next(f.horizon_minutes)
    r2 = f"R² {f.r2:.2f}" if f.r2 is not None else ""

    if f.verdict == "insuffisant":
        # Ce qui manque est dit en clair : sans cela, le modèle invente un
        # nombre de mesures requis (« il faudrait au moins 30 mesures », observé),
        # puis conclut qu'il n'y a « pas de risque immédiat » — ce que rien ne dit.
        return (
            f"{label}: NOT ENOUGH HISTORY ({f.points} measurements over the last "
            "hour, the application has probably restarted recently). No forecast "
            f"is possible until at least {_duration(COUVERTURE_MIN * 60)} of "
            "history has been collected. This does NOT mean there is no risk: "
            "nothing is known yet. Do NOT give any time estimate."
        )

    if f.verdict == "incertain":
        # « Within reach » était lu « pourrait être atteint à tout moment » :
        # une alarme que la donnée ne porte pas davantage qu'une échéance.
        return (
            f"{label}: NO RELIABLE TREND ({r2}: the values fluctuate too much for "
            "a straight line to explain them). It is not possible to say whether "
            f"or when the warning threshold ({alerte}) will be reached. Do NOT "
            "give any time estimate; say that the trend is uncertain."
        )

    if f.verdict == "stable":
        if f.r2 is not None and f.r2 < R2_MIN:
            # Stable par la pente haute : pas de tendance lisible, mais le seuil
            # est hors d'atteinte quoi qu'il arrive. On ne donne pas la pente,
            # qui ne veut rien dire ici.
            return (
                f"{label}: STABLE. No clear trend, and even at the worst plausible "
                f"rate the warning threshold ({alerte}) cannot be reached within "
                f"{horizon}."
            )
        return (
            f"{label}: STABLE ({r2}). No threshold will be reached within "
            f"{horizon} (warning at {alerte})."
        )

    if f.verdict == "baisse":
        return (
            f"{label}: FALLING at {f.slope_per_hour:.1f}{u} per hour ({r2}, reliable "
            f"trend). No threshold will be reached within {horizon}."
        )

    # hausse
    phrases = [f"{label}: RISING at +{f.slope_per_hour:.1f}{u} per hour ({r2}, reliable trend)."]
    if f.minutes_to_warning == 0:
        phrases.append(f"The warning threshold ({alerte}) is already exceeded.")
    elif f.minutes_to_warning is not None:
        # « Saturer » est le mot de l'utilisateur, pas celui de la donnée : le
        # seuil d'alerte déclenche une alerte, la mémoire n'est pas pleine.
        phrases.append(
            f"At this rate the warning threshold ({alerte}) will be reached "
            f"{_in(f.minutes_to_warning)} (an alert, not yet saturation)."
        )
    if f.minutes_to_critical == 0:
        phrases.append(f"The critical threshold ({incident}) is already exceeded.")
    elif f.minutes_to_critical is not None:
        phrases.append(
            f"The critical threshold ({incident}) will be reached "
            f"{_in(f.minutes_to_critical)}."
        )
    else:
        phrases.append(
            f"The critical threshold ({incident}) will not be reached within {horizon}."
        )
    return " ".join(phrases)


class ToolHandlers:
    def __init__(self, metrics: MetricsService):
        self._metrics = metrics

    async def get_platform_health(self) -> str:
        s = await self._metrics.get_snapshot("5m")
        lines = [
            f"Overall status: {s.status}",
            f"Application: {s.app_status}, Database: {s.db_status}",
            f"Memory heap: {_fmt(s.heap_percent, '%')} "
            f"({_fmt(s.heap_used_mb, ' MB', 0)} of {_fmt(s.heap_max_mb, ' MB', 0)})",
            f"CPU: {_fmt(s.cpu_percent, '%')}",
            f"HTTP p95 latency: {_fmt(s.latency_p95_ms, ' ms', 0)}",
            f"HTTP error rate: {_fmt(s.error_rate_percent, '%', 2)}",
            f"Throughput: {_fmt(s.requests_per_second, ' req/s', 2)}",
        ]
        if s.issues:
            lines.append("Detected issues: " + "; ".join(s.issues))
        return "\n".join(lines)

    async def get_memory_usage(self) -> str:
        s = await self._metrics.get_snapshot("5m")
        if s.heap_percent is None:
            return "Memory metrics are unavailable (Prometheus may be down)."
        verdict = (
            "CRITICAL" if s.heap_percent >= 90
            else "HIGH" if s.heap_percent >= 75
            else "NORMAL"
        )
        return (
            f"Heap: {_fmt(s.heap_used_mb, ' MB', 0)} used of "
            f"{_fmt(s.heap_max_mb, ' MB', 0)} ({_fmt(s.heap_percent, '%')}) - {verdict}. "
            f"CPU: {_fmt(s.cpu_percent, '%')}"
        )

    async def get_http_performance(self, window: str = "5m") -> str:
        w = _sanitize_window(window)
        s = await self._metrics.get_snapshot(w)
        if s.latency_p95_ms is None:
            return f"No HTTP traffic recorded over the last {w}."
        return (
            f"Over the last {w}: p95 latency {_fmt(s.latency_p95_ms, ' ms', 0)}, "
            f"error rate {_fmt(s.error_rate_percent, '%', 2)}, "
            f"throughput {_fmt(s.requests_per_second, ' req/s', 2)}"
        )

    async def get_slowest_endpoints(self, window: str = "1h") -> str:
        w = _sanitize_window(window)
        endpoints = await self._metrics.get_slowest_endpoints(w)
        if not endpoints:
            return f"No HTTP traffic recorded over the last {w}."

        top = sorted(endpoints.items(), key=lambda kv: kv[1], reverse=True)[:5]
        lines = [f"Slowest endpoints over the last {w}:"]
        lines += [f"  {uri}: {ms:.0f} ms" for uri, ms in top]
        return "\n".join(lines)

    async def get_metric_history(self, metric: str = "memory", window: str = "1h") -> str:
        """Tendance d'une métrique : min / moyenne / max / valeur courante."""
        m = _sanitize_metric(metric)
        w = _sanitize_window(window)
        trend = await self._metrics.get_trend(m, w)
        if trend is None:
            return (
                f"No history available for {m} over the last {w} "
                "(no traffic recorded, or Prometheus is down)."
            )
        u = trend["unit"]
        # Formulation calibrée sur une erreur observée : avec « min X, max Y »,
        # le modèle écrit « passant de X à Y », c'est-à-dire une progression
        # chronologique que la donnée ne dit pas. On met donc la conclusion
        # (la tendance) en tête, et on qualifie min/max de valeurs extrêmes.
        # Une valeur absente est OMISE plutôt que rendue "unavailable" : sur un
        # p95, avg_over_time devient NaN dès qu'il y a un trou de trafic dans la
        # fenêtre, et le modèle traduisait "average unavailable" par des
        # formules absurdes ("moyenne inatteignable").
        extremes = [
            f"{name} {_fmt(trend[key], u)}"
            for key, name in (("min", "lowest"), ("max", "highest"), ("avg", "average"))
            if trend[key] is not None
        ]
        line = (
            f"{trend['label']}: current value {_fmt(trend['current'], u)}. "
            f"Trend over the last {w}: {trend['direction'].upper()}."
        )
        if extremes:
            line += (
                " Extreme values during that period (not in chronological order): "
                + ", ".join(extremes)
                + "."
            )
        return line

    async def get_resource_forecast(self) -> str:
        """
        Quand la mémoire et le CPU toucheront-ils leur seuil ?

        La régression, le R² et le verdict sont calculés en Python
        (services/prevision.py). Un modèle de cette taille ne sait ni ajuster
        une droite ni juger un R² ; il sait reformuler « rising, warning in
        about 45 minutes, R² 0.91 ».
        """
        forecasts = await self._metrics.get_forecast("1h")
        if not forecasts:
            return "Forecast unavailable (Prometheus may be down). Do NOT give any time estimate."

        lines = [
            _describe_forecast(_FORECAST_LABELS.get(key, key), forecast)
            for key, forecast in forecasts.items()
        ]
        # Borne rappelée au modèle, en termes qu'il ne peut pas contourner.
        # « Never extrapolate beyond the time span » ne suffisait pas : qwen2.5:7b
        # écrivait quand même « pas de problème dans les prochaines heures » sur
        # une prévision d'une heure. Nommer les formules interdites a suffi.
        lines.append(
            "Method: linear regression over the last hour of data, projected at "
            "most one hour ahead. This forecast says NOTHING about later: never "
            "write 'in the coming hours', 'today' or 'no risk' — say 'within the "
            "next hour'."
        )
        return "\n".join(lines)

    async def get_database_status(self) -> str:
        s = await self._metrics.get_snapshot("5m")
        pending = s.db_connections_pending
        verdict = (
            "unknown" if pending is None
            else "SATURATED - requests are queuing" if pending >= 5
            else "UNDER PRESSURE" if pending > 0
            else "HEALTHY"
        )
        return (
            f"Database health: {s.db_status}. "
            f"Connection pool: {_fmt(s.db_connections_active, '', 0)} active, "
            f"{_fmt(pending, '', 0)} waiting - {verdict}"
        )

    # ── Métier : par établissement ──────────────────────────────────────────

    async def list_schools(self) -> str:
        schools = await self._metrics.list_schools()
        if not schools:
            return _NO_SCHOOL_METRICS
        return f"Schools on the platform ({len(schools)}): " + ", ".join(schools)

    async def get_school_metrics(self, school: str = "") -> str:
        """
        Chiffres métier d'un établissement : effectifs et générations de planning.

        Un seul outil couvre les deux sujets plutôt que deux outils spécialisés :
        un modèle de cette taille choisit d'autant plus mal qu'il a d'options, et
        la réponse complète tient en huit lignes.
        """
        known = await self._metrics.list_schools()
        if not known:
            return _NO_SCHOOL_METRICS

        code = _match_school(school, known)
        if code is None:
            # Ne PAS répondre « 0 » : l'établissement demandé n'a pas été
            # trouvé, ce qui est très différent d'un établissement sans données.
            logger.info("Établissement inconnu demandé par le LLM : %r", school)
            return (
                f"No school matches '{school}'. "
                f"Known schools: {', '.join(known)}. "
                "Tell the user the school was not found and list the known ones."
            )

        m = await self._metrics.get_school_metrics(code)

        lines = [f"School {code}:"]
        if m["users_total"] is not None:
            roles = ", ".join(
                f"{role} {count:.0f}" for role, count in sorted(m["users_by_role"].items())
            )
            lines.append(f"  Users: {m['users_total']:.0f} total ({roles})")
        else:
            lines.append("  Users: unavailable")
        lines.append(f"  Students: {_fmt(m['students'], '', 0)}")
        lines.append(f"  Classes: {_fmt(m['classes'], '', 0)}")
        lines.append(f"  Teachers: {_fmt(m['teachers'], '', 0)}")

        if m["planning_total"] is not None:
            statuses = ", ".join(
                f"{status} {count:.0f}"
                for status, count in sorted(m["planning_by_status"].items())
            )
            lines.append(
                f"  Timetable generations: {m['planning_total']:.0f} total ({statuses})"
            )
        else:
            # Zéro génération est une réponse légitime, et fréquente pour une
            # école qui vient d'être créée.
            lines.append("  Timetable generations: 0 (none recorded)")

        if m["last_generation_seconds"] is not None:
            lines.append(
                f"  Last generation took: {m['last_generation_seconds']:.0f} s"
            )
        return "\n".join(lines)

    # Table de dispatch : associe le nom vu par le LLM à la méthode Python.
    # Toute fonction absente de ce dict est rejetée — le modèle ne peut pas
    # provoquer l'appel d'autre chose.
    def as_registry(self) -> dict:
        return {
            "get_platform_health": self.get_platform_health,
            "get_memory_usage": self.get_memory_usage,
            "get_http_performance": self.get_http_performance,
            "get_slowest_endpoints": self.get_slowest_endpoints,
            "get_metric_history": self.get_metric_history,
            "get_resource_forecast": self.get_resource_forecast,
            "get_database_status": self.get_database_status,
            "list_schools": self.list_schools,
            "get_school_metrics": self.get_school_metrics,
        }