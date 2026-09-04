package tn.wtm.school.planning.constraints.dsl.enums;

/**
 * Grandeur cumulée par les portées agrégées.
 *
 * <p>Le solveur raisonne en créneaux de 30 min. {@link #TOTAL_HOURS} est donc
 * converti en demi-heures à la compilation : un seuil « 3 heures » devient
 * un seuil de 6 unités, comparé à la somme des {@code durationSlots}. Cette
 * conversion est faite une fois, pas à chaque évaluation.</p>
 */
public enum DslAggregateMetric {

    /** Somme des durées, exprimée en heures dans le DSL. */
    TOTAL_HOURS(2, "heures"),

    /** Somme des durées, exprimée en créneaux de 30 min. */
    TOTAL_SLOTS(1, "créneaux"),

    /** Nombre de séances, quelle que soit leur durée. */
    LESSON_COUNT(1, "séances");

    private final int    unitsPerValue;
    private final String unitLabel;

    DslAggregateMetric(int unitsPerValue, String unitLabel) {
        this.unitsPerValue = unitsPerValue;
        this.unitLabel     = unitLabel;
    }

    /** Facteur de conversion entre la valeur du DSL et l'unité interne du solveur. */
    public int unitsPerValue() {
        return unitsPerValue;
    }

    public String getUnitLabel() {
        return unitLabel;
    }
}
