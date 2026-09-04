package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import tn.wtm.school.org.enums.SessionType;



@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectSessionTypeRequest {

    @NotNull(message = "Le subjectLevel est obligatoire")
    Long subjectLevelId;

    @NotNull(message = "Le type de séance est obligatoire")
    SessionType type;

    @NotNull(message = "La durée est obligatoire")
    @DecimalMin(value = "0.5", message = "La durée doit être au moins 0.5h")
    @DecimalMax(value = "4.0", message = "La durée ne peut pas dépasser 4h")
    Double duration;

    Boolean requiresSplit;

    @Min(value = 2, message = "Le nombre de groupes doit être au moins 2")
    @Max(value = 4, message = "Le nombre de groupes ne peut pas dépasser 4")
    Integer groupCount;

        // Si requiresSplit=true → groupCount obligatoire
        @AssertTrue(message = "groupCount est obligatoire quand requiresSplit est true")
        public boolean isGroupCountValidWhenSplit() {
            if (Boolean.TRUE.equals(requiresSplit)) {
                return groupCount != null && groupCount >= 2;
            }
            return true;
        }

    }
