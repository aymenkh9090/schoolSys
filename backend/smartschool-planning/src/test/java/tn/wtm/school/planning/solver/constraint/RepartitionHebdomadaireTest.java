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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les deux règles de répartition écrites à l'étape I — § II.2 pour le service de
 * l'enseignant, § III.1 pour les heures d'une matière.
 *
 * <p>Toutes deux figuraient au catalogue depuis l'origine sans qu'aucun flux ne
 * les évalue : l'établissement pouvait les activer, elles ne faisaient rien.
 * C'est le défaut P1, dans sa forme la plus simple.
 *
 * <p><b>La pénalité est lue par contrainte</b>, dans les totaux d'explication du
 * score : ces deux règles sont SOFT et tournent au milieu de plusieurs autres
 * contraintes SOFT que les cas construits ici déclenchent au passage. Le poids
 * est fixé à 1 pour que la pénalité lue soit la mesure brute.
 */
class RepartitionHebdomadaireTest {

    private static final SolverConfig CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    private final SolutionManager<TimetableSolution, HardMediumSoftScore> scorer =
            SolutionManager.create(SolverFactory.create(CONFIG));

    /** Une heure de cours = deux créneaux de 30 min. */
    private static final int UNE_HEURE = 2;

    private final RoomRef salle = RoomRef.builder()
            .id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build();
    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Prof").maxHoursPerDay(6).build();

    // ══════════════════════════════════════════════════════════════════════════
    // § II.2 — le service se répartit sur les jours de travail
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Service de l'enseignant")
    class Service {

        @Test
        @DisplayName("Six heures sur trois jours ne concentrent rien")
        void serviceReparti() {
            assertThat(service(prof, deuxHeures(DayOfWeek.MONDAY),
                    deuxHeures(DayOfWeek.TUESDAY), deuxHeures(DayOfWeek.WEDNESDAY)))
                    .isZero();
        }

        @Test
        @DisplayName("Les mêmes six heures sur deux jours en concentrent quatre créneaux")
        void serviceConcentre() {
            // Part équitable sur six jours ouvrés : une heure. Le plancher du
            // même article la relève à deux heures — c'est lui qui fixe le
            // plafond. Deux journées de trois heures dépassent donc d'une heure
            // chacune, soit quatre créneaux.
            assertThat(service(prof, troisHeures(DayOfWeek.MONDAY), troisHeures(DayOfWeek.TUESDAY)))
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("Le plancher de deux heures protège l'enseignant à temps partiel")
        void plancherDeuxHeures() {
            // Trois heures de service : la part équitable sur six jours vaut une
            // demi-heure. Sans le plancher, la contrainte réclamerait une
            // demi-heure par jour six jours par semaine — ce que le § II.2
            // interdit dans la phrase suivante.
            assertThat(service(prof, deuxHeures(DayOfWeek.MONDAY), uneHeure(DayOfWeek.TUESDAY)))
                    .isZero();
        }

        @Test
        @DisplayName("Un enseignant présent deux jours n'a que deux jours à remplir")
        void joursIndisponiblesRetiresDuDiviseur() {
            TeacherRef partiel = TeacherRef.builder()
                    .id(2L).code("T2").name("Partiel").maxHoursPerDay(6)
                    .unavailableDays(Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
                    .build();

            // Six heures sur les deux seuls jours où il est là : la part
            // équitable vaut trois heures, il n'y a pas de concentration. Le
            // même emploi du temps chez un enseignant disponible toute la
            // semaine en est une.
            assertThat(service(partiel, troisHeures(DayOfWeek.MONDAY, partiel),
                    troisHeures(DayOfWeek.TUESDAY, partiel)))
                    .isZero();
        }

        @Test
        @DisplayName("Deux quinzaines opposées ne chargent pas la journée deux fois")
        void quinzainesOpposeesLeMemeJour() {
            // Lundi trois heures toutes les semaines ; mardi deux heures une
            // semaine sur deux, deux autres l'autre semaine. Aucune semaine ne
            // voit plus de deux heures le mardi. Compter les séances sans la
            // parité chargerait le mardi de quatre heures et inventerait une
            // concentration.
            assertThat(service(prof,
                    troisHeures(DayOfWeek.MONDAY),
                    quinzaine(DayOfWeek.TUESDAY, 1, WeekParity.ODD),
                    quinzaine(DayOfWeek.TUESDAY, 3, WeekParity.ODD),
                    quinzaine(DayOfWeek.TUESDAY, 5, WeekParity.EVEN),
                    quinzaine(DayOfWeek.TUESDAY, 7, WeekParity.EVEN)))
                    .isEqualTo(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § III.1 — les heures d'une matière se répartissent sur les deux périodes
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Répartition d'une matière")
    class Matiere {

        @Test
        @DisplayName("Une matière présente des deux côtés de la journée ne coûte rien")
        void matiereRepartie() {
            assertThat(matiere(
                    cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "7A", "MATH", apresMidi(DayOfWeek.TUESDAY, 1))))
                    .isZero();
        }

        @Test
        @DisplayName("Une matière entièrement matinale est massée")
        void matiereToutLeMatin() {
            assertThat(matiere(
                    cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "7A", "MATH", matin(DayOfWeek.TUESDAY, 1)),
                    cours(3L, "7A", "MATH", matin(DayOfWeek.WEDNESDAY, 1))))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("La règle est symétrique : entièrement l'après-midi aussi")
        void matiereToutLApresMidi() {
            assertThat(matiere(
                    cours(1L, "7A", "MATH", apresMidi(DayOfWeek.MONDAY, 1)),
                    cours(2L, "7A", "MATH", apresMidi(DayOfWeek.TUESDAY, 1))))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Une matière d'une seule séance n'a rien à répartir")
        void seanceUnique() {
            assertThat(matiere(cours(1L, "7A", "MUS", matin(DayOfWeek.MONDAY, 1))))
                    .isZero();
        }

        @Test
        @DisplayName("Le seuil de volume écarte les petites matières")
        void seuilDeVolume() {
            // Deux heures massées le matin, mais le réglage ne vise que les
            // matières d'au moins trois heures.
            ActiveConstraintParam seuilTroisHeures = new ActiveConstraintParam(
                    ConstraintCodes.MAIN_SUBJECT_BALANCED_DISTRIBUTION, 0, 1,
                    Map.of("weeklyHours", 3));
            assertThat(-penalite(ConstraintCodes.MAIN_SUBJECT_BALANCED_DISTRIBUTION,
                    seuilTroisHeures,
                    cours(1L, "7A", "HISTGEO", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "7A", "HISTGEO", matin(DayOfWeek.TUESDAY, 1))))
                    .isZero();
        }

        @Test
        @DisplayName("Chaque classe est jugée pour elle-même")
        void uneClasseNeMasquePasLAutre() {
            // La 7A masse ses maths le matin, la 7B les répartit. Une seule
            // pénalité, et c'est celle de la 7A.
            assertThat(matiere(
                    cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1)),
                    cours(2L, "7A", "MATH", matin(DayOfWeek.TUESDAY, 1)),
                    cours(3L, "7B", "MATH", matin(DayOfWeek.MONDAY, 3)),
                    cours(4L, "7B", "MATH", apresMidi(DayOfWeek.TUESDAY, 1))))
                    .isEqualTo(1);
        }
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    /** Créneaux de service concentrés au-delà de la part équitable — § II.2. */
    private int service(TeacherRef enseignant, Lesson... seances) {
        ActiveConstraintParam actif = new ActiveConstraintParam(
                ConstraintCodes.BALANCED_TEACHER_WORKLOAD, 0, 1, Map.of("workingDays", 6));
        return -penalite(ConstraintCodes.BALANCED_TEACHER_WORKLOAD, actif, enseignant, seances);
    }

    /** Couples classe / matière massés sur une seule période — § III.1. */
    private int matiere(Lesson... seances) {
        ActiveConstraintParam actif = new ActiveConstraintParam(
                ConstraintCodes.MAIN_SUBJECT_BALANCED_DISTRIBUTION, 0, 1);
        return -penalite(ConstraintCodes.MAIN_SUBJECT_BALANCED_DISTRIBUTION, actif, seances);
    }

    private int penalite(String code, ActiveConstraintParam param, Lesson... seances) {
        return penalite(code, param, prof, seances);
    }

    /**
     * Le score souple imputable à une contrainte et à elle seule — négatif, ou
     * zéro quand elle n'a produit aucune correspondance.
     */
    private int penalite(String code, ActiveConstraintParam param,
                         TeacherRef enseignant, Lesson... seances) {
        return scorer.explain(TimetableSolution.builder()
                        .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                        .timeSlots(tousLesCreneaux())
                        .teachers(List.of(enseignant))
                        .rooms(List.of(salle))
                        .lessons(List.of(seances))
                        .activeConstraintParams(List.of(param))
                        .build())
                .getConstraintMatchTotalMap().values().stream()
                .filter(total -> code.equals(total.getConstraintName()))
                .mapToInt(total -> total.getScore().softScore())
                .sum();
    }

    // ── séances ───────────────────────────────────────────────────────────────

    private long prochainId = 1L;

    private Lesson uneHeure(DayOfWeek jour) {
        return coursDe(prochainId++, "7A", "MATH", matin(jour, 1), UNE_HEURE, prof);
    }

    private Lesson deuxHeures(DayOfWeek jour) {
        return coursDe(prochainId++, "7A", "MATH", matin(jour, 1), 2 * UNE_HEURE, prof);
    }

    private Lesson troisHeures(DayOfWeek jour) {
        return troisHeures(jour, prof);
    }

    private Lesson troisHeures(DayOfWeek jour, TeacherRef enseignant) {
        return coursDe(prochainId++, "7A", "MATH", matin(jour, 1), 3 * UNE_HEURE, enseignant);
    }

    private Lesson quinzaine(DayOfWeek jour, int rang, WeekParity semaine) {
        return base(prochainId++, "7A", "SVT", matin(jour, rang), UNE_HEURE, prof)
                .weekParity(semaine).build();
    }

    private Lesson cours(Long id, String classe, String matiere, TimeSlotRef creneau) {
        return coursDe(id, classe, matiere, creneau, UNE_HEURE, prof);
    }

    private Lesson coursDe(Long id, String classe, String matiere, TimeSlotRef creneau,
                           int duree, TeacherRef enseignant) {
        return base(id, classe, matiere, creneau, duree, enseignant).build();
    }

    private Lesson.LessonBuilder base(Long id, String classe, String matiere,
                                      TimeSlotRef creneau, int duree, TeacherRef enseignant) {
        return Lesson.builder()
                .id(id).studentClassName(classe).subjectCode(matiere).subjectName(matiere)
                .teachingAssignmentId(id).classStudentCount(30)
                .sessionType(SessionType.COURS).groupIndex(0)
                .durationSlots(duree).officialWeeklySlots(0)
                .timeSlot(creneau).teacher(enseignant).room(salle);
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
