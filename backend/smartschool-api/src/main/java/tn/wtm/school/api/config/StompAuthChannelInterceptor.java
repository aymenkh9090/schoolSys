package tn.wtm.school.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tn.wtm.school.security.jwt.KeycloakJwtAuthenticationConverter;

import java.util.Map;

/**
 * Authentifie et cloisonne les connexions STOMP.
 *
 * Le handshake WebSocket ne peut pas porter d'en-tête {@code Authorization} depuis
 * un navigateur : {@code /ws} est donc ouvert au niveau HTTP (voir SecurityConfig)
 * et c'est ici que le JWT est exigé, dans la frame {@code CONNECT}.
 *
 * Cloisonnement multi-établissement : le filtre Hibernate qui protège les
 * requêtes REST n'existe pas sur le canal du broker. Un topic est donc préfixé par
 * l'identifiant d'établissement, et tout {@code SUBSCRIBE} vers un préfixe qui
 * n'est pas celui du jeton est rejeté — sans quoi n'importe quel utilisateur
 * authentifié pourrait écouter les événements d'une autre école.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String TENANT_SESSION_ATTRIBUTE = "tenantId";
    private static final String TOPIC_ROOT = "/topic/";
    private static final String USER_ROOT = "/user/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    /** Valide le JWT de la frame CONNECT et attache l'utilisateur + son établissement à la session. */
    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Jeton d'authentification absent");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
        } catch (JwtException ex) {
            log.debug("[ws] CONNECT refusé — jeton invalide: {}", ex.getMessage());
            throw new MessageDeliveryException("Jeton d'authentification invalide");
        }

        String tenantId = jwt.getClaimAsString("tenant_id");
        if (!StringUtils.hasText(tenantId)) {
            throw new MessageDeliveryException("Jeton sans établissement (claim tenant_id absent)");
        }

        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
        accessor.setUser(authentication);

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new MessageDeliveryException("Session WebSocket sans attributs");
        }
        sessionAttributes.put(TENANT_SESSION_ATTRIBUTE, tenantId.trim());

        log.debug("[ws] CONNECT accepté — tenant={} user={}", tenantId, jwt.getSubject());
    }

    /**
     * Deux familles de destinations sont autorisées, et rien d'autre :
     *
     * <ul>
     *   <li>{@code /topic/{tenantId}/…} — diffusion à tout l'établissement du jeton ;</li>
     *   <li>{@code /user/…} — file personnelle. Spring la réécrit en une file propre
     *       à la session courante, à partir du principal posé au CONNECT : un client
     *       ne peut pas viser celle d'un autre, quelle que soit la chaîne envoyée.</li>
     * </ul>
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object tenantId = sessionAttributes != null ? sessionAttributes.get(TENANT_SESSION_ATTRIBUTE) : null;
        if (tenantId == null) {
            throw new MessageDeliveryException("Session non authentifiée");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessageDeliveryException("Abonnement sans destination");
        }
        if (destination.startsWith(USER_ROOT)) {
            return;
        }
        if (!destination.startsWith(TOPIC_ROOT + tenantId + "/")) {
            log.warn("[ws] SUBSCRIBE refusé — tenant={} destination={}", tenantId, destination);
            throw new MessageDeliveryException("Abonnement non autorisé pour cet établissement");
        }
    }
}
