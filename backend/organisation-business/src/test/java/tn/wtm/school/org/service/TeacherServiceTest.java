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
import tn.wtm.school.org.dto.request.TeacherRequest;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.mapper.TeacherMapper;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.impl.TeacherServiceImpl;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock TeacherRepository             teacherRepository;
    @Mock TeachingAssignmentRepository  teachingAssignmentRepository;
    @Mock SubjectLevelRepository        subjectLevelRepository;
    @Mock SchoolUserRepository          schoolUserRepository;
    @Mock TeacherMapper                 teacherMapper;
    @Mock ObjectsValidator<TeacherRequest> teacherValidator;
    @Mock JwtClaimsExtractor            jwtClaimsExtractor;

    TeacherServiceImpl service;

    static final Long T_ID  = 1L;
    static final Long SL_ID = 5L;

    @BeforeEach
    void setUp() {
        tn.wtm.school.common.context.TenantContext.setTenantId("tenant-1");
        service = new TeacherServiceImpl(
                teacherRepository, teachingAssignmentRepository,
                subjectLevelRepository, schoolUserRepository, teacherMapper, teacherValidator,
                jwtClaimsExtractor);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        tn.wtm.school.common.context.TenantContext.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    TeacherRequest req(String code) {
        return TeacherRequest.builder()
                .codeEnseignant(code).numIdentite("CIN-" + code)
                .nom("Ben Ali").prenom("Amel")
                .email(code + "@school.tn")
                .maxHeuresSemaine(20).maxHeuresJour(6).minHeuresJour(0)
                .build();
    }

    Teacher teacher(Long id, String code) {
        return Teacher.builder().idEnseignant(id).codeEnseignant(code)
                .nom("Ben Ali").prenom("Amel").estEnPoste(true)
                .maxHeuresSemaine(20).build();
    }

    TeacherResponse resp(Long id) {
        return TeacherResponse.builder().idEnseignant(id).estEnPoste(true).build();
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void happyPath_savesAndReturns() {
            Teacher entity = teacher(T_ID, "T1");
            when(teacherMapper.toEntity(any())).thenReturn(entity);
            when(teacherRepository.save(entity)).thenReturn(entity);
            when(teacherMapper.toResponseLight(entity)).thenReturn(resp(T_ID));

            assertThat(service.createTeacher(req("T1")).getIdEnseignant()).isEqualTo(T_ID);
            verify(teacherRepository).save(entity);
        }

        @Test
        void duplicateCode_throwsConflict() {
            when(teacherRepository.existsByTenantIdAndCodeEnseignant("tenant-1", "T1")).thenReturn(true);

            assertThatThrownBy(() -> service.createTeacher(req("T1")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("T1");
        }

        @Test
        void duplicateNumIdentite_throwsConflict() {
            when(teacherRepository.existsByTenantIdAndNumIdentite("tenant-1", "CIN-T1")).thenReturn(true);

            assertThatThrownBy(() -> service.createTeacher(req("T1")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("identite");
        }

        @Test
        void duplicateEmail_throwsConflict() {
            when(teacherRepository.existsByTenantIdAndEmail("tenant-1", "T1@school.tn")).thenReturn(true);

            assertThatThrownBy(() -> service.createTeacher(req("T1")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("T1@school.tn");
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Nested
    class Read {

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getTeacherById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeacherById(T_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(T_ID.toString());
        }

        @Test
        void getById_found_returnsResponse() {
            Teacher entity = teacher(T_ID, "T1");
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teacherMapper.toResponseLight(entity)).thenReturn(resp(T_ID));

            assertThat(service.getTeacherById(T_ID).getIdEnseignant()).isEqualTo(T_ID);
        }

        @Test
        void getByCode_nullCode_throwsBadRequest() {
            assertThatThrownBy(() -> service.getTeacherByCode(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getByCode_blankCode_throwsBadRequest() {
            assertThatThrownBy(() -> service.getTeacherByCode("  "))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getByCode_notFound_throwsResourceNotFound() {
            when(teacherRepository.findByTenantIdAndCodeEnseignant("tenant-1", "T9")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeacherByCode("T9"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getByNumIdentite_nullValue_throwsBadRequest() {
            assertThatThrownBy(() -> service.getTeacherByNumIdentite(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getByNumIdentite_notFound_throwsResourceNotFound() {
            when(teacherRepository.findByTenantIdAndNumIdentite("tenant-1", "CIN-X")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeacherByNumIdentite("CIN-X"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getActiveTeachers_usesCorrectQuery() {
            when(teacherRepository.findByTenantIdAndEstEnPosteTrueOrderByNomAscPrenomAsc("tenant-1")).thenReturn(List.of());
            when(teacherMapper.toResponseLightList(List.of())).thenReturn(List.of());

            service.getActiveTeachers();

            verify(teacherRepository).findByTenantIdAndEstEnPosteTrueOrderByNomAscPrenomAsc("tenant-1");
        }

        @Test
        void getTeachersBySubjectLevel_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getTeachersBySubjectLevel(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getTeachersBySubjectLevel_notFound_throwsResourceNotFound() {
            when(subjectLevelRepository.existsById(SL_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.getTeachersBySubjectLevel(SL_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getTeachersBySubjectLevel_happy_delegatesToRepository() {
            Teacher entity = teacher(T_ID, "T1");
            when(subjectLevelRepository.existsById(SL_ID)).thenReturn(true);
            when(teacherRepository.findBySubjectLevel(SL_ID)).thenReturn(List.of(entity));
            when(teacherMapper.toResponseLightList(List.of(entity))).thenReturn(List.of(resp(T_ID)));

            List<TeacherResponse> result = service.getTeachersBySubjectLevel(SL_ID);

            assertThat(result).hasSize(1);
            verify(teacherRepository).findBySubjectLevel(SL_ID);
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Nested
    class Update {

        @Test
        void notFound_throwsResourceNotFound() {
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTeacher(T_ID, req("T1")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateCodeOnUpdate_throwsConflict() {
            Teacher entity = teacher(T_ID, "T1");
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teacherRepository.existsByCodeEnseignantAndIdEnseignantNot("T2", T_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updateTeacher(T_ID, req("T2")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void happyPath_updatesAndReturns() {
            Teacher entity = teacher(T_ID, "T1");
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teacherRepository.save(entity)).thenReturn(entity);
            when(teacherMapper.toResponseLight(entity)).thenReturn(resp(T_ID));

            assertThat(service.updateTeacher(T_ID, req("T1")).getIdEnseignant()).isEqualTo(T_ID);
        }
    }

    // ── ACTIVATE / DEACTIVATE ─────────────────────────────────────────────────

    @Nested
    class StatusChange {

        @Test
        void deactivateTeacher_setsEstEnPosteFalse() {
            Teacher entity = teacher(T_ID, "T1");
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teacherRepository.save(entity)).thenReturn(entity);
            when(teacherMapper.toResponseLight(entity)).thenReturn(resp(T_ID));

            service.deactivateTeacher(T_ID);

            assertThat(entity.getEstEnPoste()).isFalse();
        }

        @Test
        void reactivateTeacher_setsEstEnPosteTrue() {
            Teacher entity = teacher(T_ID, "T1");
            entity.setEstEnPoste(false);
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teacherRepository.save(entity)).thenReturn(entity);
            when(teacherMapper.toResponseLight(entity)).thenReturn(resp(T_ID));

            service.reactivateTeacher(T_ID);

            assertThat(entity.getEstEnPoste()).isTrue();
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    class Delete {

        @Test
        void hasAssignments_throwsConflict() {
            Teacher entity = teacher(T_ID, "T1");
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignant(T_ID))
                    .thenReturn(List.of(TeachingAssignment.builder().idTeachingAssignment(1L).build()));

            assertThatThrownBy(() -> service.deleteTeacher(T_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("affectations");
            verify(teacherRepository, never()).delete(any());
        }

        @Test
        void happyPath_deletesTeacher() {
            Teacher entity = teacher(T_ID, "T1");
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignant(T_ID)).thenReturn(List.of());

            service.deleteTeacher(T_ID);

            verify(teacherRepository).delete(entity);
        }
    }

    // ── WORKLOAD ──────────────────────────────────────────────────────────────

    @Nested
    class Workload {

        @Test
        void isOverloaded_noMaxHours_returnsFalse() {
            Teacher entity = Teacher.builder().idEnseignant(T_ID).maxHeuresSemaine(null).build();
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));

            assertThat(service.isOverloaded(T_ID, 5.0)).isFalse();
        }

        @Test
        void isOverloaded_underLimit_returnsFalse() {
            Teacher entity = Teacher.builder().idEnseignant(T_ID).maxHeuresSemaine(20).build();
            SubjectSessionType sst = SubjectSessionType.builder().duration(4.0).build();
            TeachingAssignment ta = TeachingAssignment.builder()
                    .isActive(true).subjectSessionType(sst).build();
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignant(T_ID)).thenReturn(List.of(ta));

            // currentLoad=4, additional=10, total=14 <= 20
            assertThat(service.isOverloaded(T_ID, 10.0)).isFalse();
        }

        @Test
        void isOverloaded_overLimit_returnsTrue() {
            Teacher entity = Teacher.builder().idEnseignant(T_ID).maxHeuresSemaine(20).build();
            SubjectSessionType sst = SubjectSessionType.builder().duration(18.0).build();
            TeachingAssignment ta = TeachingAssignment.builder()
                    .isActive(true).subjectSessionType(sst).build();
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignant(T_ID)).thenReturn(List.of(ta));

            // currentLoad=18, additional=5, total=23 > 20
            assertThat(service.isOverloaded(T_ID, 5.0)).isTrue();
        }

        @Test
        void isOverloaded_negativeAdditional_throwsBadRequest() {
            Teacher entity = Teacher.builder().idEnseignant(T_ID).maxHeuresSemaine(20).build();
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.isOverloaded(T_ID, -1.0))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("positives");
        }

        @Test
        void isOverloaded_nullAdditional_treatedAsZero() {
            Teacher entity = Teacher.builder().idEnseignant(T_ID).maxHeuresSemaine(20).build();
            when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", T_ID)).thenReturn(Optional.of(entity));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignant(T_ID)).thenReturn(List.of());

            assertThat(service.isOverloaded(T_ID, null)).isFalse();
        }

        @Test
        void getTeacherWorkload_onlyCountsActiveAssignments() {
            SubjectSessionType sst = SubjectSessionType.builder().duration(2.0).build();
            TeachingAssignment active   = TeachingAssignment.builder().isActive(true).subjectSessionType(sst).build();
            TeachingAssignment inactive = TeachingAssignment.builder().isActive(false).subjectSessionType(sst).build();
            Teacher entity = Teacher.builder().idEnseignant(T_ID).codeEnseignant("T1")
                    .nom("Ben Ali").prenom("Amel").estEnPoste(true)
                    .teachingAssignments(List.of(active, inactive)).build();

            when(teacherRepository.findByTenantIdAndEstEnPosteTrueOrderByNomAscPrenomAsc("tenant-1")).thenReturn(List.of(entity));
            when(teachingAssignmentRepository.findByTeacher_IdEnseignant(T_ID))
                    .thenReturn(List.of(active, inactive));

            List<TeacherResponse> result = service.getTeacherWorkload();

            assertThat(result).hasSize(1);
            // only the active assignment's 2h should count
            assertThat(result.get(0).getTotalHeures()).isEqualTo(2.0);
        }
    }
}
