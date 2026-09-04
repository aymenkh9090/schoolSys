"""
Implémentations des outils.

Chaque fonction renvoie une CHAÎNE COURTE ET DÉJÀ INTERPRÉTÉE, destinée à
être relue par le modèle. Jamais de JSON brut, jamais de série temporelle.
"""

import logging
import unicodedata

from app.services.metrics import MetricsService

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
            "get_database_status": self.get_database_status,
            "list_schools": self.list_schools,
            "get_school_metrics": self.get_school_metrics,
        }