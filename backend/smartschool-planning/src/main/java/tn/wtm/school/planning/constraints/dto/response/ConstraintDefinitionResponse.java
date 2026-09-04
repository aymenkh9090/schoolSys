package tn.wtm.school.planning.constraints.dto.response;

import lombok.*;
import tn.wtm.school.planning.constraints.enums.ConstraintCategory;
import tn.wtm.school.planning.constraints.enums.ConstraintType;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConstraintDefinitionResponse {

    private Long idConstraintDefinition;
    private String code;
    private String name;
    private String description;
    private ConstraintCategory category;
    private ConstraintType type;
    private ImportanceLevel defaultImportance;
    private Boolean defaultEnabled;
    private String parameterSchema;
}
