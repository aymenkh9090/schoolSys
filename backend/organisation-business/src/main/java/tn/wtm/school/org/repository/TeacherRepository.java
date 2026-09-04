package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.Teacher;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import tn.wtm.school.common.metrics.TenantCount;

@Repository
public interface TeacherRepository extends TenantAwareRepository<Teacher,Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<Teacher> findByCodeEnseignant(String code);

    Optional<Teacher> findByTenantIdAndIdEnseignant(String tenantId, Long id);

    List<Teacher> findByTenantIdAndIdEnseignantIn(String tenantId, Collection<Long> ids);

    Optional<Teacher> findByTenantIdAndCodeEnseignant(String tenantId, String code);

    Optional<Teacher> findByNumIdentite(String numIdentite);

    Optional<Teacher> findByTenantIdAndNumIdentite(String tenantId, String numIdentite);

    Optional<Teacher> findByEmail(String email);

    boolean existsByCodeEnseignant(String code);

    boolean existsByTenantIdAndCodeEnseignant(String tenantId, String code);

    boolean existsByNumIdentite(String numIdentite);

    boolean existsByTenantIdAndNumIdentite(String tenantId, String numIdentite);

    boolean existsByEmail(String email);

    boolean existsByTenantIdAndEmail(String tenantId, String email);

    Optional<Teacher> findByTenantIdAndEmail(String tenantId, String email);

    boolean existsByCodeEnseignantAndIdEnseignantNot(String code, Long id);

    boolean existsByEmailAndIdEnseignantNot(String email, Long id);

    // ── Filtres simples ───────────────────────────────────────────────────────

    List<Teacher> findByEstEnPosteTrue();

    List<Teacher> findByEstEnPosteTrueOrderByNomAscPrenomAsc();

    List<Teacher> findByTenantIdAndEstEnPosteTrueOrderByNomAscPrenomAsc(String tenantId);

    List<Teacher> findByTenantIdAndCodeEnseignantInAndEstEnPosteTrueOrderByNomAscPrenomAsc(
            String tenantId, Collection<String> codes);

    Page<Teacher> findAll(Pageable pageable);

    Page<Teacher> findByEstEnPoste(Boolean estEnPoste, Pageable pageable);

    Page<Teacher> findByTenantIdAndEstEnPoste(String tenantId, Boolean estEnPoste, Pageable pageable);

    // ── Recherche fulltext ────────────────────────────────────────────────────

    @Query("""
        SELECT t FROM Teacher t
        WHERE LOWER(t.nom)            LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(t.prenom)         LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(t.codeEnseignant) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(t.email)          LIKE LOWER(CONCAT('%', :search, '%'))
        ORDER BY t.nom ASC, t.prenom ASC
        """)
    Page<Teacher> searchTeachers(
            @Param("search") String search,
            Pageable pageable
    );

    // ── Avec affectations chargées ────────────────────────────────────────────

    @Query("""
        SELECT DISTINCT t FROM Teacher t
        LEFT JOIN FETCH t.teachingAssignments ta
        LEFT JOIN FETCH ta.subjectLevel sl
        LEFT JOIN FETCH sl.subject
        LEFT JOIN FETCH sl.level
        LEFT JOIN FETCH ta.classGroup
        WHERE t.idEnseignant = :id
        """)
    Optional<Teacher> findByIdWithAssignments(@Param("id") Long id);

    // ── Enseignants disponibles pour une matière-niveau ───────────────────────

    @Query("""
        SELECT DISTINCT t FROM Teacher t
        JOIN t.teachingAssignments ta
        WHERE ta.subjectLevel.idNiveauMatiere = :slId
          AND ta.isActive = true
          AND t.estEnPoste = true
        ORDER BY t.nom ASC
        """)
    List<Teacher> findBySubjectLevel(@Param("slId") Long subjectLevelId);

    // ── Charge hebdomadaire par enseignant ────────────────────────────────────

    @Query("""
        SELECT t.codeEnseignant,
               CONCAT(t.prenom, ' ', t.nom)  AS nomComplet,
               COUNT(ta)                      AS nbAffectations,
               SUM(sst.duration)              AS chargeHebdo
        FROM Teacher t
        LEFT JOIN t.teachingAssignments ta
        LEFT JOIN ta.subjectSessionType sst
        WHERE t.estEnPoste = true
        GROUP BY t.idEnseignant, t.codeEnseignant, t.nom, t.prenom
        ORDER BY nomComplet ASC
        """)
    List<Object[]> findTeacherWorkload();


    /** Métriques plateforme : enseignants en poste par établissement, en une requête. */
    @Query("""
           SELECT new tn.wtm.school.common.metrics.TenantCount(t.tenantId, COUNT(t))
           FROM Teacher t
           WHERE t.estEnPoste = true
           GROUP BY t.tenantId
           """)
    List<TenantCount> countActiveGroupedByTenant();
}
