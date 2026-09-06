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
 * La parité de semaine dans les deux contraintes de continuité — étape G du plan
 * de mise en conformité.
 *
 * <p>{@code NO_STUDENT_IDLE_GAPS} (§ I.5) et {@code MIN_STUDENT_HOURS_PER_HALF_DAY}
 * (§ I.2) regroupaient les séances d'une demi-journée sans regarder quelles
 * semaines elles ont lieu. Une séance de quinzaine comblait donc un trou qu'elle
 * ne comble qu'une semaine sur deux, et remplissait une demi-journée qu'elle ne
 * remplit qu'une semaine sur deux. Les deux contraintes se taisaient sur des
 * emplois du temps que l'élève subit réellement.
 *
 * <p><b>La pénalité est lue par contrainte</b>, dans les totaux d'explication du
 * score, et non sur le score dur global : ces deux contraintes tournent au
 * milieu d'une dizaine d'autres contraintes dures que les cas construits ici
 * peuvent déclencher au passage.
 */
class PariteSemaineContinuiteTest {

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

    /** Le plancher du § I.2, et le défaut du catalogue. */
    private static final int DEUX_HEURES_MINIMUM = 2;

    private final RoomRef salle = RoomRef.builder()
            .id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build();
    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Prof").maxHoursPerDay(6).build();

    // ══════════════════════════════════════════════════════════════════════════
    // § I.5 — les heures creuses
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Heures creuses")
    class HeuresCreuses {

        @Test
        @DisplayName("Deux heures qui s'enchaînent ne laissent aucun trou")
        void deuxHeuresContigues() {
            assertThat(trous(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "FR", matin(DayOfWeek.MONDAY, 3))))
                    .isZero();
        }

        @Test
        @DisplayName("Une heure libre entre deux cours hebdomadaires est un trou")
        void trouEntreDeuxCours() {
            assertThat(trous(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "FR", matin(DayOfWeek.MONDAY, 5))))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Une quinzaine ne bouche le trou que la semaine où elle a lieu")
        void quinzaineNeBouchePasLesDeuxSemaines() {
            // Semaine impaire : 8 h, 9 h, 10 h — l'élève enchaîne.
            // Semaine paire   : 8 h puis 10 h — il attend une heure au collège.
            // Sans la parité, les trois séances se suivaient et rien n'était dit.
            assertThat(trous(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    quinzaine(2L, "SVT", matin(DayOfWeek.MONDAY, 3), WeekParity.ODD),
                    cours(3L, "FR", matin(DayOfWeek.MONDAY, 5))))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Deux quinzaines opposées ne créent aucun trou")
        void quinzainesOpposees() {
            // Aucune semaine ne voit les deux : chaque semaine n'a qu'une séance.
            // Le comptage aveugle y lisait un trou d'une heure qui n'existe pas.
            assertThat(trous(
                    quinzaine(1L, "PHY", matin(DayOfWeek.MONDAY, 1), WeekParity.ODD),
                    quinzaine(2L, "SVT", matin(DayOfWeek.MONDAY, 5), WeekParity.EVEN)))
                    .isZero();
        }

        @Test
        @DisplayName("Deux quinzaines de la même semaine, elles, en créent un")
        void quinzainesDeLaMemeSemaine() {
            assertThat(trous(
                    quinzaine(1L, "PHY", matin(DayOfWeek.MONDAY, 1), WeekParity.ODD),
                    quinzaine(2L, "SVT", matin(DayOfWeek.MONDAY, 5), WeekParity.ODD)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Le trou est compté une fois par demi-journée, pas une fois par semaine")
        void unePenaliteParDemiJournee() {
            // Le trou est là les deux semaines ; la demi-journée reste une.
            assertThat(trous(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "FR", matin(DayOfWeek.MONDAY, 5)),
                    cours(3L, "ANG", apresMidi(DayOfWeek.MONDAY, 1)),
                    cours(4L, "HIST", apresMidi(DayOfWeek.MONDAY, 5))))
                    .isEqualTo(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § I.2 — le plancher de la demi-journée
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plancher de deux heures par demi-journée")
    class Plancher {

        @Test
        @DisplayName("Deux heures pleines atteignent le plancher")
        void deuxHeuresSuffisent() {
            assertThat(manque(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "FR", matin(DayOfWeek.MONDAY, 3))))
                    .isZero();
        }

        @Test
        @DisplayName("Une heure seule manque d'une heure")
        void uneHeureSeule() {
            assertThat(manque(cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1))))
                    .isEqualTo(UNE_HEURE);
        }

        @Test
        @DisplayName("Une quinzaine ne remplit la demi-journée que sa semaine")
        void quinzaineNeRemplitPasLesDeuxSemaines() {
            // Semaine impaire : deux heures, la classe a de quoi se déplacer.
            // Semaine paire   : une heure de maths, et rien d'autre.
            // Le total aveugle valait deux heures et la règle se taisait.
            assertThat(manque(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    quinzaine(2L, "SVT", matin(DayOfWeek.MONDAY, 3), WeekParity.ODD)))
                    .isEqualTo(UNE_HEURE);
        }

        @Test
        @DisplayName("Une quinzaine de deux heures se suffit à elle-même")
        void quinzaineDeDeuxHeures() {
            // Semaine impaire : deux heures, le plancher est atteint.
            // Semaine paire   : demi-journée libre — la classe ne vient pas, il
            // n'y a rien à lui reprocher. La correction ne doit rien inventer ici.
            assertThat(manque(quinzaineLongue(1L, "TECH", matin(DayOfWeek.MONDAY, 1),
                    WeekParity.ODD, DEUX_HEURES)))
                    .isZero();
        }

        @Test
        @DisplayName("L'exemption de l'EPS se réévalue semaine par semaine")
        void exemptionSportive() {
            // Semaine paire, il ne reste que l'heure de sport : le § I.2 l'exclut
            // nommément. L'exemption porte sur la demi-journée telle qu'elle est
            // vécue cette semaine-là, pas sur la liste brute des séances.
            assertThat(manque(
                    sport(1L, matin(DayOfWeek.MONDAY, 1)),
                    quinzaine(2L, "MATH", matin(DayOfWeek.MONDAY, 3), WeekParity.ODD)))
                    .isZero();
        }

        @Test
        @DisplayName("La pire des deux semaines l'emporte")
        void pireDesDeuxSemaines() {
            // Semaine impaire : trois heures. Semaine paire : une seule.
            // C'est la semaine paire qui est jugée.
            assertThat(manque(
                    cours(1L, "MATH", matin(DayOfWeek.MONDAY, 1)),
                    quinzaine(2L, "SVT", matin(DayOfWeek.MONDAY, 3), WeekParity.ODD),
                    quinzaine(3L, "PHY", matin(DayOfWeek.MONDAY, 5), WeekParity.ODD)))
                    .isEqualTo(UNE_HEURE);
        }
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    /** Nombre de demi-journées trouées, lu sur la seule contrainte § I.5. */
    private int trous(Lesson... seances) {
        return -penalite(ConstraintCodes.NO_STUDENT_IDLE_GAPS, null, seances);
    }

    /** Créneaux manquants au plancher, lus sur la seule contrainte § I.2. */
    private int manque(Lesson... seances) {
        ActiveConstraintParam actif = new ActiveConstraintParam(
                ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY, DEUX_HEURES_MINIMUM, 100);
        return -penalite(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY, actif, seances);
    }

    /**
     * Le score dur imputable à une contrainte et à elle seule — négatif, ou zéro
     * quand elle n'a produit aucune correspondance.
     */
    private int penalite(String code, ActiveConstraintParam param, Lesson... seances) {
        return scorer.explain(TimetableSolution.builder()
                        .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                        .timeSlots(tousLesCreneaux())
                        .teachers(List.of(prof))
                        .rooms(List.of(salle))
                        .lessons(List.of(seances))
                        .activeConstraintParams(param == null ? List.of() : List.of(param))
                        .build())
                .getConstraintMatchTotalMap().values().stream()
                .filter(total -> code.equals(total.getConstraintName()))
                .mapToInt(total -> total.getScore().hardScore())
                .sum();
    }

    private Lesson cours(Long id, String matiere, TimeSlotRef creneau) {
        return base(id, matiere, creneau, UNE_HEURE).build();
    }

    private Lesson sport(Long id, TimeSlotRef creneau) {
        return base(id, "EPS", creneau, UNE_HEURE).sessionType(SessionType.SPORT).build();
    }

    private Lesson quinzaine(Long id, String matiere, TimeSlotRef creneau, WeekParity semaine) {
        return quinzaineLongue(id, matiere, creneau, semaine, UNE_HEURE);
    }

    private Lesson quinzaineLongue(Long id, String matiere, TimeSlotRef creneau,
                                   WeekParity semaine, int duree) {
        return base(id, matiere, creneau, duree).weekParity(semaine).build();
    }

    private Lesson.LessonBuilder base(Long id, String matiere, TimeSlotRef creneau, int duree) {
        return Lesson.builder()
                .id(id).studentClassName("7A").subjectCode(matiere).subjectName(matiere)
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
