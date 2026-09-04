package tn.wtm.school.tenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.tenant.enums.BillingCycle;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenewSubscriptionRequest {

    @NotNull(message = "Cycle de facturation est obligatoire")
    private BillingCycle billingCycle;

    @DecimalMin(value = "0", message = "Le prix ne peut pas être négatif")
    private BigDecimal price;
}
