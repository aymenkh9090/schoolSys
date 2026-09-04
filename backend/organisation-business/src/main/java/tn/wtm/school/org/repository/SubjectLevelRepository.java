package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.SubjectLevel;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectLevelRepository extends TenantAwareRepository<SubjectLevel, Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<SubjectLevel> findBySubject_IdMatiereAndLevel_IdNiveau(
            Long subjectId, Long levelId
    );

    boolean existsBySubject_IdMatiereAndLevel_IdNiveau(
            Long subjectId, Long levelId
    );

    // ── Par niveau ou matière ─────────────────────────────────────────────────

    List<SubjectLevel> findByLevel_IdNiveau(Long levelId);

    List<SubjectLevel> findBySubject_IdMatiere(Long subjectId);

    List<SubjectLevel> findByLevel_IdNiveauAndEstObligatoireTrue(Long levelId);

    Page<SubjectLevel> findByLevel_IdNiveau(Long levelId, Pageable pageable);

    // ── Avec tout chargé (pour la génération de planning) ────────────────────

    @Query("""
        SELECT DISTINCT sl FROM SubjectLevel sl
        LEFT JOIN FETCH sl.subject
        LEFT JOIN FETCH sl.level
        LEFT JOIN FETCH sl.subjectSessionTypes sst
        LEFT JOIN FETCH sl.patterns p
        WHERE sl.idNiveauMatiere = :id
        """)
    Optional<SubjectLevel> findByIdFullyLoaded(@Param("id") Long id);

    // ── Tous les subjectLevels d'un niveau avec détails ───────────────────────

    @Query("""
        SELECT DISTINCT sl FROM SubjectLevel sl
        LEFT JOIN FETCH sl.subject
        LEFT JOIN FETCH sl.subjectSessionTypes
        WHERE sl.level.idNiveau = :levelId
        ORDER BY sl.subject.codeMatiere ASC
        """)
    List<SubjectLevel> findByLevelWithSessionTypes(@Param("levelId") Long levelId);

    // ── Volume horaire total par niveau ───────────────────────────────────────

    @Query("""
        SELECT l.code,
               SUM(sl.heuresSemaine) AS totalHeuresSemaine,
               COUNT(sl)             AS nbMatieres
        FROM SubjectLevel sl
        JOIN sl.level l
        GROUP BY l.idNiveau, l.code
        ORDER BY l.code ASC
        """)
    List<Object[]> findWeeklyHoursByLevel();

}
