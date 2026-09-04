package tn.wtm.school.org.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.CustomizePatternRequest;
import tn.wtm.school.org.dto.request.PatternDetailRequest;
import tn.wtm.school.org.dto.request.PatternRequest;
import tn.wtm.school.org.dto.response.PatternDetailResponse;
import tn.wtm.school.org.dto.response.PatternResponse;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;

import java.util.List;

public interface PatternService {



    // ═══════════════════════════════════════════════════
    //  PATTERN — CRUD
    // ═══════════════════════════════════════════════════

    /**
     * Créer un pattern avec ses détails en une seule transaction.
     * Valide: totalHours == somme(details.duration)
     *         sessionCount == details.size()
     */
    PatternResponse createPattern(PatternRequest dto);

    /** Récupérer un pattern par ID (sans détails) */
    PatternResponse getPatternById(Long id);

    /** Récupérer un pattern avec ses détails chargés */
    PatternResponse getPatternWithDetails(Long id);

    /** Tous les patterns d'un SubjectLevel */
    List<PatternResponse> getPatternsBySubjectLevel(Long subjectLevelId);

    /** Patterns d'un SubjectLevel avec détails */
    List<PatternResponse> getPatternsBySubjectLevelWithDetails(Long subjectLevelId);

    /** Patterns d'un SubjectLevel paginés */
    Page<PatternResponse> getPatternsBySubjectLevelPaginated(Long subjectLevelId, Pageable pageable);

    /** Modifier un pattern — remplace aussi tous ses détails */
    PatternResponse updatePattern(Long id, PatternRequest dto);

    /** Supprimer un pattern et tous ses détails (cascade) */
    void deletePattern(Long id);

    /**
     * Activer/désactiver un pattern sans le supprimer.
     * Un pattern désactivé est ignoré par le solveur (la matière n'est pas planifiée
     * pour cet établissement) mais reste consultable/réactivable.
     */
    PatternResponse togglePatternActive(Long id, Boolean active);

    // ═══════════════════════════════════════════════════
    //  PATTERN — MÉTIER
    // ═══════════════════════════════════════════════════

    /**
     * Tous les patterns d'un niveau entièrement chargés pour la génération.
     * Retourne les patterns spécifiques à l'année si disponibles,
     * sinon les patterns par défaut (schoolYear IS NULL) en fallback.
     */
    List<PatternResponse> getPatternsForGeneration(Long levelId, Long schoolYearId);

    /**
     * Valider la cohérence d'un pattern:
     * - totalHours == somme des durées
     * - sessionCount == nombre de détails
     * - sessionOrder unique et consécutif
     */
    void validatePatternConsistency(Long patternId);

    /**
     * Détecter tous les patterns incohérents du tenant.
     * Utile avant de lancer la génération.
     */
    List<PatternResponse> findInconsistentPatterns();

    // ═══════════════════════════════════════════════════
    //  PATTERN DETAIL — CRUD
    // ═══════════════════════════════════════════════════

    /** Ajouter un détail à un pattern existant */
    PatternDetailResponse addDetail(Long patternId, PatternDetailRequest dto);

    /** Récupérer un détail par ID */
    PatternDetailResponse getDetailById(Long id);

    /** Tous les détails d'un pattern ordonnés par sessionOrder */
    List<PatternDetailResponse> getDetailsByPattern(Long patternId);

    /** Modifier un détail */
    PatternDetailResponse updateDetail(Long id, PatternDetailRequest dto);

    /**
     * Supprimer un détail et réordonner les sessionOrder restants.
     * Met à jour totalHours et sessionCount du Pattern parent.
     */
    void deleteDetail(Long id);

    // ═══════════════════════════════════════════════════
    //  PATTERN DETAIL — MÉTIER
    // ═══════════════════════════════════════════════════

    /** Détails d'un pattern par type de séance */
    List<PatternDetailResponse> getDetailsByType(Long patternId, SessionType type);

    /** Détails d'un pattern par type de salle requis */
    List<PatternDetailResponse> getDetailsByRoomType(Long patternId, RoomType roomType);

    /** Détails divisés (isSplit=true) d'un pattern */
    List<PatternDetailResponse> getSplitDetails(Long patternId);

    /**
     * Réordonner manuellement les détails d'un pattern.
     * @param patternId  id du pattern
     * @param orderedIds ids des détails dans le nouvel ordre souhaité
     */
    List<PatternDetailResponse> reorderDetails(Long patternId, List<Long> orderedIds);

    // ═══════════════════════════════════════════════════
    //  CUSTOMISATION POST-APPLY-NATIONAL
    // ═══════════════════════════════════════════════════

    /** Tous les patterns d'un niveau via son code (ex: "7EME"), avec détails */
    List<PatternResponse> getPatternsByLevelCode(String levelCode);

    /** Modifier le pattern par défaut d'une matière pour un niveau donné */
    PatternResponse customizePattern(String levelCode, String subjectCode, CustomizePatternRequest request);














}
