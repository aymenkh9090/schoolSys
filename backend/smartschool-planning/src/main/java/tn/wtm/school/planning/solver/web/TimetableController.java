package tn.wtm.school.planning.solver.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.planning.solver.dto.request.MoveSessionRequest;
import tn.wtm.school.planning.solver.dto.request.TimetableGenerationRequest;
import tn.wtm.school.planning.solver.dto.response.ClassTimetableView;
import tn.wtm.school.planning.solver.dto.response.RoomTimetableView;
import tn.wtm.school.planning.solver.dto.response.TeacherTimetableView;
import tn.wtm.school.planning.solver.dto.response.GeneratedTimetableResponse;
import tn.wtm.school.planning.solver.dto.response.ScoreExplanationResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableJobResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableSessionResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableSolutionResponse;
import tn.wtm.school.planning.solver.service.TimetableSolverService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST API du moteur de génération d'emplois du temps.
 *
 * Deux groupes de ressources :
 *   /jobs        — cycle de vie des jobs solver (asynchrone)
 *   /generated   — emplois du temps produits et leur publication
 *
 * Toutes les opérations sont scopées au tenant courant (extrait du JWT via TenantContext).
 */
@RestController
@RequestMapping("/api/planning/timetable")
@RequiredArgsConstructor
// Le surveillant consulte l'emploi du temps (classes / enseignants / salles) : la
// lecture lui est ouverte ici, la génération et l'édition restent SCHOOL_ADMIN.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class TimetableController {

    private final TimetableSolverService solverService;

    // ══════════════════════════════════════════════════════════════════════════
    // Jobs — génération et suivi
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lance une génération d'emploi du temps de façon asynchrone.
     * Retourne immédiatement un jobId à poller via GET /jobs/{jobId}.
     *
     * {@code constraintProfileId} est optionnel : si absent, le profil actif
     * le plus récent de l'année scolaire est utilisé.
     */
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TimetableJobResponse generate(@Valid @RequestBody TimetableGenerationRequest request) {
        return solverService.startGeneration(request);
    }

    /**
     * Liste tous les jobs de génération du tenant courant.
     * Filtre optionnel par année scolaire ({@code academicYearId}).
     */
    @GetMapping("/jobs")
    public List<TimetableJobResponse> listJobs(
            @RequestParam(required = false) @Nullable Long academicYearId) {
        return solverService.listJobs(academicYearId);
    }

    /**
     * Retourne le statut courant d'un job.
     * Cycle : {@code PENDING → RUNNING → SOLVED | FAILED | CANCELLED}.
     */
    @GetMapping("/jobs/{jobId}")
    public TimetableJobResponse getJobStatus(@PathVariable Long jobId) {
        return solverService.getJobStatus(jobId);
    }

    /**
     * Annule un job en cours ({@code RUNNING}).
     * Si le job n'est pas RUNNING, retourne son état actuel sans erreur.
     */
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/jobs/{jobId}/cancel")
    public TimetableJobResponse cancel(@PathVariable Long jobId) {
        return solverService.cancelJob(jobId);
    }

    /**
     * Supprime définitivement un job de génération (séances et résultat agrégé
     * inclus). Rejeté si le job est encore RUNNING/PENDING (annuler d'abord),
     * ou si son emploi du temps est PUBLISHED (le dépublier d'abord).
     */
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/jobs/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable Long jobId) {
        solverService.deleteJob(jobId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Jobs — consultation du résultat solver
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Retourne la grille complète des sessions placées par le solver.
     * Disponible uniquement quand le job est {@code SOLVED} ou {@code FAILED}
     * (Timefold peut produire des sessions même si le score n'est pas feasible).
     */
    @GetMapping("/jobs/{jobId}/solution")
    public TimetableSolutionResponse getSolution(@PathVariable Long jobId) {
        return solverService.getSolution(jobId);
    }

    /**
     * Explication déterministe du score Timefold (panneau "pourquoi ce planning ?") :
     * pour chaque contrainte violée (hard/medium/soft), libellé français, nombre
     * d'occurrences et exemples concrets (enseignant/classe/créneau en cause).
     * Reste disponible après la fin du job (y compris FAILED). Indisponible
     * uniquement si l'instance backend a redémarré depuis, ou pour un job annulé
     * — un message de repli est alors renvoyé à la place des détails.
     */
    @GetMapping("/jobs/{jobId}/score-explanation")
    public ScoreExplanationResponse getScoreExplanation(@PathVariable Long jobId) {
        return solverService.explainScore(jobId);
    }

    /**
     * Retourne le {@link tn.wtm.school.planning.solver.entity.GeneratedTimetable}
     * associé à ce job (score agrégé, compteurs de violations, statut de publication).
     * 404 si le job n'a pas encore de résultat persisté.
     */
    @GetMapping("/jobs/{jobId}/result")
    public GeneratedTimetableResponse getResult(@PathVariable Long jobId) {
        return solverService.getResult(jobId);
    }

    /**
     * Retourne l'emploi du temps formaté d'une classe : sessions groupées par jour,
     * triées par heure, avec durée réelle calculée (ex: "1h30").
     * Disponible dès que le job est SOLVED ou FAILED.
     */
    @GetMapping("/jobs/{jobId}/view/class/{classCode}")
    public ClassTimetableView getClassTimetable(
            @PathVariable Long jobId,
            @PathVariable String classCode) {
        return solverService.getClassTimetable(jobId, classCode);
    }

    /**
     * Toutes les grilles classes du job, en une passe — l'écran de consultation
     * l'utilise pour l'option « Toutes les classes » (affichage et impression
     * groupée). Classes triées par code.
     */
    @GetMapping("/jobs/{jobId}/view/classes")
    public List<ClassTimetableView> getAllClassTimetables(@PathVariable Long jobId) {
        return solverService.getAllClassTimetables(jobId);
    }

    @GetMapping("/jobs/{jobId}/view/teacher/{teacherCode}")
    public TeacherTimetableView getTeacherTimetable(
            @PathVariable Long jobId,
            @PathVariable String teacherCode) {
        return solverService.getTeacherTimetable(jobId, teacherCode);
    }

    /** Toutes les grilles enseignants du job — voir {@link #getAllClassTimetables}. */
    @GetMapping("/jobs/{jobId}/view/teachers")
    public List<TeacherTimetableView> getAllTeacherTimetables(@PathVariable Long jobId) {
        return solverService.getAllTeacherTimetables(jobId);
    }

    @GetMapping("/jobs/{jobId}/view/room/{roomCode}")
    public RoomTimetableView getRoomTimetable(
            @PathVariable Long jobId,
            @PathVariable String roomCode) {
        return solverService.getRoomTimetable(jobId, roomCode);
    }

    /** Toutes les grilles salles du job — voir {@link #getAllClassTimetables}. */
    @GetMapping("/jobs/{jobId}/view/rooms")
    public List<RoomTimetableView> getAllRoomTimetables(@PathVariable Long jobId) {
        return solverService.getAllRoomTimetables(jobId);
    }

    /**
     * Deplace manuellement une seance generee (jour/heure/salle) — edition admin
     * post-generation, utilisee par le drag & drop de la grille de consultation.
     * L'enseignant, la matiere et la classe ne sont jamais modifies ici.
     */
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PatchMapping("/jobs/{jobId}/sessions/{sessionId}")
    public TimetableSessionResponse moveSession(
            @PathVariable Long jobId,
            @PathVariable Long sessionId,
            @Valid @RequestBody MoveSessionRequest request) {
        return solverService.moveSession(jobId, sessionId, request);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Generated timetables — publication
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Liste les emplois du temps générés du tenant courant.
     * Filtre optionnel par année scolaire ({@code academicYearId}).
     * Triés du plus récent au plus ancien.
     */
    @GetMapping("/generated")
    public List<GeneratedTimetableResponse> listGenerated(
            @RequestParam(required = false) @Nullable Long academicYearId) {
        return solverService.listGeneratedTimetables(academicYearId);
    }

    /**
     * Publie un emploi du temps (passe son statut de {@code DRAFT} à {@code PUBLISHED}).
     * Une fois publié, l'EDT est visible par les enseignants et les élèves.
     * Retourne 400 si l'EDT est déjà {@code ARCHIVED}.
     */
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/generated/{id}/publish")
    public GeneratedTimetableResponse publish(@PathVariable Long id) {
        return solverService.publishTimetable(id);
    }
}
