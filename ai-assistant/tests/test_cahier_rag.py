"""
Tests de la recherche sémantique sur les cahiers de séance.

Deux familles, et la seconde compte davantage que la première :

  - la mécanique de recherche (normalisation, seuil, agrégation) ;
  - les invariants de sécurité — pas d'écriture exposée au modèle, un index par
    compte et non par établissement, aucun moyen de désigner un autre enseignant.

Une régression sur la première dégrade la pertinence ; une régression sur la
seconde fait lire à quelqu'un ce qui ne le regarde pas.
"""

import asyncio
import math

import pytest

from app.services.retrieval import (
    CahierIndex,
    CahierIndexStore,
    CahierRetriever,
    SeanceDocument,
    _normalise,
    _texte,
    _to_document,
)
from app.tools.cahier_handlers import CahierToolHandlers, _tronquer


# ─────────────────────────────────────────────────────────────────────────────
# Construction des documents
# ─────────────────────────────────────────────────────────────────────────────

ENTREE = {
    "id": 12,
    "seanceAppelId": 45,
    "enseignantNom": "Amine Trabelsi",
    "classeCode": "7B",
    "matiereLibelle": "Mathématiques",
    "dateSeance": "2026-03-12",
    "chapitre": "Théorème de Pythagore",
    "sujet": "Introduction au théorème",
    "activites": "Démonstration puis exercices 12 à 18",
    "remarques": "Les élèves bloquent sur la racine carrée",
    "travailDemande": "Exercices 19 et 20",
    "dateEcheance": "2026-03-19",
}


def test_le_texte_indexe_porte_la_classe_et_la_matiere():
    """
    Sans les libellés, « où en est la 7B en maths ? » n'aurait aucun mot commun
    avec les séances de 7B en maths, et la recherche se rabattrait sur la seule
    proximité thématique.
    """
    texte = _texte(ENTREE)
    assert "7B" in texte
    assert "Mathématiques" in texte
    assert "Pythagore" in texte


def test_une_seance_sans_texte_n_est_pas_indexee():
    """Un cahier ouvert puis abandonné ne ferait que diluer la recherche."""
    assert _to_document({"id": 1, "dateSeance": "2026-01-05"}) is None


def test_la_citation_reste_lisible_sans_libelles():
    doc = _to_document({"id": 7, "sujet": "Révisions"})
    assert doc is not None
    assert "7" in doc.citation


def test_la_citation_assemble_date_classe_matiere():
    doc = _to_document(ENTREE)
    assert doc.citation == "2026-03-12 — 7B — Mathématiques"


# ─────────────────────────────────────────────────────────────────────────────
# Mécanique de recherche
# ─────────────────────────────────────────────────────────────────────────────

def test_la_normalisation_ramene_a_la_norme_un():
    vecteur = _normalise([3.0, 4.0])
    assert math.isclose(math.sqrt(sum(v * v for v in vecteur)), 1.0)


def test_un_vecteur_nul_ne_fait_pas_diviser_par_zero():
    assert _normalise([0.0, 0.0]) == [0.0, 0.0]


def _index_deux_documents() -> CahierIndex:
    maths = SeanceDocument(1, "maths", "2026-03-12", "7B", "Maths", "", "", "", "")
    sport = SeanceDocument(2, "sport", "2026-03-13", "8A", "Sport", "", "", "", "")
    return CahierIndex(
        documents=[maths, sport],
        vecteurs=[_normalise([1.0, 0.0]), _normalise([0.0, 1.0])],
    )


def test_la_recherche_classe_par_similarite():
    resultats = _index_deux_documents().rechercher(
        [0.9, 0.1], top_k=2, plancher=0.0, marge=1.0
    )
    assert [doc.seance_id for doc, _ in resultats] == [1, 2]


def test_le_plancher_ecarte_une_question_hors_sujet():
    """
    Si même la meilleure séance reste sous le plancher, la réponse est une liste
    vide — pas la séance la moins mauvaise, que le modèle commenterait avec
    assurance.
    """
    index = _index_deux_documents()
    assert index.rechercher([1.0, 1.0], top_k=2, plancher=0.9, marge=0.05) == []


def test_la_marge_coupe_la_queue_du_classement():
    """
    Une réponse exacte ne doit pas être noyée par des séances qui n'ont fait que
    passer le plancher.
    """
    index = _index_deux_documents()
    resultats = index.rechercher([1.0, 0.05], top_k=2, plancher=0.5, marge=0.05)
    assert len(resultats) == 1
    assert resultats[0][0].seance_id == 1


def test_la_marge_conserve_les_extraits_aussi_pertinents():
    """Deux séances également proches doivent revenir toutes les deux."""
    index = _index_deux_documents()
    resultats = index.rechercher([1.0, 1.0], top_k=2, plancher=0.5, marge=0.05)
    assert len(resultats) == 2


def test_un_index_vide_ne_plante_pas_sur_le_plancher():
    assert CahierIndex().rechercher([1.0, 0.0], top_k=5, plancher=0.6, marge=0.03) == []


# ─────────────────────────────────────────────────────────────────────────────
# Couverture du programme — le comptage, pas l'estimation
# ─────────────────────────────────────────────────────────────────────────────

def _doc(seance_id, classe, matiere, date_seance, chapitre):
    return SeanceDocument(
        seance_id, f"{classe} {matiere} {chapitre}", date_seance,
        classe, matiere, "Prof", chapitre, "", "",
    )


def test_la_couverture_retient_la_seance_la_plus_recente():
    index = CahierIndex(
        documents=[
            _doc(1, "7B", "Maths", "2026-01-10", "Fractions"),
            _doc(2, "7B", "Maths", "2026-03-12", "Pythagore"),
            _doc(3, "8A", "SVT", "2026-02-01", "Respiration"),
        ]
    )
    couverture = {(e["classe"], e["matiere"]): e for e in index.couverture()}

    assert couverture[("7B", "Maths")]["seances"] == 2
    assert couverture[("7B", "Maths")]["derniere_date"] == "2026-03-12"
    assert couverture[("7B", "Maths")]["dernier_chapitre"] == "Pythagore"
    assert couverture[("8A", "SVT")]["seances"] == 1


def test_la_couverture_est_vide_sans_document():
    assert CahierIndex().couverture() == []


# ─────────────────────────────────────────────────────────────────────────────
# Invariants de sécurité
# ─────────────────────────────────────────────────────────────────────────────

def test_le_registre_ne_contient_que_des_lectures():
    """
    Le cahier de séance fait foi sur ce qui a été enseigné et se verrouille.
    Si un outil d'écriture apparaissait dans le registre, ce test échouerait le
    jour même, et non en production.
    """
    registry = CahierToolHandlers(retriever=None, cle="t", token="tok").as_registry()

    interdits = ("create", "delete", "update", "write", "lock", "enregistr")
    for name in registry:
        assert not any(verbe in name for verbe in interdits), name


def test_le_registre_expose_les_deux_outils_attendus():
    registry = CahierToolHandlers(retriever=None, cle="t", token="tok").as_registry()
    assert set(registry) == {"search_cahier_seances", "get_couverture_programme"}


def test_aucun_outil_ne_prend_d_identifiant_d_enseignant():
    """
    L'invariant de B1 : le modèle ne dispose d'AUCUN paramètre par lequel viser
    un autre enseignant. Le périmètre vient du jeton, pas de l'appel d'outil.
    """
    import inspect

    from app.tools.cahier_definitions import TOOL_DEFINITIONS

    registry = CahierToolHandlers(retriever=None, cle="t", token="tok").as_registry()
    for handler in registry.values():
        for nom in inspect.signature(handler).parameters:
            assert "enseignant" not in nom.lower()
            assert "teacher" not in nom.lower()
            assert "tenant" not in nom.lower()

    for outil in TOOL_DEFINITIONS:
        proprietes = outil["function"]["parameters"]["properties"]
        for nom in proprietes:
            assert "teacher" not in nom.lower()
            assert "school" not in nom.lower()


# ─────────────────────────────────────────────────────────────────────────────
# Cache d'index : une entrée par compte
# ─────────────────────────────────────────────────────────────────────────────

class _BackendFactice:
    """Renvoie un corpus différent par jeton, comme le ferait le vrai backend."""

    def __init__(self):
        self.appels = 0

    async def get_cahier_corpus(self, token, depuis=None, limite=500):
        self.appels += 1
        return [{**ENTREE, "id": 1, "sujet": f"corpus de {token}"}]


class _OllamaFactice:
    async def embed(self, texts):
        return [[1.0, 0.0] for _ in texts]


def _store(backend, **kwargs):
    return CahierIndexStore(backend, _OllamaFactice(), **kwargs)


def test_deux_comptes_ne_partagent_pas_le_meme_index():
    """
    Le corpus dépend du compte : un cache indexé sur l'établissement servirait à
    un enseignant l'index de son directeur. Rien ne planterait — simplement, la
    mauvaise personne lirait.
    """
    backend = _BackendFactice()
    store = _store(backend)

    async def scenario():
        a = await store.obtenir("tenant1:prof-a", "jeton-a")
        b = await store.obtenir("tenant1:prof-b", "jeton-b")
        return a, b

    a, b = asyncio.run(scenario())
    assert backend.appels == 2
    assert a is not b
    assert a.documents[0].texte != b.documents[0].texte


def test_le_meme_compte_reutilise_son_index():
    backend = _BackendFactice()
    store = _store(backend)

    async def scenario():
        await store.obtenir("tenant1:prof-a", "jeton-a")
        await store.obtenir("tenant1:prof-a", "jeton-a")

    asyncio.run(scenario())
    assert backend.appels == 1


def test_deux_questions_simultanees_n_indexent_qu_une_fois():
    """Le verrou par clé évite deux vectorisations complètes en parallèle."""
    backend = _BackendFactice()
    store = _store(backend)

    async def scenario():
        await asyncio.gather(
            store.obtenir("tenant1:prof-a", "jeton-a"),
            store.obtenir("tenant1:prof-a", "jeton-a"),
        )

    asyncio.run(scenario())
    assert backend.appels == 1


def test_l_index_expire():
    backend = _BackendFactice()
    store = _store(backend, ttl_seconds=0.0)

    async def scenario():
        await store.obtenir("tenant1:prof-a", "jeton-a")
        await store.obtenir("tenant1:prof-a", "jeton-a")

    asyncio.run(scenario())
    assert backend.appels == 2


def test_le_cache_est_borne():
    """Sans borne, un service qui tourne des semaines garderait tous les index."""
    backend = _BackendFactice()
    store = _store(backend, max_entries=2)

    async def scenario():
        for i in range(5):
            await store.obtenir(f"tenant1:prof-{i}", f"jeton-{i}")

    asyncio.run(scenario())
    assert len(store._index) <= 2


# ─────────────────────────────────────────────────────────────────────────────
# Rendu des extraits
# ─────────────────────────────────────────────────────────────────────────────

def test_une_recherche_sans_resultat_interdit_explicitement_de_supposer():
    """
    Le silence est le pire retour possible : le modèle le comble par une réponse
    générale et fausse. Le message doit lui dire quoi faire de ce vide.
    """
    handlers = CahierToolHandlers(
        retriever=_RetrieverVide(), cle="t", token="tok"
    )
    reponse = asyncio.run(handlers.search_cahier_seances("Pythagore"))
    assert "Aucune séance" in reponse
    assert "ne suppose rien" in reponse.lower()


def test_le_vide_ne_se_presente_pas_comme_une_panne():
    """
    Observé en conditions réelles : sur un corpus vide, le modèle répondait
    « je n'ai pas pu vérifier, réessayons plus tard » — ce qui envoie le
    directeur vers l'informatique alors qu'il faut relancer ses enseignants.
    Les deux messages de vide doivent écarter cette lecture explicitement.
    """
    from app.tools.cahier_handlers import INDISPONIBLE, VIDE_COUVERTURE, VIDE_RECHERCHE

    for message in (VIDE_RECHERCHE, VIDE_COUVERTURE):
        assert "PAS une panne" in message
        assert "réessayer" in message
        # Le mot qui déclenche la confusion ne doit apparaître que dans le
        # message de panne, jamais dans ceux de corpus vide.
        assert "unavailable" not in message

    assert "unavailable" in INDISPONIBLE


def test_une_recherche_vide_est_rejetee_sans_appeler_le_moteur():
    handlers = CahierToolHandlers(retriever=None, cle="t", token="tok")
    assert "Error" in asyncio.run(handlers.search_cahier_seances("  "))


def test_l_extrait_porte_sa_citation():
    handlers = CahierToolHandlers(
        retriever=_RetrieverFixe(), cle="t", token="tok"
    )
    reponse = asyncio.run(handlers.search_cahier_seances("Pythagore"))
    assert "[2026-03-12 — 7B — Mathématiques]" in reponse
    assert "Exercices 19 et 20" in reponse


def test_l_extrait_est_tronque():
    assert _tronquer("a" * 500).endswith("…")
    assert len(_tronquer("a" * 500)) <= 400


class _RetrieverVide:
    async def rechercher(self, cle, token, question):
        return []


class _RetrieverFixe:
    async def rechercher(self, cle, token, question):
        return [(_to_document(ENTREE), 0.87)]
