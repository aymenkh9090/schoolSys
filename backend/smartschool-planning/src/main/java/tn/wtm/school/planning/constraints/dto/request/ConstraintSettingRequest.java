package tn.wtm.school.planning.constraints.dto.request;

import jakarta.validation.constraints.Min;
import lombok.*;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConstraintSettingRequest {

    private Long constraintDefinitionId;
    private Boolean enabled;
    private ImportanceLevel importance;

    @Min(value = 0, message = "Le poids doit etre positif ou nul")
    private Integer weight;

    private String parametersJson;
}
