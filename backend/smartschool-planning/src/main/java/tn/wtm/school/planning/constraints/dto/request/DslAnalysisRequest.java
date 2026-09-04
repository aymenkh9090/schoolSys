package tn.wtm.school.planning.constraints.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;

/**
 * Demande d'analyse d'une règle <b>candidate</b> : validation, estimation de
 * l'impact et détection de conflit avec les données existantes, sans rien
 * enregistrer.
 *
 * <p>C'est l'appel que fait l'écran de confirmation — et l'assistant IA avant de
 * proposer quoi que ce soit à l'utilisateur.</p>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DslAnalysisRequest {

    @NotNull(message = "La définition DSL est obligatoire")
    private ConstraintDsl dsl;

    /**
     * Année scolaire sur laquelle confronter la règle aux données réelles.
     * Sans elle, seule la validation syntaxique et sémantique est effectuée.
     */
    private Long schoolYearId;

    /** Profil de contraintes servant de contexte. Optionnel : le profil actif sinon. */
    private Long constraintProfileId;
}
