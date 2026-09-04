package tn.wtm.school.absence.repository;

import org.springframework.stereotype.Repository;
import tn.wtm.school.absence.entity.HistoriqueAppel;
import tn.wtm.school.common.repository.TenantAwareRepository;

import java.util.List;

@Repository
public interface HistoriqueAppelRepository extends TenantAwareRepository<HistoriqueAppel, Long> {

    List<HistoriqueAppel> findByTenantIdAndLigneAppel_IdOrderByModifieAtAsc(String tenantId, Long ligneAppelId);

    List<HistoriqueAppel> findByTenantIdAndModifiePar(String tenantId, Long modifiePar);
}
