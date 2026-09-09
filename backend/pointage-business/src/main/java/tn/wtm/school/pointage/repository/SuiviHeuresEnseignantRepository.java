package tn.wtm.school.pointage.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.pointage.entity.SuiviHeuresEnseignant;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuiviHeuresEnseignantRepository extends TenantAwareRepository<SuiviHeuresEnseignant, Long> {

    Optional<SuiviHeuresEnseignant> findByTenantIdAndEnseignantIdAndNumeroSemaineAndAnneeAcademique(
            String tenantId, Long enseignantId, int numeroSemaine, String anneeAcademique);

    List<SuiviHeuresEnseignant> findByTenantIdAndEnseignantIdAndAnneeAcademique(
            String tenantId, Long enseignantId, String anneeAcademique);

    @Query("SELECT COALESCE(SUM(s.heuresPrevues), 0) FROM SuiviHeuresEnseignant s WHERE s.tenantId = :tenantId AND s.enseignantId = :enseignantId AND s.anneeAcademique = :annee")
    Double sommeHeuresPrevuesAnnuelles(@Param("tenantId") String tenantId,
                                        @Param("enseignantId") Long enseignantId,
                                        @Param("annee") String annee);

    @Query("SELECT COALESCE(SUM(s.heuresRealisees), 0) FROM SuiviHeuresEnseignant s WHERE s.tenantId = :tenantId AND s.enseignantId = :enseignantId AND s.anneeAcademique = :annee")
    Double sommeHeuresRealiseesAnnuelles(@Param("tenantId") String tenantId,
                                          @Param("enseignantId") Long enseignantId,
                                          @Param("annee") String annee);
}
