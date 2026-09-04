package tn.wtm.school.org.repository;

import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.enums.UserRole;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import tn.wtm.school.common.metrics.TenantStatusCount;

@Repository
public interface SchoolUserRepository extends TenantAwareRepository<SchoolUser, Long> {

    List<SchoolUser> findByTenantIdAndActifTrue(String tenantId);

    List<SchoolUser> findByTenantIdAndIdIn(String tenantId, java.util.Collection<Long> ids);

    List<SchoolUser> findByTenantIdAndRole(String tenantId, UserRole role);

    List<SchoolUser> findByTenantIdAndRoleAndActifTrue(String tenantId, UserRole role);

    Optional<SchoolUser> findByTenantIdAndEmail(String tenantId, String email);

    boolean existsByTenantIdAndEmail(String tenantId, String email);

    boolean existsByTenantIdAndTeacher_IdEnseignant(String tenantId, Long teacherId);

    Optional<SchoolUser> findByKeycloakUserId(String keycloakUserId);

    long countByTenantIdAndRole(String tenantId, UserRole role);

    long countByTenantIdAndActifTrue(String tenantId);

    // ── Métriques plateforme ───────────────────────────────────────────────
    // Volontairement non filtrées par tenant : appelées par le collecteur de
    // métriques, hors contexte de requête, pour balayer tous les établissements
    // en une passe. Le filtre Hibernate n'est pas actif faute de TenantContext.

    @Query("""
           SELECT new tn.wtm.school.common.metrics.TenantStatusCount(u.tenantId, CAST(u.role AS string), COUNT(u))
           FROM SchoolUser u
           WHERE u.actif = true
           GROUP BY u.tenantId, u.role
           """)
    List<TenantStatusCount> countActiveGroupedByTenantAndRole();
}
