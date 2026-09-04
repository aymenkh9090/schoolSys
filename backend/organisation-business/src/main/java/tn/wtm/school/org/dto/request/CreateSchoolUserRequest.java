package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.wtm.school.org.enums.UserRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSchoolUserRequest {

    /** Ignoré pour role=TEACHER (dérivé de la fiche enseignant). */
    @Email(message = "L'email doit être valide")
    private String email;

    /** Ignoré pour role=TEACHER (dérivé de la fiche enseignant). */
    @Size(min = 2, max = 100, message = "Le nom complet doit contenir entre 2 et 100 caractères")
    private String nomComplet;

    @NotNull(message = "Le rôle est obligatoire (SCHOOL_ADMIN, TEACHER, SURVEILLANT, PARENT, STUDENT)")
    private UserRole role;

    /** Fiche enseignant à lier — obligatoire pour role=TEACHER. */
    private Long teacherId;

    private String matiere;

    @Pattern(
            regexp = "^(\\+216|0)[2-9](\\d{7})$",
            message = "Le téléphone doit être au format tunisien : 0XXXXXXXX ou +216XXXXXXXX"
    )
    private String telephone;
}
