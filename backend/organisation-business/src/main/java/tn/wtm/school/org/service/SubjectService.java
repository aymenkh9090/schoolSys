package tn.wtm.school.org.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.SubjectLevelRequest;
import tn.wtm.school.org.dto.request.SubjectRequest;
import tn.wtm.school.org.dto.request.SubjectSessionTypeRequest;
import tn.wtm.school.org.dto.response.SubjectLevelResponse;
import tn.wtm.school.org.dto.response.SubjectResponse;
import tn.wtm.school.org.dto.response.SubjectSessionTypeResponse;

import java.util.List;

public interface SubjectService {

    // ---------Subject ----------
    SubjectResponse createSubject(SubjectRequest subjectRequest);

    SubjectResponse updateSubject(Long id, SubjectRequest subjectRequest);

    SubjectResponse getSubjectById(Long id);

    Page<SubjectResponse> getAllSubjects(Pageable pageable);

    List<SubjectResponse> getEnseignedSubjects();

    List<SubjectResponse> getPrincipaleSubjects();

    List<SubjectResponse> searchSubjects(String search);

    /**
     * Activer desactiver enseignanement d une matier
     */
    SubjectResponse toggleEnseignee(Long id, Boolean estEnseignee);

    void deleteSubject(Long id);

    Boolean existsByCodeMatiere(String codeMatiere);

    // Subject Leevel


    /**
     * Affecter une matière à un niveau
     */
    SubjectLevelResponse createSubjectLevel(SubjectLevelRequest dto);

    /**
     * Récupérer un subjectLevel par ID (tout chargé)
     */
    SubjectLevelResponse getSubjectLevelById(Long id);

    /**
     * Lister les subjectLevels d'un niveau
     */
    List<SubjectLevelResponse> getSubjectLevelsByLevel(Long levelId);

    /**
     * Lister les subjectLevels d'une matière
     */
    List<SubjectLevelResponse> getSubjectLevelsBySubject(Long subjectId);

    /**
     * Lister les subjectLevels d'un niveau avec sessionTypes
     */
    List<SubjectLevelResponse> getSubjectLevelsByLevelWithSessionTypes(Long levelId);

    /**
     * Modifier un subjectLevel
     */
    SubjectLevelResponse updateSubjectLevel(Long id, SubjectLevelRequest dto);

    /**
     * Supprimer un subjectLevel (vérifie absense de patterns et affectations)
     */
    void deleteSubjectLevel(Long id);

    // ═══════════════════════════════════════════════════════════
    //  SUBJECT LEVEL — MÉTIER
    // ═══════════════════════════════════════════════════════════

    /**
     * Vérifier si la liaison matière-niveau existe déjà
     */
    boolean existsSubjectLevel(Long subjectId, Long levelId);

    /**
     * Récupérer un subjectLevel avec tout chargé (pour génération)
     */
    SubjectLevelResponse getSubjectLevelFullyLoaded(Long id);

    // ═══════════════════════════════════════════════════════════
    //  SUBJECT SESSION TYPE — CRUD
    // ═══════════════════════════════════════════════════════════

    /**
     * Créer un type de séance pour un subjectLevel
     */
    SubjectSessionTypeResponse createSessionType(SubjectSessionTypeRequest dto);

    /**
     * Récupérer un sessionType par ID
     */
    SubjectSessionTypeResponse getSessionTypeById(Long id);

    /**
     * Lister les sessionTypes d'un subjectLevel
     */
    List<SubjectSessionTypeResponse> getSessionTypesBySubjectLevel(Long subjectLevelId);

    /**
     * Lister les sessionTypes actifs d'un subjectLevel
     */
    List<SubjectSessionTypeResponse> getActiveSessionTypesBySubjectLevel(Long subjectLevelId);

    /**
     * Modifier un sessionType
     */
    SubjectSessionTypeResponse updateSessionType(Long id, SubjectSessionTypeRequest dto);

    /**
     * Activer / désactiver un sessionType
     */
    SubjectSessionTypeResponse toggleSessionTypeStatus(Long id, boolean estActif);

    /**
     * Supprimer un sessionType (vérifie absence dans les patterns et affectations)
     */
    void deleteSessionType(Long id);



}



















