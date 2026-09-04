package tn.wtm.school.planning.constraints.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.planning.constraints.dto.response.ConstraintDefinitionResponse;
import tn.wtm.school.planning.constraints.entity.ConstraintDefinition;
import tn.wtm.school.planning.constraints.enums.ConstraintCategory;
import tn.wtm.school.planning.constraints.enums.ConstraintType;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;
import tn.wtm.school.planning.constraints.mapper.ConstraintMapper;
import tn.wtm.school.planning.constraints.repository.ConstraintDefinitionRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConstraintDefinitionServiceImplTest {

    @Mock
    private ConstraintDefinitionRepository repository;

    private ConstraintDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConstraintDefinitionServiceImpl(
                repository,
                Mappers.getMapper(ConstraintMapper.class)
        );
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAllReturnsSortedList() {
        ConstraintDefinition d1 = definition(1L, "MAX_STUDENT_HOURS_PER_DAY", ConstraintCategory.STUDENT, ConstraintType.HARD, ImportanceLevel.CRITICAL);
        ConstraintDefinition d2 = definition(2L, "MAX_TEACHER_HOURS_PER_DAY", ConstraintCategory.TEACHER, ConstraintType.HARD, ImportanceLevel.CRITICAL);
        when(repository.findAllByOrderByCategoryAscCodeAsc()).thenReturn(List.of(d1, d2));

        List<ConstraintDefinitionResponse> result = service.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("MAX_STUDENT_HOURS_PER_DAY");
        assertThat(result.get(1).getCode()).isEqualTo("MAX_TEACHER_HOURS_PER_DAY");
        verify(repository).findAllByOrderByCategoryAscCodeAsc();
    }

    @Test
    void findAllReturnsEmptyWhenNoneExist() {
        when(repository.findAllByOrderByCategoryAscCodeAsc()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void findAllMapsAllFieldsCorrectly() {
        ConstraintDefinition def = definition(5L, "SPECIAL_ROOM_REQUIRED", ConstraintCategory.ROOM, ConstraintType.HARD, ImportanceLevel.CRITICAL);
        def.setDescription("Description test");
        def.setParameterSchema("{\"enabled\":{\"default\":true}}");
        when(repository.findAllByOrderByCategoryAscCodeAsc()).thenReturn(List.of(def));

        ConstraintDefinitionResponse response = service.findAll().getFirst();

        assertThat(response.getIdConstraintDefinition()).isEqualTo(5L);
        assertThat(response.getCode()).isEqualTo("SPECIAL_ROOM_REQUIRED");
        assertThat(response.getCategory()).isEqualTo(ConstraintCategory.ROOM);
        assertThat(response.getType()).isEqualTo(ConstraintType.HARD);
        assertThat(response.getDefaultImportance()).isEqualTo(ImportanceLevel.CRITICAL);
        assertThat(response.getDefaultEnabled()).isTrue();
        assertThat(response.getParameterSchema()).contains("enabled");
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Test
    void findByCodeReturnsMatchingDefinition() {
        ConstraintDefinition def = definition(3L, "NO_STUDENT_IDLE_GAPS", ConstraintCategory.STUDENT, ConstraintType.HARD, ImportanceLevel.CRITICAL);
        when(repository.findByCode("NO_STUDENT_IDLE_GAPS")).thenReturn(Optional.of(def));

        ConstraintDefinitionResponse result = service.findByCode("NO_STUDENT_IDLE_GAPS");

        assertThat(result.getCode()).isEqualTo("NO_STUDENT_IDLE_GAPS");
        assertThat(result.getCategory()).isEqualTo(ConstraintCategory.STUDENT);
    }

    @Test
    void findByCodeTrimsWhitespace() {
        ConstraintDefinition def = definition(4L, "MAX_TEACHER_HOURS_PER_DAY", ConstraintCategory.TEACHER, ConstraintType.HARD, ImportanceLevel.CRITICAL);
        when(repository.findByCode("MAX_TEACHER_HOURS_PER_DAY")).thenReturn(Optional.of(def));

        ConstraintDefinitionResponse result = service.findByCode("  MAX_TEACHER_HOURS_PER_DAY  ");

        assertThat(result.getCode()).isEqualTo("MAX_TEACHER_HOURS_PER_DAY");
        verify(repository).findByCode("MAX_TEACHER_HOURS_PER_DAY");
    }

    @Test
    void findByCodeThrowsNotFoundWhenCodeUnknown() {
        when(repository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void findByCodeThrowsBadRequestWhenNull() {
        assertThatThrownBy(() -> service.findByCode(null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void findByCodeThrowsBadRequestWhenBlank() {
        assertThatThrownBy(() -> service.findByCode("   "))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findByIdReturnsDefinition() {
        ConstraintDefinition def = definition(7L, "ONE_TEACHER_PER_SUBJECT_CLASS", ConstraintCategory.TEACHER, ConstraintType.HARD, ImportanceLevel.CRITICAL);
        when(repository.findById(7L)).thenReturn(Optional.of(def));

        ConstraintDefinitionResponse result = service.findById(7L);

        assertThat(result.getIdConstraintDefinition()).isEqualTo(7L);
    }

    @Test
    void findByIdThrowsNotFoundWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByIdThrowsBadRequestWhenNull() {
        assertThatThrownBy(() -> service.findById(null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConstraintDefinition definition(Long id, String code,
                                             ConstraintCategory category,
                                             ConstraintType type,
                                             ImportanceLevel importance) {
        return ConstraintDefinition.builder()
                .idConstraintDefinition(id)
                .code(code)
                .name(code.replace('_', ' ').toLowerCase())
                .category(category)
                .type(type)
                .defaultImportance(importance)
                .defaultEnabled(true)
                .parameterSchema("{\"enabled\":{\"type\":\"boolean\",\"default\":true}}")
                .build();
    }
}
