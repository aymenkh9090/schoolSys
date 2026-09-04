package tn.wtm.school.tenant.dto;

import lombok.*;
import tn.wtm.school.tenant.enums.BillingCycle;
import tn.wtm.school.tenant.enums.SubscriptionStatus;
import tn.wtm.school.tenant.enums.TenantPlan;

import java.time.LocalDate;

/** Une ligne de la table "Abonnements" : établissement + son abonnement courant (s'il existe). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionOverviewResponse {

    private Long tenantId;
    private String tenantName;
    private String tenantCode;

    /** Null si l'établissement n'a jamais eu d'abonnement. */
    private Long subscriptionId;
    private TenantPlan plan;
    private BillingCycle billingCycle;
    private SubscriptionStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long daysRemaining;
}
