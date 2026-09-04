package tn.wtm.school.security.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lit les claims JWT depuis SecurityContextHolder après validation par Spring Security.
 * Thread-safe : SecurityContextHolder utilise ThreadLocal.
 */
@Component
public class JwtClaimsExtractor {

    /** Claim tenant_id injecté via Protocol Mapper Keycloak. */
    public String getTenantId() {
        return getClaimAsString("tenant_id");
    }

    /** Claim sub = UUID Keycloak de l'utilisateur. */
    public String getUserId() {
        Jwt jwt = extractJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    public String getUsername() {
        return getClaimAsString("preferred_username");
    }

    public String getEmail() {
        return getClaimAsString("email");
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles() {
        Jwt jwt = extractJwt();
        if (jwt == null) return Collections.emptyList();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return Collections.emptyList();
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles != null ? roles : Collections.emptyList();
    }

    private String getClaimAsString(String claimName) {
        Jwt jwt = extractJwt();
        return jwt != null ? jwt.getClaimAsString(claimName) : null;
    }

    private Jwt extractJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return (Jwt) jwtAuth.getCredentials();
        }
        return null;
    }
}
