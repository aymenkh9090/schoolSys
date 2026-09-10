"""
Client bas niveau pour l'API HTTP de Prometheus.

Responsabilité unique : exécuter du PromQL et renvoyer des valeurs Python.
Aucune interprétation, aucun seuil, aucun formatage — c'est le rôle de
services/metrics.py.

"""


import logging
import math
import time

import httpx


logger = logging.getLogger(__name__)

class PrometheusClient:

    def __init__(self, base_url: str, timeout: float = 5.0):

        # Un AsyncClient réutilisable maintient le pool de connexions ouvert.
        # En créer un par requête coûterait un handshake TCP à chaque appel.
        self._client = httpx.AsyncClient(base_url=base_url , timeout=timeout)

    async def close(self) -> None:
        await self._client.aclose()

    async def query_scalar(self,promql: str) -> float | None:
        """
        Exécute une requête instantanée et renvoie la première valeur.

        Renvoie None si : la métrique n'existe pas, le résultat est vide,
        la valeur est NaN, ou Prometheus est injoignable.

        Ne lève JAMAIS d'exception : une source d'observation indisponible
        est une dégradation, pas une panne. Le service doit répondre
        « donnée indisponible », jamais planter.
        """

        try:
            response = await self._client.get(
                "/api/v1/query", params={"query":promql}
            )

            response.raise_for_status()
            payload = response.json()

            results = payload.get("data",{}).get("result",[])

            if not results:
                logger.debug("PromQL sans résultat : %s", promql)
                return None

            # value = [timestamp, "valeur"] — la valeur est une chaîne
            raw = results[0]["value"][1]
            value = float(raw)

            # histogram_quantile() renvoie NaN sans trafic dans la fenêtre.
            # NaN casserait la sérialisation JSON en sortie de l'API.
            if math.isnan(value) or math.isinf(value):
                return None

            return value

        except httpx.HTTPError as exc:
            logger.warning("Prometheus injoignable [%s] : %s", promql, exc)
            return None
        except (KeyError, IndexError, ValueError) as exc:
            logger.warning("Réponse Prometheus inattendue [%s] : %s", promql, exc)
            return None

    async def query_range(
        self, promql: str, minutes: int, step_seconds: int
    ) -> list[float]:
        """
        Exécute une requête sur une plage et renvoie la suite des valeurs.

        Sert la courbe de tendance des tuiles de supervision : là où
        `query_scalar` répond « où en est-on », celle-ci répond « d'où vient-on ».

        <h4>Les trous sont retirés, jamais comblés</h4>

        Prometheus rend `NaN` pour un point sans donnée — service arrêté,
        quantile sans trafic dans la fenêtre. Ces points sont écartés plutôt que
        remplacés par zéro : une courbe qui plonge à zéro pendant un redémarrage
        raconte un effondrement du service qui n'a pas eu lieu. Le tracé saute
        alors le trou, ce qui est le rendu honnête d'une mesure absente.

        Renvoie une liste vide — jamais d'exception — si la métrique n'existe
        pas ou si Prometheus est injoignable, pour la même raison que
        `query_scalar` : une source d'observation indisponible est une
        dégradation, pas une panne.
        """
        points = await self.query_range_points(promql, minutes, step_seconds)
        return [valeur for _horodatage, valeur in points]

    async def query_range_points(
        self, promql: str, minutes: int, step_seconds: int
    ) -> list[tuple[float, float]]:
        """
        La même plage que `query_range`, mais chaque valeur garde son horodatage.

        Une courbe de douze points peut se passer de l'axe du temps ; une
        régression, non. Un redémarrage retire vingt points de la série : sans
        horodatage, les points suivants glisseraient de vingt minutes vers le
        passé, et la pente calculée serait fausse d'autant.

        Mêmes règles que `query_range` : trous retirés, jamais d'exception.
        Horodatages en secondes Unix, dans l'ordre croissant.
        """
        fin = time.time()
        debut = fin - minutes * 60

        try:
            response = await self._client.get(
                "/api/v1/query_range",
                params={
                    "query": promql,
                    "start": debut,
                    "end": fin,
                    "step": f"{step_seconds}s",
                },
            )
            response.raise_for_status()
            results = response.json().get("data", {}).get("result", [])

            if not results:
                logger.debug("PromQL sans série : %s", promql)
                return []

            points: list[tuple[float, float]] = []
            # values = [[timestamp, "valeur"], ...] — les valeurs sont des chaînes.
            for horodatage, brut in results[0].get("values", []):
                valeur = float(brut)
                if not (math.isnan(valeur) or math.isinf(valeur)):
                    points.append((float(horodatage), valeur))
            return points

        except httpx.HTTPError as exc:
            logger.warning("Prometheus injoignable [%s] : %s", promql, exc)
            return []
        except (KeyError, IndexError, ValueError, TypeError) as exc:
            logger.warning("Réponse Prometheus inattendue [%s] : %s", promql, exc)
            return []

    async def query_by_label(
        self, promql: str, label: str
    ) -> dict[str, float]:
        """
        Requête multi-séries : renvoie {valeur_du_label: valeur_numérique}.

        Utilisé pour « les endpoints les plus lents », où chaque série
        correspond à un `uri` différent.
        """
        try:
            response = await self._client.get(
                "/api/v1/query", params={"query": promql}
            )
            response.raise_for_status()
            results = response.json().get("data", {}).get("result", [])

            output: dict[str, float] = {}
            for item in results:
                key = item.get("metric", {}).get(label)
                if key is None:
                    continue
                try:
                    value = float(item["value"][1])
                except (KeyError, IndexError, ValueError):
                    continue
                if not math.isnan(value):
                    output[key] = value
            return output

        except httpx.HTTPError as exc:
            logger.warning("Prometheus injoignable [%s] : %s", promql, exc)
            return {}

    async def is_available(self) -> bool:
        """Sonde de disponibilité, pour le /health du service Python."""
        try:
            response = await self._client.get("/-/healthy")
            return response.status_code == 200
        except httpx.HTTPError:
            return False



