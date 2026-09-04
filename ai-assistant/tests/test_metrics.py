import pytest

from app.models import HealthSnapshot
from app.services.metrics import MetricsService


def _snapshot(**kwargs) -> HealthSnapshot:
    base = dict(status="UNKNOWN", app_status="UP", db_status="UP")
    base.update(kwargs)
    return HealthSnapshot(**base)


@pytest.fixture
def service():
    # _evaluate() est une fonction pure : aucun client réel nécessaire.
    return MetricsService(prometheus=None, actuator=None)


def test_healthy(service):
    status, issues = service._evaluate(
        _snapshot(heap_percent=40.0, cpu_percent=20.0, latency_p95_ms=150.0,
                  error_rate_percent=0.0, db_connections_pending=0.0)
    )
    assert status == "HEALTHY"
    assert issues == []


def test_warning_sur_memoire(service):
    status, issues = service._evaluate(
        _snapshot(heap_percent=80.0, latency_p95_ms=150.0)
    )
    assert status == "WARNING"
    assert any("Mémoire" in i for i in issues)


def test_critical_prend_le_dessus(service):
    """Un seul indicateur critique suffit à basculer tout le statut."""
    status, _ = service._evaluate(
        _snapshot(heap_percent=80.0, latency_p95_ms=5000.0)
    )
    assert status == "CRITICAL"


def test_app_down_court_circuite(service):
    """Application DOWN : inutile de regarder le reste."""
    status, issues = service._evaluate(
        _snapshot(app_status="DOWN", heap_percent=10.0)
    )
    assert status == "CRITICAL"
    assert issues == ["L'application est DOWN"]


def test_aucune_donnee_nest_pas_healthy(service):
    """
    Le cas le plus important : sans métrique, on ne doit SURTOUT PAS
    annoncer que tout va bien.
    """
    status, issues = service._evaluate(_snapshot())
    assert status == "UNKNOWN"
    assert issues
