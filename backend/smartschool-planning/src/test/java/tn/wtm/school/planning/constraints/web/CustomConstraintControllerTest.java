package tn.wtm.school.planning.constraints.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSuggestionResponse;
import tn.wtm.school.planning.constraints.dto.response.CustomConstraintResponse;
import tn.wtm.school.planning.constraints.dto.response.DslAnalysisResponse;
import tn.wtm.school.planning.constraints.dto.response.DslSchemaResponse;
import tn.wtm.school.planning.constraints.service.ConstraintSuggestionService;
import tn.wtm.school.planning.constraints.service.CustomConstraintService;
import tn.wtm.school.planning.constraints.service.DslSchemaService;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le contrat HTTP des contraintes personnalisées : catalogue DSL, analyse d'une
 * règle candidate, CRUD des règles, suggestions.
 *
 * <p>Montage {@code standaloneSetup}, comme {@link ConstraintControllerTest} du
 * même paquet : {@code smartschool-planning} est une bibliothèque, sans contexte
 * Spring bootable ni {@code @EnableMethodSecurity} — les {@code @PreAuthorize}
 * sont appliqués là où ces controllers tournent réellement, dans
 * {@code smartschool-api}. Leur présence et leur valeur sont vérifiées par
 * {@link PlanningControllerSecuriteTest} ; ici on regarde le routage, la
 * validation du corps, les codes de retour et la délégation au service.
 *
 * <p>Le {@code GlobalExceptionHandler} de {@code common-module} est branché pour
 * que {@code ResourceNotFoundException} devienne un 404, comme en production.
 */
@ExtendWith(MockitoExtension.class)
class CustomConstraintControllerTest {

    @Mock private CustomConstraintService customConstraintService;
    @Mock private DslSchemaService schemaService;
    @Mock private ConstraintSuggestionService suggestionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CustomConstraintController(
                        customConstraintService, schemaService, suggestionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── catalogue et analyse ─────────────────────────────────────────────────

    @Test
    void getDslSchemaReturns200() throws Exception {
        when(schemaService.describe()).thenReturn(DslSchemaResponse.builder().build());

        mockMvc.perform(get("/api/planning/constraints/dsl/schema"))
                .andExpect(status().isOk());
    }

    @Test
    void analyzeValidRequestReturns200() throws Exception {
        when(customConstraintService.analyze(any())).thenReturn(
                DslAnalysisResponse.builder().valid(true).matchedLessons(12).totalLessons(400).build());

        mockMvc.perform(post("/api/planning/constraints/custom/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dsl\":{\"action\":\"PENALIZE\",\"conditions\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.matchedLessons").value(12));
    }

    @Test
    void analyzeWithoutDslReturns400() throws Exception {
        mockMvc.perform(post("/api/planning/constraints/custom/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolYearId\":2026}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(customConstraintService);
    }

    // ── lecture ──────────────────────────────────────────────────────────────

    @Test
    void listWithoutProfileIdPassesNull() throws Exception {
        when(customConstraintService.findAll(null)).thenReturn(List.of(response(), response()));

        mockMvc.perform(get("/api/planning/constraints/custom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        verify(customConstraintService).findAll(isNull());
    }

    @Test
    void listForwardsProfileIdWhenPresent() throws Exception {
        when(customConstraintService.findAll(5L)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/planning/constraints/custom").param("profileId", "5"))
                .andExpect(status().isOk());

        verify(customConstraintService).findAll(5L);
    }

    @Test
    void getByIdReturns200() throws Exception {
        when(customConstraintService.findById(7L)).thenReturn(response());

        mockMvc.perform(get("/api/planning/constraints/custom/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCustomConstraint").value(7))
                .andExpect(jsonPath("$.code").value("PAS_DE_MATH_LE_VENDREDI"));
    }

    @Test
    void getByIdUnknownReturns404() throws Exception {
        when(customConstraintService.findById(404L))
                .thenThrow(new ResourceNotFoundException("Règle 404 introuvable"));

        mockMvc.perform(get("/api/planning/constraints/custom/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void suggestionsReturns200() throws Exception {
        when(suggestionService.suggest(null, null))
                .thenReturn(ConstraintSuggestionResponse.builder().build());

        mockMvc.perform(get("/api/planning/constraints/custom/suggestions"))
                .andExpect(status().isOk());
    }

    // ── écriture ─────────────────────────────────────────────────────────────

    @Test
    void createValidReturns201() throws Exception {
        when(customConstraintService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/planning/constraints/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"constraintProfileId":3,"code":"PAS_DE_MATH_LE_VENDREDI",
                                 "name":"Pas de maths le vendredi après-midi",
                                 "dsl":{"action":"PENALIZE","conditions":[]}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCustomConstraint").value(7));
    }

    @Test
    void createWithoutProfileIdReturns400() throws Exception {
        mockMvc.perform(post("/api/planning/constraints/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"X\",\"dsl\":{}}"))
                .andExpect(status().isBadRequest());
        verify(customConstraintService, never()).create(any());
    }

    @Test
    void createWithoutCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/planning/constraints/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"constraintProfileId\":3,\"name\":\"X\",\"dsl\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutDslReturns400() throws Exception {
        mockMvc.perform(post("/api/planning/constraints/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"constraintProfileId\":3,\"code\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setEnabledForwardsFlagAndReturns200() throws Exception {
        CustomConstraintResponse disabled = response();
        disabled.setEnabled(false);
        when(customConstraintService.setEnabled(7L, false)).thenReturn(disabled);

        mockMvc.perform(patch("/api/planning/constraints/custom/7/enabled").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(customConstraintService).setEnabled(7L, false);
    }

    @Test
    void setEnabledWithoutFlagReturns400() throws Exception {
        mockMvc.perform(patch("/api/planning/constraints/custom/7/enabled"))
                .andExpect(status().isBadRequest());
        verify(customConstraintService, never()).setEnabled(eq(7L), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void updateReturns200() throws Exception {
        when(customConstraintService.update(eq(7L), any())).thenReturn(response());

        mockMvc.perform(put("/api/planning/constraints/custom/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nouveau libellé\"}"))
                .andExpect(status().isOk());

        verify(customConstraintService).update(eq(7L), any());
    }

    @Test
    void deleteReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/planning/constraints/custom/7"))
                .andExpect(status().isNoContent());

        verify(customConstraintService).delete(7L);
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static CustomConstraintResponse response() {
        return CustomConstraintResponse.builder()
                .idCustomConstraint(7L)
                .constraintProfileId(3L)
                .code("PAS_DE_MATH_LE_VENDREDI")
                .name("Pas de maths le vendredi après-midi")
                .enabled(true)
                .weight(50)
                .build();
    }
}
