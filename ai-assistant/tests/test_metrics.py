import httpx
import pytest

from app.clients.prometheus import PrometheusClient

from app.models import HealthSnapshot
from app.services.metrics import HISTORY_METRICS, TREND_POINTS, MetricsService


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


# ─────────────────────────────────────────────────────────────────────────────
# Courbe de tendance — d'où l'on vient, et non où l'on en est
# ─────────────────────────────────────────────────────────────────────────────

class FakePrometheus:
    """Un Prometheus réduit à ce que `get_trends` lui demande."""

    def __init__(self, series):
        self._series = series
        self.appels = []

    async def query_range(self, promql, minutes, step_seconds):
        self.appels.append({"promql": promql, "minutes": minutes, "step": step_seconds})
        return self._series.get(promql, [])


def _service_avec(series):
    return MetricsService(prometheus=FakePrometheus(series), actuator=None)


async def test_les_cinq_mesures_sont_interrogees():
    prom = FakePrometheus({})
    await MetricsService(prometheus=prom, actuator=None).get_trends("1h")

    assert len(prom.appels) == len(HISTORY_METRICS)
    # Les expressions sont celles de HISTORY_METRICS, pas une seconde version :
    # deux requêtes différentes pour la même mesure finiraient par produire une
    # courbe qui contredit le chiffre posé au-dessus d'elle.
    interrogees = {a["promql"] for a in prom.appels}
    assert interrogees == {expr for _, _, expr in HISTORY_METRICS.values()}


async def test_une_mesure_sans_donnee_est_absente_pas_vide():
    """
    Le garde-fou : une liste vide se dessinerait comme une ligne plate, et une
    ligne plate se lit comme « stable » — soit exactement l'inverse de « on ne
    sait pas ».
    """
    expression = HISTORY_METRICS["cpu"][2]
    service = _service_avec({expression: [10.0, 12.0]})

    tendances = await service.get_trends("1h")

    assert tendances == {"cpu": [10.0, 12.0]}
    assert "memory" not in tendances


async def test_la_fenetre_choisit_la_duree_et_le_pas():
    prom = FakePrometheus({})
    await MetricsService(prometheus=prom, actuator=None).get_trends("24h")

    premier = prom.appels[0]
    assert premier["minutes"] == 24 * 60
    # Douze points sur vingt-quatre heures : deux heures par point.
    assert premier["step"] == 24 * 60 * 60 // TREND_POINTS


async def test_une_fenetre_inconnue_retombe_sur_une_heure():
    """Le paramètre vient de l'URL : il ne décide jamais d'un calcul non borné."""
    prom = FakePrometheus({})
    await MetricsService(prometheus=prom, actuator=None).get_trends("n'importe quoi")

    assert prom.appels[0]["minutes"] == 60


async def test_le_pas_ne_descend_jamais_sous_quinze_secondes():
    """
    Sur cinq minutes, douze points feraient un pas de 25 s — mais une fenêtre
    plus courte donnerait un pas inférieur à l'intervalle de collecte de
    Prometheus, et Prometheus répéterait alors le même point.
    """
    prom = FakePrometheus({})
    await MetricsService(prometheus=prom, actuator=None).get_trends("5m")

    assert prom.appels[0]["step"] >= 15


# ─────────────────────────────────────────────────────────────────────────────
# La requête de plage — ce que Prometheus rend, et ce qu'on en garde
# ─────────────────────────────────────────────────────────────────────────────

def _prometheus_rendant(payload=None, status=200, erreur=False):
    """Un PrometheusClient dont le transport HTTP est simulé."""
    def transport(request):
        if erreur:
            raise httpx.ConnectError("injoignable", request=request)
        return httpx.Response(status, json=payload or {})

    client = PrometheusClient("http://prom")
    client._client = httpx.AsyncClient(
        transport=httpx.MockTransport(transport), base_url="http://prom"
    )
    return client


def _serie(*valeurs):
    return {"data": {"result": [{"values": [[1700000000 + i * 60, v] for i, v in enumerate(valeurs)]}]}}


async def test_les_points_sortent_dans_l_ordre():
    points = await _prometheus_rendant(_serie("1.5", "2.5", "3.5")).query_range("x", 60, 300)

    assert points == [1.5, 2.5, 3.5]


async def test_les_trous_sont_retires_jamais_combles():
    """
    Prometheus rend NaN pour un point sans donnée — service arrêté, quantile
    sans trafic. Le remplacer par zéro dessinerait un effondrement du service
    qui n'a pas eu lieu.
    """
    points = await _prometheus_rendant(_serie("40.0", "NaN", "60.0")).query_range("x", 60, 300)

    assert points == [40.0, 60.0]
    assert 0.0 not in points


async def test_une_serie_absente_rend_une_liste_vide():
    points = await _prometheus_rendant({"data": {"result": []}}).query_range("x", 60, 300)

    assert points == []


async def test_prometheus_injoignable_ne_leve_jamais():
    """
    Même contrat que `query_scalar` : une source d'observation indisponible est
    une dégradation, pas une panne. L'écran doit rester lisible sans courbe.
    """
    points = await _prometheus_rendant(erreur=True).query_range("x", 60, 300)

    assert points == []


async def test_une_reponse_inattendue_ne_leve_jamais():
    points = await _prometheus_rendant({"data": {"result": [{}]}}).query_range("x", 60, 300)

    assert points == []


async def test_la_fenetre_et_le_pas_partent_dans_la_requete():
    vues = {}

    def transport(request):
        vues.update(dict(request.url.params))
        return httpx.Response(200, json=_serie("1.0"))

    client = PrometheusClient("http://prom")
    client._client = httpx.AsyncClient(
        transport=httpx.MockTransport(transport), base_url="http://prom"
    )
    await client.query_range("ma_metrique", 60, 300)

    assert vues["query"] == "ma_metrique"
    assert vues["step"] == "300s"
    # Une heure exactement entre le début et la fin demandés.
    assert round(float(vues["end"]) - float(vues["start"])) == 3600
