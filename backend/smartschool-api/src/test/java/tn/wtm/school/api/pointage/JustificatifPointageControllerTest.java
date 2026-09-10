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
import org.mockito.ArgumentCaptor;
import tn.wtm.school.org.dto.response.SchoolUserResponse;
import tn.wtm.school.org.service.SchoolUserService;
import tn.wtm.school.pointage.dto.requete.TraitementJustificatifPointageRequete;
import tn.wtm.school.pointage.dto.reponse.JustificatifPointageReponse;
import tn.wtm.school.pointage.service.ServiceJustificatifPointage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les justificatifs de pointage — contrat HTTP.
 *
 * <p>Sous {@code SCHOOL_ADMIN} ; la matrice des rôles est dans
 * {@link PointageSecuriteTest}. {@code SchoolUserService} est mocké : le
 * controller y lit l'auteur de la décision, jamais un nom du client.
 */
@WebMvcTest(JustificatifPointageController.class)
@Import({SecuriteWebTestConfig.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "SCHOOL_ADMIN")
class JustificatifPointageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ServiceJustificatifPointage serviceJustificatif;

    @MockBean
    SchoolUserService schoolUserService;

    @Test
    void soumettreValide_retourne201() throws Exception {
        when(serviceJustificatif.soumettreJustificatif(any())).thenReturn(reponse());

        mockMvc.perform(post("/api/v1/pointage/justificatifs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"presencePersonnelId":12,"typeDocument":"CERTIFICAT_MEDICAL",
                                 "description":"Grippe, 2 jours"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void soumettreSansDescription_retourne400() throws Exception {
        mockMvc.perform(post("/api/v1/pointage/justificatifs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presencePersonnelId\":12,\"typeDocument\":\"CERTIFICAT_MEDICAL\"}"))
                .andExpect(status().isBadRequest());
        verify(serviceJustificatif, never()).soumettreJustificatif(any());
    }

    @Test
    void soumettreTypeDocumentInconnu_retourne400() throws Exception {
        mockMvc.perform(post("/api/v1/pointage/justificatifs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"presencePersonnelId":12,"typeDocument":"MOT_DES_PARENTS",
                                 "description":"x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listerEnAttente_retourne200() throws Exception {
        when(serviceJustificatif.listerJustificatifsEnAttente())
                .thenReturn(List.of(reponse(), reponse(), reponse()));

        mockMvc.perform(get("/api/v1/pointage/justificatifs/en-attente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void getByPresence_retourne200() throws Exception {
        when(serviceJustificatif.obtenirJustificatifParPresence(12L)).thenReturn(reponse());

        mockMvc.perform(get("/api/v1/pointage/justificatifs/presence/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getByPresenceSansJustificatif_retourne404() throws Exception {
        when(serviceJustificatif.obtenirJustificatifParPresence(99L))
                .thenThrow(new ResourceNotFoundException("Aucun justificatif pour la présence 99"));

        mockMvc.perform(get("/api/v1/pointage/justificatifs/presence/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void traiterValide_retourne200EtImposeLAuteurConnecte() throws Exception {
        // L'auteur de la décision vient du compte connecté, jamais du corps :
        // le "traitePar" envoyé par le client est écrasé.
        when(schoolUserService.getCurrentUserId()).thenReturn(77L);
        when(schoolUserService.getUserById(77L))
                .thenReturn(SchoolUserResponse.builder().id(77L).nomComplet("Salwa Directrice").build());
        when(serviceJustificatif.traiterJustificatif(eq(3L), any())).thenReturn(reponse());

        mockMvc.perform(patch("/api/v1/pointage/justificatifs/3/traiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approuve\":true,\"traitePar\":\"Quelqu'un d'autre\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TraitementJustificatifPointageRequete> capt =
                ArgumentCaptor.forClass(TraitementJustificatifPointageRequete.class);
        verify(serviceJustificatif).traiterJustificatif(eq(3L), capt.capture());
        assertThat(capt.getValue().getTraitePar()).isEqualTo("Salwa Directrice");
    }

    @Test
    void traiterSansDecision_retourne400() throws Exception {
        mockMvc.perform(patch("/api/v1/pointage/justificatifs/3/traiter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motifRejet\":\"incomplet\"}"))
                .andExpect(status().isBadRequest());
        verify(serviceJustificatif, never()).traiterJustificatif(any(), any());
    }

    private static JustificatifPointageReponse reponse() {
        return JustificatifPointageReponse.builder().id(1L).membrePersonnelId(5L).build();
    }
}
