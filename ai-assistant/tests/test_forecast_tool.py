"""
L'outil de prévision de l'assistant de supervision.

Le calcul est testé dans test_prevision.py. Ici, on teste ce que le MODÈLE lit :
une phrase déjà interprétée, qui ne lui laisse aucune division à faire — et,
pour les verdicts sans échéance, l'interdit écrit en toutes lettres.
"""

import re

import pytest

from app.models import ChatResponse, ResourceForecast
from app.services.assistant import SYSTEM_PROMPT, AssistantService, borner_horizon
from app.tools.definitions import TOOL_DEFINITIONS
from app.tools.handlers import ToolHandlers, _duration


def _prevision(**champs) -> ResourceForecast:
    base = dict(
        verdict="stable", series="x", unit="%", warning_threshold=75.0,
        critical_threshold=90.0, points=61, r2=0.9, slope_per_hour=0.0,
        current=10.0, at_horizon=10.0, horizon_minutes=60.0,
    )
    base.update(champs)
    return ResourceForecast(**base)


class FakeMetrics:
    def __init__(self, memoire=None, cpu=None):
        self._previsions = {}
        if memoire is not None:
            self._previsions["memory"] = memoire
        if cpu is not None:
            self._previsions["cpu"] = cpu
        self.fenetre = None

    async def get_forecast(self, window="1h"):
        self.fenetre = window
        return self._previsions


async def _texte(memoire=None, cpu=None) -> str:
    return await ToolHandlers(FakeMetrics(memoire, cpu)).get_resource_forecast()


def _une_duree_est_annoncee(texte: str) -> bool:
    return bool(re.search(r"\bin (about|less)\b", texte))


# ─────────────────────────────────────────────────────────────────────────────
# Ce que le modèle lit, verdict par verdict
# ─────────────────────────────────────────────────────────────────────────────

async def test_une_hausse_donne_l_echeance_toute_calculee():
    texte = await _texte(memoire=_prevision(
        verdict="hausse", r2=0.91, slope_per_hour=20.0, minutes_to_warning=45.0,
    ))

    assert "RISING" in texte
    assert "+20.0% per hour" in texte
    assert "warning threshold (75%) will be reached in about 45 minutes" in texte
    # L'incident tombe au-delà de l'heure observée : dit comme tel, pas extrapolé.
    assert "critical threshold (90%) will not be reached within the next hour" in texte
    assert "R² 0.91" in texte


async def test_une_alerte_deja_franchie_n_est_pas_une_echeance():
    texte = await _texte(memoire=_prevision(
        verdict="hausse", slope_per_hour=10.0, minutes_to_warning=0.0,
        minutes_to_critical=40.0,
    ))

    assert "warning threshold (75%) is already exceeded" in texte
    assert "critical threshold (90%) will be reached in about 40 minutes" in texte


async def test_une_tendance_incertaine_interdit_toute_duree():
    """
    Le cas pour lequel l'interdit existe : avec un R² et une valeur sous les
    yeux, un modèle fait la division de lui-même.
    """
    texte = await _texte(memoire=_prevision(verdict="incertain", r2=0.12, at_horizon=None))

    assert "NO RELIABLE TREND" in texte
    assert "Do NOT give any time estimate" in texte
    assert not _une_duree_est_annoncee(texte)


async def test_un_historique_insuffisant_interdit_toute_duree():
    texte = await _texte(cpu=_prevision(
        verdict="insuffisant", points=7, r2=None, slope_per_hour=None,
        current=None, at_horizon=None, horizon_minutes=None,
    ))

    assert "NOT ENOUGH HISTORY" in texte
    assert "7 measurements" in texte
    # Ce qui manque est dit : sans cela, le modèle inventait un seuil de mesures.
    assert "at least 30 minutes of history" in texte
    # Observé : « pas de risque immédiat ». Ne rien savoir n'est pas être rassuré.
    assert "does NOT mean there is no risk" in texte
    assert "Do NOT give any time estimate" in texte


async def test_stable_sans_tendance_lisible_ne_donne_pas_la_pente():
    """
    Le cas de la démo : R² faible, mais seuil hors d'atteinte. La pente d'une
    droite qui n'explique rien ne doit pas atteindre le modèle, qui la
    raconterait comme une tendance.
    """
    texte = await _texte(memoire=_prevision(
        verdict="stable", r2=0.12, slope_per_hour=-3.2, at_horizon=None,
    ))

    assert "STABLE" in texte
    assert "cannot be reached within the next hour" in texte
    assert "per hour" not in texte
    assert not _une_duree_est_annoncee(texte)


async def test_stable_avec_une_tendance_nette():
    texte = await _texte(cpu=_prevision(verdict="stable", r2=0.93, slope_per_hour=0.4))

    assert "STABLE (R² 0.93)" in texte
    assert "No threshold will be reached within the next hour" in texte


async def test_une_baisse():
    texte = await _texte(cpu=_prevision(verdict="baisse", r2=0.8, slope_per_hour=-5.0))

    assert "FALLING at -5.0% per hour" in texte
    assert not _une_duree_est_annoncee(texte)


async def test_la_memoire_regressee_est_nommee_pour_ce_qu_elle_est():
    """
    L'échéance porte sur le plancher de la heap. Si le modèle la rattachait au
    chiffre de la tuile, il annoncerait une alerte que la tuile aura déjà
    affichée — la heap brute touche le seuil plus tôt, à chaque pic.
    """
    texte = await _texte(memoire=_prevision(), cpu=_prevision())

    assert "after garbage collection" in texte
    assert "CPU load" in texte


async def test_la_borne_de_l_extrapolation_est_rappelee():
    """
    Observé avec qwen2.5:7b : « aucun problème dans les prochaines heures » sur
    une prévision d'une heure. Les formules interdites sont donc nommées.
    """
    texte = await _texte(memoire=_prevision())

    assert "says NOTHING about later" in texte
    assert "in the coming hours" in texte


async def test_la_fenetre_est_fixee_a_une_heure():
    metrics = FakeMetrics(memoire=_prevision())
    await ToolHandlers(metrics).get_resource_forecast()

    assert metrics.fenetre == "1h"


async def test_prometheus_injoignable_interdit_toute_duree():
    texte = await _texte()

    assert "unavailable" in texte
    assert "Do NOT give any time estimate" in texte


def test_les_durees_se_lisent_sans_division():
    assert _duration(45.2) == "45 minutes"
    assert _duration(130) == "2 h 10 min"
    assert _duration(120) == "2 hours"
    # Arrondi à cinq minutes au-delà d'une heure et demie.
    assert _duration(127) == "2 h 05 min"


# ─────────────────────────────────────────────────────────────────────────────
# Branchement : déclaré, enregistré, annoncé dans le prompt
# ─────────────────────────────────────────────────────────────────────────────

def test_chaque_outil_declare_a_son_handler_et_inversement():
    """
    Un outil déclaré sans handler est rejeté par la boucle ; un handler sans
    déclaration n'est jamais proposé au modèle. Les deux listes doivent
    coïncider.
    """
    declares = {d["function"]["name"] for d in TOOL_DEFINITIONS}
    enregistres = set(ToolHandlers(FakeMetrics()).as_registry())

    assert "get_resource_forecast" in declares
    assert declares == enregistres


def test_l_outil_ne_prend_aucun_parametre():
    definition = next(
        d for d in TOOL_DEFINITIONS if d["function"]["name"] == "get_resource_forecast"
    )
    assert definition["function"]["parameters"]["properties"] == {}


def test_le_prompt_oriente_les_questions_quand_vers_la_prevision():
    assert "get_resource_forecast" in SYSTEM_PROMPT
    assert "quand" in SYSTEM_PROMPT


# ─────────────────────────────────────────────────────────────────────────────
# L'horizon, tenu par le code quand la consigne ne suffit pas
# ─────────────────────────────────────────────────────────────────────────────

@pytest.mark.parametrize(
    "ecrit,attendu",
    [
        # Les trois formules relevées avec qwen2.5:7b, le 10/09/2026.
        ("Le CPU ne risque pas de saturer dans les prochaines heures.",
         "Le CPU ne risque pas de saturer dans l'heure qui vient."),
        ("Aucune tendance à la saturation dans les prochaines heures.",
         "Aucune tendance à la saturation dans l'heure qui vient."),
        ("Pas d'alerte au cours des prochaines heures.",
         "Pas d'alerte dans l'heure qui vient."),
        ("Stable pour les heures à venir.", "Stable dans l'heure qui vient."),
        ("Il faut surveiller les prochaines heures.", "Il faut surveiller l'heure qui vient."),
    ],
)
def test_les_formules_qui_elargissent_l_horizon_sont_ramenees_a_l_heure(ecrit, attendu):
    assert borner_horizon(ecrit) == attendu


def test_une_reponse_deja_bornee_n_est_pas_touchee():
    texte = "Le seuil d'alerte sera atteint dans environ 45 minutes, pas d'incident dans l'heure à venir."
    assert borner_horizon(texte) == texte


class _BoucleFigee:
    """Une boucle d'outils qui rend une réponse écrite d'avance."""

    def __init__(self, answer, tools_used):
        self._reponse = ChatResponse(answer=answer, tools_used=tools_used, duration_ms=0)

    async def run(self, **_):
        return self._reponse


def _assistant(answer, tools_used):
    service = AssistantService(ollama=None, handlers=ToolHandlers(FakeMetrics()))
    service._loop = _BoucleFigee(answer, tools_used)
    return service


async def test_l_assistant_borne_l_horizon_apres_une_prevision():
    service = _assistant("Stable dans les prochaines heures.", ["get_resource_forecast"])

    assert (await service.ask("q")).answer == "Stable dans l'heure qui vient."


async def test_hors_prevision_la_reponse_du_modele_est_laissee_telle_quelle():
    """Sans prévision consultée, « les prochaines heures » n'élargit rien."""
    service = _assistant("Surveillez les prochaines heures.", ["get_metric_history"])

    assert (await service.ask("q")).answer == "Surveillez les prochaines heures."


def test_le_prompt_borne_la_prevision_a_l_heure_qui_vient():
    """L'interdit en anglais dans la sortie de l'outil ne suffisait pas : il est aussi dans le prompt, en français."""
    assert "prochaines" in SYSTEM_PROMPT
    assert "ne rassure pas" in SYSTEM_PROMPT
