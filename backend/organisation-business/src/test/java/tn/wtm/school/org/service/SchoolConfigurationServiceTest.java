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
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.org.dto.request.SchoolConfigRequestDTO;
import tn.wtm.school.org.dto.request.WorkingDayRequestDTO;
import tn.wtm.school.org.dto.response.SchoolConfigResponseDTO;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.mapper.TimeSlotMapper;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.org.repository.WorkingDayRepository;
import tn.wtm.school.org.service.impl.SchoolConfigurationService;
import tn.wtm.school.org.service.impl.TimeSlotGenerationServiceImpl;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolConfigurationServiceTest {

    @Mock WorkingDayRepository           workingDayRepo;
    @Mock TimeSlotRepository             timeSlotRepo;
    @Mock TimeSlotGenerationServiceImpl  slotGenerator;
    @Mock TimeSlotMapper                 timeSlotMapper;

    SchoolConfigurationService service;

    static final String TENANT = "tenant-1";

    @BeforeEach
    void setUp() {
        service = new SchoolConfigurationService(workingDayRepo, timeSlotRepo, slotGenerator, timeSlotMapper);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── helpers ───────────────────────────────────────────────────────────────

    WorkingDayRequestDTO mondayDto() {
        return new WorkingDayRequestDTO(
                DayOfWeek.MONDAY, true,
                LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalTime.of(14, 0), LocalTime.of(17, 0));
    }

    WorkingDayRequestDTO morningOnlyDto(DayOfWeek day) {
        return new WorkingDayRequestDTO(day, true, LocalTime.of(8, 0), LocalTime.of(12, 0), null, null);
    }

    SchoolConfigRequestDTO validConfig() {
        return new SchoolConfigRequestDTO(List.of(mondayDto()), 30);
    }

    SchoolWorkingDay savedDay(DayOfWeek day) {
        return SchoolWorkingDay.builder().dayOfWeek(day)
                .morningStart(LocalTime.of(8, 0)).morningEnd(LocalTime.of(12, 0)).active(true).build();
    }

    void stubBuildResponse() {
        when(workingDayRepo.findByTenantIdOrderByDayOfWeekAsc(TENANT)).thenReturn(List.of());
        when(timeSlotRepo.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(TENANT)).thenReturn(List.of());
        when(timeSlotMapper.toResponseList(List.of())).thenReturn(List.of());
    }

    // ── CONFIGURE ────────────────────────────────────────────────────────────

    @Nested
    class Configure {

        @Test
        void noTenant_throwsTenantSecurityException() {
            assertThatThrownBy(() -> service.configure(validConfig()))
                    .isInstanceOf(TenantSecurityException.class);
            verify(timeSlotRepo, never()).deleteByTenantId(any());
        }

        @Test
        void nullDto_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);

            assertThatThrownBy(() -> service.configure(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void slotDurationBelowMinimum_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            SchoolConfigRequestDTO dto = new SchoolConfigRequestDTO(List.of(mondayDto()), 15);

            assertThatThrownBy(() -> service.configure(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("30");
        }

        @Test
        void nullSlotDuration_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            SchoolConfigRequestDTO dto = new SchoolConfigRequestDTO(List.of(mondayDto()), null);

            assertThatThrownBy(() -> service.configure(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void duplicateDays_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            SchoolConfigRequestDTO dto = new SchoolConfigRequestDTO(
                    List.of(morningOnlyDto(DayOfWeek.MONDAY), morningOnlyDto(DayOfWeek.MONDAY)), 30);

            assertThatThrownBy(() -> service.configure(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("doublons");
        }

        @Test
        void invalidMorningPeriod_startAfterEnd_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            SchoolConfigRequestDTO dto = new SchoolConfigRequestDTO(List.of(
                    new WorkingDayRequestDTO(DayOfWeek.MONDAY, true,
                            LocalTime.of(10, 0), LocalTime.of(8, 0), null, null)), 30);

            assertThatThrownBy(() -> service.configure(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("matin");
        }

        @Test
        void afternoonBeforeMorningEnd_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            SchoolConfigRequestDTO dto = new SchoolConfigRequestDTO(List.of(
                    new WorkingDayRequestDTO(DayOfWeek.MONDAY, true,
                            LocalTime.of(8, 0), LocalTime.of(13, 0),
                            LocalTime.of(12, 0), LocalTime.of(17, 0))), 30); // afternoon overlaps morning

            assertThatThrownBy(() -> service.configure(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("pause");
        }

        @Test
        void afternoonStartWithoutEnd_throwsBadRequest() {
            TenantContext.setTenantId(TENANT);
            SchoolConfigRequestDTO dto = new SchoolConfigRequestDTO(List.of(
                    new WorkingDayRequestDTO(DayOfWeek.MONDAY, true,
                            LocalTime.of(8, 0), LocalTime.of(12, 0),
                            LocalTime.of(14, 0), null)), 30); // end is null

            assertThatThrownBy(() -> service.configure(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void happyPath_resetsAndSavesAllDays() {
            TenantContext.setTenantId(TENANT);
            SchoolWorkingDay saved = savedDay(DayOfWeek.MONDAY);
            when(workingDayRepo.save(any())).thenReturn(saved);
            when(slotGenerator.generateForAllDays(anyList(), anyInt())).thenReturn(List.of());
            stubBuildResponse();

            SchoolConfigResponseDTO result = service.configure(validConfig());

            verify(timeSlotRepo).deleteByTenantId(TENANT);
            verify(workingDayRepo).deleteByTenantId(TENANT);
            verify(workingDayRepo).save(any());
            verify(slotGenerator).generateForAllDays(anyList(), anyInt());
            assertThat(result).isNotNull();
        }

        @Test
        void happyPath_multipleDays_savesEachDay() {
            TenantContext.setTenantId(TENANT);
            SchoolConfigRequestDTO dto = new SchoolConfigRequestDTO(
                    List.of(morningOnlyDto(DayOfWeek.MONDAY), morningOnlyDto(DayOfWeek.TUESDAY)), 30);

            when(workingDayRepo.save(any())).thenReturn(savedDay(DayOfWeek.MONDAY));
            when(slotGenerator.generateForAllDays(anyList(), anyInt())).thenReturn(List.of());
            stubBuildResponse();

            service.configure(dto);

            // save called twice — once per day
            verify(workingDayRepo, org.mockito.Mockito.times(2)).save(any());
        }
    }

    // ── TOGGLE DAY ────────────────────────────────────────────────────────────

    @Nested
    class ToggleDay {

        @Test
        void dayNotFound_throwsResourceNotFound() {
            TenantContext.setTenantId(TENANT);
            when(workingDayRepo.findByTenantIdAndDayOfWeek(TENANT, DayOfWeek.FRIDAY))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggleDay(DayOfWeek.FRIDAY, true))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void activateDay_regeneratesSlots() {
            TenantContext.setTenantId(TENANT);
            SchoolWorkingDay wd = savedDay(DayOfWeek.MONDAY);
            when(workingDayRepo.findByTenantIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY))
                    .thenReturn(Optional.of(wd));
            when(workingDayRepo.save(wd)).thenReturn(wd);
            when(slotGenerator.generateForDay(any(), anyInt())).thenReturn(List.of());
            stubBuildResponse();

            service.toggleDay(DayOfWeek.MONDAY, true);

            assertThat(wd.getActive()).isTrue();
            verify(slotGenerator).generateForDay(wd, SchoolConfigurationService.DEFAULT_SLOT_DURATION_MINUTES);
            verify(timeSlotRepo, never()).deleteByTenantIdAndDayOfWeek(anyString(), any());
        }

        @Test
        void deactivateDay_deletesSlots() {
            TenantContext.setTenantId(TENANT);
            SchoolWorkingDay wd = savedDay(DayOfWeek.MONDAY);
            when(workingDayRepo.findByTenantIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY))
                    .thenReturn(Optional.of(wd));
            when(workingDayRepo.save(wd)).thenReturn(wd);
            stubBuildResponse();

            service.toggleDay(DayOfWeek.MONDAY, false);

            assertThat(wd.getActive()).isFalse();
            verify(timeSlotRepo).deleteByTenantIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY);
            verify(slotGenerator, never()).generateForDay(any(), anyInt());
        }
    }

    // ── GET CONFIGURATION ─────────────────────────────────────────────────────

    @Nested
    class GetConfiguration {

        @Test
        void noTenant_throwsTenantSecurityException() {
            assertThatThrownBy(() -> service.getConfiguration())
                    .isInstanceOf(TenantSecurityException.class);
        }

        @Test
        void happyPath_returnsConfigWithCorrectSlotCount() {
            TenantContext.setTenantId(TENANT);
            SchoolWorkingDay wd = savedDay(DayOfWeek.MONDAY);
            tn.wtm.school.org.entity.TimeSlot ts1 = tn.wtm.school.org.entity.TimeSlot.builder()
                    .dayOfWeek(DayOfWeek.MONDAY).orderIndex(0).build();
            tn.wtm.school.org.entity.TimeSlot ts2 = tn.wtm.school.org.entity.TimeSlot.builder()
                    .dayOfWeek(DayOfWeek.MONDAY).orderIndex(1).build();

            when(workingDayRepo.findByTenantIdOrderByDayOfWeekAsc(TENANT)).thenReturn(List.of(wd));
            when(timeSlotRepo.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(TENANT))
                    .thenReturn(List.of(ts1, ts2));
            when(timeSlotMapper.toResponseList(List.of(ts1, ts2))).thenReturn(List.of());

            SchoolConfigResponseDTO result = service.getConfiguration();

            assertThat(result.totalSlotsPerWeek()).isEqualTo(2);
            assertThat(result.isReadyForGeneration()).isTrue();
            assertThat(result.workingDays()).hasSize(1);
        }

        @Test
        void noDaysConfigured_notConfigured() {
            TenantContext.setTenantId(TENANT);
            when(workingDayRepo.findByTenantIdOrderByDayOfWeekAsc(TENANT)).thenReturn(List.of());
            when(timeSlotRepo.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(TENANT)).thenReturn(List.of());
            when(timeSlotMapper.toResponseList(List.of())).thenReturn(List.of());

            SchoolConfigResponseDTO result = service.getConfiguration();

            assertThat(result.isReadyForGeneration()).isFalse();
            assertThat(result.totalSlotsPerWeek()).isZero();
        }
    }
}
