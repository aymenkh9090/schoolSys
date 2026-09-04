package tn.wtm.school.tenant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.tenant.enums.TenantPlan;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePlanRequest {

    @NotNull(message = "Plan est obligatoire")
    private TenantPlan plan;
}
