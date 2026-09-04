package tn.wtm.school.absence.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.common.repository.TenantAwareRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeanceAppelRepository extends TenantAwareRepository<SeanceAppel, Long> {

    /**
     * Une séance du planning revient chaque semaine : elle est donc identifiée
     * par son couple (séance planning, jour de cours) et non par le seul
     * identifiant de planning.
     */
    Optional<SeanceAppel> findByTenantIdAndSeancePlanningIdAndDateSeance(
            String tenantId, Long seancePlanningId, LocalDate dateSeance);

    boolean existsByTenantIdAndSeancePlanningIdAndDateSeance(
            String tenantId, Long seancePlanningId, LocalDate dateSeance);

    Optional<SeanceAppel> findByTenantIdAndId(String tenantId, Long id);

    @Query("""
            SELECT DISTINCT s FROM SeanceAppel s
            LEFT JOIN FETCH s.lignesAppel
            WHERE s.tenantId = :tenantId AND s.id = :id
            """)
    Optional<SeanceAppel> findWithLignes(@Param("tenantId") String tenantId, @Param("id") Long id);

    List<SeanceAppel> findByTenantIdAndGroupeClasseId(String tenantId, Long groupeClasseId);

    List<SeanceAppel> findByTenantIdAndEnseignantId(String tenantId, Long enseignantId);

    /**
     * Liste les séances d'appel d'un jour de cours, avec filtres optionnels
     * (classe, enseignant, verrouillage) — permet de parcourir les séances
     * plutôt que de connaître leur ID à l'avance.
     * <p>
     * Le filtre porte sur le jour de cours et non sur l'horodatage d'ouverture :
     * un appel régularisé le lendemain reste rattaché à sa journée réelle.
     */
    @Query("""
            SELECT DISTINCT s FROM SeanceAppel s
            LEFT JOIN FETCH s.lignesAppel
            WHERE s.tenantId = :tenantId
              AND s.dateSeance = :jour
              AND (:groupeClasseId IS NULL OR s.groupeClasseId = :groupeClasseId)
              AND (:enseignantId IS NULL OR s.enseignantId = :enseignantId)
              AND (:estVerrouille IS NULL OR s.estVerrouille = :estVerrouille)
            ORDER BY s.ouvertureAt DESC
            """)
    List<SeanceAppel> rechercher(
            @Param("tenantId") String tenantId,
            @Param("jour") LocalDate jour,
            @Param("groupeClasseId") Long groupeClasseId,
            @Param("enseignantId") Long enseignantId,
            @Param("estVerrouille") Boolean estVerrouille);

    @Query("""
            SELECT s FROM SeanceAppel s
            WHERE s.fermetureAt < :maintenant
              AND s.estVerrouille = false
            """)
    List<SeanceAppel> findSeancesExpiresNonVerrouillees(@Param("maintenant") LocalDateTime maintenant);
}
