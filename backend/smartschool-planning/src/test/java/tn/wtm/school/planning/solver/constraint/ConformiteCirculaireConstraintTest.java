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
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les deux contraintes dures que le catalogue annonçait sans que le solveur les
 * évalue — étape C du plan de mise en conformité.
 *
 * <p>Elles n'étaient pas seulement absentes : elles étaient activées en CRITICAL
 * dans le profil d'un établissement réel, poids 1000, et n'ont jamais compté une
 * seule violation. Le job 11864 a ainsi produit 18 classes à deux séances d'EPS
 * et trois classes à zéro, tout en se déclarant conforme sur ce point.
 */
class ConformiteCirculaireConstraintTest {

    private static final SolverConfig CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    private final SolutionManager<TimetableSolution, HardMediumSoftScore> scorer =
            SolutionManager.create(SolverFactory.create(CONFIG));

    // ── décor ─────────────────────────────────────────────────────────────────

    private final TimeSlotRef lun1 = slot(1L, DayOfWeek.MONDAY, 1, "08:00");
    private final TimeSlotRef lun2 = slot(2L, DayOfWeek.MONDAY, 2, "09:00");
    private final TimeSlotRef mar1 = slot(3L, DayOfWeek.TUESDAY, 1, "08:00");
    private final TimeSlotRef mer1 = slot(4L, DayOfWeek.WEDNESDAY, 1, "08:00");
    private final RoomRef salle = RoomRef.builder()
            .id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build();
    /** Les deux moitiés d'une classe dédoublée occupent deux salles : le
     *  professeur circule entre elles, et {@code roomConflict} l'exige. */
    private final RoomRef salleBis = RoomRef.builder()
            .id(2L).code("A2").type(RoomType.NORMALE).capacity(30).build();
    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Prof").maxHoursPerDay(6).build();

    // ══════════════════════════════════════════════════════════════════════════
    // RESPECT_OFFICIAL_SUBJECT_HOURS — § T.1
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Volume hebdomadaire officiel (§ T.1)")
    class VolumeOfficiel {

        /** 4 créneaux = 2 h officielles pour la matière. */
        private static final int DEUX_HEURES = 4;

        @Test
        @DisplayName("Le volume placé qui rejoint le volume officiel ne coûte rien")
        void volumeExact() {
            Lesson a = cours(1L, "7A", "INFO", lun1, 2, DEUX_HEURES);
            Lesson b = cours(2L, "7A", "INFO", lun2, 2, DEUX_HEURES);

            assertThat(scoreAvec(param(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS), a, b)
                    .hardScore()).isZero();
        }

        @Test
        @DisplayName("Un volume amputé est une violation dure — le cas des séances en groupes")
        void volumeAmpute() {
            // Exactement le défaut corrigé à l'étape B : (2) lu « 2 groupes »
            // donnait une séance d'une heure au lieu de deux. Le planning était
            // faisable, et l'élève perdait la moitié de son informatique.
            Lesson seule = cours(1L, "7A", "INFO", lun1, 2, DEUX_HEURES);

            assertThat(scoreAvec(param(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS), seule)
                    .hardScore())
                    .as("2 créneaux placés contre 4 attendus")
                    .isEqualTo(-2);
        }

        @Test
        @DisplayName("Un volume en excès est une violation dure aussi")
        void volumeExcedentaire() {
            Lesson a = cours(1L, "7A", "INFO", lun1, 2, DEUX_HEURES);
            Lesson b = cours(2L, "7A", "INFO", lun2, 2, DEUX_HEURES);
            Lesson c = cours(3L, "7A", "INFO", mar1, 2, DEUX_HEURES);

            assertThat(scoreAvec(param(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS), a, b, c)
                    .hardScore())
                    .as("une séance en double coûte autant qu'une séance manquante")
                    .isEqualTo(-2);
        }

        @Test
        @DisplayName("Les deux moitiés d'un demi-groupe ne comptent qu'une fois")
        void demiGroupeCompteUneFois() {
            // L'élève n'assiste qu'à l'une des deux : compter les deux
            // doublerait le volume reçu et ferait échouer toute matière dédoublée.
            Lesson groupeA = demiGroupe(1L, "7A", "INFO", lun1, 4, DEUX_HEURES, 1, salle);
            Lesson groupeB = demiGroupe(2L, "7A", "INFO", lun1, 4, DEUX_HEURES, 2, salleBis);

            assertThat(scoreAvec(param(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS),
                    groupeA, groupeB).hardScore()).isZero();
        }

        @Test
        @DisplayName("Un volume officiel inconnu fait taire la contrainte")
        void volumeInconnu() {
            Lesson sansVolume = cours(1L, "7A", "INFO", lun1, 2, 0);

            assertThat(scoreAvec(param(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS), sansVolume)
                    .hardScore())
                    .as("une donnée manquante ne doit pas se transformer en violation")
                    .isZero();
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            Lesson seule = cours(1L, "7A", "INFO", lun1, 2, DEUX_HEURES);
            assertThat(scoreAvec(null, seule).hardScore()).isZero();
        }

        @Test
        @DisplayName("Deux matières d'une même classe sont évaluées séparément")
        void matieresIndependantes() {
            Lesson info = cours(1L, "7A", "INFO", lun1, 2, 2);   // conforme : 2 = 2
            Lesson math = cours(2L, "7A", "MATH", lun2, 2, 4);   // amputée : 2 < 4

            assertThat(scoreAvec(param(ConstraintCodes.RESPECT_OFFICIAL_SUBJECT_HOURS), info, math)
                    .hardScore()).isEqualTo(-2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHYSICAL_EDUCATION_THREE_SESSIONS — § III.2.b
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Découpage de l'éducation physique (§ III.2.b)")
    class DecoupageEps {

        @Test
        @DisplayName("Trois séances : première forme autorisée")
        void troisSeances() {
            assertThat(scoreEps(sport(1L, lun1, 2), sport(2L, mar1, 2), sport(3L, mer1, 2))
                    .hardScore()).isZero();
        }

        @Test
        @DisplayName("Deux séances de 2 h et 1 h : seconde forme autorisée")
        void deuxSeancesDeuxPlusUne() {
            assertThat(scoreEps(sport(1L, lun1, 4), sport(2L, mar1, 2)).hardScore()).isZero();
            assertThat(scoreEps(sport(1L, lun1, 2), sport(2L, mar1, 4)).hardScore())
                    .as("l'ordre des deux séances est indifférent").isZero();
        }

        @Test
        @DisplayName("Deux séances d'une heure : non conforme")
        void deuxSeancesEgales() {
            assertThat(scoreEps(sport(1L, lun1, 2), sport(2L, mar1, 2)).hardScore())
                    .as("c'est le cas produit par le job 11864 sur 18 classes")
                    .isNegative();
        }

        @Test
        @DisplayName("Une seule séance de trois heures : le volume est bon, la forme non")
        void seanceUnique() {
            assertThat(scoreEps(sport(1L, lun1, 6)).hardScore())
                    .as("la circulaire n'autorise pas ce découpage, "
                            + "même si le total hebdomadaire tombe juste")
                    .isNegative();
        }

        @Test
        @DisplayName("Aucune séance d'EPS : la contrainte se tait")
        void aucuneSeance() {
            // Une classe sans EPS relève du volume officiel, pas du découpage :
            // laisser les deux contraintes se prononcer produirait deux
            // violations pour une seule cause.
            assertThat(scoreEps(cours(9L, "7A", "MATH", lun1, 2, 0)).hardScore()).isZero();
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            assertThat(scoreAvec(null, sport(1L, lun1, 2), sport(2L, mar1, 2)).hardScore())
                    .isZero();
        }
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    private HardMediumSoftScore scoreEps(Lesson... lessons) {
        return scoreAvec(new ActiveConstraintParam(
                ConstraintCodes.PHYSICAL_EDUCATION_THREE_SESSIONS, 3, 1000), lessons);
    }

    private static ActiveConstraintParam param(String code) {
        return new ActiveConstraintParam(code, 0, 1000);
    }

    private HardMediumSoftScore scoreAvec(ActiveConstraintParam param, Lesson... lessons) {
        return scorer.update(TimetableSolution.builder()
                .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                .timeSlots(List.of(lun1, lun2, mar1, mer1))
                .teachers(List.of(prof))
                .rooms(List.of(salle, salleBis))
                .lessons(List.of(lessons))
                .activeConstraintParams(param == null ? List.of() : List.of(param))
                .build());
    }

    private Lesson cours(Long id, String classe, String matiere,
                          TimeSlotRef creneau, int duree, int volumeOfficiel) {
        return base(id, classe, matiere, creneau, duree, volumeOfficiel)
                .sessionType(SessionType.COURS).groupIndex(0).build();
    }

    private Lesson demiGroupe(Long id, String classe, String matiere, TimeSlotRef creneau,
                               int duree, int volumeOfficiel, int index, RoomRef ou) {
        return base(id, classe, matiere, creneau, duree, volumeOfficiel)
                .sessionType(SessionType.TP).groupIndex(index).pairedLessonId(1L)
                .room(ou).build();
    }

    /** Séance d'EPS ; son volume officiel est neutralisé pour isoler le découpage. */
    private Lesson sport(Long id, TimeSlotRef creneau, int duree) {
        return base(id, "7A", "SPORT", creneau, duree, 0)
                .sessionType(SessionType.SPORT).groupIndex(0).build();
    }

    private Lesson.LessonBuilder base(Long id, String classe, String matiere,
                                       TimeSlotRef creneau, int duree, int volumeOfficiel) {
        return Lesson.builder()
                .id(id).studentClassName(classe).subjectCode(matiere)
                .teachingAssignmentId(id).classStudentCount(30)
                .durationSlots(duree).officialWeeklySlots(volumeOfficiel)
                .timeSlot(creneau).teacher(prof).room(salle);
    }

    private static TimeSlotRef slot(Long id, DayOfWeek jour, int ordre, String debut) {
        return TimeSlotRef.builder()
                .id(id).day(jour).orderIndex(ordre)
                .startTime(LocalTime.parse(debut)).endTime(LocalTime.parse(debut).plusHours(1))
                .active(true).period(DayPeriod.MORNING).maxDurationSlots(8)
                .build();
    }
}
