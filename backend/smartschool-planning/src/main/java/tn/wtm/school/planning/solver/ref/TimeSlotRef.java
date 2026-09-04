package tn.wtm.school.planning.solver.ref;

import lombok.*;
import tn.wtm.school.org.enums.DayPeriod;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Lightweight snapshot of a TimeSlot for the Timefold solver.
 * active=false marks a slot excluded from the value range (currently unused —
 * every persisted TimeSlot is already a genuine working slot).
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TimeSlotRef {

    private Long id;

    private DayOfWeek day;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer orderIndex;
    private boolean active;    // false = break slot (12h–14h), never assigned by solver
    private DayPeriod period;  // MORNING / AFTERNOON

    /**
     * Number of contiguous active slots from this slot to the end of its work
     * block (same day + same period). A session starting here can occupy at most
     * this many 30-min slots without crossing the lunch break or the day's end.
     * Computed by TimetableProblemBuilder.
     */
    @Builder.Default
    private int maxDurationSlots = 1;

    /** Returns true when this slot falls in the 12h–14h break window. */
    public boolean isBreakSlot() {
        return !active;
    }

    public String label() {
        return day + " " + startTime + "-" + endTime;
    }
}
