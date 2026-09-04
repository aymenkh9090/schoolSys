package tn.wtm.school.absence.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.absence.entity.LigneAppel;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.common.repository.TenantAwareRepository;

import java.util.List;
import java.util.Optional;

/**
 * Comme {@link JustificatifAbsenceRepository} et {@link CahierSeanceRepository},
 * les recherches à bornes optionnelles passent par une
 * {@link org.springframework.data.jpa.domain.Specification} : sur PostgreSQL,
 * un {@code (:param IS NULL OR ...)} portant une date est rejeté quand le
 * paramètre vaut null, le pilote l'envoyant sans type.
 */
@Repository
public interface LigneAppelRepository
        extends TenantAwareRepository<LigneAppel, Long>, JpaSpecificationExecutor<LigneAppel> {

    Optional<LigneAppel> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByTenantIdAndSeanceAppel_IdAndEleveId(String tenantId, Long seanceAppelId, Long eleveId);

    List<LigneAppel> findByTenantIdAndSeanceAppel_Id(String tenantId, Long seanceAppelId);

    List<LigneAppel> findByTenantIdAndEleveId(String tenantId, Long eleveId);

    List<LigneAppel> findByTenantIdAndEleveIdAndStatut(String tenantId, Long eleveId, StatutPresence statut);

    @Query("""
            SELECT l FROM LigneAppel l
            WHERE l.tenantId = :tenantId
              AND l.eleveId = :eleveId
              AND l.seanceAppel.anneeAcademique = :annee
            """)
    List<LigneAppel> findByEleveEtAnnee(
            @Param("tenantId") String tenantId,
            @Param("eleveId") Long eleveId,
            @Param("annee") String anneeAcademique);

    /**
     * Lignes d'appel d'un élève sur une période, filtrées par statut — base du
     * dossier d'absences consulté par la vie scolaire pour déposer et traiter
     * un justificatif sans connaître l'identifiant de la ligne.
     * <p>
     * Les justificatifs sont chargés dans la foulée : la réponse indique l'état
     * du dernier dépôt sur chaque absence.
     * <p>
     * Les bornes sont toujours renseignées par l'appelant : un {@code null}
     * comparé à {@code IS NULL} serait envoyé sans type et rejeté par PostgreSQL.
     */
    @Query("""
            SELECT DISTINCT l FROM LigneAppel l
            JOIN FETCH l.seanceAppel s
            LEFT JOIN FETCH l.justificatifs
            WHERE l.tenantId = :tenantId
              AND l.eleveId = :eleveId
              AND l.statut IN :statuts
              AND s.dateSeance BETWEEN :debut AND :fin
            ORDER BY s.dateSeance DESC
            """)
    List<LigneAppel> findAbsencesEleve(
            @Param("tenantId") String tenantId,
            @Param("eleveId") Long eleveId,
            @Param("statuts") java.util.Collection<StatutPresence> statuts,
            @Param("debut") java.time.LocalDate debut,
            @Param("fin") java.time.LocalDate fin);
}
