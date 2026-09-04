package tn.wtm.school.tenant.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.tenant.dto.ChangePlanRequest;
import tn.wtm.school.tenant.dto.CreateSubscriptionRequest;
import tn.wtm.school.tenant.dto.RenewSubscriptionRequest;
import tn.wtm.school.tenant.dto.SubscriptionOverviewResponse;
import tn.wtm.school.tenant.dto.SubscriptionResponse;

public interface SubscriptionService {

    SubscriptionResponse createSubscription(CreateSubscriptionRequest request);

    SubscriptionResponse renewSubscription(Long tenantId, RenewSubscriptionRequest request);

    SubscriptionResponse changePlan(Long tenantId, ChangePlanRequest request);

    SubscriptionResponse cancelSubscription(Long tenantId);

    SubscriptionResponse getCurrent(Long tenantId);

    Page<SubscriptionResponse> getHistory(Long tenantId, Pageable pageable);

    Page<SubscriptionOverviewResponse> listOverview(Pageable pageable);
}
