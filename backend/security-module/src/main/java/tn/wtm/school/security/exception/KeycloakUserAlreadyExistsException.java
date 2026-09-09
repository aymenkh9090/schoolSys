package tn.wtm.school.security.exception;

import lombok.Getter;

/**
 * Keycloak refuse la création d'un compte parce que l'identifiant ou l'adresse
 * est déjà pris (HTTP 409).
 *
 * Cas distingué du reste des échecs Keycloak parce qu'il n'a rien d'une panne :
 * c'est une saisie à corriger. L'appelant peut donc la traduire en conflit
 * métier (409 + message adressé à l'utilisateur) au lieu d'un 502 « erreur de
 * communication » qui laisse croire à une indisponibilité.
 */
@Getter
public class KeycloakUserAlreadyExistsException extends KeycloakIntegrationException {

    private final String email;

    public KeycloakUserAlreadyExistsException(String email) {
        super("[Keycloak] Utilisateur déjà existant : " + email);
        this.email = email;
    }
}
