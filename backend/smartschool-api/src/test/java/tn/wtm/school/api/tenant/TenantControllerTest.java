package tn.wtm.school.api.tenant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;
import tn.wtm.school.tenant.dto.PublicTenantResponse;
import tn.wtm.school.tenant.dto.TenantResponse;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.service.TenantService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La gestion des établissements — réservée au super-admin de la plateforme.
 *
 * <p>Deux barrières se recouvrent, et les deux sont vérifiées ici :
 * <ul>
 *   <li>la règle d'URL de {@code SecurityConfig} : {@code /api/tenants/**} exige
 *       le rôle {@code PLATFORM_SUPER_ADMIN}, sauf {@code GET /api/tenants/me}
 *       qui n'exige qu'un compte authentifié ;</li>
 *   <li>le {@code @PreAuthorize} posé sur le controller.</li>
 * </ul>
 *
 * <p>La logique métier (unicité du code, intégration Keycloak…) vit dans
 * {@code TenantServiceImpl} et y est testée : ici le service est mocké, on ne
 * regarde que le contrat HTTP — routage, codes de retour, validation du corps,
 * et le seul aiguillage que le controller fait lui-même, celui de {@code /me}.
 */
@WebMvcTest(TenantController.class)
@Import({SecuriteWebTestConfig.class, GlobalExceptionHandler.class})
class TenantControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtClaimsExtractor jwtClaimsExtractor;

    @MockBean
    TenantService tenantService;

    // ── Sécurité ─────────────────────────────────────────────────────────────

    @Nested
    class Securite {

        @Test
        @WithAnonymousUser
        void sansJeton_listeRefusee401() throws Exception {
            mockMvc.perform(get("/api/tenants"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(tenantService);
        }

        @Test
        @WithMockUser(roles = "SCHOOL_ADMIN")
        void adminEtablissement_creationRefusee403() throws Exception {
            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(creationValide()))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(tenantService);
        }

        @Test
        @WithMockUser(roles = "SCHOOL_ADMIN")
        void adminEtablissement_suppressionRefusee403() throws Exception {
            mockMvc.perform(delete("/api/tenants/9"))
                    .andExpect(status().isForbidden());
            verify(tenantService, never()).deleteTenant(anyLong());
        }

        @Test
        @WithMockUser(roles = "PLATFORM_SUPER_ADMIN")
        void superAdmin_listeAutorisee200() throws Exception {
            when(tenantService.findAllTenants(any()))
                    .thenReturn(new PageImpl<>(List.of(reponse())));

            mockMvc.perform(get("/api/tenants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].code").value("carthage"));
        }
    }

    // ── /me : l'établissement de l'utilisateur connecté ──────────────────────

    @Nested
    class EtablissementCourant {

        @Test
        @WithMockUser(roles = "STUDENT")
        void unSimpleCompteAuthentifieYAccede_ceNEstPasReserveAuSuperAdmin() throws Exception {
            when(jwtClaimsExtractor.getTenantId()).thenReturn("42");
            when(tenantService.findPublicTenantById(42L))
                    .thenReturn(PublicTenantResponse.builder().id(42L).name("Collège de Carthage").build());

            mockMvc.perform(get("/api/tenants/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Collège de Carthage"));

            verify(tenantService).findPublicTenantById(42L);
        }

        @Test
        @WithMockUser(roles = "PLATFORM_SUPER_ADMIN")
        void claimTenantIdNonNumerique_donne404SansAppelerLeService() throws Exception {
            when(jwtClaimsExtractor.getTenantId()).thenReturn("system");

            mockMvc.perform(get("/api/tenants/me"))
                    .andExpect(status().isNotFound());

            verify(tenantService, never()).findPublicTenantById(anyLong());
        }

        @Test
        @WithMockUser(roles = "TEACHER")
        void claimTenantIdAbsent_donne404() throws Exception {
            when(jwtClaimsExtractor.getTenantId()).thenReturn(null);

            mockMvc.perform(get("/api/tenants/me"))
                    .andExpect(status().isNotFound());

            verify(tenantService, never()).findPublicTenantById(anyLong());
        }
    }

    // ── Contrat HTTP des routes super-admin ──────────────────────────────────

    @Nested
    @WithMockUser(roles = "PLATFORM_SUPER_ADMIN")
    class ContratHttp {

        @Test
        void creationValide_retourne201EtLesIdentifiantsAdmin() throws Exception {
            TenantResponse cree = reponse();
            cree.setAdminEmail("admin@carthage.tn");
            cree.setAdminTempPassword("Xy7!provisoire");
            when(tenantService.createTenant(any())).thenReturn(cree);

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(creationValide()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.adminTempPassword").value("Xy7!provisoire"));
        }

        @Test
        void creationSansCode_retourne400() throws Exception {
            String sansCode = """
                    {"name":"Collège de Carthage","type":"COLLEGE","address":"Carthage",
                     "phone":"+21600000000","emailAdmin":"admin@carthage.tn",
                     "nomCompletAdmin":"Ahmed Ben Salah"}
                    """;

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sansCode))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(tenantService);
        }

        @Test
        void creationEmailAdminInvalide_retourne400() throws Exception {
            String mauvaisEmail = creationValide().replace("admin@carthage.tn", "pas-un-email");

            mockMvc.perform(post("/api/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mauvaisEmail))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void etablissementInconnu_retourne404() throws Exception {
            when(tenantService.findTenantById(404L))
                    .thenThrow(new ResourceNotFoundException("Établissement 404 introuvable"));

            mockMvc.perform(get("/api/tenants/404"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void suppression_retourne204EtDelegue() throws Exception {
            mockMvc.perform(delete("/api/tenants/9"))
                    .andExpect(status().isNoContent());

            verify(tenantService).deleteTenant(9L);
        }

        @Test
        void bascule_passeLaValeurActiveAuService_retourne204() throws Exception {
            mockMvc.perform(patch("/api/tenants/9/toggle").param("active", "false"))
                    .andExpect(status().isNoContent());

            verify(tenantService).toggleActive(9L, false);
        }

        @Test
        void basculeSansParametreActive_retourne400() throws Exception {
            mockMvc.perform(patch("/api/tenants/9/toggle"))
                    .andExpect(status().isBadRequest());
            verify(tenantService, never()).toggleActive(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        void rechercheParNom_retourne200() throws Exception {
            when(tenantService.findTenantByName("Carthage")).thenReturn(reponse());

            mockMvc.perform(get("/api/tenants/search").param("name", "Carthage"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("carthage"));
        }

        @Test
        void rechercheSansNom_retourne400() throws Exception {
            mockMvc.perform(get("/api/tenants/search"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void activation_retourne200() throws Exception {
            TenantResponse actif = reponse();
            actif.setStatus(TenantStatus.ACTIVE);
            actif.setActive(true);
            when(tenantService.activateTenant(9L)).thenReturn(actif);

            mockMvc.perform(patch("/api/tenants/9/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void statistiques_retournentLesTroisCompteurs() throws Exception {
            when(tenantService.countAll()).thenReturn(12L);
            when(tenantService.countActive()).thenReturn(9L);
            when(tenantService.countInactive()).thenReturn(3L);

            mockMvc.perform(get("/api/tenants/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(12))
                    .andExpect(jsonPath("$.active").value(9))
                    .andExpect(jsonPath("$.inactive").value(3));
        }

        @Test
        void liste_estPaginee() throws Exception {
            Page<TenantResponse> page = new PageImpl<>(List.of(reponse()));
            when(tenantService.findAllTenants(any())).thenReturn(page);

            mockMvc.perform(get("/api/tenants").param("page", "0").param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static String creationValide() {
        return """
                {"code":"carthage","name":"Collège de Carthage","type":"COLLEGE",
                 "address":"Carthage, Tunis","phone":"+21671000000",
                 "emailAdmin":"admin@carthage.tn","nomCompletAdmin":"Ahmed Ben Salah"}
                """;
    }

    private static TenantResponse reponse() {
        return TenantResponse.builder()
                .id(3L)
                .code("carthage")
                .name("Collège de Carthage")
                .type(EtablissementType.COLLEGE)
                .active(true)
                .status(TenantStatus.ACTIVE)
                .plan(TenantPlan.FREE)
                .build();
    }
}
