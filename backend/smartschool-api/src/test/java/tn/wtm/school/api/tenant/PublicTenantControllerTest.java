package tn.wtm.school.api.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.tenant.dto.PublicTenantResponse;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.service.TenantService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La page de login établissement résout une école par son code, <b>sans jeton</b>.
 *
 * <p>C'est le seul endpoint du domaine tenant ouvert au public
 * ({@code /api/public/**} dans {@code SecurityConfig}) : un test qui exige un
 * {@code @WithMockUser} ici masquerait une régression le jour où quelqu'un
 * déplacerait la route hors de {@code /api/public}.
 */
@WebMvcTest(PublicTenantController.class)
@Import({SecuriteWebTestConfig.class, GlobalExceptionHandler.class})
class PublicTenantControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TenantService tenantService;

    @Test
    void resoutUnEtablissementParSonCode_sansAuthentification() throws Exception {
        when(tenantService.findPublicTenantByCode("lycee-alfarabi")).thenReturn(
                PublicTenantResponse.builder()
                        .id(7L)
                        .code("lycee-alfarabi")
                        .name("Lycée Al-Farabi")
                        .type(EtablissementType.SECONDAIRE)
                        .logo("https://cdn/logo.png")
                        .build());

        mockMvc.perform(get("/api/public/tenants/by-code/lycee-alfarabi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Lycée Al-Farabi"))
                .andExpect(jsonPath("$.logo").value("https://cdn/logo.png"));
    }

    @Test
    void codeInconnu_donne404() throws Exception {
        when(tenantService.findPublicTenantByCode("fantome"))
                .thenThrow(new ResourceNotFoundException("Établissement introuvable : fantome"));

        mockMvc.perform(get("/api/public/tenants/by-code/fantome"))
                .andExpect(status().isNotFound());
    }

    @Test
    void passeLeCodeTelQuelAuService() throws Exception {
        when(tenantService.findPublicTenantByCode(eq("École-Été 2027")))
                .thenReturn(PublicTenantResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/public/tenants/by-code/{code}", "École-Été 2027"))
                .andExpect(status().isOk());

        verify(tenantService).findPublicTenantByCode("École-Été 2027");
    }
}
