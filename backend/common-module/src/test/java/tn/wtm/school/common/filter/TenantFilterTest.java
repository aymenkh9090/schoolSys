package tn.wtm.school.common.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock JwtClaimsExtractor jwtClaimsExtractor;
    @Mock FilterChain chaine;

    TenantFilter filtre;
    MockHttpServletResponse reponse;

    /** Ce que la suite de la chaîne voit dans TenantContext pendant la requête. */
    final AtomicReference<String> tenantVu = new AtomicReference<>();
    final AtomicReference<String> userVu   = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        filtre = new TenantFilter(jwtClaimsExtractor);
        reponse = new MockHttpServletResponse();
        TenantContext.clear();
        // lenient : les tests de shouldNotFilter et les refus 400 n'atteignent pas la chaîne.
        lenient().doAnswer(inv -> {
            tenantVu.set(TenantContext.getTenantId());
            userVu.set(TenantContext.getUserId());
            return null;
        }).when(chaine).doFilter(any(), any());
    }

    void mode(String mode) {
        ReflectionTestUtils.setField(filtre, "tenantResolutionMode", mode);
    }

    MockHttpServletRequest requete(String methode, String chemin) {
        return new MockHttpServletRequest(methode, chemin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/swagger-ui/index.html", "/v3/api-docs",
            "/api/public/x", "/api/tenants", "/ws/info"})
    void cheminsPublics_nonFiltres(String chemin) {
        assertThat(filtre.shouldNotFilter(requete("GET", chemin))).isTrue();
    }

    @Test
    void preflightCors_nonFiltre() {
        assertThat(filtre.shouldNotFilter(requete("OPTIONS", "/api/eleves"))).isTrue();
    }

    @Test
    void cheminMetier_filtre() {
        assertThat(filtre.shouldNotFilter(requete("GET", "/api/eleves"))).isFalse();
    }

    @Test
    void modeHeader_tenantNettoyePuisEffaceApresLaRequete() throws Exception {
        mode("header");
        MockHttpServletRequest req = requete("GET", "/api/eleves");
        req.addHeader("X-Tenant-Id", "  28 ");

        filtre.doFilterInternal(req, reponse, chaine);

        assertThat(tenantVu.get()).isEqualTo("28");
        assertThat(TenantContext.getTenantId()).isNull();
        verify(jwtClaimsExtractor, never()).getTenantId();
    }

    @Test
    void sansTenant_400_etLaChaineNEstPasAppelee() throws Exception {
        mode("header");

        filtre.doFilterInternal(requete("GET", "/api/eleves"), reponse, chaine);

        assertThat(reponse.getStatus()).isEqualTo(400);
        assertThat(reponse.getContentAsString()).contains("X-Tenant-Id");
        verify(chaine, never()).doFilter(any(), any());
    }

    @Test
    void tenantBlanc_400() throws Exception {
        mode("header");
        MockHttpServletRequest req = requete("GET", "/api/eleves");
        req.addHeader("X-Tenant-Id", "   ");

        filtre.doFilterInternal(req, reponse, chaine);

        assertThat(reponse.getStatus()).isEqualTo(400);
    }

    @Test
    void modeJwt_leClaimPrimeSurLEnTete() throws Exception {
        mode("jwt");
        when(jwtClaimsExtractor.getTenantId()).thenReturn("12");
        when(jwtClaimsExtractor.getUserId()).thenReturn("kc-1");
        when(jwtClaimsExtractor.getUsername()).thenReturn("ali");
        MockHttpServletRequest req = requete("GET", "/api/eleves");
        req.addHeader("X-Tenant-Id", "99");

        filtre.doFilterInternal(req, reponse, chaine);

        assertThat(tenantVu.get()).isEqualTo("12");
        assertThat(userVu.get()).isEqualTo("kc-1");
    }

    @Test
    void modeJwt_sansClaim_retombeSurLEnTete() throws Exception {
        mode("JWT");
        when(jwtClaimsExtractor.getTenantId()).thenReturn(null);
        MockHttpServletRequest req = requete("GET", "/api/eleves");
        req.addHeader("X-Tenant-Id", "99");

        filtre.doFilterInternal(req, reponse, chaine);

        assertThat(tenantVu.get()).isEqualTo("99");
    }
}
