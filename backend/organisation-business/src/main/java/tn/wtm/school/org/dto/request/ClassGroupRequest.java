package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import tn.wtm.school.org.enums.Specialite;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClassGroupRequest {

    @NotBlank(message = "Le code de la classe est obligatoire")
    @Size(max = 15, message = "Le code ne doit pas dépasser 15 caractères")
    @Pattern(
            regexp = "^[0-9]{1,2}[A-Z]{1,2}[0-9]{1,2}$",
            message = "Format invalide. Exemples valides: 7B1, 9B10, 1S2"
    )
    String code;

    @NotNull(message = "La spécialité est obligatoire")
    Specialite codeSpecialite;

    @Min(value = 1, message = "Le nombre d'élèves doit être au moins 1")
    @Max(value = 60, message = "Le nombre d'élèves ne peut pas dépasser 60")
    Integer nbEleve;

    Boolean estActif;

    @NotNull(message = "L'année scolaire est obligatoire")
    Long schoolYearId;

    @NotNull(message = "Le niveau est obligatoire")
    Long levelId;




}
