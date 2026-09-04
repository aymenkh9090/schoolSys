package tn.wtm.school.planning.constraints.dsl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslLogic;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrat DSL d'une contrainte personnalisée — la forme exacte stockée en base
 * dans {@code custom_constraint.dsl_json} et produite par l'assistant IA.
 *
 * <p>C'est une <b>description</b>, jamais du code : aucune expression n'est
 * compilée ni interprétée dynamiquement. Le compilateur ne fait que composer des
 * lambdas Java écrites à l'avance à partir de champs et d'opérateurs issus d'un
 * catalogue fermé. Un DSL malveillant ne peut donc rien exécuter — au pire, il
 * est rejeté par le validateur.</p>
 *
 * <pre>{@code
 * {
 *   "scope": "LESSON",
 *   "logic": "AND",
 *   "conditions": [
 *     { "field": "class.level",  "operator": "EQUALS", "value": "TERMINALE" },
 *     { "field": "subject.code", "operator": "EQUALS", "value": "MATH" },
 *     { "field": "day",          "operator": "EQUALS", "value": "FRIDAY" },
 *     { "field": "startTime",    "operator": "GREATER_THAN_OR_EQUAL", "value": "15:00" }
 *   ],
 *   "action": "PENALIZE",
 *   "severity": "HARD",
 *   "weight": 100
 * }
 * }</pre>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class ConstraintDsl {

    /** Version du contrat — permet de faire évoluer le format sans casser l'existant. */
    @Builder.Default
    private Integer version = 1;

    @Builder.Default
    private DslScope scope = DslScope.LESSON;

    @Builder.Default
    private DslLogic logic = DslLogic.AND;

    @Builder.Default
    private List<DslCondition> conditions = new ArrayList<>();

    /** Obligatoire pour les portées agrégées, interdit pour {@link DslScope#LESSON}. */
    private DslAggregate aggregate;

    @Builder.Default
    private DslAction action = DslAction.PENALIZE;

    @Builder.Default
    private DslSeverity severity = DslSeverity.SOFT;

    /** Multiplicateur du score unitaire. Borné par le validateur. */
    @Builder.Default
    private Integer weight = 1;

    public List<DslCondition> safeConditions() {
        return conditions == null ? List.of() : conditions;
    }
}
