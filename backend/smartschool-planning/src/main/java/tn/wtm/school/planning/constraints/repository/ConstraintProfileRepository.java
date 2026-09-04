package tn.wtm.school.planning.constraints.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConstraintProfileRepository extends TenantAwareRepository<ConstraintProfile, Long> {

    List<ConstraintProfile> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<ConstraintProfile> findByIdConstraintProfileAndTenantId(Long id, String tenantId);

    boolean existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(String tenantId, String name, Long academicYearId);

    Optional<ConstraintProfile> findFirstByTenantIdAndAcademicYearIdAndActiveTrueOrderByIdConstraintProfileDesc(
            String tenantId, Long academicYearId);

    Optional<ConstraintProfile> findFirstByTenantIdAndActiveTrueOrderByIdConstraintProfileDesc(String tenantId);

    /**
     * Les profils actifs d'une annee scolaire. L'invariant metier est qu'il n'y
     * en a qu'un : la liste sert a le faire respecter (on desactive les autres)
     * et a rattraper les donnees anterieures a cette regle.
     */
    List<ConstraintProfile> findByTenantIdAndAcademicYearIdAndActiveTrue(String tenantId, Long academicYearId);

    /** Le profil le plus recent d'une annee, actif ou non — sert a reprendre la main apres suppression de l'actif. */
    Optional<ConstraintProfile> findFirstByTenantIdAndAcademicYearIdOrderByIdConstraintProfileDesc(
            String tenantId, Long academicYearId);

    @Query("""
            SELECT DISTINCT p FROM ConstraintProfile p
            LEFT JOIN FETCH p.settings s
            LEFT JOIN FETCH s.definition
            WHERE p.idConstraintProfile = :id
              AND p.tenantId = :tenantId
            """)
    Optional<ConstraintProfile> findByIdWithSettingsAndTenantId(@Param("id") Long id,
                                                                 @Param("tenantId") String tenantId);
}
