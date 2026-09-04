package tn.wtm.school.tenant.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.tenant.entity.Subscription;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.BillingCycle;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.SubscriptionStatus;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.repository.SubscriptionRepository;
import tn.wtm.school.tenant.repository.TenantRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryJobTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private TenantRepository tenantRepository;

    private SubscriptionExpiryJob job;

    @BeforeEach
    void setUp() {
        job = new SubscriptionExpiryJob(subscriptionRepository, tenantRepository);
    }

    @Test
    void expiresOverdueSubscriptionsAndSuspendsTenant() {
        Tenant tenant = Tenant.builder()
                .tenantId(1L)
                .code("IBN")
                .name("College Ibn Khaldoun")
                .etablismentType(EtablissementType.COLLEGE)
                .address("Rue de l'ecole")
                .phone("+216 71 000 000")
                .status(TenantStatus.ACTIVE)
                .plan(TenantPlan.STANDARD)
                .build();
        Subscription overdue = Subscription.builder()
                .id(1L)
                .tenant(tenant)
                .plan(TenantPlan.STANDARD)
                .billingCycle(BillingCycle.MONTHLY)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusMonths(2))
                .endDate(LocalDate.now().minusDays(1))
                .build();

        when(subscriptionRepository.findAllByStatusAndEndDateBefore(eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(overdue));

        job.expireOverdueSubscriptions();

        assertThat(overdue.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        verify(subscriptionRepository).save(overdue);
        verify(tenantRepository).save(tenant);
    }
}
