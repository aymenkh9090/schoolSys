package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.Pattern;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatternRepository extends TenantAwareRepository<Pattern, Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(p) > 0 FROM Pattern p
        WHERE p.name = :name
          AND p.subjectLevel.idNiveauMatiere = :slId
          AND ((:syId IS NULL AND p.schoolYear IS NULL)
               OR p.schoolYear.idAnnee = :syId)
          AND (:excludeId IS NULL OR p.idPattern <> :excludeId)
        """)
    boolean existsByNameAndSubjectLevelAndYear(
            @Param("name")      String name,
            @Param("slId")      Long slId,
            @Param("syId")      Long syId,
            @Param("excludeId") Long excludeId
    );

    // ── Par subjectLevel ──────────────────────────────────────────────────────

    Optional<Pattern> findByTenantIdAndIdPattern(String tenantId, Long id);

    List<Pattern> findBySubjectLevel_IdNiveauMatiere(Long subjectLevelId);

    Page<Pattern> findBySubjectLevel_IdNiveauMatiere(Long subjectLevelId, Pageable pageable);

    // ── Avec détails chargés (évite N+1) ─────────────────────────────────────

    @Query("""
        SELECT DISTINCT p FROM Pattern p
        LEFT JOIN FETCH p.patternDetails pd
        LEFT JOIN FETCH pd.subjectSessionType
        WHERE p.idPattern = :id
        """)
    Optional<Pattern> findByIdWithDetails(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT p FROM Pattern p
        LEFT JOIN FETCH p.patternDetails pd
        LEFT JOIN FETCH p.subjectLevel sl
        LEFT JOIN FETCH sl.subject
        LEFT JOIN FETCH sl.level
        WHERE p.subjectLevel.idNiveauMatiere = :slId
        ORDER BY p.name ASC
        """)
    List<Pattern> findBySubjectLevelWithDetails(@Param("slId") Long slId);

    // ── Pour la génération : patterns d'un niveau, priorité année > défaut ────
    //
    // Règle : si un pattern spécifique à l'année existe pour un SubjectLevel,
    //         il est retourné. Sinon, le pattern par défaut (schoolYear IS NULL)
    //         est utilisé comme fallback.

    @Query("""
        SELECT DISTINCT p FROM Pattern p
        LEFT JOIN FETCH p.patternDetails pd
        LEFT JOIN FETCH pd.subjectSessionType
        LEFT JOIN FETCH p.subjectLevel sl
        LEFT JOIN FETCH sl.subject
        WHERE sl.level.idNiveau = :levelId
          AND (p.schoolYear.idAnnee = :schoolYearId
               OR (p.schoolYear IS NULL
                   AND NOT EXISTS (
                       SELECT p2 FROM Pattern p2
                       WHERE p2.subjectLevel = sl
                         AND p2.schoolYear.idAnnee = :schoolYearId
                   )))
        ORDER BY sl.subject.codeMatiere ASC, p.name ASC
        """)
    List<Pattern> findAllByLevelForGeneration(
            @Param("levelId")      Long levelId,
            @Param("schoolYearId") Long schoolYearId
    );

    // ── Par code niveau (pour customisation post-apply-national) ─────────────

    @Query("""
        SELECT DISTINCT p FROM Pattern p
        LEFT JOIN FETCH p.patternDetails
        LEFT JOIN FETCH p.subjectLevel sl
        LEFT JOIN FETCH sl.subject
        LEFT JOIN FETCH sl.level
        WHERE sl.level.code = :levelCode
          AND p.tenantId = :tenantId
        ORDER BY sl.subject.codeMatiere ASC
        """)
    List<Pattern> findByLevelCodeWithDetails(
            @Param("tenantId")   String tenantId,
            @Param("levelCode")  String levelCode);

    @Query("""
        SELECT DISTINCT p FROM Pattern p
        LEFT JOIN FETCH p.patternDetails
        LEFT JOIN FETCH p.subjectLevel sl
        LEFT JOIN FETCH sl.subject subj
        LEFT JOIN FETCH sl.level
        WHERE sl.level.code = :levelCode
          AND subj.codeMatiere = :subjectCode
          AND p.tenantId = :tenantId
          AND p.schoolYear IS NULL
        """)
    Optional<Pattern> findDefaultByLevelAndSubject(
            @Param("tenantId")    String tenantId,
            @Param("levelCode")   String levelCode,
            @Param("subjectCode") String subjectCode);

    // ── Stats : cohérence totalHours vs somme details ────────────────────────

    @Query("""
        SELECT p.name,
               p.totalHours,
               SUM(pd.duration)    AS sumDetails,
               COUNT(pd)           AS nbDetails
        FROM Pattern p
        LEFT JOIN p.patternDetails pd
        GROUP BY p.idPattern, p.name, p.totalHours
        HAVING ABS(p.totalHours - COALESCE(SUM(pd.duration), 0)) > 0.01
        """)
    List<Object[]> findInconsistentPatterns();

}
