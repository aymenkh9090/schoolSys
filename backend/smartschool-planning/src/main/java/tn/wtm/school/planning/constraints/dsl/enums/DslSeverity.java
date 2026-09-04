package tn.wtm.school.planning.constraints.dsl.enums;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

/**
 * Niveau de score Timefold visé par la règle.
 *
 * <p>HARD = règle absolue : tant qu'elle est violée, l'emploi du temps est infaisable.
 * MEDIUM = règle réglementaire forte, SOFT = préférence d'établissement.</p>
 */
public enum DslSeverity {

    HARD(HardMediumSoftScore.ONE_HARD,   "Contrainte dure"),
    MEDIUM(HardMediumSoftScore.ONE_MEDIUM, "Contrainte moyenne"),
    SOFT(HardMediumSoftScore.ONE_SOFT,   "Préférence");

    private final HardMediumSoftScore unitScore;
    private final String              label;

    DslSeverity(HardMediumSoftScore unitScore, String label) {
        this.unitScore = unitScore;
        this.label     = label;
    }

    /** Score unitaire au bon niveau — multiplié ensuite par le poids de la règle. */
    public HardMediumSoftScore unitScore() {
        return unitScore;
    }

    public String getLabel() {
        return label;
    }
}
