"""
Outils métier par établissement.

Le point sensible n'est pas le calcul — c'est la RÉSOLUTION du nom. Le modèle
transmet ce que l'utilisateur a écrit (« l'école Ibn Khaldoun »), alors que le
label Prometheus vaut « IBN_KHALDOUN ». Entre les deux il faut une
correspondance qui tolère la casse, les accents et les séparateurs, sans jamais
laisser une chaîne du LLM atteindre une requête PromQL.
"""

import pytest

from app.tools.handlers import ToolHandlers, _match_school, _normalize_school

KNOWN = ["IBN_KHALDOUN", "CARTHAGE", "college-mh", "seed-test"]


# ── Normalisation ───────────────────────────────────────────────────────────

@pytest.mark.parametrize(
    "value,expected",
    [
        ("IBN_KHALDOUN", "ibnkhaldoun"),
        ("Ibn Khaldoun", "ibnkhaldoun"),
        ("ibn-khaldoun", "ibnkhaldoun"),
        ("Collège MH", "collegemh"),
        ("  CARTHAGE  ", "carthage"),
        ("", ""),
    ],
)
def test_normalisation(value, expected):
    assert _normalize_school(value) == expected


# ── Correspondance ──────────────────────────────────────────────────────────

@pytest.mark.parametrize(
    "written",
    ["IBN_KHALDOUN", "Ibn Khaldoun", "ibn khaldoun", "Ibn-Khaldoun", "ibnkhaldoun"],
)
def test_toutes_les_graphies_trouvent_l_ecole(written):
    """C'est le cas qui a échoué en production : « l'école Ibn Khaldoun »."""
    assert _match_school(written, KNOWN) == "IBN_KHALDOUN"


def test_correspondance_partielle():
    assert _match_school("college", KNOWN) == "college-mh"
    assert _match_school("carthage", KNOWN) == "CARTHAGE"


@pytest.mark.parametrize("written", ["", None, "   ", "école inexistante"])
def test_nom_inconnu_ne_correspond_a_rien(written):
    """
    Renvoyer None, jamais un établissement au hasard : répondre les chiffres
    d'une autre école serait pire que de ne pas répondre.
    """
    assert _match_school(written, KNOWN) is None


def test_aucune_ecole_connue():
    assert _match_school("Ibn Khaldoun", []) is None


# ── Outils ──────────────────────────────────────────────────────────────────

class FakeMetrics:
    def __init__(self, schools=None, payload=None):
        self._schools = schools if schools is not None else list(KNOWN)
        self._payload = payload or {}
        self.asked_for = None

    async def list_schools(self):
        return self._schools

    async def get_school_metrics(self, school):
        self.asked_for = school
        return {
            "school": school,
            "users_by_role": {"TEACHER": 23.0, "SURVEILLANT": 1.0},
            "users_total": 24.0,
            "students": 512.0,
            "classes": 13.0,
            "teachers": 23.0,
            "planning_by_status": {"SOLVED": 3.0, "FAILED": 4.0, "INFEASIBLE": 3.0},
            "planning_total": 10.0,
            "last_generation_seconds": 300.0,
            **self._payload,
        }


@pytest.mark.asyncio
async def test_question_reelle_nombre_de_plannings_generes():
    """« le nombre de planning généré par l'école Ibn Khaldoun »."""
    metrics = FakeMetrics()
    answer = await ToolHandlers(metrics).get_school_metrics("l'école Ibn Khaldoun")

    # Le code résolu, et lui seul, atteint la couche PromQL.
    assert metrics.asked_for == "IBN_KHALDOUN"
    assert "10 total" in answer
    assert "SOLVED 3" in answer
    assert "FAILED 4" in answer


@pytest.mark.asyncio
async def test_ecole_introuvable_liste_les_ecoles_connues():
    """
    Ne surtout pas répondre « 0 » : l'école n'a pas été trouvée, ce qui est
    autre chose qu'une école sans données.
    """
    answer = await ToolHandlers(FakeMetrics()).get_school_metrics("Sorbonne")
    assert "No school matches" in answer
    assert "IBN_KHALDOUN" in answer


@pytest.mark.asyncio
async def test_metriques_non_publiees_ne_renvoie_aucun_chiffre():
    """
    Backend d'une version antérieure : la liste est vide. Le modèle doit dire
    que la donnée n'est pas publiée, pas inventer un nombre.
    """
    answer = await ToolHandlers(FakeMetrics(schools=[])).get_school_metrics("Carthage")
    assert "not available" in answer
    assert "do NOT answer with a number" in answer


@pytest.mark.asyncio
async def test_aucune_generation_est_une_reponse_legitime():
    """Une école qui vient d'être créée n'a aucun planning : ce n'est pas une panne."""
    metrics = FakeMetrics(payload={"planning_by_status": {}, "planning_total": None})
    answer = await ToolHandlers(metrics).get_school_metrics("Carthage")
    assert "Timetable generations: 0" in answer


@pytest.mark.asyncio
async def test_liste_des_ecoles():
    answer = await ToolHandlers(FakeMetrics()).list_schools()
    assert "4" in answer
    assert "IBN_KHALDOUN" in answer


@pytest.mark.asyncio
async def test_liste_vide_signale_l_absence_de_metriques():
    answer = await ToolHandlers(FakeMetrics(schools=[])).list_schools()
    assert "not available" in answer


def test_les_outils_sont_dans_le_registre():
    """Un outil absent du registre est rejeté par la boucle : la déclaration ne suffit pas."""
    registry = ToolHandlers(FakeMetrics()).as_registry()
    assert "list_schools" in registry
    assert "get_school_metrics" in registry
