package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.TeachingAssignment;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeachingAssignmentRepository extends TenantAwareRepository<TeachingAssignment, Long> {

    // ── Vérif unicité (UK_AFFECTATION_UNIQUE) ─────────────────────────────────

    boolean existsBySchoolYear_IdAnneeAndTeacher_IdEnseignantAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
            Long schoolYearId,
            Long teacherId,
            Long classGroupId,
            Long subjectLevelId,
            Long subjectSessionTypeId
    );

    /** Checks if ANY assignment exists for this class/subject/session-type regardless of teacher.
     *  Used by the demo runner to prevent creating duplicate assignments on re-run. */
    boolean existsBySchoolYear_IdAnneeAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
            Long schoolYearId,
            Long classGroupId,
            Long subjectLevelId,
            Long subjectSessionTypeId
    );

    @Query("""
        SELECT COUNT(ta) > 0 FROM TeachingAssignment ta
        WHERE ta.schoolYear.idAnnee                       = :syId
          AND ta.teacher.idEnseignant                     = :tId
          AND ta.classGroup.idClasse                      = :cgId
          AND ta.subjectLevel.idNiveauMatiere             = :slId
          AND ta.subjectSessionType.idSubjectSessionType  = :sstId
          AND (:excludeId IS NULL OR ta.idTeachingAssignment <> :excludeId)
        """)
    boolean existsDuplicate(
            @Param("syId")      Long syId,
            @Param("tId")       Long tId,
            @Param("cgId")      Long cgId,
            @Param("slId")      Long slId,
            @Param("sstId")     Long sstId,
            @Param("excludeId") Long excludeId
    );

    // ── Par entité ────────────────────────────────────────────────────────────

    List<TeachingAssignment> findByTeacher_IdEnseignant(Long teacherId);

    List<TeachingAssignment> findByClassGroup_IdClasse(Long classGroupId);

    List<TeachingAssignment> findBySubjectLevel_IdNiveauMatiere(Long subjectLevelId);

    List<TeachingAssignment> findBySchoolYear_IdAnnee(Long schoolYearId);

    Page<TeachingAssignment> findBySchoolYear_IdAnnee(Long schoolYearId, Pageable pageable);

    List<TeachingAssignment> findByTeacher_IdEnseignantAndSchoolYear_IdAnnee(
            Long teacherId, Long schoolYearId
    );

    List<TeachingAssignment> findByClassGroup_IdClasseAndSchoolYear_IdAnnee(
            Long classGroupId, Long schoolYearId
    );

    // ── Actives seulement ─────────────────────────────────────────────────────

    List<TeachingAssignment> findByIsActiveTrueAndSchoolYear_IdAnnee(Long schoolYearId);

    List<TeachingAssignment> findByIsActiveTrueAndTeacher_IdEnseignant(Long teacherId);

    // ── Chargement complet (évite N+1 pour la génération) ────────────────────

    @Query("""
        SELECT DISTINCT ta FROM TeachingAssignment ta
        LEFT JOIN FETCH ta.schoolYear
        LEFT JOIN FETCH ta.teacher
        LEFT JOIN FETCH ta.classGroup  cg
        LEFT JOIN FETCH cg.level
        LEFT JOIN FETCH ta.subjectLevel sl
        LEFT JOIN FETCH sl.subject
        LEFT JOIN FETCH sl.level
        LEFT JOIN FETCH ta.subjectSessionType
        WHERE ta.idTeachingAssignment = :id
        """)
    Optional<TeachingAssignment> findByIdFullyLoaded(@Param("id") Long id);

    // ── Toutes les affectations actives d'une année — pour génération ─────────

    // Two-pass fetch to avoid MultipleBagFetchException:
    // Pass 1 fetches TAs with patterns (no patternDetails yet).
    // Pass 2 (separate query below) fetches patternDetails per pattern.
    // LessonGenerator accesses patternDetails lazily within the @Transactional builder.
    @Query("""
        SELECT DISTINCT ta FROM TeachingAssignment ta
        LEFT JOIN FETCH ta.teacher t
        LEFT JOIN FETCH ta.classGroup  cg
        LEFT JOIN FETCH cg.level
        LEFT JOIN FETCH ta.subjectLevel sl
        LEFT JOIN FETCH sl.subject
        LEFT JOIN FETCH sl.patterns p
        LEFT JOIN FETCH ta.subjectSessionType
        WHERE ta.schoolYear.idAnnee = :syId
          AND ta.isActive = true
          AND t.estEnPoste = true
        ORDER BY cg.code ASC, sl.subject.codeMatiere ASC
        """)
    List<TeachingAssignment> findAllForGeneration(@Param("syId") Long syId);

    // ── Charge enseignant : total heures sur une année ────────────────────────

    @Query("""
        SELECT  ta.teacher.idEnseignant,
                CONCAT(ta.teacher.prenom, ' ', ta.teacher.nom) AS nomComplet,
                ta.teacher.maxHeuresSemaine,
                SUM(sst.duration)    AS totalHeures,
                COUNT(ta)            AS nbAffectations
        FROM TeachingAssignment ta
        JOIN ta.subjectSessionType sst
        WHERE ta.schoolYear.idAnnee = :syId
          AND ta.isActive = true
        GROUP BY ta.teacher.idEnseignant,
                 ta.teacher.prenom,
                 ta.teacher.nom,
                 ta.teacher.maxHeuresSemaine
        ORDER BY nomComplet ASC
        """)
    List<Object[]> findTeacherLoadByYear(@Param("syId") Long syId);

    // ── Affectations manquantes : (classe × session-type) sans enseignant ────────

    @Query("""
        SELECT cg.idClasse,
               cg.code,
               l.code,
               sl.idNiveauMatiere,
               s.codeMatiere,
               s.libMatiere,
               sst.idSubjectSessionType,
               CAST(sst.type AS string)
        FROM SubjectSessionType sst
        JOIN sst.subjectLevel sl
        JOIN sl.level         l
        JOIN sl.subject       s
        JOIN l.classGroups    cg
        JOIN cg.schoolYear    sy
        WHERE cg.tenantId   = :tenantId
          AND sl.tenantId   = :tenantId
          AND cg.estActif   = true
          AND sy.idAnnee    = :schoolYearId
          AND sst.estActif  = true
          AND NOT EXISTS (
              SELECT ta FROM TeachingAssignment ta
              WHERE ta.schoolYear.idAnnee                      = :schoolYearId
                AND ta.classGroup.idClasse                     = cg.idClasse
                AND ta.subjectSessionType.idSubjectSessionType = sst.idSubjectSessionType
                AND ta.isActive = true
          )
        ORDER BY cg.code ASC, s.codeMatiere ASC, sst.type ASC
        """)
    List<Object[]> findMissingAssignments(
            @Param("tenantId")      String tenantId,
            @Param("schoolYearId")  Long   schoolYearId
    );

    // ── Détecter les conflits : même enseignant, même classe, même subjectLevel ──

    @Query("""
        SELECT ta FROM TeachingAssignment ta
        WHERE ta.schoolYear.idAnnee         = :syId
          AND ta.teacher.idEnseignant       = :teacherId
          AND ta.subjectLevel.idNiveauMatiere = :slId
          AND ta.isActive = true
        ORDER BY ta.classGroup.code ASC
        """)
    List<TeachingAssignment> findConflicts(
            @Param("syId")      Long syId,
            @Param("teacherId") Long teacherId,
            @Param("slId")      Long slId
    );

}
