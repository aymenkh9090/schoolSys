"""
Prévision de la consommation des ressources, par régression linéaire.

Des fonctions pures : des couples (horodatage, valeur) en entrée, un verdict en
sortie. Ni Prometheus, ni horloge, ni état — c'est ce qui les rend testables sur
des séries écrites à la main. Lire l'historique est l'affaire de
services/metrics.py ; ici, on ne fait que le calcul.

La droite est recalculée à chaque demande sur l'historique le plus récent : pas
d'apprentissage préalable, pas de modèle sauvegardé.

RÈGLE : ne jamais annoncer une échéance que les données ne soutiennent pas. Une
prévision fausse est pire que pas de prévision — elle fait intervenir pour rien,
puis ignorer la suivante. Voir docs/plan-prediction-ressources.md, § 5.
"""

import math
from dataclasses import dataclass
from typing import Literal

# En dessous, pas de régression : une droite passe toujours à peu près par
# trois points, et n'en dit rien.
POINTS_MIN = 10

# Part minimale des points attendus sur la fenêtre. Sous la moitié, le service a
# redémarré ou Prometheus a décroché : la droite ne décrirait qu'un fragment.
COUVERTURE_MIN = 0.5

# Part de la variation que la droite doit expliquer pour qu'on lui fasse
# confiance. Sans ce seuil, le système annoncerait « alerte dans 40 h » sur une
# droite tirée à travers du bruit.
R2_MIN = 0.5

# Marge sur la pente, en erreurs-types : environ 95 % de confiance.
Z_CONFIANCE = 2.0

Verdict = Literal["insuffisant", "incertain", "hausse", "stable", "baisse"]


@dataclass(frozen=True)
class Ajustement:
    """La droite des moindres carrés, et ce qu'elle vaut."""

    # Vitesse de variation, en unité de la série par seconde.
    pente: float
    # Valeur de la droite à t = 0 (l'origine des horodatages Unix).
    ordonnee: float
    # Part de la variation expliquée par la droite : 0 = bruit pur, 1 = droite parfaite.
    r2: float
    # Erreur-type de la pente : l'imprécision que le bruit laisse sur elle.
    erreur_pente: float
    n: int

    def valeur(self, t: float) -> float:
        return self.pente * t + self.ordonnee


@dataclass(frozen=True)
class Prevision:
    """
    Le verdict, et les chiffres qui le justifient.

    Les durées ne sont renseignées que pour le verdict `hausse`. Pour tous les
    autres, elles restent à None — jamais une durée par défaut.
    """

    verdict: Verdict
    n: int
    r2: float | None = None
    # Unité de la série par heure.
    pente_par_heure: float | None = None
    # Valeur de la droite au dernier point, et au bout de l'horizon.
    valeur_actuelle: float | None = None
    valeur_horizon: float | None = None
    horizon_min: float | None = None
    # 0 = seuil déjà franchi ; None = pas atteint avant l'horizon.
    minutes_avant_alerte: float | None = None
    minutes_avant_incident: float | None = None


def ajuster(points: list[tuple[float, float]]) -> Ajustement:
    """
    Droite des moindres carrés sur les points (tᵢ, yᵢ).

    Avec t̄ et ȳ les moyennes :
      - pente     a  = Σ(tᵢ − t̄)(yᵢ − ȳ) / Σ(tᵢ − t̄)²
      - ordonnée  b  = ȳ − a·t̄
      - R²           = 1 − Σ(yᵢ − ŷᵢ)² / Σ(yᵢ − ȳ)²
      - erreur-type  = √( Σ(yᵢ − ŷᵢ)² / (n − 2) / Σ(tᵢ − t̄)² )

    `statistics.linear_regression` ferait la pente et l'ordonnée, mais ni le R²
    ni l'erreur-type ; écrites en clair, les quatre formules se citent telles
    quelles.

    Lève ValueError si les points ne couvrent pas au moins deux instants
    distincts : la pente n'y est pas définie.
    """
    n = len(points)
    if n < 2:
        raise ValueError("Deux points au moins sont nécessaires")

    t_moy = sum(t for t, _ in points) / n
    y_moy = sum(y for _, y in points) / n

    stt = sum((t - t_moy) ** 2 for t, _ in points)
    if stt == 0:
        raise ValueError("Tous les points sont au même instant")

    sty = sum((t - t_moy) * (y - y_moy) for t, y in points)
    syy = sum((y - y_moy) ** 2 for _, y in points)

    pente = sty / stt
    ordonnee = y_moy - pente * t_moy
    residus = sum((y - (pente * t + ordonnee)) ** 2 for t, y in points)

    # Série constante : la droite horizontale passe par tous les points, il ne
    # reste rien d'inexpliqué. Le R² vaut 0/0 par la formule, 1 par le sens.
    r2 = 1.0 if syy == 0 else max(0.0, 1 - residus / syy)
    erreur = math.sqrt(residus / (n - 2) / stt) if n > 2 else math.inf

    return Ajustement(pente, ordonnee, r2, erreur, n)


def prevoir(
    points: list[tuple[float, float]],
    seuils: dict[str, float],
    attendus: int,
) -> Prevision:
    """
    Le verdict sur une série : où va-t-elle, et quand touchera-t-elle un seuil ?

    `seuils` porte `warning` et `critical`, ceux de `THRESHOLDS`. `attendus` est
    le nombre de points que la fenêtre aurait dû rendre sans aucun trou.

    **L'horizon est la durée observée**, jamais au-delà : une heure d'historique
    ne permet pas de prédire la journée. Un seuil atteint plus loin que l'horizon
    donne « stable à l'horizon », pas une durée extrapolée.
    """
    n = len(points)
    if n < POINTS_MIN or n < COUVERTURE_MIN * attendus:
        return Prevision("insuffisant", n)

    points = sorted(points)
    maintenant = points[-1][0]
    horizon_s = maintenant - points[0][0]
    if horizon_s <= 0:
        return Prevision("insuffisant", n)

    aj = ajuster(points)
    actuelle = aj.valeur(maintenant)
    chiffres = dict(
        n=n,
        r2=aj.r2,
        pente_par_heure=aj.pente * 3600,
        valeur_actuelle=actuelle,
        valeur_horizon=aj.valeur(maintenant + horizon_s),
        horizon_min=horizon_s / 60,
    )

    if aj.r2 < R2_MIN:
        # La droite n'explique pas la série : aucune échéance ne se déduit
        # d'elle. Une question garde pourtant une réponse — même avec la pente
        # la plus défavorable que le bruit autorise, le seuil d'alerte est-il
        # hors d'atteinte dans l'horizon ? Sur une heap qui oscille entre 2 et
        # 8 % face à un seuil à 75 %, oui : c'est « stable », pas « on ne sait
        # pas ». Juste sous le seuil, la même oscillation reste « incertain ».
        pente_haute = max(0.0, aj.pente + Z_CONFIANCE * aj.erreur_pente)
        if actuelle + pente_haute * horizon_s < seuils["warning"]:
            return Prevision("stable", **chiffres)
        return Prevision("incertain", **chiffres)

    if aj.pente < 0:
        return Prevision("baisse", **chiffres)

    alerte = _minutes_avant(seuils["warning"], actuelle, aj.pente, horizon_s)
    if alerte is None:
        # Pente nulle, ou seuil au-delà de l'horizon.
        return Prevision("stable", **chiffres)

    return Prevision(
        "hausse",
        **chiffres,
        minutes_avant_alerte=alerte,
        minutes_avant_incident=_minutes_avant(
            seuils["critical"], actuelle, aj.pente, horizon_s
        ),
    )


def _minutes_avant(
    seuil: float, actuelle: float, pente: float, horizon_s: float
) -> float | None:
    """(seuil − ŷ(maintenant)) / a, en minutes — si le seuil tombe dans l'horizon."""
    if pente <= 0:
        return None
    if actuelle >= seuil:
        return 0.0
    secondes = (seuil - actuelle) / pente
    return secondes / 60 if secondes <= horizon_s else None
