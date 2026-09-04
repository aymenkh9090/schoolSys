package tn.wtm.school.planning.constraints.dsl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.planning.constraints.dsl.catalog.DslField;
import tn.wtm.school.planning.constraints.dsl.catalog.DslFieldCatalog;
import tn.wtm.school.planning.constraints.dsl.enums.DslAggregateMetric;
import tn.wtm.school.planning.constraints.dsl.enums.DslFieldType;
import tn.wtm.school.planning.constraints.dsl.enums.DslLogic;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dsl.model.DslAggregate;
import tn.wtm.school.planning.constraints.dsl.model.DslCondition;
import tn.wtm.school.planning.solver.domain.Lesson;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Transforme une règle DSL validée en {@link CompiledConstraint} évaluable.
 *
 * <p>La compilation consiste uniquement à <b>composer des lambdas écrites ici</b>
 * : une par opérateur, appliquée à un extracteur pris dans le catalogue. Rien
 * n'est évalué dynamiquement, aucun code n'est chargé, aucune expression n'est
 * interprétée. C'est ce qui distingue un DSL déclaratif d'un « script en base » :
 * la surface d'attaque se limite au choix d'un champ et d'un opérateur parmi une
 * liste finie, tous deux déjà vérifiés par {@link ConstraintDslValidator}.</p>
 *
 * <p>La conversion des valeurs (heures, jours, nombres) se fait une seule fois,
 * ici, et pas à chaque appel du prédicat : le solveur évalue ces règles des
 * millions de fois par génération.</p>
 */
@Component
@RequiredArgsConstructor
public class ConstraintDslCompiler {

    private final DslFieldCatalog catalog;

    /**
     * Compile la règle. Suppose qu'elle a déjà passé la validation : toute erreur
     * résiduelle est traitée comme une anomalie serveur, pas comme une saisie
     * utilisateur.
     */
    public CompiledConstraint compile(ConstraintDsl dsl, Long id, String code, String name) {
        Predicate<Lesson> filter = compileConditions(dsl);

        int limitUnits = 0;
        DslAggregateMetric metric = null;
        DslOperator aggregateOperator = null;

        if (dsl.getScope().isAggregate()) {
            DslAggregate aggregate = dsl.getAggregate();
            if (aggregate == null || aggregate.getValue() == null) {
                throw new BadRequestException(
                        "La portée " + dsl.getScope() + " exige un seuil « aggregate ».");
            }
            metric            = aggregate.getMetric();
            aggregateOperator = aggregate.getOperator();
            limitUnits        = (int) Math.round(aggregate.getValue() * metric.unitsPerValue());
        }

        return CompiledConstraint.builder()
                .id(id)
                .code(code)
                .name(name)
                .scope(dsl.getScope())
                .action(dsl.getAction())
                .severity(dsl.getSeverity())
                .weight(dsl.getWeight() == null ? 1 : dsl.getWeight())
                .filter(filter)
                .metric(metric)
                .aggregateOperator(aggregateOperator)
                .limitUnits(limitUnits)
                .summary(summarize(dsl))
                .build();
    }

    // ── conditions ────────────────────────────────────────────────────────────

    private Predicate<Lesson> compileConditions(ConstraintDsl dsl) {
        List<DslCondition> conditions = dsl.safeConditions();
        if (conditions.isEmpty()) {
            // Une règle sans condition s'applique à toutes les séances. C'est
            // légitime pour une portée agrégée (« aucun enseignant au-delà de 5h »)
            // et refusé par le validateur pour une portée LESSON.
            return lesson -> true;
        }

        List<Predicate<Lesson>> compiled = conditions.stream()
                .map(this::compileCondition)
                .toList();

        if (dsl.getLogic() == DslLogic.OR) {
            return lesson -> compiled.stream().anyMatch(p -> p.test(lesson));
        }
        return lesson -> compiled.stream().allMatch(p -> p.test(lesson));
    }

    private Predicate<Lesson> compileCondition(DslCondition condition) {
        DslField field = catalog.find(condition.getField())
                .orElseThrow(() -> new BadRequestException(
                        "Champ inconnu : " + condition.getField()));

        DslFieldType type = field.getType();
        DslOperator  op   = condition.getOperator();

        List<Comparable<?>> operands = condition.effectiveValues().stream()
                .map(raw -> DslValues.coerce(type, raw))
                .collect(Collectors.toList());

        return lesson -> {
            Comparable<?> actual = DslValues.fromLesson(type, field.valueOf(lesson));
            // Séance pas encore placée, ou information absente : la condition est
            // fausse. Une séance sans créneau ne peut violer aucune règle horaire.
            return actual != null && evaluate(op, actual, operands);
        };
    }

    private boolean evaluate(DslOperator op, Comparable<?> actual, List<Comparable<?>> operands) {
        return switch (op) {
            case EQUALS     -> operands.get(0).equals(actual);
            case NOT_EQUALS -> !operands.get(0).equals(actual);
            case IN         -> operands.contains(actual);
            case NOT_IN     -> !operands.contains(actual);

            case GREATER_THAN          -> isAtLeast(DslValues.compare(actual, operands.get(0)), 1);
            case GREATER_THAN_OR_EQUAL -> isAtLeast(DslValues.compare(actual, operands.get(0)), 0);
            case LESS_THAN             -> isAtMost(DslValues.compare(actual, operands.get(0)), -1);
            case LESS_THAN_OR_EQUAL    -> isAtMost(DslValues.compare(actual, operands.get(0)), 0);

            case BETWEEN -> isAtLeast(DslValues.compare(actual, operands.get(0)), 0)
                            && isAtMost(DslValues.compare(actual, operands.get(1)), 0);

            case CONTAINS    -> actual instanceof String s && s.contains(String.valueOf(operands.get(0)));
            case STARTS_WITH -> actual instanceof String s && s.startsWith(String.valueOf(operands.get(0)));
        };
    }

    private static boolean isAtLeast(Integer comparison, int threshold) {
        return comparison != null && comparison >= threshold;
    }

    private static boolean isAtMost(Integer comparison, int threshold) {
        return comparison != null && comparison <= threshold;
    }

    // ── résumé lisible ────────────────────────────────────────────────────────

    /**
     * Phrase française décrivant la règle.
     *
     * <p>Elle est produite à partir du DSL, pas du texte saisi par l'utilisateur :
     * c'est donc bien ce que le moteur va appliquer qui est montré à l'écran de
     * confirmation. Une reformulation par le LLM pourrait, elle, être fidèle à
     * l'intention mais infidèle à la règle réellement enregistrée.</p>
     */
    public String summarize(ConstraintDsl dsl) {
        StringBuilder sb = new StringBuilder();
        sb.append(dsl.getAction() == tn.wtm.school.planning.constraints.dsl.enums.DslAction.REWARD
                ? "Favoriser" : "Interdire");
        sb.append(" — portée ").append(dsl.getScope().getLabel().toLowerCase());

        List<DslCondition> conditions = dsl.safeConditions();
        if (!conditions.isEmpty()) {
            String joiner = dsl.getLogic() == DslLogic.OR ? " OU " : " ET ";
            sb.append(" : ").append(conditions.stream()
                    .map(this::describeCondition)
                    .collect(Collectors.joining(joiner)));
        }

        if (dsl.getScope().isAggregate() && dsl.getAggregate() != null) {
            DslAggregate agg = dsl.getAggregate();
            sb.append(conditions.isEmpty() ? " : " : ", ")
              .append("le total ").append(agg.getOperator().getLabel()).append(' ')
              .append(trimNumber(agg.getValue())).append(' ')
              .append(agg.getMetric().getUnitLabel());
        }

        sb.append(" (").append(dsl.getSeverity().getLabel())
          .append(", poids ").append(dsl.getWeight()).append(')');
        return sb.toString();
    }

    private String describeCondition(DslCondition condition) {
        String label = catalog.find(condition.getField())
                .map(DslField::getLabel)
                .orElse(condition.getField());
        String values = String.join(" et ", condition.effectiveValues());
        return label + " " + condition.getOperator().getLabel() + " " + values;
    }

    private static String trimNumber(Double value) {
        if (value == null) {
            return "?";
        }
        return value == Math.floor(value) ? String.valueOf(value.intValue()) : String.valueOf(value);
    }
}
