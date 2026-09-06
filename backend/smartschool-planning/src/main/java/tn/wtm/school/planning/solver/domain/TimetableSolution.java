package tn.wtm.school.planning.solver.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import lombok.*;
import tn.wtm.school.planning.constraints.dsl.CompiledConstraint;
import tn.wtm.school.planning.solver.constraint.ActiveConstraintParam;
import tn.wtm.school.planning.solver.ref.ExpectedCourse;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Timefold planning solution — the complete weekly timetable problem for one tenant.
 *
 * Score levels (PLANNING_MODULE.md §16):
 *   Hard   — structural violations (teacher/room conflict, break slots)
 *   Medium — Ministry-mandated rules (max hours, student idle gaps)
 *   Soft   — optimisation preferences (balanced workload, morning/afternoon)
 *
 * The solver must reach score 0hard before the solution is considered valid.
 */
@PlanningSolution
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TimetableSolution {

    // ── solution metadata (not visible to Timefold) ───────────────────────────

    private String tenantId;
    private Long academicYearId;
    private Long constraintProfileId;

    // ── problem facts — TimeSlots (value range for Lesson.timeSlot) ───────────

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeSlotRange")
    @Builder.Default
    private List<TimeSlotRef> timeSlots = new ArrayList<>();

    // ── problem facts — Teachers (référence seulement, teacher est fixe dans Lesson) ──

    @ProblemFactCollectionProperty
    @Builder.Default
    private List<TeacherRef> teachers = new ArrayList<>();

    // ── problem facts — Rooms (value range for Lesson.room) ──────────────────

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "roomRange")
    @Builder.Default
    private List<RoomRef> rooms = new ArrayList<>();

    // ── dynamic constraint parameters (problem facts, per-solve) ─────────────

    /**
     * One entry per enabled constraint in the active profile.
     * Presence enables the constraint; absence disables it (JOIN produces no matches).
     * Loaded by {@link tn.wtm.school.planning.solver.builder.TimetableProblemBuilder}
     * via {@link tn.wtm.school.planning.solver.constraint.ConstraintWeightMapper#load}.
     */
    @ProblemFactCollectionProperty
    @Builder.Default
    private List<ActiveConstraintParam> activeConstraintParams = new ArrayList<>();

    // ── contraintes personnalisées compilées (problem facts, par solve) ───────

    /**
     * Une entrée par règle DSL active du profil, déjà compilée en prédicat.
     * Même mécanisme que {@link #activeConstraintParams} : la présence du fait
     * active la règle, son absence la neutralise sans toucher au provider.
     * Chargées par {@link tn.wtm.school.planning.solver.builder.CustomConstraintLoader}.
     */
    @ProblemFactCollectionProperty
    @Builder.Default
    private List<CompiledConstraint> customConstraints = new ArrayList<>();

    // ── programme attendu (ni fait Timefold, ni entité) ──────────────────────

    /**
     * Ce que les classes doivent recevoir, matière par matière — lu depuis les
     * données d'organisation, pas depuis les séances.
     *
     * <p><b>Aucune contrainte ne le joint</b>, et c'est pour cela qu'il n'est pas
     * déclaré comme fait Timefold : il ne sert pas au solveur mais à la
     * validation, avant la génération comme après. Il voyage ici pour que
     * {@code TimetableBusinessValidator} reste une fonction de son seul argument
     * tout en pouvant constater ce qui <em>manque</em> — une matière sans
     * affectation n'engendre aucune séance, et une fonction des séances est
     * aveugle à ce qui n'en a produit aucune.
     *
     * <p>Chargé par {@link tn.wtm.school.planning.solver.builder.CurriculumLoader}.
     */
    @Builder.Default
    private List<ExpectedCourse> expectedCurriculum = new ArrayList<>();

    // ── planning entities ─────────────────────────────────────────────────────

    @PlanningEntityCollectionProperty
    @Builder.Default
    private List<Lesson> lessons = new ArrayList<>();

    // ── score ─────────────────────────────────────────────────────────────────

    @PlanningScore
    private HardMediumSoftScore score;

    // ── derived helpers ───────────────────────────────────────────────────────

    public boolean isFeasible() {
        return score != null && score.isFeasible();
    }

    public int lessonCount() {
        return lessons == null ? 0 : lessons.size();
    }
}
