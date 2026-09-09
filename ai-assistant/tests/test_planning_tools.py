import json

import pytest

from app.models import ChatResponse, PlanningChatResponse
from app.services.consigne_retrieval import ArticleConsigne
from app.services.planning_assistant import PlanningAssistantService
from app.services.dsl_translator import (
    DslTranslator,
    _clean,
    _describe_schema,
    _repair_instruction,
)
from app.tools.planning_handlers import (
    MAX_OCCURRENCES_CITED,
    PlanningToolHandlers,
    _sanitize_level,
)


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


# ─────────────────────────────────────────────────────────────────────────────
# Ancrage sur la circulaire ministérielle
# ─────────────────────────────────────────────────────────────────────────────
#
# Ce que ces tests protègent n'est pas la pertinence de la recherche — elle est
# mesurée dans test_consigne_rag.py — mais deux propriétés du CHAÎNAGE, dont la
# seconde est celle qui décide de la crédibilité de l'outil :
#
#   - les articles arrivent bien dans le prompt AVANT la génération, et non
#     collés après coup à une règle déjà écrite ;
#   - la citation rendue distingue l'article dont la portée concorde avec la
#     règle produite de ceux qui ne font que voisiner.


class FakeConsigne:
    """Retriever de circulaire jouable : rend des articles préparés, ou lève."""

    def __init__(self, articles=(), exception=None):
        self._articles = list(articles)
        self._exception = exception
        self.questions = []

    async def rechercher(self, question):
        self.questions.append(question)
        if self._exception is not None:
            raise self._exception
        return [(a, 0.7) for a in self._articles]


def _article(identifiant, portee, severite, texte="Texte de l'article."):
    return ArticleConsigne(
        id=identifiant, page=2, section="Recommandations", texte=texte,
        texte_ar="نصّ", portee=portee, severite=severite,
    )


ART_II_2 = _article(
    "II.2", "TEACHER_DAY", "HARD",
    "Les emplois du temps sont établis sur la base de six heures d'enseignement "
    "par jour au maximum et de deux heures au minimum, matin ou après-midi.",
)
ART_III_2_C = _article(
    "III.2.c", "LESSON", "MEDIUM",
    "Les matières enseignées à raison de deux heures par semaine ne sont pas "
    "programmées sur deux jours consécutifs.",
)
# Un tableau de volumes horaires : retrouvé par la recherche, mais sans portée,
# donc inutilisable pour suggérer une contrainte.
ART_TABLEAU = _article("T.1", None, None, "| Arabe | 2+1+1+1 |")


@pytest.mark.asyncio
async def test_les_articles_entrent_dans_le_prompt_avant_la_generation():
    """
    L'ordre est le fond du sujet. Une citation ajoutée après coup décrirait ce
    que le modèle aurait PU lire, pas ce qu'il a lu — donc ne prouverait rien.
    """
    ollama = FakeOllama([json.dumps({"scope": "TEACHER_DAY"})])
    backend = FakeBackend([VALID_ANALYSIS])
    consigne = FakeConsigne([ART_II_2])

    await DslTranslator(ollama, backend, consigne=consigne).translate(
        "max 6h par jour pour un prof", "tok"
    )

    assert consigne.questions == ["max 6h par jour pour un prof"]
    prompt = ollama.calls[0]["messages"][0]["content"]
    assert "§ II.2" in prompt
    assert "six heures d'enseignement" in prompt
    # La portée annotée à la main est donnée explicitement : c'est tout
    # l'intérêt d'avoir annoté le corpus plutôt que de laisser un 7B déduire
    # CLASS_DAY ou TEACHER_DAY d'une phrase qui parle des deux.
    assert "TEACHER_DAY" in prompt


@pytest.mark.asyncio
async def test_le_prompt_interdit_de_traduire_l_article_au_lieu_de_la_demande():
    """
    Le risque propre à l'ancrage : un petit modèle à qui on montre un article
    traduit l'article. Le garde-fou doit être dans le prompt, pas dans l'espoir.
    """
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])

    await DslTranslator(ollama, backend, consigne=FakeConsigne([ART_II_2])).translate(
        "pas de sport le vendredi après-midi", "tok"
    )

    # Espacement normalisé : le garde-fou est réparti sur plusieurs lignes dans
    # le prompt, et c'est sa présence qui compte, pas sa mise en page.
    prompt = " ".join(ollama.calls[0]["messages"][0]["content"].lower().split())
    assert "tu traduis la phrase de l'utilisateur, et elle seule" in prompt
    assert "n'ajoute jamais une condition" in prompt


@pytest.mark.asyncio
async def test_les_tableaux_de_volumes_ne_sont_jamais_injectes():
    """
    Sans portée, un tableau ne peut suggérer ni scope ni sévérité — et ses
    milliers de caractères noieraient le catalogue DSL dans le contexte du 7B.
    """
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])
    consigne = FakeConsigne([ART_TABLEAU, ART_III_2_C])

    proposal = await DslTranslator(ollama, backend, consigne=consigne).translate(
        "combien d'heures d'arabe", "tok"
    )

    prompt = ollama.calls[0]["messages"][0]["content"]
    assert "2+1+1+1" not in prompt
    assert [s["id"] for s in proposal.sources] == ["III.2.c"]


@pytest.mark.asyncio
async def test_la_concordance_distingue_la_correspondance_du_voisinage():
    """
    C'est le test qui empêche la citation décorative. Un article remonté par la
    recherche n'est pas pour autant celui que la règle applique : l'interface
    n'a le droit d'écrire « cette règle correspond au § II.2 » que si la portée
    produite est bien celle de l'article.
    """
    ollama = FakeOllama([json.dumps({"scope": "TEACHER_DAY"})])
    backend = FakeBackend([VALID_ANALYSIS])
    consigne = FakeConsigne([ART_II_2, ART_III_2_C])

    proposal = await DslTranslator(ollama, backend, consigne=consigne).translate(
        "max 6h par jour pour un prof", "tok"
    )

    par_id = {s["id"]: s for s in proposal.sources}
    assert par_id["II.2"]["concordance"] is True       # TEACHER_DAY == TEACHER_DAY
    assert par_id["III.2.c"]["concordance"] is False   # LESSON, seulement voisin


@pytest.mark.asyncio
async def test_la_citation_porte_la_page_et_le_texte_arabe():
    """
    Une citation sans page ne se vérifie pas, et sans l'original elle s'arrête à
    notre traduction — c'est elle qu'il faudrait alors croire sur parole.
    """
    ollama = FakeOllama([json.dumps({"scope": "TEACHER_DAY"})])
    backend = FakeBackend([VALID_ANALYSIS])

    proposal = await DslTranslator(
        ollama, backend, consigne=FakeConsigne([ART_II_2])
    ).translate("max 6h par jour", "tok")

    source = proposal.sources[0]
    assert source["citation"] == "Circulaire n°66/2024, p. 2, § II.2"
    assert source["page"] == 2
    assert source["extrait_ar"] == "نصّ"


@pytest.mark.asyncio
async def test_une_regle_maison_ne_cite_aucun_article():
    """
    Le cas le plus fréquent, et il ne doit surtout pas être maquillé : « pas de
    cours pour M. Ben Salah le mercredi » est une contrainte de personne, que le
    ministère n'encadre pas. Zéro source, et l'interface peut le dire.
    """
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])

    proposal = await DslTranslator(
        ollama, backend, consigne=FakeConsigne([])
    ).translate("pas de cours pour Ben Salah le mercredi", "tok")

    assert proposal.valid is True
    assert proposal.sources == []
    assert "CIRCULAIRE" not in ollama.calls[0]["messages"][0]["content"]


@pytest.mark.asyncio
async def test_un_corpus_indisponible_degrade_la_citation_pas_la_traduction():
    """
    Sans corpus — fichier absent, Ollama muet — on perd la citation, jamais la
    règle. Une panne de traçabilité ne doit pas devenir une panne de service.
    """
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])
    consigne = FakeConsigne(exception=RuntimeError("Ollama injoignable"))

    proposal = await DslTranslator(ollama, backend, consigne=consigne).translate(
        "pas de maths le vendredi", "tok"
    )

    assert proposal.valid is True
    assert proposal.sources == []


@pytest.mark.asyncio
async def test_sans_corpus_configure_la_traduction_reste_identique():
    """Le service doit pouvoir tourner sans le fichier de circulaire."""
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])

    proposal = await translator(ollama, backend).translate("pas de maths", "tok")

    assert proposal.valid is True
    assert proposal.sources == []


@pytest.mark.asyncio
async def test_les_articles_restent_cites_quand_aucune_regle_n_est_produite():
    """
    Un échec de traduction sur un sujet que la circulaire couvre doit quand même
    dire ce que le ministère prévoit : c'est plus utile qu'un échec sec. Rien ne
    concorde alors, puisqu'il n'y a pas de règle.
    """
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})] * 2)
    backend = FakeBackend([{"valid": False, "errors": ["toujours faux"]}] * 2)
    consigne = FakeConsigne([ART_II_2])

    proposal = await DslTranslator(ollama, backend, consigne=consigne).translate(
        "max 6h par jour", "tok"
    )

    assert proposal.valid is False
    assert proposal.dsl is None
    assert [s["id"] for s in proposal.sources] == ["II.2"]
    assert proposal.sources[0]["concordance"] is False


@pytest.mark.asyncio
async def test_le_prompt_interdit_aussi_d_omettre_une_condition_demandee():
    """
    Verrouille un correctif issu d'une régression MESURÉE, pas d'une intuition.

    Avant lui, « de préférence des maths le matin » perdait sa condition
    `period = MORNING` dès qu'on lui montrait le § III.2.a — l'article nuance en
    « trois quarts le matin, un quart l'après-midi », et le modèle en concluait
    qu'il ne fallait pas fixer la demi-journée. La règle récompensait alors
    TOUTES les séances de maths, y compris celles de l'après-midi.

    Le garde-fou d'origine n'interdisait que d'AJOUTER une condition absente de
    la phrase. Le contre-exemple travaillé doit rester dans le prompt : sans
    lui, la régression revient, et elle produit une règle valide au sens du
    validateur — donc silencieuse.
    """
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])

    await DslTranslator(ollama, backend, consigne=FakeConsigne([ART_II_2])).translate(
        "de préférence des maths le matin", "tok"
    )

    prompt = " ".join(ollama.calls[0]["messages"][0]["content"].lower().split())
    assert "n'omets jamais un élément qui figure dans sa phrase" in prompt
    assert "l'erreur à ne pas commettre" in prompt
    assert '"value": "morning"' in prompt


@pytest.mark.asyncio
async def test_le_bloc_articles_supporte_des_exemples_json():
    """
    Le prompt de la circulaire contient des accolades (exemples de conditions).
    Il est donc assemblé par concaténation, jamais par str.format — qui y
    verrait des champs à substituer et lèverait KeyError à la première
    traduction. Régression rencontrée, et invisible en test unitaire tant qu'on
    n'exerce pas le chemin avec articles.
    """
    ollama = FakeOllama([json.dumps({"scope": "LESSON"})])
    backend = FakeBackend([VALID_ANALYSIS])

    proposal = await DslTranslator(
        ollama, backend, consigne=FakeConsigne([ART_II_2, ART_III_2_C])
    ).translate("une règle", "tok")

    assert proposal.valid is True
    prompt = ollama.calls[0]["messages"][0]["content"]
    # Les deux articles ET le pied de prompt ont bien été assemblés.
    assert "§ II.2" in prompt and "§ III.2.c" in prompt
    assert prompt.index("§ II.2") < prompt.index("RÈGLE ABSOLUE SUR CES ARTICLES")


# ─────────────────────────────────────────────────────────────────────────────
# Étape 4 — l'assistant désigne, sans que le modèle touche à la structure
# ─────────────────────────────────────────────────────────────────────────────
#
# La règle que ces tests protègent tient en une phrase : le modèle raconte, le
# code désigne. Le handler continue de rendre au modèle du texte court et déjà
# interprété — lui donner `occurrences` ferait annoncer par un 7B des scores et
# des identifiants qui n'existent pas — et remplit en parallèle une liste que
# l'interface transforme en puces cliquables.

RELOCATION = {
    "sessionId": 1001,
    "lessonId": 1,
    "subjectName": "Mathématiques",
    "className": "7A",
    "fromDay": "MONDAY",
    "fromStartTime": "08:00",
    "toDay": "THURSDAY",
    "toStartTime": "10:00",
    "toSlotId": 42,
    "toRoomCode": "A1",
    "text": (
        "Déplacer Mathématiques · 7A · Mr. Dupont · Lundi 08:00 vers Jeudi 10:00 "
        "— créneau libre pour l'enseignant et pour la classe, salle A1 disponible."
    ),
}

CONFLIT_ENSEIGNANT = {
    "constraintName": "TEACHER_CONFLICT",
    "label": "Conflit d'enseignant",
    "score": "-2hard/0medium/0soft",
    "count": 2,
    "examples": ["Mathématiques · 7A ↔ Mathématiques · 7B"],
    "suggestion": RELOCATION["text"],
    "occurrences": [
        {
            "label": "Mathématiques · 7A ↔ Mathématiques · 7B",
            "score": "-1hard/0medium/0soft",
            "sessions": [
                {
                    "lessonId": 1, "sessionId": 1001,
                    "subjectCode": "MATH", "subjectName": "Mathématiques",
                    "className": "7A", "teacherCode": "T1", "teacherName": "Mr. Dupont",
                    "roomCode": "A1", "day": "MONDAY", "startTime": "08:00",
                    "groupIndex": 0,
                },
                {
                    "lessonId": 2, "sessionId": 1002,
                    "subjectCode": "MATH", "subjectName": "Mathématiques",
                    "className": "7B", "teacherCode": "T1", "teacherName": "Mr. Dupont",
                    "roomCode": "B2", "day": "MONDAY", "startTime": "08:00",
                    "groupIndex": 0,
                },
            ],
            "relocation": RELOCATION,
        }
    ],
}


class FakeExplanationBackend:
    """Le backend réduit aux deux appels qu'`explain_violations` effectue."""

    def __init__(self, explanation):
        self._explanation = explanation
        self.appels = 0

    async def get_jobs(self, token, school_year_id=None):
        return [{"idTimetableJob": 7, "status": "INFEASIBLE", "scoreAchieved": "-2hard/0medium/0soft"}]

    async def get_score_explanation(self, token, job_id):
        self.appels += 1
        return self._explanation


def explication(violations, cle="hardViolations"):
    return {"score": "-2hard/0medium/0soft", "feasible": False, cle: violations}


def handlers_sur(explanation):
    return PlanningToolHandlers(FakeExplanationBackend(explanation), token="tok")


async def test_les_occurrences_deviennent_des_conflits_designes():
    handlers = handlers_sur(explication([CONFLIT_ENSEIGNANT]))

    await handlers.explain_violations()

    assert len(handlers.cited_conflicts) == 1
    conflit = handlers.cited_conflicts[0]
    assert conflit.constraint == "Conflit d'enseignant"
    assert conflit.severity == "HARD"
    assert [s.session_id for s in conflit.sessions] == [1001, 1002]
    assert [s.class_name for s in conflit.sessions] == ["7A", "7B"]


async def test_la_proposition_est_recopiee_telle_quelle():
    """
    La phrase vient du solveur, qui seul sait quel créneau est libre. La
    reformuler ici reviendrait à réécrire une garantie qu'on n'a pas donnée.
    """
    handlers = handlers_sur(explication([CONFLIT_ENSEIGNANT]))

    await handlers.explain_violations()

    proposition = handlers.cited_conflicts[0].relocation
    assert proposition.text == RELOCATION["text"]
    assert proposition.to_day == "THURSDAY"
    assert proposition.to_start_time == "10:00"
    assert proposition.to_slot_id == 42
    # La cible du PATCH : sans elle, l'interface n'a rien à déclencher.
    assert proposition.session_id == 1001


async def test_sans_creneau_libre_aucune_proposition_n_est_fabriquee():
    """Le silence est une réponse : sur un établissement saturé, c'est la bonne."""
    sans_creneau = {**CONFLIT_ENSEIGNANT, "occurrences": [
        {**CONFLIT_ENSEIGNANT["occurrences"][0], "relocation": None}
    ]}
    handlers = handlers_sur(explication([sans_creneau]))

    await handlers.explain_violations()

    assert handlers.cited_conflicts[0].relocation is None
    # Les séances restent désignées : on sait toujours OÙ est le conflit.
    assert len(handlers.cited_conflicts[0].sessions) == 2


async def test_une_proposition_sans_phrase_est_ecartee():
    """Des coordonnées sans texte ne font pas une proposition affichable."""
    tronquee = {**CONFLIT_ENSEIGNANT, "occurrences": [
        {**CONFLIT_ENSEIGNANT["occurrences"][0],
         "relocation": {"toDay": "THURSDAY", "toStartTime": "10:00"}}
    ]}
    handlers = handlers_sur(explication([tronquee]))

    await handlers.explain_violations()

    assert handlers.cited_conflicts[0].relocation is None


async def test_deux_appels_ne_cumulent_pas_les_conflits():
    """
    Le modèle peut rappeler l'outil dans le même tour — la boucle l'autorise.
    Sans remise à zéro, le directeur verrait chaque conflit deux fois.
    """
    handlers = handlers_sur(explication([CONFLIT_ENSEIGNANT]))

    await handlers.explain_violations()
    await handlers.explain_violations()

    assert len(handlers.cited_conflicts) == 1


async def test_la_severite_suit_le_niveau_de_la_violation():
    handlers = handlers_sur(explication([CONFLIT_ENSEIGNANT], cle="softViolations"))

    await handlers.explain_violations()

    assert handlers.cited_conflicts[0].severity == "SOFT"


async def test_le_texte_rendu_au_modele_ne_contient_aucune_structure():
    """
    Le garde-fou de l'étape 4 : `occurrences` ne doit jamais atteindre le prompt.

    Un 7B qui navigue dans un objet imbriqué se trompe de champ et annonce un
    identifiant qui n'existe pas. Le modèle reçoit des phrases ; les
    identifiants passent à côté de lui, par `cited_conflicts`.
    """
    handlers = handlers_sur(explication([CONFLIT_ENSEIGNANT]))

    texte = await handlers.explain_violations()

    for interdit in ("sessionId", "lessonId", "toSlotId", "occurrences", "{"):
        assert interdit not in texte
    # Ce que le modèle voit, en revanche : le constat et le remède, en clair.
    assert "Conflit d'enseignant" in texte
    assert RELOCATION["text"] in texte


async def test_le_nombre_d_occurrences_citees_est_borne():
    """Au-delà de quelques puces, le directeur ne lit plus, il fait défiler."""
    beaucoup = {**CONFLIT_ENSEIGNANT,
                "occurrences": CONFLIT_ENSEIGNANT["occurrences"] * 10}
    handlers = handlers_sur(explication([beaucoup]))

    await handlers.explain_violations()

    assert len(handlers.cited_conflicts) == MAX_OCCURRENCES_CITED


class FakeLoop:
    """
    Une boucle d'outils qui appelle l'outil, comme le modèle le ferait.

    Elle ne rend au « modèle » que le texte du handler — c'est justement le
    contrat qu'on veut tenir : la structure passe à côté de la boucle.
    """

    async def run(self, question, system_prompt, tool_definitions, registry,
                  no_tool_message, exhausted_message):
        self.texte_vu = await registry["explain_violations"]()
        return ChatResponse(
            answer="Deux cours du même professeur se chevauchent lundi à 8 h.",
            tools_used=["explain_violations"],
            duration_ms=1234,
        )


async def test_ask_rattache_les_designations_a_la_reponse():
    """
    Le bout de chaîne : ce que le handler a désigné arrive jusqu'à l'interface.

    Les conflits sont relus sur l'objet handler après la boucle, et non extraits
    de la génération : c'est ce qui garantit qu'aucun identifiant affiché ne
    vient du modèle.
    """
    loop = FakeLoop()
    service = PlanningAssistantService(
        loop,
        FakeExplanationBackend(explication([CONFLIT_ENSEIGNANT])),
        translator=None,
    )

    reponse = await service.ask("pourquoi ce planning n'est-il pas bon ?", "tok")

    assert isinstance(reponse, PlanningChatResponse)
    assert reponse.answer.startswith("Deux cours")
    # Ce que la boucle porte déjà ne doit pas se perdre au passage.
    assert reponse.tools_used == ["explain_violations"]
    assert reponse.duration_ms == 1234
    # Et ce que le code a désigné arrive en plus.
    assert len(reponse.conflicts) == 1
    assert reponse.conflicts[0].relocation.text == RELOCATION["text"]
    assert [s.session_id for s in reponse.conflicts[0].sessions] == [1001, 1002]


async def test_les_identifiants_ne_sont_jamais_passes_au_modele():
    loop = FakeLoop()
    service = PlanningAssistantService(
        loop,
        FakeExplanationBackend(explication([CONFLIT_ENSEIGNANT])),
        translator=None,
    )

    reponse = await service.ask("où sont les conflits ?", "tok")

    # Le texte qu'a vu la boucle ne porte aucun identifiant…
    assert "1001" not in loop.texte_vu
    # …alors que la réponse rendue à l'interface, si.
    assert reponse.conflicts[0].sessions[0].session_id == 1001
