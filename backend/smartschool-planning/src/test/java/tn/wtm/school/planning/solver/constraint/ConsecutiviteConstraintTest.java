package tn.wtm.school.planning.solver.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.enums.WeekParity;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La consécutivité, mesurée pour ce qu'elle est — étape F du plan de mise en
 * conformité.
 *
 * <p>{@code MAX_TWO_CONSECUTIVE_SESSIONS_SAME_SUBJECT} comptait les séances de la
 * matière <em>dans la journée</em>, sans regarder où elles tombaient : deux
 * heures de mathématiques à 8 h et à 16 h passaient pour consécutives, et trois
 * heures d'affilée coupées par une heure d'anglais ne l'étaient pas. Elle
 * mesurait la concentration — que {@code AVOID_SUBJECT_CONCENTRATION_SAME_DAY}
 * mesurait déjà, avec le même {@code groupBy}.
 *
 * <p><b>La pénalité est isolée par différence</b> : le score avec le réglage
 * actif, moins le score sans lui. La contrainte est dure, et d'autres
 * contraintes dures tournent toujours — {@code noStudentIdleGaps} en
 * particulier, qui réagit précisément aux emplois du temps dispersés que ces cas
 * construisent. Lire {@code hardScore()} brut mesurerait leur somme, pas celle
 * qu'on teste.
 */
class ConsecutiviteConstraintTest {

    private static final SolverConfig CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    private final SolutionManager<TimetableSolution, HardMediumSoftScore> scorer =
            SolutionManager.create(SolverFactory.create(CONFIG));

    /** Une heure de cours = deux créneaux de 30 min. */
    private static final int UNE_HEURE   = 2;
    private static final int DEUX_HEURES = 4;

    /** Le plafond de la circulaire, et le défaut du catalogue. */
    private static final int DEUX_SEANCES = 2;

    private final RoomRef salle = RoomRef.builder()
            .id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build();
    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Prof").maxHoursPerDay(6).build();

    // ══════════════════════════════════════════════════════════════════════════
    // Ce que la contrainte mesure désormais
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Suites de séances contiguës")
    class Suites {

        @Test
        @DisplayName("Deux heures d'affilée restent sous le plafond")
        void deuxDAffilee() {
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3))))
                    .isZero();
        }

        @Test
        @DisplayName("Trois heures d'affilée dépassent d'une")
        void troisDAffilee() {
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(3L, "MATH", matin(DayOfWeek.MONDAY, 5))))
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("Quatre heures d'affilée dépassent de deux")
        void quatreDAffilee() {
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(3L, "MATH", matin(DayOfWeek.MONDAY, 5)),
                    cours(4L, "MATH", matin(DayOfWeek.MONDAY, 7))))
                    .as("la pénalité suit la longueur de la suite, pas le nombre de séances")
                    .isEqualTo(-2);
        }

        @Test
        @DisplayName("Une séance de deux heures enchaînée à une d'une heure fait une suite de deux")
        void dureesInegales() {
            // La première occupe quatre créneaux : la suivante s'y enchaîne au
            // cinquième, pas au troisième. Compter les séances sans lire leur
            // durée aurait ici rompu la suite à tort.
            assertThat(penaliteConsecutivite(
                    coursLong(1L, "MATH", matin(DayOfWeek.MONDAY, 1), DEUX_HEURES),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 5))))
                    .isZero();
        }

        @Test
        @DisplayName("Trois séances dont deux seulement s'enchaînent")
        void suiteInterrompue() {
            // 8 h, 9 h, puis 11 h. Trois séances dans la matinée, mais l'élève
            // n'en enchaîne jamais plus de deux — l'ancienne version pénalisait.
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(3L, "MATH", matin(DayOfWeek.MONDAY, 7))))
                    .isZero();
        }

        @Test
        @DisplayName("Le matin et l'après-midi ne s'enchaînent pas")
        void deuxCotesDeLaPause() {
            // Trois heures dans la journée, mais la pause du § I.3 sépare la
            // dernière heure de la matinée de la première de l'après-midi.
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 5)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 7)),
                    cours(3L, "MATH", apresMidi(DayOfWeek.MONDAY, 1))))
                    .isZero();
        }

        @Test
        @DisplayName("Deux jours différents ne s'enchaînent pas")
        void joursDifferents() {
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(3L, "MATH", matin(DayOfWeek.TUESDAY, 1))))
                    .isZero();
        }

        @Test
        @DisplayName("Deux matières différentes ne forment pas une suite")
        void matieresDifferentes() {
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(3L, "ARABE", matin(DayOfWeek.MONDAY, 5))))
                    .isZero();
        }

        @Test
        @DisplayName("Deux classes différentes ne se cumulent pas")
        void classesDifferentes() {
            assertThat(penaliteConsecutivite(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    coursDe(3L, "7B", "MATH", matin(DayOfWeek.MONDAY, 5))))
                    .isZero();
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            Lesson[] quatreDAffilee = {
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(3L, "MATH", matin(DayOfWeek.MONDAY, 5)),
                    cours(4L, "MATH", matin(DayOfWeek.MONDAY, 7))};

            assertThat(penaliteConsecutivite(quatreDAffilee))
                    .as("le réglage actif compte bien la suite")
                    .isEqualTo(-2);
            assertThat(scoreAvec(null, quatreDAffilee).hardScore())
                    .as("sans le réglage, la jointure est vide et la suite ne coûte rien")
                    .isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Quinzaine
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Séances de quinzaine (§ N.1)")
    class Quinzaine {

        @Test
        @DisplayName("Une quinzaine ne prolonge la suite que les semaines où elle a lieu")
        void quinzaineNAllongePas() {
            // Semaine impaire : la séance hebdomadaire puis la quinzaine impaire,
            // deux d'affilée. Semaine paire : la séance hebdomadaire puis un trou,
            // la quinzaine paire arrive après. L'élève n'enchaîne jamais trois
            // heures — les compter toutes en inventerait une.
            Lesson hebdomadaire = cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1));
            Lesson impaire = quinzaine(2L, "MATH", matin(DayOfWeek.MONDAY, 3), WeekParity.ODD);
            Lesson paire   = quinzaine(3L, "MATH", matin(DayOfWeek.MONDAY, 5), WeekParity.EVEN);

            assertThat(penaliteConsecutivite(hebdomadaire, impaire, paire)).isZero();
        }

        @Test
        @DisplayName("Trois séances qui s'enchaînent la même semaine sont bien comptées")
        void memeSemaine() {
            // Les trois portent la même parité : la suite existe réellement,
            // une semaine sur deux. La parité ne doit pas servir d'échappatoire.
            Lesson a = quinzaine(1L, "MATH", matin(DayOfWeek.MONDAY, 1), WeekParity.ODD);
            Lesson b = quinzaine(2L, "MATH", matin(DayOfWeek.MONDAY, 3), WeekParity.ODD);
            Lesson c = quinzaine(3L, "MATH", matin(DayOfWeek.MONDAY, 5), WeekParity.ODD);

            assertThat(penaliteConsecutivite(a, b, c)).isEqualTo(-1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Le doublon levé
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Consécutivité et concentration ne se recouvrent plus")
    class DoublonLeve {

        /** Trois heures de maths dispersées dans la matinée : 8 h, 9 h, 11 h. */
        private Lesson[] dispersees() {
            return new Lesson[]{
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(3L, "MATH", matin(DayOfWeek.MONDAY, 7))};
        }

        @Test
        @DisplayName("La consécutivité se tait sur une matière dispersée")
        void consecutiviteSeTait() {
            assertThat(penaliteConsecutivite(dispersees())).isZero();
        }

        @Test
        @DisplayName("La concentration, elle, la signale toujours")
        void concentrationParle() {
            // Les deux contraintes avaient le même groupBy et disaient la même
            // chose. Sur ce cas précis, elles disent maintenant deux choses
            // différentes — et c'est exactement ce que l'étape F cherchait.
            assertThat(scoreAvec(new ActiveConstraintParam(
                    ConstraintCodes.AVOID_SUBJECT_CONCENTRATION_SAME_DAY, 0, 10),
                    dispersees()).softScore())
                    .isNegative();
        }
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    /**
     * La pénalité imputable à la seule consécutivité : score avec le réglage,
     * moins score sans lui.
     */
    private int penaliteConsecutivite(Lesson... seances) {
        ActiveConstraintParam actif = new ActiveConstraintParam(
                ConstraintCodes.MAX_TWO_CONSECUTIVE_SESSIONS, DEUX_SEANCES, 100);
        return scoreAvec(actif, seances).hardScore() - scoreAvec(null, seances).hardScore();
    }

    private HardMediumSoftScore scoreAvec(ActiveConstraintParam param, Lesson... seances) {
        return scorer.update(TimetableSolution.builder()
                .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                .timeSlots(tousLesCreneaux())
                .teachers(List.of(prof))
                .rooms(List.of(salle))
                .lessons(List.of(seances))
                .activeConstraintParams(param == null ? List.of() : List.of(param))
                .build());
    }

    private Lesson cours(Long id, String matiere, TimeSlotRef creneau) {
        return coursDe(id, "7A", matiere, creneau);
    }

    private Lesson coursDe(Long id, String classe, String matiere, TimeSlotRef creneau) {
        return base(id, classe, matiere, creneau, UNE_HEURE).build();
    }

    private Lesson coursLong(Long id, String matiere, TimeSlotRef creneau, int duree) {
        return base(id, "7A", matiere, creneau, duree).build();
    }

    private Lesson quinzaine(Long id, String matiere, TimeSlotRef creneau, WeekParity semaine) {
        return base(id, "7A", matiere, creneau, UNE_HEURE).weekParity(semaine).build();
    }

    private Lesson.LessonBuilder base(Long id, String classe, String matiere,
                                       TimeSlotRef creneau, int duree) {
        return Lesson.builder()
                .id(id).studentClassName(classe).subjectCode(matiere).subjectName(matiere)
                .teachingAssignmentId(id).classStudentCount(30)
                .sessionType(SessionType.COURS).groupIndex(0)
                .durationSlots(duree).officialWeeklySlots(0)
                .timeSlot(creneau).teacher(prof).room(salle);
    }

    // ── créneaux ──────────────────────────────────────────────────────────────
    //
    // Trente minutes chacun ; `rang` est le numéro du créneau dans sa
    // demi-journée. Les instances sont uniques : Timefold vérifie l'appartenance
    // au domaine de valeurs par identité, TimeSlotRef n'ayant pas de @PlanningId.

    private static final int CRENEAUX_PAR_DEMI_JOURNEE = 8;

    private static final Map<Long, TimeSlotRef> CRENEAUX = construireLaSemaine();

    private static TimeSlotRef matin(DayOfWeek jour, int rang) {
        return CRENEAUX.get(identifiant(jour, DayPeriod.MORNING, rang));
    }

    private static TimeSlotRef apresMidi(DayOfWeek jour, int rang) {
        return CRENEAUX.get(identifiant(jour, DayPeriod.AFTERNOON, rang));
    }

    private static long identifiant(DayOfWeek jour, DayPeriod periode, int rang) {
        return jour.getValue() * 100L
                + (periode == DayPeriod.MORNING ? 0 : CRENEAUX_PAR_DEMI_JOURNEE) + rang;
    }

    private static Map<Long, TimeSlotRef> construireLaSemaine() {
        Map<Long, TimeSlotRef> semaine = new LinkedHashMap<>();
        for (DayOfWeek jour : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) {
            for (int rang = 1; rang <= CRENEAUX_PAR_DEMI_JOURNEE; rang++) {
                ajouter(semaine, jour, DayPeriod.MORNING, rang, LocalTime.of(8, 0));
                ajouter(semaine, jour, DayPeriod.AFTERNOON, rang, LocalTime.of(14, 0));
            }
        }
        return semaine;
    }

    private static void ajouter(Map<Long, TimeSlotRef> semaine, DayOfWeek jour,
                                DayPeriod periode, int rang, LocalTime origine) {
        int decalage = rang - 1;
        LocalTime debut = origine.plusMinutes(30L * decalage);
        long id = identifiant(jour, periode, rang);
        semaine.put(id, TimeSlotRef.builder()
                .id(id)
                .day(jour).period(periode).orderIndex((int) (id % 100))
                .startTime(debut).endTime(debut.plusMinutes(30))
                .active(true)
                .maxDurationSlots(CRENEAUX_PAR_DEMI_JOURNEE - decalage)
                .build());
    }

    private static List<TimeSlotRef> tousLesCreneaux() {
        return new ArrayList<>(CRENEAUX.values());
    }
}
