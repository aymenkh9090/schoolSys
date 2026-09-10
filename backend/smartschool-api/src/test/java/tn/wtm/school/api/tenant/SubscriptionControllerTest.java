package tn.wtm.school.api.tenant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.tenant.dto.SubscriptionOverviewResponse;
import tn.wtm.school.tenant.dto.SubscriptionResponse;
import tn.wtm.school.tenant.enums.BillingCycle;
import tn.wtm.school.tenant.enums.SubscriptionStatus;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.service.SubscriptionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les abonnements des établissements — entièrement réservés au super-admin.
 *
 * <p>Contrairement à {@code TenantController}, il n'y a pas d'exception
 * {@code /me} : toute la surface porte le {@code @PreAuthorize} de classe et
 * tombe sous la règle d'URL {@code /api/super-admin/**}. Le premier bloc le
 * vérifie route par méthode HTTP ; le reste couvre le contrat HTTP, le service
 * étant mocké (sa logique — dates de renouvellement, un seul abonnement actif —
 * est testée dans {@code SubscriptionServiceImplTest}).
 */
@WebMvcTest(SubscriptionController.class)
@Import({SecuriteWebTestConfig.class, GlobalExceptionHandler.class})
class SubscriptionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SubscriptionService subscriptionService;

    // ── Sécurité ─────────────────────────────────────────────────────────────

    @Nested
    class Securite {

        @Test
        @WithAnonymousUser
        void sansJeton_401() throws Exception {
            mockMvc.perform(get("/api/super-admin/subscriptions"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(subscriptionService);
        }

        @Test
        @WithMockUser(roles = "SCHOOL_ADMIN")
        void adminEtablissement_lectureRefusee403() throws Exception {
            mockMvc.perform(get("/api/super-admin/subscriptions/tenant/2"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(subscriptionService);
        }

        @Test
        @WithMockUser(roles = "SCHOOL_ADMIN")
        void adminEtablissement_annulationRefusee403() throws Exception {
            mockMvc.perform(patch("/api/super-admin/subscriptions/tenant/2/cancel"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(subscriptionService);
        }

        @Test
        @WithMockUser(roles = "PLATFORM_SUPER_ADMIN")
        void superAdmin_vueDEnsembleAutorisee200() throws Exception {
            when(subscriptionService.listOverview(any()))
                    .thenReturn(new PageImpl<>(List.of(ligneOverview())));

            mockMvc.perform(get("/api/super-admin/subscriptions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].tenantName").value("Collège de Carthage"));
        }
    }

    // ── Contrat HTTP ─────────────────────────────────────────────────────────

    @Nested
    @WithMockUser(roles = "PLATFORM_SUPER_ADMIN")
    class ContratHttp {

        @Test
        void creation_retourne201() throws Exception {
            when(subscriptionService.createSubscription(any())).thenReturn(reponse());

            mockMvc.perform(post("/api/super-admin/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tenantId":2,"plan":"STANDARD","billingCycle":"YEARLY",
                                     "startDate":"2026-09-15","price":1200.0}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(50))
                    .andExpect(jsonPath("$.plan").value("STANDARD"));
        }

        @Test
        void creationSansTenant_retourne400() throws Exception {
            mockMvc.perform(post("/api/super-admin/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"plan\":\"STANDARD\",\"billingCycle\":\"YEARLY\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(subscriptionService);
        }

        @Test
        void creationSansCycleDeFacturation_retourne400() throws Exception {
            mockMvc.perform(post("/api/super-admin/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":2,\"plan\":\"STANDARD\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void creationAvecPlanInconnu_retourne400() throws Exception {
            mockMvc.perform(post("/api/super-admin/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tenantId\":2,\"plan\":\"GRATUIT_A_VIE\",\"billingCycle\":\"YEARLY\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void changementDePlanSansPlan_retourne400() throws Exception {
            mockMvc.perform(patch("/api/super-admin/subscriptions/tenant/2/plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
            verify(subscriptionService, never()).changePlan(any(), any());
        }

        @Test
        void renouvellementSansCycle_retourne400() throws Exception {
            mockMvc.perform(patch("/api/super-admin/subscriptions/tenant/2/renew")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"price\":900.0}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void changementDePlan_delegueAvecTenantEtPlan_retourne200() throws Exception {
            SubscriptionResponse premium = reponse();
            premium.setPlan(TenantPlan.PREMIUM);
            when(subscriptionService.changePlan(eq(2L), any())).thenReturn(premium);

            mockMvc.perform(patch("/api/super-admin/subscriptions/tenant/2/plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"plan\":\"PREMIUM\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plan").value("PREMIUM"));

            verify(subscriptionService).changePlan(eq(2L), any());
        }

        @Test
        void annulation_retourne200EtDelegue() throws Exception {
            SubscriptionResponse annule = reponse();
            annule.setStatus(SubscriptionStatus.CANCELLED);
            when(subscriptionService.cancelSubscription(2L)).thenReturn(annule);

            mockMvc.perform(patch("/api/super-admin/subscriptions/tenant/2/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            verify(subscriptionService).cancelSubscription(2L);
        }

        @Test
        void abonnementCourantAbsent_retourne404() throws Exception {
            when(subscriptionService.getCurrent(7L))
                    .thenThrow(new ResourceNotFoundException("Aucun abonnement pour l'établissement 7"));

            mockMvc.perform(get("/api/super-admin/subscriptions/tenant/7"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void historique_estPagine() throws Exception {
            when(subscriptionService.getHistory(eq(2L), any()))
                    .thenReturn(new PageImpl<>(List.of(reponse())));

            mockMvc.perform(get("/api/super-admin/subscriptions/tenant/2/history")
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static SubscriptionResponse reponse() {
        return SubscriptionResponse.builder()
                .id(50L)
                .tenantId(2L)
                .plan(TenantPlan.STANDARD)
                .billingCycle(BillingCycle.YEARLY)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 9, 15))
                .endDate(LocalDate.of(2027, 9, 15))
                .price(new BigDecimal("1200.0"))
                .build();
    }

    private static SubscriptionOverviewResponse ligneOverview() {
        return SubscriptionOverviewResponse.builder()
                .tenantId(2L)
                .tenantName("Collège de Carthage")
                .tenantCode("carthage")
                .subscriptionId(50L)
                .plan(TenantPlan.STANDARD)
                .billingCycle(BillingCycle.YEARLY)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 9, 15))
                .endDate(LocalDate.of(2027, 9, 15))
                .daysRemaining(200L)
                .build();
    }
}
