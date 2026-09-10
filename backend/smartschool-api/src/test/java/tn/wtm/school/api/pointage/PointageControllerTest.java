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
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.solver.service.PersonnelDisponibleService;
import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.reponse.RapportJournalierReponse;
import tn.wtm.school.pointage.dto.reponse.ResultatPointageMasseReponse;
import tn.wtm.school.pointage.service.ServicePointage;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le pointage du personnel — contrat HTTP.
 *
 * <p>Tranche {@code @WebMvcTest} avec le vrai {@code SecurityConfig}. La matrice
 * des rôles est dans {@link PointageSecuriteTest} ; ici, sous un
 * {@code SCHOOL_ADMIN}, on regarde le contrat HTTP : codes de retour, validation
 * {@code @Valid}, paramètres de requête, traduction {@code Periode → DayPeriod},
 * délégation au service. La logique métier est testée dans
 * {@code ServicePointageImplTest}.
 */
@WebMvcTest(PointageController.class)
@Import({SecuriteWebTestConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "SCHOOL_ADMIN")
class PointageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ServicePointage servicePointage;

    @MockBean
    PersonnelDisponibleService personnelDisponibleService;

    @Test
    void pointerValide_retourne201() throws Exception {
        when(servicePointage.pointer(any())).thenReturn(reponse());

        mockMvc.perform(post("/api/v1/pointage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pointageValide()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void pointerSansStatut_retourne400() throws Exception {
        String sansStatut = pointageValide().replaceAll(",\\s*\"statut\"\\s*:\\s*\"PRESENT\"", "");

        mockMvc.perform(post("/api/v1/pointage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sansStatut))
                .andExpect(status().isBadRequest());
        verify(servicePointage, never()).pointer(any());
    }

    @Test
    void pointageEnMasseVide_retourne400() throws Exception {
        mockMvc.perform(post("/api/v1/pointage/masse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pointages\":[]}"))
                .andExpect(status().isBadRequest());
        verify(servicePointage, never()).pointerEnMasse(any());
    }

    @Test
    void pointageEnMasse_retourne201EtLeBilan() throws Exception {
        when(servicePointage.pointerEnMasse(any())).thenReturn(
                ResultatPointageMasseReponse.builder().total(3).reussis(2).echoues(1).build());

        mockMvc.perform(post("/api/v1/pointage/masse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pointages\":[" + pointageValide() + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.echoues").value(1));
    }

    @Test
    void modifier_retourne200EtDelegueAvecLId() throws Exception {
        when(servicePointage.modifierPointage(eq(9L), any())).thenReturn(reponse());

        mockMvc.perform(patch("/api/v1/pointage/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pointageValide()))
                .andExpect(status().isOk());

        verify(servicePointage).modifierPointage(eq(9L), any());
    }

    @Test
    void rapportJournalier_retourne200() throws Exception {
        when(servicePointage.obtenirRapportJournalier(LocalDate.of(2026, 9, 10)))
                .thenReturn(RapportJournalierReponse.builder()
                        .date(LocalDate.of(2026, 9, 10)).totalPresents(40).totalAbsents(2).build());

        mockMvc.perform(get("/api/v1/pointage/rapport").param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPresents").value(40));
    }

    @Test
    void rapportSansDate_retourne400() throws Exception {
        mockMvc.perform(get("/api/v1/pointage/rapport"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rapportDateMalFormee_retourne400() throws Exception {
        mockMvc.perform(get("/api/v1/pointage/rapport").param("date", "10-09-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historiquePersonnel_passeLIdEtLesBornes() throws Exception {
        when(servicePointage.obtenirHistoriquePersonnel(5L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(reponse()));

        mockMvc.perform(get("/api/v1/pointage/personnel/5/historique")
                        .param("debut", "2026-09-01").param("fin", "2026-09-30"))
                .andExpect(status().isOk());

        verify(servicePointage).obtenirHistoriquePersonnel(5L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void enseignantsDisponiblesSansPeriode_passeNull() throws Exception {
        when(personnelDisponibleService.findTeachersWorkingOn(eq(LocalDate.of(2026, 9, 10)), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/pointage/enseignants-disponibles").param("date", "2026-09-10"))
                .andExpect(status().isOk());

        verify(personnelDisponibleService).findTeachersWorkingOn(LocalDate.of(2026, 9, 10), null);
    }

    @Test
    void enseignantsDisponiblesMatin_traduitEnMORNING() throws Exception {
        when(personnelDisponibleService.findTeachersWorkingOn(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/pointage/enseignants-disponibles")
                        .param("date", "2026-09-10").param("periode", "MATIN"))
                .andExpect(status().isOk());

        verify(personnelDisponibleService)
                .findTeachersWorkingOn(LocalDate.of(2026, 9, 10), DayPeriod.MORNING);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static String pointageValide() {
        return """
                {"membrePersonnelId":5,"typePersonnel":"ENSEIGNANT","datePointage":"2026-09-10",
                 "periode":"MATIN","statut":"PRESENT"}
                """;
    }

    private static PresencePersonnelReponse reponse() {
        return PresencePersonnelReponse.builder().id(1L).membrePersonnelId(5L).build();
    }
}
