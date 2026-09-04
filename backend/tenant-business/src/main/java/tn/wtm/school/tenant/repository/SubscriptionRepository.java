package tn.wtm.school.tenant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.wtm.school.tenant.entity.Subscription;
import tn.wtm.school.tenant.enums.SubscriptionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByTenant_TenantIdOrderByEndDateDesc(Long tenantId);

    Page<Subscription> findAllByTenant_TenantIdOrderByStartDateDesc(Long tenantId, Pageable pageable);

    List<Subscription> findAllByStatusAndEndDateBefore(SubscriptionStatus status, LocalDate date);
}
