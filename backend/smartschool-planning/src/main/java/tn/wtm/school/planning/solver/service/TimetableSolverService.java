package tn.wtm.school.planning.solver.service;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.planning.solver.builder.TimetableProblemBuilder;
import tn.wtm.school.planning.solver.constraint.ConstraintCodes;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.validation.PreGenerationValidator;
import tn.wtm.school.planning.solver.validation.TimetableBusinessValidator;
import tn.wtm.school.planning.solver.validation.ValidationFinding;
import tn.wtm.school.planning.solver.validation.ValidationReport;
import tn.wtm.school.planning.solver.domain.TimetableSession;
import tn.wtm.school.planning.solver.dto.request.MoveSessionRequest;
import tn.wtm.school.planning.solver.dto.request.TimetableGenerationRequest;
import tn.wtm.school.planning.solver.dto.response.ClassTimetableView;
import tn.wtm.school.planning.solver.dto.response.RoomTimetableView;
import tn.wtm.school.planning.solver.dto.response.TeacherTimetableView;
import tn.wtm.school.planning.constraints.dsl.CompiledConstraint;
import tn.wtm.school.planning.solver.dto.response.PreflightResponse;
import tn.wtm.school.planning.solver.dto.response.ScoreExplanationResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableJobResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableSessionResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableSolutionResponse;
import tn.wtm.school.planning.solver.dto.response.GeneratedTimetableResponse;
import tn.wtm.school.planning.solver.entity.GeneratedTimetable;
import tn.wtm.school.planning.solver.entity.TimetableJob;
import tn.wtm.school.planning.solver.enums.SolverStatus;
import tn.wtm.school.planning.solver.port.SolverProgressPublisher;
import tn.wtm.school.planning.solver.port.SolverProgressPublisher.SolverProgress;
import tn.wtm.school.planning.solver.repository.GeneratedTimetableRepository;
import tn.wtm.school.planning.solver.repository.TimetableJobRepository;
import tn.wtm.school.planning.solver.repository.TimetableSessionRepository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * {@code bestSolutions} garde en mémoire la dernière solution connue de chaque job, y compris
 * après sa fin (plus de purge à la terminaison), pour que {@link #explainScore(Long)} reste
 * utilisable après coup — notamment pour un job INFEASIBLE, dont l'admin doit pouvoir consulter
 * les conflits pour les corriger à la main.
 * Compromis assumé : la carte grandit avec le nombre de jobs exécutés depuis le démarrage de
 * l'instance (jamais purgée, sauf pour un job annulé). Acceptable tant que la génération de
 * planning reste une opération peu fréquente ; prévoir une éviction bornée (LRU/TTL) si le
 * volume de jobs devient significatif en production.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableSolverService extends TenantService {

    private final TimetableProblemBuilder        problemBuilder;
    private final PreGenerationValidator         preGenerationValidator;
    private final TimetableJobRepository         jobRepository;
    private final TimetableSessionRepository     sessionRepository;
    private final GeneratedTimetableRepository   generatedTimetableRepository;
    private final SolverManager<TimetableSolution, Long>                  solverManager;
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;
    private final TransactionTemplate            txTemplate;

    /**
     * Diffusion de l'avancement. Optionnelle et multiple : aucune implémentation
     * quand le module planning tourne seul (tests), une ou plusieurs dès que
     * {@code smartschool-api} enregistre ses adaptateurs (diffusion WebSocket,
     * création de notifications…). Chaque adaptateur est indépendant : l'échec
     * de l'un ne prive pas les autres de l'événement.
     */
    private final ObjectProvider<SolverProgressPublisher> progressPublishers;

    /**
     * Contrôle métier de l'emploi du temps produit — étape E du plan de mise en
     * conformité.
     *
     * <p>Instancié plutôt qu'injecté : c'est une fonction pure de la solution,
     * sans état, sans collaborateur et sans réglage. En faire un bean n'ouvrirait
     * la porte qu'à une chose — le remplacer par un double complaisant dans un
     * test, et perdre en silence la garantie qu'il apporte.
     */
    private final TimetableBusinessValidator businessValidator = new TimetableBusinessValidator();

    private final Map<Long, TimetableSolution>                bestSolutions = new ConcurrentHashMap<>();
    private final Map<Long, SolverJob<TimetableSolution, Long>> solverJobs  = new ConcurrentHashMap<>();

    /**
     * Dernière diffusion par job, en nanosecondes. Timefold appelle le listener à
     * chaque nouvelle meilleure solution — plusieurs fois par seconde en début de
     * résolution. On limite le débit à {@link #PROGRESS_THROTTLE_NANOS} : l'écran
     * n'a pas besoin de plus, et le broker ne doit pas devenir le goulot.
     */
    private final Map<Long, Long> lastProgressPublishNanos = new ConcurrentHashMap<>();

    /** Intervalle minimum entre deux diffusions de progression pour un même job. */
    private static final long PROGRESS_THROTTLE_NANOS = Duration.ofSeconds(1).toNanos();

    /** Nombre maximum d'exemples concrets renvoyés par contrainte violée dans l'explication. */
    private static final int MAX_EXAMPLES = 10;

    /** Libellés français des contraintes, pour l'affichage dans le panneau d'explication. */
    private static final Map<String, String> CONSTRAINT_LABELS = Map.ofEntries(
            Map.entry(ConstraintCodes.TEACHER_CONFLICT, "Conflit enseignant — deux séances en même temps"),
            Map.entry(ConstraintCodes.ROOM_CONFLICT, "Conflit de salle — deux séances dans la même salle"),
            Map.entry(ConstraintCodes.CLASS_CONFLICT, "Conflit de classe — deux séances en même temps"),
            Map.entry(ConstraintCodes.ROOM_CAPACITY, "Salle trop petite pour l'effectif"),
            Map.entry(ConstraintCodes.TEACHER_AVAILABILITY, "Enseignant indisponible ce jour-là"),
            Map.entry(ConstraintCodes.SPECIAL_ROOM_REQUIRED, "Salle spécialisée (labo/sport) requise non respectée"),
            Map.entry(ConstraintCodes.NORMAL_COURSE_NOT_IN_SPECIAL_ROOM, "Cours normal placé dans une salle spécialisée"),
            Map.entry(ConstraintCodes.ONE_TEACHER_PER_SUBJECT_PER_CLASS, "Deux enseignants différents pour la même matière/classe"),
            Map.entry(ConstraintCodes.PAIRED_DEMI_GROUP_SAME_SLOT, "Demi-groupes A/B non placés au même créneau"),
            Map.entry(ConstraintCodes.NO_LESSON_IN_BREAK_SLOT, "Séance placée pendant la pause déjeuner"),
            Map.entry(ConstraintCodes.LESSON_EXCEEDS_WORKING_BLOCK, "Séance qui déborde de la plage matin/après-midi"),
            Map.entry(ConstraintCodes.NO_STUDENT_IDLE_GAPS, "Trou dans l'emploi du temps d'une classe"),
            Map.entry(ConstraintCodes.MAX_TEACHER_HOURS_PER_DAY, "Enseignant dépasse son maximum d'heures par jour"),
            Map.entry(ConstraintCodes.MAX_STUDENT_HOURS_PER_DAY, "Classe dépasse le maximum d'heures par jour"),
            Map.entry(ConstraintCodes.MAX_TEACHER_HOURS_FRIDAY_SATURDAY, "Enseignant dépasse le maximum d'heures vendredi/samedi"),
            Map.entry(ConstraintCodes.MAX_TWO_CONSECUTIVE_SESSIONS, "Trop de séances consécutives de la même matière"),
            Map.entry(ConstraintCodes.THEORY_PRACTICE_SEPARATION, "Cours et TP de la même matière le même jour"),
            Map.entry(ConstraintCodes.BALANCED_MORNING_AFTERNOON, "Emploi du temps enseignant déséquilibré matin/après-midi"),
            Map.entry(ConstraintCodes.TEACHER_WEEKLY_REST_DAY, "Enseignant sans jour de repos dans la semaine"),
            Map.entry(ConstraintCodes.AVOID_SUBJECT_CONCENTRATION_SAME_DAY, "Matière concentrée plusieurs fois le même jour pour une classe"),
            // Règles de la circulaire n°66, câblées aux étapes C, D et I. Sans
            // libellé, le panneau d'explication affichait leur code brut.
            Map.entry(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS, "Volume horaire officiel non respecté (§ T.1)"),
            Map.entry(ConstraintCodes.PHYSICAL_EDUCATION_THREE_SESSIONS, "Découpage des séances d'EPS non conforme (§ III.2.b)"),
            Map.entry(ConstraintCodes.PHYSICAL_EDUCATION_SESSION_SPACING, "Deux séances d'EPS trop rapprochées (§ III.2.b)"),
            Map.entry(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS, "Matière à 2 h placée deux jours de suite (§ III.2.c)"),
            Map.entry(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY, "Demi-journée de moins de deux heures pour une classe (§ I.2)"),
            Map.entry(ConstraintCodes.MAIN_SUBJECTS_MORNING_QUOTA, "Matières fondamentales pas assez matinales (§ III.2.a)"),
            Map.entry(ConstraintCodes.CLASS_ROOM_STABILITY_PER_HALF_DAY, "Classe qui change de salle dans la demi-journée (§ I.4)"),
            Map.entry(ConstraintCodes.TEACHER_MIN_TWO_LEVELS, "Enseignant sur un seul niveau (§ II.5)"),
            Map.entry(ConstraintCodes.BALANCED_TEACHER_WORKLOAD, "Service de l'enseignant concentré sur quelques jours (§ II.2)"),
            Map.entry(ConstraintCodes.MAIN_SUBJECT_BALANCED_DISTRIBUTION, "Matière massée sur une seule période de la journée (§ III.1)"),
            Map.entry(ConstraintCodes.CUSTOM_RULE_HARD, "Règle personnalisée de l'établissement non respectée"),
            Map.entry(ConstraintCodes.CUSTOM_RULE_MEDIUM, "Règle personnalisée de l'établissement non respectée"),
            Map.entry(ConstraintCodes.CUSTOM_RULE_SOFT, "Préférence personnalisée non respectée"),
            Map.entry(ConstraintCodes.CUSTOM_REWARD_MEDIUM, "Préférence personnalisée satisfaite"),
            Map.entry(ConstraintCodes.CUSTOM_REWARD_SOFT, "Préférence personnalisée satisfaite"),
            Map.entry(ConstraintCodes.CUSTOM_LIMIT_HARD, "Seuil personnalisé dépassé"),
            Map.entry(ConstraintCodes.CUSTOM_LIMIT_MEDIUM, "Seuil personnalisé dépassé"),
            Map.entry(ConstraintCodes.CUSTOM_LIMIT_SOFT, "Seuil personnalisé dépassé")
    );

    /**
     * Une violation de règle personnalisée n'a pas de correctif universel : la
     * règle a été écrite par l'établissement lui-même. La seule suggestion
     * honnête est donc d'inviter à arbitrer entre la règle et les données, en
     * rappelant que l'assouplir est une option légitime.
     */
    private static final String CUSTOM_RULE_SUGGESTION =
            "Cette règle a été ajoutée par votre établissement. Déplacez les séances citées, "
                    + "ou assouplissez la règle (sévérité SOFT plutôt que HARD) si elle est trop stricte "
                    + "au vu des affectations actuelles.";

    private static final String CUSTOM_LIMIT_SUGGESTION =
            "Le seuil défini par votre établissement est dépassé. Répartissez les séances concernées "
                    + "sur d'autres jours, ou relevez le seuil de cette règle personnalisée.";

    /**
     * Suggestion concrète et actionnable par contrainte, affichée à côté de chaque
     * violation dans le panneau d'explication. Rédigée pour un administrateur non
     * technique : que faire concrètement (déplacer une séance, ajouter une salle,
     * revoir une affectation...), pas de jargon solveur. Statique et déterministe,
     * comme {@link #CONSTRAINT_LABELS} — aucune IA impliquée.
     */
    private static final Map<String, String> CONSTRAINT_SUGGESTIONS = Map.ofEntries(
            Map.entry(ConstraintCodes.TEACHER_CONFLICT,
                    "Déplacez l'une des deux séances vers un autre créneau depuis la Consultation du planning."),
            Map.entry(ConstraintCodes.ROOM_CONFLICT,
                    "Attribuez une salle différente à l'une des séances, ou ajoutez une salle du même type si elles sont trop peu nombreuses."),
            Map.entry(ConstraintCodes.CLASS_CONFLICT,
                    "Déplacez l'une des séances : une classe ne peut pas suivre deux cours en même temps."),
            Map.entry(ConstraintCodes.ROOM_CAPACITY,
                    "Choisissez une salle avec une capacité plus grande pour cette séance, ou scindez la classe en groupes."),
            Map.entry(ConstraintCodes.TEACHER_AVAILABILITY,
                    "Vérifiez les disponibilités déclarées de l'enseignant, ou déplacez la séance sur un jour où il est disponible."),
            Map.entry(ConstraintCodes.SPECIAL_ROOM_REQUIRED,
                    "Attribuez une salle spécialisée (labo/sport) à cette séance, ou vérifiez qu'il y en a assez pour ce créneau."),
            Map.entry(ConstraintCodes.NORMAL_COURSE_NOT_IN_SPECIAL_ROOM,
                    "Déplacez ce cours vers une salle normale pour libérer la salle spécialisée aux séances qui en ont réellement besoin."),
            Map.entry(ConstraintCodes.ONE_TEACHER_PER_SUBJECT_PER_CLASS,
                    "Vérifiez les affectations d'enseignement de cette classe : une seule personne devrait être affectée à cette matière."),
            Map.entry(ConstraintCodes.PAIRED_DEMI_GROUP_SAME_SLOT,
                    "Alignez manuellement les deux moitiés de groupe (A/B) sur le même créneau horaire."),
            Map.entry(ConstraintCodes.NO_LESSON_IN_BREAK_SLOT,
                    "Déplacez la séance en dehors de la pause déjeuner définie dans la configuration de l'établissement."),
            Map.entry(ConstraintCodes.LESSON_EXCEEDS_WORKING_BLOCK,
                    "Raccourcissez la séance ou déplacez-la pour qu'elle tienne entièrement dans la même demi-journée."),
            Map.entry(ConstraintCodes.NO_STUDENT_IDLE_GAPS,
                    "Resserrez les séances de cette classe pour supprimer les heures creuses entre deux cours."),
            Map.entry(ConstraintCodes.MAX_TEACHER_HOURS_PER_DAY,
                    "Répartissez certaines séances de cet enseignant sur d'autres jours, ou ajustez son plafond d'heures journalier si voulu."),
            Map.entry(ConstraintCodes.MAX_STUDENT_HOURS_PER_DAY,
                    "Répartissez certaines séances de cette classe sur d'autres jours de la semaine."),
            Map.entry(ConstraintCodes.MAX_TEACHER_HOURS_FRIDAY_SATURDAY,
                    "Déplacez une partie des séances de cet enseignant en semaine (lundi à jeudi)."),
            Map.entry(ConstraintCodes.MAX_TWO_CONSECUTIVE_SESSIONS,
                    "Espacez les séances de cette matière dans la semaine au lieu de les regrouper à la suite."),
            Map.entry(ConstraintCodes.THEORY_PRACTICE_SEPARATION,
                    "Placez le cours théorique et le TP de cette matière sur des jours différents."),
            Map.entry(ConstraintCodes.BALANCED_MORNING_AFTERNOON,
                    "Répartissez les séances de cet enseignant plus équitablement entre le matin et l'après-midi."),
            Map.entry(ConstraintCodes.TEACHER_WEEKLY_REST_DAY,
                    "Libérez au moins un jour complet dans la semaine pour cet enseignant."),
            Map.entry(ConstraintCodes.AVOID_SUBJECT_CONCENTRATION_SAME_DAY,
                    "Répartissez les séances de cette matière sur plusieurs jours au lieu de les concentrer le même jour."),
            Map.entry(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS,
                    "Comparez le programme appliqué à cette classe au § T.1 : un volume placé qui s'écarte du volume officiel signale une séance perdue, en double, ou une durée mal transcrite."),
            Map.entry(ConstraintCodes.PHYSICAL_EDUCATION_THREE_SESSIONS,
                    "L'EPS se donne en trois séances espacées, ou en deux séances de 2 h et 1 h. Ajustez le découpage du programme de la classe."),
            Map.entry(ConstraintCodes.PHYSICAL_EDUCATION_SESSION_SPACING,
                    "Espacez d'au moins vingt-quatre heures les séances d'EPS de cette classe."),
            Map.entry(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS,
                    "Écartez d'un jour au moins les deux séances de cette matière."),
            Map.entry(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY,
                    "Regroupez les séances de cette classe : la faire venir pour moins de deux heures dans une demi-journée n'est pas admis, sauf pour l'EPS."),
            Map.entry(ConstraintCodes.MAIN_SUBJECTS_MORNING_QUOTA,
                    "Placez davantage d'heures d'arabe, de français et de mathématiques le matin — le texte en réserve les trois quarts."),
            Map.entry(ConstraintCodes.CLASS_ROOM_STABILITY_PER_HALF_DAY,
                    "Gardez la même salle à cette classe sur toute la demi-journée, sauf pour les matières qui exigent une salle spécialisée."),
            Map.entry(ConstraintCodes.TEACHER_MIN_TWO_LEVELS,
                    "Confiez à cet enseignant des classes d'au moins deux niveaux différents."),
            Map.entry(ConstraintCodes.BALANCED_TEACHER_WORKLOAD,
                    "Étalez le service de cet enseignant sur davantage de journées, au lieu de le masser sur deux ou trois."),
            Map.entry(ConstraintCodes.MAIN_SUBJECT_BALANCED_DISTRIBUTION,
                    "Déplacez au moins une séance de cette matière de l'autre côté de la journée."),
            Map.entry(ConstraintCodes.CUSTOM_RULE_HARD, CUSTOM_RULE_SUGGESTION),
            Map.entry(ConstraintCodes.CUSTOM_RULE_MEDIUM, CUSTOM_RULE_SUGGESTION),
            Map.entry(ConstraintCodes.CUSTOM_RULE_SOFT, CUSTOM_RULE_SUGGESTION),
            Map.entry(ConstraintCodes.CUSTOM_LIMIT_HARD, CUSTOM_LIMIT_SUGGESTION),
            Map.entry(ConstraintCodes.CUSTOM_LIMIT_MEDIUM, CUSTOM_LIMIT_SUGGESTION),
            Map.entry(ConstraintCodes.CUSTOM_LIMIT_SOFT, CUSTOM_LIMIT_SUGGESTION)
    );


    private static final String DEFAULT_SUGGESTION =
            "Corrigez manuellement les séances concernées depuis la Consultation du planning, "
                    + "ou relancez la génération avec un profil de contraintes différent.";

    // ── contrôle avant génération ─────────────────────────────────────────────

    /**
     * Ce que les données disent avant qu'on lance quoi que ce soit.
     *
     * <p>Appelable à volonté : c'est ce qui rend une modification du programme —
     * un volume horaire changé, une matière ajoutée, une affectation retirée —
     * vérifiable tout de suite, au lieu d'attendre la fin d'une génération pour
     * apprendre qu'elle a été refusée.
     */
    @Transactional(readOnly = true)
    public PreflightResponse preflight(Long schoolYearId, Long constraintProfileId) {
        TimetableSolution probleme = problemBuilder.build(
                currentTenant(), schoolYearId, constraintProfileId);
        ValidationReport rapport = preGenerationValidator.valider(probleme);

        return PreflightResponse.builder()
                .ready(rapport.estConforme())
                .blockingCount(rapport.bloquants().size())
                .warningCount(rapport.avertissements().size())
                .findings(java.util.stream.Stream
                        .concat(rapport.bloquants().stream(), rapport.avertissements().stream())
                        .map(TimetableSolverService::toBusinessFinding)
                        .toList())
                .build();
    }

    // ── start ─────────────────────────────────────────────────────────────────

    @Transactional
    public TimetableJobResponse startGeneration(TimetableGenerationRequest req) {
        String tenantId = currentTenant();

        TimetableJob job = TimetableJob.builder()
                .academicYearId(req.getSchoolYearId())
                .constraintProfileId(req.getConstraintProfileId())
                .status(SolverStatus.PENDING)
                .build();
        job.setTenantId(tenantId);
        job = jobRepository.save(job);
        final Long jobId = job.getIdTimetableJob();

        TimetableSolution problem = problemBuilder.build(
                tenantId, req.getSchoolYearId(), req.getConstraintProfileId());

        // Rien ne servait de chercher trois minutes une solution que les données
        // rendent impossible. L'exception annule la transaction, donc le job créé
        // plus haut : l'historique ne garde pas trace d'une génération qui n'a
        // jamais commencé.
        ValidationReport avantVol = preGenerationValidator.valider(problem);
        if (!avantVol.estConforme()) {
            throw new BadRequestException(
                    "La génération n'a pas été lancée : les données de cette année scolaire "
                            + "comportent " + avantVol.bloquants().size()
                            + " anomalie(s) bloquante(s).\n" + avantVol.resume());
        }

        bestSolutions.put(jobId, problem);

        final String capturedTenant = tenantId;

        // solveAndListen(ProblemId_, Solution_, Consumer) — direct Solution_ overload
        SolverJob<TimetableSolution, Long> solverJob =
                solverManager.solveAndListen(jobId, problem,
                        solution -> onBestSolution(jobId, capturedTenant, solution));
        solverJobs.put(jobId, solverJob);

        CompletableFuture.runAsync(() -> {
            TenantContext.setTenantId(capturedTenant);
            try {
                TimetableSolution finalSolution = solverJob.getFinalBestSolution();
                persistResult(jobId, capturedTenant, finalSolution);
            } catch (Throwable ex) {
                // Throwable et pas Exception : une Error ici (OutOfMemoryError,
                // NoClassDefFoundError…) n'etait rattrapee par personne. Le
                // CompletableFuture l'avalait, le job restait RUNNING pour
                // toujours, sans ligne de log ni fin — impossible a diagnostiquer
                // depuis l'interface.
                //
                // getFinalBestSolution() est bloquant : il leve une
                // InterruptedException quand le pool d'execution est arrete
                // (fermeture du contexte Spring, typiquement). L'attraper sans
                // reposer le drapeau efface l'ordre d'arret pour tout ce qui
                // s'executera ensuite sur ce thread, qui appartient au pool
                // commun — le thread ne saurait plus qu'on lui a demande de
                // s'arreter. On le repose avant de traiter l'echec.
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                onFailed(jobId, capturedTenant, ex);
            } finally {
                TenantContext.clear();
                solverJobs.remove(jobId);
                lastProgressPublishNanos.remove(jobId);
            }
        });

        job.setStatus(SolverStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setSolverJobId(String.valueOf(jobId));
        jobRepository.save(job);

        log.info("[planning] Job {} started — tenant={} year={}", jobId, tenantId, req.getSchoolYearId());
        publishProgress(tenantId, SolverProgress.status(jobId, SolverStatus.RUNNING, null));
        return toJobResponse(job);
    }

    // ── query ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TimetableJobResponse getJobStatus(Long jobId) {
        return toJobResponse(loadJob(jobId, currentTenant()));
    }

    @Transactional(readOnly = true)
    public TimetableSolutionResponse getSolution(Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);
        List<TimetableSession> sessions = sessionRepository.findByJobIdAndTenantId(jobId, tenantId);

        return TimetableSolutionResponse.builder()
                .jobId(jobId).academicYearId(job.getAcademicYearId())
                .status(job.getStatus()).scoreAchieved(job.getScoreAchieved())
                .totalSessions(sessions.size())
                .sessions(sessions.stream().map(this::toSessionResponse).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public ScoreExplanationResponse explainScore(Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);
        TimetableSolution solution = bestSolutions.get(jobId);

        if (solution == null || solution.getScore() == null) {
            ScoreExplanationResponse.ConstraintViolation unavailable =
                    ScoreExplanationResponse.ConstraintViolation.builder()
                            .constraintName("N/A").label("Détails indisponibles")
                            .score(job.getScoreAchieved()).count(0)
                            .examples(List.of(
                                    "Le détail des violations n'est plus en mémoire "
                                            + "(instance backend redémarrée depuis, ou job annulé). "
                                            + "Score archivé : " + job.getScoreAchieved()))
                            // Rien à désigner : la solution n'est plus en mémoire, donc
                            // aucun ConstraintMatch n'existe. Liste vide et non `null` —
                            // l'interface parcourt ce champ sans le tester.
                            .occurrences(List.of())
                            .suggestion("Relancez une génération pour obtenir un détail à jour des conflits.")
                            .build();
            return ScoreExplanationResponse.builder()
                    .jobId(jobId).score(job.getScoreAchieved()).feasible(false)
                    .hardViolations(List.of(unavailable))
                    .mediumViolations(List.of()).softViolations(List.of())
                    // Le rapport métier, lui, a été archivé sur le job : il reste
                    // lisible quand le détail Timefold ne l'est plus. C'est
                    // précisément le cas que la colonne validation_report sert.
                    .businessValidation(constatsArchives(job))
                    .businessValid(job.getValidationReport() == null)
                    .build();
        }

        ScoreExplanation<TimetableSolution, HardMediumSoftScore> explanation =
                solutionManager.explain(solution);
        HardMediumSoftScore score = explanation.getScore();
        ValidationReport    validation = businessValidator.valider(solution);

        // Le pont entre ce que Timefold incrimine et ce que l'interface sait
        // manipuler. Lu une fois pour les trois niveaux : une contrainte violée
        // cent fois ne doit pas provoquer cent requêtes.
        Map<Long, Long> sessionIdByLessonId = sessionRepository
                .findByJobIdAndTenantId(jobId, tenantId).stream()
                .filter(s -> s.getLessonId() != null)
                .collect(Collectors.toMap(
                        TimetableSession::getLessonId,
                        TimetableSession::getIdTimetableSession,
                        // Deux lignes pour un même lessonId ne devrait pas exister.
                        // Si cela arrive, on garde la première plutôt que de faire
                        // échouer une explication sur une anomalie de données.
                        (premiere, doublon) -> premiere));

        // Construit une fois pour les trois niveaux : il indexe l'emploi du temps
        // par jour, et le refaire à chaque violation rendrait l'explication
        // quadratique sur un établissement chargé.
        AlternativeSlotFinder finder = new AlternativeSlotFinder(solution);

        return ScoreExplanationResponse.builder()
                .jobId(jobId).score(score.toString())
                // « Faisable » veut dire « remettable », ici comme sur le job.
                .feasible(score.isFeasible() && validation.estConforme())
                .hardViolations(explainLevel(explanation, "HARD", sessionIdByLessonId, finder))
                .mediumViolations(explainLevel(explanation, "MEDIUM", sessionIdByLessonId, finder))
                .softViolations(explainLevel(explanation, "SOFT", sessionIdByLessonId, finder))
                .businessValidation(validation.findings().stream()
                        .map(TimetableSolverService::toBusinessFinding)
                        .toList())
                .businessValid(validation.estConforme())
                .build();
    }

    private static ScoreExplanationResponse.BusinessFinding toBusinessFinding(ValidationFinding f) {
        return ScoreExplanationResponse.BusinessFinding.builder()
                .severity(f.severity().name())
                .code(f.code())
                .scope(f.scope())
                .message(f.message())
                .build();
    }

    /**
     * Le rapport archivé, rendu sous la même forme que les constats vivants.
     *
     * <p>Il a perdu sa structure en devenant du texte : on le restitue en une
     * seule entrée plutôt que de tenter de le réanalyser. Un rapport relu est un
     * rapport, pas une liste de constats — prétendre le contraire donnerait des
     * champs {@code code} et {@code scope} inventés.
     */
    private static List<ScoreExplanationResponse.BusinessFinding> constatsArchives(TimetableJob job) {
        if (job.getValidationReport() == null || job.getValidationReport().isBlank()) {
            return List.of();
        }
        return List.of(ScoreExplanationResponse.BusinessFinding.builder()
                .severity("BLOQUANT")
                .code("RAPPORT_ARCHIVE")
                .scope("validation métier")
                .message(job.getValidationReport())
                .build());
    }

    // ── list jobs ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TimetableJobResponse> listJobs(Long academicYearId) {
        String tenantId = currentTenant();
        List<TimetableJob> jobs = academicYearId != null
                ? jobRepository.findByTenantIdAndAcademicYearIdOrderByIdTimetableJobDesc(tenantId, academicYearId)
                : jobRepository.findByTenantIdOrderByIdTimetableJobDesc(tenantId);
        return jobs.stream().map(this::toJobResponse).toList();
    }

    // ── generated timetables ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GeneratedTimetableResponse getResult(Long jobId) {
        String tenantId = currentTenant();
        loadJob(jobId, tenantId);  // valide l'appartenance au tenant
        return generatedTimetableRepository
                .findByJobIdAndTenantId(jobId, tenantId)
                .map(this::toGeneratedTimetableResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Résultat introuvable pour le job : " + jobId));
    }

    @Transactional(readOnly = true)
    public List<GeneratedTimetableResponse> listGeneratedTimetables(Long academicYearId) {
        String tenantId = currentTenant();
        List<GeneratedTimetable> list = academicYearId != null
                ? generatedTimetableRepository
                        .findByTenantIdAndAcademicYearIdOrderByIdGeneratedTimetableDesc(tenantId, academicYearId)
                : generatedTimetableRepository
                        .findByTenantIdOrderByIdGeneratedTimetableDesc(tenantId);
        return list.stream().map(this::toGeneratedTimetableResponse).toList();
    }

    @Transactional
    public GeneratedTimetableResponse publishTimetable(Long id) {
        String tenantId = currentTenant();
        GeneratedTimetable timetable = generatedTimetableRepository
                .findByIdGeneratedTimetableAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Emploi du temps introuvable avec l'ID : " + id));

        if ("ARCHIVED".equals(timetable.getStatus())) {
            throw new BadRequestException("Un emploi du temps archivé ne peut pas être publié");
        }
        timetable.setStatus("PUBLISHED");
        timetable.setPublishedAt(Instant.now());
        log.info("[planning] Timetable {} published — tenant={}", id, tenantId);
        return toGeneratedTimetableResponse(generatedTimetableRepository.save(timetable));
    }

    /**
     * Deplace manuellement une seance (jour/heure/salle) — edition admin apres generation.
     * L'enseignant, la matiere et la classe restent fixes (memes garanties que le solveur :
     * seuls timeSlot et room sont des variables de decision).
     * Ne recree pas de lien avec la moitie demi-groupe eventuelle (non persistee en base).
     */
    @Transactional
    public TimetableSessionResponse moveSession(Long jobId, Long sessionId, MoveSessionRequest req) {
        String tenantId = currentTenant();
        loadJob(jobId, tenantId);

        TimetableSession session = sessionRepository.findByIdTimetableSessionAndTenantId(sessionId, tenantId)
                .filter(s -> s.getJobId().equals(jobId))
                .orElseThrow(() -> new ResourceNotFoundException("Seance introuvable avec l'ID : " + sessionId));

        generatedTimetableRepository.findByJobIdAndTenantId(jobId, tenantId).ifPresent(gt -> {
            if ("ARCHIVED".equals(gt.getStatus())) {
                throw new BadRequestException("Un emploi du temps archive ne peut pas etre modifie");
            }
        });

        Duration duration = Duration.between(session.getStartTime(), session.getEndTime());
        LocalTime newStart = req.getStartTime();
        LocalTime newEnd = newStart.plus(duration);
        String newRoomCode = req.getRoomCode() != null ? req.getRoomCode() : session.getRoomCode();

        List<TimetableSession> overlapping = sessionRepository.findByJobIdAndTenantId(jobId, tenantId).stream()
                .filter(s -> !s.getIdTimetableSession().equals(sessionId))
                .filter(s -> s.getDay() == req.getDay())
                .filter(s -> overlapsInTime(s.getStartTime(), s.getEndTime(), newStart, newEnd))
                .toList();

        for (TimetableSession other : overlapping) {
            // Les deux moitiees d'une seance en demi-groupe (groupIndex 1/2) partagent
            // volontairement le meme prof/classe/horaire (le solveur les genere ainsi) —
            // ce n'est pas un conflit, seule une collision de salle l'est encore pour elles.
            boolean demiGroupSibling = session.getGroupIndex() > 0 && other.getGroupIndex() > 0
                    && session.getGroupIndex() != other.getGroupIndex()
                    && java.util.Objects.equals(session.getStudentClassName(), other.getStudentClassName())
                    && java.util.Objects.equals(session.getSubjectCode(), other.getSubjectCode());

            if (!demiGroupSibling && other.getTeacherCode() != null
                    && other.getTeacherCode().equals(session.getTeacherCode())) {
                throw new ConflictException(
                        "L'enseignant " + session.getTeacherName() + " a deja une seance a cette heure");
            }
            if (newRoomCode != null && newRoomCode.equals(other.getRoomCode())) {
                throw new ConflictException("La salle " + newRoomCode + " est deja occupee a cette heure");
            }
            if (!demiGroupSibling && other.getStudentClassName() != null
                    && other.getStudentClassName().equals(session.getStudentClassName())) {
                throw new ConflictException(
                        "La classe " + session.getStudentClassName() + " a deja une seance a cette heure");
            }
        }

        session.setDay(req.getDay());
        session.setStartTime(newStart);
        session.setEndTime(newEnd);
        session.setRoomCode(newRoomCode);
        if (req.getRoomType() != null) {
            session.setRoomType(req.getRoomType());
        }

        log.info("[planning] Session {} deplacee -> {} {} (salle {}) — tenant={}",
                sessionId, req.getDay(), newStart, newRoomCode, tenantId);
        return toSessionResponse(sessionRepository.save(session));
    }

    private static boolean overlapsInTime(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    // ── vues consultation (classe / enseignant / salle) ───────────────────────

    /** Ordre d'affichage de la semaine — le dimanche n'est jamais planifié. */
    private static final List<DayOfWeek> WEEK_ORDER = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    @Transactional(readOnly = true)
    public ClassTimetableView getClassTimetable(Long jobId, String classCode) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        List<TimetableSession> sessions = sessionRepository
                .findByJobIdAndTenantIdAndStudentClassName(jobId, tenantId, classCode);
        requireFinishedWhenEmpty(sessions, job);

        return toClassView(jobId, job, classCode, sessions);
    }

    /**
     * Toutes les classes du job en une seule requête SQL.
     *
     * L'écran de consultation propose « Toutes les classes » (impression d'un
     * jeu complet de grilles) : appeler N fois {@link #getClassTimetable} ferait
     * N allers-retours base pour des données déjà toutes contenues dans le même
     * job. On charge donc les séances une fois, puis on regroupe en mémoire.
     * Classes triées par code, pour un ordre d'impression stable.
     */
    @Transactional(readOnly = true)
    public List<ClassTimetableView> getAllClassTimetables(Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        List<TimetableSession> sessions = sessionRepository.findByJobIdAndTenantId(jobId, tenantId);
        requireFinishedWhenEmpty(sessions, job);

        return groupBy(sessions, TimetableSession::getStudentClassName).entrySet().stream()
                .map(e -> toClassView(jobId, job, e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherTimetableView getTeacherTimetable(Long jobId, String teacherCode) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        List<TimetableSession> sessions = sessionRepository
                .findByJobIdAndTenantIdAndTeacherCode(jobId, tenantId, teacherCode);
        requireFinishedWhenEmpty(sessions, job);

        return toTeacherView(jobId, job, teacherCode, sessions);
    }

    /** Voir {@link #getAllClassTimetables} — même principe, groupé par enseignant. */
    @Transactional(readOnly = true)
    public List<TeacherTimetableView> getAllTeacherTimetables(Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        List<TimetableSession> sessions = sessionRepository.findByJobIdAndTenantId(jobId, tenantId);
        requireFinishedWhenEmpty(sessions, job);

        return groupBy(sessions, TimetableSession::getTeacherCode).entrySet().stream()
                .map(e -> toTeacherView(jobId, job, e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomTimetableView getRoomTimetable(Long jobId, String roomCode) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        List<TimetableSession> sessions = sessionRepository
                .findByJobIdAndTenantIdAndRoomCode(jobId, tenantId, roomCode);
        requireFinishedWhenEmpty(sessions, job);

        return toRoomView(jobId, job, roomCode, sessions);
    }

    /** Voir {@link #getAllClassTimetables} — même principe, groupé par salle. */
    @Transactional(readOnly = true)
    public List<RoomTimetableView> getAllRoomTimetables(Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        List<TimetableSession> sessions = sessionRepository.findByJobIdAndTenantId(jobId, tenantId);
        requireFinishedWhenEmpty(sessions, job);

        return groupBy(sessions, TimetableSession::getRoomCode).entrySet().stream()
                .map(e -> toRoomView(jobId, job, e.getKey(), e.getValue()))
                .toList();
    }

    // ── assemblage des vues ───────────────────────────────────────────────────

    private ClassTimetableView toClassView(Long jobId, TimetableJob job, String classCode,
                                           List<TimetableSession> sessions) {
        List<ClassTimetableView.DaySchedule> schedule = groupByDay(sessions).entrySet().stream()
                .map(e -> new ClassTimetableView.DaySchedule(
                        e.getKey(),
                        dayLabel(e.getKey()),
                        e.getValue().stream().map(this::toSessionView).toList()))
                .toList();

        return new ClassTimetableView(jobId, classCode, job.getStatus(),
                job.getScoreAchieved(), schedule);
    }

    private TeacherTimetableView toTeacherView(Long jobId, TimetableJob job, String teacherCode,
                                               List<TimetableSession> sessions) {
        String teacherName = sessions.stream()
                .map(TimetableSession::getTeacherName)
                .filter(n -> n != null && !n.isBlank())
                .findFirst().orElse(teacherCode);

        List<TeacherTimetableView.DaySchedule> schedule = groupByDay(sessions).entrySet().stream()
                .map(e -> new TeacherTimetableView.DaySchedule(
                        e.getKey(),
                        dayLabel(e.getKey()),
                        e.getValue().stream().map(this::toTeacherSessionView).toList()))
                .toList();

        return new TeacherTimetableView(jobId, teacherCode, teacherName,
                job.getStatus(), job.getScoreAchieved(), schedule);
    }

    private RoomTimetableView toRoomView(Long jobId, TimetableJob job, String roomCode,
                                         List<TimetableSession> sessions) {
        String roomType = sessions.stream()
                .filter(s -> s.getRoomType() != null)
                .map(s -> s.getRoomType().name())
                .findFirst().orElse(null);

        List<RoomTimetableView.DaySchedule> schedule = groupByDay(sessions).entrySet().stream()
                .map(e -> new RoomTimetableView.DaySchedule(
                        e.getKey(),
                        dayLabel(e.getKey()),
                        e.getValue().stream().map(this::toRoomSessionView).toList()))
                .toList();

        return new RoomTimetableView(jobId, roomCode, roomType,
                job.getStatus(), job.getScoreAchieved(), schedule);
    }

    /**
     * Une grille vide est normale pour un job terminé (classe sans séance), mais
     * trompeuse tant que le solver tourne : on préfère alors un message explicite
     * à un emploi du temps vide qui passerait pour un résultat.
     */
    private static void requireFinishedWhenEmpty(List<TimetableSession> sessions, TimetableJob job) {
        if (sessions.isEmpty() && !job.getStatus().isFinished()) {
            throw new BadRequestException(
                    "Le job n'est pas encore terminé. Statut actuel : " + job.getStatus());
        }
    }

    /** Regroupe par code (classe/enseignant/salle), en ignorant les séances non affectées. */
    private static Map<String, List<TimetableSession>> groupBy(
            List<TimetableSession> sessions, java.util.function.Function<TimetableSession, String> key) {
        return sessions.stream()
                .filter(s -> key.apply(s) != null && !key.apply(s).isBlank())
                .collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
    }

    /** Séances d'une grille : par jour dans l'ordre de la semaine, puis par heure. */
    private static Map<DayOfWeek, List<TimetableSession>> groupByDay(List<TimetableSession> sessions) {
        return sessions.stream()
                .sorted(Comparator.comparing(TimetableSession::getDay,
                                Comparator.comparingInt(WEEK_ORDER::indexOf))
                        .thenComparing(TimetableSession::getStartTime))
                .collect(Collectors.groupingBy(
                        TimetableSession::getDay,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private RoomTimetableView.SessionView toRoomSessionView(TimetableSession s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        return new RoomTimetableView.SessionView(
                s.getIdTimetableSession(),
                s.getStartTime().format(fmt),
                s.getEndTime().format(fmt),
                formatDuration(s.getStartTime(), s.getEndTime()),
                s.getSubjectCode(),
                s.getSubjectName(),
                s.getStudentClassName(),
                s.getTeacherCode(),
                s.getTeacherName(),
                s.getSessionType() != null ? s.getSessionType().name() : null,
                s.getGroupIndex(),
                groupLabel(s.getGroupIndex())
        );
    }

    private TeacherTimetableView.SessionView toTeacherSessionView(TimetableSession s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        return new TeacherTimetableView.SessionView(
                s.getIdTimetableSession(),
                s.getStartTime().format(fmt),
                s.getEndTime().format(fmt),
                formatDuration(s.getStartTime(), s.getEndTime()),
                s.getSubjectCode(),
                s.getSubjectName(),
                s.getStudentClassName(),
                s.getRoomCode(),
                s.getRoomType() != null ? s.getRoomType().name() : null,
                s.getSessionType() != null ? s.getSessionType().name() : null,
                s.getGroupIndex(),
                groupLabel(s.getGroupIndex())
        );
    }

    private ClassTimetableView.SessionView toSessionView(TimetableSession s) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        return new ClassTimetableView.SessionView(
                s.getIdTimetableSession(),
                s.getStartTime().format(fmt),
                s.getEndTime().format(fmt),
                formatDuration(s.getStartTime(), s.getEndTime()),
                s.getSubjectCode(),
                s.getSubjectName(),
                s.getTeacherCode(),
                s.getTeacherName(),
                s.getRoomCode(),
                s.getRoomType() != null ? s.getRoomType().name() : null,
                s.getSessionType() != null ? s.getSessionType().name() : null,
                s.getGroupIndex(),
                groupLabel(s.getGroupIndex())
        );
    }

    private static String formatDuration(LocalTime start, LocalTime end) {
        int totalMinutes = (int) java.time.Duration.between(start, end).toMinutes();
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return hours + "h" + (minutes == 0 ? "00" : String.format("%02d", minutes));
    }

    private static String dayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY    -> "Lundi";
            case TUESDAY   -> "Mardi";
            case WEDNESDAY -> "Mercredi";
            case THURSDAY  -> "Jeudi";
            case FRIDAY    -> "Vendredi";
            case SATURDAY  -> "Samedi";
            default        -> day.name();
        };
    }

    private static String groupLabel(int groupIndex) {
        return switch (groupIndex) {
            case 1  -> "Groupe A";
            case 2  -> "Groupe B";
            default -> "Classe entière";
        };
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Transactional
    public TimetableJobResponse cancelJob(Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        if (job.getStatus() == SolverStatus.RUNNING) {
            SolverJob<TimetableSolution, Long> running = solverJobs.get(jobId);
            if (running != null) running.terminateEarly();
            job.setStatus(SolverStatus.CANCELLED);
            job.setFinishedAt(Instant.now());
            job = jobRepository.save(job);
            bestSolutions.remove(jobId);
            log.info("[planning] Job {} cancelled — tenant={}", jobId, tenantId);
            publishProgress(tenantId, SolverProgress.status(jobId, SolverStatus.CANCELLED, job.getScoreAchieved()));
        }
        return toJobResponse(job);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    /**
     * Supprime un job de génération et tout ce qui en dépend (séances, résultat
     * agrégé). Un job {@code RUNNING}/{@code PENDING} doit d'abord être annulé
     * ({@link #cancelJob}) ; un emploi du temps {@code PUBLISHED} ne peut pas
     * être supprimé tant qu'il est diffusé — l'admin doit d'abord en publier un
     * autre ou attendre son archivage.
     */
    @Transactional
    public void deleteJob(Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = loadJob(jobId, tenantId);

        if (job.getStatus() == SolverStatus.RUNNING || job.getStatus() == SolverStatus.PENDING) {
            throw new BadRequestException(
                    "Ce job est en cours d'exécution. Annulez-le d'abord avant de le supprimer.");
        }

        generatedTimetableRepository.findByJobIdAndTenantId(jobId, tenantId).ifPresent(gt -> {
            if ("PUBLISHED".equals(gt.getStatus())) {
                throw new BadRequestException(
                        "Cet emploi du temps est publié et visible par les enseignants et les classes. "
                                + "Publiez-en un autre à sa place avant de le supprimer.");
            }
            generatedTimetableRepository.delete(gt);
        });

        sessionRepository.deleteByJobIdAndTenantId(jobId, tenantId);
        jobRepository.delete(job);
        bestSolutions.remove(jobId);

        log.info("[planning] Job {} deleted — tenant={}", jobId, tenantId);
    }

    // ── callbacks ─────────────────────────────────────────────────────────────

    /**
     * Appelé par Timefold sur un de ses threads à chaque nouvelle meilleure solution.
     * Le {@link TenantContext} n'y est pas positionné : le tenant est celui capturé
     * au lancement du job, jamais celui d'une éventuelle requête concurrente.
     */
    private void onBestSolution(Long jobId, String tenantId, TimetableSolution solution) {
        bestSolutions.put(jobId, solution);
        if (solution.getScore() != null) {
            log.debug("[planning] Job {} new best: {}", jobId, solution.getScore());
        }
        if (shouldPublishProgress(jobId)) {
            publishProgress(tenantId, toProgress(jobId, SolverStatus.RUNNING, solution));
        }
    }

    /**
     * Limite le débit de diffusion à un message par seconde et par job.
     * Un intervalle écoulé est réservé de façon atomique, pour que deux threads
     * du solveur ne publient pas le même tick.
     */
    private boolean shouldPublishProgress(Long jobId) {
        long now = System.nanoTime();
        Long previous = lastProgressPublishNanos.get(jobId);
        if (previous != null && now - previous < PROGRESS_THROTTLE_NANOS) {
            return false;
        }
        return previous == null
                ? lastProgressPublishNanos.putIfAbsent(jobId, now) == null
                : lastProgressPublishNanos.replace(jobId, previous, now);
    }

    /**
     * Construit un instantané de progression à partir d'une solution du solveur.
     *
     * <p>Pendant la résolution, « faisable » ne peut vouloir dire que ce que le
     * score en dit : lancer la validation métier à chaque nouvelle meilleure
     * solution — plusieurs fois par seconde — coûterait plus cher que la
     * résolution elle-même.
     */
    private SolverProgress toProgress(Long jobId, SolverStatus status, TimetableSolution solution) {
        HardMediumSoftScore score = solution.getScore();
        return toProgress(jobId, status, solution, score != null ? score.isFeasible() : null);
    }

    /**
     * Même instantané, avec le verdict définitif imposé de l'extérieur.
     *
     * <p>À la fin du job, « faisable » doit vouloir dire « remettable » : score
     * faisable <em>et</em> validation métier conforme. Publier la faisabilité
     * Timefold seule afficherait un emploi du temps « faisable » à côté d'un
     * statut INFEASIBLE, ce que personne ne saurait interpréter.
     */
    private SolverProgress toProgress(Long jobId, SolverStatus status,
                                      TimetableSolution solution, Boolean feasible) {
        HardMediumSoftScore score = solution.getScore();
        List<Lesson> lessons = solution.getLessons() != null ? solution.getLessons() : List.of();
        int placed = (int) lessons.stream()
                .filter(l -> l.getTimeSlot() != null && l.getRoom() != null)
                .count();
        return new SolverProgress(
                jobId,
                status,
                score != null ? score.toString() : null,
                feasible,
                placed,
                lessons.size(),
                null,
                Instant.now());
    }

    /**
     * Le résumé de la validation, ou {@code null} quand il n'y a rien à dire.
     *
     * <p>Une colonne vide et une colonne contenant une chaîne vide ne se lisent
     * pas de la même façon dans un rapport : la première dit « rien à signaler »,
     * la seconde « on a essayé de dire quelque chose et on a échoué ».
     */
    private static String resumeOuNull(ValidationReport rapport) {
        String resume = rapport.resume();
        return resume.isEmpty() ? null : resume;
    }

    /**
     * Diffuse un instantané à tous les adaptateurs enregistrés. Chaque erreur est
     * absorbée individuellement : une génération ne doit jamais échouer parce
     * qu'un client WebSocket est injoignable, et un adaptateur en échec ne doit
     * pas empêcher les suivants de recevoir l'événement.
     */
    private void publishProgress(String tenantId, SolverProgress progress) {
        progressPublishers.orderedStream().forEach(publisher -> {
            try {
                publisher.publish(tenantId, progress);
            } catch (Exception ex) {
                log.warn("[planning] Job {} — diffusion de la progression échouée ({}): {}",
                        progress.jobId(), publisher.getClass().getSimpleName(), ex.getMessage());
            }
        });
    }

    private void onFailed(Long jobId, String tenantId, Throwable ex) {
        log.error("[planning] Job {} failed — tenant={}: {}", jobId, tenantId, ex.getMessage(), ex);
        txTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                jobRepository.findById(jobId).ifPresent(job -> {
                    job.setStatus(SolverStatus.FAILED);
                    job.setFinishedAt(Instant.now());
                    job.setErrorMessage(truncate(ex.getMessage(), 900));
                    jobRepository.save(job);
                });
            }
        });
        // bestSolutions volontairement conservée (pas de remove ici) : un job en échec est
        // justement celui qu'on veut pouvoir expliquer via /score-explanation après coup.
        publishProgress(tenantId, SolverProgress.failed(jobId, truncate(ex.getMessage(), 900)));
    }

    // ── persistence ───────────────────────────────────────────────────────────

    /**
     * Finalise un job solver : met à jour le {@link TimetableJob}, persiste les
     * {@link TimetableSession}s et crée (ou met à jour) le {@link GeneratedTimetable}.
     *
     * Statut résultant :
     * <ul>
     *   <li>{@link SolverStatus#SOLVED}     — score feasible (0 violation hard)</li>
     *   <li>{@link SolverStatus#INFEASIBLE} — solveur terminé, violations hard restantes</li>
     * </ul>
     * Dans les deux cas l'emploi du temps est complet et exploitable : toutes les
     * séances sont placées, seules certaines contraintes restent violées. L'admin
     * consulte les conflits via /score-explanation puis corrige à la main
     * (voir {@link #moveSession}). {@link SolverStatus#FAILED} est réservé aux
     * plantages du solveur, où il n'y a aucun résultat à montrer.
     */
    protected void persistResult(Long jobId, String tenantId, TimetableSolution solution) {
        TimetableSolution best = bestSolutions.getOrDefault(jobId, solution);
        AtomicReference<SolverStatus>  finalStatus   = new AtomicReference<>();
        // Le verdict retenu, et non la seule faisabilité Timefold : l'écran ne doit
        // pas afficher « faisable » sur un job que la validation métier a refusé.
        AtomicReference<Boolean>       finalFeasible = new AtomicReference<>();
        // TenantContext already set by the CompletableFuture lambda in startGeneration()
        txTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                jobRepository.findById(jobId).ifPresent(job -> {

                    HardMediumSoftScore score    = best.getScore();
                    boolean             feasible = score != null && score.isFeasible();

                    // Deuxième avis, indépendant du solveur et du profil : le score
                    // ne dit que « aucune des contraintes activées n'est violée ».
                    // Voir TimetableBusinessValidator pour ce que cela laissait passer.
                    ValidationReport validation = businessValidator.valider(best);
                    boolean          retenu     = feasible && validation.estConforme();

                    // 1. Mise à jour du job
                    job.setStatus(retenu ? SolverStatus.SOLVED : SolverStatus.INFEASIBLE);
                    job.setFinishedAt(Instant.now());
                    if (score != null) job.setScoreAchieved(score.toString());
                    job.setValidationReport(resumeOuNull(validation));
                    jobRepository.save(job);

                    // 2. Sauvegarde des sessions
                    int sessionCount = saveSessions(jobId, tenantId, best);

                    // 3. Création / mise à jour du GeneratedTimetable
                    saveGeneratedTimetable(job, tenantId, score, sessionCount, retenu);

                    // 4. bestSolutions volontairement conservée : /score-explanation doit
                    // rester utilisable après la fin du job (notamment pour les jobs INFEASIBLE,
                    // le cas d'usage principal du panneau d'explication). Compromis mémoire
                    // assumé (voir doc de la classe) — à revoir avec une éviction bornée
                    // (LRU/TTL) si le volume de jobs devient un problème en pratique.

                    log.info("[planning] Job {} {} — score={} sessions={} feasible={} conforme={}",
                            jobId, job.getStatus(), job.getScoreAchieved(), sessionCount,
                            feasible, validation.estConforme());
                    if (!validation.estConforme()) {
                        // En WARN et en entier : c'est la seule trace serveur du
                        // motif de refus, et elle doit survivre au job en mémoire.
                        log.warn("[planning] Job {} refusé par la validation métier :\n{}",
                                jobId, validation.resume());
                    }

                    finalStatus.set(job.getStatus());
                    finalFeasible.set(retenu);
                });
            }
        });

        // Diffusé après commit : le client qui réagit à ce message et recharge le job
        // via l'API doit lire le statut définitif, pas celui d'avant la transaction.
        SolverStatus status = finalStatus.get();
        if (status != null) {
            publishProgress(tenantId, toProgress(jobId, status, best, finalFeasible.get()));
        }
    }

    /**
     * Supprime les sessions existantes du job puis insère celles produites par le solver.
     * Seules les leçons entièrement placées (timeSlot + room non nuls) sont conservées.
     *
     * @return nombre de sessions persistées
     */
    private int saveSessions(Long jobId, String tenantId, TimetableSolution solution) {
        sessionRepository.deleteByJobIdAndTenantId(jobId, tenantId);

        List<TimetableSession> sessions = solution.getLessons().stream()
                .filter(l -> l.getTimeSlot() != null && l.getRoom() != null)
                .map(l -> toSession(l, jobId, solution.getAcademicYearId(), tenantId))
                .toList();

        sessionRepository.saveAll(sessions);
        return sessions.size();
    }

    /**
     * Crée ou met à jour le {@link GeneratedTimetable} associé au job.
     * L'upsert est basé sur {@code (job_id, tenant_id)} — si un enregistrement
     * existe déjà (re-run d'un job), il est mis à jour plutôt que dupliqué.
     *
     * Le statut initial est toujours {@code DRAFT} : la publication est une
     * action explicite de l'administrateur.
     */
    private void saveGeneratedTimetable(TimetableJob job, String tenantId,
                                         HardMediumSoftScore score, int sessionCount,
                                         boolean retenu) {
        Long jobId = job.getIdTimetableJob();

        // Upsert : retrouve l'existant ou crée un nouvel objet
        GeneratedTimetable timetable = generatedTimetableRepository
                .findByJobIdAndTenantId(jobId, tenantId)
                .orElseGet(GeneratedTimetable::new);

        timetable.setJobId(jobId);
        timetable.setAcademicYearId(job.getAcademicYearId());
        timetable.setConstraintProfileId(job.getConstraintProfileId());
        timetable.setScoreAchieved(job.getScoreAchieved());
        // Le verdict complet, pas la seule faisabilité Timefold : c'est ce drapeau
        // que l'écran « Emplois du temps générés » affiche, et il doit dire la
        // même chose que le statut du job.
        timetable.setFeasible(retenu);
        timetable.setTotalSessions(sessionCount);
        timetable.setHardViolations(score != null ? Math.abs(score.hardScore())   : 0);
        timetable.setMediumViolations(score != null ? Math.abs(score.mediumScore()) : 0);
        // On préserve le statut et publishedAt si l'enregistrement existait déjà
        if (timetable.getStatus() == null) timetable.setStatus("DRAFT");
        timetable.setTenantId(tenantId);

        generatedTimetableRepository.save(timetable);
    }

    // ── mappers ───────────────────────────────────────────────────────────────

    private TimetableSession toSession(Lesson l, Long jobId, Long yearId, String tenantId) {
        TimetableSession s = TimetableSession.builder()
                .jobId(jobId).academicYearId(yearId)
                // Le lien vers l'objet que Timefold incrimine dans ses
                // ConstraintMatch. Voir TimetableSession.lessonId.
                .lessonId(l.getId())
                .teachingAssignmentId(l.getTeachingAssignmentId())
                .subjectCode(l.getSubjectCode()).subjectName(l.getSubjectName())
                .studentClassName(l.getStudentClassName())
                .teacherCode(l.getTeacher() != null ? l.getTeacher().getCode() : null)
                .teacherName(l.getTeacher() != null ? l.getTeacher().getName() : null)
                .roomCode(l.getRoom().getCode()).roomType(l.getRoom().getType())
                .day(l.getTimeSlot().getDay())
                .startTime(l.getStartTime()).endTime(l.getEndTime())
                .sessionType(l.getSessionType()).groupIndex(l.getGroupIndex())
                .build();
        s.setTenantId(tenantId);
        return s;
    }

    private TimetableSessionResponse toSessionResponse(TimetableSession s) {
        return TimetableSessionResponse.builder()
                .idTimetableSession(s.getIdTimetableSession())
                .subjectCode(s.getSubjectCode()).subjectName(s.getSubjectName())
                .studentClassName(s.getStudentClassName())
                .teacherCode(s.getTeacherCode()).teacherName(s.getTeacherName())
                .roomCode(s.getRoomCode()).roomType(s.getRoomType())
                .day(s.getDay()).startTime(s.getStartTime()).endTime(s.getEndTime())
                .sessionType(s.getSessionType()).groupIndex(s.getGroupIndex())
                .build();
    }

    private TimetableJobResponse toJobResponse(TimetableJob job) {
        return TimetableJobResponse.builder()
                .jobId(job.getIdTimetableJob())
                .schoolYearId(job.getAcademicYearId())
                .constraintProfileId(job.getConstraintProfileId())
                .status(job.getStatus()).scoreAchieved(job.getScoreAchieved())
                .startedAt(job.getStartedAt()).finishedAt(job.getFinishedAt())
                .errorMessage(job.getErrorMessage())
                .validationReport(job.getValidationReport())
                .build();
    }

    private GeneratedTimetableResponse toGeneratedTimetableResponse(GeneratedTimetable gt) {
        return GeneratedTimetableResponse.builder()
                .id(gt.getIdGeneratedTimetable())
                .jobId(gt.getJobId())
                .academicYearId(gt.getAcademicYearId())
                .constraintProfileId(gt.getConstraintProfileId())
                .scoreAchieved(gt.getScoreAchieved())
                .feasible(gt.isFeasible())
                .totalSessions(gt.getTotalSessions())
                .hardViolations(gt.getHardViolations())
                .mediumViolations(gt.getMediumViolations())
                .status(gt.getStatus())
                .publishedAt(gt.getPublishedAt())
                .build();
    }

    private TimetableJob loadJob(Long jobId, String tenantId) {
        if (jobId == null) throw new BadRequestException("L'identifiant du job est obligatoire");
        return jobRepository.findByIdTimetableJobAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de generation introuvable avec l'ID : " + jobId));
    }

    /**
     * Construit, pour un niveau de score donné (HARD/MEDIUM/SOFT), la liste des
     * contraintes violées avec libellé français, décompte et exemples concrets
     * (quel enseignant/classe/créneau) — extraits des {@link ConstraintMatch}
     * de Timefold. Purement déterministe, aucune IA impliquée.
     */
    private List<ScoreExplanationResponse.ConstraintViolation> explainLevel(
            ScoreExplanation<TimetableSolution, HardMediumSoftScore> explanation,
            String level,
            Map<Long, Long> sessionIdByLessonId,
            AlternativeSlotFinder finder) {
        return explanation.getConstraintMatchTotalMap().values().stream()
                .filter(cmt -> {
                    HardMediumSoftScore s = cmt.getScore();
                    return switch (level) {
                        case "HARD"   -> s.hardScore()   < 0;
                        case "MEDIUM" -> s.mediumScore() < 0;
                        default       -> s.softScore()   < 0;
                    };
                })
                .map(cmt -> {
                    List<ScoreExplanationResponse.Occurrence> occurrences =
                            occurrencesOf(cmt, sessionIdByLessonId, finder);
                    return ScoreExplanationResponse.ConstraintViolation.builder()
                            .constraintName(cmt.getConstraintName())
                            .label(CONSTRAINT_LABELS.getOrDefault(cmt.getConstraintName(), cmt.getConstraintName()))
                            .score(cmt.getScore().toString())
                            .count(cmt.getConstraintMatchCount())
                            .examples(cmt.getConstraintMatchSet().stream()
                                    .map(TimetableSolverService::describeMatch)
                                    .distinct()
                                    .limit(MAX_EXAMPLES)
                                    .toList())
                            .occurrences(occurrences)
                            .suggestion(suggestionFor(cmt.getConstraintName(), occurrences))
                            .build();
                })
                .sorted(Comparator.comparingInt(ScoreExplanationResponse.ConstraintViolation::getCount).reversed())
                .toList();
    }

    /**
     * Désigne les violations au lieu de les décrire : une entrée par
     * {@link ConstraintMatch} portant les séances en cause, identifiants compris.
     *
     * <p>Les matches sans aucune {@link Lesson} incriminée sont écartés — ce sont
     * les contraintes à seuil, dont le tuple porte une clé de groupe
     * (« enseignant|MONDAY ») et un cumul, pas des séances. Il n'y a rien à
     * pointer dans une grille pour « M. Ahmed dépasse 6 h ce jour-là » : les
     * six séances sont en cause à parts égales, et en désigner une serait
     * l'accuser à tort. Le libellé textuel reste, lui, parfaitement lisible.
     */
    private List<ScoreExplanationResponse.Occurrence> occurrencesOf(
            ConstraintMatchTotal<HardMediumSoftScore> cmt,
            Map<Long, Long> sessionIdByLessonId,
            AlternativeSlotFinder finder) {
        return cmt.getConstraintMatchSet().stream()
                .filter(match -> match.getIndictedObjectList().stream().anyMatch(Lesson.class::isInstance))
                .limit(MAX_EXAMPLES)
                .map(match -> {
                    List<Lesson> lessons = match.getIndictedObjectList().stream()
                            .filter(Lesson.class::isInstance)
                            .map(Lesson.class::cast)
                            .distinct()
                            .toList();
                    return ScoreExplanationResponse.Occurrence.builder()
                            .label(describeMatch(match))
                            .score(match.getScore().toString())
                            .sessions(lessons.stream()
                                    .map(lesson -> toSessionRef(lesson, sessionIdByLessonId))
                                    .toList())
                            .relocation(relocationFor(lessons, sessionIdByLessonId, finder))
                            .build();
                })
                .toList();
    }

    /**
     * Le déplacement proposé pour une occurrence : la première des séances en
     * cause qui ait quelque part où aller.
     *
     * <p>Sur un conflit d'enseignant, les deux séances lèvent la violation en
     * bougeant — il suffit d'en proposer une. Sur une salle trop petite, il n'y
     * en a qu'une. Dans les deux cas, l'ordre d'incrimination de Timefold est
     * stable, donc la proposition l'est aussi : le directeur qui rouvre l'écran
     * relit la même phrase.
     *
     * <p>{@code null} quand aucune des séances ne tient ailleurs. C'est le cas
     * courant sur un établissement saturé, et il ne faut surtout pas le combler
     * par une phrase : § 7 du plan de conformité, trois professeurs d'EPS pour
     * vingt-trois classes ne se règlent pas en déplaçant une case.
     */
    private ScoreExplanationResponse.Relocation relocationFor(
            List<Lesson> lessons,
            Map<Long, Long> sessionIdByLessonId,
            AlternativeSlotFinder finder) {
        return lessons.stream()
                .map(finder::findFor)
                .flatMap(Optional::stream)
                .findFirst()
                .map(relocation -> toRelocation(relocation, sessionIdByLessonId))
                .orElse(null);
    }

    private static ScoreExplanationResponse.Relocation toRelocation(
            AlternativeSlotFinder.Relocation relocation,
            Map<Long, Long> sessionIdByLessonId) {
        Lesson lesson = relocation.lesson();
        TimeSlotRef cible = relocation.slot();
        return ScoreExplanationResponse.Relocation.builder()
                .sessionId(sessionIdByLessonId.get(lesson.getId()))
                .lessonId(lesson.getId())
                .subjectName(lesson.getSubjectName() != null ? lesson.getSubjectName() : lesson.getSubjectCode())
                .className(lesson.getStudentClassName())
                .fromDay(lesson.getTimeSlot() != null && lesson.getTimeSlot().getDay() != null
                        ? lesson.getTimeSlot().getDay().name() : null)
                .fromStartTime(lesson.getStartTime() != null ? lesson.getStartTime().toString() : null)
                .toDay(cible.getDay() != null ? cible.getDay().name() : null)
                .toStartTime(cible.getStartTime() != null ? cible.getStartTime().toString() : null)
                .toSlotId(cible.getId())
                .toRoomCode(relocation.room() != null ? relocation.room().getCode() : null)
                .text(relocationText(relocation))
                .build();
    }

    /**
     * La proposition en français, qui énonce <em>ce qui a été vérifié</em>.
     *
     * <p>« Le créneau est libre pour l'enseignant et pour la classe » est un
     * fait, contrôlé sur la solution. « Le planning sera meilleur » n'en serait
     * pas un : le déplacement peut dégrader une contrainte souple. La nuance
     * décide de la confiance qu'on accordera aux propositions suivantes.
     */
    private static String relocationText(AlternativeSlotFinder.Relocation relocation) {
        Lesson lesson = relocation.lesson();
        TimeSlotRef cible = relocation.slot();

        StringBuilder sb = new StringBuilder("Déplacer ")
                .append(describeLesson(lesson))
                .append(" vers ").append(dayLabel(cible.getDay()));
        if (cible.getStartTime() != null) {
            sb.append(" ").append(cible.getStartTime());
        }
        sb.append(" — créneau libre pour ");
        sb.append(lesson.getTeacher() != null ? "l'enseignant et pour la classe" : "la classe");
        if (relocation.room() != null) {
            sb.append(", salle ").append(relocation.room().getCode()).append(" disponible");
        }
        return sb.append(".").toString();
    }

    /**
     * La phrase affichée à côté de la violation : la proposition calculée si
     * l'une des occurrences en porte une, la constante sinon.
     *
     * <p>C'est là que {@link #CONSTRAINT_SUGGESTIONS} redevient ce qu'il aurait
     * toujours dû être — un repli. Une phrase écrite avant de connaître le
     * planning ne peut pas dire quel créneau est libre ; elle garde en revanche
     * tout son sens pour les contraintes à seuil, qui n'incriminent aucune
     * séance en particulier et n'ont donc jamais d'occurrence à déplacer.
     */
    private static String suggestionFor(
            String constraintName, List<ScoreExplanationResponse.Occurrence> occurrences) {
        return occurrences.stream()
                .map(ScoreExplanationResponse.Occurrence::getRelocation)
                .filter(Objects::nonNull)
                .map(ScoreExplanationResponse.Relocation::getText)
                .findFirst()
                .orElseGet(() -> CONSTRAINT_SUGGESTIONS.getOrDefault(constraintName, DEFAULT_SUGGESTION));
    }

    /**
     * Une leçon du solveur, rendue sous la forme que l'interface sait manipuler.
     *
     * <p>{@code sessionId} vaut {@code null} quand la leçon n'a pas de ligne
     * persistée en face : job produit avant la colonne {@code lesson_id}. On le
     * laisse vide plutôt que de rapprocher les lignes sur leurs coordonnées —
     * un tel rapprochement désignerait la mauvaise séance dès qu'un
     * déplacement manuel a eu lieu, et se tromperait sans le dire.
     */
    private static ScoreExplanationResponse.SessionRef toSessionRef(
            Lesson lesson, Map<Long, Long> sessionIdByLessonId) {
        return ScoreExplanationResponse.SessionRef.builder()
                .lessonId(lesson.getId())
                .sessionId(sessionIdByLessonId.get(lesson.getId()))
                .subjectCode(lesson.getSubjectCode())
                .subjectName(lesson.getSubjectName())
                .className(lesson.getStudentClassName())
                .teacherCode(lesson.getTeacher() != null ? lesson.getTeacher().getCode() : null)
                .teacherName(lesson.getTeacher() != null ? lesson.getTeacher().getName() : null)
                .roomCode(lesson.getRoom() != null ? lesson.getRoom().getCode() : null)
                .day(lesson.getTimeSlot() != null && lesson.getTimeSlot().getDay() != null
                        ? lesson.getTimeSlot().getDay().name() : null)
                .startTime(lesson.getStartTime() != null ? lesson.getStartTime().toString() : null)
                .groupIndex(lesson.getGroupIndex())
                .build();
    }

    /**
     * Décrit une violation concrète à partir des leçons impliquées (indicted objects).
     *
     * Pour les règles personnalisées, tous les flux d'un même niveau partagent un
     * seul nom de contrainte Timefold. Le nom de la règle fautive est donc préfixé
     * ici, depuis le {@link CompiledConstraint} présent dans le tuple : sans cela,
     * l'utilisateur verrait « règle personnalisée violée » sans savoir laquelle.
     */
    private static String describeMatch(ConstraintMatch<HardMediumSoftScore> match) {
        List<Object> indicted = match.getIndictedObjectList();

        String rule = indicted.stream()
                .filter(CompiledConstraint.class::isInstance)
                .map(CompiledConstraint.class::cast)
                .map(CompiledConstraint::getName)
                .findFirst()
                .map(name -> "« " + name + " » : ")
                .orElse("");

        List<String> lessons = indicted.stream()
                .filter(Lesson.class::isInstance)
                .map(Lesson.class::cast)
                .map(TimetableSolverService::describeLesson)
                .distinct()
                .toList();

        if (lessons.isEmpty()) {
            // Contrainte à seuil : le tuple ne porte pas les séances mais la clé de
            // groupe (enseignant|jour) et le cumul, déjà lisibles tels quels.
            return rule + match.getIdentificationString();
        }
        return rule + String.join(" ↔ ", lessons);
    }

    /** Description humaine courte d'une leçon : matière · classe · enseignant · jour heure. */
    private static String describeLesson(Lesson l) {
        StringBuilder sb = new StringBuilder(l.getSubjectName() != null ? l.getSubjectName() : l.getSubjectCode());
        if (l.getStudentClassName() != null) sb.append(" · ").append(l.getStudentClassName());
        if (l.getTeacher() != null && l.getTeacher().getName() != null) sb.append(" · ").append(l.getTeacher().getName());
        if (l.getTimeSlot() != null) {
            sb.append(" · ").append(dayLabel(l.getTimeSlot().getDay()));
            if (l.getStartTime() != null) sb.append(" ").append(l.getStartTime());
        }
        if (l.getGroupIndex() > 0) sb.append(" (").append(groupLabel(l.getGroupIndex())).append(")");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
