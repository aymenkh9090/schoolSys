package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeachingAssignmentRequest {

    @NotNull(message = "L'année scolaire est obligatoire")
    Long schoolYearId;

    @NotNull(message = "L'enseignant est obligatoire")
    Long teacherId;

    @NotNull(message = "La classe est obligatoire")
    Long classGroupId;

    @NotNull(message = "Le subjectLevel est obligatoire")
    Long subjectLevelId;

    @NotNull(message = "Le type de séance est obligatoire")
    Long subjectSessionTypeId;

    @Min(value = 1,   message = "La priorité doit être au moins 1")
    @Max(value = 100, message = "La priorité ne peut pas dépasser 100")
    Integer priority;

    Boolean isActive;


}
