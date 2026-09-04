package tn.wtm.school.org.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.org.dto.response.TimeSlotResponseDTO;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.org.mapper.TimeSlotMapper;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.org.service.impl.TimeSlotGenerationServiceImpl;
import tn.wtm.school.org.service.impl.TimeSlotServiceImpl;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

    @Mock TimeSlotRepository              timeSlotRepository;
    @Mock TimeSlotGenerationServiceImpl   generator;
    @Mock TimeSlotMapper                  mapper;

    TimeSlotServiceImpl service;

    static final String TENANT = "tenant-1";

    @BeforeEach
    void setUp() {
        service = new TimeSlotServiceImpl(timeSlotRepository, generator, mapper);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    TimeSlot slot(DayOfWeek day) {
        return TimeSlot.builder().dayOfWeek(day)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(8, 30))
                .dayPeriod(DayPeriod.MORNING).orderIndex(0).build();
    }

    TimeSlotResponseDTO dto(DayOfWeek day) {
        return new TimeSlotResponseDTO(null, day, null, null, null, null);
    }

    // ── listTimeSlots ─────────────────────────────────────────────────────────

    @Test
    void listTimeSlots_noTenant_throwsTenantSecurityException() {
        assertThatThrownBy(() -> service.listTimeSlots())
                .isInstanceOf(TenantSecurityException.class);
    }

    @Test
    void listTimeSlots_tenantIsolation_usesTenantscopedQuery() {
        TenantContext.setTenantId(TENANT);
        TimeSlot entity = slot(DayOfWeek.MONDAY);
        when(timeSlotRepository.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(TENANT))
                .thenReturn(List.of(entity));
        when(mapper.toResponseList(List.of(entity))).thenReturn(List.of(dto(DayOfWeek.MONDAY)));

        List<TimeSlotResponseDTO> result = service.listTimeSlots();

        assertThat(result).hasSize(1);
        verify(timeSlotRepository).findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(TENANT);
    }

    // ── listTimeSlotsByDay ────────────────────────────────────────────────────

    @Test
    void listByDay_noTenant_throwsTenantSecurityException() {
        assertThatThrownBy(() -> service.listTimeSlotsByDay(DayOfWeek.MONDAY))
                .isInstanceOf(TenantSecurityException.class);
    }

    @Test
    void listByDay_tenantIsolation_usesTenantscopedQuery() {
        TenantContext.setTenantId(TENANT);
        TimeSlot entity = slot(DayOfWeek.TUESDAY);
        when(timeSlotRepository.findByTenantIdAndDayOfWeekOrderByOrderIndexAsc(TENANT, DayOfWeek.TUESDAY))
                .thenReturn(List.of(entity));
        when(mapper.toResponseList(List.of(entity))).thenReturn(List.of(dto(DayOfWeek.TUESDAY)));

        List<TimeSlotResponseDTO> result = service.listTimeSlotsByDay(DayOfWeek.TUESDAY);

        assertThat(result).hasSize(1);
        verify(timeSlotRepository).findByTenantIdAndDayOfWeekOrderByOrderIndexAsc(TENANT, DayOfWeek.TUESDAY);
    }

    // ── generate ──────────────────────────────────────────────────────────────

    @Test
    void generate_delegatesToGeneratorAndMapsResult() {
        TenantContext.setTenantId(TENANT);
        SchoolWorkingDay wd = SchoolWorkingDay.builder().dayOfWeek(DayOfWeek.MONDAY)
                .morningStart(LocalTime.of(8, 0)).morningEnd(LocalTime.of(10, 0)).active(true).build();
        TimeSlot entity = slot(DayOfWeek.MONDAY);

        when(generator.generateForAllDays(anyList(), anyInt())).thenReturn(List.of(entity));
        when(mapper.toResponseList(List.of(entity))).thenReturn(List.of(dto(DayOfWeek.MONDAY)));

        List<TimeSlotResponseDTO> result = service.generate(List.of(wd), 30);

        assertThat(result).hasSize(1);
        verify(generator).generateForAllDays(List.of(wd), 30);
    }

    @Test
    void generate_emptyDaysList_returnsEmpty() {
        TenantContext.setTenantId(TENANT);
        when(generator.generateForAllDays(List.of(), 30)).thenReturn(List.of());
        when(mapper.toResponseList(List.of())).thenReturn(List.of());

        assertThat(service.generate(List.of(), 30)).isEmpty();
    }
}
