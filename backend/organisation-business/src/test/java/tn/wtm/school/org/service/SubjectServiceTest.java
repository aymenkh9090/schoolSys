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
import tn.wtm.school.org.dto.request.SubjectLevelRequest;
import tn.wtm.school.org.dto.request.SubjectRequest;
import tn.wtm.school.org.dto.request.SubjectSessionTypeRequest;
import tn.wtm.school.org.dto.response.SubjectLevelResponse;
import tn.wtm.school.org.dto.response.SubjectResponse;
import tn.wtm.school.org.dto.response.SubjectSessionTypeResponse;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.mapper.SubjectLevelMapper;
import tn.wtm.school.org.mapper.SubjectMapper;
import tn.wtm.school.org.mapper.SubjectSessionTypeMapper;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.impl.SubjectServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock SubjectRepository            subjectRepository;
    @Mock SubjectLevelRepository       subjectLevelRepository;
    @Mock SubjectSessionTypeRepository subjectSessionTypeRepository;
    @Mock LevelRepository              levelRepository;
    @Mock PatternRepository            patternRepository;
    @Mock TeachingAssignmentRepository teachingAssignmentRepository;
    @Mock SubjectMapper                subjectMapper;
    @Mock SubjectLevelMapper           subjectLevelMapper;
    @Mock SubjectSessionTypeMapper     subjectSessionTypeMapper;
    @Mock ObjectsValidator<SubjectRequest>        subjectValidator;
    @Mock ObjectsValidator<SubjectLevelRequest>   subjectLevelValidator;
    @Mock ObjectsValidator<SubjectSessionTypeRequest> subjectSessionTypeValidator;

    SubjectServiceImpl service;

    static final Long S_ID   = 1L;
    static final Long L_ID   = 2L;
    static final Long SL_ID  = 3L;
    static final Long SST_ID = 4L;

    @BeforeEach
    void setUp() {
        tn.wtm.school.common.context.TenantContext.setTenantId("tenant-1");
        service = new SubjectServiceImpl(
                subjectRepository, subjectLevelRepository, subjectSessionTypeRepository,
                levelRepository, patternRepository, teachingAssignmentRepository,
                subjectMapper, subjectLevelMapper, subjectSessionTypeMapper,
                subjectValidator, subjectLevelValidator, subjectSessionTypeValidator);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        tn.wtm.school.common.context.TenantContext.clear();
    }

    Subject subject(Long id, String code) {
        return Subject.builder().idMatiere(id).codeMatiere(code).estEnseignee(true).build();
    }

    Level level(Long id) {
        return Level.builder().idNiveau(id).code("L7").build();
    }

    SubjectLevel subjectLevel(Long id) {
        return SubjectLevel.builder().idNiveauMatiere(id)
                .subject(subject(S_ID, "MATH")).level(level(L_ID)).build();
    }

    SubjectSessionType sessionType(Long id, boolean requiresSplit) {
        return SubjectSessionType.builder().idSubjectSessionType(id)
                .type(SessionType.COURSE).duration(2.0)
                .requiresSplit(requiresSplit).estActif(true)
                .groupCount(requiresSplit ? 2 : null).build();
    }

    // ══════════════════════════════════════════════════
    //  SUBJECT
    // ══════════════════════════════════════════════════

    @Nested
    class CreateSubject {

        @Test
        void duplicateCode_throwsConflict() {
            when(subjectRepository.existsByTenantIdAndCodeMatiere("tenant-1", "MATH")).thenReturn(true);

            assertThatThrownBy(() -> service.createSubject(SubjectRequest.builder().codeMatiere("MATH").build()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("MATH");
        }

        @Test
        void happyPath_savesAndReturns() {
            Subject entity = subject(S_ID, "MATH");
            when(subjectMapper.toEntity(any())).thenReturn(entity);
            when(subjectRepository.save(entity)).thenReturn(entity);
            when(subjectMapper.toResponseLight(entity))
                    .thenReturn(SubjectResponse.builder().idMatiere(S_ID).codeMatiere("MATH").build());

            assertThat(service.createSubject(SubjectRequest.builder().codeMatiere("MATH").build())
                    .getCodeMatiere()).isEqualTo("MATH");
        }
    }

    @Nested
    class ReadSubject {

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getSubjectById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(subjectRepository.findByIdWithLevels(S_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSubjectById(S_ID))
                    .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining(S_ID.toString());
        }

        @Test
        void existsByCodeMatiere_nullCode_returnsFalse() {
            assertThat(service.existsByCodeMatiere(null)).isFalse();
            assertThat(service.existsByCodeMatiere("  ")).isFalse();
        }

        @Test
        void searchSubjects_blankSearch_returnsActive() {
            Subject entity = subject(S_ID, "MATH");
            when(subjectRepository.findByTenantIdAndEstEnseigneeTrueOrderByCodeMatiereAsc("tenant-1")).thenReturn(List.of(entity));
            when(subjectMapper.toResponseLight(entity))
                    .thenReturn(SubjectResponse.builder().idMatiere(S_ID).build());

            service.searchSubjects("  ");

            verify(subjectRepository).findByTenantIdAndEstEnseigneeTrueOrderByCodeMatiereAsc("tenant-1");
            verify(subjectRepository, never()).searchByLibOrCode(any());
        }

        @Test
        void searchSubjects_nonBlank_searchesByLibOrCode() {
            when(subjectRepository.searchByLibOrCode("math")).thenReturn(List.of());

            service.searchSubjects("math");

            verify(subjectRepository).searchByLibOrCode("math");
        }
    }

    @Nested
    class UpdateSubject {

        @Test
        void duplicateCode_throwsConflict() {
            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.of(subject(S_ID, "MATH")));
            when(subjectRepository.existsByTenantIdAndCodeMatiereAndIdMatiereNot("tenant-1", "PHYS", S_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updateSubject(S_ID, SubjectRequest.builder().codeMatiere("PHYS").build()))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class ToggleSubject {

        @Test
        void nullStatus_throwsBadRequest() {
            assertThatThrownBy(() -> service.toggleEnseignee(S_ID, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void toggleOff_setsEstEnseigneeFalse() {
            Subject entity = subject(S_ID, "MATH");
            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.of(entity));
            when(subjectRepository.save(entity)).thenReturn(entity);
            when(subjectMapper.toResponseLight(entity)).thenReturn(SubjectResponse.builder().build());

            service.toggleEnseignee(S_ID, false);

            assertThat(entity.getEstEnseignee()).isFalse();
        }
    }

    @Nested
    class DeleteSubject {

        @Test
        void hasSubjectLevels_throwsConflict() {
            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.of(subject(S_ID, "MATH")));
            when(subjectLevelRepository.findBySubject_IdMatiere(S_ID))
                    .thenReturn(List.of(subjectLevel(SL_ID)));

            assertThatThrownBy(() -> service.deleteSubject(S_ID))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("niveaux");
            verify(subjectRepository, never()).delete(any());
        }

        @Test
        void happyPath_deletesSubject() {
            Subject entity = subject(S_ID, "MATH");
            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.of(entity));
            when(subjectLevelRepository.findBySubject_IdMatiere(S_ID)).thenReturn(List.of());

            service.deleteSubject(S_ID);

            verify(subjectRepository).delete(entity);
        }
    }

    // ══════════════════════════════════════════════════
    //  SUBJECT LEVEL
    // ══════════════════════════════════════════════════

    SubjectLevelRequest slReq() {
        return SubjectLevelRequest.builder().subjectId(S_ID).levelId(L_ID).heuresSemaine(4.0).build();
    }

    @Nested
    class CreateSubjectLevel {

        @Test
        void subjectNotFound_throwsResourceNotFound() {
            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createSubjectLevel(slReq()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void levelNotFound_throwsResourceNotFound() {
            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.of(subject(S_ID, "MATH")));
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", L_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createSubjectLevel(slReq()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicate_throwsConflict() {
            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.of(subject(S_ID, "MATH")));
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", L_ID)).thenReturn(Optional.of(level(L_ID)));
            when(subjectLevelRepository.existsBySubject_IdMatiereAndLevel_IdNiveau(S_ID, L_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.createSubjectLevel(slReq()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("niveau");
        }

        @Test
        void happyPath_linksSubjectAndLevel() {
            Subject s = subject(S_ID, "MATH");
            Level   l = level(L_ID);
            SubjectLevel entity = subjectLevel(SL_ID);

            when(subjectRepository.findByTenantIdAndIdMatiere("tenant-1", S_ID)).thenReturn(Optional.of(s));
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", L_ID)).thenReturn(Optional.of(l));
            when(subjectLevelMapper.toEntity(any())).thenReturn(entity);
            when(subjectLevelRepository.save(entity)).thenReturn(entity);
            when(subjectLevelMapper.toResponseLight(entity))
                    .thenReturn(SubjectLevelResponse.builder().idNiveauMatiere(SL_ID).build());

            service.createSubjectLevel(slReq());

            assertThat(entity.getSubject()).isEqualTo(s);
            assertThat(entity.getLevel()).isEqualTo(l);
        }
    }

    @Nested
    class ReadSubjectLevel {

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getSubjectLevelById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(subjectLevelRepository.findByIdFullyLoaded(SL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSubjectLevelById(SL_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void existsSubjectLevel_nullArgs_returnsFalse() {
            assertThat(service.existsSubjectLevel(null, L_ID)).isFalse();
            assertThat(service.existsSubjectLevel(S_ID, null)).isFalse();
        }

        @Test
        void getByLevel_levelNotFound_throwsResourceNotFound() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", L_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSubjectLevelsByLevel(L_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class DeleteSubjectLevel {

        @Test
        void hasPatterns_throwsConflict() {
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel(SL_ID)));
            when(patternRepository.findBySubjectLevel_IdNiveauMatiere(SL_ID))
                    .thenReturn(List.of(tn.wtm.school.org.entity.Pattern.builder().idPattern(1L).build()));

            assertThatThrownBy(() -> service.deleteSubjectLevel(SL_ID))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("patterns");
        }

        @Test
        void hasAssignments_throwsConflict() {
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel(SL_ID)));
            when(patternRepository.findBySubjectLevel_IdNiveauMatiere(SL_ID)).thenReturn(List.of());
            when(teachingAssignmentRepository.findBySubjectLevel_IdNiveauMatiere(SL_ID))
                    .thenReturn(List.of(TeachingAssignment.builder().idTeachingAssignment(1L).build()));

            assertThatThrownBy(() -> service.deleteSubjectLevel(SL_ID))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("affectations");
        }

        @Test
        void happyPath_deletesSubjectLevel() {
            SubjectLevel entity = subjectLevel(SL_ID);
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(entity));
            when(patternRepository.findBySubjectLevel_IdNiveauMatiere(SL_ID)).thenReturn(List.of());
            when(teachingAssignmentRepository.findBySubjectLevel_IdNiveauMatiere(SL_ID)).thenReturn(List.of());

            service.deleteSubjectLevel(SL_ID);

            verify(subjectLevelRepository).delete(entity);
        }
    }

    // ══════════════════════════════════════════════════
    //  SUBJECT SESSION TYPE
    // ══════════════════════════════════════════════════

    SubjectSessionTypeRequest sstReq(boolean requiresSplit, Integer groupCount) {
        return SubjectSessionTypeRequest.builder()
                .subjectLevelId(SL_ID).type(SessionType.COURSE)
                .duration(2.0).requiresSplit(requiresSplit).groupCount(groupCount)
                .build();
    }

    @Nested
    class CreateSessionType {

        @Test
        void subjectLevelNotFound_throwsResourceNotFound() {
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createSessionType(sstReq(false, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateType_throwsConflict() {
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel(SL_ID)));
            when(subjectSessionTypeRepository.existsBySubjectLevel_IdNiveauMatiereAndType(SL_ID, SessionType.COURSE))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createSessionType(sstReq(false, null)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void requiresSplit_withoutGroupCount_throwsBadRequest() {
            SubjectSessionType entity = SubjectSessionType.builder()
                    .type(SessionType.COURSE).duration(2.0).requiresSplit(true).groupCount(null).build();
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel(SL_ID)));
            when(subjectSessionTypeMapper.toEntity(any())).thenReturn(entity);

            assertThatThrownBy(() -> service.createSessionType(sstReq(true, null)))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("groupes");
        }

        @Test
        void requiresSplit_withGroupCount_savesOk() {
            SubjectLevel sl = subjectLevel(SL_ID);
            SubjectSessionType entity = SubjectSessionType.builder()
                    .type(SessionType.COURSE).duration(2.0).requiresSplit(true).groupCount(2).build();
            SubjectSessionTypeResponse resp = SubjectSessionTypeResponse.builder()
                    .idSubjectSessionType(SST_ID).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(sl));
            when(subjectSessionTypeMapper.toEntity(any())).thenReturn(entity);
            when(subjectSessionTypeRepository.save(entity)).thenReturn(entity);
            when(subjectSessionTypeMapper.toResponse(entity)).thenReturn(resp);

            service.createSessionType(sstReq(true, 2));

            verify(subjectSessionTypeRepository).save(entity);
        }

        @Test
        void noSplit_nullifyGroupCount() {
            SubjectLevel sl = subjectLevel(SL_ID);
            SubjectSessionType entity = SubjectSessionType.builder()
                    .type(SessionType.COURSE).duration(2.0).requiresSplit(false).groupCount(3).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(sl));
            when(subjectSessionTypeMapper.toEntity(any())).thenReturn(entity);
            when(subjectSessionTypeRepository.save(entity)).thenReturn(entity);
            when(subjectSessionTypeMapper.toResponse(entity)).thenReturn(SubjectSessionTypeResponse.builder().build());

            service.createSessionType(sstReq(false, null));

            // normalizeSessionType should set groupCount to null when not split
            assertThat(entity.getGroupCount()).isNull();
        }
    }

    @Nested
    class ToggleSessionType {

        @Test
        void activate_setsEstActifTrue() {
            SubjectSessionType entity = sessionType(SST_ID, false);
            entity.setEstActif(false);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(entity));
            when(subjectSessionTypeRepository.save(entity)).thenReturn(entity);
            when(subjectSessionTypeMapper.toResponse(entity)).thenReturn(SubjectSessionTypeResponse.builder().build());

            service.toggleSessionTypeStatus(SST_ID, true);

            assertThat(entity.getEstActif()).isTrue();
        }

        @Test
        void deactivate_setsEstActifFalse() {
            SubjectSessionType entity = sessionType(SST_ID, false);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(entity));
            when(subjectSessionTypeRepository.save(entity)).thenReturn(entity);
            when(subjectSessionTypeMapper.toResponse(entity)).thenReturn(SubjectSessionTypeResponse.builder().build());

            service.toggleSessionTypeStatus(SST_ID, false);

            assertThat(entity.getEstActif()).isFalse();
        }
    }

    @Nested
    class DeleteSessionType {

        @Test
        void usedInPatternDetails_throwsConflict() {
            SubjectSessionType entity = sessionType(SST_ID, false);
            entity.setPatternDetails(List.of(PatternDetail.builder().idPatternDetail(1L).build()));
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.deleteSessionType(SST_ID))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("patterns");
        }

        @Test
        void usedInAssignments_throwsConflict() {
            SubjectSessionType entity = sessionType(SST_ID, false);
            entity.setPatternDetails(List.of());
            entity.setTeachingAssignments(List.of(TeachingAssignment.builder().idTeachingAssignment(1L).build()));
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.deleteSessionType(SST_ID))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("affectations");
        }

        @Test
        void happyPath_deletesSessionType() {
            SubjectSessionType entity = sessionType(SST_ID, false);
            entity.setPatternDetails(List.of());
            entity.setTeachingAssignments(List.of());
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(entity));

            service.deleteSessionType(SST_ID);

            verify(subjectSessionTypeRepository).delete(entity);
        }
    }
}
