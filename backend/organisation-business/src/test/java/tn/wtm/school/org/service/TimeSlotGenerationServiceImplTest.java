package tn.wtm.school.org.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.org.service.impl.TimeSlotGenerationServiceImpl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSlotGenerationServiceImplTest {

    @Mock TimeSlotRepository timeSlotRepo;

    TimeSlotGenerationServiceImpl service;

    static final String TENANT = "tenant-abc";

    @BeforeEach
    void setUp() {
        service = new TimeSlotGenerationServiceImpl(timeSlotRepo);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    void withTenant() {
        TenantContext.setTenantId(TENANT);
        when(timeSlotRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    SchoolWorkingDay activeDay(DayOfWeek day,
                               LocalTime mStart, LocalTime mEnd,
                               LocalTime aStart, LocalTime aEnd) {
        return SchoolWorkingDay.builder()
                .dayOfWeek(day)
                .morningStart(mStart).morningEnd(mEnd)
                .afternoonStart(aStart).afternoonEnd(aEnd)
                .active(true).build();
    }

    SchoolWorkingDay morningOnly(DayOfWeek day, LocalTime start, LocalTime end) {
        return activeDay(day, start, end, null, null);
    }

    // ── TENANT GUARD ──────────────────────────────────────────────────────────

    @Nested
    class TenantGuard {

        @Test
        void noTenantSet_throwsTenantSecurityException() {
            SchoolWorkingDay day = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));

            assertThatThrownBy(() -> service.generateForDay(day, 30))
                    .isInstanceOf(TenantSecurityException.class);

            verify(timeSlotRepo, never()).deleteByTenantIdAndDayOfWeek(anyString(), any());
            verify(timeSlotRepo, never()).saveAll(anyList());
        }
    }

    // ── NULL / INVALID INPUT ──────────────────────────────────────────────────

    @Nested
    class NullAndInvalidInput {

        @Test
        void nullWorkingDay_returnsEmptyWithNoRepoInteraction() {
            TenantContext.setTenantId(TENANT);

            List<TimeSlot> result = service.generateForDay(null, 30);

            assertThat(result).isEmpty();
            verify(timeSlotRepo, never()).deleteByTenantIdAndDayOfWeek(any(), any());
            verify(timeSlotRepo, never()).saveAll(anyList());
        }

        @Test
        void nullDayOfWeek_returnsEmptyWithNoRepoInteraction() {
            TenantContext.setTenantId(TENANT);
            SchoolWorkingDay day = SchoolWorkingDay.builder()
                    .dayOfWeek(null)
                    .morningStart(LocalTime.of(8, 0)).morningEnd(LocalTime.of(9, 0))
                    .active(true).build();

            List<TimeSlot> result = service.generateForDay(day, 30);

            assertThat(result).isEmpty();
            verify(timeSlotRepo, never()).deleteByTenantIdAndDayOfWeek(any(), any());
        }
    }

    // ── INACTIVE DAY ──────────────────────────────────────────────────────────

    @Nested
    class InactiveDay {

        @Test
        void inactiveDay_deletesExistingSlotsThenReturnsEmpty() {
            TenantContext.setTenantId(TENANT);
            SchoolWorkingDay day = SchoolWorkingDay.builder()
                    .dayOfWeek(DayOfWeek.WEDNESDAY)
                    .morningStart(LocalTime.of(8, 0)).morningEnd(LocalTime.of(12, 0))
                    .active(false).build();

            List<TimeSlot> result = service.generateForDay(day, 60);

            assertThat(result).isEmpty();
            // existing slots for that day must be cleared even when inactive
            verify(timeSlotRepo).deleteByTenantIdAndDayOfWeek(TENANT, DayOfWeek.WEDNESDAY);
            verify(timeSlotRepo, never()).saveAll(anyList());
        }

        @Test
        void activeNullTreatedAsActive_generatesSlots() {
            // active field defaults to true; null != Boolean.FALSE so generation proceeds
            withTenant();
            SchoolWorkingDay day = SchoolWorkingDay.builder()
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .morningStart(LocalTime.of(8, 0)).morningEnd(LocalTime.of(9, 0))
                    .active(null).build();

            List<TimeSlot> result = service.generateForDay(day, 30);

            assertThat(result).hasSize(2);
        }
    }

    // ── SLOT COUNT & TIMES ────────────────────────────────────────────────────

    @Nested
    class SlotCountAndTimes {

        @Test
        void morningOnly_30min_2slots() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));

            List<TimeSlot> slots = service.generateForDay(day, 30);

            assertThat(slots).hasSize(2);
            assertThat(slots.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(slots.get(0).getEndTime()).isEqualTo(LocalTime.of(8, 30));
            assertThat(slots.get(1).getStartTime()).isEqualTo(LocalTime.of(8, 30));
            assertThat(slots.get(1).getEndTime()).isEqualTo(LocalTime.of(9, 0));
        }

        @Test
        void morningOnly_60min_4slots_over4Hours() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.TUESDAY,
                    LocalTime.of(8, 0), LocalTime.of(12, 0));

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).hasSize(4);
        }

        @Test
        void morningOnly_durationExceedsWindow_zeroSlots() {
            withTenant();
            // 20-minute window, 30-minute slot → nothing fits
            SchoolWorkingDay day = morningOnly(DayOfWeek.THURSDAY,
                    LocalTime.of(8, 0), LocalTime.of(8, 20));

            List<TimeSlot> slots = service.generateForDay(day, 30);

            assertThat(slots).isEmpty();
        }

        @Test
        void morningOnly_exactlyOneFit_oneSingleSlot() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.FRIDAY,
                    LocalTime.of(8, 0), LocalTime.of(8, 30));

            List<TimeSlot> slots = service.generateForDay(day, 30);

            assertThat(slots).hasSize(1);
            assertThat(slots.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(slots.get(0).getEndTime()).isEqualTo(LocalTime.of(8, 30));
        }

        @Test
        void morningOnly_windowNotEvenly_dividedRemainingIgnored() {
            withTenant();
            // 90-minute window, 60-minute slot → 1 slot, remaining 30min ignored
            SchoolWorkingDay day = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 30));

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).hasSize(1);
            assertThat(slots.get(0).getEndTime()).isEqualTo(LocalTime.of(9, 0));
        }

        @Test
        void afternoonOnly_nullMorning_generatesSlotsForAfternoon() {
            withTenant();
            SchoolWorkingDay day = SchoolWorkingDay.builder()
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .morningStart(null).morningEnd(null)
                    .afternoonStart(LocalTime.of(14, 0)).afternoonEnd(LocalTime.of(16, 0))
                    .active(true).build();

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).hasSize(2);
            assertThat(slots).allMatch(s -> s.getDayPeriod() == DayPeriod.AFTERNOON);
        }

        @Test
        void afternoonOnly_invalidMorning_startAfterEnd_onlyAfternoonSlots() {
            withTenant();
            SchoolWorkingDay day = SchoolWorkingDay.builder()
                    .dayOfWeek(DayOfWeek.TUESDAY)
                    .morningStart(LocalTime.of(10, 0)).morningEnd(LocalTime.of(8, 0)) // inverted
                    .afternoonStart(LocalTime.of(14, 0)).afternoonEnd(LocalTime.of(15, 0))
                    .active(true).build();

            List<TimeSlot> slots = service.generateForDay(day, 30);

            assertThat(slots).hasSize(2);
            assertThat(slots).allMatch(s -> s.getDayPeriod() == DayPeriod.AFTERNOON);
        }

        @Test
        void bothPeriods_totalSlots_morningPlusAfternoon() {
            withTenant();
            SchoolWorkingDay day = activeDay(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0),  LocalTime.of(10, 0),   // 2 × 60min morning
                    LocalTime.of(14, 0), LocalTime.of(16, 0));  // 2 × 60min afternoon

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).hasSize(4);
            assertThat(slots.subList(0, 2)).allMatch(s -> s.getDayPeriod() == DayPeriod.MORNING);
            assertThat(slots.subList(2, 4)).allMatch(s -> s.getDayPeriod() == DayPeriod.AFTERNOON);
        }
    }

    // ── SLOT PROPERTIES ───────────────────────────────────────────────────────

    @Nested
    class SlotProperties {

        @Test
        void allSlotsHaveCorrectDayOfWeek() {
            withTenant();
            SchoolWorkingDay day = activeDay(DayOfWeek.WEDNESDAY,
                    LocalTime.of(8, 0),  LocalTime.of(10, 0),
                    LocalTime.of(14, 0), LocalTime.of(16, 0));

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).allMatch(s -> s.getDayOfWeek() == DayOfWeek.WEDNESDAY);
        }

        @Test
        void eachSlotDurationEqualsRequestedMinutes() {
            withTenant();
            SchoolWorkingDay day = activeDay(DayOfWeek.THURSDAY,
                    LocalTime.of(8, 0),  LocalTime.of(10, 0),
                    LocalTime.of(13, 0), LocalTime.of(15, 0));

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).allSatisfy(s ->
                    assertThat(Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
                            .isEqualTo(60));
        }

        @Test
        void slotsAreContiguous_endOfOneEqualsStartOfNext() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.FRIDAY,
                    LocalTime.of(8, 0), LocalTime.of(10, 0));

            List<TimeSlot> slots = service.generateForDay(day, 30);

            for (int i = 0; i < slots.size() - 1; i++) {
                assertThat(slots.get(i).getEndTime()).isEqualTo(slots.get(i + 1).getStartTime());
            }
        }

        @Test
        void orderIndexIsContinuousAcrossBothPeriods() {
            withTenant();
            // morning: 2 slots (0, 1) + afternoon: 2 slots (2, 3)
            SchoolWorkingDay day = activeDay(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0),  LocalTime.of(9, 0),
                    LocalTime.of(14, 0), LocalTime.of(15, 0));

            List<TimeSlot> slots = service.generateForDay(day, 30);

            assertThat(slots).hasSize(4);
            for (int i = 0; i < slots.size(); i++) {
                assertThat(slots.get(i).getOrderIndex()).isEqualTo(i);
            }
        }

        @Test
        void orderIndexStartsAtZero() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.TUESDAY,
                    LocalTime.of(8, 0), LocalTime.of(10, 0));

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots.get(0).getOrderIndex()).isZero();
        }

        @Test
        void morningSlotsDayPeriodIsMorning() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(10, 0));

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).allMatch(s -> s.getDayPeriod() == DayPeriod.MORNING);
        }

        @Test
        void afternoonSlotsDayPeriodIsAfternoon() {
            withTenant();
            SchoolWorkingDay day = SchoolWorkingDay.builder()
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .afternoonStart(LocalTime.of(14, 0)).afternoonEnd(LocalTime.of(16, 0))
                    .active(true).build();

            List<TimeSlot> slots = service.generateForDay(day, 60);

            assertThat(slots).allMatch(s -> s.getDayPeriod() == DayPeriod.AFTERNOON);
        }
    }

    // ── REPOSITORY INTERACTIONS ───────────────────────────────────────────────

    @Nested
    class RepositoryInteractions {

        @Test
        void activeDay_deletesBeforeSaving() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));

            service.generateForDay(day, 30);

            verify(timeSlotRepo).deleteByTenantIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY);
            verify(timeSlotRepo).saveAll(anyList());
        }

        @Test
        void slotsSavedWithTenantFromContext() {
            withTenant();
            SchoolWorkingDay day = morningOnly(DayOfWeek.TUESDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));

            List<TimeSlot> slots = service.generateForDay(day, 30);

            // deleteByTenantIdAndDayOfWeek was called with the correct tenant
            verify(timeSlotRepo).deleteByTenantIdAndDayOfWeek(TENANT, DayOfWeek.TUESDAY);
            // the returned slots come straight from saveAll (passthrough mock)
            assertThat(slots).hasSize(2);
        }

        @Test
        void noSlotsGenerated_saveAllStillCalledWithEmptyList() {
            withTenant();
            // duration exceeds window → slots list is empty but saveAll is still called
            SchoolWorkingDay day = morningOnly(DayOfWeek.FRIDAY,
                    LocalTime.of(8, 0), LocalTime.of(8, 20));

            service.generateForDay(day, 60);

            verify(timeSlotRepo).saveAll(anyList());
        }
    }

    // ── GENERATE FOR ALL DAYS ─────────────────────────────────────────────────

    @Nested
    class GenerateForAllDays {

        @Test
        void emptyList_returnsEmpty() {
            TenantContext.setTenantId(TENANT);

            List<TimeSlot> result = service.generateForAllDays(List.of(), 30);

            assertThat(result).isEmpty();
        }

        @Test
        void singleActiveDay_returnsSlots() {
            withTenant();
            SchoolWorkingDay monday = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(10, 0));

            List<TimeSlot> result = service.generateForAllDays(List.of(monday), 60);

            assertThat(result).hasSize(2);
        }

        @Test
        void multipleDays_aggregatesAllSlots() {
            withTenant();
            SchoolWorkingDay monday = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(10, 0));   // 2 slots
            SchoolWorkingDay tuesday = morningOnly(DayOfWeek.TUESDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));    // 1 slot

            List<TimeSlot> result = service.generateForAllDays(List.of(monday, tuesday), 60);

            assertThat(result).hasSize(3);
        }

        @Test
        void mixActiveAndInactive_onlyActiveContributeSlots() {
            withTenant();
            SchoolWorkingDay active = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));    // 2 slots
            SchoolWorkingDay inactive = SchoolWorkingDay.builder()
                    .dayOfWeek(DayOfWeek.TUESDAY)
                    .morningStart(LocalTime.of(8, 0)).morningEnd(LocalTime.of(9, 0))
                    .active(false).build();

            List<TimeSlot> result = service.generateForAllDays(List.of(active, inactive), 30);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(s -> s.getDayOfWeek() == DayOfWeek.MONDAY);
        }

        @Test
        void multipleDays_eachDayDeletesItsOwnSlots() {
            withTenant();
            SchoolWorkingDay monday = morningOnly(DayOfWeek.MONDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));
            SchoolWorkingDay friday = morningOnly(DayOfWeek.FRIDAY,
                    LocalTime.of(8, 0), LocalTime.of(9, 0));

            service.generateForAllDays(List.of(monday, friday), 30);

            verify(timeSlotRepo).deleteByTenantIdAndDayOfWeek(TENANT, DayOfWeek.MONDAY);
            verify(timeSlotRepo).deleteByTenantIdAndDayOfWeek(TENANT, DayOfWeek.FRIDAY);
        }
    }

    // ── helper to satisfy compiler on verify(mock, never()).method(any(), any()) ──
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
