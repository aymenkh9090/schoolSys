package tn.wtm.school.api.tenant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.mock.mockito.MockBean;
import tn.wtm.school.security.config.SecurityConfig;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;
import tn.wtm.school.security.jwt.KeycloakJwtAuthenticationConverter;

/**
 * La sécurité réelle, branchée dans une tranche web.
 *
 * <p>On importe le vrai {@link SecurityConfig} de {@code security-module} : les
 * tests voient donc les règles d'URL exactes de la production
 * ({@code /api/public/**} ouvert, {@code /api/tenants/me} authentifié, le reste
 * réservé au super-admin) <b>et</b> {@code @EnableMethodSecurity}, qui active les
 * {@code @PreAuthorize} posés sur les controllers. Rejouer ces règles dans une
 * config de test les laisserait diverger de celles qu'on veut protéger.
 *
 * <p>Deux collaborateurs sont mockés parce qu'ils n'ont aucun rôle dans un test
 * de controller et qu'ils tireraient sinon le démarrage vers Keycloak :
 * <ul>
 *   <li>{@link JwtDecoder} — sans lui, l'auto-configuration OAuth2 va chercher
 *       les clés JWKS à l'{@code issuer-uri} au démarrage ;</li>
 *   <li>{@link KeycloakJwtAuthenticationConverter} — dépendance de
 *       {@code SecurityConfig}, inutile ici puisque {@code @WithMockUser}
 *       peuple directement le {@code SecurityContext}.</li>
 * </ul>
 *
 * <p>{@link JwtClaimsExtractor} est mocké parce que le {@code TenantFilter} de
 * {@code common-module}, embarqué dans la chaîne de filtres, en dépend. Les
 * tests qui touchent une route filtrée par lui (hors {@code /api/public} et
 * {@code /api/tenants}, qu'il ignore) doivent stubber {@code getTenantId()}.
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class SecuriteWebTestConfig {

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @MockBean
    JwtClaimsExtractor jwtClaimsExtractor;
}
