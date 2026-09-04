package tn.wtm.school.planning.solver.ref;

import lombok.*;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Set;

/**
 * Lightweight snapshot of a Teacher for the Timefold solver.
 * Carries only what the constraint provider needs to reason about availability.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TeacherRef {

    private Long id;

    private String code;          // e.g. "08", "17" — shown in real timetable grids
    private String name;          // full name for display
    private int maxHoursPerDay;   // from Teacher.maxHeuresJour, default 6

    @Builder.Default
    private Set<DayOfWeek> unavailableDays = Collections.emptySet();

    /** True when this teacher must not be scheduled on the given day. */
    public boolean isUnavailableOn(DayOfWeek day) {
        return unavailableDays != null && unavailableDays.contains(day);
    }
}
