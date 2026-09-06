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

    /**
     * Verdict de la validation métier — indépendant du solveur et du profil.
     *
     * <p>Les trois listes ci-dessus disent ce que <em>les contraintes activées</em>
     * reprochent au planning. Celle-ci dit ce que le planning a de faux quoi qu'on
     * ait activé : une séance perdue, un volume horaire amputé, deux classes dans
     * la même salle. Les deux se lisent ensemble, et c'est bien pour cela qu'elles
     * voyagent dans la même réponse.
     */
    private List<BusinessFinding> businessValidation;

    /** True quand la validation métier ne formule aucun constat bloquant. */
    private boolean businessValid;

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

    /** Un constat de la validation métier, mis à plat pour l'interface. */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class BusinessFinding {
        /** {@code BLOQUANT} ou {@code AVERTISSEMENT} — seul le premier refuse le planning. */
        private String severity;
        /** Identifiant stable du contrôle, ex. {@code CONFLIT_SALLE}. */
        private String code;
        /** Ce qui est concerné : « 7A / MATH », « salle A1, lundi 08:00 ». */
        private String scope;
        /** Ce qui ne va pas, en une phrase. */
        private String message;
    }
}
