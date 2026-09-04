"""
Validation du JWT Keycloak.

PyJWKClient télécharge les clés publiques du realm et les met en cache.
Aucun secret partagé n'est nécessaire : la vérification repose sur la
cryptographie asymétrique (Keycloak signe avec sa clé privée, on vérifie
avec sa clé publique).
"""

import logging
from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import PyJWKClient

from app.config import Settings, get_settings

logger = logging.getLogger(__name__)

# auto_error=False : on gère nous-mêmes l'absence d'en-tête, pour pouvoir
# court-circuiter proprement quand auth_enabled vaut False.
bearer_scheme = HTTPBearer(auto_error=False)

# Client JWKS unique, initialisé au démarrage (voir main.py).
_jwks_client: PyJWKClient | None = None


def init_jwks_client(settings: Settings) -> None:
    global _jwks_client
    _jwks_client = PyJWKClient(
        settings.keycloak_jwks_url,
        cache_keys=True,
        # Les clés Keycloak tournent rarement : 1 h de cache évite un appel
        # réseau à chaque requête, sans risque pratique.
        lifespan=3600,
    )
    logger.info("Client JWKS initialisé : %s", settings.keycloak_jwks_url)


def _decode(
    credentials: HTTPAuthorizationCredentials | None,
    settings: Settings,
) -> tuple[dict, str]:
    """
    Vérifie signature, émetteur et expiration, puis renvoie (claims, jeton brut).

    Factorisé parce que deux dépendances l'utilisent — celle du monitoring et
    celle du planning. Dupliquer une validation de jeton, c'est se condamner à
    ne la corriger qu'à moitié le jour où elle est fausse.
    """
    if credentials is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="En-tête Authorization manquant",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if _jwks_client is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Service d'authentification non initialisé",
        )

    token = credentials.credentials

    try:
        signing_key = _jwks_client.get_signing_key_from_jwt(token)
        payload = jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            issuer=settings.keycloak_issuer,
            options={
                # ⚠️ PIÈGE CLASSIQUE : par défaut, un token utilisateur Keycloak
                # porte aud="account", pas le client_id. Vérifier l'audience
                # sans avoir configuré un audience mapper dans Keycloak fait
                # échouer TOUS les tokens avec "Invalid audience".
                # On désactive la vérification d'audience et on s'appuie sur
                # l'issuer + le rôle, ce qui est suffisant ici.
                "verify_aud": False,
                "verify_exp": True,
                "verify_signature": True,
            },
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expiré"
        )
    except jwt.InvalidTokenError as exc:
        logger.warning("Token invalide : %s", exc)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Token invalide"
        )
    except Exception:
        # Typiquement : Keycloak injoignable au moment de récupérer le JWKS.
        logger.exception("Échec de la validation du token")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Impossible de valider le token",
        )

    return payload, token


async def require_super_admin(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    settings: Settings = Depends(get_settings),
) -> dict:
    """
    Dépendance FastAPI : valide le token et exige le rôle super admin.

    Usage :
        @app.post("/chat")
        async def chat(user: dict = Depends(require_super_admin)): ...
    """
    # Échappatoire de développement, jamais activée en production.
    if not settings.auth_enabled:
        logger.warning("AUTH DÉSACTIVÉE — mode développement uniquement")
        return {"preferred_username": "dev", "realm_access": {"roles": []}}

    payload, _token = _decode(credentials, settings)

    # Rôles realm — même emplacement que KeycloakJwtAuthenticationConverter.java
    roles = payload.get("realm_access", {}).get("roles", [])
    if settings.required_role not in roles:
        logger.warning(
            "Accès refusé pour %s : rôles=%s",
            payload.get("preferred_username"),
            roles,
        )
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Rôle {settings.required_role} requis",
        )

    return payload


# ─────────────────────────────────────────────────────────────────────────────
# Utilisateur authentifié
# ─────────────────────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class AuthenticatedUser:
    """
    L'appelant, tel que le décrit son jeton.

    Le jeton brut est conservé parce que l'assistant le REJOUE vers le backend
    Spring : c'est ce qui garantit qu'il ne peut ni lire ni écrire quoi que ce
    soit que l'utilisateur ne pourrait pas faire lui-même. Un compte de service
    partagé aurait été plus simple, et aurait ouvert exactement la faille que
    l'architecture multi-tenant cherche à fermer — un établissement obtenant,
    via l'assistant, des données d'un autre.
    """

    token: str
    username: str
    tenant_id: str | None
    roles: tuple[str, ...]
    claims: dict

    def has_any_role(self, roles: list[str]) -> bool:
        return any(role in self.roles for role in roles)

    @property
    def cache_key(self) -> str:
        """
        Identité stable de l'appelant, pour indexer un cache par utilisateur.

        Fondée sur `sub`, l'identifiant Keycloak : il ne change pas quand un
        utilisateur est renommé, là où `preferred_username` peut être réattribué.
        Un cache indexé sur un identifiant réattribuable finirait par servir à
        quelqu'un les données de son prédécesseur.
        """
        sujet = self.claims.get("sub") or self.username
        return f"{self.tenant_id or '-'}:{sujet}"


def _to_user(token: str, payload: dict) -> AuthenticatedUser:
    return AuthenticatedUser(
        token=token,
        username=payload.get("preferred_username", "?"),
        # Même claim que JwtClaimsExtractor.getTenantId() côté Java : les deux
        # doivent lire au même endroit, sinon l'assistant et l'API désignent
        # deux établissements différents pour le même utilisateur.
        tenant_id=payload.get("tenant_id"),
        roles=tuple(payload.get("realm_access", {}).get("roles", [])),
        claims=payload,
    )


def require_planning_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    settings: Settings = Depends(get_settings),
) -> AuthenticatedUser:
    """
    Dépendance des routes Planning : jeton valide et rôle d'administration.

    La vérification fine (quel profil, quelle année, quel établissement) reste
    au backend Spring, qui la fait déjà pour ses propres routes. On ne la
    redouble pas ici : deux implémentations d'une même règle de sécurité
    divergent tôt ou tard, et c'est la plus permissive qui l'emporte.
    """
    if not settings.auth_enabled:
        logger.warning("AUTH DÉSACTIVÉE — mode développement uniquement")
        return AuthenticatedUser(
            token="", username="dev", tenant_id="dev-tenant",
            roles=("SCHOOL_ADMIN",), claims={},
        )

    payload, token = _decode(credentials, settings)
    user = _to_user(token, payload)

    allowed = settings.planning_roles_list
    if not user.has_any_role(allowed):
        logger.warning("Accès Planning refusé pour %s : rôles=%s", user.username, user.roles)
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Un de ces rôles est requis : {', '.join(allowed)}",
        )
    return user


def require_cahier_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    settings: Settings = Depends(get_settings),
) -> AuthenticatedUser:
    """
    Dépendance des routes Cahier de séance : enseignant ou administration.

    Le rôle ne sert qu'à ouvrir la porte et à choisir le ton de la réponse. Il ne
    détermine PAS ce que l'utilisateur va lire : le périmètre du corpus est
    décidé par le backend à partir du compte porté par le jeton. Un enseignant
    dont le jeton porterait par erreur un rôle d'administration n'obtiendrait
    donc pas les séances de ses collègues — seulement un tutoiement différent.
    """
    if not settings.auth_enabled:
        logger.warning("AUTH DÉSACTIVÉE — mode développement uniquement")
        return AuthenticatedUser(
            token="", username="dev", tenant_id="dev-tenant",
            roles=("TEACHER",), claims={"sub": "dev"},
        )

    payload, token = _decode(credentials, settings)
    user = _to_user(token, payload)

    allowed = settings.cahier_roles_list
    if not user.has_any_role(allowed):
        logger.warning("Accès Cahier refusé pour %s : rôles=%s", user.username, user.roles)
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Un de ces rôles est requis : {', '.join(allowed)}",
        )
    return user
