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


    @NotBlank(message = "Le code de l'établissement est obligatoire.")
    @Size(max = 50, message = "Le code de l'établissement ne doit pas dépasser 50 caractères.")
    private String code;

    @NotBlank(message = "Le nom de l'établissement est obligatoire.")
    @Size(max = 150, message = "Le nom de l'établissement ne doit pas dépasser 150 caractères.")
    private String name;

    @NotNull(message = "Le type d'établissement est obligatoire.")
    private EtablissementType type;

    @Builder.Default
    private TenantPlan plan = TenantPlan.FREE;

    @NotBlank(message = "L'adresse de l'établissement est obligatoire.")
    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères.")
    private String address;

    @NotBlank(message = "Le téléphone de l'établissement est obligatoire.")
    @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères.")
    private String phone;

    private String logo;

    /** Email de l'administrateur de l'établissement (utilisé pour créer le compte Keycloak). */
    @NotBlank(message = "L'email de l'administrateur est obligatoire.")
    @Email(message = "L'email de l'administrateur est invalide.")
    @Size(max = 150)
    private String emailAdmin;

    /** Prénom et nom de l'administrateur (ex: "Mohamed Ben Ali"). */
    @NotBlank(message = "Le nom complet de l'administrateur est obligatoire.")
    @Size(max = 100)
    private String nomCompletAdmin;
}
