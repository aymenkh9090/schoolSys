package tn.wtm.school.tenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.tenant.enums.BillingCycle;
import tn.wtm.school.tenant.enums.TenantPlan;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSubscriptionRequest {

    @NotNull(message = "Etablissement est obligatoire")
    private Long tenantId;

    @NotNull(message = "Plan est obligatoire")
    private TenantPlan plan;

    @NotNull(message = "Cycle de facturation est obligatoire")
    private BillingCycle billingCycle;

    /** Par défaut aujourd'hui si non fourni. */
    private LocalDate startDate;

    @DecimalMin(value = "0", message = "Le prix ne peut pas être négatif")
    private BigDecimal price;

    private String notes;
}
