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
import tn.wtm.school.org.dto.request.TeachingAssignmentRequest;
import tn.wtm.school.org.dto.response.TeachingAssignmentResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.mapper.TeachingAssignmentMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.impl.TeachingAssignmentServiceImpl;

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
class TeachingAssignmentServiceTest {

    @Mock TeachingAssignmentRepository teachingAssignmentRepository;
    @Mock SchoolYearRepository schoolYearRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock ClassGroupRepository classGroupRepository;
    @Mock SubjectLevelRepository subjectLevelRepository;
    @Mock SubjectSessionTypeRepository subjectSessionTypeRepository;
    @Mock TeachingAssignmentMapper teachingAssignmentMapper;
    @Mock ObjectsValidator<TeachingAssignmentRequest> validator;

    TeachingAssignmentServiceImpl service;

    // ── fixtures ─────────────────────────────────────────────────────────────

    static final Long SY_ID  = 1L;
    static final Long T_ID   = 2L;
    static final Long CG_ID  = 3L;
    static final Long SL_ID  = 4L;
    static final Long SST_ID = 5L;
    static final Long TA_ID  = 9L;

    Level level;
    SchoolYear activeYear;
    Teacher activeTeacher;
    ClassGroup activeClassGroup;
    SubjectLevel subjectLevel;
    SubjectSessionType activeSessionType;

    @BeforeEach
    void setUp() {
        tn.wtm.school.common.context.TenantContext.setTenantId("tenant-1");
        service = new TeachingAssignmentServiceImpl(
                teachingAssignmentRepository,
                schoolYearRepository,
                teacherRepository,
                classGroupRepository,
                subjectLevelRepository,
                subjectSessionTypeRepository,
                teachingAssignmentMapper,
                validator
        );

        level            = Level.builder().idNiveau(10L).build();
        activeYear       = SchoolYear.builder().idAnnee(SY_ID).estActive(true).build();
        activeTeacher    = Teacher.builder().idEnseignant(T_ID).estEnPoste(true).maxHeuresSemaine(20).build();
        activeClassGroup = ClassGroup.builder().idClasse(CG_ID).schoolYear(activeYear).level(level).estActif(true).build();
        subjectLevel     = SubjectLevel.builder().idNiveauMatiere(SL_ID).level(level).heuresSemaine(2.0).build();
        activeSessionType = SubjectSessionType.builder()
                .idSubjectSessionType(SST_ID)
                .subjectLevel(subjectLevel)
                .duration(2.0)
                .estActif(true)
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    TeachingAssignmentRequest validRequest() {
        return TeachingAssignmentRequest.builder()
                .schoolYearId(SY_ID)
                .teacherId(T_ID)
                .classGroupId(CG_ID)
                .subjectLevelId(SL_ID)
                .subjectSessionTypeId(SST_ID)
                .priority(1)
                .isActive(true)
                .build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        tn.wtm.school.common.context.TenantContext.clear();
    }

    void mockValidRefs() {
        lenient().when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
        lenient().when(teacherRepository.findById(T_ID)).thenReturn(Optional.of(activeTeacher));
        lenient().when(classGroupRepository.findById(CG_ID)).thenReturn(Optional.of(activeClassGroup));
        lenient().when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
        lenient().when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(activeSessionType));
        lenient().when(teachingAssignmentRepository.findByTeacher_IdEnseignantAndSchoolYear_IdAnnee(T_ID, SY_ID))
                .thenReturn(List.of());
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void happyPath_savesAndReturnsResponse() {
            mockValidRefs();
            TeachingAssignment entity   = TeachingAssignment.builder().idTeachingAssignment(TA_ID).build();
            TeachingAssignmentResponse response = TeachingAssignmentResponse.builder().idTeachingAssignment(TA_ID).build();
            when(teachingAssignmentMapper.toEntity(any())).thenReturn(entity);
            when(teachingAssignmentRepository.save(entity)).thenReturn(entity);
            when(teachingAssignmentMapper.toResponse(entity)).thenReturn(response);

            TeachingAssignmentResponse result = service.createTeachingAssignment(validRequest());

            assertThat(result.getIdTeachingAssignment()).isEqualTo(TA_ID);
            assertThat(entity.getTeacher()).isEqualTo(activeTeacher);
            assertThat(entity.getClassGroup()).isEqualTo(activeClassGroup);
            assertThat(entity.getSubjectLevel()).isEqualTo(subjectLevel);
            assertThat(entity.getSchoolYear()).isEqualTo(activeYear);
            assertThat(entity.getSubjectSessionType()).isEqualTo(activeSessionType);
            verify(teachingAssignmentRepository).save(entity);
        }

        @Test
        void nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.createTeachingAssignment(
                    TeachingAssignmentRequest.builder()
                            .schoolYearId(null).teacherId(T_ID).classGroupId(CG_ID)
                            .subjectLevelId(SL_ID).subjectSessionTypeId(SST_ID).build()))
                    .isInstanceOf(BadRequestException.class);
            verify(teachingAssignmentRepository, never()).save(any());
        }

        @Test
        void schoolYearNotFound_throwsNotFound() {
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SY_ID.toString());
        }

        @Test
        void teacherNotFound_throwsNotFound() {
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(T_ID.toString());
        }

        @Test
        void classGroupNotFound_throwsNotFound() {
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.of(activeTeacher));
            when(classGroupRepository.findById(CG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(CG_ID.toString());
        }

        @Test
        void subjectLevelNotFound_throwsNotFound() {
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.of(activeTeacher));
            when(classGroupRepository.findById(CG_ID)).thenReturn(Optional.of(activeClassGroup));
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SL_ID.toString());
        }

        @Test
        void sessionTypeNotFound_throwsNotFound() {
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.of(activeTeacher));
            when(classGroupRepository.findById(CG_ID)).thenReturn(Optional.of(activeClassGroup));
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SST_ID.toString());
        }

        @Test
        void inactiveSchoolYear_throwsBadRequest() {
            activeYear.setEstActive(false);
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.of(activeTeacher));
            when(classGroupRepository.findById(CG_ID)).thenReturn(Optional.of(activeClassGroup));
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(activeSessionType));

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("annee scolaire");
        }

        @Test
        void teacherNotInPost_throwsBadRequest() {
            activeTeacher.setEstEnPoste(false);
            mockValidRefs();

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("enseignant");
        }

        @Test
        void inactiveClassGroup_throwsBadRequest() {
            activeClassGroup.setEstActif(false);
            mockValidRefs();

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("classe");
        }

        @Test
        void classGroupWrongSchoolYear_throwsBadRequest() {
            SchoolYear otherYear = SchoolYear.builder().idAnnee(99L).estActive(true).build();
            activeClassGroup.setSchoolYear(otherYear);
            mockValidRefs();

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("annee scolaire");
        }

        @Test
        void classLevelMismatchSubjectLevel_throwsBadRequest() {
            Level otherLevel = Level.builder().idNiveau(99L).build();
            subjectLevel.setLevel(otherLevel);
            mockValidRefs();

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("niveau");
        }

        @Test
        void sessionTypeWrongSubjectLevel_throwsBadRequest() {
            SubjectLevel otherSl = SubjectLevel.builder().idNiveauMatiere(99L).level(level).build();
            activeSessionType.setSubjectLevel(otherSl);
            mockValidRefs();

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("matiere-niveau");
        }

        @Test
        void inactiveSessionType_throwsBadRequest() {
            activeSessionType.setEstActif(false);
            mockValidRefs();

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("seance");
        }

        @Test
        void duplicateAssignment_throwsConflict() {
            mockValidRefs();
            when(teachingAssignmentRepository.existsDuplicate(SY_ID, T_ID, CG_ID, SL_ID, SST_ID, null))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("affectation");
        }

        @Test
        void weeklyLoadExceeded_throwsConflict() {
            // teacher has maxHeuresSemaine=20, already 19h assigned
            SubjectLevel heavySl = SubjectLevel.builder().idNiveauMatiere(SL_ID).level(level).heuresSemaine(2.0).build();
            subjectLevel = heavySl;
            activeTeacher.setMaxHeuresSemaine(20);

            SubjectLevel existingSl = SubjectLevel.builder().idNiveauMatiere(77L).level(level).heuresSemaine(19.0).build();
            TeachingAssignment existing = TeachingAssignment.builder()
                    .idTeachingAssignment(8L)
                    .isActive(true)
                    .subjectLevel(existingSl)
                    .build();

            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.of(activeTeacher));
            when(classGroupRepository.findById(CG_ID)).thenReturn(Optional.of(activeClassGroup));
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(heavySl));
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(activeSessionType));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignantAndSchoolYear_IdAnnee(T_ID, SY_ID))
                    .thenReturn(List.of(existing));

            assertThatThrownBy(() -> service.createTeachingAssignment(validRequest()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("hebdomadaire");
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Nested
    class Read {

        @Test
        void getById_found_returnsResponse() {
            TeachingAssignment entity   = TeachingAssignment.builder().idTeachingAssignment(TA_ID).build();
            TeachingAssignmentResponse response = TeachingAssignmentResponse.builder().idTeachingAssignment(TA_ID).build();
            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.of(entity));
            when(teachingAssignmentMapper.toResponse(entity)).thenReturn(response);

            assertThat(service.getTeachingAssignmentById(TA_ID).getIdTeachingAssignment()).isEqualTo(TA_ID);
        }

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getTeachingAssignmentById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeachingAssignmentById(TA_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(TA_ID.toString());
        }

        @Test
        void getFullyLoaded_notFound_throwsResourceNotFound() {
            when(teachingAssignmentRepository.findByIdFullyLoaded(TA_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeachingAssignmentFullyLoaded(TA_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        // ── tenant isolation: filter by teacher stays within tenant scope ────────

        @Test
        void getByTeacher_tenantIsolation_usesTeacherScopedQuery() {
            TeachingAssignment ta = TeachingAssignment.builder().idTeachingAssignment(TA_ID).build();
            TeachingAssignmentResponse resp = TeachingAssignmentResponse.builder().idTeachingAssignment(TA_ID).build();
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.of(activeTeacher));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignant(T_ID)).thenReturn(List.of(ta));
            when(teachingAssignmentMapper.toResponseList(List.of(ta))).thenReturn(List.of(resp));

            List<TeachingAssignmentResponse> result = service.getTeachingAssignmentsByTeacher(T_ID);

            assertThat(result).hasSize(1);
            verify(teachingAssignmentRepository).findByTeacher_IdEnseignant(T_ID);
        }

        @Test
        void getByTeacher_teacherNotFound_throwsResourceNotFound() {
            when(teacherRepository.findById(T_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeachingAssignmentsByTeacher(T_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(teachingAssignmentRepository, never()).findByTeacher_IdEnseignant(any());
        }

        @Test
        void getBySchoolYear_tenantIsolation_usesYearScopedQuery() {
            TeachingAssignment ta = TeachingAssignment.builder().idTeachingAssignment(TA_ID).build();
            TeachingAssignmentResponse resp = TeachingAssignmentResponse.builder().idTeachingAssignment(TA_ID).build();
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teachingAssignmentRepository.findBySchoolYear_IdAnnee(SY_ID)).thenReturn(List.of(ta));
            when(teachingAssignmentMapper.toResponseList(List.of(ta))).thenReturn(List.of(resp));

            List<TeachingAssignmentResponse> result = service.getTeachingAssignmentsBySchoolYear(SY_ID);

            assertThat(result).hasSize(1);
            verify(teachingAssignmentRepository).findBySchoolYear_IdAnnee(SY_ID);
        }

        @Test
        void getBySchoolYear_yearNotFound_throwsResourceNotFound() {
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeachingAssignmentsBySchoolYear(SY_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(teachingAssignmentRepository, never()).findBySchoolYear_IdAnnee(any());
        }

        @Test
        void getActiveBySchoolYear_usesActiveScopedQuery() {
            when(schoolYearRepository.findById(SY_ID)).thenReturn(Optional.of(activeYear));
            when(teachingAssignmentRepository.findByIsActiveTrueAndSchoolYear_IdAnnee(SY_ID)).thenReturn(List.of());
            when(teachingAssignmentMapper.toResponseList(List.of())).thenReturn(List.of());

            assertThat(service.getActiveTeachingAssignmentsBySchoolYear(SY_ID)).isEmpty();
            verify(teachingAssignmentRepository).findByIsActiveTrueAndSchoolYear_IdAnnee(SY_ID);
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Nested
    class Update {

        @Test
        void happyPath_updatesAndReturnsResponse() {
            TeachingAssignment entity   = TeachingAssignment.builder().idTeachingAssignment(TA_ID)
                    .schoolYear(activeYear).teacher(activeTeacher)
                    .classGroup(activeClassGroup).subjectLevel(subjectLevel)
                    .subjectSessionType(activeSessionType).isActive(true).build();
            TeachingAssignmentResponse response = TeachingAssignmentResponse.builder().idTeachingAssignment(TA_ID).build();

            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.of(entity));
            mockValidRefs();
            when(teachingAssignmentRepository.existsDuplicate(SY_ID, T_ID, CG_ID, SL_ID, SST_ID, TA_ID))
                    .thenReturn(false);
            when(teachingAssignmentRepository.save(entity)).thenReturn(entity);
            when(teachingAssignmentMapper.toResponse(entity)).thenReturn(response);

            TeachingAssignmentResponse result = service.updateTeachingAssignment(TA_ID, validRequest());

            assertThat(result.getIdTeachingAssignment()).isEqualTo(TA_ID);
            verify(teachingAssignmentRepository).save(entity);
        }

        @Test
        void notFound_throwsResourceNotFound() {
            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTeachingAssignment(TA_ID, validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    class Delete {

        @Test
        void happyPath_deletesEntity() {
            TeachingAssignment entity = TeachingAssignment.builder().idTeachingAssignment(TA_ID).build();
            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.of(entity));

            service.deleteTeachingAssignment(TA_ID);

            verify(teachingAssignmentRepository).delete(entity);
        }

        @Test
        void notFound_throwsResourceNotFound() {
            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteTeachingAssignment(TA_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(teachingAssignmentRepository, never()).delete(any());
        }
    }

    // ── TOGGLE STATUS ─────────────────────────────────────────────────────────

    @Nested
    class ToggleStatus {

        @Test
        void deactivate_setsIsActiveFalse() {
            TeachingAssignment entity = TeachingAssignment.builder()
                    .idTeachingAssignment(TA_ID).isActive(true).build();
            TeachingAssignmentResponse resp = TeachingAssignmentResponse.builder()
                    .idTeachingAssignment(TA_ID).isActive(false).build();
            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.of(entity));
            when(teachingAssignmentRepository.save(entity)).thenReturn(entity);
            when(teachingAssignmentMapper.toResponse(entity)).thenReturn(resp);

            service.toggleTeachingAssignmentStatus(TA_ID, false);

            assertThat(entity.getIsActive()).isFalse();
        }

        @Test
        void activate_validAssignment_setsIsActiveTrue() {
            TeachingAssignment entity = TeachingAssignment.builder()
                    .idTeachingAssignment(TA_ID).isActive(false)
                    .schoolYear(activeYear).teacher(activeTeacher)
                    .classGroup(activeClassGroup).subjectLevel(subjectLevel)
                    .subjectSessionType(activeSessionType).build();
            TeachingAssignmentResponse resp = TeachingAssignmentResponse.builder()
                    .idTeachingAssignment(TA_ID).isActive(true).build();

            when(teachingAssignmentRepository.findById(TA_ID)).thenReturn(Optional.of(entity));
            // activation re-validates refs via validateTeachingAssignment
            mockValidRefs();
            when(teachingAssignmentRepository.existsDuplicate(SY_ID, T_ID, CG_ID, SL_ID, SST_ID, TA_ID))
                    .thenReturn(false);
            when(teachingAssignmentRepository.save(entity)).thenReturn(entity);
            when(teachingAssignmentMapper.toResponse(entity)).thenReturn(resp);

            service.toggleTeachingAssignmentStatus(TA_ID, true);

            assertThat(entity.getIsActive()).isTrue();
        }

        @Test
        void nullStatus_throwsBadRequest() {
            assertThatThrownBy(() -> service.toggleTeachingAssignmentStatus(TA_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("statut");
            verify(teachingAssignmentRepository, never()).save(any());
        }
    }

    // ── EXISTS / CONFLICT HELPERS ─────────────────────────────────────────────

    @Nested
    class ExistenceChecks {

        @Test
        void existsTeachingAssignment_anyNullArg_returnsFalse() {
            assertThat(service.existsTeachingAssignment(null, T_ID, CG_ID, SL_ID, SST_ID)).isFalse();
            assertThat(service.existsTeachingAssignment(SY_ID, null, CG_ID, SL_ID, SST_ID)).isFalse();
        }

        @Test
        void existsTeachingAssignment_allPresent_delegatesToRepository() {
            when(teachingAssignmentRepository
                    .existsBySchoolYear_IdAnneeAndTeacher_IdEnseignantAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
                            SY_ID, T_ID, CG_ID, SL_ID, SST_ID))
                    .thenReturn(true);

            assertThat(service.existsTeachingAssignment(SY_ID, T_ID, CG_ID, SL_ID, SST_ID)).isTrue();
        }

        @Test
        void existsDuplicate_anyNullArg_returnsFalse() {
            assertThat(service.existsDuplicateTeachingAssignment(null, T_ID, CG_ID, SL_ID, SST_ID, null)).isFalse();
        }

        @Test
        void existsDuplicate_withExcludeId_delegatesToRepository() {
            when(teachingAssignmentRepository.existsDuplicate(SY_ID, T_ID, CG_ID, SL_ID, SST_ID, TA_ID))
                    .thenReturn(false);

            assertThat(service.existsDuplicateTeachingAssignment(SY_ID, T_ID, CG_ID, SL_ID, SST_ID, TA_ID)).isFalse();
        }
    }
}
