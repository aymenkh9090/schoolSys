package tn.wtm.school.org.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.AssignSubjectRequest;
import tn.wtm.school.org.dto.request.TeachingAssignmentRequest;
import tn.wtm.school.org.dto.response.AssignSubjectResult;
import tn.wtm.school.org.dto.response.MissingAssignmentEntry;
import tn.wtm.school.org.dto.response.TeachingAssignmentResponse;

import java.util.List;

public interface TeachingAssignmentService {

    // TeachingAssignment - CRUD

    /** Creer une affectation enseignant. */
    TeachingAssignmentResponse createTeachingAssignment(TeachingAssignmentRequest dto);

    /** Recuperer une affectation par ID. */
    TeachingAssignmentResponse getTeachingAssignmentById(Long id);

    /** Recuperer une affectation avec toutes ses relations chargees. */
    TeachingAssignmentResponse getTeachingAssignmentFullyLoaded(Long id);

    /** Toutes les affectations paginees. */
    Page<TeachingAssignmentResponse> getAllTeachingAssignments(Pageable pageable);

    /** Modifier une affectation. */
    TeachingAssignmentResponse updateTeachingAssignment(Long id, TeachingAssignmentRequest dto);

    /** Activer / desactiver une affectation. */
    TeachingAssignmentResponse toggleTeachingAssignmentStatus(Long id, Boolean isActive);

    /** Supprimer une affectation. */
    void deleteTeachingAssignment(Long id);

    // TeachingAssignment - Filtres

    /** Lister les affectations d'un enseignant. */
    List<TeachingAssignmentResponse> getTeachingAssignmentsByTeacher(Long teacherId);

    /** Lister les affectations d'une classe. */
    List<TeachingAssignmentResponse> getTeachingAssignmentsByClassGroup(Long classGroupId);

    /** Lister les affectations d'une matière-niveau. */
    List<TeachingAssignmentResponse> getTeachingAssignmentsBySubjectLevel(Long subjectLevelId);

    /** Lister les affectations d'une année scolaire. */
    List<TeachingAssignmentResponse> getTeachingAssignmentsBySchoolYear(Long schoolYearId);

    /** Lister les affectations d'une année scolaire en pagination. */
    Page<TeachingAssignmentResponse> getTeachingAssignmentsBySchoolYearPaged(Long schoolYearId, Pageable pageable);

    /** Lister les affectations d'un enseignant pour une année scolaire. */
    List<TeachingAssignmentResponse> getTeachingAssignmentsByTeacherAndYear(Long teacherId, Long schoolYearId);

    /** Lister les affectations d'une classe pour une année scolaire. */
    List<TeachingAssignmentResponse> getTeachingAssignmentsByClassGroupAndYear(Long classGroupId, Long schoolYearId);

    /** Lister les affectations actives d'une année scolaire. */
    List<TeachingAssignmentResponse> getActiveTeachingAssignmentsBySchoolYear(Long schoolYearId);

    /** Lister les affectations actives d'un enseignant. */
    List<TeachingAssignmentResponse> getActiveTeachingAssignmentsByTeacher(Long teacherId);

    // TeachingAssignment - Generation / metier

    /**
     * Affectations actives entierement chargees pour le moteur de generation.
     */
    List<TeachingAssignmentResponse> getTeachingAssignmentsForGeneration(Long schoolYearId);

    /**
     * Charge hebdomadaire des enseignants sur une année scolaire.
     * Chaque ligne contient: teacherId, nomComplet, maxHeuresSemaine,
     * totalHeures, nbAffectations.
     */
    List<Object[]> getTeacherLoadByYear(Long schoolYearId);

    /**
     * Détecter les affectations actives conflictuelles:
     * meme annee, meme enseignant, meme matiere-niveau.
     */
    List<TeachingAssignmentResponse> findConflicts(Long schoolYearId, Long teacherId, Long subjectLevelId);

    /**
     * Verifier si une affectation identique existe deja.
     */
    boolean existsTeachingAssignment(
            Long schoolYearId,
            Long teacherId,
            Long classGroupId,
            Long subjectLevelId,
            Long subjectSessionTypeId
    );

    /**
     * Verifier si une affectation identique existe deja, avec exclusion optionnelle
     * de l'affectation courante lors d'une mise à jour.
     */
    boolean existsDuplicateTeachingAssignment(
            Long schoolYearId,
            Long teacherId,
            Long classGroupId,
            Long subjectLevelId,
            Long subjectSessionTypeId,
            Long excludeId
    );

    /**
     * Valider qu'une affectation peut être créée ou modifiée.
     */
    void validateTeachingAssignment(TeachingAssignmentRequest dto, Long excludeId);

    /**
     * Affecter un enseignant à une matière pour plusieurs classes en une seule opération.
     * Les doublons sont ignorés (skipped) sans erreur.
     */
    AssignSubjectResult assignSubjectToClasses(AssignSubjectRequest request);

    /**
     * Retourne les combinaisons (classe × type-séance) pour lesquelles aucune
     * affectation active n'existe sur l'année scolaire donnée.
     * Liste vide = tout est assigné.
     */
    List<MissingAssignmentEntry> getMissingAssignments(Long schoolYearId);

}
