package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherRequest {


    @NotBlank(message = "Le code enseignant est obligatoire")
    @Size(max = 15, message = "Le code ne doit pas dépasser 15 caractères")
    String codeEnseignant;

    @NotBlank(message = "Le numéro d'identité est obligatoire")
    @Size(max = 15, message = "Le numéro d'identité ne doit pas dépasser 15 caractères")
    String numIdentite;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 64, message = "Le nom ne doit pas dépasser 64 caractères")
    String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 64, message = "Le prénom ne doit pas dépasser 64 caractères")
    String prenom;

    @Email(message = "L'email doit être valide")
    @Size(max = 100, message = "L'email ne doit pas dépasser 100 caractères")
    String email;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères")
    @Pattern(
            regexp = "^[+]?[0-9\\s\\-]{7,20}$",
            message = "Format téléphone invalide"
    )
    String telephone;

    @Min(value = 1,  message = "Le max heures/semaine doit être au moins 1")
    @Max(value = 40, message = "Le max heures/semaine ne peut pas dépasser 40")
    Integer maxHeuresSemaine;

    @Min(value = 1, message = "Le max heures/jour doit être au moins 1")
    @Max(value = 10, message = "Le max heures/jour ne peut pas dépasser 10")
    Integer maxHeuresJour;

    @Min(value = 0, message = "Le min heures/jour doit être au moins 0")
    @Max(value = 10, message = "Le min heures/jour ne peut pas dépasser 10")
    Integer minHeuresJour;

    Boolean estEnPoste;
    String photo;

    @Size(max = 50, message = "La spécialité ne doit pas dépasser 50 caractères")
    String specialite;


        @AssertTrue(message = "minHeuresJour doit être inférieur ou égal à maxHeuresJour")
        public boolean isHeuresJourValid() {
            if (minHeuresJour == null || maxHeuresJour == null) return true;
            return minHeuresJour <= maxHeuresJour;
        }






    }
