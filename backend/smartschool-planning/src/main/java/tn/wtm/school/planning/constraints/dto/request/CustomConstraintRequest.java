package tn.wtm.school.planning.constraints.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.enums.ConstraintSource;

/**
 * Création ou mise à jour d'une contrainte personnalisée.
 *
 * <p>Le DSL est reçu en objet structuré, pas en chaîne : Jackson refuse d'emblée
 * une portée ou un opérateur inconnus, et le client — interface ou assistant IA —
 * reçoit une erreur de format avant même la validation métier.</p>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CustomConstraintRequest {

    /** Profil auquel rattacher la règle. Obligatoire : une règle vaut pour un profil. */
    @NotNull(message = "Le profil de contraintes est obligatoire")
    private Long constraintProfileId;

    @NotBlank(message = "Le code de la règle est obligatoire")
    @Size(max = 100, message = "Le code ne peut pas dépasser 100 caractères")
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Le code ne peut contenir que des majuscules, chiffres et tirets bas")
    private String code;

    @NotBlank(message = "Le nom de la règle est obligatoire")
    @Size(max = 180, message = "Le nom ne peut pas dépasser 180 caractères")
    private String name;

    private String description;

    @NotNull(message = "La définition DSL est obligatoire")
    private ConstraintDsl dsl;

    private Boolean enabled;

    /**
     * Renseigné par l'assistant pour tracer l'origine. Ignoré côté serveur si
     * absent — une règle sans origine déclarée est considérée comme saisie
     * manuellement.
     */
    private ConstraintSource source;

    /** Phrase d'origine, quand la règle vient d'une demande en langage naturel. */
    private String naturalLanguageRequest;
}
