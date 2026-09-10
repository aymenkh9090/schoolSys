import random
import statistics

import pytest

from app.services.metrics import (
    FORECAST_METRICS,
    HISTORY_METRICS,
    THRESHOLDS,
    MetricsService,
)
from app.services.prevision import ajuster, prevoir

# Seuils de la heap : alerte à 75 %, incident à 90 %.
SEUILS = THRESHOLDS["heap_percent"]

T0 = 1_700_000_000.0


def _serie(valeurs, pas_s=60):
    """Une série horodatée, un point par minute par défaut."""
    return [(T0 + i * pas_s, v) for i, v in enumerate(valeurs)]


def _droite(debut, par_heure, n=61):
    """n points sur une droite parfaite, un par minute."""
    return _serie([debut + par_heure * i / 60 for i in range(n)])


# ─────────────────────────────────────────────────────────────────────────────
# L'ajustement — les formules, rien d'autre
# ─────────────────────────────────────────────────────────────────────────────

def test_une_droite_parfaite_est_retrouvee_exactement():
    aj = ajuster(_droite(40.0, par_heure=20.0))

    assert aj.pente * 3600 == pytest.approx(20.0)
    assert aj.valeur(T0) == pytest.approx(40.0)
    assert aj.r2 == pytest.approx(1.0)
    assert aj.erreur_pente == pytest.approx(0.0, abs=1e-12)


def test_la_pente_coincide_avec_celle_de_la_bibliotheque_standard():
    """
    Les formules sont écrites à la main pour être citables ; elles doivent
    donner exactement ce que donne `statistics.linear_regression`.
    """
    alea = random.Random(7)
    points = _serie([30 + 0.1 * i + alea.uniform(-3, 3) for i in range(61)])

    aj = ajuster(points)
    reference = statistics.linear_regression(
        [t for t, _ in points], [y for _, y in points]
    )

    assert aj.pente == pytest.approx(reference.slope)
    assert aj.ordonnee == pytest.approx(reference.intercept)


def test_du_bruit_pur_a_un_r2_nul():
    # Une dent de scie symétrique : aucune tendance, et la droite le dit.
    aj = ajuster(_serie([2.0, 8.0] * 30 + [2.0]))

    assert aj.r2 == pytest.approx(0.0, abs=1e-9)


def test_une_serie_constante_ne_divise_jamais_par_zero():
    aj = ajuster(_serie([42.0] * 20))

    assert aj.pente == 0.0
    assert aj.r2 == 1.0


def test_des_points_au_meme_instant_n_ont_pas_de_pente():
    with pytest.raises(ValueError):
        ajuster([(T0, 1.0), (T0, 2.0)])


# ─────────────────────────────────────────────────────────────────────────────
# Les garde-fous — chaque verdict a son test
# ─────────────────────────────────────────────────────────────────────────────

def test_moins_de_dix_points_est_insuffisant():
    p = prevoir(_droite(40.0, 20.0, n=9), SEUILS, attendus=9)

    assert p.verdict == "insuffisant"
    assert p.r2 is None
    assert p.minutes_avant_alerte is None


def test_une_fenetre_couverte_a_moins_de_moitie_est_insuffisante():
    """Un redémarrage qui a mangé les deux tiers de l'heure : la droite ne décrirait qu'un fragment."""
    p = prevoir(_droite(40.0, 20.0, n=20), SEUILS, attendus=61)

    assert p.verdict == "insuffisant"


def test_une_serie_vide_est_insuffisante():
    assert prevoir([], SEUILS, attendus=61).verdict == "insuffisant"


def test_une_hausse_nette_donne_les_minutes_avant_alerte():
    # 40 % → 60 % en une heure : 75 % dans 45 min, 90 % dans 1 h 30.
    p = prevoir(_droite(40.0, par_heure=20.0), SEUILS, attendus=61)

    assert p.verdict == "hausse"
    assert p.minutes_avant_alerte == pytest.approx(45.0)
    # L'incident tombe au-delà de l'heure observée : pas de durée extrapolée.
    assert p.minutes_avant_incident is None
    assert p.valeur_actuelle == pytest.approx(60.0)


def test_l_horizon_ne_depasse_jamais_la_duree_observee():
    """
    10 % → 15 % en une heure : l'alerte tomberait dans douze heures. Une heure
    d'historique ne permet pas de le dire — le verdict est « stable » à
    l'horizon d'une heure, pas « alerte dans 12 h ».
    """
    p = prevoir(_droite(10.0, par_heure=5.0), SEUILS, attendus=61)

    assert p.verdict == "stable"
    assert p.minutes_avant_alerte is None
    assert p.horizon_min == pytest.approx(60.0)
    assert p.valeur_horizon == pytest.approx(20.0)


def test_une_pente_negative_est_une_baisse():
    p = prevoir(_droite(60.0, par_heure=-10.0), SEUILS, attendus=61)

    assert p.verdict == "baisse"
    assert p.minutes_avant_alerte is None


def test_une_serie_constante_est_stable():
    p = prevoir(_serie([42.0] * 61), SEUILS, attendus=61)

    assert p.verdict == "stable"
    assert p.minutes_avant_alerte is None


def test_une_dent_de_scie_sous_le_seuil_est_incertaine_sans_duree():
    """
    Le cas que le R² existe pour attraper : 65 / 80 % en alternance, juste
    autour du seuil. La droite n'explique rien, et le bruit est assez large pour
    que le seuil soit à portée — on ne sait pas, et on le dit.
    """
    p = prevoir(_serie([65.0, 80.0] * 30 + [65.0]), SEUILS, attendus=61)

    assert p.verdict == "incertain"
    assert p.r2 < 0.5
    assert p.minutes_avant_alerte is None
    assert p.minutes_avant_incident is None


def test_une_dent_de_scie_loin_du_seuil_est_stable_malgre_un_r2_nul():
    """
    Le jeu de démo : une heap qui oscille entre 2 et 8 % face à une alerte à
    75 %. Aucune tendance lisible — mais même la pente la plus défavorable que
    ce bruit autorise n'atteint pas le seuil dans l'heure. La réponse honnête
    est « stable », pas « on ne sait pas ».
    """
    p = prevoir(_serie([2.0, 8.0] * 30 + [2.0]), SEUILS, attendus=61)

    assert p.r2 < 0.5
    assert p.verdict == "stable"
    assert p.minutes_avant_alerte is None


def test_une_droite_qui_n_explique_rien_n_est_jamais_projetee():
    """
    L'écran prolonge la courbe par `valeur_horizon`. Sous R² 0,5, la pente est
    celle d'une droite que le verdict vient de refuser : la tracer dessinerait
    une tendance qui n'existe pas — même quand le verdict dit « stable ».
    """
    stable = prevoir(_serie([2.0, 8.0] * 30 + [2.0]), SEUILS, attendus=61)
    incertain = prevoir(_serie([65.0, 80.0] * 30 + [65.0]), SEUILS, attendus=61)
    hausse = prevoir(_droite(40.0, par_heure=20.0), SEUILS, attendus=61)

    assert stable.valeur_horizon is None
    assert incertain.valeur_horizon is None
    assert hausse.valeur_horizon == pytest.approx(80.0)


def test_un_seuil_deja_franchi_compte_zero_minute():
    # 70 % → 80 % en une heure : l'alerte est derrière, l'incident pile à l'horizon.
    p = prevoir(_droite(70.0, par_heure=10.0), SEUILS, attendus=61)

    assert p.verdict == "hausse"
    assert p.minutes_avant_alerte == 0.0
    assert p.minutes_avant_incident == pytest.approx(60.0)


def test_un_trou_ne_decale_pas_l_axe_du_temps():
    """
    La raison des horodatages : un redémarrage retire vingt points. Si l'on
    numérotait les points au lieu de les dater, les suivants glisseraient de
    vingt minutes et la pente serait fausse d'autant.
    """
    points = _droite(40.0, par_heure=20.0)
    avec_trou = points[:20] + points[40:]

    p = prevoir(avec_trou, SEUILS, attendus=61)

    assert p.pente_par_heure == pytest.approx(20.0)
    assert p.minutes_avant_alerte == pytest.approx(45.0)


def test_l_ordre_des_points_est_indifferent():
    points = _droite(40.0, par_heure=20.0)

    assert prevoir(points[::-1], SEUILS, 61) == prevoir(points, SEUILS, 61)


# ─────────────────────────────────────────────────────────────────────────────
# Le service — ce qui est régressé, et comment
# ─────────────────────────────────────────────────────────────────────────────

class FakePrometheus:
    """Un Prometheus réduit à ce que `get_forecast` lui demande."""

    def __init__(self, series=None):
        self._series = series or {}
        self.appels = []

    async def query_range_points(self, promql, minutes, step_seconds):
        self.appels.append({"promql": promql, "minutes": minutes, "step": step_seconds})
        return self._series.get(promql, [])


def _service(prom):
    return MetricsService(prometheus=prom, actuator=None)


async def test_la_memoire_regressee_est_le_plancher_pas_la_heap_brute():
    """
    La heap brute est une dent de scie : la régresser mesurerait le rythme du
    ramasse-miettes. C'est le plancher — le minimum sur cinq minutes — qui
    monte lors d'une fuite.
    """
    prom = FakePrometheus()
    await _service(prom).get_forecast("1h")

    memoire = FORECAST_METRICS["memory"][2]
    assert memoire.startswith("min_over_time(")
    assert HISTORY_METRICS["memory"][2] in memoire
    assert memoire != HISTORY_METRICS["memory"][2]
    assert memoire in {a["promql"] for a in prom.appels}


async def test_seules_la_memoire_et_le_cpu_sont_prevus():
    previsions = await _service(FakePrometheus()).get_forecast("1h")

    assert set(previsions) == {"memory", "cpu"}


async def test_sans_historique_chaque_ressource_dit_insuffisant():
    """Présente et explicite, plutôt qu'absente et lue comme un oubli."""
    previsions = await _service(FakePrometheus()).get_forecast("1h")

    for prevision in previsions.values():
        assert prevision.verdict == "insuffisant"
        assert prevision.points == 0
        assert prevision.minutes_to_warning is None


async def test_une_heure_se_lit_minute_par_minute():
    prom = FakePrometheus()
    await _service(prom).get_forecast("1h")

    assert prom.appels[0]["minutes"] == 60
    assert prom.appels[0]["step"] == 60


async def test_une_fenetre_inconnue_retombe_sur_une_heure():
    prom = FakePrometheus()
    await _service(prom).get_forecast("n'importe quoi")

    assert prom.appels[0]["minutes"] == 60


async def test_la_prevision_porte_ses_seuils_et_sa_serie():
    expression = FORECAST_METRICS["memory"][2]
    prom = FakePrometheus({expression: _droite(40.0, par_heure=20.0)})

    memoire = (await _service(prom).get_forecast("1h"))["memory"]

    assert memoire.verdict == "hausse"
    assert memoire.minutes_to_warning == pytest.approx(45.0)
    assert memoire.warning_threshold == 75.0
    assert memoire.critical_threshold == 90.0
    assert "floor" in memoire.series


async def test_la_prevision_rend_la_serie_regressee_horodatee():
    """
    Le graphique trace les points mêmes qui ont servi au calcul, chacun à son
    heure : un trou de redémarrage doit rester un trou sur l'axe du temps.
    """
    expression = FORECAST_METRICS["cpu"][2]
    points = _droite(20.0, par_heure=10.0)
    avec_trou = points[:20] + points[40:]
    prom = FakePrometheus({expression: avec_trou})

    cpu = (await _service(prom).get_forecast("1h"))["cpu"]

    assert cpu.history == avec_trou
    assert cpu.history[20][0] - cpu.history[19][0] == pytest.approx(21 * 60)


async def test_sans_historique_la_serie_est_vide():
    previsions = await _service(FakePrometheus()).get_forecast("1h")

    assert all(p.history == [] for p in previsions.values())
