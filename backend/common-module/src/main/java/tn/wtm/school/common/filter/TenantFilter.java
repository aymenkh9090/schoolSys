package tn.wtm.school.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;

import java.io.IOException;

/**
 * Filtre HTTP qui alimente TenantContext à chaque requête.
 *
 * Phase 1 (header)   : app.tenant-resolution=header → lit X-Tenant-Id
 * Phase 2 (jwt)      : app.tenant-resolution=jwt    → lit le claim tenant_id du JWT validé,
 *                      avec fallback sur l'header si le claim est absent.
 *
 * TenantContextFilter s'exécute APRÈS BearerTokenAuthenticationFilter (Spring Security),
 * donc SecurityContextHolder est déjà alimenté quand on lit JwtClaimsExtractor.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private static final String HEADER_TENANT = "X-Tenant-Id";

    private final JwtClaimsExtractor jwtClaimsExtractor;

    @Value("${app.tenant-resolution:header}")
    private String tenantResolutionMode;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Preflight CORS : jamais de header X-Tenant-Id ni de JWT
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/public")
                || path.startsWith("/api/tenants")
                // Handshake WebSocket : pas d'en-tête X-Tenant-Id possible. Le tenant
                // est résolu à la frame STOMP CONNECT, à partir du JWT.
                || path.startsWith("/ws");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenantId;
            String userId   = null;
            String username = null;

            if ("jwt".equalsIgnoreCase(tenantResolutionMode)) {
                // Phase 2 : extraire depuis le JWT validé dans SecurityContextHolder
                tenantId = jwtClaimsExtractor.getTenantId();
                userId   = jwtClaimsExtractor.getUserId();
                username = jwtClaimsExtractor.getUsername();

                if (!StringUtils.hasText(tenantId)) {
                    // Fallback sur l'header si le claim tenant_id est absent du JWT
                    tenantId = request.getHeader(HEADER_TENANT);
                    log.debug("[TenantFilter] Fallback header X-Tenant-Id: {}", tenantId);
                } else {
                    log.debug("[TenantFilter] JWT tenantId={} userId={}", tenantId, userId);
                }
            } else {
                // Phase 1 : header uniquement
                tenantId = request.getHeader(HEADER_TENANT);
                log.debug("[TenantFilter] Header X-Tenant-Id: {}", tenantId);
            }

            if (tenantId == null || tenantId.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Missing or empty X-Tenant-Id header\"}");
                return;
            }

            TenantContext.setTenantId(tenantId.trim());
            TenantContext.setUserId(userId);
            TenantContext.setUsername(username);

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
