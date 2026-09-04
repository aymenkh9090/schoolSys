package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LevelRequest {

    @NotBlank(message = "Le nom du niveau est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    String nom;

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 100, message = "Le code ne doit pas dépasser 100 caractères")
    String code;

    @NotBlank(message = "La description est obligatoire")
    @Size(max = 100, message = "La description ne doit pas dépasser 100 caractères")
    String description;

    Boolean estActif;










}
