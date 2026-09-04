package tn.wtm.school.planning.solver.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.*;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

/**
 * Core Timefold planning entity — one unit of instruction to be placed on the timetable.
 *
 * Fixed facts are loaded from the organisation-module (TeachingAssignment + PatternDetail)
 * and never changed by the solver.
 *
 * Planning variables (timeSlot, teacher, room) are decided by Timefold.
 *
 * Demi-group pairs: when groupIndex > 0, two lessons (A=1, B=2) share the same
 * pairedLessonId so the solver can enforce they are placed in the same TimeSlot.
 */
@PlanningEntity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Lesson {

    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    // ── fixed facts ───────────────────────────────────────────────────────────

    private String subjectCode;
    private String subjectName;

    /** True quand la matière est déclarée « principale » (Subject.estPrincipale). */
    private boolean mainSubject;

    private String studentClassName;

    /**
     * Code du niveau de la classe (ex. « 4EME », « TERMINALE »), repris de
     * ClassGroup.level. Exposé au DSL sous le champ {@code class.level} : c'est
     * ce qui permet à une école d'écrire une règle qui ne vise qu'un niveau.
     */
    private String studentClassLevel;

    /** Spécialité de la classe (ClassGroup.codeSpecialite), ou null. */
    private String studentClassSpeciality;

    private Long teachingAssignmentId;

    private SessionType sessionType;

    /** Non-null only when this lesson requires a specific lab/sport room. */
    private RoomType requiredRoomType;

    private boolean requiresSpecialRoom;

    /**
     * 0 = full class, 1 = demi-group A, 2 = demi-group B.
     * Pairs of lessons with groupIndex 1 and 2 share the same pairedLessonId.
     */
    private int groupIndex;

    /**
     * Number of consecutive 30-min slots this session occupies.
     * 1h = 2 slots, 1h30 = 3 slots, 2h = 4 slots. The assigned {@link #timeSlot}
     * is the START slot; the session spans durationSlots × 30 min from there.
     */
    @Builder.Default
    private int durationSlots = 1;

    /** Links the two demi-group lessons that must share the same TimeSlot. */
    private Long pairedLessonId;

    /** Number of students in the class (needed for room capacity constraint). */
    private int classStudentCount;

    // ── planning variables ────────────────────────────────────────────────────

    @PlanningVariable(valueRangeProviderRefs = "timeSlotRange")
    private TimeSlotRef timeSlot;

    // Champ fixe — chargé depuis TeachingAssignment, Timefold ne le modifie pas
    private TeacherRef teacher;

    @PlanningVariable(valueRangeProviderRefs = "roomRange")
    private RoomRef room;

    // ── derived helpers ───────────────────────────────────────────────────────

    /** True for group A (1) or group B (2) — i.e. not a full-class session. */
    public boolean isDemiGroup() {
        return groupIndex > 0;
    }

    /** True when this lesson has a demi-group partner that must share its time slot. */
    public boolean isPaired() {
        return pairedLessonId != null;
    }

    /** True when both time slot and room are assigned (fully placed). */
    public boolean isAssigned() {
        return timeSlot != null && room != null;
    }

    /** Minutes covered by one 30-min slot. */
    private static final int SLOT_MINUTES = 30;

    /** Start time of the session (its start slot's start), or null if unplaced. */
    public java.time.LocalTime getStartTime() {
        return timeSlot == null ? null : timeSlot.getStartTime();
    }

    /** Exclusive end time = start + durationSlots × 30 min, or null if unplaced. */
    public java.time.LocalTime getEndTime() {
        if (timeSlot == null || timeSlot.getStartTime() == null) {
            return null;
        }
        return timeSlot.getStartTime().plusMinutes((long) SLOT_MINUTES * Math.max(1, durationSlots));
    }

    /**
     * True when this lesson's time interval overlaps {@code other}'s on the same day.
     * Used by teacher/room/class conflict constraints for multi-slot sessions.
     */
    public boolean overlapsInTime(Lesson other) {
        if (timeSlot == null || other.timeSlot == null) {
            return false;
        }
        if (timeSlot.getDay() != other.timeSlot.getDay()) {
            return false;
        }
        java.time.LocalTime aStart = getStartTime(), aEnd = getEndTime();
        java.time.LocalTime bStart = other.getStartTime(), bEnd = other.getEndTime();
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
