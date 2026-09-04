package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchoolUserRequest {

    @Size(min = 2, max = 100, message = "Le nom complet doit contenir entre 2 et 100 caractères")
    private String nomComplet;

    private String matiere;

    @Pattern(
            regexp = "^(\\+216|0)[2-9](\\d{7})$",
            message = "Format téléphone tunisien invalide"
    )
    private String telephone;
}
