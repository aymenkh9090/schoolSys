package tn.wtm.school.tenant.dto;

import jakarta.validation.constraints.Size;
import lombok.*;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTenantRequest {


    @Size(max = 150, message = "Tenant name must not exceed 150 characters")
    private String name;

    private EtablissementType type;

    private TenantStatus status;

    private TenantPlan plan;

    @Size(max = 255, message = "Tenant address must not exceed 255 characters")
    private String address;

    @Size(max = 20, message = "Tenant phone must not exceed 20 characters")
    private String phone;

    private String logo;

    private Boolean active;




}
