package tn.wtm.school.tenant.dto;

import lombok.*;
import tn.wtm.school.tenant.enums.BillingCycle;
import tn.wtm.school.tenant.enums.SubscriptionStatus;
import tn.wtm.school.tenant.enums.TenantPlan;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {

    private Long id;
    private Long tenantId;
    private TenantPlan plan;
    private BillingCycle billingCycle;
    private SubscriptionStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal price;
    private String notes;
}
