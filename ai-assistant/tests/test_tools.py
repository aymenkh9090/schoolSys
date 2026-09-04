import pytest

from app.services.assistant import _accepted_args
from app.tools.handlers import ToolHandlers, _sanitize_metric, _sanitize_window


@pytest.mark.parametrize("value", ["5m", "1h", "24h"])
def test_fenetres_valides(value):
    assert _sanitize_window(value) == value


@pytest.mark.parametrize(
    "value",
    ["30 minutes", "last hour", "1d", "", None, "5 m", "'5m'", "DROP TABLE"],
)
def test_fenetres_invalides_repliees(value):
    """Tout ce que le LLM peut inventer doit retomber sur une valeur sûre."""
    assert _sanitize_window(value) == "5m"


@pytest.mark.parametrize("value", ["memory", "cpu", "latency", "errors", "throughput"])
def test_metriques_valides(value):
    assert _sanitize_metric(value) == value


@pytest.mark.parametrize("value", ["ram", "jvm", "", None, "MEMORY", "heap usage"])
def test_metriques_invalides_repliees(value):
    assert _sanitize_metric(value) == "memory"


def test_arguments_non_declares_ignores():
    """
    Le cas réel : le modèle appelle get_memory_usage(window="1h") alors que
    l'outil ne prend aucun argument. On doit exécuter l'outil quand même,
    pas renvoyer une erreur que le modèle se contentera de commenter.
    """
    handlers = ToolHandlers(None)
    assert _accepted_args(handlers.get_memory_usage, {"window": "1h"}, "x") == {}
    assert _accepted_args(
        handlers.get_metric_history, {"metric": "cpu", "window": "1h", "foo": 1}, "x"
    ) == {"metric": "cpu", "window": "1h"}
