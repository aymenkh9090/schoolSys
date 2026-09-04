package tn.wtm.school.planning.constraints.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.planning.constraints.dto.request.ConstraintProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.ConstraintSettingRequest;
import tn.wtm.school.planning.constraints.dto.response.ConstraintProfileResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSettingResponse;
import tn.wtm.school.planning.constraints.entity.ConstraintDefinition;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;
import tn.wtm.school.planning.constraints.entity.ConstraintSetting;
import tn.wtm.school.planning.constraints.enums.ConstraintCategory;
import tn.wtm.school.planning.constraints.enums.ConstraintType;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;
import tn.wtm.school.planning.constraints.mapper.ConstraintMapper;
import tn.wtm.school.planning.constraints.repository.ConstraintDefinitionRepository;
import tn.wtm.school.planning.constraints.repository.ConstraintProfileRepository;
import tn.wtm.school.planning.constraints.service.ConstraintParameters;
import tn.wtm.school.planning.constraints.repository.ConstraintSettingRepository;
import tn.wtm.school.planning.constraints.repository.CustomConstraintRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConstraintProfileServiceImplTest {

    @Mock private ConstraintProfileRepository profileRepository;
    @Mock private ConstraintSettingRepository settingRepository;
    @Mock private ConstraintDefinitionRepository definitionRepository;
    @Mock private CustomConstraintRepository customConstraintRepository;

    private ConstraintProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConstraintProfileServiceImpl(
                profileRepository,
                settingRepository,
                definitionRepository,
                customConstraintRepository,
                Mappers.getMapper(ConstraintMapper.class),
                new ConstraintParameters(new com.fasterxml.jackson.databind.ObjectMapper())
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void createSetsCurrentTenantOnProfile() {
        TenantContext.setTenantId("school-1");
        when(profileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId("school-1", "Standard", 2026L))
                .thenReturn(false);
        when(profileRepository.save(any())).thenAnswer(inv -> {
            ConstraintProfile p = inv.getArgument(0);
            p.setIdConstraintProfile(1L);
            return p;
        });

        ConstraintProfileResponse response = service.create(
                ConstraintProfileRequest.builder().name("Standard").academicYearId(2026L).build());

        ArgumentCaptor<ConstraintProfile> captor = ArgumentCaptor.forClass(ConstraintProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("school-1");
        assertThat(captor.getValue().getActive()).isTrue();
        assertThat(response.getName()).isEqualTo("Standard");
    }

    @Test
    void createDefaultsActiveToTrueWhenNotProvided() {
        TenantContext.setTenantId("school-1");
        when(profileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(any(), any(), any()))
                .thenReturn(false);
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(ConstraintProfileRequest.builder().name("Ramadan").build());

        ArgumentCaptor<ConstraintProfile> captor = ArgumentCaptor.forClass(ConstraintProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void createThrowsConflictWhenNameExistsForSameYear() {
        TenantContext.setTenantId("school-1");
        when(profileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId("school-1", "Standard", 2026L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                ConstraintProfileRequest.builder().name("Standard").academicYearId(2026L).build()))
                .isInstanceOf(ConflictException.class);

        verify(profileRepository, never()).save(any());
    }

    @Test
    void createLeavesProfileInactiveWhenYearAlreadyHasAnActiveProfile() {
        TenantContext.setTenantId("school-1");
        when(profileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(any(), any(), any()))
                .thenReturn(false);
        when(profileRepository.findByTenantIdAndAcademicYearIdAndActiveTrue("school-1", 2026L))
                .thenReturn(List.of(profile(1L, "school-1")));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConstraintProfileResponse response = service.create(
                ConstraintProfileRequest.builder().name("Ramadan").academicYearId(2026L).build());

        assertThat(response.getActive()).isFalse();
    }

    @Test
    void createDeactivatesPreviousActiveWhenExplicitlyRequestedActive() {
        TenantContext.setTenantId("school-1");
        ConstraintProfile previous = profile(1L, "school-1");
        when(profileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(any(), any(), any()))
                .thenReturn(false);
        when(profileRepository.findByTenantIdAndAcademicYearIdAndActiveTrue("school-1", 2026L))
                .thenReturn(List.of(previous));
        when(profileRepository.save(any())).thenAnswer(inv -> {
            ConstraintProfile p = inv.getArgument(0);
            if (p.getIdConstraintProfile() == null) p.setIdConstraintProfile(2L);
            return p;
        });
        when(profileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ConstraintProfileResponse response = service.create(ConstraintProfileRequest.builder()
                .name("Ramadan").academicYearId(2026L).active(true).build());

        assertThat(response.getActive()).isTrue();
        assertThat(previous.getActive()).isFalse();
    }

    // ── activate ──────────────────────────────────────────────────────────────

    @Test
    void activateMakesProfileTheOnlyActiveOneOfItsYear() {
        TenantContext.setTenantId("school-1");
        ConstraintProfile target = profile(2L, "school-1");
        target.setActive(false);
        ConstraintProfile previous = profile(1L, "school-1");

        when(profileRepository.findByIdConstraintProfileAndTenantId(2L, "school-1"))
                .thenReturn(Optional.of(target));
        when(profileRepository.findByTenantIdAndAcademicYearIdAndActiveTrue("school-1", 2026L))
                .thenReturn(List.of(previous));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.findByIdWithSettingsAndTenantId(2L, "school-1"))
                .thenReturn(Optional.of(target));

        ConstraintProfileResponse response = service.activate(2L);

        assertThat(response.getActive()).isTrue();
        assertThat(previous.getActive()).isFalse();
    }

    @Test
    void activateThrowsWhenProfileBelongsToAnotherTenant() {
        TenantContext.setTenantId("school-b");
        when(profileRepository.findByIdConstraintProfileAndTenantId(2L, "school-b"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(2L)).isInstanceOf(ResourceNotFoundException.class);
        verify(profileRepository, never()).save(any());
    }

    // ── deleteProfile ─────────────────────────────────────────────────────────

    @Test
    void deleteProfilePromotesRemainingProfileWhenActiveOneIsRemoved() {
        TenantContext.setTenantId("school-1");
        ConstraintProfile removed = profile(2L, "school-1");
        ConstraintProfile survivor = profile(1L, "school-1");
        survivor.setActive(false);

        when(profileRepository.findByIdConstraintProfileAndTenantId(2L, "school-1"))
                .thenReturn(Optional.of(removed));
        when(profileRepository.findFirstByTenantIdAndAcademicYearIdOrderByIdConstraintProfileDesc("school-1", 2026L))
                .thenReturn(Optional.of(survivor));

        service.deleteProfile(2L);

        verify(profileRepository).delete(removed);
        assertThat(survivor.getActive()).isTrue();
    }

    @Test
    void deleteProfileRemovesItsCustomConstraintsFirst() {
        TenantContext.setTenantId("school-1");
        ConstraintProfile removed = profile(2L, "school-1");
        removed.setActive(false);

        when(profileRepository.findByIdConstraintProfileAndTenantId(2L, "school-1"))
                .thenReturn(Optional.of(removed));

        service.deleteProfile(2L);

        // avant le profil : la cle etrangere refuserait des regles orphelines
        InOrder order = inOrder(customConstraintRepository, profileRepository);
        order.verify(customConstraintRepository).deleteByTenantIdAndProfile_IdConstraintProfile("school-1", 2L);
        order.verify(profileRepository).delete(removed);
    }

    @Test
    void deleteProfileDoesNotPromoteAnythingWhenRemovedProfileWasInactive() {
        TenantContext.setTenantId("school-1");
        ConstraintProfile removed = profile(2L, "school-1");
        removed.setActive(false);

        when(profileRepository.findByIdConstraintProfileAndTenantId(2L, "school-1"))
                .thenReturn(Optional.of(removed));

        service.deleteProfile(2L);

        verify(profileRepository).delete(removed);
        verify(profileRepository, never()).save(any());
    }

    // ── addSetting ────────────────────────────────────────────────────────────

    @Test
    void addSettingAppliesDefinitionDefaults() {
        TenantContext.setTenantId("school-1");
        ConstraintProfile profile = profile(10L, "school-1");
        ConstraintDefinition definition = definition(5L, ImportanceLevel.CRITICAL, true, "{\"maxHours\":{\"default\":6}}");

        when(profileRepository.findByIdConstraintProfileAndTenantId(10L, "school-1"))
                .thenReturn(Optional.of(profile));
        when(definitionRepository.findById(5L)).thenReturn(Optional.of(definition));
        when(settingRepository.existsByTenantIdAndProfile_IdConstraintProfileAndDefinition_IdConstraintDefinition(
                "school-1", 10L, 5L)).thenReturn(false);
        when(settingRepository.save(any())).thenAnswer(inv -> {
            ConstraintSetting s = inv.getArgument(0);
            s.setIdConstraintSetting(20L);
            return s;
        });

        ConstraintSettingResponse response = service.addSetting(10L,
                ConstraintSettingRequest.builder().constraintDefinitionId(5L).build());

        ArgumentCaptor<ConstraintSetting> captor = ArgumentCaptor.forClass(ConstraintSetting.class);
        verify(settingRepository).save(captor.capture());
        ConstraintSetting saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("school-1");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getImportance()).isEqualTo(ImportanceLevel.CRITICAL);
        assertThat(saved.getWeight()).isEqualTo(1000);
        assertThat(saved.getParametersJson()).contains("maxHours");
        assertThat(response.getIdConstraintSetting()).isEqualTo(20L);
    }

    @Test
    void addSettingThrowsConflictWhenAlreadyPresent() {
        TenantContext.setTenantId("school-1");
        when(profileRepository.findByIdConstraintProfileAndTenantId(10L, "school-1"))
                .thenReturn(Optional.of(profile(10L, "school-1")));
        when(definitionRepository.findById(5L))
                .thenReturn(Optional.of(definition(5L, ImportanceLevel.CRITICAL, true, "{}")));
        when(settingRepository.existsByTenantIdAndProfile_IdConstraintProfileAndDefinition_IdConstraintDefinition(
                "school-1", 10L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.addSetting(10L,
                ConstraintSettingRequest.builder().constraintDefinitionId(5L).build()))
                .isInstanceOf(ConflictException.class);
        verify(settingRepository, never()).save(any());
    }

    // ── updateSetting ─────────────────────────────────────────────────────────

    @Test
    void updateSettingAppliesPartialChanges() {
        TenantContext.setTenantId("school-1");
        ConstraintSetting setting = setting(30L, profile(10L, "school-1"),
                definition(5L, ImportanceLevel.LOW, true, "{}"));
        setting.setImportance(ImportanceLevel.LOW);
        setting.setWeight(1);

        when(settingRepository.findByIdConstraintSettingAndTenantId(30L, "school-1"))
                .thenReturn(Optional.of(setting));
        when(settingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConstraintSettingResponse response = service.updateSetting(30L,
                ConstraintSettingRequest.builder()
                        .enabled(false)
                        .importance(ImportanceLevel.HIGH)
                        .weight(75)
                        .parametersJson("{\"maxHours\":5}")
                        .build());

        assertThat(response.getEnabled()).isFalse();
        assertThat(response.getImportance()).isEqualTo(ImportanceLevel.HIGH);
        assertThat(response.getWeight()).isEqualTo(75);
        assertThat(response.getParametersJson()).contains("maxHours");
    }

    @Test
    void updateSettingRecalculatesWeightWhenImportanceChangesWithoutExplicitWeight() {
        TenantContext.setTenantId("school-1");
        ConstraintSetting setting = setting(31L, profile(10L, "school-1"),
                definition(5L, ImportanceLevel.LOW, true, "{}"));
        setting.setImportance(ImportanceLevel.LOW);
        setting.setWeight(1);

        when(settingRepository.findByIdConstraintSettingAndTenantId(31L, "school-1"))
                .thenReturn(Optional.of(setting));
        when(settingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConstraintSettingResponse response = service.updateSetting(31L,
                ConstraintSettingRequest.builder().importance(ImportanceLevel.CRITICAL).build());

        assertThat(response.getImportance()).isEqualTo(ImportanceLevel.CRITICAL);
        assertThat(response.getWeight()).isEqualTo(1000);
    }

    // ── tenant isolation ──────────────────────────────────────────────────────

    @Test
    void findByIdThrowsWhenProfileBelongsToAnotherTenant() {
        TenantContext.setTenantId("school-b");
        when(profileRepository.findByIdWithSettingsAndTenantId(1L, "school-b"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(profileRepository, never()).findByIdWithSettingsAndTenantId(1L, "school-a");
    }

    @Test
    void updateSettingThrowsWhenSettingBelongsToAnotherTenant() {
        TenantContext.setTenantId("school-b");
        when(settingRepository.findByIdConstraintSettingAndTenantId(30L, "school-b"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSetting(30L,
                ConstraintSettingRequest.builder().enabled(false).build()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(settingRepository, never()).save(any());
    }

    @Test
    void deleteSettingThrowsWhenSettingBelongsToAnotherTenant() {
        TenantContext.setTenantId("school-b");
        when(settingRepository.findByIdConstraintSettingAndTenantId(30L, "school-b"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSetting(30L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(settingRepository, never()).delete(any());
    }

    // ── findActiveSettings ────────────────────────────────────────────────────

    @Test
    void findActiveSettingsReturnsonlyEnabledSettingsForTenant() {
        TenantContext.setTenantId("school-1");
        ConstraintProfile profile = profile(10L, "school-1");
        ConstraintSetting active = setting(40L, profile,
                definition(5L, ImportanceLevel.CRITICAL, true, "{}"));
        active.setEnabled(true);

        when(profileRepository.findByIdConstraintProfileAndTenantId(10L, "school-1"))
                .thenReturn(Optional.of(profile));
        when(settingRepository.findActiveByProfileAndTenantId(10L, "school-1"))
                .thenReturn(List.of(active));

        List<ConstraintSettingResponse> result = service.findActiveSettings(10L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getIdConstraintSetting()).isEqualTo(40L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConstraintProfile profile(Long id, String tenantId) {
        ConstraintProfile p = ConstraintProfile.builder()
                .idConstraintProfile(id).name("Standard").academicYearId(2026L).active(true).build();
        p.setTenantId(tenantId);
        return p;
    }

    private ConstraintDefinition definition(Long id, ImportanceLevel importance,
                                             boolean defaultEnabled, String schema) {
        return ConstraintDefinition.builder()
                .idConstraintDefinition(id)
                .code("MAX_TEACHER_HOURS_PER_DAY")
                .name("Max teacher hours")
                .category(ConstraintCategory.TEACHER)
                .type(ConstraintType.HARD)
                .defaultImportance(importance)
                .defaultEnabled(defaultEnabled)
                .parameterSchema(schema)
                .build();
    }

    private ConstraintSetting setting(Long id, ConstraintProfile profile, ConstraintDefinition definition) {
        ConstraintSetting s = ConstraintSetting.builder()
                .idConstraintSetting(id)
                .profile(profile)
                .definition(definition)
                .enabled(true)
                .importance(definition.getDefaultImportance())
                .weight(1000)
                .parametersJson(definition.getParameterSchema())
                .build();
        s.setTenantId(profile.getTenantId());
        return s;
    }
}
