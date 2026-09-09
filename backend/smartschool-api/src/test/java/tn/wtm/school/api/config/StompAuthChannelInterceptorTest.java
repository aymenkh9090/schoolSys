package tn.wtm.school.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import tn.wtm.school.security.jwt.KeycloakJwtAuthenticationConverter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * L'authentification et le cloisonnement des connexions STOMP.
 *
 * <p>C'est ici, et nulle part ailleurs, que le multi-établissement est protégé
 * sur le canal WebSocket : le filtre Hibernate qui couvre les requêtes REST
 * n'existe pas sur le canal du broker. Un abonnement mal filtré laisserait un
 * utilisateur authentifié écouter les événements d'une autre école — un défaut
 * qu'aucun test d'API REST ne peut révéler.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StompAuthChannelInterceptorTest {

    private static final String JETON = "un-jeton";
    /** Le canal n'est jamais sollicité : l'intercepteur décide avant lui. */
    private static final MessageChannel CANAL = (Message<?> message, long timeout) -> true;

    @Mock private JwtDecoder jwtDecoder;
    @Mock private KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    private StompAuthChannelInterceptor interceptor() {
        return new StompAuthChannelInterceptor(jwtDecoder, jwtAuthenticationConverter);
    }

    // ── CONNECT ──────────────────────────────────────────────────────────────────

    @Test
    void attacheLutilisateurEtSonEtablissementALaSessionQuandLeJetonEstValide() {
        jetonValide("28");
        StompHeaderAccessor connect = connect("Bearer " + JETON);

        interceptor().preSend(message(connect), CANAL);

        assertThat(connect.getUser()).isNotNull();
        assertThat(connect.getSessionAttributes()).containsEntry("tenantId", "28");
    }

    /** L'identifiant est normalisé : un claim entouré d'espaces ne doit pas fabriquer un préfixe de topic qui ne correspondra à rien. */
    @Test
    void elagueLesEspacesAutourDeLidentifiantDetablissement() {
        jetonValide("  28  ");
        StompHeaderAccessor connect = connect("Bearer " + JETON);

        interceptor().preSend(message(connect), CANAL);

        assertThat(connect.getSessionAttributes()).containsEntry("tenantId", "28");
    }

    @Test
    void refuseUnConnectSansEnTeteDautorisation() {
        assertThatThrownBy(() -> interceptor().preSend(message(connect(null)), CANAL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("absent");
    }

    /** Un jeton sans le schéma « Bearer » n'est pas décodé : il est refusé d'emblée. */
    @Test
    void refuseUnConnectDontLeJetonNestPasPorteParLeSchemaBearer() {
        assertThatThrownBy(() -> interceptor().preSend(message(connect("Basic " + JETON)), CANAL))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void refuseUnConnectDontLeJetonEstInvalide() {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("signature invalide"));

        assertThatThrownBy(() -> interceptor().preSend(message(connect("Bearer " + JETON)), CANAL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("invalide");
    }

    /**
     * Un jeton authentique mais sans {@code tenant_id} est refusé : sans
     * établissement, l'autorisation d'abonnement n'aurait rien à comparer et
     * laisserait passer n'importe quel topic.
     */
    @Test
    void refuseUnJetonSansEtablissement() {
        jetonValide(null);

        assertThatThrownBy(() -> interceptor().preSend(message(connect("Bearer " + JETON)), CANAL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void refuseUnConnectSurUneSessionSansAttributs() {
        jetonValide("28");
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer " + JETON);

        assertThatThrownBy(() -> interceptor().preSend(message(connect), CANAL))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ── SUBSCRIBE ────────────────────────────────────────────────────────────────

    @Test
    void autoriseUnAbonnementAuTopicDeSonPropreEtablissement() {
        StompHeaderAccessor abonnement = subscribe("28", "/topic/28/emploi-du-temps");

        assertThatCode(() -> interceptor().preSend(message(abonnement), CANAL)).doesNotThrowAnyException();
    }

    /** Le cœur du cloisonnement : un jeton de l'établissement 28 n'écoute pas le 31. */
    @Test
    void refuseUnAbonnementAuTopicDunAutreEtablissement() {
        StompHeaderAccessor abonnement = subscribe("28", "/topic/31/emploi-du-temps");

        assertThatThrownBy(() -> interceptor().preSend(message(abonnement), CANAL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("non autorisé");
    }

    /**
     * Régression : la comparaison porte sur le préfixe suivi d'une barre. Sans
     * elle, {@code /topic/281/…} passerait le test de préfixe de
     * l'établissement 28.
     */
    @Test
    void refuseUnEtablissementDontLidentifiantCommenceParLeSien() {
        StompHeaderAccessor abonnement = subscribe("28", "/topic/281/emploi-du-temps");

        assertThatThrownBy(() -> interceptor().preSend(message(abonnement), CANAL))
                .isInstanceOf(MessageDeliveryException.class);
    }

    /**
     * La file personnelle est laissée passer : Spring la réécrit à partir du
     * principal posé au CONNECT, un client ne peut donc pas viser celle d'un
     * autre quelle que soit la chaîne envoyée.
     */
    @Test
    void laissePasserLaFilePersonnelle() {
        StompHeaderAccessor abonnement = subscribe("28", "/user/queue/notifications");

        assertThatCode(() -> interceptor().preSend(message(abonnement), CANAL)).doesNotThrowAnyException();
    }

    @Test
    void refuseUnAbonnementSurUneSessionNonAuthentifiee() {
        StompHeaderAccessor abonnement = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        abonnement.setSessionAttributes(new HashMap<>());
        abonnement.setDestination("/topic/28/emploi-du-temps");

        assertThatThrownBy(() -> interceptor().preSend(message(abonnement), CANAL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("non authentifiée");
    }

    @Test
    void refuseUnAbonnementSansDestination() {
        StompHeaderAccessor abonnement = subscribe("28", null);

        assertThatThrownBy(() -> interceptor().preSend(message(abonnement), CANAL))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("destination");
    }

    // ── Les autres frames ────────────────────────────────────────────────────────

    /** Un SEND passe sans contrôle : seuls CONNECT et SUBSCRIBE sont interceptés. */
    @Test
    void laissePasserLesFramesQuiNeSontNiConnectNiSubscribe() {
        StompHeaderAccessor envoi = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = message(envoi);

        assertThat(interceptor().preSend(message, CANAL)).isSameAs(message);
    }

    /** Un message qui ne porte aucune commande STOMP traverse l'intercepteur intact. */
    @Test
    void laissePasserUnMessageSansCommandeStomp() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();

        assertThat(interceptor().preSend(message, CANAL)).isSameAs(message);
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private void jetonValide(String tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "mehdi.ben.ali");
        if (tenantId != null) {
            claims.put("tenant_id", tenantId);
        }
        Jwt jwt = new Jwt(JETON, Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"), claims);
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        AbstractAuthenticationToken authentification =
                new UsernamePasswordAuthenticationToken("mehdi.ben.ali", null, List.of());
        when(jwtAuthenticationConverter.convert(any(Jwt.class))).thenReturn(authentification);
    }

    private StompHeaderAccessor connect(String enTeteAutorisation) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        if (enTeteAutorisation != null) {
            accessor.setNativeHeader("Authorization", enTeteAutorisation);
        }
        return accessor;
    }

    private StompHeaderAccessor subscribe(String tenantId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Map<String, Object> attributs = new HashMap<>();
        attributs.put("tenantId", tenantId);
        accessor.setSessionAttributes(attributs);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return accessor;
    }

    /**
     * L'accessoire est laissé mutable, comme le fait le canal de Spring : sans
     * {@code setLeaveMutable}, {@code getMessageHeaders()} fige les en-têtes et
     * l'intercepteur échoue sur « Already immutable » dès qu'il pose
     * l'utilisateur. C'est aussi ce qui permet de relire ici ce qu'il a écrit.
     */
    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
