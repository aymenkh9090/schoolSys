package tn.wtm.school.planning.solver.port;

import tn.wtm.school.planning.solver.enums.SolverStatus;

import java.time.Instant;

/**
 * Diffusion de l'avancement d'un job de génération vers les clients connectés,
 * sans coupler le module planning au transport qui l'implémente (WebSocket).
 *
 * Aucune implémentation n'est fournie ici : {@code smartschool-api} enregistre
 * l'adaptateur. Le service solveur traite ce port comme optionnel — si aucun bean
 * n'est présent (tests du module isolé), la génération fonctionne à l'identique,
 * sans diffusion.
 */
public interface SolverProgressPublisher {

    /**
     * Publie un état d'avancement pour l'établissement donné.
     *
     * L'implémentation ne doit jamais propager d'exception : elle est appelée
     * depuis les threads du solveur, et un échec de diffusion ne doit pas
     * interrompre une génération en cours.
     */
    void publish(String tenantId, SolverProgress progress);

    /**
     * Instantané de l'avancement d'un job.
     *
     * @param jobId          identifiant du job de génération
     * @param status         statut courant du solveur
     * @param score          score Timefold formaté (ex. {@code "0hard/-12medium/-340soft"}), null tant qu'aucune solution n'a été trouvée
     * @param feasible       true si le score ne viole aucune contrainte dure ; null si le score est inconnu
     * @param placedSessions nombre de séances effectivement placées (créneau + salle affectés)
     * @param totalSessions  nombre total de séances à placer
     * @param errorMessage   message d'erreur, renseigné uniquement pour {@link SolverStatus#FAILED}
     * @param at             horodatage de l'instantané
     */
    record SolverProgress(
            Long jobId,
            SolverStatus status,
            String score,
            Boolean feasible,
            Integer placedSessions,
            Integer totalSessions,
            String errorMessage,
            Instant at
    ) {

        /** Instantané sans détail de solution — changement de statut simple. */
        public static SolverProgress status(Long jobId, SolverStatus status, String score) {
            return new SolverProgress(jobId, status, score, null, null, null, null, Instant.now());
        }

        /** Instantané d'échec, avec le message d'erreur du solveur. */
        public static SolverProgress failed(Long jobId, String errorMessage) {
            return new SolverProgress(jobId, SolverStatus.FAILED, null, null, null, null, errorMessage, Instant.now());
        }
    }
}
