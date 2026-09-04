package tn.wtm.school.tenant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.tenant.dto.ChangePlanRequest;
import tn.wtm.school.tenant.dto.CreateSubscriptionRequest;
import tn.wtm.school.tenant.dto.RenewSubscriptionRequest;
import tn.wtm.school.tenant.dto.SubscriptionResponse;
import tn.wtm.school.tenant.entity.Subscription;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.BillingCycle;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.SubscriptionStatus;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.mapper.SubscriptionMapper;
import tn.wtm.school.tenant.mapper.SubscriptionMapperImpl;
import tn.wtm.school.tenant.repository.SubscriptionRepository;
import tn.wtm.school.tenant.repository.TenantRepository;
import tn.wtm.school.tenant.service.impl.SubscriptionServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private TenantRepository tenantRepository;

    private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        SubscriptionMapper mapper = new SubscriptionMapperImpl();
        service = new SubscriptionServiceImpl(subscriptionRepository, tenantRepository, mapper);
    }

    @Test
    void createsSubscriptionAndActivatesTenant() {
        Tenant tenant = tenant(1L, TenantStatus.SUSPENDED, TenantPlan.FREE);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(1L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = service.createSubscription(CreateSubscriptionRequest.builder()
                .tenantId(1L)
                .plan(TenantPlan.STANDARD)
                .billingCycle(BillingCycle.MONTHLY)
                .price(BigDecimal.valueOf(99))
                .build());

        assertThat(response.getPlan()).isEqualTo(TenantPlan.STANDARD);
        assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.getEndDate()).isEqualTo(response.getStartDate().plusMonths(1));
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getPlan()).isEqualTo(TenantPlan.STANDARD);
    }

    @Test
    void rejectsCreationWhenActiveSubscriptionAlreadyExists() {
        Tenant tenant = tenant(1L, TenantStatus.ACTIVE, TenantPlan.STANDARD);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(1L))
                .thenReturn(Optional.of(subscription(tenant, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(10))));

        assertThatThrownBy(() -> service.createSubscription(CreateSubscriptionRequest.builder()
                .tenantId(1L)
                .plan(TenantPlan.PREMIUM)
                .billingCycle(BillingCycle.YEARLY)
                .build()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void renewsSubscriptionFromCurrentEndDateAndReactivatesTenant() {
        Tenant tenant = tenant(1L, TenantStatus.SUSPENDED, TenantPlan.STANDARD);
        Subscription current = subscription(tenant, SubscriptionStatus.EXPIRED, LocalDate.now().minusDays(5));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(1L)).thenReturn(Optional.of(current));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = service.renewSubscription(1L,
                RenewSubscriptionRequest.builder().billingCycle(BillingCycle.MONTHLY).build());

        assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(response.getEndDate()).isEqualTo(LocalDate.now().plusMonths(1));
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void changesPlanWithoutTouchingDates() {
        Tenant tenant = tenant(1L, TenantStatus.ACTIVE, TenantPlan.FREE);
        Subscription current = subscription(tenant, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(10));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(1L)).thenReturn(Optional.of(current));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = service.changePlan(1L,
                ChangePlanRequest.builder().plan(TenantPlan.PREMIUM).build());

        assertThat(response.getPlan()).isEqualTo(TenantPlan.PREMIUM);
        assertThat(response.getEndDate()).isEqualTo(current.getEndDate());
        assertThat(tenant.getPlan()).isEqualTo(TenantPlan.PREMIUM);
    }

    @Test
    void cancelsSubscriptionAndSuspendsTenant() {
        Tenant tenant = tenant(1L, TenantStatus.ACTIVE, TenantPlan.STANDARD);
        Subscription current = subscription(tenant, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(10));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(1L)).thenReturn(Optional.of(current));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = service.cancelSubscription(1L);

        assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void failsWhenNoCurrentSubscriptionExists() {
        when(subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Tenant tenant(Long id, TenantStatus status, TenantPlan plan) {
        return Tenant.builder()
                .tenantId(id)
                .code("IBN")
                .name("College Ibn Khaldoun")
                .etablismentType(EtablissementType.COLLEGE)
                .address("Rue de l'ecole")
                .phone("+216 71 000 000")
                .status(status)
                .plan(plan)
                .build();
    }

    private Subscription subscription(Tenant tenant, SubscriptionStatus status, LocalDate endDate) {
        return Subscription.builder()
                .id(1L)
                .tenant(tenant)
                .plan(tenant.getPlan())
                .billingCycle(BillingCycle.MONTHLY)
                .status(status)
                .startDate(endDate.minusMonths(1))
                .endDate(endDate)
                .build();
    }
}
