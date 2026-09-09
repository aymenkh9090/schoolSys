package tn.wtm.school.planning.solver.service;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.planning.solver.builder.TimetableProblemBuilder;
import tn.wtm.school.planning.solver.constraint.ConstraintCodes;
import tn.wtm.school.planning.solver.constraint.TimetableConstraintProvider;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSession;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.dto.response.ScoreExplanationResponse;
import tn.wtm.school.planning.solver.entity.TimetableJob;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.enums.SolverStatus;
import tn.wtm.school.planning.solver.port.SolverProgressPublisher;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;
import tn.wtm.school.planning.solver.repository.GeneratedTimetableRepository;
import tn.wtm.school.planning.solver.repository.TimetableJobRepository;
import tn.wtm.school.planning.solver.repository.TimetableSessionRepository;
import tn.wtm.school.planning.solver.validation.PreGenerationValidator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * L'explication de score ne décrit plus les conflits, elle les <b>désigne</b>.
 *
 * <p>Le champ {@code examples} produisait déjà une phrase juste — « Maths · 7A ·
 * Mr. Dupont · lundi 08:00 ». Elle ne servait qu'à lire : rien dedans ne
 * permettait à l'interface de surligner la case fautive, ni à une suggestion de
 * viser le {@code PATCH .../sessions/{sessionId}} qui sait déplacer cette
 * séance. Le maillon manquant était l'identifiant, et c'est ce que ces tests
 * vérifient.
 *
 * <p><b>Le solveur est réel ici</b>, comme dans les tests de contraintes : les
 * {@code ConstraintMatch} examinés sont ceux que Timefold produit sur un conflit
 * d'enseignant construit exprès, et non ceux d'un double qui dirait ce qui
 * arrange. Un test qui simulerait l'explication ne prouverait rien de la chaîne
 * qu'on veut garantir.
 */
@ExtendWith(MockitoExtension.class)
class ScoreExplanationOccurrencesTest {

    private static final String TENANT  = "ecole-1";
    private static final Long   JOB_ID  = 42L;
    private static final Long   YEAR_ID = 2026L;

    private static final SolverConfig CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    @Mock private TimetableProblemBuilder      problemBuilder;
    @Mock private PreGenerationValidator       preGenerationValidator;
    @Mock private TimetableJobRepository       jobRepository;
    @Mock private TimetableSessionRepository   sessionRepository;
    @Mock private GeneratedTimetableRepository generatedTimetableRepository;
    @Mock private SolverManager<TimetableSolution, Long> solverManager;
    @Mock private TransactionTemplate          txTemplate;
    @Mock private ObjectProvider<SolverProgressPublisher> progressPublishers;

    private TimetableSolverService service;

    /** Le vrai calculateur de score, celui du solveur de production. */
    private final SolutionManager<TimetableSolution, HardMediumSoftScore> solutionManager =
            SolutionManager.create(SolverFactory.create(CONFIG));

    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Mr. Dupont").maxHoursPerDay(6).build();
    private final RoomRef salleA = RoomRef.builder()
            .id(1L).code("A1").type(RoomType.NORMALE).capacity(35).build();
    private final RoomRef salleB = RoomRef.builder()
            .id(2L).code("B2").type(RoomType.NORMALE).capacity(35).build();

    /** Lundi 8 h : le créneau sur lequel les deux séances se télescopent. */
    private final TimeSlotRef lundi8h = TimeSlotRef.builder()
            .id(1L).day(DayOfWeek.MONDAY).orderIndex(1)
            .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
            .maxDurationSlots(2).active(true).build();

    @BeforeEach
    void setUp() {
        service = new TimetableSolverService(
                problemBuilder, preGenerationValidator, jobRepository, sessionRepository,
                generatedTimetableRepository, solverManager, solutionManager, txTemplate,
                progressPublishers);
        TenantContext.setTenantId(TENANT);
        lenient().when(jobRepository.findByIdTimetableJobAndTenantId(JOB_ID, TENANT))
                .thenReturn(Optional.of(job()));
    }

    @AfterEach
    void viderLeTenant() {
        TenantContext.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Le conflit est désigné, pas seulement décrit
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Un conflit d'enseignant porte les deux séances en cause, identifiants compris")
    void conflitEnseignant_porteLesDeuxSeancesIdentifiees() {
        armer(deuxSeancesEnConflit(), sessionsPersistees());

        ScoreExplanationResponse.Occurrence conflit = premierConflitEnseignant();

        assertThat(conflit.getSessions())
                .hasSize(2)
                .extracting(ScoreExplanationResponse.SessionRef::getSessionId)
                .containsExactlyInAnyOrder(1001L, 1002L);
    }

    @Test
    @DisplayName("Surligner une seule des deux séances ne montrerait pas le conflit")
    void lesDeuxSeancesSontDesClassesDifferentes() {
        armer(deuxSeancesEnConflit(), sessionsPersistees());

        assertThat(premierConflitEnseignant().getSessions())
                .extracting(ScoreExplanationResponse.SessionRef::getClassName)
                .containsExactlyInAnyOrder("7A", "7B");
    }

    @Test
    @DisplayName("Chaque séance désignée porte ses coordonnées dans la grille")
    void chaqueSeancePorteSesCoordonnees() {
        armer(deuxSeancesEnConflit(), sessionsPersistees());

        ScoreExplanationResponse.SessionRef seance = premierConflitEnseignant().getSessions().stream()
                .filter(s -> "7A".equals(s.getClassName()))
                .findFirst().orElseThrow();

        assertThat(seance.getLessonId()).isEqualTo(1L);
        assertThat(seance.getSubjectCode()).isEqualTo("MATH");
        assertThat(seance.getTeacherCode()).isEqualTo("T1");
        assertThat(seance.getRoomCode()).isEqualTo("A1");
        assertThat(seance.getDay()).isEqualTo("MONDAY");
        assertThat(seance.getStartTime()).isEqualTo("08:00");
        assertThat(seance.getGroupIndex()).isZero();
    }

    @Test
    @DisplayName("L'occurrence porte le score de ce conflit-là, pas celui de la contrainte entière")
    void occurrencePorteSonPropreScore() {
        armer(deuxSeancesEnConflit(), sessionsPersistees());

        assertThat(premierConflitEnseignant().getScore()).isEqualTo("-1hard/0medium/0soft");
    }

    @Test
    @DisplayName("La phrase lisible reste, à l'identique de examples")
    void laPhraseResteDisponible() {
        armer(deuxSeancesEnConflit(), sessionsPersistees());

        ScoreExplanationResponse.ConstraintViolation violation = conflitEnseignant();
        assertThat(premierConflitEnseignant().getLabel())
                .isNotBlank()
                .isIn(violation.getExamples());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ce qui manque est laissé vide, jamais deviné
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Sans ligne persistée en face, la séance est désignée sans identifiant plutôt qu'au hasard")
    void jobAnterieurALaColonne_sessionIdReste() {
        // Le cas d'un job produit avant la colonne lesson_id : les lignes sont
        // là, mais aucune ne dit de quelle Lesson elle vient. Rapprocher sur les
        // coordonnées désignerait la mauvaise séance dès le premier déplacement
        // manuel — on préfère ne rien affirmer.
        armer(deuxSeancesEnConflit(), sessionsSansLessonId());

        assertThat(premierConflitEnseignant().getSessions())
                .isNotEmpty()
                .allSatisfy(s -> {
                    assertThat(s.getSessionId()).isNull();
                    assertThat(s.getLessonId()).isNotNull();
                });
    }

    @Test
    @DisplayName("La solution disparue de la mémoire rend une liste vide, jamais null")
    void solutionAbsente_occurrencesVidesEtNonNull() {
        // Pas de solution injectée : c'est l'état d'après un redémarrage. Le
        // service sort avant même de lire les séances — d'où l'absence de stub
        // sur sessionRepository, que Mockito signalerait comme inutile.
        ScoreExplanationResponse reponse = service.explainScore(JOB_ID);

        assertThat(reponse.getHardViolations())
                .isNotEmpty()
                .allSatisfy(v -> assertThat(v.getOccurrences()).isNotNull().isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Injecte la solution dans le cache mémoire du service et branche les lignes persistées. */
    @SuppressWarnings("unchecked")
    private void armer(TimetableSolution solution, List<TimetableSession> sessions) {
        // Le score doit être posé : explainScore refuse une solution non évaluée.
        solutionManager.update(solution);
        ((Map<Long, TimetableSolution>) ReflectionTestUtils.getField(service, "bestSolutions"))
                .put(JOB_ID, solution);
        when(sessionRepository.findByJobIdAndTenantId(JOB_ID, TENANT)).thenReturn(sessions);
    }

    private ScoreExplanationResponse.ConstraintViolation conflitEnseignant() {
        return service.explainScore(JOB_ID).getHardViolations().stream()
                .filter(v -> ConstraintCodes.TEACHER_CONFLICT.equals(v.getConstraintName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Le décor devait produire un conflit d'enseignant et n'en produit aucun"));
    }

    private ScoreExplanationResponse.Occurrence premierConflitEnseignant() {
        return conflitEnseignant().getOccurrences().getFirst();
    }

    /** Le même enseignant, à la même heure, devant deux classes. */
    private TimetableSolution deuxSeancesEnConflit() {
        return TimetableSolution.builder()
                .tenantId(TENANT).academicYearId(YEAR_ID).constraintProfileId(1L)
                .timeSlots(List.of(lundi8h))
                .teachers(List.of(prof))
                .rooms(List.of(salleA, salleB))
                .lessons(List.of(
                        seance(1L, "MATH", "Mathématiques", "7A", salleA),
                        seance(2L, "MATH", "Mathématiques", "7B", salleB)))
                .build();
    }

    private Lesson seance(Long id, String code, String nom, String classe, RoomRef salle) {
        return Lesson.builder()
                .id(id)
                .subjectCode(code).subjectName(nom)
                .studentClassName(classe).studentClassLevel("7EME")
                .teachingAssignmentId(id)
                .sessionType(SessionType.COURS)
                .groupIndex(0).classStudentCount(30)
                .durationSlots(2)
                .teacher(prof).room(salle).timeSlot(lundi8h)
                .build();
    }

    /** Les deux lignes en base, portant le lessonId de leur séance d'origine. */
    private List<TimetableSession> sessionsPersistees() {
        return List.of(session(1001L, 1L), session(1002L, 2L));
    }

    /** Les mêmes lignes, telles qu'un job antérieur à la colonne les a laissées. */
    private List<TimetableSession> sessionsSansLessonId() {
        return List.of(session(1001L, null), session(1002L, null));
    }

    private TimetableSession session(Long sessionId, Long lessonId) {
        TimetableSession s = TimetableSession.builder()
                .jobId(JOB_ID).academicYearId(YEAR_ID)
                .lessonId(lessonId)
                .day(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .build();
        s.setIdTimetableSession(sessionId);
        s.setTenantId(TENANT);
        return s;
    }

    private TimetableJob job() {
        TimetableJob job = TimetableJob.builder()
                .academicYearId(YEAR_ID).constraintProfileId(1L)
                .status(SolverStatus.SOLVED).scoreAchieved("-1hard/0medium/0soft")
                .build();
        job.setIdTimetableJob(JOB_ID);
        job.setTenantId(TENANT);
        return job;
    }
}
