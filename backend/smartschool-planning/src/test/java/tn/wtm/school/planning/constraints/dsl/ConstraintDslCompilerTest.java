package tn.wtm.school.planning.constraints.dsl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.constraints.dsl.catalog.DslFieldCatalog;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslLogic;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dsl.model.DslCondition;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static tn.wtm.school.planning.constraints.dsl.ConstraintDslValidatorTest.condition;
import static tn.wtm.school.planning.constraints.dsl.ConstraintDslValidatorTest.maxThreeHoursPerTeacherDay;
import static tn.wtm.school.planning.constraints.dsl.ConstraintDslValidatorTest.terminaleMathFridayAfternoon;

class ConstraintDslCompilerTest {

    private ConstraintDslCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new ConstraintDslCompiler(new DslFieldCatalog());
    }

    // ── portée LESSON ─────────────────────────────────────────────────────────

    @Test
    void matchesTheLessonTheRuleTargets() {
        CompiledConstraint rule = compile(terminaleMathFridayAfternoon());

        Lesson mathsFridayAt15 = lesson("TERMINALE", "MATH",
                slot(DayOfWeek.FRIDAY, "15:00", DayPeriod.AFTERNOON));

        assertThat(rule.matches(mathsFridayAt15)).isTrue();
    }

    @Test
    void ignoresTheSameLessonEarlierInTheDay() {
        CompiledConstraint rule = compile(terminaleMathFridayAfternoon());

        Lesson mathsFridayAt10 = lesson("TERMINALE", "MATH",
                slot(DayOfWeek.FRIDAY, "10:00", DayPeriod.MORNING));

        assertThat(rule.matches(mathsFridayAt10)).isFalse();
    }

    @Test
    void ignoresAnotherLevel() {
        CompiledConstraint rule = compile(terminaleMathFridayAfternoon());

        Lesson secondeMathsFriday = lesson("SECONDE", "MATH",
                slot(DayOfWeek.FRIDAY, "16:00", DayPeriod.AFTERNOON));

        assertThat(rule.matches(secondeMathsFriday)).isFalse();
    }

    @Test
    void neverMatchesAnUnplacedLesson() {
        CompiledConstraint rule = compile(terminaleMathFridayAfternoon());

        // Une séance sans créneau ne viole rien : c'est ce qui permet au solveur
        // de partir d'une solution vide sans score dur catastrophique.
        Lesson unplaced = lesson("TERMINALE", "MATH", null);

        assertThat(rule.matches(unplaced)).isFalse();
    }

    // ── comparaison de chaînes ────────────────────────────────────────────────

    @Test
    void comparesStringsWithoutCaseOrAccents() {
        CompiledConstraint rule = compile(ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(condition("subject.name", DslOperator.EQUALS, "Mathématiques")))
                .action(DslAction.PENALIZE).severity(DslSeverity.SOFT).weight(1)
                .build());

        Lesson lesson = lesson("TERMINALE", "MATH", slot(DayOfWeek.MONDAY, "08:00", DayPeriod.MORNING));
        lesson.setSubjectName("MATHEMATIQUES");

        assertThat(rule.matches(lesson)).isTrue();
    }

    // ── logique OR ────────────────────────────────────────────────────────────

    @Test
    void orMatchesWhenAnySingleConditionHolds() {
        CompiledConstraint rule = compile(ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .logic(DslLogic.OR)
                .conditions(List.of(
                        condition("day", DslOperator.EQUALS, "FRIDAY"),
                        condition("day", DslOperator.EQUALS, "SATURDAY")))
                .action(DslAction.PENALIZE).severity(DslSeverity.SOFT).weight(1)
                .build());

        assertThat(rule.matches(lesson("TERMINALE", "MATH",
                slot(DayOfWeek.SATURDAY, "08:00", DayPeriod.MORNING)))).isTrue();
        assertThat(rule.matches(lesson("TERMINALE", "MATH",
                slot(DayOfWeek.MONDAY, "08:00", DayPeriod.MORNING)))).isFalse();
    }

    @Test
    void inOperatorAcceptsAListOfDays() {
        CompiledConstraint rule = compile(ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(DslCondition.builder()
                        .field("day").operator(DslOperator.IN)
                        .values(List.of("FRIDAY", "SATURDAY"))
                        .build()))
                .action(DslAction.PENALIZE).severity(DslSeverity.SOFT).weight(1)
                .build());

        assertThat(rule.matches(lesson("T", "MATH", slot(DayOfWeek.FRIDAY, "08:00", DayPeriod.MORNING)))).isTrue();
        assertThat(rule.matches(lesson("T", "MATH", slot(DayOfWeek.MONDAY, "08:00", DayPeriod.MORNING)))).isFalse();
    }

    @Test
    void acceptsFrenchDayNames() {
        CompiledConstraint rule = compile(ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(condition("day", DslOperator.EQUALS, "vendredi")))
                .action(DslAction.PENALIZE).severity(DslSeverity.SOFT).weight(1)
                .build());

        assertThat(rule.matches(lesson("T", "MATH",
                slot(DayOfWeek.FRIDAY, "08:00", DayPeriod.MORNING)))).isTrue();
    }

    // ── portée agrégée ────────────────────────────────────────────────────────

    @Test
    void convertsHourThresholdIntoHalfHourSlots() {
        CompiledConstraint rule = compile(maxThreeHoursPerTeacherDay());

        // 3 heures = 6 créneaux de 30 min. Le seuil interne doit refléter cette
        // conversion, sans quoi « max 3 h » deviendrait « max 3 créneaux ».
        assertThat(rule.getLimitUnits()).isEqualTo(6);
        assertThat(rule.breaches(6)).isFalse();
        assertThat(rule.breaches(7)).isTrue();
    }

    @Test
    void penaltyGrowsWithTheOvershoot() {
        CompiledConstraint rule = compile(maxThreeHoursPerTeacherDay());

        // Un dépassement de 2 créneaux doit coûter plus qu'un dépassement de 1,
        // sinon le solveur n'a aucune raison de réduire un dépassement qu'il ne
        // peut pas supprimer complètement.
        assertThat(rule.penaltyFor(rule.overshoot(8)))
                .isGreaterThan(rule.penaltyFor(rule.overshoot(7)));
    }

    @Test
    void groupsByTeacherAndDay() {
        CompiledConstraint rule = compile(maxThreeHoursPerTeacherDay());

        Lesson monday = lesson("T", "MATH", slot(DayOfWeek.MONDAY, "08:00", DayPeriod.MORNING));
        Lesson friday = lesson("T", "MATH", slot(DayOfWeek.FRIDAY, "08:00", DayPeriod.MORNING));

        assertThat(rule.groupKey(monday)).isNotEqualTo(rule.groupKey(friday));
    }

    @Test
    void countsDurationInHalfHourSlots() {
        CompiledConstraint rule = compile(maxThreeHoursPerTeacherDay());

        Lesson twoHours = lesson("T", "MATH", slot(DayOfWeek.MONDAY, "08:00", DayPeriod.MORNING));
        twoHours.setDurationSlots(4);

        assertThat(rule.metricUnits(twoHours)).isEqualTo(4);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private CompiledConstraint compile(ConstraintDsl dsl) {
        return compiler.compile(dsl, 1L, "TEST_RULE", "Règle de test");
    }

    private static Lesson lesson(String level, String subjectCode, TimeSlotRef slot) {
        return Lesson.builder()
                .id(1L)
                .subjectCode(subjectCode)
                .subjectName(subjectCode)
                .studentClassName("TA")
                .studentClassLevel(level)
                .sessionType(SessionType.COURS)
                .durationSlots(2)
                .classStudentCount(30)
                .teacher(TeacherRef.builder().id(7L).code("T7").name("Ahmed").maxHoursPerDay(6).build())
                .timeSlot(slot)
                .build();
    }

    private static TimeSlotRef slot(DayOfWeek day, String start, DayPeriod period) {
        return TimeSlotRef.builder()
                .id(1L).day(day).orderIndex(1)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(start).plusHours(1))
                .active(true).period(period).maxDurationSlots(4)
                .build();
    }
}
