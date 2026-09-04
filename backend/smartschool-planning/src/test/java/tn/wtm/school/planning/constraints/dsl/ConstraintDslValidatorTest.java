package tn.wtm.school.planning.constraints.dsl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.wtm.school.planning.constraints.dsl.catalog.DslFieldCatalog;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslAggregateMetric;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dsl.model.DslAggregate;
import tn.wtm.school.planning.constraints.dsl.model.DslCondition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le validateur est la seule barrière entre une règle proposée — par un
 * formulaire ou par le LLM — et le moteur de calcul. Ces tests couvrent les
 * refus qui comptent : champ inexistant, opérateur incompatible avec le type,
 * valeur hors liste fermée.
 */
class ConstraintDslValidatorTest {

    private ConstraintDslValidator validator;

    @BeforeEach
    void setUp() {
        DslFieldCatalog catalog = new DslFieldCatalog();
        validator = new ConstraintDslValidator(catalog, new ConstraintDslCompiler(catalog));
    }

    // ── cas nominal ───────────────────────────────────────────────────────────

    @Test
    void acceptsTheReferenceRule() {
        ConstraintDsl dsl = terminaleMathFridayAfternoon();

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getSummary()).isNotBlank();
    }

    @Test
    void summaryDescribesTheRuleInFrench() {
        DslValidationResult result = validator.validate(terminaleMathFridayAfternoon());

        assertThat(result.getSummary())
                .contains("Interdire")
                .contains("Niveau de la classe")
                .contains("Contrainte dure");
    }

    // ── refus : catalogue ─────────────────────────────────────────────────────

    @Test
    void rejectsUnknownField() {
        ConstraintDsl dsl = lessonRule(condition("teacher.salary", DslOperator.GREATER_THAN, "1000"));

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("champ inconnu").contains("teacher.salary");
    }

    @Test
    void listsAvailableFieldsWhenTheFieldIsUnknown() {
        DslValidationResult result = validator.validate(
                lessonRule(condition("matiere", DslOperator.EQUALS, "MATH")));

        // Le message est réinjecté dans le prompt du LLM : il doit contenir
        // de quoi se corriger, pas seulement constater l'échec.
        assertThat(result.errorMessage()).contains("subject.code").contains("class.level");
    }

    @Test
    void rejectsValueOutsideAClosedField() {
        ConstraintDsl dsl = lessonRule(condition("sessionType", DslOperator.EQUALS, "MAGISTRAL"));

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("MAGISTRAL").contains("Valeurs admises");
    }

    // ── refus : typage ────────────────────────────────────────────────────────

    @Test
    void rejectsOrderingOperatorOnAStringField() {
        ConstraintDsl dsl = lessonRule(condition("subject.code", DslOperator.GREATER_THAN, "MATH"));

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("GREATER_THAN").contains("STRING");
    }

    @Test
    void rejectsMalformedTime() {
        ConstraintDsl dsl = lessonRule(
                condition("startTime", DslOperator.GREATER_THAN_OR_EQUAL, "quinze heures"));

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("HH:mm");
    }

    @Test
    void rejectsBetweenWithASingleValue() {
        ConstraintDsl dsl = lessonRule(DslCondition.builder()
                .field("startTime").operator(DslOperator.BETWEEN)
                .values(List.of("08:00"))
                .build());

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("attend 2 valeur");
    }

    // ── refus : cohérence de la règle ─────────────────────────────────────────

    @Test
    void rejectsLessonScopeWithoutCondition() {
        ConstraintDsl dsl = ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of())
                .action(DslAction.PENALIZE).severity(DslSeverity.HARD).weight(10)
                .build();

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("au moins une condition");
    }

    @Test
    void rejectsHardReward() {
        ConstraintDsl dsl = ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(condition("day", DslOperator.EQUALS, "FRIDAY")))
                .action(DslAction.REWARD).severity(DslSeverity.HARD).weight(10)
                .build();

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("REWARD").contains("HARD");
    }

    @Test
    void rejectsWeightOutOfBounds() {
        ConstraintDsl dsl = terminaleMathFridayAfternoon();
        dsl.setWeight(99_999);

        assertThat(validator.validate(dsl).errorMessage()).contains("poids");
    }

    @Test
    void rejectsAggregateOnLessonScope() {
        ConstraintDsl dsl = terminaleMathFridayAfternoon();
        dsl.setAggregate(DslAggregate.builder()
                .metric(DslAggregateMetric.TOTAL_HOURS)
                .operator(DslOperator.GREATER_THAN).value(3d).build());

        assertThat(validator.validate(dsl).errorMessage()).contains("n'accepte pas de seuil");
    }

    @Test
    void rejectsAggregateScopeWithoutThreshold() {
        ConstraintDsl dsl = ConstraintDsl.builder()
                .scope(DslScope.TEACHER_DAY)
                .conditions(List.of())
                .action(DslAction.PENALIZE).severity(DslSeverity.HARD).weight(100)
                .build();

        assertThat(validator.validate(dsl).errorMessage()).contains("exige un seuil");
    }

    @Test
    void acceptsAggregateScopeWithoutCondition() {
        // « Aucun enseignant au-delà de 3 h par jour » ne cible personne en
        // particulier : l'absence de condition est ici le comportement voulu.
        ConstraintDsl dsl = maxThreeHoursPerTeacherDay();

        assertThat(validator.validate(dsl).isValid()).isTrue();
    }

    @Test
    void warnsWhenThresholdIsZero() {
        ConstraintDsl dsl = maxThreeHoursPerTeacherDay();
        dsl.getAggregate().setValue(0d);

        DslValidationResult result = validator.validate(dsl);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).isNotEmpty();
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    static ConstraintDsl terminaleMathFridayAfternoon() {
        return ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(
                        condition("class.level", DslOperator.EQUALS, "TERMINALE"),
                        condition("subject.code", DslOperator.EQUALS, "MATH"),
                        condition("day", DslOperator.EQUALS, "FRIDAY"),
                        condition("startTime", DslOperator.GREATER_THAN_OR_EQUAL, "15:00")))
                .action(DslAction.PENALIZE)
                .severity(DslSeverity.HARD)
                .weight(100)
                .build();
    }

    static ConstraintDsl maxThreeHoursPerTeacherDay() {
        return ConstraintDsl.builder()
                .scope(DslScope.TEACHER_DAY)
                .conditions(List.of())
                .aggregate(DslAggregate.builder()
                        .metric(DslAggregateMetric.TOTAL_HOURS)
                        .operator(DslOperator.GREATER_THAN)
                        .value(3d)
                        .build())
                .action(DslAction.PENALIZE)
                .severity(DslSeverity.HARD)
                .weight(100)
                .build();
    }

    private static ConstraintDsl lessonRule(DslCondition condition) {
        return ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(condition))
                .action(DslAction.PENALIZE).severity(DslSeverity.HARD).weight(10)
                .build();
    }

    static DslCondition condition(String field, DslOperator operator, String value) {
        return DslCondition.builder().field(field).operator(operator).value(value).build();
    }
}
