package tn.wtm.school.planning.solver.dto.response;

import lombok.*;

import java.util.List;

/**
 * Explication déterministe du score d'une solution : liste des contraintes
 * violées par niveau (hard/medium/soft), avec pour chacune un libellé lisible,
 * le nombre d'occurrences et quelques exemples concrets (quel enseignant,
 * quelle classe, quel créneau) — construits à partir des
 * {@code ConstraintMatch} de Timefold, pas d'une IA.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScoreExplanationResponse {

    private Long jobId;
    private String score;
    private boolean feasible;
    private List<ConstraintViolation> hardViolations;
    private List<ConstraintViolation> mediumViolations;
    private List<ConstraintViolation> softViolations;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ConstraintViolation {
        /** Nom technique de la contrainte (identifiant Timefold, ex. "Teacher conflict"). */
        private String constraintName;
        /** Libellé français destiné à l'affichage. */
        private String label;
        /** Score total infligé par cette contrainte, ex. "-3hard/0medium/0soft". */
        private String score;
        /** Nombre d'occurrences de cette violation dans la solution. */
        private int count;
        /** Exemples concrets (enseignant/classe/créneau concernés), limités en nombre. */
        private List<String> examples;
        /** Suggestion concrète et actionnable pour résoudre ce type de conflit. */
        private String suggestion;
    }
}
