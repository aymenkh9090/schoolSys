package tn.wtm.school.planning.solver.service;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tn.wtm.school.planning.solver.builder.TimetableProblemBuilder;
import tn.wtm.school.planning.solver.domain.Lesson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.dto.request.TimetableGenerationRequest;
import tn.wtm.school.planning.solver.dto.response.PreflightResponse;
import tn.wtm.school.planning.solver.dto.response.ScoreExplanationResponse;
import tn.wtm.school.planning.solver.validation.PreGenerationValidator;
import tn.wtm.school.planning.solver.validation.ValidationFinding;
import tn.wtm.school.planning.solver.validation.ValidationReport;
import tn.wtm.school.planning.solver.domain.TimetableSession;
import tn.wtm.school.planning.solver.entity.GeneratedTimetable;
import tn.wtm.school.planning.solver.entity.TimetableJob;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.enums.SolverStatus;
import tn.wtm.school.planning.solver.port.SolverProgressPublisher;
import tn.wtm.school.planning.solver.port.SolverProgressPublisher.SolverProgress;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;
import tn.wtm.school.planning.solver.repository.GeneratedTimetableRepository;
import tn.wtm.school.planning.solver.repository.TimetableJobRepository;
import tn.wtm.school.planning.solver.repository.TimetableSessionRepository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

/**
 * Tests unitaires pour {@link TimetableSolverService#persistResult}.
 *
 * Méthode {@code protected} → test dans le même package.
 * {@code @Transactional} inopérant sans Spring context : les appels repo sont directs.
 *
 * Scénarios couverts :
 *   1. Statut du job  — SOLVED / INFEASIBLE selon la faisabilité du score
 *   2. Champs du job  — finishedAt, scoreAchieved
 *   3. Sessions       — suppression préalable, filtrage des leçons non placées, mapping
 *   4. GeneratedTimetable — create vs upsert, tous les champs, préservation du statut
 *   5. Cache mémoire  — priorité et nettoyage de bestSolutions
 *   6. Job introuvable — aucun effet de bord
 *   7. Refus avant génération — le verrou symétrique, à l'autre bout
 *
 * <p>La section 7 tient ici et non dans sa propre classe pour une raison de
 * fond : {@code persistResult} et {@code startGeneration} sont les deux
 * extrémités du même verrou. L'un refuse de rendre un emploi du temps faux,
 * l'autre refuse d'en chercher un que les données condamnent. Les éprouver sur
 * le même harnais de simulacres évite d'en écrire un second à l'identique.
 */
@ExtendWith(MockitoExtension.class)
class TimetableSolverServicePersistResultTest {

    // ── dépendances mockées ───────────────────────────────────────────────────

    @Mock private TimetableProblemBuilder       problemBuilder;
    @Mock private PreGenerationValidator        preGenerationValidator;
    @Mock private TimetableJobRepository        jobRepository;
    @Mock private TimetableSessionRepository    sessionRepository;
    @Mock private GeneratedTimetableRepository  generatedTimetableRepository;
    @SuppressWarnings("unchecked")
    @Mock private SolverManager<TimetableSolution, Long> solverManager;
    @SuppressWarnings("unchecked")
    @Mock private SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager;
    @Mock private TransactionTemplate txTemplate;
    @Mock private SolverProgressPublisher progressPublisher;
    @SuppressWarnings("unchecked")
    @Mock private ObjectProvider<SolverProgressPublisher> progressPublisherProvider;

    @Captor private ArgumentCaptor<SolverProgress>     progressCaptor;
    @Captor private ArgumentCaptor<TimetableJob>       jobCaptor;
    @Captor private ArgumentCaptor<GeneratedTimetable> timetableCaptor;
    @SuppressWarnings("rawtypes")
    @Captor private ArgumentCaptor<Iterable>           sessionsCaptor;

    // ── constantes ────────────────────────────────────────────────────────────

    private static final String TENANT     = "school-1";
    private static final Long   JOB_ID     = 42L;
    private static final Long   YEAR_ID    = 2026L;
    private static final Long   PROFILE_ID = 1L;

    private static final HardMediumSoftScore FEASIBLE_SCORE   = HardMediumSoftScore.of(0, 0, -5);
    private static final HardMediumSoftScore INFEASIBLE_SCORE = HardMediumSoftScore.of(-2, -1, 0);

    // ── service sous test ─────────────────────────────────────────────────────

    private TimetableSolverService service;

    @BeforeEach
    void setUp() {
        service = new TimetableSolverService(
                problemBuilder, preGenerationValidator, jobRepository, sessionRepository,
                generatedTimetableRepository, solverManager, solutionManager, txTemplate,
                progressPublisherProvider);

        // orderedStream() est réévalué à chaque diffusion : on renvoie un flux neuf
        // par appel, un Stream étant à usage unique.
        lenient().when(progressPublisherProvider.orderedStream())
                .thenAnswer(inv -> Stream.of(progressPublisher));

        // TransactionTemplate exécute le callback directement (pas de vrai contexte tx).
        // lenient() : les tests de la section 7 s'arrêtent avant toute persistance.
        lenient().doAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            cb.doInTransaction(null);
            return null;
        }).when(txTemplate).execute(any());

        // Par défaut : aucun GeneratedTimetable existant (create path).
        // lenient() car les tests "job introuvable" n'atteignent jamais ce stub.
        lenient().when(generatedTimetableRepository.findByJobIdAndTenantId(JOB_ID, TENANT))
                .thenReturn(Optional.empty());
    }

    /** Le tenant posé par la section 7 ne doit pas déborder sur les autres. */
    @AfterEach
    void viderLeTenant() {
        TenantContext.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. Refus avant génération
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Une donnée qui condamne la génération l'empêche de partir")
    void preGenerationBloquante_refuseDeLancerLeSolveur() {
        // Avant, le solveur partait quoi qu'il arrive : on découvrait le défaut
        // après trois minutes de calcul, ou jamais.
        TenantContext.setTenantId(TENANT);
        when(jobRepository.save(any())).thenAnswer(inv -> {
            TimetableJob j = inv.getArgument(0);
            j.setIdTimetableJob(JOB_ID);
            return j;
        });
        when(problemBuilder.build(TENANT, YEAR_ID, PROFILE_ID))
                .thenReturn(solution(FEASIBLE_SCORE, List.of()));
        when(preGenerationValidator.valider(any())).thenReturn(new ValidationReport(List.of(
                ValidationFinding.bloquant("MATIERE_SANS_ENSEIGNANT", "7A / Musique",
                        "aucune séance n'est engendrée"))));

        TimetableGenerationRequest req = new TimetableGenerationRequest();
        req.setSchoolYearId(YEAR_ID);
        req.setConstraintProfileId(PROFILE_ID);

        assertThatThrownBy(() -> service.startGeneration(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("7A / Musique");

        verify(solverManager, never()).solveAndListen(any(), any(TimetableSolution.class), any());
    }

    @Test
    @DisplayName("Le contrôle rend ses constats, bloquants d'abord")
    void preflight_rendLesConstats() {
        TenantContext.setTenantId(TENANT);
        when(problemBuilder.build(TENANT, YEAR_ID, PROFILE_ID))
                .thenReturn(solution(FEASIBLE_SCORE, List.of()));
        when(preGenerationValidator.valider(any())).thenReturn(new ValidationReport(List.of(
                ValidationFinding.avertissement("SEANCE_HORS_PROGRAMME", "7A / LATIN", "hors programme"),
                ValidationFinding.bloquant("SERVICE_IMPOSSIBLE", "M. Trabelsi", "trop d'heures"))));

        PreflightResponse reponse = service.preflight(YEAR_ID, PROFILE_ID);

        assertThat(reponse.isReady()).isFalse();
        assertThat(reponse.getBlockingCount()).isEqualTo(1);
        assertThat(reponse.getWarningCount()).isEqualTo(1);
        assertThat(reponse.getFindings())
                .extracting(ScoreExplanationResponse.BusinessFinding::getCode)
                .as("le bloquant se lit avant l'avertissement")
                .containsExactly("SERVICE_IMPOSSIBLE", "SEANCE_HORS_PROGRAMME");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Statut du job
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void feasibleScore_setsJobStatusToSOLVED() {
        stubJobFound();
        TimetableSolution sol = solution(FEASIBLE_SCORE, List.of());

        persist(sol);

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.SOLVED);
    }

    @Test
    void nonFeasibleScore_setsJobStatusToINFEASIBLE() {
        stubJobFound();
        TimetableSolution sol = solution(INFEASIBLE_SCORE, List.of());

        persist(sol);

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.INFEASIBLE);
    }

    @Test
    void nullScore_setsJobStatusToINFEASIBLE() {
        stubJobFound();
        TimetableSolution sol = solution(null, List.of());

        persist(sol);

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.INFEASIBLE);
    }

    // ── le second verrou : la validation métier (étape E) ─────────────────────

    @Test
    void feasibleScoreButBusinessInvalid_setsJobStatusToINFEASIBLE() {
        stubJobFound();
        // Le score dit « aucune contrainte activée n'est violée ». Il ne dit rien
        // du volume officiel, que l'établissement peut avoir décoché : ici 1 h
        // placée contre 2 h au programme.
        TimetableSolution sol = solution(FEASIBLE_SCORE, List.of(volumeAmpute(1L)));

        persist(sol);

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.INFEASIBLE);
    }

    @Test
    void businessInvalid_archivesTheReasonOnTheJob() {
        stubJobFound();

        persist(solution(FEASIBLE_SCORE, List.of(volumeAmpute(1L))));

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getValidationReport())
                .as("le motif du refus doit survivre à la solution en mémoire")
                .contains("VOLUME_HORAIRE")
                .contains("7A / MATH");
    }

    @Test
    void businessInvalid_marksTheGeneratedTimetableAsNotFeasible() {
        stubJobFound();

        persist(solution(FEASIBLE_SCORE, List.of(volumeAmpute(1L))));

        verify(generatedTimetableRepository).save(timetableCaptor.capture());
        assertThat(timetableCaptor.getValue().isFeasible())
                .as("l'écran ne doit pas afficher « faisable » sur un emploi du temps refusé")
                .isFalse();
    }

    @Test
    void conformSolution_leavesTheValidationReportEmpty() {
        stubJobFound();

        persist(solution(FEASIBLE_SCORE, List.of(volumeConforme(1L))));

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.SOLVED);
        assertThat(jobCaptor.getValue().getValidationReport())
                .as("une colonne vide et « rien à signaler » ne se lisent pas pareil")
                .isNull();
    }

    @Test
    void aWarningIsArchivedWithoutRefusingTheTimetable() {
        stubJobFound();
        // placedLesson ne porte aucun volume officiel : la validation ne peut pas
        // vérifier sa conformité au programme, et elle le dit plutôt que de se
        // taire. Un avertissement s'archive, il ne refuse pas.
        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.SOLVED);
        assertThat(jobCaptor.getValue().getValidationReport())
                .contains("[avertissement]")
                .contains("VOLUME_NON_VERIFIABLE");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Champs du job — finishedAt et scoreAchieved
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void finishedAtIsAlwaysSetOnJob() {
        stubJobFound();
        Instant before = Instant.now();

        persist(solution(FEASIBLE_SCORE, List.of()));

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getFinishedAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
    }

    @Test
    void scoreAchievedIsSetOnJobWhenScorePresent() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of()));

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getScoreAchieved())
                .isEqualTo(FEASIBLE_SCORE.toString());
    }

    @Test
    void scoreAchievedIsNotSetWhenScoreIsNull() {
        stubJobFound();
        persist(solution(null, List.of()));

        verify(jobRepository).save(jobCaptor.capture());
        // Le setter n'est pas appelé : scoreAchieved reste à sa valeur initiale (null)
        assertThat(jobCaptor.getValue().getScoreAchieved()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Sessions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void existingSessionsAreDeletedBeforeSavingNewOnes() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        InOrder order = inOrder(sessionRepository);
        order.verify(sessionRepository).deleteByJobIdAndTenantId(JOB_ID, TENANT);
        order.verify(sessionRepository).saveAll(any());
    }

    @Test
    void onlyFullyPlacedLessonsAreSaved() {
        stubJobFound();
        // 2 placées, 1 sans timeSlot, 1 sans room
        List<Lesson> lessons = List.of(
                placedLesson(1L),
                placedLesson(2L),
                lessonWithoutTimeSlot(3L),
                lessonWithoutRoom(4L));

        persist(solution(FEASIBLE_SCORE, lessons));

        assertThat(capturedSessions()).hasSize(2);
    }

    @Test
    void whenAllLessonsUnplaced_noSessionIsSaved() {
        stubJobFound();
        List<Lesson> lessons = List.of(lessonWithoutTimeSlot(1L), lessonWithoutRoom(2L));

        persist(solution(FEASIBLE_SCORE, lessons));

        assertThat(capturedSessions()).isEmpty();
    }

    @Test
    void sessionFieldsAreMappedFromLesson() {
        stubJobFound();
        Lesson lesson = placedLesson(1L);

        persist(solution(FEASIBLE_SCORE, List.of(lesson)));

        TimetableSession session = capturedSessions().getFirst();
        assertThat(session.getJobId()).isEqualTo(JOB_ID);
        assertThat(session.getAcademicYearId()).isEqualTo(YEAR_ID);
        assertThat(session.getSubjectCode()).isEqualTo(lesson.getSubjectCode());
        assertThat(session.getSubjectName()).isEqualTo(lesson.getSubjectName());
        assertThat(session.getStudentClassName()).isEqualTo(lesson.getStudentClassName());
        assertThat(session.getTeacherCode()).isEqualTo(lesson.getTeacher().getCode());
        assertThat(session.getTeacherName()).isEqualTo(lesson.getTeacher().getName());
        assertThat(session.getRoomCode()).isEqualTo(lesson.getRoom().getCode());
        assertThat(session.getRoomType()).isEqualTo(lesson.getRoom().getType());
        assertThat(session.getDay()).isEqualTo(lesson.getTimeSlot().getDay());
        assertThat(session.getStartTime()).isEqualTo(lesson.getStartTime());
        assertThat(session.getEndTime()).isEqualTo(lesson.getEndTime());   // startTime + durationSlots×30min
        assertThat(session.getSessionType()).isEqualTo(lesson.getSessionType());
        assertThat(session.getGroupIndex()).isEqualTo(lesson.getGroupIndex());
    }

    @Test
    void sessionTenantIdIsSetFromParameter() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        assertThat(capturedSessions().getFirst().getTenantId()).isEqualTo(TENANT);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. GeneratedTimetable — create
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void generatedTimetableIsAlwaysSaved() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of()));

        verify(generatedTimetableRepository).save(any(GeneratedTimetable.class));
    }

    @Test
    void generatedTimetable_feasibleTrueWhenScoreFeasible() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of()));

        assertThat(capturedTimetable().isFeasible()).isTrue();
    }

    @Test
    void generatedTimetable_feasibleFalseWhenHardViolations() {
        stubJobFound();
        persist(solution(INFEASIBLE_SCORE, List.of()));

        assertThat(capturedTimetable().isFeasible()).isFalse();
    }

    @Test
    void generatedTimetable_feasibleFalseWhenNullScore() {
        stubJobFound();
        persist(solution(null, List.of()));

        assertThat(capturedTimetable().isFeasible()).isFalse();
    }

    @Test
    void generatedTimetable_hardViolationsIsAbsoluteValueOfHardScore() {
        stubJobFound();
        // INFEASIBLE_SCORE = HardMediumSoftScore.of(-2, -1, 0) → 2 hard violations
        persist(solution(INFEASIBLE_SCORE, List.of()));

        assertThat(capturedTimetable().getHardViolations()).isEqualTo(2);
    }

    @Test
    void generatedTimetable_mediumViolationsIsAbsoluteValueOfMediumScore() {
        stubJobFound();
        // INFEASIBLE_SCORE = HardMediumSoftScore.of(-2, -1, 0) → 1 medium violation
        persist(solution(INFEASIBLE_SCORE, List.of()));

        assertThat(capturedTimetable().getMediumViolations()).isEqualTo(1);
    }

    @Test
    void generatedTimetable_violationsAreZeroWhenNullScore() {
        stubJobFound();
        persist(solution(null, List.of()));

        GeneratedTimetable gt = capturedTimetable();
        assertThat(gt.getHardViolations()).isZero();
        assertThat(gt.getMediumViolations()).isZero();
    }

    @Test
    void generatedTimetable_totalSessionsMatchesPlacedLessonsCount() {
        stubJobFound();
        List<Lesson> lessons = List.of(
                placedLesson(1L), placedLesson(2L), placedLesson(3L),
                lessonWithoutTimeSlot(4L)); // exclu du comptage

        persist(solution(FEASIBLE_SCORE, lessons));

        assertThat(capturedTimetable().getTotalSessions()).isEqualTo(3);
    }

    @Test
    void generatedTimetable_statusIsDraftOnFirstCreate() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of()));

        assertThat(capturedTimetable().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void generatedTimetable_coreFieldsFromJob() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of()));

        GeneratedTimetable gt = capturedTimetable();
        assertThat(gt.getJobId()).isEqualTo(JOB_ID);
        assertThat(gt.getAcademicYearId()).isEqualTo(YEAR_ID);
        assertThat(gt.getConstraintProfileId()).isEqualTo(PROFILE_ID);
        assertThat(gt.getScoreAchieved()).isEqualTo(FEASIBLE_SCORE.toString());
    }

    @Test
    void generatedTimetable_tenantIdIsSet() {
        stubJobFound();
        persist(solution(FEASIBLE_SCORE, List.of()));

        assertThat(capturedTimetable().getTenantId()).isEqualTo(TENANT);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. GeneratedTimetable — upsert (enregistrement existant)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void whenGeneratedTimetableAlreadyExists_itIsUpdatedNotDuplicated() {
        stubJobFound();
        GeneratedTimetable existing = existingTimetable("DRAFT");
        when(generatedTimetableRepository.findByJobIdAndTenantId(JOB_ID, TENANT))
                .thenReturn(Optional.of(existing));

        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        // save appelé exactement une fois avec l'objet existant mis à jour
        verify(generatedTimetableRepository).save(timetableCaptor.capture());
        assertThat(timetableCaptor.getValue()).isSameAs(existing);
    }

    @Test
    void whenExistingGeneratedTimetableIsPublished_statusIsPreserved() {
        stubJobFound();
        GeneratedTimetable published = existingTimetable("PUBLISHED");
        when(generatedTimetableRepository.findByJobIdAndTenantId(JOB_ID, TENANT))
                .thenReturn(Optional.of(published));

        persist(solution(FEASIBLE_SCORE, List.of()));

        // Le statut PUBLISHED ne doit pas être écrasé par DRAFT
        assertThat(capturedTimetable().getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void whenExistingGeneratedTimetableHasNullStatus_draftIsApplied() {
        stubJobFound();
        GeneratedTimetable withNullStatus = existingTimetable(null);
        when(generatedTimetableRepository.findByJobIdAndTenantId(JOB_ID, TENANT))
                .thenReturn(Optional.of(withNullStatus));

        persist(solution(FEASIBLE_SCORE, List.of()));

        assertThat(capturedTimetable().getStatus()).isEqualTo("DRAFT");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Cache mémoire bestSolutions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void inMemorySolutionIsPreferredOverParameterSolution() {
        stubJobFound();
        // Solution en cache avec 3 leçons placées
        TimetableSolution cached = solution(FEASIBLE_SCORE,
                List.of(placedLesson(1L), placedLesson(2L), placedLesson(3L)));
        putInCache(JOB_ID, cached);

        // Paramètre sans leçons (ignoré si le cache est prioritaire)
        TimetableSolution param = solution(INFEASIBLE_SCORE, List.of());

        persist(param);

        // Le score utilisé doit être celui du cache (FEASIBLE → SOLVED)
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.SOLVED);
        assertThat(capturedSessions()).hasSize(3);
    }

    @Test
    void bestSolutionIsRetainedInCacheAfterPersist() {
        stubJobFound();
        putInCache(JOB_ID, solution(FEASIBLE_SCORE, List.of()));

        persist(solution(FEASIBLE_SCORE, List.of()));

        // Volontairement conservée : /score-explanation doit rester utilisable après
        // la fin du job (notamment pour les jobs INFEASIBLE).
        Map<Long, TimetableSolution> cache = getBestSolutionsCache();
        assertThat(cache).containsKey(JOB_ID);
    }

    @Test
    void whenNoCachedSolution_parameterSolutionIsUsed() {
        stubJobFound();
        // Aucune entrée en cache : bestSolutions est vide
        TimetableSolution param = solution(FEASIBLE_SCORE,
                List.of(placedLesson(1L), placedLesson(2L)));

        persist(param);

        assertThat(capturedSessions()).hasSize(2);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. Job introuvable — aucun effet de bord
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void whenJobNotFound_noSessionsAreSaved() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void whenJobNotFound_noGeneratedTimetableIsSaved() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        persist(solution(FEASIBLE_SCORE, List.of()));

        verify(generatedTimetableRepository, never()).save(any());
    }

    @Test
    void whenJobNotFound_jobRepositorySaveIsNeverCalled() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        persist(solution(FEASIBLE_SCORE, List.of()));

        verify(jobRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. Diffusion temps réel de la progression
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void publishesFinalProgressWithStatusAndSessionCounts() {
        stubJobFound();

        // Une séance sans salle : le score Timefold est stubé faisable, mais
        // l'emploi du temps ne l'est pas — cette séance n'apparaîtra nulle part.
        // C'est exactement le trou que la validation métier ferme (étape E) : le
        // job est refusé, et « faisable » suit le refus au lieu de le contredire.
        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L), lessonWithoutRoom(2L))));

        verify(progressPublisher).publish(eq(TENANT), progressCaptor.capture());
        SolverProgress published = progressCaptor.getValue();
        assertThat(published.jobId()).isEqualTo(JOB_ID);
        assertThat(published.status()).isEqualTo(SolverStatus.INFEASIBLE);
        assertThat(published.score()).isEqualTo(FEASIBLE_SCORE.toString());
        assertThat(published.feasible()).isFalse();
        // Seule la leçon complètement placée compte, sur un total de deux.
        assertThat(published.placedSessions()).isEqualTo(1);
        assertThat(published.totalSessions()).isEqualTo(2);
    }

    @Test
    void publishesINFEASIBLEWhenScoreHasHardViolations() {
        stubJobFound();

        persist(solution(INFEASIBLE_SCORE, List.of()));

        verify(progressPublisher).publish(eq(TENANT), progressCaptor.capture());
        assertThat(progressCaptor.getValue().status()).isEqualTo(SolverStatus.INFEASIBLE);
        assertThat(progressCaptor.getValue().feasible()).isFalse();
    }

    @Test
    void whenJobNotFound_noProgressIsPublished() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        persist(solution(FEASIBLE_SCORE, List.of()));

        verifyNoInteractions(progressPublisher);
    }

    @Test
    void whenNoPublisherRegistered_persistStillCompletes() {
        stubJobFound();
        when(progressPublisherProvider.orderedStream()).thenAnswer(inv -> Stream.empty());

        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.SOLVED);
    }

    @Test
    void whenOnePublisherThrows_theOthersStillReceiveTheEvent() {
        stubJobFound();
        SolverProgressPublisher enPanne = mock(SolverProgressPublisher.class);
        doThrow(new IllegalStateException("broker indisponible"))
                .when(enPanne).publish(any(), any());
        when(progressPublisherProvider.orderedStream())
                .thenAnswer(inv -> Stream.of(enPanne, progressPublisher));

        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        // Le second adaptateur reçoit l'événement malgré l'échec du premier.
        verify(progressPublisher).publish(eq(TENANT), progressCaptor.capture());
        assertThat(progressCaptor.getValue().status()).isEqualTo(SolverStatus.SOLVED);
    }

    @Test
    void whenPublisherThrows_persistIsNotAffected() {
        stubJobFound();
        doThrow(new IllegalStateException("broker indisponible"))
                .when(progressPublisher).publish(any(), any());

        persist(solution(FEASIBLE_SCORE, List.of(placedLesson(1L))));

        // La génération reste persistée : l'échec de diffusion est absorbé.
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(SolverStatus.SOLVED);
        verify(generatedTimetableRepository).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers — invocation
    // ══════════════════════════════════════════════════════════════════════════

    private void persist(TimetableSolution sol) {
        service.persistResult(JOB_ID, TENANT, sol);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers — stubs
    // ══════════════════════════════════════════════════════════════════════════

    private void stubJobFound() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(makeJob()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers — fixtures
    // ══════════════════════════════════════════════════════════════════════════

    private TimetableJob makeJob() {
        TimetableJob job = TimetableJob.builder()
                .academicYearId(YEAR_ID)
                .constraintProfileId(PROFILE_ID)
                .status(SolverStatus.RUNNING)
                .build();
        job.setIdTimetableJob(JOB_ID);
        job.setTenantId(TENANT);
        return job;
    }

    private TimetableSolution solution(HardMediumSoftScore score, List<Lesson> lessons) {
        return TimetableSolution.builder()
                .tenantId(TENANT).academicYearId(YEAR_ID).constraintProfileId(PROFILE_ID)
                .lessons(lessons)
                .score(score)
                .build();
    }

    private Lesson placedLesson(Long id) {
        return Lesson.builder()
                .id(id)
                .subjectCode("MATH").subjectName("Mathématiques")
                .studentClassName("7A")
                .teachingAssignmentId(id)
                .sessionType(SessionType.COURS)
                .groupIndex(0).classStudentCount(30)
                .teacher(TeacherRef.builder()
                        .id(1L).code("T1").name("Mr. Dupont").maxHoursPerDay(6).build())
                .room(RoomRef.builder()
                        .id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build())
                // Une heure distincte par séance. Depuis que persistResult passe
                // par TimetableBusinessValidator, un décor où trois séances de la
                // même classe se superposent n'est plus un décor neutre : c'est un
                // emploi du temps impossible, et il est refusé — à juste titre.
                .timeSlot(TimeSlotRef.builder()
                        .id(id).day(DayOfWeek.MONDAY).orderIndex(id.intValue())
                        .startTime(LocalTime.of(8, 0).plusHours(id - 1))
                        .endTime(LocalTime.of(9, 0).plusHours(id - 1))
                        .active(true).build())
                .build();
    }

    /**
     * Une séance d'une heure là où le programme en prévoit deux — le défaut que
     * seule la validation métier voit, puisque le solveur ne le compte que si
     * l'établissement a laissé RESPECT_OFFICIAL_SUBJECT_HOURS activée.
     */
    private Lesson volumeAmpute(Long id) {
        Lesson l = placedLesson(id);
        l.setDurationSlots(2);
        l.setOfficialWeeklySlots(4);
        return l;
    }

    /** La même séance, dont le volume placé rejoint exactement le programme. */
    private Lesson volumeConforme(Long id) {
        Lesson l = placedLesson(id);
        l.setOfficialWeeklySlots(l.getDurationSlots());
        return l;
    }

    private Lesson lessonWithoutTimeSlot(Long id) {
        return Lesson.builder()
                .id(id).subjectCode("PHYS").studentClassName("7B")
                .sessionType(SessionType.COURS)
                .room(RoomRef.builder().id(2L).code("B1").type(RoomType.NORMALE).capacity(30).build())
                // timeSlot intentionnellement absent
                .build();
    }

    private Lesson lessonWithoutRoom(Long id) {
        return Lesson.builder()
                .id(id).subjectCode("FRAN").studentClassName("7C")
                .sessionType(SessionType.COURS)
                .timeSlot(TimeSlotRef.builder()
                        .id(id).day(DayOfWeek.TUESDAY).orderIndex(2)
                        .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                        .active(true).build())
                // room intentionnellement absent
                .build();
    }

    private GeneratedTimetable existingTimetable(String status) {
        GeneratedTimetable gt = new GeneratedTimetable();
        gt.setIdGeneratedTimetable(99L);
        gt.setJobId(JOB_ID);
        gt.setTenantId(TENANT);
        gt.setAcademicYearId(YEAR_ID);
        gt.setStatus(status);
        return gt;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers — capture
    // ══════════════════════════════════════════════════════════════════════════

    private GeneratedTimetable capturedTimetable() {
        verify(generatedTimetableRepository).save(timetableCaptor.capture());
        return timetableCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<TimetableSession> capturedSessions() {
        verify(sessionRepository).saveAll(sessionsCaptor.capture());
        return (List<TimetableSession>) sessionsCaptor.getValue();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers — cache mémoire (ReflectionTestUtils)
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<Long, TimetableSolution> getBestSolutionsCache() {
        return (Map<Long, TimetableSolution>)
                ReflectionTestUtils.getField(service, "bestSolutions");
    }

    private void putInCache(Long jobId, TimetableSolution sol) {
        getBestSolutionsCache().put(jobId, sol);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. Interruption du solveur
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * {@code getFinalBestSolution()} bloque jusqu'à la fin du solveur, et lève une
     * {@code InterruptedException} quand le pool qui l'exécute est arrêté — à la
     * fermeture du contexte Spring, typiquement.
     *
     * <p>Deux choses doivent alors se produire, et la seconde est celle qu'on
     * oublie. La première : le job passe en FAILED, sinon il reste RUNNING pour
     * toujours dans l'interface. La seconde : le drapeau d'interruption est
     * reposé sur le thread. Ce thread est emprunté au pool commun et sera rendu
     * puis réutilisé ; attraper l'exception sans reposer le drapeau efface
     * l'ordre d'arrêt pour tout ce qui s'y exécutera ensuite, qui n'aura aucun
     * moyen d'apprendre qu'on lui a demandé de s'arrêter.
     *
     * <p>Le drapeau est lu avec {@code Thread.interrupted()} et non
     * {@code isInterrupted()} : la lecture a lieu sur le thread concerné — le
     * simulacre s'exécute dans la tâche asynchrone — et {@code interrupted()}
     * efface le drapeau au passage. C'est aussi ce qui évite de rendre au pool
     * commun un thread encore marqué, qui ferait dérailler un test ultérieur.
     */
    @Test
    @DisplayName("Un solveur interrompu échoue le job et repose le drapeau d'interruption")
    void solveurInterrompu_echoueLeJobEtReposeLeDrapeau() throws Exception {
        TenantContext.setTenantId(TENANT);

        TimetableJob job = new TimetableJob();
        when(jobRepository.save(any())).thenAnswer(inv -> {
            TimetableJob j = inv.getArgument(0);
            j.setIdTimetableJob(JOB_ID);
            return j;
        });
        when(problemBuilder.build(TENANT, YEAR_ID, PROFILE_ID))
                .thenReturn(solution(FEASIBLE_SCORE, List.of()));
        when(preGenerationValidator.valider(any())).thenReturn(new ValidationReport(List.of()));

        @SuppressWarnings("unchecked")
        SolverJob<TimetableSolution, Long> solverJob = mock(SolverJob.class);
        when(solverJob.getFinalBestSolution())
                .thenThrow(new InterruptedException("pool arrêté"));
        when(solverManager.solveAndListen(eq(JOB_ID), any(TimetableSolution.class),
                ArgumentMatchers.<Consumer<TimetableSolution>>any()))
                .thenReturn(solverJob);

        // onFailed passe par findById : c'est notre point d'observation à
        // l'intérieur de la tâche asynchrone.
        AtomicBoolean drapeauPose = new AtomicBoolean(false);
        CountDownLatch echecTraite = new CountDownLatch(1);
        when(jobRepository.findById(JOB_ID)).thenAnswer(inv -> {
            drapeauPose.set(Thread.interrupted());
            echecTraite.countDown();
            return Optional.of(job);
        });

        TimetableGenerationRequest req = new TimetableGenerationRequest();
        req.setSchoolYearId(YEAR_ID);
        req.setConstraintProfileId(PROFILE_ID);

        service.startGeneration(req);

        assertThat(echecTraite.await(5, TimeUnit.SECONDS))
                .as("l'échec doit être traité, pas avalé")
                .isTrue();
        assertThat(drapeauPose).isTrue();
        assertThat(job.getStatus()).isEqualTo(SolverStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("pool arrêté");
    }
}
