package tn.wtm.school.common.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import tn.wtm.school.common.base.TenantEntity;

import java.util.List;

@NoRepositoryBean
public interface TenantAwareRepository<T extends TenantEntity, ID> extends JpaRepository<T, ID> {

    List<T> findByTenantId(String tenantId);
    List<T> findAllByTenantId(String tenantId);
    Page<T> findByTenantId(String tenantId, Pageable pageable);
    long countByTenantId(String tenantId);
    Boolean existsByTenantId(String tenantId);
}
