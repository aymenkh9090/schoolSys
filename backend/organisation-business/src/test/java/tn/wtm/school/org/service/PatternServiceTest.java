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
import tn.wtm.school.org.dto.request.PatternDetailRequest;
import tn.wtm.school.org.dto.request.PatternRequest;
import tn.wtm.school.org.dto.response.PatternDetailResponse;
import tn.wtm.school.org.dto.response.PatternResponse;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Pattern;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.mapper.PatternDetailMapper;
import tn.wtm.school.org.mapper.PatternMapper;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.PatternDetailRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.service.impl.PatternServiceImpl;

import java.util.ArrayList;
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
class PatternServiceTest {

    @Mock PatternRepository            patternRepository;
    @Mock PatternDetailRepository      patternDetailRepository;
    @Mock SubjectLevelRepository       subjectLevelRepository;
    @Mock SubjectSessionTypeRepository subjectSessionTypeRepository;
    @Mock LevelRepository              levelRepository;
    @Mock SchoolYearRepository         schoolYearRepository;
    @Mock PatternMapper                patternMapper;
    @Mock PatternDetailMapper          patternDetailMapper;
    @Mock ObjectsValidator<PatternRequest>       patternValidator;
    @Mock ObjectsValidator<PatternDetailRequest> patternDetailValidator;

    PatternServiceImpl service;

    // ── IDs ───────────────────────────────────────────────────────────────────
    static final Long SL_ID    = 1L;
    static final Long SY_ID    = 2L;
    static final Long P_ID     = 10L;
    static final Long PD_ID    = 20L;
    static final Long SST_ID   = 30L;
    static final Long LVL_ID   = 40L;

    // ── fixtures ──────────────────────────────────────────────────────────────
    Level        level;
    Subject      subject;
    SubjectLevel subjectLevel;
    SchoolYear   schoolYear;

    @BeforeEach
    void setUp() {
        tn.wtm.school.common.context.TenantContext.setTenantId("tenant-1");
        service = new PatternServiceImpl(
                patternRepository,
                patternDetailRepository,
                subjectLevelRepository,
                subjectSessionTypeRepository,
                levelRepository,
                schoolYearRepository,
                patternMapper,
                patternDetailMapper,
                patternValidator,
                patternDetailValidator
        );

        level        = Level.builder().idNiveau(LVL_ID).code("L7").nom("7eme").build();
        subject      = Subject.builder().idMatiere(5L).codeMatiere("MATH").libMatiere("Mathematiques").build();
        subjectLevel = SubjectLevel.builder().idNiveauMatiere(SL_ID).level(level).subject(subject).build();
        schoolYear   = SchoolYear.builder().idAnnee(SY_ID).nom("2024-2025").estActive(true).build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        tn.wtm.school.common.context.TenantContext.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    PatternDetailRequest detailReq(int order, double duration) {
        return PatternDetailRequest.builder()
                .sessionOrder(order)
                .duration(duration)
                .type(SessionType.COURSE)
                .build();
    }

    PatternRequest patternReq(String name, double totalHours, int sessionCount, List<PatternDetailRequest> details) {
        return PatternRequest.builder()
                .name(name)
                .totalHours(totalHours)
                .sessionCount(sessionCount)
                .subjectLevelId(SL_ID)
                .details(details)
                .build();
    }

    PatternDetail stubDetail(PatternDetailRequest req, int order, double duration) {
        PatternDetail d = PatternDetail.builder().sessionOrder(order).duration(duration).type(SessionType.COURSE).build();
        lenient().when(patternDetailMapper.toEntity(req)).thenReturn(d);
        return d;
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Nested
    class CreatePattern {

        @Test
        void happyPath_noSchoolYear_savesAndReturnsResponse() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req      = patternReq("P-MATH", 2.0, 1, List.of(dr));

            Pattern entity   = Pattern.builder().patternDetails(new ArrayList<>()).build();
            PatternDetail pd = stubDetail(dr, 1, 2.0);
            PatternResponse response = PatternResponse.builder().idPattern(P_ID).name("P-MATH").build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("P-MATH", SL_ID, null, null)).thenReturn(false);
            when(patternMapper.toEntity(req)).thenReturn(entity);
            when(patternRepository.save(entity)).thenReturn(entity);
            when(patternMapper.toResponse(entity)).thenReturn(response);

            PatternResponse result = service.createPattern(req);

            assertThat(result.getIdPattern()).isEqualTo(P_ID);
            assertThat(entity.getSubjectLevel()).isEqualTo(subjectLevel);
            assertThat(entity.getSchoolYear()).isNull();
            assertThat(entity.getPatternDetails()).containsExactly(pd);
            verify(patternRepository).save(entity);
        }

        @Test
        void happyPath_withSchoolYear_linksSchoolYear() {
            PatternDetailRequest dr = detailReq(1, 3.0);
            PatternRequest req = PatternRequest.builder()
                    .name("P-SY").totalHours(3.0).sessionCount(1)
                    .subjectLevelId(SL_ID).schoolYearId(SY_ID).details(List.of(dr)).build();

            Pattern entity = Pattern.builder().patternDetails(new ArrayList<>()).build();
            stubDetail(dr, 1, 3.0);
            PatternResponse response = PatternResponse.builder().idPattern(P_ID).schoolYearId(SY_ID).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", SY_ID)).thenReturn(Optional.of(schoolYear));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("P-SY", SL_ID, SY_ID, null)).thenReturn(false);
            when(patternMapper.toEntity(req)).thenReturn(entity);
            when(patternRepository.save(entity)).thenReturn(entity);
            when(patternMapper.toResponse(entity)).thenReturn(response);

            service.createPattern(req);

            assertThat(entity.getSchoolYear()).isEqualTo(schoolYear);
        }

        @Test
        void nullSubjectLevelId_throwsBadRequest() {
            PatternRequest req = PatternRequest.builder()
                    .name("P").totalHours(2.0).sessionCount(1)
                    .subjectLevelId(null).details(List.of(detailReq(1, 2.0))).build();

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class);
            verify(patternRepository, never()).save(any());
        }

        @Test
        void subjectLevelNotFound_throwsResourceNotFound() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req      = patternReq("P", 2.0, 1, List.of(dr));

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SL_ID.toString());
        }

        @Test
        void schoolYearNotFound_throwsResourceNotFound() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req = PatternRequest.builder()
                    .name("P").totalHours(2.0).sessionCount(1)
                    .subjectLevelId(SL_ID).schoolYearId(SY_ID).details(List.of(dr)).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", SY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SY_ID.toString());
        }

        @Test
        void duplicateName_throwsConflict() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req      = patternReq("P-DUP", 2.0, 1, List.of(dr));

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("P-DUP", SL_ID, null, null)).thenReturn(true);

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("P-DUP");
        }

        @Test
        void emptyDetails_throwsBadRequest() {
            PatternRequest req = patternReq("P", 2.0, 0, List.of());

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("detail");
        }

        @Test
        void nullDetails_throwsBadRequest() {
            PatternRequest req = PatternRequest.builder()
                    .name("P").totalHours(2.0).sessionCount(1)
                    .subjectLevelId(SL_ID).details(null).build();

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void totalHoursMismatch_throwsBadRequest() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req      = patternReq("P", 5.0, 1, List.of(dr)); // sum=2 ≠ total=5

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("totalHours");
        }

        @Test
        void sessionCountMismatch_throwsBadRequest() {
            PatternDetailRequest dr1 = detailReq(1, 1.0);
            PatternDetailRequest dr2 = detailReq(2, 1.0);
            PatternRequest req       = patternReq("P", 2.0, 1, List.of(dr1, dr2)); // count=1 ≠ actual 2

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("sessionCount");
        }

        @Test
        void duplicateSessionOrders_throwsBadRequest() {
            PatternDetailRequest dr1 = detailReq(1, 1.0);
            PatternDetailRequest dr2 = detailReq(1, 1.0); // same order
            PatternRequest req       = patternReq("P", 2.0, 2, List.of(dr1, dr2));

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("uniques");
        }

        @Test
        void nonConsecutiveSessionOrders_throwsBadRequest() {
            PatternDetailRequest dr1 = detailReq(1, 1.0);
            PatternDetailRequest dr2 = detailReq(3, 1.0); // gap: missing 2
            PatternRequest req       = patternReq("P", 2.0, 2, List.of(dr1, dr2));

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("consecutifs");
        }

        @Test
        void invalidRoomType_throwsBadRequest() {
            PatternDetailRequest dr = PatternDetailRequest.builder()
                    .sessionOrder(1).duration(2.0).type(SessionType.COURSE)
                    .requiredRoomType("INVALID_TYPE").build();
            PatternRequest req = patternReq("P", 2.0, 1, List.of(dr));

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("INVALID_TYPE");
        }

        @Test
        void subjectSessionTypeNotFound_throwsResourceNotFound() {
            PatternDetailRequest dr = PatternDetailRequest.builder()
                    .sessionOrder(1).duration(2.0).type(SessionType.COURSE)
                    .subjectSessionTypeId(SST_ID).build();
            PatternRequest req = patternReq("P", 2.0, 1, List.of(dr));

            Pattern entity = Pattern.builder().patternDetails(new ArrayList<>()).build();
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).type(SessionType.COURSE).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("P", SL_ID, null, null)).thenReturn(false);
            when(patternMapper.toEntity(req)).thenReturn(entity);
            when(patternDetailMapper.toEntity(dr)).thenReturn(pd);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SST_ID.toString());
        }

        @Test
        void sessionTypeWrongSubjectLevel_throwsBadRequest() {
            PatternDetailRequest dr = PatternDetailRequest.builder()
                    .sessionOrder(1).duration(2.0).type(SessionType.COURSE)
                    .subjectSessionTypeId(SST_ID).build();
            PatternRequest req = patternReq("P", 2.0, 1, List.of(dr));

            SubjectLevel otherSl = SubjectLevel.builder().idNiveauMatiere(99L).build();
            SubjectSessionType sst = SubjectSessionType.builder()
                    .idSubjectSessionType(SST_ID).subjectLevel(otherSl).estActif(true).build();
            Pattern entity = Pattern.builder().patternDetails(new ArrayList<>()).build();
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).type(SessionType.COURSE).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("P", SL_ID, null, null)).thenReturn(false);
            when(patternMapper.toEntity(req)).thenReturn(entity);
            when(patternDetailMapper.toEntity(dr)).thenReturn(pd);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(sst));

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("matiere-niveau");
        }

        @Test
        void inactiveSessionType_throwsBadRequest() {
            PatternDetailRequest dr = PatternDetailRequest.builder()
                    .sessionOrder(1).duration(2.0).type(SessionType.COURSE)
                    .subjectSessionTypeId(SST_ID).build();
            PatternRequest req = patternReq("P", 2.0, 1, List.of(dr));

            SubjectSessionType sst = SubjectSessionType.builder()
                    .idSubjectSessionType(SST_ID).subjectLevel(subjectLevel).estActif(false).build();
            Pattern entity = Pattern.builder().patternDetails(new ArrayList<>()).build();
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).type(SessionType.COURSE).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("P", SL_ID, null, null)).thenReturn(false);
            when(patternMapper.toEntity(req)).thenReturn(entity);
            when(patternDetailMapper.toEntity(dr)).thenReturn(pd);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(sst));

            assertThatThrownBy(() -> service.createPattern(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("inactif");
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Nested
    class ReadPattern {

        @Test
        void getById_found_returnsLightResponse() {
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .totalHours(2.0).sessionCount(1)
                    .subjectLevel(subjectLevel).build();
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.of(p));

            PatternResponse result = service.getPatternById(P_ID);

            assertThat(result.getIdPattern()).isEqualTo(P_ID);
            assertThat(result.getDetails()).isNull();
        }

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getPatternById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPatternById(P_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(P_ID.toString());
        }

        @Test
        void getWithDetails_found_returnsWithSortedDetails() {
            PatternDetail d2 = PatternDetail.builder().idPatternDetail(2L).sessionOrder(2).duration(1.0).build();
            PatternDetail d1 = PatternDetail.builder().idPatternDetail(1L).sessionOrder(1).duration(2.0).build();
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .totalHours(3.0).sessionCount(2)
                    .subjectLevel(subjectLevel)
                    .patternDetails(new ArrayList<>(List.of(d2, d1))) // intentionally unsorted
                    .build();
            PatternResponse response = PatternResponse.builder().idPattern(P_ID)
                    .details(List.of(
                            PatternDetailResponse.builder().idPatternDetail(1L).sessionOrder(1).build(),
                            PatternDetailResponse.builder().idPatternDetail(2L).sessionOrder(2).build()))
                    .build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(p));
            when(patternMapper.toResponse(p)).thenReturn(response);

            PatternResponse result = service.getPatternWithDetails(P_ID);

            assertThat(result.getDetails()).hasSize(2);
            // sortPatternDetails was applied before toResponse
            assertThat(p.getPatternDetails().get(0).getSessionOrder()).isEqualTo(1);
            assertThat(p.getPatternDetails().get(1).getSessionOrder()).isEqualTo(2);
        }

        @Test
        void getWithDetails_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getPatternWithDetails(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getWithDetails_notFound_throwsResourceNotFound() {
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPatternWithDetails(P_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getBySubjectLevel_tenantIsolation_usesSubjectLevelScopedQuery() {
            Pattern p = Pattern.builder().idPattern(P_ID).subjectLevel(subjectLevel).build();
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.findBySubjectLevel_IdNiveauMatiere(SL_ID)).thenReturn(List.of(p));

            List<PatternResponse> results = service.getPatternsBySubjectLevel(SL_ID);

            assertThat(results).hasSize(1);
            verify(patternRepository).findBySubjectLevel_IdNiveauMatiere(SL_ID);
        }

        @Test
        void getBySubjectLevel_subjectLevelNotFound_throwsResourceNotFound() {
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPatternsBySubjectLevel(SL_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(patternRepository, never()).findBySubjectLevel_IdNiveauMatiere(any());
        }

        @Test
        void getBySubjectLevelWithDetails_tenantIsolation_usesSubjectLevelScopedQuery() {
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .subjectLevel(subjectLevel)
                    .patternDetails(new ArrayList<>(List.of(pd))).build();
            PatternResponse response = PatternResponse.builder().idPattern(P_ID).build();

            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.findBySubjectLevelWithDetails(SL_ID)).thenReturn(List.of(p));
            when(patternMapper.toResponse(p)).thenReturn(response);

            List<PatternResponse> results = service.getPatternsBySubjectLevelWithDetails(SL_ID);

            assertThat(results).hasSize(1);
            verify(patternRepository).findBySubjectLevelWithDetails(SL_ID);
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Nested
    class UpdatePattern {

        @Test
        void happyPath_replacesDetailsAndReturnsResponse() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req = patternReq("Updated", 2.0, 1, List.of(dr));

            PatternDetail oldDetail = PatternDetail.builder().idPatternDetail(PD_ID).sessionOrder(1).duration(1.0).build();
            Pattern existing = Pattern.builder().idPattern(P_ID)
                    .patternDetails(new ArrayList<>(List.of(oldDetail))).build();
            PatternDetail newDetail = stubDetail(dr, 1, 2.0);
            PatternResponse response = PatternResponse.builder().idPattern(P_ID).name("Updated").build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(existing));
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("Updated", SL_ID, null, P_ID)).thenReturn(false);
            when(patternDetailRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(patternRepository.save(existing)).thenReturn(existing);
            when(patternMapper.toResponse(existing)).thenReturn(response);

            PatternResponse result = service.updatePattern(P_ID, req);

            assertThat(result.getName()).isEqualTo("Updated");
            verify(patternDetailRepository).deleteByPattern_IdPattern(P_ID);
            verify(patternDetailRepository).saveAll(any());
            verify(patternRepository).save(existing);
        }

        @Test
        void notFound_throwsResourceNotFound() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req      = patternReq("P", 2.0, 1, List.of(dr));

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePattern(P_ID, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateName_throwsConflict() {
            PatternDetailRequest dr = detailReq(1, 2.0);
            PatternRequest req      = patternReq("P-DUP", 2.0, 1, List.of(dr));
            Pattern existing = Pattern.builder().idPattern(P_ID).patternDetails(new ArrayList<>()).build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(existing));
            when(subjectLevelRepository.findById(SL_ID)).thenReturn(Optional.of(subjectLevel));
            when(patternRepository.existsByNameAndSubjectLevelAndYear("P-DUP", SL_ID, null, P_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updatePattern(P_ID, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("P-DUP");
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    class DeletePattern {

        @Test
        void happyPath_deletesPatternAndDetails() {
            Pattern p = Pattern.builder().idPattern(P_ID).build();
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.of(p));

            service.deletePattern(P_ID);

            verify(patternDetailRepository).deleteByPattern_IdPattern(P_ID);
            verify(patternRepository).delete(p);
        }

        @Test
        void notFound_throwsResourceNotFound() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deletePattern(P_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(patternRepository, never()).delete(any());
        }
    }

    // ── BUSINESS ─────────────────────────────────────────────────────────────

    @Nested
    class PatternBusiness {

        @Test
        void getPatternsForGeneration_happyPath_delegatesToRepository() {
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .patternDetails(new ArrayList<>(List.of(pd))).build();
            PatternResponse response = PatternResponse.builder().idPattern(P_ID).build();

            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(level));
            when(patternRepository.findAllByLevelForGeneration(LVL_ID, SY_ID)).thenReturn(List.of(p));
            when(patternMapper.toResponse(p)).thenReturn(response);

            List<PatternResponse> results = service.getPatternsForGeneration(LVL_ID, SY_ID);

            assertThat(results).hasSize(1);
            verify(patternRepository).findAllByLevelForGeneration(LVL_ID, SY_ID);
        }

        @Test
        void getPatternsForGeneration_nullSchoolYear_throwsBadRequest() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.of(level));

            assertThatThrownBy(() -> service.getPatternsForGeneration(LVL_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("scolaire");
        }

        @Test
        void getPatternsForGeneration_levelNotFound_throwsResourceNotFound() {
            when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", LVL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPatternsForGeneration(LVL_ID, SY_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getPatternsForGeneration_nullLevelId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getPatternsForGeneration(null, SY_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void validatePatternConsistency_consistentPattern_noException() {
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .totalHours(2.0).sessionCount(1)
                    .patternDetails(new ArrayList<>(List.of(pd))).build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(p));

            service.validatePatternConsistency(P_ID); // must not throw
        }

        @Test
        void validatePatternConsistency_totalHoursMismatch_throwsBadRequest() {
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .totalHours(5.0)             // mismatch: sum = 2.0
                    .sessionCount(1)
                    .patternDetails(new ArrayList<>(List.of(pd))).build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.validatePatternConsistency(P_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("incoherent");
        }

        @Test
        void validatePatternConsistency_sessionCountMismatch_throwsBadRequest() {
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .totalHours(2.0)
                    .sessionCount(3)             // mismatch: actual = 1 detail
                    .patternDetails(new ArrayList<>(List.of(pd))).build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.validatePatternConsistency(P_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void validatePatternConsistency_emptyDetails_throwsBadRequest() {
            Pattern p = Pattern.builder().idPattern(P_ID)
                    .totalHours(2.0).sessionCount(0)
                    .patternDetails(new ArrayList<>()).build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.validatePatternConsistency(P_ID))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void findInconsistentPatterns_returnsOnlyInconsistentOnes() {
            PatternDetail consistent = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern good = Pattern.builder().idPattern(1L)
                    .totalHours(2.0).sessionCount(1)
                    .patternDetails(new ArrayList<>(List.of(consistent))).build();

            PatternDetail inconsistentDetail = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern bad = Pattern.builder().idPattern(2L)
                    .totalHours(5.0)             // deliberate mismatch
                    .sessionCount(1)
                    .patternDetails(new ArrayList<>(List.of(inconsistentDetail))).build();

            PatternResponse badResponse = PatternResponse.builder().idPattern(2L).build();

            when(patternRepository.findByTenantId("tenant-1")).thenReturn(List.of(good, bad));
            when(patternMapper.toResponse(bad)).thenReturn(badResponse);

            List<PatternResponse> results = service.findInconsistentPatterns();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getIdPattern()).isEqualTo(2L);
        }

        @Test
        void findInconsistentPatterns_allConsistent_returnsEmptyList() {
            PatternDetail pd = PatternDetail.builder().sessionOrder(1).duration(2.0).build();
            Pattern good = Pattern.builder().idPattern(P_ID)
                    .totalHours(2.0).sessionCount(1)
                    .patternDetails(new ArrayList<>(List.of(pd))).build();

            when(patternRepository.findByTenantId("tenant-1")).thenReturn(List.of(good));

            assertThat(service.findInconsistentPatterns()).isEmpty();
        }
    }
}
