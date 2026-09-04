package tn.wtm.school.planning.constraints.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.wtm.school.planning.constraints.dto.response.ConstraintDefinitionResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintProfileResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSettingResponse;
import tn.wtm.school.planning.constraints.enums.ConstraintCategory;
import tn.wtm.school.planning.constraints.enums.ConstraintType;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;
import tn.wtm.school.planning.constraints.service.ConstraintDefinitionService;
import tn.wtm.school.planning.constraints.service.ConstraintProfileService;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConstraintControllerTest {

    @Mock private ConstraintDefinitionService definitionService;
    @Mock private ConstraintProfileService profileService;

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConstraintController(definitionService, profileService))
                .build();
    }

    @Test
    void getDefinitionsReturns200WithList() throws Exception {
        when(definitionService.findAll()).thenReturn(List.of(definitionResponse()));

        mockMvc.perform(get("/api/planning/constraints/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("MAX_STUDENT_HOURS_PER_DAY"))
                .andExpect(jsonPath("$[0].category").value("STUDENT"));
    }

    @Test
    void postProfilesCreatesAndReturns201() throws Exception {
        when(profileService.create(any())).thenReturn(profileResponse());

        mockMvc.perform(post("/api/planning/constraints/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Standard","academicYearId":2026,"active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idConstraintProfile").value(1))
                .andExpect(jsonPath("$.name").value("Standard"));
    }

    @Test
    void postProfilesMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/planning/constraints/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYearId\":2026}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProfilesReturns200WithTenantList() throws Exception {
        when(profileService.findAll()).thenReturn(List.of(profileResponse()));

        mockMvc.perform(get("/api/planning/constraints/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Standard"));
    }

    @Test
    void getProfileByIdReturnsProfileWithSettings() throws Exception {
        when(profileService.findById(1L)).thenReturn(profileResponse());

        mockMvc.perform(get("/api/planning/constraints/profiles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idConstraintProfile").value(1))
                .andExpect(jsonPath("$.settings", hasSize(1)));
    }

    @Test
    void postProfileSettingsAddSettingReturns201() throws Exception {
        when(profileService.addSetting(eq(1L), any())).thenReturn(settingResponse());

        mockMvc.perform(post("/api/planning/constraints/profiles/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"constraintDefinitionId":5,"enabled":true,
                                 "importance":"CRITICAL","weight":1000,
                                 "parametersJson":"{\\"maxHours\\":6}"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idConstraintSetting").value(10))
                .andExpect(jsonPath("$.constraintCode").value("MAX_STUDENT_HOURS_PER_DAY"))
                .andExpect(jsonPath("$.importance").value("CRITICAL"));
    }

    @Test
    void putSettingsUpdatesAndReturns200() throws Exception {
        ConstraintSettingResponse updated = settingResponse();
        updated.setEnabled(false);
        updated.setImportance(ImportanceLevel.HIGH);
        updated.setWeight(100);
        when(profileService.updateSetting(eq(10L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/planning/constraints/settings/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"importance\":\"HIGH\",\"weight\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.importance").value("HIGH"))
                .andExpect(jsonPath("$.weight").value(100));
    }

    @Test
    void deleteSettingsReturns204() throws Exception {
        mockMvc.perform(delete("/api/planning/constraints/settings/10"))
                .andExpect(status().isNoContent());

        verify(profileService).deleteSetting(10L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConstraintDefinitionResponse definitionResponse() {
        return ConstraintDefinitionResponse.builder()
                .idConstraintDefinition(5L)
                .code("MAX_STUDENT_HOURS_PER_DAY")
                .name("Maximum student hours per day")
                .category(ConstraintCategory.STUDENT)
                .type(ConstraintType.HARD)
                .defaultImportance(ImportanceLevel.CRITICAL)
                .defaultEnabled(true)
                .parameterSchema("{\"maxHours\":{\"type\":\"number\",\"default\":6}}")
                .build();
    }

    private ConstraintProfileResponse profileResponse() {
        return ConstraintProfileResponse.builder()
                .idConstraintProfile(1L)
                .name("Standard")
                .academicYearId(2026L)
                .active(true)
                .settings(List.of(settingResponse()))
                .build();
    }

    private ConstraintSettingResponse settingResponse() {
        return ConstraintSettingResponse.builder()
                .idConstraintSetting(10L)
                .profileId(1L)
                .definitionId(5L)
                .constraintCode("MAX_STUDENT_HOURS_PER_DAY")
                .constraintName("Maximum student hours per day")
                .category(ConstraintCategory.STUDENT)
                .type(ConstraintType.HARD)
                .enabled(true)
                .importance(ImportanceLevel.CRITICAL)
                .weight(1000)
                .parametersJson("{\"maxHours\":6}")
                .build();
    }
}
