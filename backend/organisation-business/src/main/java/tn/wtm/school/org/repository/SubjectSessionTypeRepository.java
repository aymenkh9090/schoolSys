package tn.wtm.school.org.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.enums.SessionType;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectSessionTypeRepository extends TenantAwareRepository<SubjectSessionType, Long> {

    // ── Par subjectLevel ──────────────────────────────────────────────────────

    List<SubjectSessionType> findBySubjectLevel_IdNiveauMatiere(Long subjectLevelId);

    List<SubjectSessionType> findBySubjectLevel_IdNiveauMatiereAndEstActifTrue(
            Long subjectLevelId
    );

    // ── Unicité (tenant_id, subject_level_id, type) ───────────────────────────

    boolean existsBySubjectLevel_IdNiveauMatiereAndType(Long subjectLevelId, SessionType type);

    @Query("""
        SELECT COUNT(sst) > 0 FROM SubjectSessionType sst
        WHERE sst.subjectLevel.idNiveauMatiere = :slId
          AND sst.type = :type
          AND (:excludeId IS NULL OR sst.idSubjectSessionType <> :excludeId)
        """)
    boolean existsBySubjectLevelAndType(
            @Param("slId")      Long slId,
            @Param("type")      SessionType type,
            @Param("excludeId") Long excludeId
    );

    // ── Avec split ────────────────────────────────────────────────────────────

    List<SubjectSessionType> findByRequiresSplitTrue();

    @Query("""

            SELECT sst FROM SubjectSessionType sst
        WHERE sst.subjectLevel.idNiveauMatiere = :slId
          AND sst.requiresSplit = true
        """)
    List<SubjectSessionType> findSplitSessionsBySubjectLevel(@Param("slId") Long slId);

    // ── Pour la génération : tous les types actifs d'un niveau ───────────────

    @Query("""
        SELECT sst FROM SubjectSessionType sst
        JOIN FETCH sst.subjectLevel sl
        JOIN FETCH sl.subject
        JOIN FETCH sl.level
        WHERE sl.level.idNiveau = :levelId
          AND sst.estActif = true
        ORDER BY sl.subject.codeMatiere ASC
        """)
    List<SubjectSessionType> findActiveByLevel(@Param("levelId") Long levelId);

    // ── Vérifier unicité subjectLevel (une seule config active par subjectLevel) ─

    @Query("""
        SELECT COUNT(sst) > 0 FROM SubjectSessionType sst
        WHERE sst.subjectLevel.idNiveauMatiere = :slId
          AND sst.estActif = true
          AND (:excludeId IS NULL OR sst.idSubjectSessionType <> :excludeId)
        """)
    boolean existsActiveForSubjectLevel(
            @Param("slId")      Long slId,
            @Param("excludeId") Long excludeId
    );
}
