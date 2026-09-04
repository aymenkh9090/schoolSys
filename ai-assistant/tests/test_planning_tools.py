import json

import pytest

from app.services.dsl_translator import (
    DslTranslator,
    _clean,
    _describe_schema,
    _repair_instruction,
)
from app.tools.planning_handlers import PlanningToolHandlers, _sanitize_level


# ─────────────────────────────────────────────────────────────────────────────
# Assainissement des arguments venus du LLM
# ─────────────────────────────────────────────────────────────────────────────

@pytest.mark.parametrize("value", ["HARD", "MEDIUM", "SOFT", "ALL"])
def test_niveaux_valides(value):
    assert _sanitize_level(value) == value


@pytest.mark.parametrize(
    "value", ["dur", "critique", "", None, "hard hard", "DROP TABLE", "URGENT"]
)
def test_niveaux_invalides_replies(value):
    """Tout ce que le modèle peut inventer doit retomber sur une valeur sûre."""
    assert _sanitize_level(value) == "ALL"


def test_niveau_insensible_a_la_casse():
    assert _sanitize_level("hard") == "HARD"


# ─────────────────────────────────────────────────────────────────────────────
# Aucune écriture n'est exposée au modèle
# ─────────────────────────────────────────────────────────────────────────────

def test_le_registre_ne_contient_que_des_lectures():
    """
    Le garde-fou central : si un outil d'écriture apparaissait un jour dans le
    registre, le modèle pourrait modifier le planning sans confirmation. Ce test
    échoue au moment où ça arrive, pas six mois plus tard en production.
    """
    registry = PlanningToolHandlers(backend=None, token="t").as_registry()

    interdits = ("create", "delete", "update", "optimize", "generate", "publish")
    for name in registry:
        assert not any(verbe in name for verbe in interdits), name


def test_le_registre_expose_les_outils_attendus():
    registry = PlanningToolHandlers(backend=None, token="t").as_registry()

    assert set(registry) == {
        "get_planning_overview",
        "explain_violations",
        "get_active_constraints",
        "suggest_constraint",
    }


# ─────────────────────────────────────────────────────────────────────────────
# Nettoyage du JSON produit par le modèle
# ─────────────────────────────────────────────────────────────────────────────

def test_supprime_aggregate_sur_une_portee_lesson():
    """
    Écart observé : le modèle remplit "aggregate" même sur une règle par séance,
    ce que le parseur strict du backend refuse.
    """
    cleaned = _clean({"scope": "LESSON", "aggregate": {"metric": "TOTAL_HOURS"}, "weight": 100})
    assert "aggregate" not in cleaned


def test_supprime_les_cles_nulles():
    cleaned = _clean({"scope": "LESSON", "aggregate": None, "logic": None, "weight": 10})
    assert cleaned == {"scope": "LESSON", "weight": 10}


def test_convertit_les_valeurs_numeriques_en_chaines():
    """Le contrat attend des chaînes ; le modèle écrit volontiers 15 sans guillemets."""
    cleaned = _clean(
        {
            "scope": "LESSON",
            "conditions": [{"field": "class.size", "operator": "GREATER_THAN", "value": 30}],
        }
    )
    assert cleaned["conditions"][0]["value"] == "30"


def test_conserve_le_bloc_aggregate_sur_une_portee_agregee():
    payload = {
        "scope": "TEACHER_DAY",
        "aggregate": {"metric": "TOTAL_HOURS", "operator": "GREATER_THAN", "value": 3},
    }
    assert _clean(payload)["aggregate"]["value"] == 3


def test_poids_flottant_ramene_a_un_entier():
    assert _clean({"scope": "LESSON", "weight": 100.0})["weight"] == 100


# ─────────────────────────────────────────────────────────────────────────────
# Prompt construit depuis le catalogue réel
# ─────────────────────────────────────────────────────────────────────────────

SCHEMA = {
    "fields": [
        {"name": "subject.code", "type": "STRING", "label": "Code de la matière", "example": "MATH"},
        {
            "name": "sessionType",
            "type": "ENUM",
            "label": "Type de séance",
            "allowedValues": ["COURS", "TD", "TP", "SPORT"],
        },
    ],
    "operators": [{"name": "EQUALS"}, {"name": "GREATER_THAN"}],
    "scopes": [{"name": "LESSON"}, {"name": "TEACHER_DAY"}],
    "severities": [{"name": "HARD"}, {"name": "SOFT"}],
    "aggregateMetrics": [{"name": "TOTAL_HOURS"}],
    "example": {"scope": "LESSON"},
}


def test_le_prompt_liste_les_valeurs_fermees():
    """
    C'est exactement là que les modèles inventent (« MAGISTRAL » pour un type de
    séance). Les valeurs admises doivent apparaître littéralement.
    """
    described = _describe_schema(SCHEMA)

    assert "COURS, TD, TP, SPORT" in described
    assert "subject.code (STRING)" in described


def test_le_prompt_contient_les_operateurs_et_portees():
    described = _describe_schema(SCHEMA)

    assert "EQUALS" in described
    assert "TEACHER_DAY" in described


def test_le_message_de_correction_reprend_les_erreurs_du_backend():
    instruction = _repair_instruction(["champ inconnu « matiere »"])

    assert "matiere" in instruction
    assert "sans texte autour" in instruction


# ─────────────────────────────────────────────────────────────────────────────
# Boucle traduction → validation
# ─────────────────────────────────────────────────────────────────────────────

class FakeOllama:
    """Rejoue une liste de réponses préparées, et note ce qu'on lui a envoyé."""

    def __init__(self, responses):
        self._responses = list(responses)
        self.calls = []

    async def chat(self, messages, tools=None, json_mode=False, options=None):
        self.calls.append({"messages": messages, "json_mode": json_mode})
        return {"content": self._responses.pop(0)}


class FakeBackend:
    def __init__(self, analyses):
        self._analyses = list(analyses)
        self.analyzed = []

    async def get_dsl_schema(self, token):
        return SCHEMA

    async def analyze_constraint(self, token, dsl, school_year_id=None, profile_id=None):
        self.analyzed.append(dsl)
        return self._analyses.pop(0)


VALID_ANALYSIS = {
    "valid": True,
    "summary": "Interdire — portée séance : Code de la matière est égal à MATH",
    "verdict": "Règle valide et applicable : 12 séance(s) concernée(s) sur 400.",
    "feasible": True,
    "conflicts": [],
    "matchedLessons": 12,
    "totalLessons": 400,
    "examples": ["Mathématiques · TA1"],
    "warnings": [],
}


def translator(ollama, backend, max_repair_attempts=1):
    return DslTranslator(ollama, backend, max_repair_attempts=max_repair_attempts)


@pytest.mark.asyncio
async def test_traduction_reussie_renvoie_le_verdict_du_backend():
    dsl = {"scope": "LESSON", "conditions": [], "severity": "HARD", "weight": 100}
    ollama = FakeOllama([json.dumps(dsl)])
    backend = FakeBackend([VALID_ANALYSIS])

    proposal = await translator(ollama, backend).translate("pas de maths le vendredi", "tok")

    assert proposal.valid is True
    assert proposal.attempts == 1
    # Le message rendu à l'utilisateur est celui du backend, pas une phrase du
    # modèle : ce qui s'affiche doit être ce qui s'appliquera.
    assert proposal.message == VALID_ANALYSIS["verdict"]
    assert proposal.matched_lessons == 12


@pytest.mark.asyncio
async def test_le_json_est_toujours_demande_en_mode_contraint():
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])

    await translator(ollama, backend).translate("une règle", "tok")

    assert ollama.calls[0]["json_mode"] is True


@pytest.mark.asyncio
async def test_une_regle_refusee_est_corrigee_avec_l_erreur_du_backend():
    ollama = FakeOllama(
        [
            json.dumps({"scope": "LESSON", "conditions": [{"field": "matiere"}]}),
            json.dumps({"scope": "LESSON", "conditions": [{"field": "subject.code"}]}),
        ]
    )
    backend = FakeBackend(
        [{"valid": False, "errors": ["champ inconnu « matiere »"]}, VALID_ANALYSIS]
    )

    proposal = await translator(ollama, backend).translate("pas de maths", "tok")

    assert proposal.valid is True
    assert proposal.attempts == 2
    # La correction doit être guidée par l'erreur réelle du validateur.
    second_prompt = ollama.calls[1]["messages"][-1]["content"]
    assert "matiere" in second_prompt


@pytest.mark.asyncio
async def test_abandon_apres_les_tentatives_prevues():
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})] * 2)
    backend = FakeBackend([{"valid": False, "errors": ["toujours faux"]}] * 2)

    proposal = await translator(ollama, backend).translate("règle absurde", "tok")

    assert proposal.valid is False
    assert proposal.dsl is None
    assert "toujours faux" in proposal.errors


@pytest.mark.asyncio
async def test_json_illisible_ne_fait_pas_planter():
    ollama = FakeOllama(["pas du json", "toujours pas"])
    backend = FakeBackend([])

    proposal = await translator(ollama, backend).translate("règle", "tok")

    assert proposal.valid is False
    assert backend.analyzed == []


@pytest.mark.asyncio
async def test_une_regle_infaisable_reste_valide_mais_signalee():
    """
    Distinction essentielle : la règle est bien formée (elle passe la
    validation) mais irréalisable avec les données. L'utilisateur doit voir les
    deux informations, pas un simple « refusé ».
    """
    analysis = dict(VALID_ANALYSIS)
    analysis["feasible"] = False
    analysis["conflicts"] = [
        {
            "type": "WORKLOAD_EXCEEDS_LIMIT",
            "subject": "Enseignant Ahmed",
            "detail": "Charge obligatoire de 24 heures, maximum autorisé 15 heures.",
        }
    ]
    ollama = FakeOllama([json.dumps({"scope": "TEACHER_DAY"})])
    backend = FakeBackend([analysis])

    proposal = await translator(ollama, backend).translate("Ahmed max 3h par jour", "tok")

    assert proposal.valid is True
    assert proposal.feasible is False
    assert proposal.conflicts[0]["subject"] == "Enseignant Ahmed"
