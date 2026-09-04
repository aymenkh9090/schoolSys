"""
Exécution des outils du cahier de séance.

Comme pour les outils Planning, les handlers sont construits PAR REQUÊTE et
portent le jeton de l'appelant : le périmètre des données n'est jamais un
paramètre que le modèle pourrait renseigner, c'est une propriété de l'objet qui
exécute l'outil.
"""

import logging

from app.clients.backend import BackendError
from app.services.retrieval import CahierRetriever, SeanceDocument

logger = logging.getLogger(__name__)

# Longueur d'un extrait rendu au modèle. Un cahier bavard peut faire plusieurs
# milliers de caractères ; cinq de ces cahiers saturent le contexte d'un 7B et
# la réponse se dégrade au lieu de s'enrichir.
EXTRAIT_MAX = 400

INDISPONIBLE = (
    "unavailable: les cahiers de séance n'ont pas pu être consultés."
)

# « Aucune donnée » et « service en panne » se ressemblent pour un petit modèle,
# qui répond « réessayez plus tard » dans les deux cas. Or les deux appellent des
# actions opposées : relancer l'informatique, ou relancer les enseignants. Ces
# messages disent donc explicitement au modèle que le vide n'est pas une panne.
VIDE_RECHERCHE = (
    "Aucune séance renseignée ne correspond à cette recherche. "
    "Ce n'est PAS une panne : le cahier ne contient simplement rien sur ce sujet. "
    "Dis-le tel quel, ne suppose rien, et n'invite pas à réessayer plus tard."
)

VIDE_COUVERTURE = (
    "Aucune séance n'est renseignée dans le cahier sur la période indexée. "
    "Ce n'est PAS une panne : les enseignants n'ont rien saisi. Dis-le tel quel, "
    "suggère de leur rappeler de remplir leur cahier de séance, et n'invite pas "
    "à réessayer plus tard."
)


def _tronquer(texte: str, limite: int = EXTRAIT_MAX) -> str:
    texte = " ".join(texte.split())
    return texte if len(texte) <= limite else texte[: limite - 1].rstrip() + "…"


def _rendre(doc: SeanceDocument, score: float) -> str:
    """
    Un extrait, préfixé de sa source.

    Le score est transmis au modèle volontairement : entre deux extraits, il lui
    indique lequel porte la réponse. Il n'a pas à le citer, et le prompt le lui
    interdit — mais le lui cacher reviendrait à présenter comme équivalents un
    document très proche de la question et un document à peine pertinent.
    """
    lignes = [f"[{doc.citation}] (pertinence {score:.2f})"]
    if doc.enseignant:
        lignes.append(f"Enseignant : {doc.enseignant}")
    if doc.chapitre:
        lignes.append(f"Chapitre : {doc.chapitre}")
    lignes.append(_tronquer(doc.texte))
    if doc.travail_demande:
        echeance = f" (pour le {doc.date_echeance})" if doc.date_echeance else ""
        lignes.append(f"Travail demandé{echeance} : {_tronquer(doc.travail_demande, 200)}")
    return "\n".join(lignes)


class CahierToolHandlers:
    """Table de dispatch des outils, liée à un appelant et à son périmètre."""

    def __init__(self, retriever: CahierRetriever, cle: str, token: str):
        self._retriever = retriever
        self._cle = cle
        self._token = token

    async def search_cahier_seances(self, query: str) -> str:
        if not query or not query.strip():
            return "Error: the 'query' parameter is required and must not be empty."

        try:
            resultats = await self._retriever.rechercher(self._cle, self._token, query)
        except BackendError as exc:
            logger.warning("Corpus indisponible : %s", exc.detail)
            return f"{INDISPONIBLE} ({exc.detail})"
        except Exception:
            logger.exception("Échec de la recherche sémantique")
            return INDISPONIBLE

        if not resultats:
            # Message explicite plutôt que chaîne vide : sans lui, le modèle
            # comble le silence par une réponse générale et fausse.
            return VIDE_RECHERCHE

        return "\n\n".join(_rendre(doc, score) for doc, score in resultats)

    async def get_couverture_programme(self) -> str:
        try:
            couverture = await self._retriever.couverture(self._cle, self._token)
        except BackendError as exc:
            logger.warning("Corpus indisponible : %s", exc.detail)
            return f"{INDISPONIBLE} ({exc.detail})"
        except Exception:
            logger.exception("Échec du calcul de couverture")
            return INDISPONIBLE

        if not couverture:
            return VIDE_COUVERTURE

        lignes = ["classe | matière | séances | dernière séance | dernier chapitre"]
        for e in couverture:
            lignes.append(
                " | ".join(
                    (
                        e["classe"] or "?",
                        e["matiere"] or "?",
                        str(e["seances"]),
                        e["derniere_date"] or "?",
                        _tronquer(e["dernier_chapitre"] or "?", 60),
                    )
                )
            )
        return "\n".join(lignes)

    def as_registry(self) -> dict:
        return {
            "search_cahier_seances": self.search_cahier_seances,
            "get_couverture_programme": self.get_couverture_programme,
        }
