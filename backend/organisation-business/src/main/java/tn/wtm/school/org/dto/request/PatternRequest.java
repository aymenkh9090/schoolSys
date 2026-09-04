package tn.wtm.school.org.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import tn.wtm.school.org.enums.PatternType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatternRequest {

    @NotBlank(message = "Le nom du pattern est obligatoire")
    @Size(max = 1000, message = "Le nom ne doit pas dépasser 1000 caractères")
    String name;

    @NotNull(message = "Le total des heures est obligatoire")
    @DecimalMin(value = "0.5", message = "Le total des heures doit être au moins 0.5h")
    @DecimalMax(value = "40.0", message = "Le total des heures ne peut pas dépasser 40h")
    Double totalHours;

    @NotNull(message = "Le nombre de séances est obligatoire")
    @Min(value = 1,  message = "Le nombre de séances doit être au moins 1")
    @Max(value = 20, message = "Le nombre de séances ne peut pas dépasser 20")
    Integer sessionCount;

    @Pattern(
            regexp = "^(\\d+(\\.\\d+)?\\+)*\\d+(\\.\\d+)?$",
            message = "Format de répartition invalide. Exemples valides: 2+2+1, 1.5+1.5"
    )
    String repartition;

    PatternType patternType;

    @NotNull(message = "Le subjectLevel est obligatoire")
    Long subjectLevelId;

    /** Null = pattern par défaut (toutes années). Renseigner pour créer un pattern spécifique à une année. */
    Long schoolYearId;

    @NotEmpty(message = "Au moins un détail de pattern est requis")
    @Valid
    List<PatternDetailRequest> details;


        // totalHours doit correspondre à la somme des durées des details
        @AssertTrue(message = "La somme des durées des détails doit correspondre au totalHours")
        public boolean isTotalHoursConsistent() {
            if (details == null || details.isEmpty() || totalHours == null) return true;
            double sum = details.stream()
                    .filter(d -> d.getDuration() != null)
                    .mapToDouble(PatternDetailRequest::getDuration)
                    .sum();
            return Math.abs(sum - totalHours) < 0.01;
        }

        // sessionCount doit correspondre au nombre de details
        @AssertTrue(message = "Le sessionCount doit correspondre au nombre de détails")
        public boolean isSessionCountConsistent() {
            if (details == null || sessionCount == null) return true;
            return details.size() == sessionCount;
        }

    }
