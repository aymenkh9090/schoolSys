package tn.wtm.school.pointage.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.pointage.entity.PresencePersonnel;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PresencePersonnelRepository extends TenantAwareRepository<PresencePersonnel, Long> {

    boolean existsByTenantIdAndMembrePersonnelIdAndDatePointageAndPeriode(
            String tenantId, Long membrePersonnelId, LocalDate datePointage, Periode periode);

    Optional<PresencePersonnel> findByTenantIdAndMembrePersonnelIdAndDatePointageAndPeriode(
            String tenantId, Long membrePersonnelId, LocalDate datePointage, Periode periode);

    Optional<PresencePersonnel> findByTenantIdAndId(String tenantId, Long id);

    List<PresencePersonnel> findByTenantIdAndDatePointage(String tenantId, LocalDate datePointage);

    List<PresencePersonnel> findByTenantIdAndMembrePersonnelIdAndDatePointageBetween(
            String tenantId, Long membrePersonnelId, LocalDate debut, LocalDate fin);

    @Query("SELECT COUNT(p) FROM PresencePersonnel p WHERE p.tenantId = :tenantId AND p.datePointage = :date AND p.statut = :statut")
    long compterParStatutEtDate(@Param("tenantId") String tenantId,
                                 @Param("date") LocalDate date,
                                 @Param("statut") StatutPresencePersonnel statut);

    @Query("SELECT COUNT(p) FROM PresencePersonnel p WHERE p.tenantId = :tenantId AND p.membrePersonnelId = :membreId AND p.datePointage BETWEEN :debut AND :fin AND p.statut = :statut")
    long compterParMembreEtStatutEtPeriode(@Param("tenantId") String tenantId,
                                            @Param("membreId") Long membreId,
                                            @Param("debut") LocalDate debut,
                                            @Param("fin") LocalDate fin,
                                            @Param("statut") StatutPresencePersonnel statut);
}
