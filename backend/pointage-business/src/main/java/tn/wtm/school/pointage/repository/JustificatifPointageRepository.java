package tn.wtm.school.pointage.repository;

import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.pointage.entity.JustificatifPointage;
import tn.wtm.school.pointage.enums.StatutJustificatifPointage;

import java.util.List;
import java.util.Optional;

@Repository
public interface JustificatifPointageRepository extends TenantAwareRepository<JustificatifPointage, Long> {

    Optional<JustificatifPointage> findByTenantIdAndPresencePersonnelId(String tenantId, Long presencePersonnelId);

    List<JustificatifPointage> findByTenantIdAndStatut(String tenantId, StatutJustificatifPointage statut);

    Optional<JustificatifPointage> findByTenantIdAndId(String tenantId, Long id);
}
