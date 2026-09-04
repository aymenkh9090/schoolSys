package tn.wtm.school.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tn.wtm.school.security.constants.RoleConstants;
import tn.wtm.school.security.jwt.KeycloakJwtAuthenticationConverter;

import java.util.List;

/**
 * Configuration centrale Spring Security.
 * Mode STATELESS : chaque requête porte son JWT Bearer.
 * Les rôles fins sont gérés par @PreAuthorize sur les controllers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;

    /** Origines autorisées pour le frontend (séparées par des virgules). */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Endpoints publics
                .requestMatchers(
                    "/actuator/health", "/actuator/prometheus",
                    "/actuator/info",
                    "/api/public/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    // Handshake WebSocket : un navigateur ne peut pas y joindre
                    // d'en-tête Authorization. Le JWT est exigé dans la frame STOMP
                    // CONNECT (StompAuthChannelInterceptor), pas ici.
                    "/ws/**"
                ).permitAll()

                // Établissement de l'utilisateur connecté (branding dashboard) : tout utilisateur authentifié
                .requestMatchers(HttpMethod.GET, "/api/tenants/me")
                    .authenticated()

                // Gestion des établissements : Super Admin uniquement
                .requestMatchers("/api/tenants/**")
                    .hasRole(RoleConstants.PLATFORM_SUPER_ADMIN)

                // Super Admin dashboard
                .requestMatchers("/api/super-admin/**")
                    .hasRole(RoleConstants.PLATFORM_SUPER_ADMIN)

                // Endpoints admin (SCHOOL_ADMIN en écriture, SUPER_ADMIN en lecture)
                .requestMatchers(HttpMethod.POST, "/api/admin/**")
                    .hasAnyRole(RoleConstants.PLATFORM_SUPER_ADMIN, RoleConstants.SCHOOL_ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/admin/**")
                    .hasRole(RoleConstants.SCHOOL_ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/admin/**")
                    .hasRole(RoleConstants.SCHOOL_ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/admin/**")
                    .hasAnyRole(RoleConstants.PLATFORM_SUPER_ADMIN, RoleConstants.SCHOOL_ADMIN,
                                RoleConstants.TEACHER, RoleConstants.SURVEILLANT)

                // Endpoints enseignants
                .requestMatchers("/api/teacher/**")
                    .hasAnyRole(RoleConstants.SCHOOL_ADMIN, RoleConstants.TEACHER)

                // Surveillance
                .requestMatchers("/api/surveillance/**")
                    .hasAnyRole(RoleConstants.SCHOOL_ADMIN, RoleConstants.SURVEILLANT)

                // Parents
                .requestMatchers("/api/parent/**")
                    .hasRole(RoleConstants.PARENT)

                // Élèves
                .requestMatchers("/api/student/**")
                    .hasRole(RoleConstants.STUDENT)

                // Tout le reste : JWT requis, rôle géré par @PreAuthorize
                .anyRequest().authenticated()
            )

            // Validation JWT via JWKS Keycloak
            // URL: {issuer-uri}/protocol/openid-connect/certs (mis en cache)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
