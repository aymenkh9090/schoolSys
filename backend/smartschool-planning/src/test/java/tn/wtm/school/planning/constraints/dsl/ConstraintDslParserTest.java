package tn.wtm.school.planning.constraints.dsl;

import org.junit.jupiter.api.Test;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslLogic;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le parseur est le premier filtre traversé par une règle venue de l'extérieur —
 * formulaire, appel API direct ou génération par le LLM. Sa sévérité est un choix :
 * mieux vaut un refus explicite, que l'appelant peut corriger, qu'une règle
 * enregistrée en ignorant la moitié de ce qu'il croyait avoir dit.
 */
class ConstraintDslParserTest {

    private final ConstraintDslParser parser = new ConstraintDslParser();

    @Test
    void readsTheReferenceRule() {
        String json = """
                {
                  "scope": "LESSON",
                  "logic": "AND",
                  "conditions": [
                    {"field": "class.level",  "operator": "EQUALS", "value": "TERMINALE"},
                    {"field": "subject.code", "operator": "EQUALS", "value": "MATH"},
                    {"field": "day",          "operator": "EQUALS", "value": "FRIDAY"},
                    {"field": "startTime",    "operator": "GREATER_THAN_OR_EQUAL", "value": "15:00"}
                  ],
                  "action": "PENALIZE",
                  "severity": "HARD",
                  "weight": 100
                }
                """;

        ConstraintDsl dsl = parser.parse(json);

        assertThat(dsl.getScope()).isEqualTo(DslScope.LESSON);
        assertThat(dsl.getLogic()).isEqualTo(DslLogic.AND);
        assertThat(dsl.getAction()).isEqualTo(DslAction.PENALIZE);
        assertThat(dsl.getSeverity()).isEqualTo(DslSeverity.HARD);
        assertThat(dsl.getWeight()).isEqualTo(100);
        assertThat(dsl.safeConditions()).hasSize(4);
        assertThat(dsl.safeConditions().get(3).getOperator())
                .isEqualTo(DslOperator.GREATER_THAN_OR_EQUAL);
    }

    @Test
    void rejectsAnUnknownProperty() {
        // Le cas typique du LLM : une clé plausible mais inexistante. L'ignorer
        // silencieusement enregistrerait une règle amputée de son intention.
        String json = """
                {"scope": "LESSON", "target": "TERMINALE", "severity": "HARD", "weight": 10}
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("target");
    }

    @Test
    void rejectsAnUnknownEnumValue() {
        String json = """
                {"scope": "TRIMESTRE", "severity": "HARD", "weight": 10}
                """;

        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{ pas du json"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("illisible");
    }

    @Test
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void appliesDeclaredDefaultsWhenKeysAreOmitted() {
        // Les valeurs par défaut annoncées dans le contrat doivent tenir à la
        // désérialisation, pas seulement quand on passe par le builder : le JSON
        // est le chemin normal, le builder ne sert qu'aux tests et au code interne.
        ConstraintDsl dsl = parser.parse("""
                {"conditions": [{"field": "day", "operator": "EQUALS", "value": "FRIDAY"}]}
                """);

        assertThat(dsl.getVersion()).isEqualTo(1);
        assertThat(dsl.getScope()).isEqualTo(DslScope.LESSON);
        assertThat(dsl.getLogic()).isEqualTo(DslLogic.AND);
        assertThat(dsl.getAction()).isEqualTo(DslAction.PENALIZE);
        assertThat(dsl.getSeverity()).isEqualTo(DslSeverity.SOFT);
        assertThat(dsl.getWeight()).isEqualTo(1);
    }

    @Test
    void survivesAWriteReadRoundTrip() {
        ConstraintDsl original = ConstraintDslValidatorTest.terminaleMathFridayAfternoon();

        ConstraintDsl reloaded = parser.parse(parser.write(original));

        assertThat(reloaded.getScope()).isEqualTo(original.getScope());
        assertThat(reloaded.getWeight()).isEqualTo(original.getWeight());
        assertThat(reloaded.safeConditions()).hasSameSizeAs(original.safeConditions());
    }

    @Test
    void aggregateRuleSurvivesTheRoundTrip() {
        ConstraintDsl original = ConstraintDslValidatorTest.maxThreeHoursPerTeacherDay();

        ConstraintDsl reloaded = parser.parse(parser.write(original));

        assertThat(reloaded.getAggregate()).isNotNull();
        assertThat(reloaded.getAggregate().getValue()).isEqualTo(3d);
        assertThat(reloaded.getAggregate().getOperator()).isEqualTo(DslOperator.GREATER_THAN);
    }
}
