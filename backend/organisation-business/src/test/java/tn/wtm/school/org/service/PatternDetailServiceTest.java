package tn.wtm.school.org.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.PatternDetailRequest;
import tn.wtm.school.org.dto.request.PatternRequest;
import tn.wtm.school.org.dto.response.PatternDetailResponse;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Pattern;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.enums.RoomType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatternDetailServiceTest {

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
    static final Long P_ID   = 10L;
    static final Long PD1_ID = 21L;
    static final Long PD2_ID = 22L;
    static final Long SL_ID  = 1L;
    static final Long SST_ID = 30L;

    // ── fixtures ──────────────────────────────────────────────────────────────
    SubjectLevel subjectLevel;

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

        Level   level   = Level.builder().idNiveau(40L).build();
        Subject subject = Subject.builder().idMatiere(5L).codeMatiere("MATH").build();
        subjectLevel = SubjectLevel.builder().idNiveauMatiere(SL_ID).level(level).subject(subject).build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        tn.wtm.school.common.context.TenantContext.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    PatternDetail detail(Long id, int order, double duration) {
        return PatternDetail.builder()
                .idPatternDetail(id).sessionOrder(order).duration(duration)
                .type(SessionType.COURSE).build();
    }

    Pattern patternWithDetails(PatternDetail... details) {
        Pattern p = Pattern.builder()
                .idPattern(P_ID).subjectLevel(subjectLevel)
                .totalHours(0.0).sessionCount(0)
                .patternDetails(new ArrayList<>(List.of(details)))
                .build();
        return p;
    }

    PatternDetailRequest detailReq(int order, double duration) {
        return PatternDetailRequest.builder()
                .sessionOrder(order).duration(duration)
                .type(SessionType.COURSE).build();
    }

    // ── ADD DETAIL ────────────────────────────────────────────────────────────

    @Nested
    class AddDetail {

        @Test
        void happyPath_addsDetailAndReturnsResponse() {
            PatternDetail existing = detail(PD1_ID, 1, 2.0);
            Pattern pattern        = patternWithDetails(existing);
            PatternDetailRequest req = detailReq(2, 1.0);

            PatternDetail newDetail = detail(null, 2, 1.0);
            PatternDetailResponse response = PatternDetailResponse.builder()
                    .idPatternDetail(PD2_ID).sessionOrder(2).build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));
            when(patternDetailMapper.toEntity(req)).thenReturn(newDetail);
            when(patternDetailRepository.save(newDetail)).thenReturn(newDetail);
            when(patternDetailMapper.toResponse(newDetail)).thenReturn(response);

            PatternDetailResponse result = service.addDetail(P_ID, req);

            assertThat(result.getSessionOrder()).isEqualTo(2);
            assertThat(newDetail.getPattern()).isEqualTo(pattern);
            assertThat(pattern.getPatternDetails()).contains(newDetail);
            verify(patternDetailRepository).save(newDetail);
            verify(patternRepository).save(pattern);
        }

        @Test
        void patternNotFound_throwsResourceNotFound() {
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addDetail(P_ID, detailReq(1, 2.0)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(P_ID.toString());
            verify(patternDetailRepository, never()).save(any());
        }

        @Test
        void invalidRoomType_throwsBadRequest() {
            PatternDetailRequest req = PatternDetailRequest.builder()
                    .sessionOrder(1).duration(2.0).type(SessionType.COURSE)
                    .requiredRoomType("INVALID").build();

            assertThatThrownBy(() -> service.addDetail(P_ID, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("INVALID");
            verify(patternRepository, never()).findByIdWithDetails(any());
        }

        @Test
        void duplicateSessionOrder_throwsBadRequest() {
            // pattern already has order=1; new detail also claims order=1
            PatternDetail existing = detail(PD1_ID, 1, 2.0);
            Pattern pattern        = patternWithDetails(existing);
            PatternDetailRequest req = detailReq(1, 1.0); // duplicate order

            PatternDetail newDetail = detail(null, 1, 1.0);

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));
            when(patternDetailMapper.toEntity(req)).thenReturn(newDetail);

            assertThatThrownBy(() -> service.addDetail(P_ID, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("uniques");
            verify(patternDetailRepository, never()).save(any());
        }

        @Test
        void sessionTypeNotFound_throwsResourceNotFound() {
            PatternDetailRequest req = PatternDetailRequest.builder()
                    .sessionOrder(1).duration(2.0).type(SessionType.COURSE)
                    .subjectSessionTypeId(SST_ID).build();
            PatternDetail newDetail = detail(null, 1, 2.0);
            Pattern pattern = patternWithDetails(); // empty list; we'll add order=1 → valid

            // Prepare a pattern that will pass validateEntityDetails after adding detail order=1
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));
            when(patternDetailMapper.toEntity(req)).thenReturn(newDetail);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addDetail(P_ID, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SST_ID.toString());
        }

        @Test
        void sessionTypeWrongSubjectLevel_throwsBadRequest() {
            SubjectLevel otherSl = SubjectLevel.builder().idNiveauMatiere(99L).build();
            SubjectSessionType sst = SubjectSessionType.builder()
                    .idSubjectSessionType(SST_ID).subjectLevel(otherSl).estActif(true).build();

            PatternDetailRequest req = PatternDetailRequest.builder()
                    .sessionOrder(2).duration(1.0).type(SessionType.COURSE)
                    .subjectSessionTypeId(SST_ID).build();
            PatternDetail existing  = detail(PD1_ID, 1, 2.0);
            PatternDetail newDetail = detail(null, 2, 1.0);
            Pattern pattern         = patternWithDetails(existing);

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));
            when(patternDetailMapper.toEntity(req)).thenReturn(newDetail);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(sst));

            assertThatThrownBy(() -> service.addDetail(P_ID, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("matiere-niveau");
        }

        @Test
        void sessionTypeInactive_throwsBadRequest() {
            SubjectSessionType sst = SubjectSessionType.builder()
                    .idSubjectSessionType(SST_ID).subjectLevel(subjectLevel).estActif(false).build();

            PatternDetailRequest req = PatternDetailRequest.builder()
                    .sessionOrder(2).duration(1.0).type(SessionType.COURSE)
                    .subjectSessionTypeId(SST_ID).build();
            PatternDetail existing  = detail(PD1_ID, 1, 2.0);
            PatternDetail newDetail = detail(null, 2, 1.0);
            Pattern pattern         = patternWithDetails(existing);

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));
            when(patternDetailMapper.toEntity(req)).thenReturn(newDetail);
            when(subjectSessionTypeRepository.findById(SST_ID)).thenReturn(Optional.of(sst));

            assertThatThrownBy(() -> service.addDetail(P_ID, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("inactif");
        }
    }

    // ── READ DETAIL ───────────────────────────────────────────────────────────

    @Nested
    class ReadDetail {

        @Test
        void getById_found_returnsResponse() {
            PatternDetail pd = detail(PD1_ID, 1, 2.0);
            PatternDetailResponse response = PatternDetailResponse.builder().idPatternDetail(PD1_ID).build();

            when(patternDetailRepository.findById(PD1_ID)).thenReturn(Optional.of(pd));
            when(patternDetailMapper.toResponse(pd)).thenReturn(response);

            assertThat(service.getDetailById(PD1_ID).getIdPatternDetail()).isEqualTo(PD1_ID);
        }

        @Test
        void getById_nullId_throwsBadRequest() {
            assertThatThrownBy(() -> service.getDetailById(null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("obligatoire");
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            when(patternDetailRepository.findById(PD1_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDetailById(PD1_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(PD1_ID.toString());
        }

        @Test
        void getByPattern_tenantIsolation_usesPatternScopedQuery() {
            PatternDetail pd = detail(PD1_ID, 1, 2.0);
            PatternDetailResponse response = PatternDetailResponse.builder().idPatternDetail(PD1_ID).build();

            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID))
                    .thenReturn(Optional.of(Pattern.builder().idPattern(P_ID).build()));
            when(patternDetailRepository.findByPattern_IdPatternOrderBySessionOrderAsc(P_ID))
                    .thenReturn(List.of(pd));
            when(patternDetailMapper.toResponseList(List.of(pd))).thenReturn(List.of(response));

            List<PatternDetailResponse> result = service.getDetailsByPattern(P_ID);

            assertThat(result).hasSize(1);
            verify(patternDetailRepository).findByPattern_IdPatternOrderBySessionOrderAsc(P_ID);
        }

        @Test
        void getByPattern_patternNotFound_throwsResourceNotFound() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDetailsByPattern(P_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(patternDetailRepository, never()).findByPattern_IdPatternOrderBySessionOrderAsc(any());
        }
    }

    // ── UPDATE DETAIL ─────────────────────────────────────────────────────────

    @Nested
    class UpdateDetail {

        @Test
        void happyPath_updatesAndReturnsResponse() {
            PatternDetail existing = detail(PD1_ID, 1, 2.0);
            existing.setPattern(Pattern.builder().idPattern(P_ID).build());
            Pattern pattern = patternWithDetails(existing);

            PatternDetailRequest req = detailReq(1, 3.0);
            PatternDetailResponse response = PatternDetailResponse.builder()
                    .idPatternDetail(PD1_ID).duration(3.0).build();

            when(patternDetailRepository.findById(PD1_ID)).thenReturn(Optional.of(existing));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));
            when(patternDetailRepository.save(existing)).thenReturn(existing);
            when(patternDetailMapper.toResponse(existing)).thenReturn(response);

            PatternDetailResponse result = service.updateDetail(PD1_ID, req);

            assertThat(result.getDuration()).isEqualTo(3.0);
            verify(patternRepository).save(pattern);
            verify(patternDetailRepository).save(existing);
        }

        @Test
        void detailNotFound_throwsResourceNotFound() {
            when(patternDetailRepository.findById(PD1_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateDetail(PD1_ID, detailReq(1, 2.0)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(PD1_ID.toString());
        }

        @Test
        void invalidRoomType_throwsBadRequest() {
            PatternDetailRequest req = PatternDetailRequest.builder()
                    .sessionOrder(1).duration(2.0).type(SessionType.COURSE)
                    .requiredRoomType("BAD_TYPE").build();

            assertThatThrownBy(() -> service.updateDetail(PD1_ID, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("BAD_TYPE");
            verify(patternDetailRepository, never()).findById(any());
        }
    }

    // ── DELETE DETAIL ─────────────────────────────────────────────────────────

    @Nested
    class DeleteDetail {

        @Test
        void happyPath_deletesAndReordersRemaining() {
            PatternDetail d1 = detail(PD1_ID, 1, 2.0);
            PatternDetail d2 = detail(PD2_ID, 2, 1.0);
            d1.setPattern(Pattern.builder().idPattern(P_ID).build());
            Pattern pattern = patternWithDetails(d1, d2);

            when(patternDetailRepository.findById(PD1_ID)).thenReturn(Optional.of(d1));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            service.deleteDetail(PD1_ID);

            verify(patternDetailRepository).delete(d1);
            verify(patternRepository).save(pattern);
            // d2 was at order=2, d1 (order=1) deleted → d2 reordered to 1
            assertThat(d2.getSessionOrder()).isEqualTo(1);
            assertThat(pattern.getPatternDetails()).doesNotContain(d1);
        }

        @Test
        void lastDetail_throwsBadRequest() {
            PatternDetail only = detail(PD1_ID, 1, 2.0);
            only.setPattern(Pattern.builder().idPattern(P_ID).build());
            Pattern pattern = patternWithDetails(only);

            when(patternDetailRepository.findById(PD1_ID)).thenReturn(Optional.of(only));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            assertThatThrownBy(() -> service.deleteDetail(PD1_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("dernier");
            verify(patternDetailRepository, never()).delete(any());
        }

        @Test
        void detailNotFound_throwsResourceNotFound() {
            when(patternDetailRepository.findById(PD1_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteDetail(PD1_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(patternRepository, never()).save(any());
        }

        @Test
        void deleteMiddleDetail_reordersOnlyHigherOrders() {
            PatternDetail d1 = detail(PD1_ID, 1, 1.0);
            PatternDetail d2 = detail(PD2_ID, 2, 1.0);
            PatternDetail d3 = detail(33L,    3, 1.0);
            d2.setPattern(Pattern.builder().idPattern(P_ID).build());
            Pattern pattern = patternWithDetails(d1, d2, d3);

            when(patternDetailRepository.findById(PD2_ID)).thenReturn(Optional.of(d2));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            service.deleteDetail(PD2_ID);

            // d1 (order=1) unchanged, d3 (order=3 > 2) decremented to 2
            assertThat(d1.getSessionOrder()).isEqualTo(1);
            assertThat(d3.getSessionOrder()).isEqualTo(2);
            verify(patternDetailRepository).delete(d2);
        }
    }

    // ── BUSINESS QUERIES ──────────────────────────────────────────────────────

    @Nested
    class DetailBusinessQueries {

        @Test
        void getDetailsByType_happyPath_returnsFilteredAndSorted() {
            PatternDetail td  = detail(PD1_ID, 1, 2.0);
            PatternDetail tp  = PatternDetail.builder()
                    .idPatternDetail(PD2_ID).sessionOrder(2).duration(1.0)
                    .type(SessionType.TP).build();
            PatternDetailResponse response = PatternDetailResponse.builder().idPatternDetail(PD1_ID).build();

            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID))
                    .thenReturn(Optional.of(Pattern.builder().idPattern(P_ID).build()));
            when(patternDetailRepository.findByPattern_IdPatternAndType(P_ID, SessionType.COURSE))
                    .thenReturn(List.of(td));
            when(patternDetailMapper.toResponseList(any())).thenReturn(List.of(response));

            List<PatternDetailResponse> result = service.getDetailsByType(P_ID, SessionType.COURSE);

            assertThat(result).hasSize(1);
            verify(patternDetailRepository).findByPattern_IdPatternAndType(P_ID, SessionType.COURSE);
        }

        @Test
        void getDetailsByType_nullType_throwsBadRequest() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID))
                    .thenReturn(Optional.of(Pattern.builder().idPattern(P_ID).build()));

            assertThatThrownBy(() -> service.getDetailsByType(P_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("type de seance");
        }

        @Test
        void getDetailsByType_patternNotFound_throwsResourceNotFound() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDetailsByType(P_ID, SessionType.COURSE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getDetailsByRoomType_happyPath_filtersInMemory() {
            PatternDetail lab    = PatternDetail.builder()
                    .idPatternDetail(PD1_ID).sessionOrder(1).duration(2.0)
                    .type(SessionType.TP).requiredRoomType(RoomType.LABSCIENCE).build();
            PatternDetail normal = PatternDetail.builder()
                    .idPatternDetail(PD2_ID).sessionOrder(2).duration(1.0)
                    .type(SessionType.COURSE).requiredRoomType(RoomType.NORMALE).build();
            PatternDetailResponse response = PatternDetailResponse.builder().idPatternDetail(PD1_ID).build();

            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID))
                    .thenReturn(Optional.of(Pattern.builder().idPattern(P_ID).build()));
            when(patternDetailRepository.findByPattern_IdPatternOrderBySessionOrderAsc(P_ID))
                    .thenReturn(List.of(lab, normal));
            when(patternDetailMapper.toResponseList(List.of(lab))).thenReturn(List.of(response));

            List<PatternDetailResponse> result = service.getDetailsByRoomType(P_ID, RoomType.LABSCIENCE);

            assertThat(result).hasSize(1);
        }

        @Test
        void getDetailsByRoomType_nullRoomType_throwsBadRequest() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID))
                    .thenReturn(Optional.of(Pattern.builder().idPattern(P_ID).build()));

            assertThatThrownBy(() -> service.getDetailsByRoomType(P_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("salle");
        }

        @Test
        void getDetailsByRoomType_patternNotFound_throwsResourceNotFound() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDetailsByRoomType(P_ID, RoomType.LABSCIENCE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getSplitDetails_happyPath_returnsSplitOnesOnly() {
            PatternDetail split  = PatternDetail.builder()
                    .idPatternDetail(PD1_ID).sessionOrder(1).duration(1.0).isSplit(true).build();
            PatternDetailResponse response = PatternDetailResponse.builder().idPatternDetail(PD1_ID).build();

            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID))
                    .thenReturn(Optional.of(Pattern.builder().idPattern(P_ID).build()));
            when(patternDetailRepository.findByPattern_IdPatternAndIsSplitTrue(P_ID))
                    .thenReturn(List.of(split));
            when(patternDetailMapper.toResponseList(any())).thenReturn(List.of(response));

            List<PatternDetailResponse> result = service.getSplitDetails(P_ID);

            assertThat(result).hasSize(1);
            verify(patternDetailRepository).findByPattern_IdPatternAndIsSplitTrue(P_ID);
        }

        @Test
        void getSplitDetails_patternNotFound_throwsResourceNotFound() {
            when(patternRepository.findByTenantIdAndIdPattern("tenant-1", P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSplitDetails(P_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── REORDER ───────────────────────────────────────────────────────────────

    @Nested
    class ReorderDetails {

        @Test
        void happyPath_reassignsSessionOrdersInRequestedSequence() {
            PatternDetail d1 = detail(PD1_ID, 1, 2.0);
            PatternDetail d2 = detail(PD2_ID, 2, 1.0);
            Pattern pattern  = patternWithDetails(d1, d2);

            PatternDetailResponse r1 = PatternDetailResponse.builder().idPatternDetail(PD1_ID).sessionOrder(2).build();
            PatternDetailResponse r2 = PatternDetailResponse.builder().idPatternDetail(PD2_ID).sessionOrder(1).build();

            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));
            when(patternDetailMapper.toResponseList(any())).thenReturn(List.of(r2, r1));

            // Reverse the order: PD2 first, PD1 second
            List<PatternDetailResponse> result = service.reorderDetails(P_ID, List.of(PD2_ID, PD1_ID));

            assertThat(d2.getSessionOrder()).isEqualTo(1);
            assertThat(d1.getSessionOrder()).isEqualTo(2);
            verify(patternRepository).save(pattern);
        }

        @Test
        void emptyOrderedList_throwsBadRequest() {
            Pattern pattern = patternWithDetails(detail(PD1_ID, 1, 2.0));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            assertThatThrownBy(() -> service.reorderDetails(P_ID, List.of()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("obligatoire");
        }

        @Test
        void nullOrderedList_throwsBadRequest() {
            Pattern pattern = patternWithDetails(detail(PD1_ID, 1, 2.0));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            assertThatThrownBy(() -> service.reorderDetails(P_ID, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void wrongListSize_throwsBadRequest() {
            Pattern pattern = patternWithDetails(detail(PD1_ID, 1, 2.0), detail(PD2_ID, 2, 1.0));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            assertThatThrownBy(() -> service.reorderDetails(P_ID, List.of(PD1_ID)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("tous les details");
        }

        @Test
        void duplicateIdsInList_throwsBadRequest() {
            Pattern pattern = patternWithDetails(detail(PD1_ID, 1, 2.0), detail(PD2_ID, 2, 1.0));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            assertThatThrownBy(() -> service.reorderDetails(P_ID, List.of(PD1_ID, PD1_ID)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("dupliques");
        }

        @Test
        void unknownIdInList_throwsBadRequest() {
            Pattern pattern = patternWithDetails(detail(PD1_ID, 1, 2.0), detail(PD2_ID, 2, 1.0));
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.of(pattern));

            assertThatThrownBy(() -> service.reorderDetails(P_ID, List.of(PD1_ID, 999L)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("uniquement les details");
        }

        @Test
        void patternNotFound_throwsResourceNotFound() {
            when(patternRepository.findByIdWithDetails(P_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reorderDetails(P_ID, List.of(PD1_ID)))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(patternRepository, never()).save(any());
        }
    }
}
