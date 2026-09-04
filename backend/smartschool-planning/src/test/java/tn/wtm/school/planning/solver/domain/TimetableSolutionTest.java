package tn.wtm.school.planning.solver.domain;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the domain model is correctly wired for Timefold:
 * value range provider IDs match, score type is correct, and the solver
 * can build a solution without throwing annotation-validation errors.
 *
 * A no-op ConstraintProvider is used — constraint logic comes in Step 3.
 */
class TimetableSolutionTest {

    private static final SolverConfig SOLVER_CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(NoOpConstraintProvider.class)
            .withTerminationConfig(
                    new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                            .withSpentLimit(Duration.ofSeconds(1)));

    private final SolverFactory<TimetableSolution> solverFactory =
            SolverFactory.create(SOLVER_CONFIG);
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager =
            SolutionManager.create(solverFactory);

    // ── builder tests ─────────────────────────────────────────────────────────

    @Test
    void emptySolutionBuildsWithDefaults() {
        TimetableSolution solution = TimetableSolution.builder()
                .tenantId("school-1").academicYearId(2026L).constraintProfileId(1L).build();

        assertThat(solution.getLessons()).isEmpty();
        assertThat(solution.getTimeSlots()).isEmpty();
        assertThat(solution.getTeachers()).isEmpty();
        assertThat(solution.getRooms()).isEmpty();
        assertThat(solution.getScore()).isNull();
    }

    @Test
    void lessonCountReflectsListSize() {
        TimetableSolution solution = TimetableSolution.builder()
                .lessons(List.of(lesson(1L), lesson(2L), lesson(3L)))
                .build();

        assertThat(solution.lessonCount()).isEqualTo(3);
    }

    @Test
    void isFeasibleFalseWhenScoreIsNull() {
        assertThat(TimetableSolution.builder().build().isFeasible()).isFalse();
    }

    @Test
    void isFeasibleTrueWhenHardScoreIsZero() {
        TimetableSolution solution = TimetableSolution.builder().build();
        solution.setScore(HardMediumSoftScore.of(0, 0, -5));
        assertThat(solution.isFeasible()).isTrue();
    }

    @Test
    void isFeasibleFalseWhenHardScoreIsNegative() {
        TimetableSolution solution = TimetableSolution.builder().build();
        solution.setScore(HardMediumSoftScore.of(-1, 0, 0));
        assertThat(solution.isFeasible()).isFalse();
    }

    // ── Timefold structural wiring ────────────────────────────────────────────

    @Test
    void timefoldAcceptsDomainModelAnnotations() {
        TimetableSolution problem = TimetableSolution.builder()
                .tenantId("school-1")
                .academicYearId(2026L)
                .constraintProfileId(1L)
                .timeSlots(List.of(
                        slot(1L, DayOfWeek.MONDAY, 1, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                        slot(2L, DayOfWeek.MONDAY, 2, LocalTime.of(9, 0), LocalTime.of(10, 0))))
                .teachers(List.of(
                        TeacherRef.builder().id(1L).code("T1").name("Teacher 1").maxHoursPerDay(6).build()))
                .rooms(List.of(
                        RoomRef.builder().id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build()))
                .lessons(List.of(lesson(1L)))
                .build();

        // solutionManager.update() validates annotations and computes score
        HardMediumSoftScore score = solutionManager.update(problem);

        assertThat(score).isNotNull();
        // No constraints → always 0/0/0
        assertThat(score).isEqualTo(HardMediumSoftScore.ZERO);
    }

    @Test
    void solverAssignsPlanningVariablesWhenProblemIsWellFormed() {
        TimetableSolution problem = TimetableSolution.builder()
                .tenantId("school-1")
                .academicYearId(2026L)
                .constraintProfileId(1L)
                .timeSlots(List.of(
                        slot(1L, DayOfWeek.MONDAY, 1, LocalTime.of(8, 0), LocalTime.of(9, 0))))
                .teachers(List.of(
                        TeacherRef.builder().id(1L).code("T1").name("Teacher 1").maxHoursPerDay(6).build()))
                .rooms(List.of(
                        RoomRef.builder().id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build()))
                .lessons(List.of(lesson(1L)))
                .build();

        TimetableSolution solved = solverFactory.buildSolver().solve(problem);

        Lesson placed = solved.getLessons().getFirst();
        assertThat(placed.getTimeSlot()).isNotNull();
        assertThat(placed.getTeacher()).isNotNull();
        assertThat(placed.getRoom()).isNotNull();
        assertThat(solved.getScore()).isEqualTo(HardMediumSoftScore.ZERO);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Lesson lesson(Long id) {
        return Lesson.builder()
                .id(id)
                .subjectCode("MATH")
                .subjectName("Mathématiques")
                .studentClassName("7B1")
                .teachingAssignmentId(id)
                .sessionType(SessionType.COURS)
                .requiresSpecialRoom(false)
                .groupIndex(0)
                // teacher est un champ fixe (pas PlanningVariable) — pré-initialisé ici
                .teacher(TeacherRef.builder().id(1L).code("T1").name("Teacher 1").maxHoursPerDay(6).build())
                .build();
    }

    private TimeSlotRef slot(Long id, DayOfWeek day, int order,
                              LocalTime start, LocalTime end) {
        return TimeSlotRef.builder()
                .id(id).day(day).orderIndex(order)
                .startTime(start).endTime(end)
                .active(true).period(DayPeriod.MORNING)
                .build();
    }

    /** Minimal ConstraintProvider for structural testing only — no constraints. */
    public static class NoOpConstraintProvider implements ConstraintProvider {
        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[0];
        }
    }
}
