package tn.wtm.school.tenant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.EtablissementType;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCode(String code);
    Optional<Tenant> findByName(String name);
    Optional<Tenant> findByCodeIgnoreCase(String code);
    Optional<Tenant> findByNameIgnoreCase(String name);
    boolean existsByCode(String code);
    boolean existsByName(String name);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndTenantIdNot(String name, Long tenantId);
    Long countByActiveTrue();
    Long countByActiveFalse();
    Page<Tenant> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT t.keycloakGroupId FROM Tenant t WHERE t.tenantId = :tenantId")
    Optional<String> findKeycloakGroupIdById(@Param("tenantId") Long tenantId);
}
