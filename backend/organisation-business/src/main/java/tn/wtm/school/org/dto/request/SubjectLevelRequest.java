package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectLevelRequest {

    @NotNull(message = "La matière est obligatoire")
    Long subjectId;

    @NotNull(message = "Le niveau est obligatoire")
    Long levelId;

    @NotNull(message = "Le nombre d'heures par semaine est obligatoire")
    @DecimalMin(value = "0.5", message = "Le nombre d'heures doit être au moins 0.5h")
    @DecimalMax(value = "40.0", message = "Le nombre d'heures ne peut pas dépasser 40h")
    @Positive
    Double heuresSemaine;

    @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
    String description;

    Boolean estObligatoire;

    @DecimalMin(value = "0.5", message = "Le coefficient doit être au moins 0.5")
    @DecimalMax(value = "10.0", message = "Le coefficient ne peut pas dépasser 10")
    Double coefficient;

    @Min(value = 1, message = "Le max heures consécutives doit être au moins 1")
    @Max(value = 6,  message = "Le max heures consécutives ne peut pas dépasser 6")
    Integer maxHeuresConsecutives;

}
