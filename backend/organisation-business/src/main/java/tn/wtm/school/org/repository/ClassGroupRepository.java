package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.ClassGroup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import tn.wtm.school.common.metrics.TenantCount;

@Repository
public interface ClassGroupRepository extends TenantAwareRepository<ClassGroup,Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<ClassGroup> findByCode(String code);

    Optional<ClassGroup> findByTenantIdAndIdClasse(String tenantId, Long id);

    /** Chargement en lot, pour résoudre des libellés sans requête par identifiant. */
    List<ClassGroup> findByTenantIdAndIdClasseIn(String tenantId, Collection<Long> ids);

    Optional<ClassGroup> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByCode(String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);

    boolean existsByCodeAndIdClasseNot(String code, Long id);

    // ── Par niveau / année ────────────────────────────────────────────────────

    List<ClassGroup> findByLevel_IdNiveau(Long levelId);

    List<ClassGroup> findBySchoolYear_IdAnnee(Long schoolYearId);

    List<ClassGroup> findByLevel_IdNiveauAndSchoolYear_IdAnnee(
            Long levelId, Long schoolYearId
    );

    List<ClassGroup> findByTenantIdAndLevel_IdNiveau(String tenantId, Long levelId);

    List<ClassGroup> findByTenantIdAndSchoolYear_IdAnnee(String tenantId, Long schoolYearId);

    List<ClassGroup> findByTenantIdAndLevel_IdNiveauAndSchoolYear_IdAnnee(
            String tenantId, Long levelId, Long schoolYearId
    );

    Page<ClassGroup> findByTenantIdAndSchoolYear_IdAnnee(String tenantId, Long schoolYearId, Pageable pageable);

    List<ClassGroup> findByEstActifTrueAndSchoolYear_IdAnnee(Long schoolYearId);

    Page<ClassGroup> findBySchoolYear_IdAnnee(Long schoolYearId, Pageable pageable);

    // ── Avec affectations chargées ────────────────────────────────────────────

    @Query("""
        SELECT DISTINCT c FROM ClassGroup c
        LEFT JOIN FETCH c.teachingAssignments ta
        LEFT JOIN FETCH ta.teacher
        LEFT JOIN FETCH ta.subjectLevel sl
        LEFT JOIN FETCH sl.subject
        WHERE c.idClasse = :id
        """)
    Optional<ClassGroup> findByIdWithAssignments(@Param("id") Long id);

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Query("""
        SELECT c.code,
               l.code           AS levelCode,
               sy.nom           AS annee,
               COUNT(ta)        AS nbAffectations,
               SUM(c.nbEleve)   AS totalEleves
        FROM ClassGroup c
        LEFT JOIN c.level       l
        LEFT JOIN c.schoolYear  sy
        LEFT JOIN c.teachingAssignments ta
        WHERE c.estActif = true
        GROUP BY c.idClasse, c.code, l.code, sy.nom
        ORDER BY sy.nom DESC, l.code ASC, c.code ASC
        """)
    List<Object[]> findClassGroupStats();

    // ── Vérifier doublon code dans le même niveau + même année ───────────────

    @Query("""
        SELECT COUNT(c) > 0 FROM ClassGroup c
        WHERE c.code = :code
          AND c.level.idNiveau       = :levelId
          AND c.schoolYear.idAnnee   = :schoolYearId
          AND (:excludeId IS NULL OR c.idClasse <> :excludeId)
        """)
    boolean existsByCodeInLevelAndYear(
            @Param("code")        String code,
            @Param("levelId")     Long levelId,
            @Param("schoolYearId")Long schoolYearId,
            @Param("excludeId")   Long excludeId
    );


















    /** Métriques plateforme : classes actives par établissement, en une requête. */
    @Query("""
           SELECT new tn.wtm.school.common.metrics.TenantCount(c.tenantId, COUNT(c))
           FROM ClassGroup c
           WHERE c.estActif = true
           GROUP BY c.tenantId
           """)
    List<TenantCount> countActiveGroupedByTenant();
}
