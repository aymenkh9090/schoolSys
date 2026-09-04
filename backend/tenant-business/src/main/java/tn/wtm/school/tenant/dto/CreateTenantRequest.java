package tn.wtm.school.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTenantRequest {


    @NotBlank(message = "Etablissement code est obligatoire")
    @Size(max = 50, message = "Etablissement code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Etablissement name is required")
    @Size(max = 150, message = "Etablissement name must not exceed 150 characters")
    private String name;

    @NotNull(message = "Establishment type is required")
    private EtablissementType type;

    @Builder.Default
    private TenantPlan plan = TenantPlan.FREE;

    @NotBlank(message = "Etablissement address is required")
    @Size(max = 255, message = "Etablissement address must not exceed 255 characters")
    private String address;

    @NotBlank(message = "Etablissement phone is required")
    @Size(max = 20, message = "Etablissement phone must not exceed 20 characters")
    private String phone;

    private String logo;

    /** Email de l'administrateur de l'établissement (utilisé pour créer le compte Keycloak). */
    @NotBlank(message = "Email de l'administrateur est obligatoire")
    @Email(message = "Email de l'administrateur invalide")
    @Size(max = 150)
    private String emailAdmin;

    /** Prénom et nom de l'administrateur (ex: "Mohamed Ben Ali"). */
    @NotBlank(message = "Nom complet de l'administrateur est obligatoire")
    @Size(max = 100)
    private String nomCompletAdmin;
}
