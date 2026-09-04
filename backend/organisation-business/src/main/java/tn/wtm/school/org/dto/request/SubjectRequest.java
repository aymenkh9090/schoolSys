package tn.wtm.school.org.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import tn.wtm.school.org.enums.RoomType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectRequest {

    @NotBlank(message = "Le code matière est obligatoire")
    @Size(max = 10, message = "Le code matière ne doit pas dépasser 10 caractères")
    @Pattern(
            regexp = "^[A-Z0-9_]+$",
            message = "Le code matière doit être en majuscules sans espaces. Ex: MATH, ARABE, PHYS"
    )
    String codeMatiere;

    @Size(max = 64, message = "Le libellé ne doit pas dépasser 64 caractères")
    String libMatiere;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    String description;

    Boolean necessiteLab;
    Boolean necessiteSport;

    /** Type de salle requise par défaut pour cette matière (NORMALE si absent). */
    RoomType typeSalleRequise;

    @Pattern(
            regexp = "^#([A-Fa-f0-9]{6})$",
            message = "La couleur doit être au format hexadécimal. Ex: #FF5733"
    )
    String couleur;

    @Size(max = 10, message = "L'abréviation ne doit pas dépasser 10 caractères")
    String abreviation;

    Boolean estPrincipale;
    Boolean estEnseignee;






}
