"""
Tests du cours déposé et de la conversation à son sujet.

Ce qu'ils protègent : **le service ne doit jamais faire semblant**. Un PDF
scanné, un document expiré, celui d'un collègue — chacun doit produire un refus
nommé, jamais une réponse plausible sur un texte qui n'a pas été lu.

La qualité pédagogique des réponses ne se teste pas ici : elle dépend du modèle.
Ce qui se teste, c'est la mécanique autour — ce qui entre, ce qui est refusé,
qui a le droit de relire quoi, et ce qui est dit à l'enseignant sur ce qui a
réellement été lu.
"""

import pytest

from app.services.cours_document import (
    HISTORIQUE_MAX,
    MAX_CARACTERES,
    CoursDocumentService,
    DocumentStore,
    PreparationImpossible,
    extraire_texte,
    tronquer,
)
from tests.pdf_minimal import pdf_avec_texte, pdf_sans_texte

COURS = (
    "Le theoreme de Pythagore.\n"
    "Dans un triangle rectangle, le carre de l hypotenuse est egal\n"
    "a la somme des carres des deux autres cotes.\n"
    "Reciproque et applications au calcul de distances.\n"
) * 3


class OllamaFactice:
    """Double du client : rend ce qu'on lui dit, et retient ce qu'il a reçu."""

    def __init__(self, contenu: str | Exception = "Réponse du modèle."):
        self.contenu = contenu
        self.messages = None
        self.options = None
        self.timeout = None

    async def chat(self, messages, tools=None, json_mode=False, options=None, timeout=None):
        if isinstance(self.contenu, Exception):
            raise self.contenu
        self.messages = messages
        self.options = options
        self.timeout = timeout
        return {"content": self.contenu}


# ── Extraction ──────────────────────────────────────────────────────────────


def test_extrait_le_texte_et_compte_les_pages():
    texte, pages = extraire_texte(pdf_avec_texte([COURS, COURS]))
    assert pages == 2
    assert "Pythagore" in texte


def test_refuse_un_fichier_qui_n_est_pas_un_pdf():
    with pytest.raises(PreparationImpossible, match="ouvert comme PDF"):
        extraire_texte(b"ceci est un fichier texte, pas un PDF")


def test_refuse_un_pdf_scanne_en_nommant_la_cause():
    """
    Le cas qui justifie le contrôle : sans lui, le modèle recevrait deux lignes
    de bruit et inventerait un cours plausible — la pire sortie possible,
    parce qu'elle est crédible.
    """
    with pytest.raises(PreparationImpossible, match="scanné"):
        extraire_texte(pdf_sans_texte(4))


# ── Troncature ──────────────────────────────────────────────────────────────


def test_un_texte_court_n_est_pas_tronque():
    texte, coupe = tronquer("Un cours bref.")
    assert texte == "Un cours bref."
    assert coupe is False


def test_un_texte_long_est_coupe_sur_une_frontiere_de_paragraphe():
    texte, coupe = tronquer("paragraphe.\n\n" * 2000)
    assert coupe is True
    assert len(texte) <= MAX_CARACTERES
    assert texte.endswith("paragraphe.")


# ── Le magasin de documents ─────────────────────────────────────────────────


def test_un_document_se_relit_par_son_identifiant():
    store = DocumentStore()
    depose = store.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    relu = store.lire("prof-1", depose.identifiant)

    assert relu.nom_fichier == "cours.pdf"
    assert "Pythagore" in relu.texte


def test_le_document_d_un_collegue_reste_hors_de_portee():
    """
    Le point d'isolation : l'identifiant seul ne suffit pas, il faut être le
    compte qui a déposé. Sans ce couplage, un identifiant deviné ouvrirait le
    cours de quelqu'un d'autre.
    """
    store = DocumentStore()
    depose = store.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    with pytest.raises(PreparationImpossible, match="plus disponible"):
        store.lire("prof-2", depose.identifiant)


def test_un_document_expire_n_est_plus_lisible():
    store = DocumentStore(ttl_seconds=-1)
    depose = store.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    with pytest.raises(PreparationImpossible, match="Déposez-le à nouveau"):
        store.lire("prof-1", depose.identifiant)


def test_le_magasin_est_borne_en_nombre():
    """Un service qui garde tout finit par tomber pour une raison sans rapport."""
    store = DocumentStore(max_documents=3)
    for i in range(6):
        store.deposer("prof-1", pdf_avec_texte([COURS]), f"cours-{i}.pdf")

    assert len(store) == 3


def test_le_plus_ancien_part_en_premier():
    store = DocumentStore(max_documents=2)
    premier = store.deposer("prof-1", pdf_avec_texte([COURS]), "a.pdf")
    store.deposer("prof-1", pdf_avec_texte([COURS]), "b.pdf")
    store.deposer("prof-1", pdf_avec_texte([COURS]), "c.pdf")

    with pytest.raises(PreparationImpossible):
        store.lire("prof-1", premier.identifiant)


# ── Dépôt ───────────────────────────────────────────────────────────────────


def test_le_depot_annonce_ce_qui_a_ete_lu():
    service = CoursDocumentService(OllamaFactice())

    depose = service.deposer("prof-1", pdf_avec_texte([COURS, COURS]), "pythagore.pdf")

    assert depose.nom_fichier == "pythagore.pdf"
    assert depose.pages == 2
    assert depose.caracteres_lus > 0
    assert depose.tronque is False


def test_le_depot_signale_la_troncature():
    """
    Une troncature silencieuse serait un mensonge par omission : l'enseignant
    croirait que les réponses couvrent tout son chapitre.
    """
    service = CoursDocumentService(OllamaFactice())

    depose = service.deposer("prof-1", pdf_avec_texte([COURS * 30, COURS * 30]), "long.pdf")

    assert depose.tronque is True
    assert depose.caracteres_lus <= MAX_CARACTERES


# ── Conversation ────────────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_la_demande_de_l_enseignant_parvient_avec_le_cours():
    ollama = OllamaFactice("Voici un QCM de cinq questions…")
    service = CoursDocumentService(ollama)
    depose = service.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    reponse = await service.repondre(
        "prof-1", depose.document_id, "Fais un QCM de 5 questions avec le corrigé."
    )

    assert reponse.answer.startswith("Voici un QCM")
    assert reponse.duration_ms >= 0
    # Le cours est dans le prompt, et la demande est le dernier message.
    assert any("Pythagore" in m["content"] for m in ollama.messages)
    assert ollama.messages[-1]["content"].startswith("Fais un QCM")
    # La génération dispose de sa propre enveloppe de temps et de longueur.
    assert ollama.timeout is not None and ollama.timeout > 90
    assert ollama.options["num_predict"] > 300


@pytest.mark.asyncio
async def test_aucune_consigne_ne_force_un_format_de_sortie():
    """
    Le service ne décide pas de ce qu'il faut produire. Un prompt qui imposerait
    « résume puis propose des exercices » empêcherait « explique-moi la partie 3 »
    — la demande de l'enseignant serait noyée sous celle du développeur.
    """
    ollama = OllamaFactice()
    service = CoursDocumentService(ollama)
    depose = service.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    await service.repondre("prof-1", depose.document_id, "Explique la réciproque.")

    systeme = ollama.messages[0]["content"].lower()
    assert "uniquement sur le texte du cours" in systeme
    assert "exactement ce qui est demandé" in systeme


@pytest.mark.asyncio
async def test_l_historique_est_transmis_mais_borne():
    ollama = OllamaFactice()
    service = CoursDocumentService(ollama)
    depose = service.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    historique = [
        {"role": "user" if i % 2 == 0 else "assistant", "content": f"tour {i}"}
        for i in range(10)
    ]
    await service.repondre("prof-1", depose.document_id, "Et la suite ?", historique)

    tours = [m for m in ollama.messages if m["content"].startswith("tour ")]
    assert len(tours) == HISTORIQUE_MAX
    # Ce sont les DERNIERS tours qui sont gardés, pas les premiers.
    assert tours[-1]["content"] == "tour 9"


@pytest.mark.asyncio
async def test_une_reponse_vide_ne_passe_pas_pour_une_reponse():
    service = CoursDocumentService(OllamaFactice("   "))
    depose = service.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    with pytest.raises(PreparationImpossible, match="rien répondu"):
        await service.repondre("prof-1", depose.document_id, "Résume.")


@pytest.mark.asyncio
async def test_une_panne_du_modele_est_distinguee_d_un_document_illisible():
    service = CoursDocumentService(OllamaFactice(RuntimeError("connexion refusée")))
    depose = service.deposer("prof-1", pdf_avec_texte([COURS]), "cours.pdf")

    with pytest.raises(PreparationImpossible, match="indisponible"):
        await service.repondre("prof-1", depose.document_id, "Résume.")
