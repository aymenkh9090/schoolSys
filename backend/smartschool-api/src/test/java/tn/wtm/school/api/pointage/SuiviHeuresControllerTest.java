package tn.wtm.school.api.pointage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tn.wtm.school.api.support.SecuriteWebTestConfig;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.service.ServiceSuiviHeures;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le suivi des heures d'un enseignant — contrat HTTP.
 *
 * <p>Sous {@code SCHOOL_ADMIN} ; la matrice des rôles (surveillant exclu) est
 * dans {@link PointageSecuriteTest}.
 */
@WebMvcTest(SuiviHeuresController.class)
@Import({SecuriteWebTestConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "SCHOOL_ADMIN")
class SuiviHeuresControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ServiceSuiviHeures serviceSuiviHeures;

    @Test
    void mettreAJourValide_retourne201() throws Exception {
        when(serviceSuiviHeures.mettreAJourHeures(any())).thenReturn(reponse());

        mockMvc.perform(post("/api/v1/pointage/suivi-heures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enseignantId":5,"numeroSemaine":37,"anneeAcademique":"2026-2027",
                                 "heuresPrevues":18.0,"heuresRealisees":16.0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.heuresManquees").value(2.0));
    }

    @Test
    void mettreAJourSansHeuresRealisees_retourne400() throws Exception {
        mockMvc.perform(post("/api/v1/pointage/suivi-heures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enseignantId":5,"numeroSemaine":37,"anneeAcademique":"2026-2027",
                                 "heuresPrevues":18.0}
                                """))
                .andExpect(status().isBadRequest());
        verify(serviceSuiviHeures, never()).mettreAJourHeures(any());
    }

    @Test
    void mettreAJourSansAnneeAcademique_retourne400() throws Exception {
        mockMvc.perform(post("/api/v1/pointage/suivi-heures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enseignantId":5,"numeroSemaine":37,
                                 "heuresPrevues":18.0,"heuresRealisees":16.0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSemaine_passeLesTroisArgumentsAuService() throws Exception {
        when(serviceSuiviHeures.obtenirResumeSemaine(5L, 37, "2026-2027")).thenReturn(reponse());

        mockMvc.perform(get("/api/v1/pointage/suivi-heures/enseignant/5/semaine/37")
                        .param("anneeAcademique", "2026-2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroSemaine").value(37));

        verify(serviceSuiviHeures).obtenirResumeSemaine(5L, 37, "2026-2027");
    }

    @Test
    void getSemaineNumeroNonNumerique_retourne400() throws Exception {
        mockMvc.perform(get("/api/v1/pointage/suivi-heures/enseignant/5/semaine/trente-sept")
                        .param("anneeAcademique", "2026-2027"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSemaineSansAnneeAcademique_retourne400() throws Exception {
        mockMvc.perform(get("/api/v1/pointage/suivi-heures/enseignant/5/semaine/37"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnnuel_retourne200() throws Exception {
        when(serviceSuiviHeures.obtenirResumeAnnuel(5L, "2026-2027"))
                .thenReturn(List.of(reponse(), reponse()));

        mockMvc.perform(get("/api/v1/pointage/suivi-heures/enseignant/5/annuel")
                        .param("anneeAcademique", "2026-2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void getAnnuelEnseignantInconnu_retourne404() throws Exception {
        when(serviceSuiviHeures.obtenirResumeAnnuel(404L, "2026-2027"))
                .thenThrow(new ResourceNotFoundException("Enseignant 404 introuvable"));

        mockMvc.perform(get("/api/v1/pointage/suivi-heures/enseignant/404/annuel")
                        .param("anneeAcademique", "2026-2027"))
                .andExpect(status().isNotFound());
    }

    private static SuiviHeuresEnseignantReponse reponse() {
        return SuiviHeuresEnseignantReponse.builder()
                .id(1L)
                .enseignantId(5L)
                .numeroSemaine(37)
                .anneeAcademique("2026-2027")
                .heuresPrevues(18.0)
                .heuresRealisees(16.0)
                .heuresManquees(2.0)
                .build();
    }
}
