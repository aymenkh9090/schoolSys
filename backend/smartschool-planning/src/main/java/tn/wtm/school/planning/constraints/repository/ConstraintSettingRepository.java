package tn.wtm.school.planning.constraints.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.planning.constraints.entity.ConstraintSetting;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConstraintSettingRepository extends TenantAwareRepository<ConstraintSetting, Long> {

    Optional<ConstraintSetting> findByIdConstraintSettingAndTenantId(Long id, String tenantId);

    boolean existsByTenantIdAndProfile_IdConstraintProfileAndDefinition_IdConstraintDefinition(
            String tenantId, Long profileId, Long definitionId);

    @Query("""
            SELECT s FROM ConstraintSetting s
            JOIN FETCH s.definition
            WHERE s.profile.idConstraintProfile = :profileId
              AND s.tenantId = :tenantId
              AND s.enabled = true
            ORDER BY s.definition.code ASC
            """)
    List<ConstraintSetting> findActiveByProfileAndTenantId(@Param("profileId") Long profileId,
                                                            @Param("tenantId") String tenantId);
}
