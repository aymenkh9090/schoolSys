package tn.wtm.school.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal temps réel STOMP sur WebSocket natif (sans SockJS : les navigateurs
 * ciblés supportent WebSocket, et le client se contente de {@code @stomp/stompjs}).
 *
 * Broker simple en mémoire, suffisant tant que l'API tourne en instance unique.
 * Un passage à plusieurs instances imposerait un relais externe (RabbitMQ/Redis) :
 * un message publié par l'instance A n'atteint pas un client connecté à B.
 *
 * Diffusion seule pour l'instant — aucun préfixe applicatif n'est déclaré, les
 * clients s'abonnent mais n'envoient rien au serveur.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /** Mêmes origines que CORS côté REST. */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    /**
     * Deux préfixes :
     * <ul>
     *   <li>{@code /topic} — diffusion à un établissement (progression du solveur) ;</li>
     *   <li>{@code /queue} — files personnelles alimentées par
     *       {@code convertAndSendToUser} (notifications). Spring y réécrit les
     *       destinations {@code /user/…} en ajoutant l'identifiant de session,
     *       d'où la nécessité que le broker gère aussi ce préfixe.</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
