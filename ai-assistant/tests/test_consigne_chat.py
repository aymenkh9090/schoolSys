"""
Tests de la route de question libre sur la circulaire.

Ce qu'ils protègent tient en une phrase : **le modèle ne doit jamais pouvoir
répondre sans avoir reçu d'articles**. Tout le reste — la citation, le refus,
la complétion des tableaux — découle de là.

La qualité des réponses rédigées, elle, ne se teste pas ici : elle dépend du
modèle, elle a été mesurée à la main sur neuf questions dont les réponses sont
vérifiables dans le PDF, et ce qui en est ressorti est consigné dans les
commentaires du service plutôt que figé dans une assertion qu'un changement de
modèle ferait échouer sans rien apprendre.
"""

import pytest

from app.services.consigne_assistant import (
    AUCUN_ARTICLE,
    MODELE_INDISPONIBLE,
    ConsigneAssistantService,
    _completer_tableaux,
    _extraits,
)
from app.services.consigne_retrieval import ArticleConsigne


def _article(identifiant, texte="Texte de l'article.", page=2, portee=None):
    return ArticleConsigne(
        id=identifiant, page=page, section="Recommandations", texte=texte,
        texte_ar="نصّ", portee=portee, severite=None,
    )


ART_II_2 = _article(
    "II.2",
    "Les emplois du temps sont établis sur la base de six heures d'enseignement "
    "par jour au maximum.",
    portee="TEACHER_DAY",
)
ART_III_4 = _article("III.4", "Les travaux pratiques se déroulent en salle spécialisée.", page=3)

TABLEAUX = [
    _article("T.1", "| Arabe | 2+1+1+1 · 5 h |", page=5),
    _article("T.2", "| Arabe | 2 / 2 |", page=6),
    _article("T.3", "| Arabe | 2+1+1+1 · 5 h |", page=7),
]


class FakeConsigne:
    """Retriever jouable. `articles` expose le corpus, comme le vrai."""

    def __init__(self, resultats=(), corpus=(), exception=None):
        self._resultats = list(resultats)
        self.articles = list(corpus) or list(resultats)
        self._exception = exception
        self.questions = []

    async def rechercher(self, question):
        self.questions.append(question)
        if self._exception is not None:
            raise self._exception
        return [(a, 0.7) for a in self._resultats]


class FakeOllama:
    def __init__(self, reponse="Réponse rédigée.", exception=None):
        self._reponse = reponse
        self._exception = exception
        self.appels = []

    async def chat(self, messages, tools=None, json_mode=False, options=None):
        self.appels.append(messages)
        if self._exception is not None:
            raise self._exception
        return {"content": self._reponse}


# ─────────────────────────────────────────────────────────────────────────────
# La recherche précède toujours la génération
# ─────────────────────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_sans_article_le_modele_n_est_pas_appele_du_tout():
    """
    L'invariant central. Une question hors sujet ne doit pas atteindre le modèle :
    pas de génération, donc rien à inventer — et une réponse immédiate au lieu de
    5 à 30 secondes de rédaction sur CPU.

    C'est aussi la différence avec une boucle d'outils, où le modèle est appelé
    d'abord et décide ensuite s'il cherche.
    """
    ollama = FakeOllama()
    service = ConsigneAssistantService(ollama, FakeConsigne([]))

    reponse = await service.ask("Quel temps fera-t-il demain ?")

    assert ollama.appels == []
    assert reponse.answer == AUCUN_ARTICLE
    assert reponse.sources == []


@pytest.mark.asyncio
async def test_le_refus_dit_que_le_corpus_ne_couvre_pas_le_sujet():
    """
    La formulation compte : « la circulaire ne traite pas ce sujet » et « je n'ai
    pas compris » n'appellent pas la même action. Un directeur qui cherche à
    savoir si une règle existe a besoin de la première.
    """
    service = ConsigneAssistantService(FakeOllama(), FakeConsigne([]))

    reponse = await service.ask("Puis-je faire cours le dimanche ?")

    assert "ne traite pas ce sujet" in reponse.answer
    # Et on lui dit ce que la circulaire couvre, au lieu de le laisser deviner.
    assert "emplois du temps" in reponse.answer


@pytest.mark.asyncio
async def test_les_articles_entrent_dans_le_prompt_avec_leur_citation_toute_faite():
    """
    Le modèle recopie la référence, il ne l'assemble pas. Un 7B qui compose
    « [§ II.2, p. 2] » à partir d'un numéro et d'une page se trompe de page de
    temps en temps — et une citation fausse est pire qu'une absence de citation,
    puisqu'elle a l'apparence d'une preuve.
    """
    ollama = FakeOllama()
    service = ConsigneAssistantService(ollama, FakeConsigne([ART_II_2]))

    await service.ask("Combien d'heures par jour pour un enseignant ?")

    prompt = ollama.appels[0][0]["content"]
    assert "[§ II.2, p. 2]" in prompt
    assert "six heures d'enseignement" in prompt


@pytest.mark.asyncio
async def test_la_question_est_transmise_telle_quelle_a_la_recherche():
    consigne = FakeConsigne([ART_III_4])
    service = ConsigneAssistantService(FakeOllama(), consigne)

    await service.ask("Où doivent se faire les TP ?")

    assert consigne.questions == ["Où doivent se faire les TP ?"]


@pytest.mark.asyncio
async def test_les_sources_accompagnent_la_reponse():
    """
    Les sources sont ce qui permet de ne pas croire le modèle sur parole. Elles
    portent la page et le texte arabe : la traduction reste vérifiable.
    """
    service = ConsigneAssistantService(FakeOllama(), FakeConsigne([ART_II_2]))

    reponse = await service.ask("Combien d'heures par jour ?")

    # `sources` est typé list[ConsigneArticle] : pydantic valide les dicts du
    # service en modèles, et c'est cette forme-là que le client reçoit.
    assert [s.id for s in reponse.sources] == ["II.2"]
    assert reponse.sources[0].citation == "Circulaire n°66/2024, p. 2, § II.2"
    assert reponse.sources[0].texte_ar == "نصّ"


# ─────────────────────────────────────────────────────────────────────────────
# Les trois tableaux se lisent ensemble
# ─────────────────────────────────────────────────────────────────────────────

def test_un_tableau_retrouve_entraine_ses_deux_freres():
    """
    Correctif d'un défaut OBSERVÉ : la recherche n'ayant remonté que le tableau
    des collèges pilotes, le modèle a répondu sur un collège ordinaire en
    INVENTANT une citation au § T.1 qu'il n'avait pas sous les yeux.

    Compléter la fratrie est déterministe, là où élargir les seuils de la
    recherche aurait déplacé le problème sur toutes les autres questions.
    """
    complet = _completer_tableaux([TABLEAUX[2]], TABLEAUX)

    assert [a.id for a in complet] == ["T.3", "T.1", "T.2"]


def test_les_articles_ordinaires_n_entrainent_aucun_tableau():
    """La complétion ne vaut que pour les tableaux : ailleurs, elle serait du bruit."""
    complet = _completer_tableaux([ART_II_2], TABLEAUX + [ART_II_2])

    assert [a.id for a in complet] == ["II.2"]


def test_la_completion_ne_duplique_pas_un_tableau_deja_present():
    complet = _completer_tableaux([TABLEAUX[0], TABLEAUX[1]], TABLEAUX)

    assert sorted(a.id for a in complet) == ["T.1", "T.2", "T.3"]


@pytest.mark.asyncio
async def test_la_completion_s_applique_bout_en_bout():
    service = ConsigneAssistantService(FakeOllama(), FakeConsigne([TABLEAUX[2]], TABLEAUX))

    reponse = await service.ask("Combien d'heures d'arabe en 7ème ?")

    assert {s.id for s in reponse.sources} == {"T.1", "T.2", "T.3"}


# ─────────────────────────────────────────────────────────────────────────────
# Dégradations
# ─────────────────────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_un_modele_en_panne_rend_quand_meme_les_articles():
    """
    La recherche a réussi, seule la rédaction a échoué. Rendre les extraits vaut
    mieux qu'un message d'erreur seul : ils répondent souvent directement.
    """
    ollama = FakeOllama(exception=RuntimeError("Ollama injoignable"))
    service = ConsigneAssistantService(ollama, FakeConsigne([ART_III_4]))

    reponse = await service.ask("Où faire les TP ?")

    assert reponse.answer == MODELE_INDISPONIBLE
    assert [s.id for s in reponse.sources] == ["III.4"]


@pytest.mark.asyncio
async def test_une_reponse_vide_du_modele_est_traitee_comme_une_panne():
    """Un contenu vide n'est pas une réponse : l'afficher tel quel serait une page blanche."""
    service = ConsigneAssistantService(FakeOllama(reponse="   "), FakeConsigne([ART_III_4]))

    reponse = await service.ask("Où faire les TP ?")

    assert reponse.answer == MODELE_INDISPONIBLE
    assert len(reponse.sources) == 1


@pytest.mark.asyncio
async def test_une_recherche_en_panne_ne_fait_pas_appeler_le_modele():
    """
    Sans corpus, il n'y a rien à reformuler. Appeler le modèle malgré tout, c'est
    exactement lui demander de répondre de mémoire.
    """
    ollama = FakeOllama()
    service = ConsigneAssistantService(
        ollama, FakeConsigne(exception=RuntimeError("index indisponible"))
    )

    reponse = await service.ask("Combien d'heures de maths ?")

    assert ollama.appels == []
    assert reponse.answer == AUCUN_ARTICLE


@pytest.mark.asyncio
async def test_la_duree_est_mesuree():
    service = ConsigneAssistantService(FakeOllama(), FakeConsigne([ART_II_2]))

    reponse = await service.ask("Une question")

    assert reponse.duration_ms >= 0


def test_les_extraits_portent_la_section_de_chaque_article():
    """Le contexte de section aide le modèle à distinguer les articles élève des articles enseignant."""
    rendu = _extraits([ART_II_2, ART_III_4])

    assert "[§ II.2, p. 2] — Recommandations" in rendu
    assert "[§ III.4, p. 3] — Recommandations" in rendu
