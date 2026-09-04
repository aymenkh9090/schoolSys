package tn.wtm.school.tenant.dto;

import lombok.*;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantResponse {

    private Long id;
    private String code;
    private String name;
    private EtablissementType type;
    private String address;
    private String phone;
    private String logo;
    private Boolean active;
    private TenantStatus status;
    private TenantPlan plan;

    /** Credentials de l'admin : présents UNIQUEMENT dans la réponse de création (POST). */
    private String adminEmail;
    private String adminUsername;
    private String adminTempPassword;
}
