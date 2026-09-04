package tn.wtm.school.planning.constraints.dto.response;

import lombok.*;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.enums.ConstraintSource;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CustomConstraintResponse {

    private Long idCustomConstraint;
    private Long constraintProfileId;

    private String code;
    private String name;
    private String description;

    /** La règle, renvoyée structurée pour que l'interface puisse la ré-éditer. */
    private ConstraintDsl dsl;

    private DslScope    scope;
    private DslSeverity severity;
    private Integer     weight;
    private Boolean     enabled;

    private ConstraintSource source;
    private String           naturalLanguageRequest;

    /** Résumé français figé à l'enregistrement — ce que le moteur applique. */
    private String summary;
}
