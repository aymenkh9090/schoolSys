package tn.wtm.school.planning.constraints.dsl;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslAggregateMetric;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.solver.domain.Lesson;

import java.util.function.Predicate;

/**
 * Règle DSL compilée, prête à être évaluée par Timefold.
 *
 * <p>Cet objet est un <b>fait du problème</b> : il est ajouté à
 * {@link tn.wtm.school.planning.solver.domain.TimetableSolution#getCustomConstraints()}
 * au moment de construire le problème, et les flux de contraintes le joignent
 * pour appliquer la règle. Sa présence active la règle, son absence la désactive
 * — exactement le mécanisme déjà employé par
 * {@link tn.wtm.school.planning.solver.constraint.ActiveConstraintParam}.</p>
 *
 * <p>Il est immuable et son prédicat est pur (aucun accès base, aucun état
 * mutable) : Timefold peut le réévaluer des millions de fois sans risque de
 * corruption de score, et l'égalité porte sur le seul {@code code} pour que
 * l'objet reste utilisable comme clé de regroupement.</p>
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CompiledConstraint {

    /** Identifiant en base, utile pour remonter la règle fautive dans l'explication. */
    private final Long id;

    /** Code stable de la règle, unique par tenant. Sert de clé d'égalité. */
    @EqualsAndHashCode.Include
    private final String code;

    /** Libellé lisible, affiché dans le panneau d'explication du score. */
    private final String name;

    private final DslScope    scope;
    private final DslAction   action;
    private final DslSeverity severity;

    /** Multiplicateur du score unitaire, déjà borné par le validateur. */
    private final int weight;

    /** Conditions compilées. Toujours non nul — une règle sans condition matche tout. */
    private final Predicate<Lesson> filter;

    // ── partie agrégée (null pour une portée LESSON) ──────────────────────────

    private final DslAggregateMetric metric;
    private final DslOperator        aggregateOperator;

    /**
     * Seuil converti dans l'unité interne du solveur (demi-heures pour
     * TOTAL_HOURS, créneaux pour TOTAL_SLOTS, séances pour LESSON_COUNT).
     * La conversion est faite à la compilation, pas à chaque évaluation.
     */
    private final int limitUnits;

    /** Résumé en français, généré à la compilation pour l'écran de confirmation. */
    private final String summary;

    // ── évaluation ────────────────────────────────────────────────────────────

    public boolean isAggregate() {
        return scope != null && scope.isAggregate();
    }

    /** True quand la séance satisfait toutes les conditions de la règle. */
    public boolean matches(Lesson lesson) {
        return filter != null && filter.test(lesson);
    }

    /**
     * Clé de regroupement pour une portée agrégée.
     * Renvoie {@code null} quand la séance n'a pas l'information nécessaire —
     * Timefold écarte alors la ligne du regroupement.
     */
    public String groupKey(Lesson lesson) {
        if (lesson.getTimeSlot() == null) {
            return null;
        }
        String day = lesson.getTimeSlot().getDay() == null ? "?" : lesson.getTimeSlot().getDay().name();
        return switch (scope) {
            case TEACHER_DAY  -> teacherKey(lesson) + "|" + day;
            case TEACHER_WEEK -> teacherKey(lesson);
            case CLASS_DAY    -> nullSafe(lesson.getStudentClassName()) + "|" + day;
            case CLASS_WEEK   -> nullSafe(lesson.getStudentClassName());
            case ROOM_DAY     -> roomKey(lesson) + "|" + day;
            case LESSON       -> null;
        };
    }

    /** Contribution de cette séance au cumul du groupe, dans l'unité interne. */
    public int metricUnits(Lesson lesson) {
        if (metric == null) {
            return 0;
        }
        return switch (metric) {
            case TOTAL_HOURS, TOTAL_SLOTS -> Math.max(1, lesson.getDurationSlots());
            case LESSON_COUNT             -> 1;
        };
    }

    /** True quand le cumul du groupe franchit le seuil de la règle. */
    public boolean breaches(int totalUnits) {
        if (aggregateOperator == null) {
            return false;
        }
        return switch (aggregateOperator) {
            case GREATER_THAN          -> totalUnits >  limitUnits;
            case GREATER_THAN_OR_EQUAL -> totalUnits >= limitUnits;
            case LESS_THAN             -> totalUnits <  limitUnits;
            case LESS_THAN_OR_EQUAL    -> totalUnits <= limitUnits;
            case EQUALS                -> totalUnits == limitUnits;
            case NOT_EQUALS            -> totalUnits != limitUnits;
            default                    -> false;
        };
    }

    /**
     * Ampleur du dépassement, en unités internes, minorée à 1.
     *
     * <p>Pénaliser proportionnellement plutôt que forfaitairement donne au solveur
     * un gradient : passer de 8h à 7h améliore le score même si la règle reste
     * violée, ce qui l'oriente vers la sortie au lieu de le laisser sur un plateau.</p>
     */
    public int overshoot(int totalUnits) {
        int delta = switch (aggregateOperator) {
            case GREATER_THAN, GREATER_THAN_OR_EQUAL -> totalUnits - limitUnits;
            case LESS_THAN, LESS_THAN_OR_EQUAL       -> limitUnits - totalUnits;
            default                                   -> 1;
        };
        return Math.max(1, delta);
    }

    /** Pénalité (ou récompense) totale pour une occurrence donnée. */
    public int penaltyFor(int overshoot) {
        return Math.max(1, weight) * Math.max(1, overshoot);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String teacherKey(Lesson lesson) {
        return lesson.getTeacher() == null || lesson.getTeacher().getId() == null
                ? "?"
                : String.valueOf(lesson.getTeacher().getId());
    }

    private static String roomKey(Lesson lesson) {
        return lesson.getRoom() == null || lesson.getRoom().getId() == null
                ? "?"
                : String.valueOf(lesson.getRoom().getId());
    }

    private static String nullSafe(String value) {
        return value == null ? "?" : value;
    }

    @Override
    public String toString() {
        return "[" + code + "] " + (summary != null ? summary : name);
    }
}
