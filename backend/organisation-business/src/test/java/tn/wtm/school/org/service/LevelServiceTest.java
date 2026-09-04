package tn.wtm.school.org.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.ClassGroupRequest;
import tn.wtm.school.org.dto.request.LevelRequest;
import tn.wtm.school.org.dto.response.ClassGroupResponse;
import tn.wtm.school.org.dto.response.LevelResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.enums.Specialite;
import tn.wtm.school.org.mapper.ClassGroupMapper;
import tn.wtm.school.org.mapper.LevelMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.service.impl.LevelServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelServiceTest {

    @Mock LevelRepository        levelRepository;
    @Mock ClassGroupRepository   classGroupRepository;
    @Mock SchoolYearRepository   schoolYearRepository;
    @Mock SubjectLevelRepository subjectLevelRepository;
    @Mock LevelMapper            levelMapper;
    @Mock ClassGroupMapper       classGroupMapper;
    @Mock ObjectsValidator<LevelRequest>       levelValidator;
    @Mock ObjectsValidator<ClassGroupRequest>  classGroupValidator;

    LevelServiceImpl service;

    static final Long LVL_ID = 1L;
    static final Long SY_ID  = 2L;
    static final Long CG_ID  = 3L;

    @BeforeEach
    void setUp() {
        tn.wtm.school.common.context.TenantContext.setTenantId("tenant-1");
        service = new LevelServiceImpl(
                levelRepository, classGroupRepository, schoolYearRepository,
                subjectLevelRepository, levelMapper, classGroupMapper,
                levelValidator, classGroupValidator);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        tn.wtm.school.common.context.TenantContext.clear();
    }

    Level level(Long id, String code) {
        return Level.builder().idNiveau(id).code(code).estActif(true).build();
    }

    LevelResponse levelResp(Long id, String code) {
        return LevelResponse.builder().idNiveau(id).code(code).estActif(true).build();
    }

    ClassGroup classGroup(Long id, String code) {
        return ClassGroup.builder().idClasse(id).code(code)
                .codeSpecialite(Specialite.TCOM).estActif(true).build();
    }

    ClassGroupResponse cgResp(Long id) {
        return ClassGroupResponse.builder().idClasse(id).build();
    }

    // ══════════════════════════════════════════════════
    //  LEVEL
    // ══════════════════════════════════════════════════

    @Nested
    class CreateLevel {

        @Test
        void duplicateCode_throwsConflict() {
            when(levelRepository.existsByTenantIdAndCode("tenant-1", "L7")).thenReturn(true);

            assertThatThrownBy(() -> service.createLevel(LevelRequest.builder().code("L7").nom("7eme").build()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("L7");
        }

        @Test
        void happyPath_savesAndReturns() {
            Level entity = level(LVL_ID, "L7");
            when(levelMapper.toEntity(any())).thenReturn(entity);
            when(levelRepository.save(entity)).thenReturn(entity);
            when(levelMapper.toResponse(entity)).thenReturn(levelResp(LVL_ID, "L7"));

            assertThat(service.createLevel(LevelRequest.builder().code("L7").nom("7eme").build())
                    .getCode()).isEqualTo("L7");
        }
    }

    @Nested
    class ReadLevel {

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getLevelById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLevelById(LVL_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(LVL_ID.toString());
        }

        @Test
        void getActiveLevels_usesCorrectQuery() {
            Level entity = level(LVL_ID, "L7");
            when(levelRepository.findByTenantIdAndEstActifTrueOrderByNomAsc("tenant-1")).thenReturn(List.of(entity));
            when(levelMapper.toResponseList(List.of(entity))).thenReturn(List.of(levelResp(LVL_ID, "L7")));

            assertThat(service.getActivesLevels()).hasSize(1);
            verify(levelRepository).findByTenantIdAndEstActifTrueOrderByNomAsc("tenant-1");
        }

        @Test
        void getLevelWithClasses_notFound_throwsResourceNotFound() {
            when(levelRepository.findByIdWithClasses(LVL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLevelWithClasses(LVL_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getLevelWithSubjects_notFound_throwsResourceNotFound() {
            when(levelRepository.findByIdWithSubjects(LVL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLevelWithSubjects(LVL_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void existsByCode_nullCode_returnsFalse() {
            assertThat(service.existsByCode(null)).isFalse();
        }

        @Test
        void existsByCode_blankCode_returnsFalse() {
            assertThat(service.existsByCode("  ")).isFalse();
        }

        @Test
        void existsByCode_found_returnsTrue() {
            when(levelRepository.existsByTenantIdAndCode("tenant-1", "L7")).thenReturn(true);
            assertThat(service.existsByCode("L7")).isTrue();
        }
    }

    @Nested
    class UpdateLevel {

        @Test
        void notFound_throwsResourceNotFound() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateLevel(LevelRequest.builder().code("L7").nom("7eme").build(), LVL_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateCodeOnUpdate_throwsConflict() {
            Level entity = level(LVL_ID, "L7");
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(entity));
            when(levelRepository.existsByTenantIdAndCodeAndIdNiveauNot("tenant-1", "L8", LVL_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updateLevel(LevelRequest.builder().code("L8").nom("8eme").build(), LVL_ID))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class ToggleLevel {

        @Test
        void nullStatus_throwsBadRequest() {
            assertThatThrownBy(() -> service.toggleLevelStatus(LVL_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("statut");
        }

        @Test
        void deactivate_setsEstActifFalse() {
            Level entity = level(LVL_ID, "L7");
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(entity));
            when(levelRepository.save(entity)).thenReturn(entity);
            when(levelMapper.toResponse(entity)).thenReturn(levelResp(LVL_ID, "L7"));

            service.toggleLevelStatus(LVL_ID, false);

            assertThat(entity.getEstActif()).isFalse();
        }
    }

    @Nested
    class DeleteLevel {

        @Test
        void hasClasses_throwsConflict() {
            Level entity = level(LVL_ID, "L7");
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(entity));
            when(classGroupRepository.findByLevel_IdNiveau(LVL_ID))
                    .thenReturn(List.of(classGroup(CG_ID, "7A")));

            assertThatThrownBy(() -> service.deleteLevel(LVL_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("classes");
            verify(levelRepository, never()).delete(any());
        }

        @Test
        void hasSubjectLevels_throwsConflict() {
            Level entity = level(LVL_ID, "L7");
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(entity));
            when(classGroupRepository.findByLevel_IdNiveau(LVL_ID)).thenReturn(List.of());
            when(subjectLevelRepository.findByLevel_IdNiveau(LVL_ID))
                    .thenReturn(List.of(SubjectLevel.builder().idNiveauMatiere(1L).build()));

            assertThatThrownBy(() -> service.deleteLevel(LVL_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("matieres");
        }

        @Test
        void happyPath_deletesLevel() {
            Level entity = level(LVL_ID, "L7");
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(entity));
            when(classGroupRepository.findByLevel_IdNiveau(LVL_ID)).thenReturn(List.of());
            when(subjectLevelRepository.findByLevel_IdNiveau(LVL_ID)).thenReturn(List.of());

            service.deleteLevel(LVL_ID);

            verify(levelRepository).delete(entity);
        }
    }

    // ══════════════════════════════════════════════════
    //  CLASS GROUP
    // ══════════════════════════════════════════════════

    ClassGroupRequest cgReq(String code) {
        return ClassGroupRequest.builder()
                .code(code).levelId(LVL_ID).schoolYearId(SY_ID)
                .codeSpecialite(Specialite.TCOM).nbEleve(30).build();
    }

    @Nested
    class CreateClassGroup {

        @Test
        void levelNotFound_throwsResourceNotFound() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createClass(cgReq("7A")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void schoolYearNotFound_throwsResourceNotFound() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID))
                    .thenReturn(Optional.of(level(LVL_ID, "L7")));
            when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", SY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createClass(cgReq("7A")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateCode_throwsConflict() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(level(LVL_ID, "L7")));
            when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", SY_ID))
                    .thenReturn(Optional.of(SchoolYear.builder().idAnnee(SY_ID).build()));
            when(classGroupRepository.existsByCodeInLevelAndYear("7A", LVL_ID, SY_ID, null)).thenReturn(true);

            assertThatThrownBy(() -> service.createClass(cgReq("7A")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("7A");
        }

        @Test
        void happyPath_savesAndReturns() {
            Level lvl   = level(LVL_ID, "L7");
            SchoolYear sy = SchoolYear.builder().idAnnee(SY_ID).build();
            ClassGroup entity = classGroup(CG_ID, "7A");

            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(lvl));
            when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", SY_ID)).thenReturn(Optional.of(sy));
            when(classGroupMapper.toEntity(any())).thenReturn(entity);
            when(classGroupRepository.save(entity)).thenReturn(entity);
            when(classGroupMapper.toResponse(entity)).thenReturn(cgResp(CG_ID));

            assertThat(service.createClass(cgReq("7A")).getIdClasse()).isEqualTo(CG_ID);
            assertThat(entity.getLevel()).isEqualTo(lvl);
            assertThat(entity.getSchoolYear()).isEqualTo(sy);
        }
    }

    @Nested
    class ReadClassGroup {

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getClassGroupById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(classGroupRepository.findByTenantIdAndIdClasse("tenant-1", CG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getClassGroupById(CG_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getByLevel_levelNotFound_throwsResourceNotFound() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getClassGroupsByLevel(LVL_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(classGroupRepository, never()).findByLevel_IdNiveau(any());
        }

        @Test
        void getBySchoolYear_yearNotFound_throwsResourceNotFound() {
            when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", SY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getClassGroupsBySchoolYear(SY_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void existsByCodeInLevelAndYear_nullArgs_returnsFalse() {
            assertThat(service.existsByCodeInLevelAndYear(null, LVL_ID, SY_ID)).isFalse();
            assertThat(service.existsByCodeInLevelAndYear("7A", null, SY_ID)).isFalse();
            assertThat(service.existsByCodeInLevelAndYear("7A", LVL_ID, null)).isFalse();
        }
    }

    @Nested
    class UpdateClassGroup {

        @Test
        void notFound_throwsResourceNotFound() {
            when(classGroupRepository.findByTenantIdAndIdClasse("tenant-1", CG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateClassGroup(CG_ID, cgReq("7A")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateCodeOnUpdate_throwsConflict() {
            ClassGroup entity = classGroup(CG_ID, "7A");
            lenient().when(classGroupRepository.findByTenantIdAndIdClasse("tenant-1", CG_ID)).thenReturn(Optional.of(entity));
            lenient().when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(level(LVL_ID, "L7")));
            lenient().when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", SY_ID))
                    .thenReturn(Optional.of(SchoolYear.builder().idAnnee(SY_ID).build()));
            when(classGroupRepository.existsByCodeInLevelAndYear("7B", LVL_ID, SY_ID, CG_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updateClassGroup(CG_ID, cgReq("7B")))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class ToggleClassGroup {

        @Test
        void nullStatus_throwsBadRequest() {
            assertThatThrownBy(() -> service.toggleClassGroupStatus(CG_ID, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void deactivate_setsEstActifFalse() {
            ClassGroup entity = classGroup(CG_ID, "7A");
            when(classGroupRepository.findByTenantIdAndIdClasse("tenant-1", CG_ID)).thenReturn(Optional.of(entity));
            when(classGroupRepository.save(entity)).thenReturn(entity);
            when(classGroupMapper.toResponse(entity)).thenReturn(cgResp(CG_ID));

            service.toggleClassGroupStatus(CG_ID, false);

            assertThat(entity.getEstActif()).isFalse();
        }
    }

    @Nested
    class DeleteClassGroup {

        @Test
        void hasAssignments_throwsConflict() {
            ClassGroup entity = classGroup(CG_ID, "7A");
            entity.setTeachingAssignments(
                    List.of(TeachingAssignment.builder().idTeachingAssignment(1L).build()));
            when(classGroupRepository.findByIdWithAssignments(CG_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.deleteClassGroup(CG_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("affectations");
            verify(classGroupRepository, never()).delete(any());
        }

        @Test
        void notFound_throwsResourceNotFound() {
            when(classGroupRepository.findByIdWithAssignments(CG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteClassGroup(CG_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void happyPath_deletesClassGroup() {
            ClassGroup entity = classGroup(CG_ID, "7A");
            entity.setTeachingAssignments(List.of());
            when(classGroupRepository.findByIdWithAssignments(CG_ID)).thenReturn(Optional.of(entity));

            service.deleteClassGroup(CG_ID);

            verify(classGroupRepository).delete(entity);
        }
    }
}
