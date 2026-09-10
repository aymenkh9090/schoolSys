package tn.wtm.school.api.pointage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tn.wtm.school.api.support.SecuriteWebTestConfig;
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.pointage.dto.reponse.StatistiquesPresenceReponse;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.service.ServiceStatistiquesPointage;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les statistiques de présence du personnel — contrat HTTP.
 *
 * <p>Sous {@code SCHOOL_ADMIN} ; la matrice des rôles (surveillant exclu) est
 * dans {@link PointageSecuriteTest}.
 */
@WebMvcTest(StatistiquesPointageController.class)
@Import({SecuriteWebTestConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "SCHOOL_ADMIN")
class StatistiquesPointageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ServiceStatistiquesPointage serviceStatistiques;

    @Test
    void statistiquesPersonnel_passeLIdEtLaFenetre() throws Exception {
        when(serviceStatistiques.obtenirStatistiquesPersonnel(
                5L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(StatistiquesPresenceReponse.builder()
                        .membrePersonnelId(5L).joursPresent(18).tauxPresence(0.95).build());

        mockMvc.perform(get("/api/v1/statistiques/pointage/personnel/5")
                        .param("debut", "2026-09-01").param("fin", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joursPresent").value(18));

        verify(serviceStatistiques).obtenirStatistiquesPersonnel(
                5L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void statistiquesPersonnelSansBornes_retourne400() throws Exception {
        mockMvc.perform(get("/api/v1/statistiques/pointage/personnel/5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statistiquesParType_convertitLEnumDuChemin() throws Exception {
        when(serviceStatistiques.obtenirStatistiquesParTypePersonnel(
                TypePersonnel.ENSEIGNANT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(StatistiquesPresenceReponse.builder().build()));

        mockMvc.perform(get("/api/v1/statistiques/pointage/type/ENSEIGNANT")
                        .param("debut", "2026-09-01").param("fin", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(serviceStatistiques).obtenirStatistiquesParTypePersonnel(
                TypePersonnel.ENSEIGNANT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void statistiquesParTypeInconnu_retourne400() throws Exception {
        mockMvc.perform(get("/api/v1/statistiques/pointage/type/FEMME_DE_MENAGE")
                        .param("debut", "2026-09-01").param("fin", "2026-09-30"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statistiquesParTypeDateMalFormee_retourne400() throws Exception {
        mockMvc.perform(get("/api/v1/statistiques/pointage/type/ENSEIGNANT")
                        .param("debut", "septembre").param("fin", "2026-09-30"))
                .andExpect(status().isBadRequest());
    }
}
