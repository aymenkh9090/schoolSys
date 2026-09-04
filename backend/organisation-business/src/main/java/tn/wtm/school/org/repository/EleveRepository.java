package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.Eleve;

import java.util.List;
import java.util.Optional;
import tn.wtm.school.common.metrics.TenantCount;

@Repository
public interface EleveRepository extends TenantAwareRepository<Eleve, Long> {

    // ── Lookup ─────────────────────────────────────────────────────────────────

    Optional<Eleve> findByTenantIdAndIdEleve(String tenantId, Long id);

    Optional<Eleve> findByTenantIdAndCodeEleve(String tenantId, String codeEleve);

    boolean existsByTenantIdAndCodeEleve(String tenantId, String codeEleve);

    boolean existsByTenantIdAndCodeEleveAndIdEleveNot(String tenantId, String codeEleve, Long id);

    // ── Par classe ─────────────────────────────────────────────────────────────

    List<Eleve> findByTenantIdAndClasseGroup_IdClasse(String tenantId, Long classeId);

    List<Eleve> findByTenantIdAndClasseGroup_IdClasseAndEstActifTrue(String tenantId, Long classeId);

    Page<Eleve> findByTenantIdAndClasseGroup_IdClasse(String tenantId, Long classeId, Pageable pageable);

    // ── Liste globale + recherche ─────────────────────────────────────────────

    Page<Eleve> findByTenantId(String tenantId, Pageable pageable);

    @Query("""
        SELECT e FROM Eleve e
        WHERE e.tenantId = :tenantId
          AND (LOWER(e.nom)       LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.prenom)    LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.codeEleve) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY e.nom ASC, e.prenom ASC
        """)
    Page<Eleve> searchEleves(
            @Param("tenantId") String tenantId,
            @Param("q")        String q,
            Pageable pageable);

    // ── IDs uniquement (pour PortEleveGroupe — évite le chargement complet) ────

    @Query("""
            SELECT e.idEleve FROM Eleve e
            WHERE e.tenantId = :tenantId
              AND e.classeGroup.idClasse = :classeId
              AND e.estActif = true
            ORDER BY e.nom ASC, e.prenom ASC
            """)
    List<Long> findIdsByTenantIdAndClasseId(
            @Param("tenantId") String tenantId,
            @Param("classeId") Long classeId);

    /** Métriques plateforme : élèves actifs par établissement, en une requête. */
    @Query("""
           SELECT new tn.wtm.school.common.metrics.TenantCount(e.tenantId, COUNT(e))
           FROM Eleve e
           WHERE e.estActif = true
           GROUP BY e.tenantId
           """)
    List<TenantCount> countActiveGroupedByTenant();
}
