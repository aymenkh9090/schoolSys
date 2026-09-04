package tn.wtm.school.planning.constraints.dto.response;

import lombok.*;
import tn.wtm.school.planning.constraints.enums.ConstraintCategory;
import tn.wtm.school.planning.constraints.enums.ConstraintType;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConstraintSettingResponse {

    private Long idConstraintSetting;
    private Long profileId;
    private Long definitionId;
    private String constraintCode;
    private String constraintName;
    private String description;
    private ConstraintCategory category;
    private ConstraintType type;
    private Boolean enabled;
    private ImportanceLevel importance;
    private Integer weight;
    private String parametersJson;
}
