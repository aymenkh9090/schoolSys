package tn.wtm.school.org.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.TeacherRequest;
import tn.wtm.school.org.dto.response.TeacherImportResult;
import tn.wtm.school.org.dto.response.TeacherResponse;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface TeacherService {




    //  CRUD
    // ═══════════════════════════════════════════════════

    /** Créer un enseignant */
    TeacherResponse createTeacher(TeacherRequest dto);

    /** Récupérer par ID */
    TeacherResponse getTeacherById(Long id);

    /**
     * Fiche de l'enseignant connecté (résolue via le JWT : compte school_user lié,
     * avec repli par email et auto-liaison des comptes créés avant la FK enseignant_id).
     */
    TeacherResponse getCurrentTeacher();

    /**
     * Identifiant de la fiche enseignant liée au compte connecté, s'il y en a une.
     *
     * <p>Même résolution que {@link #getCurrentTeacher()}, mais sans exception :
     * un administrateur n'a pas de fiche enseignant, et ce n'est pas une erreur.
     * Les appelants qui doivent DISTINGUER les deux cas — typiquement pour choisir
     * entre un périmètre personnel et un périmètre établissement — ont besoin
     * d'une réponse vide, pas d'un 404.</p>
     */
    Optional<Long> getCurrentTeacherId();

    /** Récupérer par code enseignant */
    TeacherResponse getTeacherByCode(String code);

    /** Récupérer par numéro d'identité */
    TeacherResponse getTeacherByNumIdentite(String numIdentite);

    /** Tous les enseignants */
    List<TeacherResponse> getAllTeachers();

    /** Enseignants en poste */
    List<TeacherResponse> getActiveTeachers();

    /** Tous paginés */
    Page<TeacherResponse> getAllTeachersPaginated(Pageable pageable);

    /** Paginés filtrés par statut en poste */
    Page<TeacherResponse> getTeachersByStatus(Boolean estEnPoste, Pageable pageable);

    /** Modifier un enseignant */
    TeacherResponse updateTeacher(Long id, TeacherRequest dto);

    /**
     * Marquer comme muté (estEnPoste = false).
     * Ne supprime pas — préserve l'historique des affectations.
     */
    TeacherResponse deactivateTeacher(Long id);

    /** Réactiver un enseignant muté */
    TeacherResponse reactivateTeacher(Long id);

    /** Supprimer — uniquement si aucune affectation existante */
    void deleteTeacher(Long id);

    // ═══════════════════════════════════════════════════
    //  MÉTIER
    // ═══════════════════════════════════════════════════

    /** Recherche par nom, prénom, code ou email */
    Page<TeacherResponse> searchTeachers(String search, Pageable pageable);

    /** Enseignant avec toutes ses affectations chargées */
    TeacherResponse getTeacherWithAssignments(Long id);

    /** Enseignants disponibles pour un SubjectLevel donné */
    List<TeacherResponse> getTeachersBySubjectLevel(Long subjectLevelId);

    /**
     * Charge hebdomadaire de tous les enseignants.
     * Retourne: code, nomComplet, nbAffectations, totalHeures, maxHeuresSemaine
     */
    List<TeacherResponse> getTeacherWorkload();

    /**
     * Vérifier si un enseignant dépasse son max hebdomadaire.
     * Utile avant d'ajouter une affectation.
     */
    boolean isOverloaded(Long teacherId, Double additionalHours);

    /** Importer des enseignants depuis un flux CSV. Colonnes requises : codeEnseignant, numIdentite, nom, prenom */
    TeacherImportResult importFromCsv(InputStream csvStream);

    /** Importer des enseignants depuis un flux Excel (.xls / .xlsx). Même colonnes que le CSV. */
    TeacherImportResult importFromExcel(InputStream excelStream);











}
