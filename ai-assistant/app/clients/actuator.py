"""
Client pour les endpoints Actuator de Spring Boot.

Complémentaire de Prometheus : Actuator donne l'état INSTANTANÉ et binaire
(UP/DOWN), là où Prometheus donne des tendances numériques.
"""

import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class ActuatorClient:
    def __init__(self, base_url: str, timeout: float = 5.0):
        self._client = httpx.AsyncClient(base_url=base_url, timeout=timeout)

    async def close(self) -> None:
        await self._client.aclose()

    async def health(self) -> dict[str, Any]:
        """
        Récupère /actuator/health.

        Structure attendue (avec show-details: always) :
        {
          "status": "UP",
          "components": {
            "db":        {"status": "UP", "details": {"database": "PostgreSQL"}},
            "diskSpace": {"status": "UP", "details": {"free": 12345678}},
            "ping":      {"status": "UP"}
          }
        }

        ⚠️ Spring Boot renvoie HTTP 503 quand le statut global est DOWN.
        C'est une réponse VALIDE et informative, pas une erreur : on ne
        doit surtout pas la traiter comme un échec réseau. C'est précisément
        le cas que l'assistant doit savoir rapporter.
        """
        try:
            response = await self._client.get("/actuator/health")
            # Pas de raise_for_status() ici : voir le commentaire ci-dessus.
            if response.status_code in (200, 503):
                return response.json()
            logger.warning("Actuator a renvoyé %s", response.status_code)
            return {"status": "UNKNOWN"}
        except httpx.HTTPError as exc:
            logger.warning("Actuator injoignable : %s", exc)
            return {"status": "UNKNOWN"}

    async def component_status(self, component: str) -> str:
        """État d'un composant précis : 'db', 'diskSpace', 'ping'…"""
        data = await self.health()
        return (
            data.get("components", {})
            .get(component, {})
            .get("status", "UNKNOWN")
        )