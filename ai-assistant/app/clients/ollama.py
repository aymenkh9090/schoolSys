"""
Client minimal pour l'API Ollama.

On utilise httpx directement plutôt que la bibliothèque `ollama` :
 - une dépendance de moins,
 - l'API HTTP est stable et documentée, la lib change plus souvent,
 - le comportement est explicite et lisible.
"""

import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class OllamaClient:
    def __init__(
        self,
        base_url: str,
        model: str,
        temperature: float = 0.1,
        num_predict: int = 300,
        timeout: float = 90.0,
        embedding_model: str = "nomic-embed-text",
        embedding_timeout: float = 120.0,
    ):
        # Timeout long et assumé : sur CPU, une génération prend 5 à 30 s.
        # C'est pourquoi ce client a son propre timeout, bien plus généreux
        # que celui de Prometheus.
        self._client = httpx.AsyncClient(base_url=base_url, timeout=timeout)
        self._model = model
        self._options = {"temperature": temperature, "num_predict": num_predict}
        self._embedding_model = embedding_model
        self._embedding_timeout = embedding_timeout

    async def close(self) -> None:
        await self._client.aclose()

    async def chat(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict] | None = None,
        json_mode: bool = False,
        options: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """
        Un aller-retour avec le modèle.

        Renvoie l'objet `message` de la réponse, qui contient soit `content`
        (réponse finale), soit `tool_calls` (demande d'exécution d'outils).

        `json_mode` active le décodage contraint d'Ollama : le modèle ne peut
        alors produire que du JSON syntaxiquement valide. C'est décisif pour la
        génération de DSL — sans lui, un petit modèle encadre volontiers sa
        réponse de ```json, d'une phrase d'introduction ou d'une virgule finale,
        et on passe son temps à rattraper du texte au lieu de valider une règle.
        Le contenu, lui, reste à vérifier : bien formé ne veut pas dire correct.

        `options` surcharge ponctuellement température et longueur, sans toucher
        aux réglages du client partagé.
        """
        payload: dict[str, Any] = {
            "model": self._model,
            "messages": messages,
            "stream": False,
            "options": {**self._options, **(options or {})},
        }
        if tools:
            payload["tools"] = tools
        if json_mode:
            payload["format"] = "json"

        response = await self._client.post("/api/chat", json=payload)
        response.raise_for_status()
        return response.json().get("message", {})

    async def embed(self, texts: list[str]) -> list[list[float]]:
        """
        Vectorise une liste de textes avec le modèle d'embedding.

        Modèle distinct de celui du chat : embarquer un texte et générer une
        réponse sont deux tâches différentes, et un modèle d'embedding de 137 M
        paramètres fait le premier travail mieux et cent fois plus vite qu'un
        modèle de chat de 7 milliards.

        Le timeout est celui de la génération : une première indexation de
        plusieurs centaines de séances est longue, là où une requête isolée
        répond en quelques dizaines de millisecondes.
        """
        if not texts:
            return []

        response = await self._client.post(
            "/api/embed",
            json={"model": self._embedding_model, "input": texts},
            timeout=self._embedding_timeout,
        )
        response.raise_for_status()
        embeddings = response.json().get("embeddings") or []

        # Un embedding manquant décalerait silencieusement toute la
        # correspondance texte ↔ vecteur : on préfère échouer ici.
        if len(embeddings) != len(texts):
            raise RuntimeError(
                f"Ollama a renvoyé {len(embeddings)} vecteurs pour {len(texts)} textes."
            )
        return embeddings

    async def is_embedding_model_available(self) -> bool:
        """Vérifie que le modèle d'embedding est téléchargé (`ollama pull`)."""
        return await self._has_model(self._embedding_model)

    async def is_available(self) -> bool:
        """Vérifie qu'Ollama répond ET que le modèle de chat est téléchargé."""
        return await self._has_model(self._model)

    async def _has_model(self, model: str) -> bool:
        try:
            response = await self._client.get("/api/tags", timeout=5.0)
            if response.status_code != 200:
                return False
            models = [m["name"] for m in response.json().get("models", [])]
            # Ollama suffixe parfois le tag : "qwen2.5:3b" vs "qwen2.5:3b-instruct-q4_K_M"
            return any(m.startswith(model.split(":")[0]) for m in models)
        except httpx.HTTPError:
            return False