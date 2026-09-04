package tn.wtm.school.tenant.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.tenant.entity.Subscription;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.SubscriptionStatus;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.repository.SubscriptionRepository;
import tn.wtm.school.tenant.repository.TenantRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Expire quotidiennement les abonnements dépassés et suspend l'établissement
 * correspondant — évite qu'un établissement reste actif indéfiniment sans
 * renouvellement (pas de super admin humain requis pour ce cas courant).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJob {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    @Scheduled(cron = "${tenant.subscription.expiry-cron:0 0 1 * * *}")
    @Transactional
    public void expireOverdueSubscriptions() {
        List<Subscription> overdue = subscriptionRepository
                .findAllByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, LocalDate.now());

        for (Subscription subscription : overdue) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);

            Tenant tenant = subscription.getTenant();
            if (tenant.getStatus() != TenantStatus.SUSPENDED) {
                tenant.setStatus(TenantStatus.SUSPENDED);
                tenantRepository.save(tenant);
            }

            log.info("[SubscriptionExpiryJob] Abonnement expiré, établissement suspendu. tenantId={} endDate={}",
                    tenant.getTenantId(), subscription.getEndDate());
        }
    }
}
