package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EleveRequest {

    @NotBlank(message = "Le code élève est obligatoire")
    @Size(max = 20, message = "Le code élève ne doit pas dépasser 20 caractères")
    private String codeEleve;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 64)
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 64)
    private String prenom;

    @Size(max = 20)
    private String numIdentite;

    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String telephone;

    @NotNull(message = "La classe est obligatoire")
    private Long classeId;
}
