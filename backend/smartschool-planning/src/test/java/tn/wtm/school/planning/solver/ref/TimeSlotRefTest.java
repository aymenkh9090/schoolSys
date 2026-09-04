package tn.wtm.school.planning.solver.ref;

import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSlotRefTest {

    @Test
    void activeSlotIsNotABreak() {
        TimeSlotRef slot = slot(1L, true, DayPeriod.MORNING);
        assertThat(slot.isBreakSlot()).isFalse();
    }

    @Test
    void inactiveSlotIsABreak() {
        TimeSlotRef slot = slot(2L, false, DayPeriod.MORNING);
        assertThat(slot.isBreakSlot()).isTrue();
    }

    @Test
    void labelContainsDayAndTime() {
        TimeSlotRef slot = TimeSlotRef.builder()
                .id(1L)
                .day(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .active(true)
                .period(DayPeriod.MORNING)
                .build();

        assertThat(slot.label()).contains("MONDAY", "08:00", "09:00");
    }

    @Test
    void equalityBasedOnIdOnly() {
        TimeSlotRef a = slot(5L, true, DayPeriod.MORNING);
        TimeSlotRef b = slot(5L, false, DayPeriod.AFTERNOON);
        TimeSlotRef c = slot(6L, true, DayPeriod.MORNING);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void periodIsPreserved() {
        assertThat(slot(1L, true, DayPeriod.MORNING).getPeriod()).isEqualTo(DayPeriod.MORNING);
        assertThat(slot(2L, true, DayPeriod.AFTERNOON).getPeriod()).isEqualTo(DayPeriod.AFTERNOON);
    }

    private TimeSlotRef slot(Long id, boolean active, DayPeriod period) {
        return TimeSlotRef.builder()
                .id(id)
                .day(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .orderIndex(1)
                .active(active)
                .period(period)
                .build();
    }
}
