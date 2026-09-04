package tn.wtm.school.planning.constraints.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.planning.constraints.entity.CustomConstraint;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomConstraintRepository extends TenantAwareRepository<CustomConstraint, Long> {

    Optional<CustomConstraint> findByIdCustomConstraintAndTenantId(Long id, String tenantId);

    List<CustomConstraint> findByTenantIdOrderByCodeAsc(String tenantId);

    List<CustomConstraint> findByTenantIdAndProfile_IdConstraintProfileOrderByCodeAsc(
            String tenantId, Long profileId);

    boolean existsByTenantIdAndCodeIgnoreCase(String tenantId, String code);

    /**
     * Supprime les regles personnalisees d'un profil. Appele avant la suppression
     * du profil : la contrainte de cle etrangere refuserait sinon de laisser des
     * regles orphelines (et le comportement ne dependrait que du ON DELETE de la
     * base, absent des schemas crees par `ddl-auto`).
     */
    long deleteByTenantIdAndProfile_IdConstraintProfile(String tenantId, Long profileId);

    /**
     * Règles actives d'un profil, chargées avant chaque génération.
     * Le filtre tenant est explicite en plus du filtre Hibernate : ce chemin est
     * emprunté par le solveur, qui tourne hors requête HTTP.
     */
    @Query("""
            SELECT c FROM CustomConstraint c
            WHERE c.profile.idConstraintProfile = :profileId
              AND c.tenantId = :tenantId
              AND c.enabled = true
            ORDER BY c.code ASC
            """)
    List<CustomConstraint> findActiveByProfileAndTenantId(@Param("profileId") Long profileId,
                                                          @Param("tenantId") String tenantId);
}
