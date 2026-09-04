package tn.wtm.school.org.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.SchoolYearRequest;
import tn.wtm.school.org.dto.response.SchoolYearResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.mapper.SchoolYearMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.impl.AcademicYearServiceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicYearServiceTest {

    @Mock SchoolYearRepository           schoolYearRepository;
    @Mock ClassGroupRepository           classGroupRepository;
    @Mock TeachingAssignmentRepository   teachingAssignmentRepository;
    @Mock SchoolYearMapper               schoolYearMapper;
    @Mock ObjectsValidator<SchoolYearRequest> validator;

    AcademicYearServiceImpl service;

    static final String TENANT = "tenant-1";
    static final Long   SY_ID  = 1L;

    @BeforeEach
    void setUp() {
        service = new AcademicYearServiceImpl(
                schoolYearRepository, classGroupRepository,
                teachingAssignmentRepository, schoolYearMapper, validator);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    SchoolYearRequest req(String nom) {
        return SchoolYearRequest.builder()
                .nom(nom)
                .dateDebut(LocalDate.of(2024, 9, 1))
                .dateFin(LocalDate.of(2025, 6, 30))
                .build();
    }

    SchoolYear year(Long id, String nom) {
        return SchoolYear.builder().idAnnee(id).nom(nom).estActive(true).build();
    }

    SchoolYearResponse resp(Long id, String nom) {
        return SchoolYearResponse.builder().idAnnee(id).nom(nom).estActive(true).build();
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void noTenant_throwsTenantSecurityException() {
            assertThatThrownBy(() -> service.createAcademicYear(req("2024-2025")))
                    .isInstanceOf(TenantSecurityException.class);
            verify(schoolYearRepository, never()).save(any());
        }

        @Test
        void duplicateName_throwsConflict() {

            TenantContext.setTenantId(TENANT);
            when(schoolYearRepository.existsByTenantIdAndNom(TENANT, "2024-2025")).thenReturn(true);

            assertThatThrownBy(() -> service.createAcademicYear(req("2024-2025")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("2024-2025");
        }

        @Test
        void happyPath_savesAndReturnsResponse() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            when(schoolYearMapper.toEntity(any())).thenReturn(entity);
            when(schoolYearRepository.save(entity)).thenReturn(entity);
            when(schoolYearMapper.toResponse(entity)).thenReturn(resp(SY_ID, "2024-2025"));

            SchoolYearResponse result = service.createAcademicYear(req("2024-2025"));

            assertThat(result.getNom()).isEqualTo("2024-2025");
            verify(schoolYearRepository).save(entity);
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Nested
    class Read {

        @Test
        void getById_nullId_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            assertThatThrownBy(() -> service.getAcademicYearById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            TenantContext.setTenantId(TENANT);
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAcademicYearById(SY_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SY_ID.toString());
        }

        @Test
        void getById_tenantIsolation_queryContainsTenantId() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(schoolYearMapper.toResponse(entity)).thenReturn(resp(SY_ID, "2024-2025"));

            service.getAcademicYearById(SY_ID);

            verify(schoolYearRepository).findByTenantIdAndIdAnnee(TENANT, SY_ID);
        }

        @Test
        void getByTenant_tenantIsolation_usesTenantscopedQuery() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            when(schoolYearRepository.findByTenantId(TENANT)).thenReturn(List.of(entity));
            when(schoolYearMapper.toResponse(entity)).thenReturn(resp(SY_ID, "2024-2025"));

            List<SchoolYearResponse> result = service.getAcademicYearsByTenant();

            assertThat(result).hasSize(1);
            verify(schoolYearRepository).findByTenantId(TENANT);
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Nested
    class Update {

        @Test
        void notFound_throwsResourceNotFound() {
            TenantContext.setTenantId(TENANT);
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAcademicYear(SY_ID, req("2024-2025")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateName_throwsConflict() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "OLD");
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(schoolYearRepository.existsByTenantIdAndNomAndIdAnneeNot(TENANT, "2024-2025", SY_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updateAcademicYear(SY_ID, req("2024-2025")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void happyPath_updatesAndReturnsResponse() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "OLD");
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(schoolYearRepository.save(entity)).thenReturn(entity);
            when(schoolYearMapper.toResponse(entity)).thenReturn(resp(SY_ID, "2024-2025"));

            SchoolYearResponse result = service.updateAcademicYear(SY_ID, req("2024-2025"));

            assertThat(result.getNom()).isEqualTo("2024-2025");
        }
    }

    // ── TOGGLE ────────────────────────────────────────────────────────────────

    @Nested
    class Toggle {

        @Test
        void nullStatus_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            assertThatThrownBy(() -> service.toggleAcademicYearStatus(SY_ID, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("statut");
        }

        @Test
        void deactivate_setsEstActiveFalse() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(schoolYearRepository.save(entity)).thenReturn(entity);
            when(schoolYearMapper.toResponse(entity)).thenReturn(resp(SY_ID, "2024-2025"));

            service.toggleAcademicYearStatus(SY_ID, false);

            assertThat(entity.getEstActive()).isFalse();
        }

        @Test
        void activate_setsEstActiveTrue() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            entity.setEstActive(false);
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(schoolYearRepository.save(entity)).thenReturn(entity);
            when(schoolYearMapper.toResponse(entity)).thenReturn(resp(SY_ID, "2024-2025"));

            service.toggleAcademicYearStatus(SY_ID, true);

            assertThat(entity.getEstActive()).isTrue();
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    class Delete {

        @Test
        void hasClasses_throwsConflict() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(classGroupRepository.findBySchoolYear_IdAnnee(SY_ID))
                    .thenReturn(List.of(ClassGroup.builder().idClasse(1L).build()));

            assertThatThrownBy(() -> service.deleteAcademicYear(SY_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("classes");
            verify(schoolYearRepository, never()).delete(any());
        }

        @Test
        void hasAssignments_throwsConflict() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(classGroupRepository.findBySchoolYear_IdAnnee(SY_ID)).thenReturn(List.of());
            when(teachingAssignmentRepository.findBySchoolYear_IdAnnee(SY_ID))
                    .thenReturn(List.of(TeachingAssignment.builder().idTeachingAssignment(1L).build()));

            assertThatThrownBy(() -> service.deleteAcademicYear(SY_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("affectations");
        }

        @Test
        void happyPath_deletesYear() {
            TenantContext.setTenantId(TENANT);
            SchoolYear entity = year(SY_ID, "2024-2025");
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, SY_ID)).thenReturn(Optional.of(entity));
            when(classGroupRepository.findBySchoolYear_IdAnnee(SY_ID)).thenReturn(List.of());
            when(teachingAssignmentRepository.findBySchoolYear_IdAnnee(SY_ID)).thenReturn(List.of());

            service.deleteAcademicYear(SY_ID);

            verify(schoolYearRepository).delete(entity);
        }
    }
}
