package tn.wtm.school.api.pointage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import tn.wtm.school.api.support.SecuriteWebTestConfig;
import tn.wtm.school.org.service.SchoolUserService;
import tn.wtm.school.planning.solver.service.PersonnelDisponibleService;
import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.reponse.StatistiquesPresenceReponse;
import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.service.ServiceJustificatifPointage;
import tn.wtm.school.pointage.service.ServicePointage;
import tn.wtm.school.pointage.service.ServiceStatistiquesPointage;
import tn.wtm.school.pointage.service.ServiceSuiviHeures;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qui a le droit de toucher au pointage — la matrice des rôles des quatre
 * controllers du domaine, en un seul endroit.
 *
 * <p>Sous {@code /api/v1/**} la règle d'URL de {@code SecurityConfig} n'exige
 * qu'un compte authentifié : ce sont les {@code @PreAuthorize} de classe qui
 * portent le rôle. Le partage voulu :
 * <ul>
 *   <li><b>pointage</b> et <b>justificatifs</b> — {@code SCHOOL_ADMIN},
 *       {@code TEACHER}, {@code SURVEILLANT} : le surveillant fait l'appel du
 *       personnel sur le terrain ;</li>
 *   <li><b>suivi des heures</b> et <b>statistiques</b> — {@code SCHOOL_ADMIN},
 *       {@code TEACHER} seulement : le décompte du service d'un enseignant ne
 *       regarde pas le surveillant.</li>
 * </ul>
 *
 * <p>Ce test n'importe <b>pas</b> le {@code GlobalExceptionHandler} : on veut
 * que le refus remonte jusqu'à l'{@code ExceptionTranslationFilter} de Spring,
 * qui rend le vrai 401 / 403 — le {@code @ControllerAdvice} le transformerait
 * autrement en 500 (son {@code @ExceptionHandler(Exception.class)} attrape aussi
 * l'{@code AccessDeniedException}).
 */
@WebMvcTest(controllers = {
        PointageController.class, SuiviHeuresController.class,
        JustificatifPointageController.class, StatistiquesPointageController.class})
@Import(SecuriteWebTestConfig.class)
class PointageSecuriteTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean ServicePointage servicePointage;
    @MockBean ServiceSuiviHeures serviceSuiviHeures;
    @MockBean ServiceJustificatifPointage serviceJustificatif;
    @MockBean ServiceStatistiquesPointage serviceStatistiques;
    @MockBean PersonnelDisponibleService personnelDisponibleService;
    @MockBean SchoolUserService schoolUserService;

    private static final String POINTAGE      = "/api/v1/pointage/rapport?date=2026-09-10";
    private static final String JUSTIFICATIFS = "/api/v1/pointage/justificatifs/en-attente";
    private static final String SUIVI_HEURES  = "/api/v1/pointage/suivi-heures/enseignant/5/annuel?anneeAcademique=2026-2027";
    private static final String STATS         = "/api/v1/statistiques/pointage/personnel/5?debut=2026-09-01&fin=2026-09-30";

    @org.junit.jupiter.api.BeforeEach
    void stubs() {
        lenient().when(servicePointage.obtenirRapportJournalier(any())).thenReturn(null);
        lenient().when(serviceJustificatif.listerJustificatifsEnAttente()).thenReturn(List.of());
        lenient().when(serviceSuiviHeures.obtenirResumeAnnuel(any(), any()))
                .thenReturn(List.of(SuiviHeuresEnseignantReponse.builder().build()));
        lenient().when(serviceStatistiques.obtenirStatistiquesPersonnel(any(), any(), any()))
                .thenReturn(StatistiquesPresenceReponse.builder().build());
        lenient().when(servicePointage.pointer(any()))
                .thenReturn(PresencePersonnelReponse.builder().id(1L).build());
    }

    // ── Sans authentification : tout est fermé ───────────────────────────────

    @Test
    @WithAnonymousUser
    void sansJeton_toutesLesRoutesRepondent401() throws Exception {
        for (String url : List.of(POINTAGE, JUSTIFICATIFS, SUIVI_HEURES, STATS)) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    // ── Rôles sans aucun accès au domaine ────────────────────────────────────

    @Test
    @WithMockUser(roles = "PARENT")
    void unParent_estRefusePartout403() throws Exception {
        for (String url : List.of(POINTAGE, JUSTIFICATIFS, SUIVI_HEURES, STATS)) {
            mockMvc.perform(get(url)).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void unEleve_estRefusePartout403() throws Exception {
        for (String url : List.of(POINTAGE, JUSTIFICATIFS, SUIVI_HEURES, STATS)) {
            mockMvc.perform(get(url)).andExpect(status().isForbidden());
        }
    }

    // ── Le surveillant : le terrain oui, le décompte des heures non ──────────

    @Test
    @WithMockUser(roles = "SURVEILLANT")
    void unSurveillant_pointeEtVoitLesJustificatifs() throws Exception {
        mockMvc.perform(get(POINTAGE)).andExpect(status().isOk());
        mockMvc.perform(get(JUSTIFICATIFS)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SURVEILLANT")
    void unSurveillant_neVoitNiLesHeuresNiLesStatistiques403() throws Exception {
        mockMvc.perform(get(SUIVI_HEURES)).andExpect(status().isForbidden());
        mockMvc.perform(get(STATS)).andExpect(status().isForbidden());
    }

    // ── L'enseignant et l'admin : tout le domaine ───────────────────────────

    @Test
    @WithMockUser(roles = "TEACHER")
    void unEnseignant_accedeAToutLeDomaine() throws Exception {
        for (String url : List.of(POINTAGE, JUSTIFICATIFS, SUIVI_HEURES, STATS)) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(roles = "SCHOOL_ADMIN")
    void lAdminEcoleAccedeAToutLeDomaine() throws Exception {
        for (RequestBuilder requete : List.of(
                get(POINTAGE), get(JUSTIFICATIFS), get(SUIVI_HEURES), get(STATS),
                post("/api/v1/pointage").contentType(MediaType.APPLICATION_JSON).content("""
                        {"membrePersonnelId":5,"typePersonnel":"ENSEIGNANT","datePointage":"2026-09-10",
                         "periode":"MATIN","statut":"PRESENT"}
                        """))) {
            mockMvc.perform(requete).andExpect(status().is2xxSuccessful());
        }
    }
}
