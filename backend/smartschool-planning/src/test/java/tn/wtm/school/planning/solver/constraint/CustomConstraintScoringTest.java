package tn.wtm.school.planning.solver.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.constraints.dsl.CompiledConstraint;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslCompiler;
import tn.wtm.school.planning.constraints.dsl.catalog.DslFieldCatalog;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslAggregateMetric;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dsl.model.DslAggregate;
import tn.wtm.school.planning.constraints.dsl.model.DslCondition;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que les règles DSL atteignent réellement le score Timefold.
 *
 * Les tests du compilateur prouvent qu'un prédicat est correct ; ceux-ci prouvent
 * qu'il est bien branché sur le moteur — y compris le point le plus facile à
 * casser sans s'en apercevoir : une règle absente de la solution ne doit RIEN
 * pénaliser, faute de quoi désactiver une contrainte dans l'interface n'aurait
 * aucun effet sur la génération.
 */
class CustomConstraintScoringTest {

    private static final SolverConfig CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    private final SolutionManager<TimetableSolution, HardMediumSoftScore> scorer =
            SolutionManager.create(SolverFactory.create(CONFIG));

    private final ConstraintDslCompiler compiler = new ConstraintDslCompiler(new DslFieldCatalog());

    private final TimeSlotRef friMorning   = slot(1L, DayOfWeek.FRIDAY, 1, "08:00", DayPeriod.MORNING);
    private final TimeSlotRef friAfternoon = slot(2L, DayOfWeek.FRIDAY, 6, "15:00", DayPeriod.AFTERNOON);
    private final TimeSlotRef monMorning   = slot(3L, DayOfWeek.MONDAY, 1, "08:00", DayPeriod.MORNING);
    private final TimeSlotRef monMidMorning = slot(4L, DayOfWeek.MONDAY, 3, "09:00", DayPeriod.MORNING);
    private final TimeSlotRef monLate      = slot(5L, DayOfWeek.MONDAY, 5, "10:00", DayPeriod.MORNING);

    private final RoomRef    room    = RoomRef.builder().id(1L).code("A1").type(RoomType.NORMALE).capacity(40).build();
    private final TeacherRef teacher = TeacherRef.builder().id(7L).code("T7").name("Ahmed").maxHoursPerDay(6).build();

    // ── règle LESSON ──────────────────────────────────────────────────────────

    @Test
    void penalizesTheLessonForbiddenByTheRule() {
        Lesson forbidden = lesson(1L, "TERMINALE", "MATH", friAfternoon);

        HardMediumSoftScore score = score(List.of(noMathsFridayAfternoon()), forbidden);

        assertThat(score.hardScore()).isEqualTo(-100);
    }

    @Test
    void leavesAnAllowedLessonUntouched() {
        Lesson allowed = lesson(1L, "TERMINALE", "MATH", friMorning);

        assertThat(score(List.of(noMathsFridayAfternoon()), allowed).hardScore()).isZero();
    }

    @Test
    void disablingTheRuleRemovesThePenaltyEntirely() {
        Lesson forbidden = lesson(1L, "TERMINALE", "MATH", friAfternoon);

        // Aucune règle dans la solution = règle désactivée dans le profil.
        assertThat(score(List.of(), forbidden).hardScore()).isZero();
    }

    @Test
    void oneTenantsRuleDoesNotAffectAnotherTenantsLessons() {
        // Les règles injectées proviennent du chargement filtré par tenant : une
        // solution ne porte jamais que les règles de son établissement. Ce test
        // fige le comportement attendu — pas de règle, pas de pénalité.
        Lesson otherSchoolLesson = lesson(1L, "TERMINALE", "MATH", friAfternoon);

        assertThat(score(List.of(), otherSchoolLesson)).isEqualTo(HardMediumSoftScore.ZERO);
    }

    @Test
    void weightScalesThePenalty() {
        ConstraintDsl light = noMathsFridayAfternoon();
        light.setWeight(5);
        Lesson forbidden = lesson(1L, "TERMINALE", "MATH", friAfternoon);

        assertThat(score(List.of(light), forbidden).hardScore()).isEqualTo(-5);
    }

    @Test
    void softRuleTouchesOnlyTheSoftScore() {
        ConstraintDsl soft = noMathsFridayAfternoon();
        soft.setSeverity(DslSeverity.SOFT);
        soft.setWeight(3);
        Lesson forbidden = lesson(1L, "TERMINALE", "MATH", friAfternoon);

        HardMediumSoftScore score = score(List.of(soft), forbidden);

        assertThat(score.hardScore()).isZero();
        assertThat(score.softScore()).isEqualTo(-3);
    }

    @Test
    void rewardImprovesTheScoreInsteadOfDegradingIt() {
        ConstraintDsl reward = noMathsFridayAfternoon();
        reward.setAction(DslAction.REWARD);
        reward.setSeverity(DslSeverity.SOFT);
        reward.setWeight(4);
        Lesson matching = lesson(1L, "TERMINALE", "MATH", friAfternoon);

        assertThat(score(List.of(reward), matching).softScore()).isEqualTo(4);
    }

    // ── règle à seuil ─────────────────────────────────────────────────────────

    @Test
    void penalizesTheTeacherWhoExceedsTheDailyLimit() {
        // Trois séances d'une heure le même jour = 6 créneaux, pour un plafond de
        // 2 h (4 créneaux) : dépassement de 2 créneaux.
        Lesson a = lesson(1L, "TERMINALE", "MATH", monMorning);
        Lesson b = lesson(2L, "TERMINALE", "MATH", monMidMorning);
        Lesson c = lesson(3L, "TERMINALE", "MATH", monLate);

        HardMediumSoftScore score = score(List.of(maxTwoHoursPerTeacherDay()), a, b, c);

        assertThat(score.hardScore()).isEqualTo(-2 * 100);
    }

    @Test
    void staysAtZeroWhenTheLimitIsRespected() {
        Lesson a = lesson(1L, "TERMINALE", "MATH", monMorning);
        Lesson b = lesson(2L, "TERMINALE", "MATH", monMidMorning);

        assertThat(score(List.of(maxTwoHoursPerTeacherDay()), a, b).hardScore()).isZero();
    }

    @Test
    void countsEachDaySeparately() {
        // Deux heures lundi et deux heures vendredi ne dépassent pas un plafond
        // journalier de deux heures : le regroupement doit inclure le jour.
        Lesson a = lesson(1L, "TERMINALE", "MATH", monMorning);
        Lesson b = lesson(2L, "TERMINALE", "MATH", monMidMorning);
        Lesson c = lesson(3L, "TERMINALE", "MATH", friMorning);
        Lesson d = lesson(4L, "TERMINALE", "MATH", friAfternoon);

        assertThat(score(List.of(maxTwoHoursPerTeacherDay()), a, b, c, d).hardScore()).isZero();
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private ConstraintDsl noMathsFridayAfternoon() {
        return ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(
                        cond("class.level", DslOperator.EQUALS, "TERMINALE"),
                        cond("subject.code", DslOperator.EQUALS, "MATH"),
                        cond("day", DslOperator.EQUALS, "FRIDAY"),
                        cond("startTime", DslOperator.GREATER_THAN_OR_EQUAL, "15:00")))
                .action(DslAction.PENALIZE)
                .severity(DslSeverity.HARD)
                .weight(100)
                .build();
    }

    private ConstraintDsl maxTwoHoursPerTeacherDay() {
        return ConstraintDsl.builder()
                .scope(DslScope.TEACHER_DAY)
                .conditions(List.of())
                .aggregate(DslAggregate.builder()
                        .metric(DslAggregateMetric.TOTAL_HOURS)
                        .operator(DslOperator.GREATER_THAN)
                        .value(2d)
                        .build())
                .action(DslAction.PENALIZE)
                .severity(DslSeverity.HARD)
                .weight(100)
                .build();
    }

    private HardMediumSoftScore score(List<ConstraintDsl> rules, Lesson... lessons) {
        List<CompiledConstraint> compiled = rules.stream()
                .map(dsl -> compiler.compile(dsl, 1L, "RULE_" + rules.indexOf(dsl), "Règle test"))
                .toList();

        TimetableSolution solution = TimetableSolution.builder()
                .tenantId("school-1").academicYearId(2026L).constraintProfileId(1L)
                .timeSlots(List.of(friMorning, friAfternoon, monMorning, monMidMorning, monLate))
                .teachers(List.of(teacher))
                .rooms(List.of(room))
                .lessons(List.of(lessons))
                .activeConstraintParams(List.of())
                .customConstraints(compiled)
                .build();

        return scorer.update(solution);
    }

    private static DslCondition cond(String field, DslOperator operator, String value) {
        return DslCondition.builder().field(field).operator(operator).value(value).build();
    }

    private Lesson lesson(Long id, String level, String subjectCode, TimeSlotRef slot) {
        return Lesson.builder()
                .id(id)
                .subjectCode(subjectCode).subjectName(subjectCode)
                .studentClassName("TA-" + id).studentClassLevel(level)
                .sessionType(SessionType.COURS)
                .requiresSpecialRoom(false).groupIndex(0)
                .durationSlots(2).classStudentCount(30)
                .teacher(teacher).timeSlot(slot).room(room)
                .build();
    }

    private static TimeSlotRef slot(Long id, DayOfWeek day, int order, String start, DayPeriod period) {
        return TimeSlotRef.builder()
                .id(id).day(day).orderIndex(order)
                .startTime(LocalTime.parse(start)).endTime(LocalTime.parse(start).plusHours(1))
                .active(true).period(period).maxDurationSlots(4)
                .build();
    }
}
