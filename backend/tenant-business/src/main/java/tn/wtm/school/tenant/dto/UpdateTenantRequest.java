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


    @Size(max = 150, message = "Le nom de l'établissement ne doit pas dépasser 150 caractères.")
    private String name;

    private EtablissementType type;

    private TenantStatus status;

    private TenantPlan plan;

    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères.")
    private String address;

    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères.")
    private String phone;

    private String logo;

    private Boolean active;




}
