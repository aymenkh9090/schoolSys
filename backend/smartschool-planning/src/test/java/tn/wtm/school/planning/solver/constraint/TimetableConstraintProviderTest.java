package tn.wtm.school.planning.solver.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static tn.wtm.school.planning.solver.constraint.ConstraintCodes.*;

/**
 * Black-box tests for all TimetableConstraintProvider constraints.
 * Uses SolutionManager.update() to score fully-assigned timetables without running the solver.
 *
 * Section 6.2 — hard-coded structural constraints (always active, no params needed).
 * Section 6.3 — dynamic configurable constraints (require matching ActiveConstraintParam in solution).
 */
class TimetableConstraintProviderTest {

    private static final SolverConfig CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    private final SolverFactory<TimetableSolution> factory = SolverFactory.create(CONFIG);
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> scorer =
            SolutionManager.create(factory);

    // ── shared fixtures ───────────────────────────────────────────────────────

    private final TimeSlotRef mon1 = slot(1L,  DayOfWeek.MONDAY,    1, "08:00", "09:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef mon2 = slot(2L,  DayOfWeek.MONDAY,    2, "09:00", "10:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef mon3 = slot(3L,  DayOfWeek.MONDAY,    3, "10:00", "11:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef mon4 = slot(4L,  DayOfWeek.MONDAY,    4, "11:00", "12:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef brk  = slot(5L,  DayOfWeek.MONDAY,    5, "12:00", "13:00", false, DayPeriod.MORNING);
    private final TimeSlotRef mon6 = slot(6L,  DayOfWeek.MONDAY,    6, "14:00", "15:00", true,  DayPeriod.AFTERNOON);
    private final TimeSlotRef mon7 = slot(7L,  DayOfWeek.MONDAY,    7, "15:00", "16:00", true,  DayPeriod.AFTERNOON);
    private final TimeSlotRef fri1 = slot(8L,  DayOfWeek.FRIDAY,    1, "08:00", "09:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef fri2 = slot(9L,  DayOfWeek.FRIDAY,    2, "09:00", "10:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef fri3 = slot(10L, DayOfWeek.FRIDAY,    3, "10:00", "11:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef fri4 = slot(11L, DayOfWeek.FRIDAY,    4, "11:00", "12:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef fri5 = slot(12L, DayOfWeek.FRIDAY,    5, "14:00", "15:00", true,  DayPeriod.AFTERNOON);
    private final TimeSlotRef fri6 = slot(13L, DayOfWeek.FRIDAY,    6, "15:00", "16:00", true,  DayPeriod.AFTERNOON);
    private final TimeSlotRef tue1 = slot(14L, DayOfWeek.TUESDAY,   1, "08:00", "09:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef wed1 = slot(15L, DayOfWeek.WEDNESDAY, 1, "08:00", "09:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef thu1 = slot(16L, DayOfWeek.THURSDAY,  1, "08:00", "09:00", true,  DayPeriod.MORNING);
    private final TimeSlotRef sat1 = slot(17L, DayOfWeek.SATURDAY,  1, "08:00", "09:00", true,  DayPeriod.MORNING);

    private final RoomRef roomA    = room(1L,  "A1",  RoomType.NORMALE,      30);
    private final RoomRef roomB    = room(2L,  "B1",  RoomType.NORMALE,      30);
    private final RoomRef labPhys  = room(3L,  "ph1", RoomType.LABPHYSIQUE, 30);
    private final RoomRef labSci   = room(4L,  "sc1", RoomType.LABSCIENCE, 30);
    private final RoomRef smallRoom= room(5L,  "S1",  RoomType.NORMALE,      10);

    private final TeacherRef t1  = teacher(1L, "T1");
    private final TeacherRef t2  = teacher(2L, "T2");
    private final TeacherRef tFri = TeacherRef.builder().id(3L).code("T3").name("Fri Teacher")
            .maxHoursPerDay(6).unavailableDays(Set.of(DayOfWeek.FRIDAY)).build();

    // ══════════════════════════════════════════════════════════════════════════
    // Section 6.2 — hard-coded structural constraints
    // ══════════════════════════════════════════════════════════════════════════

    // ── 1. Teacher conflict ───────────────────────────────────────────────────

    @Test
    void teacherConflictIsPenalizedWhenSameTeacherSameSlot() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "PHYS", t1, mon1, roomB);

        assertThat(score(a, b).hardScore()).isNegative();
    }

    @Test
    void teacherConflictIsZeroWhenDifferentSlots() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, mon2, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    @Test
    void teacherConflictIsZeroWhenDifferentTeachers() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t2, mon1, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    // ── 2. Room conflict ──────────────────────────────────────────────────────

    @Test
    void roomConflictIsPenalizedWhenSameRoomSameSlot() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "PHYS", t2, mon1, roomA);

        assertThat(score(a, b).hardScore()).isNegative();
    }

    @Test
    void roomConflictIsZeroWhenDifferentRooms() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "PHYS", t2, mon1, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    // ── 3. Class conflict ─────────────────────────────────────────────────────

    @Test
    void classConflictIsPenalizedWhenSameClassSameSlot() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "PHYS", t2, mon1, roomB);

        assertThat(score(a, b).hardScore()).isNegative();
    }

    @Test
    void classConflictIsZeroWhenDifferentClasses() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t2, mon1, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    // ── 4. Room capacity ──────────────────────────────────────────────────────

    @Test
    void roomCapacityIsPenalizedWhenRoomTooSmall() {
        Lesson a = lessonWithCount(1L, "7A", "MATH", t1, mon1, smallRoom, 30);

        assertThat(score(a).hardScore()).isNegative();
    }

    @Test
    void roomCapacityIsZeroWhenRoomFitsFullClass() {
        Lesson a = lessonWithCount(1L, "7A", "MATH", t1, mon1, roomA, 30);

        assertThat(score(a).hardScore()).isZero();
    }

    @Test
    void roomCapacityDemiGroupNeedsHalfCapacity() {
        Lesson a = demiLesson(1L, "7A", "PHYS", t1, mon1, smallRoom, 30, 1, null);

        assertThat(score(a).hardScore()).isNegative();
    }

    @Test
    void roomCapacityDemiGroupFitsWhenHalfFits() {
        Lesson a = demiLesson(1L, "7A", "PHYS", t1, mon1, smallRoom, 20, 1, null);

        assertThat(score(a).hardScore()).isZero();
    }

    // ── 5. Teacher availability ───────────────────────────────────────────────

    @Test
    void teacherAvailabilityIsPenalizedOnUnavailableDay() {
        Lesson a = lesson(1L, "7A", "MATH", tFri, fri1, roomA);

        assertThat(score(a).hardScore()).isNegative();
    }

    @Test
    void teacherAvailabilityIsZeroOnAvailableDay() {
        Lesson a = lesson(1L, "7A", "MATH", tFri, mon1, roomA);

        assertThat(score(a).hardScore()).isZero();
    }

    // ── 6. Special room required ──────────────────────────────────────────────

    @Test
    void specialRoomRequiredIsPenalizedWhenTPInNormalRoom() {
        Lesson a = specialLesson(1L, "7A", "PHYS", t1, mon1, roomA,
                SessionType.TP, RoomType.LABPHYSIQUE);

        assertThat(score(a).hardScore()).isNegative();
    }

    @Test
    void specialRoomRequiredIsZeroWhenTPInCorrectLab() {
        Lesson a = specialLesson(1L, "7A", "PHYS", t1, mon1, labPhys,
                SessionType.TP, RoomType.LABPHYSIQUE);

        assertThat(score(a).hardScore()).isZero();
    }

    @Test
    void specialRoomRequiredIsPenalizedWhenWrongLabType() {
        Lesson a = specialLesson(1L, "7A", "PHYS", t1, mon1, labSci,
                SessionType.TP, RoomType.LABPHYSIQUE);

        assertThat(score(a).hardScore()).isNegative();
    }

    @Test
    void noSpecialRoomViolationForStandardLesson() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);

        assertThat(score(a).hardScore()).isZero();
    }

    // ── 7. Paired demi-group same slot ────────────────────────────────────────

    @Test
    void pairedDemiGroupIsPenalizedWhenDifferentSlots() {
        Long pairId = 99L;
        Lesson groupA = demiLesson(1L, "7A", "PHYS", t1, mon1, labPhys, 30, 1, pairId);
        Lesson groupB = demiLesson(2L, "7A", "PHYS", t2, mon2, roomA,   30, 2, pairId);

        assertThat(score(groupA, groupB).hardScore()).isNegative();
    }

    @Test
    void pairedDemiGroupIsZeroWhenSameSlot() {
        Long pairId = 99L;
        // Salles normales (pas de special room) pour isoler la contrainte de timing
        Lesson groupA = demiLesson(1L, "7A", "PHYS", t1, mon1, roomA, 30, 1, pairId);
        Lesson groupB = demiLesson(2L, "7A", "PHYS", t2, mon1, roomB, 30, 2, pairId);

        assertThat(score(groupA, groupB).hardScore()).isZero();
    }

    @Test
    void unpairedLessonsDoNotTriggerDemiGroupConstraint() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t2, mon2, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    // ── 8. No lesson in break slot ────────────────────────────────────────────

    @Test
    void breakSlotViolationIsPenalized() {
        Lesson a = lesson(1L, "7A", "MATH", t1, brk, roomA);

        assertThat(score(a).hardScore()).isNegative();
    }

    @Test
    void activeSlotDoesNotTriggerBreakConstraint() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);

        assertThat(score(a).hardScore()).isZero();
    }

    // ── 9. No student idle gaps ───────────────────────────────────────────────

    @Test
    void idleGapIsPenalizedWhenStudentHasHoleInSchedule() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "PHYS", t2, mon3, roomB);

        assertThat(score(a, b).hardScore()).isNegative();
    }

    @Test
    void noGapWhenLessonsAreContiguous() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "PHYS", t2, mon2, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    @Test
    void gapDoesNotAffectDifferentClasses() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "PHYS", t2, mon3, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    @Test
    void gapDoesNotAffectDifferentDays() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "PHYS", t2, fri1, roomB);

        assertThat(score(a, b).hardScore()).isZero();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Section 6.3 — dynamic configurable constraints
    // All tests use scoreWithParams() — the constraint is activated by an ActiveConstraintParam.
    // Tests also verify that without a matching param the constraint produces no penalty.
    // ══════════════════════════════════════════════════════════════════════════

    // ── 10. ONE_TEACHER_PER_SUBJECT_CLASS ─────────────────────────────────────

    @Test
    void oneTeacherPerSubjectClassPenalizesWhenTwoDifferentTeachersSameSubjectClass() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t2, mon2, roomB); // same class+subject, different teacher

        ActiveConstraintParam param = param(ONE_TEACHER_PER_SUBJECT_CLASS, 0, 1000);

        assertThat(scoreWithParams(List.of(param), a, b).hardScore()).isNegative();
    }

    @Test
    void oneTeacherPerSubjectClassIsZeroWhenSameTeacher() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t1, mon2, roomB);

        ActiveConstraintParam param = param(ONE_TEACHER_PER_SUBJECT_CLASS, 0, 1000);

        assertThat(scoreWithParams(List.of(param), a, b).hardScore()).isZero();
    }

    @Test
    void oneTeacherPerSubjectClassIsZeroWhenDisabled() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t2, mon2, roomB);

        // no param → constraint disabled
        assertThat(score(a, b).hardScore()).isZero();
    }

    // ── 11. MAX_TEACHER_HOURS_PER_DAY ─────────────────────────────────────────

    @Test
    void maxTeacherHoursPerDayPenalizedWhenExceeded() {
        // 4 leçons × 30min = 2h > maxHours=1 (2 slots)
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, mon2, roomB);
        Lesson c = lesson(3L, "7C", "MATH", t1, mon3, roomA);
        Lesson d = lesson(4L, "7D", "MATH", t1, mon4, roomB);

        ActiveConstraintParam param = param(MAX_TEACHER_HOURS_PER_DAY, 1, 1000);

        assertThat(scoreWithParams(List.of(param), a, b, c, d).hardScore()).isNegative();
    }

    @Test
    void maxTeacherHoursPerDayIsZeroWhenUnderLimit() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, mon2, roomB);

        ActiveConstraintParam param = param(MAX_TEACHER_HOURS_PER_DAY, 3, 1000);

        assertThat(scoreWithParams(List.of(param), a, b).hardScore()).isZero();
    }

    @Test
    void maxTeacherHoursPerDayIsZeroWhenDisabled() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, mon2, roomB);
        Lesson c = lesson(3L, "7C", "MATH", t1, mon3, roomA);
        Lesson d = lesson(4L, "7D", "MATH", t1, mon4, roomB);

        // no param → constraint disabled regardless of lesson count
        assertThat(score(a, b, c, d).hardScore()).isZero();
    }

    // ── 12. MAX_TEACHER_HOURS_FRIDAY_SATURDAY ─────────────────────────────────

    @Test
    void maxTeacherHoursFridayPenalizedWhenExceeded() {
        // 6 leçons × 30min = 3h > maxHours=1 (2 slots)
        Lesson a = lesson(1L,  "7A", "MATH", t1, fri1, roomA);
        Lesson b = lesson(2L,  "7B", "MATH", t1, fri2, roomB);
        Lesson c = lesson(3L,  "7C", "MATH", t1, fri3, roomA);
        Lesson d = lesson(4L,  "7D", "MATH", t1, fri4, roomB);
        Lesson e = lesson(5L,  "7E", "MATH", t1, fri5, roomA);
        Lesson f2 = lesson(6L, "7F", "MATH", t1, fri6, roomB);

        ActiveConstraintParam param = param(MAX_TEACHER_HOURS_FRIDAY_SATURDAY, 1, 1000);

        assertThat(scoreWithParams(List.of(param), a, b, c, d, e, f2).hardScore()).isNegative();
    }

    @Test
    void maxTeacherHoursFridayIsZeroWhenMondayNotCounted() {
        // Monday lessons should not count for the Friday/Saturday constraint
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, mon2, roomB);
        Lesson c = lesson(3L, "7C", "MATH", t1, mon3, roomA);
        Lesson d = lesson(4L, "7D", "MATH", t1, mon4, roomB);
        Lesson e = lesson(5L, "7E", "MATH", t1, mon6, roomA);
        Lesson f2 = lesson(6L, "7F", "MATH", t1, mon7, roomB);

        ActiveConstraintParam param = param(MAX_TEACHER_HOURS_FRIDAY_SATURDAY, 5, 1000);

        assertThat(scoreWithParams(List.of(param), a, b, c, d, e, f2).hardScore()).isZero();
    }

    // ── 13. MAX_STUDENT_HOURS_PER_DAY ─────────────────────────────────────────

    @Test
    void maxStudentHoursPerDayPenalizedWhenExceeded() {
        // 5 leçons × 30min = 2.5h > maxHours=1 (2 slots)
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "PHYS", t2, mon2, roomB);
        Lesson c = lesson(3L, "7A", "FRAN", t1, mon3, roomA);
        Lesson d = lesson(4L, "7A", "ARAB", t2, mon4, roomB);
        Lesson e = lesson(5L, "7A", "HIST", t1, mon6, roomA);

        ActiveConstraintParam param = param(MAX_STUDENT_HOURS_PER_DAY, 1, 1000);

        assertThat(scoreWithParams(List.of(param), a, b, c, d, e).hardScore()).isNegative();
    }

    @Test
    void maxStudentHoursPerDayIsZeroWhenUnderLimit() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "PHYS", t2, mon2, roomB);

        ActiveConstraintParam param = param(MAX_STUDENT_HOURS_PER_DAY, 4, 1000);

        assertThat(scoreWithParams(List.of(param), a, b).hardScore()).isZero();
    }

    @Test
    void maxStudentHoursPerDayDoesNotCountDemiGroupB() {
        // demi-group B (groupIndex=2) should not be counted
        // Salles normales pour éviter la contrainte normalCourseNotInSpecialRoom
        Lesson full   = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson demiA  = demiLesson(2L, "7A", "PHYS", t1, mon2, roomA,   30, 1, 99L);
        Lesson demiB  = demiLesson(3L, "7A", "PHYS", t2, mon2, roomB,   30, 2, 99L);
        Lesson full2  = lesson(4L, "7A", "ARAB", t2, mon3, roomA);

        // 2 full-class lessons + 1 demi-A counted = 3 total (groupIndex=2 excluded)
        // limit=4 → should be zero
        ActiveConstraintParam param = param(MAX_STUDENT_HOURS_PER_DAY, 4, 1000);

        assertThat(scoreWithParams(List.of(param), full, demiA, demiB, full2).hardScore()).isZero();
    }

    // ── 14. MAX_TWO_CONSECUTIVE_SESSIONS_SAME_SUBJECT ────────────────────────

    @Test
    void maxConsecutiveSameSubjectPenalizedWhenThreeInARow() {
        // Three consecutive MATH lessons for 7A on Monday (slots 1,2,3) — exceeds limit of 2
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t1, mon2, roomB);
        Lesson c = lesson(3L, "7A", "MATH", t1, mon3, roomA);

        ActiveConstraintParam param = param(MAX_TWO_CONSECUTIVE_SESSIONS, 2, 100);

        assertThat(scoreWithParams(List.of(param), a, b, c).hardScore()).isNegative();
    }

    @Test
    void maxConsecutiveSameSubjectIsZeroWhenTwoInARow() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t1, mon2, roomB);

        ActiveConstraintParam param = param(MAX_TWO_CONSECUTIVE_SESSIONS, 2, 100);

        assertThat(scoreWithParams(List.of(param), a, b).hardScore()).isZero();
    }

    @Test
    void maxConsecutiveSameSubjectIsZeroWhenOnDifferentDays() {
        // MATH on Monday and MATH on Friday — different days, never consecutive in the same day
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t2, fri1, roomB);

        ActiveConstraintParam param = param(MAX_TWO_CONSECUTIVE_SESSIONS, 2, 100);

        assertThat(scoreWithParams(List.of(param), a, b).hardScore()).isZero();
    }

    // ── 15. BALANCED_MORNING_AFTERNOON ───────────────────────────────────────

    @Test
    void balancedMorningAfternoonPenalizedWhenImbalanced() {
        // T1 has 4 morning lessons and 0 afternoon lessons → imbalance of 4
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, mon2, roomB);
        Lesson c = lesson(3L, "7C", "MATH", t1, mon3, roomA);
        Lesson d = lesson(4L, "7D", "MATH", t1, mon4, roomB);

        ActiveConstraintParam param = param(BALANCED_MORNING_AFTERNOON, 0, 100);

        assertThat(scoreWithParams(List.of(param), a, b, c, d).mediumScore()).isNegative();
    }

    @Test
    void balancedMorningAfternoonIsZeroWhenBalanced() {
        // T1 has 2 morning and 2 afternoon lessons → imbalance = 0
        Lesson morning1 = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson morning2 = lesson(2L, "7B", "MATH", t1, mon2, roomB);
        Lesson aft1     = lesson(3L, "7C", "MATH", t1, mon6, roomA);
        Lesson aft2     = lesson(4L, "7D", "MATH", t1, mon7, roomB);

        ActiveConstraintParam param = param(BALANCED_MORNING_AFTERNOON, 0, 100);

        assertThat(scoreWithParams(List.of(param), morning1, morning2, aft1, aft2).mediumScore()).isZero();
    }

    @Test
    void balancedMorningAfternoonIsZeroWhenDisabled() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, mon2, roomB);

        // no param → constraint disabled
        assertThat(score(a, b).mediumScore()).isZero();
    }

    // ── 17. TEACHER_WEEKLY_REST_DAY ───────────────────────────────────────────

    @Test
    void teacherWeeklyRestDayPenalizedWhenTeachesSixDays() {
        // T1 teaches on 6 different days
        Lesson a = lesson(1L,  "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L,  "7B", "MATH", t1, tue1, roomB);
        Lesson c = lesson(3L,  "7C", "MATH", t1, wed1, roomA);
        Lesson d = lesson(4L,  "7D", "MATH", t1, thu1, roomB);
        Lesson e = lesson(5L,  "7E", "MATH", t1, fri1, roomA);
        Lesson f2 = lesson(6L, "7F", "MATH", t1, sat1, roomB);

        ActiveConstraintParam param = param(TEACHER_WEEKLY_REST_DAY, 0, 10);

        assertThat(scoreWithParams(List.of(param), a, b, c, d, e, f2).softScore()).isNegative();
    }

    @Test
    void teacherWeeklyRestDayIsZeroWhenTeachesFiveDays() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7B", "MATH", t1, tue1, roomB);
        Lesson c = lesson(3L, "7C", "MATH", t1, wed1, roomA);
        Lesson d = lesson(4L, "7D", "MATH", t1, thu1, roomB);
        Lesson e = lesson(5L, "7E", "MATH", t1, fri1, roomA);

        ActiveConstraintParam param = param(TEACHER_WEEKLY_REST_DAY, 0, 10);

        assertThat(scoreWithParams(List.of(param), a, b, c, d, e).softScore()).isZero();
    }

    // ── 18. AVOID_SUBJECT_CONCENTRATION_SAME_DAY ─────────────────────────────

    @Test
    void avoidSubjectConcentrationPenalizedWhenSameSubjectTwicePerDay() {
        // 7A has MATH twice on Monday
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t1, mon3, roomB);

        ActiveConstraintParam param = param(AVOID_SUBJECT_CONCENTRATION_SAME_DAY, 0, 10);

        assertThat(scoreWithParams(List.of(param), a, b).softScore()).isNegative();
    }

    @Test
    void avoidSubjectConcentrationIsZeroWhenSubjectOncePerDay() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "PHYS", t2, mon2, roomB);

        ActiveConstraintParam param = param(AVOID_SUBJECT_CONCENTRATION_SAME_DAY, 0, 10);

        assertThat(scoreWithParams(List.of(param), a, b).softScore()).isZero();
    }

    @Test
    void avoidSubjectConcentrationIsZeroWhenSameSubjectDifferentDays() {
        Lesson a = lesson(1L, "7A", "MATH", t1, mon1, roomA);
        Lesson b = lesson(2L, "7A", "MATH", t1, fri1, roomB);

        ActiveConstraintParam param = param(AVOID_SUBJECT_CONCENTRATION_SAME_DAY, 0, 10);

        assertThat(scoreWithParams(List.of(param), a, b).softScore()).isZero();
    }

    // ── 19. THEORY_PRACTICE_SEPARATION ───────────────────────────────────────

    @Test
    void theoryPracticeSeparationPenalizedWhenCourseAndTPSameDay() {
        Lesson cours = lesson(1L, "7A", "PHYS", t1, mon1, roomA);
        Lesson tp    = specialLesson(2L, "7A", "PHYS", t2, mon3, labPhys,
                SessionType.TP, RoomType.LABPHYSIQUE);

        ActiveConstraintParam param = param(THEORY_PRACTICE_SEPARATION, 0, 100);

        assertThat(scoreWithParams(List.of(param), cours, tp).mediumScore()).isNegative();
    }

    @Test
    void theoryPracticeSeparationIsZeroWhenOnDifferentDays() {
        Lesson cours = lesson(1L, "7A", "PHYS", t1, mon1, roomA);
        Lesson tp    = specialLesson(2L, "7A", "PHYS", t2, fri1, labPhys,
                SessionType.TP, RoomType.LABPHYSIQUE);

        ActiveConstraintParam param = param(THEORY_PRACTICE_SEPARATION, 0, 100);

        assertThat(scoreWithParams(List.of(param), cours, tp).mediumScore()).isZero();
    }

    @Test
    void theoryPracticeSeparationIsZeroWhenDisabled() {
        Lesson cours = lesson(1L, "7A", "PHYS", t1, mon1, roomA);
        Lesson tp    = specialLesson(2L, "7A", "PHYS", t2, mon3, labPhys,
                SessionType.TP, RoomType.LABPHYSIQUE);

        // no param → constraint disabled
        assertThat(score(cours, tp).mediumScore()).isZero();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Score without any active constraint params (all dynamic constraints disabled). */
    private HardMediumSoftScore score(Lesson... lessons) {
        return scoreWithParams(List.of(), lessons);
    }

    /** Score with the given constraint params active. */
    private HardMediumSoftScore scoreWithParams(List<ActiveConstraintParam> params, Lesson... lessons) {
        TimetableSolution sol = TimetableSolution.builder()
                .tenantId("school-1").academicYearId(2026L).constraintProfileId(1L)
                .timeSlots(List.of(mon1, mon2, mon3, mon4, brk, mon6, mon7,
                                   fri1, fri2, fri3, fri4, fri5, fri6,
                                   tue1, wed1, thu1, sat1))
                .teachers(List.of(t1, t2, tFri))
                .rooms(List.of(roomA, roomB, labPhys, labSci, smallRoom))
                .lessons(List.of(lessons))
                .activeConstraintParams(params)
                .build();
        return scorer.update(sol);
    }

    // ── lesson builders ───────────────────────────────────────────────────────

    private Lesson lesson(Long id, String className, String subject,
                           TeacherRef teacher, TimeSlotRef slot, RoomRef room) {
        return Lesson.builder()
                .id(id).studentClassName(className).subjectCode(subject)
                .teachingAssignmentId(id).sessionType(SessionType.COURS)
                .requiresSpecialRoom(false).groupIndex(0).classStudentCount(30)
                .timeSlot(slot).teacher(teacher).room(room)
                .build();
    }

    private Lesson lessonWithCount(Long id, String className, String subject,
                                    TeacherRef teacher, TimeSlotRef slot, RoomRef room,
                                    int studentCount) {
        return Lesson.builder()
                .id(id).studentClassName(className).subjectCode(subject)
                .teachingAssignmentId(id).sessionType(SessionType.COURS)
                .requiresSpecialRoom(false).groupIndex(0).classStudentCount(studentCount)
                .timeSlot(slot).teacher(teacher).room(room)
                .build();
    }

    private Lesson demiLesson(Long id, String className, String subject,
                               TeacherRef teacher, TimeSlotRef slot, RoomRef room,
                               int studentCount, int groupIndex, Long pairedId) {
        return Lesson.builder()
                .id(id).studentClassName(className).subjectCode(subject)
                .teachingAssignmentId(id).sessionType(SessionType.TP)
                .requiredRoomType(RoomType.LABPHYSIQUE).requiresSpecialRoom(false)
                .groupIndex(groupIndex).pairedLessonId(pairedId).classStudentCount(studentCount)
                .timeSlot(slot).teacher(teacher).room(room)
                .build();
    }

    private Lesson specialLesson(Long id, String className, String subject,
                                  TeacherRef teacher, TimeSlotRef slot, RoomRef room,
                                  SessionType sessionType, RoomType requiredRoomType) {
        return Lesson.builder()
                .id(id).studentClassName(className).subjectCode(subject)
                .teachingAssignmentId(id).sessionType(sessionType)
                .requiredRoomType(requiredRoomType).requiresSpecialRoom(true)
                .groupIndex(0).classStudentCount(30)
                .timeSlot(slot).teacher(teacher).room(room)
                .build();
    }

    // ── fixture builders ──────────────────────────────────────────────────────

    private TimeSlotRef slot(Long id, DayOfWeek day, int order,
                              String start, String end, boolean active, DayPeriod period) {
        return TimeSlotRef.builder()
                .id(id).day(day).orderIndex(order)
                .startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end))
                .active(active).period(period)
                .build();
    }

    private RoomRef room(Long id, String code, RoomType type, int capacity) {
        return RoomRef.builder().id(id).code(code).type(type).capacity(capacity).build();
    }

    private TeacherRef teacher(Long id, String code) {
        return TeacherRef.builder().id(id).code(code).name("Teacher " + code)
                .maxHoursPerDay(6).build();
    }

    private ActiveConstraintParam param(String code, int intParam, int softWeight) {
        return new ActiveConstraintParam(code, intParam, softWeight);
    }
}
