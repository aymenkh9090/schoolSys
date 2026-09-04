package tn.wtm.school.security.exception;

/**
 * Levée lors d'un échec d'appel à l'Admin REST API Keycloak.
 * RuntimeException → rollback @Transactional automatique.
 * Capturée par GlobalExceptionHandler → HTTP 502 Bad Gateway.
 */
public class KeycloakIntegrationException extends RuntimeException {

    public KeycloakIntegrationException(String message) {
        super(message);
    }

    public KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
