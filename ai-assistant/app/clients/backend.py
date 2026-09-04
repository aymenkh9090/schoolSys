"""
Client HTTP vers l'API métier Spring Boot (module Planning).

Principe directeur : **l'assistant ne parle jamais à la base de données**. Tout
passe par les mêmes routes REST que l'interface React, avec le jeton de
l'utilisateur qui pose la question. Trois conséquences, toutes voulues :

  - l'isolation multi-tenant est celle du backend, appliquée une seule fois,
    au même endroit, pour tous les clients ;
  - une règle que l'utilisateur n'a pas le droit de créer, l'assistant ne peut
    pas la créer non plus — le 403 vient de Spring, pas d'un test local ;
  - la validation du DSL est celle du serveur, pas une copie approximative
    maintenue en Python et destinée à diverger.
"""

import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class BackendError(RuntimeError):
    """
    Échec d'un appel au backend, avec le message métier renvoyé par Spring.

    On conserve le statut : 400 et 403 se racontent différemment à
    l'utilisateur (« la règle est refusée » contre « vous n'avez pas le droit »).
    """

    def __init__(self, status_code: int, detail: str):
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


class BackendClient:
    def __init__(self, base_url: str, timeout: float = 20.0):
        self._client = httpx.AsyncClient(base_url=base_url, timeout=timeout)

    async def close(self) -> None:
        await self._client.aclose()

    # ── catalogue DSL ─────────────────────────────────────────────────────────

    async def get_dsl_schema(self, token: str) -> dict:
        return await self._get("/api/planning/constraints/dsl/schema", token)

    # ── contraintes ───────────────────────────────────────────────────────────

    async def get_constraint_definitions(self, token: str) -> list:
        return await self._get("/api/planning/constraints/definitions", token)

    async def get_profiles(self, token: str) -> list:
        return await self._get("/api/planning/constraints/profiles", token)

    async def get_profile(self, token: str, profile_id: int) -> dict:
        return await self._get(f"/api/planning/constraints/profiles/{profile_id}", token)

    async def get_custom_constraints(self, token: str, profile_id: int | None = None) -> list:
        params = {"profileId": profile_id} if profile_id is not None else None
        return await self._get("/api/planning/constraints/custom", token, params=params)

    async def analyze_constraint(
        self,
        token: str,
        dsl: dict,
        school_year_id: int | None = None,
        profile_id: int | None = None,
    ) -> dict:
        """
        Valide une règle candidate et cherche les conflits, SANS rien enregistrer.

        C'est l'appel central du flux « langage naturel → DSL » : ce que le
        modèle propose n'a de valeur qu'une fois passé par ce contrôle.
        """
        payload: dict[str, Any] = {"dsl": dsl}
        if school_year_id is not None:
            payload["schoolYearId"] = school_year_id
        if profile_id is not None:
            payload["constraintProfileId"] = profile_id
        return await self._post("/api/planning/constraints/custom/analyze", token, payload)

    async def create_custom_constraint(self, token: str, payload: dict) -> dict:
        """
        Enregistre la règle. Appelée uniquement depuis la route de confirmation,
        jamais depuis la boucle d'outils du modèle.
        """
        return await self._post("/api/planning/constraints/custom", token, payload)

    async def get_suggestions(
        self, token: str, school_year_id: int | None = None, job_id: int | None = None
    ) -> dict:
        params = {}
        if school_year_id is not None:
            params["schoolYearId"] = school_year_id
        if job_id is not None:
            params["jobId"] = job_id
        return await self._get(
            "/api/planning/constraints/custom/suggestions", token, params=params or None
        )

    # ── emplois du temps ──────────────────────────────────────────────────────

    async def get_jobs(self, token: str, school_year_id: int | None = None) -> list:
        # Le backend nomme ce paramètre academicYearId sur cette route (et
        # schoolYearId sur celle des suggestions) : on s'aligne sur l'API telle
        # qu'elle est plutôt que d'inventer un nom cohérent qui échouerait.
        params = {"academicYearId": school_year_id} if school_year_id is not None else None
        return await self._get("/api/planning/timetable/jobs", token, params=params)

    async def get_score_explanation(self, token: str, job_id: int) -> dict:
        """
        Explication déterministe du score, produite par Timefold puis mise en
        forme par le backend. C'est la SEULE source de chiffres sur les
        violations : l'assistant les restitue, il ne les recalcule pas et ne
        les invente pas.
        """
        return await self._get(f"/api/planning/timetable/jobs/{job_id}/score-explanation", token)

    # ── cahier de séance ──────────────────────────────────────────────────────

    async def get_cahier_corpus(
        self, token: str, depuis: str | None = None, limite: int = 500
    ) -> list:
        """
        Séances renseignées de l'appelant, prêtes à être indexées.

        Le périmètre — ses propres séances pour un enseignant, tout
        l'établissement pour un directeur — est décidé par le BACKEND à partir du
        compte porté par le jeton. Cette route ne prend volontairement aucun
        identifiant d'enseignant : il n'existe donc aucun paramètre par lequel
        l'assistant pourrait réclamer le cahier de quelqu'un d'autre, ni par
        erreur ni sur demande d'un modèle mal inspiré.
        """
        params: dict[str, Any] = {"limite": limite}
        if depuis:
            params["depuis"] = depuis
        return await self._get("/api/v1/cahier/corpus", token, params=params)

    # ── plomberie ─────────────────────────────────────────────────────────────

    async def _get(self, path: str, token: str, params: dict | None = None):
        return await self._request("GET", path, token, params=params)

    async def _post(self, path: str, token: str, payload: dict):
        return await self._request("POST", path, token, json=payload)

    async def _request(self, method: str, path: str, token: str, **kwargs):
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        try:
            response = await self._client.request(method, path, headers=headers, **kwargs)
        except httpx.HTTPError as exc:
            logger.warning("Backend injoignable (%s %s) : %s", method, path, exc)
            raise BackendError(503, "Le backend SmartSchool est injoignable.") from exc

        if response.status_code >= 400:
            raise BackendError(response.status_code, _detail(response))

        if not response.content:
            return {}
        return response.json()


def _detail(response: httpx.Response) -> str:
    """
    Message d'erreur exploitable.

    Le GlobalExceptionHandler du backend renvoie un corps JSON structuré. On en
    extrait la phrase métier : c'est elle qu'on montre à l'utilisateur, et c'est
    elle qu'on réinjecte dans le prompt quand le modèle doit corriger son DSL.
    Un « HTTP 400 » nu ne dirait rien ni à l'un ni à l'autre.
    """
    try:
        body = response.json()
    except ValueError:
        return response.text.strip() or f"Erreur HTTP {response.status_code}"

    if isinstance(body, dict):
        for key in ("message", "detail", "error"):
            value = body.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return f"Erreur HTTP {response.status_code}"
