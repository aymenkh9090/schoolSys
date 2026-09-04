package tn.wtm.school.tenant.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.tenant.dto.ChangePlanRequest;
import tn.wtm.school.tenant.dto.CreateSubscriptionRequest;
import tn.wtm.school.tenant.dto.RenewSubscriptionRequest;
import tn.wtm.school.tenant.dto.SubscriptionOverviewResponse;
import tn.wtm.school.tenant.dto.SubscriptionResponse;
import tn.wtm.school.tenant.entity.Subscription;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.BillingCycle;
import tn.wtm.school.tenant.enums.SubscriptionStatus;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.mapper.SubscriptionMapper;
import tn.wtm.school.tenant.repository.SubscriptionRepository;
import tn.wtm.school.tenant.repository.TenantRepository;
import tn.wtm.school.tenant.service.SubscriptionService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Etablissement avec ce ID " + request.getTenantId() + " n'existe pas"));

        subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(tenant.getTenantId())
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE && !s.getEndDate().isBefore(LocalDate.now()))
                .ifPresent(s -> {
                    throw new ConflictException(
                            "Un abonnement actif existe déjà pour cet établissement, utilisez le renouvellement");
                });

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(request.getPlan())
                .billingCycle(request.getBillingCycle())
                .status(SubscriptionStatus.ACTIVE)
                .startDate(startDate)
                .endDate(computeEndDate(startDate, request.getBillingCycle()))
                .price(request.getPrice())
                .notes(request.getNotes())
                .build();
        subscription = subscriptionRepository.save(subscription);

        tenant.setPlan(request.getPlan());
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            tenant.setStatus(TenantStatus.ACTIVE);
        }
        tenantRepository.save(tenant);

        log.info("[Subscription] Abonnement créé. tenantId={} plan={} endDate={}",
                tenant.getTenantId(), subscription.getPlan(), subscription.getEndDate());

        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional
    public SubscriptionResponse renewSubscription(Long tenantId, RenewSubscriptionRequest request) {
        Tenant tenant = findTenant(tenantId);
        Subscription current = findCurrentOrThrow(tenantId);

        LocalDate newStart = current.getEndDate().isBefore(LocalDate.now()) ? LocalDate.now() : current.getEndDate();
        Subscription renewed = Subscription.builder()
                .tenant(tenant)
                .plan(current.getPlan())
                .billingCycle(request.getBillingCycle())
                .status(SubscriptionStatus.ACTIVE)
                .startDate(newStart)
                .endDate(computeEndDate(newStart, request.getBillingCycle()))
                .price(request.getPrice() != null ? request.getPrice() : current.getPrice())
                .build();
        renewed = subscriptionRepository.save(renewed);

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
        }

        log.info("[Subscription] Abonnement renouvelé. tenantId={} nouvelleEcheance={}",
                tenantId, renewed.getEndDate());

        return subscriptionMapper.toResponse(renewed);
    }

    @Override
    @Transactional
    public SubscriptionResponse changePlan(Long tenantId, ChangePlanRequest request) {
        Tenant tenant = findTenant(tenantId);
        Subscription current = findCurrentOrThrow(tenantId);

        current.setPlan(request.getPlan());
        subscriptionRepository.save(current);

        tenant.setPlan(request.getPlan());
        tenantRepository.save(tenant);

        return subscriptionMapper.toResponse(current);
    }

    @Override
    @Transactional
    public SubscriptionResponse cancelSubscription(Long tenantId) {
        Tenant tenant = findTenant(tenantId);
        Subscription current = findCurrentOrThrow(tenantId);

        current.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(current);

        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        log.info("[Subscription] Abonnement annulé, établissement suspendu. tenantId={}", tenantId);

        return subscriptionMapper.toResponse(current);
    }

    @Override
    public SubscriptionResponse getCurrent(Long tenantId) {
        return subscriptionMapper.toResponse(findCurrentOrThrow(tenantId));
    }

    @Override
    public Page<SubscriptionResponse> getHistory(Long tenantId, Pageable pageable) {
        return subscriptionRepository.findAllByTenant_TenantIdOrderByStartDateDesc(tenantId, pageable)
                .map(subscriptionMapper::toResponse);
    }

    @Override
    public Page<SubscriptionOverviewResponse> listOverview(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(tenant -> {
            Optional<Subscription> current =
                    subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(tenant.getTenantId());

            SubscriptionOverviewResponse.SubscriptionOverviewResponseBuilder builder =
                    SubscriptionOverviewResponse.builder()
                            .tenantId(tenant.getTenantId())
                            .tenantName(tenant.getName())
                            .tenantCode(tenant.getCode());

            current.ifPresent(s -> builder
                    .subscriptionId(s.getId())
                    .plan(s.getPlan())
                    .billingCycle(s.getBillingCycle())
                    .status(s.getStatus())
                    .startDate(s.getStartDate())
                    .endDate(s.getEndDate())
                    .daysRemaining(ChronoUnit.DAYS.between(LocalDate.now(), s.getEndDate())));

            return builder.build();
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Etablissement avec ce ID " + tenantId + " n'existe pas"));
    }

    private Subscription findCurrentOrThrow(Long tenantId) {
        return subscriptionRepository.findFirstByTenant_TenantIdOrderByEndDateDesc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun abonnement pour l'établissement " + tenantId));
    }

    private LocalDate computeEndDate(LocalDate startDate, BillingCycle billingCycle) {
        return billingCycle == BillingCycle.YEARLY ? startDate.plusYears(1) : startDate.plusMonths(1);
    }
}
