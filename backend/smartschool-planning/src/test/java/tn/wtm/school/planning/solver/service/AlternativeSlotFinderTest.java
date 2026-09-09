package tn.wtm.school.planning.solver.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.enums.WeekParity;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un créneau proposé doit être <b>réellement</b> libre.
 *
 * <p>C'est toute la raison d'être de {@link AlternativeSlotFinder} : un modèle
 * de langage sait rédiger « déplacez ce cours au jeudi 10 h » sans avoir le
 * moindre moyen de vérifier que le jeudi 10 h est libre. Une suggestion fausse
 * coûte plus cher au directeur que pas de suggestion du tout, puisqu'il l'aura
 * essayée avant de constater qu'elle ne tient pas.
 *
 * <p>Ces tests vérifient donc, règle dure par règle dure, que le chercheur
 * écarte ce que le solveur pénaliserait — <b>et</b>, tout aussi important,
 * qu'il n'écarte pas ce que le solveur accepte : recopier une contrainte en
 * oubliant son exception ferait taire des propositions parfaitement valides,
 * un défaut silencieux qu'aucun écran ne trahirait.
 */
class AlternativeSlotFinderTest {

    private static final String CLASSE = "7A";

    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Mr. Dupont").maxHoursPerDay(6).build();

    private final RoomRef salleA  = salle(1L, "A1", RoomType.NORMALE, 35);
    private final RoomRef salleB  = salle(2L, "B2", RoomType.NORMALE, 35);
    private final RoomRef placard = salle(3L, "P3", RoomType.NORMALE, 10);
    private final RoomRef gymnase = salle(4L, "G4", RoomType.SALLESPORT, 60);

    private final TimeSlotRef lundi8h  = creneau(1L, DayOfWeek.MONDAY,  LocalTime.of(8, 0), 1);
    private final TimeSlotRef lundi10h = creneau(2L, DayOfWeek.MONDAY,  LocalTime.of(10, 0), 5);
    private final TimeSlotRef mardi8h  = creneau(3L, DayOfWeek.TUESDAY, LocalTime.of(8, 0), 1);

    // ══════════════════════════════════════════════════════════════════════════
    // Le cas nominal, et ce qu'il garantit
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ce qui est proposé")
    class CeQuiEstPropose {

        @Test
        @DisplayName("Une séance seule au monde reçoit le premier créneau libre de la semaine")
        void creneauLibreEstPropose() {
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);

            AlternativeSlotFinder.Relocation proposition = chercher(seance, solution(List.of(seance)));

            assertThat(proposition.slot()).isEqualTo(lundi10h);
        }

        @Test
        @DisplayName("Le créneau d'origine n'est jamais proposé : ce ne serait pas un déplacement")
        void creneauActuelEcarte() {
            // Le seul créneau de la semaine est celui qu'elle occupe déjà.
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);
            TimetableSolution solution = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h)).teachers(List.of(prof))
                    .rooms(List.of(salleA, salleB)).lessons(List.of(seance))
                    .build();

            assertThat(new AlternativeSlotFinder(solution).findFor(seance)).isEmpty();
        }

        @Test
        @DisplayName("Le plus tôt dans la semaine l'emporte, pour que la proposition ne change pas d'un affichage à l'autre")
        void propositionDeterministe() {
            Lesson seance = cours(1L, CLASSE, salleA, mardi8h);
            AlternativeSlotFinder finder = new AlternativeSlotFinder(solution(List.of(seance)));

            assertThat(finder.findFor(seance)).get()
                    .extracting(AlternativeSlotFinder.Relocation::slot)
                    .isEqualTo(lundi8h);
            // Deux fois de suite : le directeur qui rouvre l'écran relit la même phrase.
            assertThat(finder.findFor(seance)).get()
                    .extracting(AlternativeSlotFinder.Relocation::slot)
                    .isEqualTo(lundi8h);
        }

        @Test
        @DisplayName("La salle actuelle est gardée quand elle est libre à l'arrivée")
        void salleActuellePrefereeSiLibre() {
            // salleB n'est pas la première de la liste : sans préférence explicite,
            // c'est salleA qui sortirait, et la proposition bougerait deux choses
            // là où une suffit.
            Lesson seance = cours(1L, CLASSE, salleB, lundi8h);

            assertThat(chercher(seance, solution(List.of(seance))).room()).isEqualTo(salleB);
        }

        @Test
        @DisplayName("Une salle est proposée quand la salle actuelle est prise à l'arrivée")
        void autreSalleQuandLActuelleEstOccupee() {
            Lesson seance   = cours(1L, CLASSE, salleA, lundi8h);
            Lesson occupant = cours(2L, "7B", salleA, lundi10h);
            occupant.setTeacher(autreProf());

            assertThat(chercher(seance, solution(List.of(seance, occupant))).room()).isEqualTo(salleB);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Les règles dures rejouées — ce que le chercheur doit écarter
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ce qui est écarté")
    class CeQuiEstEcarte {

        @Test
        @DisplayName("TEACHER_CONFLICT : l'enseignant déjà en cours ailleurs ferme le créneau")
        void enseignantOccupe() {
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);
            Lesson ailleurs = cours(2L, "7B", salleB, lundi10h);   // même prof

            assertThat(chercher(seance, solution(List.of(seance, ailleurs))).slot()).isEqualTo(mardi8h);
        }

        @Test
        @DisplayName("CLASS_CONFLICT : la classe déjà en cours ferme le créneau")
        void classeOccupee() {
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);
            Lesson ailleurs = cours(2L, CLASSE, salleB, lundi10h);
            ailleurs.setTeacher(autreProf());

            assertThat(chercher(seance, solution(List.of(seance, ailleurs))).slot()).isEqualTo(mardi8h);
        }

        @Test
        @DisplayName("TEACHER_AVAILABILITY : un jour déclaré indisponible n'est pas un créneau libre")
        void jourIndisponible() {
            TeacherRef indisponibleLundi = TeacherRef.builder()
                    .id(1L).code("T1").name("Mr. Dupont").maxHoursPerDay(6)
                    .unavailableDays(Set.of(DayOfWeek.MONDAY))
                    .build();
            Lesson seance = cours(1L, CLASSE, salleA, mardi8h);
            seance.setTeacher(indisponibleLundi);

            assertThat(new AlternativeSlotFinder(solution(List.of(seance))).findFor(seance)).isEmpty();
        }

        @Test
        @DisplayName("LESSON_EXCEEDS_WORKING_BLOCK : une séance de 2 h ne tient pas dans une fin de bloc")
        void seanceTropLonguePourLeBloc() {
            // Quatre demi-heures demandées, deux disponibles avant la pause.
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);
            seance.setDurationSlots(4);

            assertThat(new AlternativeSlotFinder(solution(List.of(seance))).findFor(seance)).isEmpty();
        }

        @Test
        @DisplayName("Le créneau de pause n'est pas un créneau")
        void creneauDePauseJamaisPropose() {
            TimeSlotRef pause = TimeSlotRef.builder()
                    .id(9L).day(DayOfWeek.MONDAY).orderIndex(9)
                    .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0))
                    .maxDurationSlots(2).active(false).build();
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);
            TimetableSolution solution = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h, pause)).teachers(List.of(prof))
                    .rooms(List.of(salleA, salleB)).lessons(List.of(seance))
                    .build();

            assertThat(new AlternativeSlotFinder(solution).findFor(seance)).isEmpty();
        }

        @Test
        @DisplayName("ROOM_CONFLICT : toutes les salles prises, rien n'est proposé plutôt qu'une salle occupée")
        void toutesLesSallesOccupees() {
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);
            Lesson occupantA = cours(2L, "7B", salleA, lundi10h);
            Lesson occupantB = cours(3L, "7C", salleB, lundi10h);
            occupantA.setTeacher(autreProf());
            occupantB.setTeacher(autreProf());

            TimetableSolution solution = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h, lundi10h)).teachers(List.of(prof))
                    .rooms(List.of(salleA, salleB))
                    .lessons(List.of(seance, occupantA, occupantB))
                    .build();

            assertThat(new AlternativeSlotFinder(solution).findFor(seance)).isEmpty();
        }

        @Test
        @DisplayName("ROOM_CAPACITY : une salle trop petite n'est pas une salle libre")
        void salleTropPetiteEcartee() {
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);   // 30 élèves
            TimetableSolution solution = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h, lundi10h)).teachers(List.of(prof))
                    .rooms(List.of(placard))                      // 10 places
                    .lessons(List.of(seance))
                    .build();

            assertThat(new AlternativeSlotFinder(solution).findFor(seance)).isEmpty();
        }

        @Test
        @DisplayName("NORMAL_COURSE_NOT_IN_SPECIAL_ROOM : un cours ordinaire ne prend pas le gymnase faute de mieux")
        void coursOrdinaireJamaisEnSalleSpecialisee() {
            Lesson seance = cours(1L, CLASSE, salleA, lundi8h);
            TimetableSolution solution = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h, lundi10h)).teachers(List.of(prof))
                    .rooms(List.of(gymnase))
                    .lessons(List.of(seance))
                    .build();

            assertThat(new AlternativeSlotFinder(solution).findFor(seance)).isEmpty();
        }

        @Test
        @DisplayName("SPECIAL_ROOM_REQUIRED : l'EPS n'est pas replacée dans une salle de cours")
        void epsExigeLeGymnase() {
            Lesson eps = cours(1L, CLASSE, gymnase, lundi8h);
            eps.setRequiresSpecialRoom(true);
            eps.setRequiredRoomType(RoomType.SALLESPORT);

            TimetableSolution avecGymnase = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h, lundi10h)).teachers(List.of(prof))
                    .rooms(List.of(salleA, gymnase)).lessons(List.of(eps))
                    .build();
            assertThat(chercher(eps, avecGymnase).room()).isEqualTo(gymnase);

            TimetableSolution sansGymnase = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h, lundi10h)).teachers(List.of(prof))
                    .rooms(List.of(salleA, salleB)).lessons(List.of(eps))
                    .build();
            assertThat(new AlternativeSlotFinder(sansGymnase).findFor(eps)).isEmpty();
        }

        @Test
        @DisplayName("PAIRED_DEMI_GROUP_SAME_SLOT : un demi-groupe apparié se tait plutôt que d'abandonner son jumeau")
        void demiGroupeApparieNeProposeRien() {
            Lesson groupeA = cours(1L, CLASSE, salleA, lundi8h);
            groupeA.setGroupIndex(1);
            groupeA.setPairedLessonId(2L);

            assertThat(new AlternativeSlotFinder(solution(List.of(groupeA))).findFor(groupeA)).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Les exceptions des contraintes — ce qu'il ne faut PAS écarter
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Les exceptions, recopiées à l'identique")
    class LesExceptions {

        @Test
        @DisplayName("Deux demi-groupes distincts cohabitent : la classe n'est pas « occupée »")
        void demiGroupesDistinctsCohabitent() {
            // A en TP pendant que B est en cours : CLASS_CONFLICT l'autorise
            // explicitement. Oublier l'exception fermerait un créneau valide.
            Lesson groupeA = cours(1L, CLASSE, salleA, lundi8h);
            groupeA.setGroupIndex(1);
            Lesson groupeB = cours(2L, CLASSE, salleB, lundi10h);
            groupeB.setGroupIndex(2);
            groupeB.setTeacher(autreProf());

            assertThat(chercher(groupeA, solution(List.of(groupeA, groupeB))).slot()).isEqualTo(lundi10h);
        }

        @Test
        @DisplayName("Deux quinzaines opposées ne se heurtent pas, même créneau pour même enseignant")
        void quinzainesOpposeesNeSeHeurtentPas() {
            Lesson impaire = cours(1L, CLASSE, salleA, lundi8h);
            impaire.setWeekParity(WeekParity.ODD);
            Lesson paire = cours(2L, "7B", salleB, lundi10h);
            paire.setWeekParity(WeekParity.EVEN);   // même prof, semaine d'après

            assertThat(chercher(impaire, solution(List.of(impaire, paire))).slot()).isEqualTo(lundi10h);
        }

        @Test
        @DisplayName("Un demi-groupe tient dans une salle deux fois trop petite pour la classe entière")
        void demiGroupeDemandeLaMoitieDesPlaces() {
            // 30 élèves en classe entière, 15 en demi-groupe : le placard de
            // 10 places reste trop petit, mais une salle de 20 conviendrait.
            RoomRef salleDeVingt = salle(5L, "V5", RoomType.NORMALE, 20);
            Lesson demiGroupe = cours(1L, CLASSE, salleA, lundi8h);
            demiGroupe.setGroupIndex(1);

            TimetableSolution solution = TimetableSolution.builder()
                    .timeSlots(List.of(lundi8h, lundi10h)).teachers(List.of(prof))
                    .rooms(List.of(placard, salleDeVingt)).lessons(List.of(demiGroupe))
                    .build();

            assertThat(new AlternativeSlotFinder(solution).findFor(demiGroupe)).get()
                    .extracting(AlternativeSlotFinder.Relocation::room)
                    .isEqualTo(salleDeVingt);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Décor
    // ══════════════════════════════════════════════════════════════════════════

    private AlternativeSlotFinder.Relocation chercher(Lesson seance, TimetableSolution solution) {
        Optional<AlternativeSlotFinder.Relocation> proposition =
                new AlternativeSlotFinder(solution).findFor(seance);
        assertThat(proposition).as("le décor devait offrir un créneau et n'en offre aucun").isPresent();
        return proposition.orElseThrow();
    }

    /** Les trois créneaux de la semaine, tous les enseignants, toutes les salles. */
    private TimetableSolution solution(List<Lesson> lessons) {
        return TimetableSolution.builder()
                .timeSlots(List.of(lundi8h, lundi10h, mardi8h))
                .teachers(List.of(prof))
                .rooms(List.of(salleA, salleB))
                .lessons(lessons)
                .build();
    }

    /** Une heure de cours ordinaire : deux créneaux de trente minutes, classe entière. */
    private Lesson cours(Long id, String classe, RoomRef salle, TimeSlotRef creneau) {
        return Lesson.builder()
                .id(id)
                .subjectCode("MATH").subjectName("Mathématiques")
                .studentClassName(classe).studentClassLevel("7EME")
                .teachingAssignmentId(id)
                .sessionType(SessionType.COURS)
                .groupIndex(0).classStudentCount(30)
                .durationSlots(2)
                .teacher(prof).room(salle).timeSlot(creneau)
                .build();
    }

    /** Un autre enseignant, pour occuper une salle ou une classe sans créer de conflit de prof. */
    private TeacherRef autreProf() {
        return TeacherRef.builder().id(2L).code("T2").name("Mme Ben Ali").maxHoursPerDay(6).build();
    }

    private RoomRef salle(Long id, String code, RoomType type, int capacite) {
        return RoomRef.builder().id(id).code(code).type(type).capacity(capacite).build();
    }

    /** Un créneau d'une heure, dans un bloc qui en accepte une de plus. */
    private TimeSlotRef creneau(Long id, DayOfWeek jour, LocalTime debut, int ordre) {
        return TimeSlotRef.builder()
                .id(id).day(jour).orderIndex(ordre)
                .startTime(debut).endTime(debut.plusHours(1))
                .maxDurationSlots(2).active(true)
                .build();
    }
}
