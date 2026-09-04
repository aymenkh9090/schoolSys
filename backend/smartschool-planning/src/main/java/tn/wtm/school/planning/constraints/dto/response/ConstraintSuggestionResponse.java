package tn.wtm.school.planning.constraints.dto.response;

import lombok.*;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;

import java.util.List;

/**
 * Proposition de règle issue de l'analyse d'un emploi du temps déjà généré.
 *
 * <p>Chaque proposition arrive avec ses <b>preuves</b> : les cas observés qui la
 * motivent. C'est ce qui permet à l'utilisateur de trancher en connaissance de
 * cause plutôt que d'accepter un conseil sur parole — et c'est aussi ce qui
 * empêche l'assistant de justifier la proposition par des chiffres inventés,
 * puisqu'il n'a qu'à relayer ceux-ci.</p>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConstraintSuggestionResponse {

    /** Job d'où proviennent les observations. */
    private Long jobId;

    private List<Suggestion> suggestions;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Suggestion {

        /** Code proposé pour la règle, déjà au bon format. */
        private String code;

        private String title;

        /** Ce qui a été observé, chiffré. */
        private String observation;

        /** Pourquoi la règle est utile, en français simple. */
        private String rationale;

        /** Cas concrets observés (enseignants, classes, jours). */
        private List<String> evidence;

        /** Nombre de cas observés — l'ampleur du problème. */
        private int occurrences;

        /** La règle prête à être analysée puis enregistrée après confirmation. */
        private ConstraintDsl dsl;
    }
}
