package tn.wtm.school.planning.constraints.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConstraintProfileRequest {

    @NotBlank(message = "Le nom du profil est obligatoire")
    @Size(max = 120, message = "Le nom ne peut pas depasser 120 caracteres")
    private String name;

    private Long academicYearId;

    private Boolean active;
}
