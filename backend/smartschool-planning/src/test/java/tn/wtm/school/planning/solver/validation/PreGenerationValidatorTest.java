package tn.wtm.school.planning.solver.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.ExpectedCourse;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contrôle qui précède la génération — étape J du plan de conformité.
 *
 * <p>Chaque cas décrit une donnée qui condamnait la génération, et la façon dont
 * on l'apprenait avant : après trois minutes de calcul pour les unes, jamais
 * pour les autres. Une matière déclarée sans enseignant n'engendre aucune
 * séance ; une validation qui lit les séances ne peut rien dire de celles qui
 * n'existent pas.
 *
 * <p><b>Ce que ces tests vérifient aussi, en creux</b> : le validateur ne
 * <em>prédit</em> rien. Chaque constat est un dénombrement, jamais une
 * conjecture — et un problème qui passe tous les contrôles peut parfaitement
 * rester infaisable.
 */
class PreGenerationValidatorTest {

    private final PreGenerationValidator validateur = new PreGenerationValidator();

    private static final int UNE_HEURE   = 2;
    private static final int DEUX_HEURES = 4;

    // ══════════════════════════════════════════════════════════════════════════
    // Le programme attendu face aux séances engendrées
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Programme et séances")
    class Programme {

        @Test
        @DisplayName("Un programme entièrement servi laisse partir la génération")
        void programmeServi() {
            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "MATH", DEUX_HEURES)),
                    cours(1L, "7A", "MATH", DEUX_HEURES)));

            assertThat(rapport.estConforme()).isTrue();
        }

        @Test
        @DisplayName("Une matière déclarée que personne n'enseigne est bloquante")
        void matiereSansEnseignant() {
            // Le cas emblématique : on ajoute une matière au niveau, on oublie
            // l'affectation. Aucune séance n'est engendrée et, jusqu'ici,
            // personne ne le disait — ni avant, ni après.
            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "MATH", DEUX_HEURES),
                            attendu("7A", "MUS", UNE_HEURE)),
                    cours(1L, "7A", "MATH", DEUX_HEURES)));

            assertThat(rapport.bloquants()).singleElement().satisfies(c -> {
                assertThat(c.code()).isEqualTo(PreGenerationValidator.MATIERE_SANS_ENSEIGNANT);
                assertThat(c.scope()).contains("MUS");
                assertThat(c.message())
                        .as("le message doit dire comment déclarer qu'une matière n'est pas enseignée")
                        .contains("zéro");
            });
        }

        @Test
        @DisplayName("Un pattern qui ne dit plus le même volume que le niveau est bloquant")
        void volumeIncoherent() {
            // Les séances viennent du pattern, le volume attendu de
            // niveaux_matieres.heures_semaine. Modifier l'un sans l'autre donne
            // ceci — et le job aurait été refusé à la fin, après avoir cherché.
            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "MATH", 10)),
                    cours(1L, "7A", "MATH", DEUX_HEURES)));

            assertThat(rapport.bloquants()).singleElement().satisfies(c -> {
                assertThat(c.code()).isEqualTo(PreGenerationValidator.VOLUME_INCOHERENT);
                assertThat(c.message()).contains("5 h").contains("2 h");
            });
        }

        @Test
        @DisplayName("Les deux moitiés d'un demi-groupe ne comptent qu'une fois")
        void demiGroupeCompteUneFois() {
            Lesson a = base(1L, "7A", "INFO", DEUX_HEURES).groupIndex(1).pairedLessonId(9L).build();
            Lesson b = base(2L, "7A", "INFO", DEUX_HEURES).groupIndex(2).pairedLessonId(9L).build();

            assertThat(validateur.valider(probleme(
                    List.of(attendu("7A", "INFO", DEUX_HEURES)), a, b)).estConforme())
                    .isTrue();
        }

        @Test
        @DisplayName("Une séance hors programme est signalée sans bloquer")
        void seanceHorsProgramme() {
            // Une affectation porte sur une matière que le niveau ne déclare
            // pas : la séance sera placée, mais son volume est invérifiable.
            // Le dire, et laisser partir — refuser serait disproportionné.
            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "MATH", DEUX_HEURES)),
                    cours(1L, "7A", "MATH", DEUX_HEURES),
                    cours(2L, "7A", "LATIN", UNE_HEURE)));

            assertThat(rapport.estConforme()).isTrue();
            assertThat(rapport.avertissements()).singleElement()
                    .satisfies(c -> assertThat(c.code())
                            .isEqualTo(PreGenerationValidator.SEANCE_HORS_PROGRAMME));
        }

        @Test
        @DisplayName("Un programme vide est signalé, pas jugé")
        void programmeInconnu() {
            ValidationReport rapport = validateur.valider(probleme(
                    List.of(), cours(1L, "7A", "MATH", DEUX_HEURES)));

            assertThat(rapport.estConforme())
                    .as("une donnée manquante ne justifie pas de refuser la génération")
                    .isTrue();
            assertThat(rapport.avertissements()).singleElement()
                    .satisfies(c -> assertThat(c.code())
                            .isEqualTo(PreGenerationValidator.PROGRAMME_INCONNU));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Les salles qui n'existent pas
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Salles")
    class Salles {

        @Test
        @DisplayName("Un TP qui exige un type de salle que l'établissement n'a pas")
        void typeDeSalleAbsent() {
            Lesson tp = base(1L, "7A", "INFO", DEUX_HEURES)
                    .sessionType(SessionType.TP)
                    .requiresSpecialRoom(true).requiredRoomType(RoomType.LABINFORMATIQUE)
                    .build();

            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "INFO", DEUX_HEURES)), tp));

            assertThat(rapport.bloquants()).singleElement()
                    .satisfies(c -> assertThat(c.code())
                            .isEqualTo(PreGenerationValidator.TYPE_DE_SALLE_ABSENT));
        }

        @Test
        @DisplayName("Le même TP ne dit rien quand le laboratoire existe")
        void typeDeSallePresent() {
            Lesson tp = base(1L, "7A", "INFO", DEUX_HEURES)
                    .sessionType(SessionType.TP)
                    .requiresSpecialRoom(true).requiredRoomType(RoomType.LABINFORMATIQUE)
                    .build();

            TimetableSolution probleme = probleme(List.of(attendu("7A", "INFO", DEUX_HEURES)), tp);
            probleme.setRooms(List.of(salleOrdinaire,
                    RoomRef.builder().id(2L).code("LAB")
                            .type(RoomType.LABINFORMATIQUE).capacity(30).build()));

            assertThat(validateur.valider(probleme).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Une classe qu'aucune salle ne peut contenir")
        void aucuneSalleAssezGrande() {
            Lesson trenteCinq = base(1L, "7A", "MATH", DEUX_HEURES)
                    .classStudentCount(35).build();

            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "MATH", DEUX_HEURES)), trenteCinq));

            assertThat(rapport.bloquants()).singleElement().satisfies(c -> {
                assertThat(c.code()).isEqualTo(PreGenerationValidator.AUCUNE_SALLE_ASSEZ_GRANDE);
                assertThat(c.message()).contains("30");
            });
        }

        @Test
        @DisplayName("Un demi-groupe se contente de la moitié des places")
        void demiGroupeMoitiéDesPlaces() {
            Lesson a = base(1L, "7A", "INFO", DEUX_HEURES)
                    .classStudentCount(50).groupIndex(1).pairedLessonId(9L).build();
            Lesson b = base(2L, "7A", "INFO", DEUX_HEURES)
                    .classStudentCount(50).groupIndex(2).pairedLessonId(9L).build();

            assertThat(validateur.valider(probleme(
                    List.of(attendu("7A", "INFO", DEUX_HEURES)), a, b)).estConforme())
                    .isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ce qui ne tient pas dans une semaine
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Capacités hebdomadaires")
    class Capacites {

        @Test
        @DisplayName("Un service qui dépasse ce qu'une semaine autorise")
        void serviceImpossible() {
            // Le cas emblématique, en miniature : un enseignant présent un seul
            // jour, plafonné à deux heures, à qui on demande six heures. Trois
            // professeurs d'EPS pour vingt-trois classes, c'est le même calcul.
            TeacherRef surcharge = TeacherRef.builder()
                    .id(9L).code("T9").name("M. Surchargé").maxHoursPerDay(2)
                    .unavailableDays(Set.of(DayOfWeek.TUESDAY)).build();

            // Trois classes, deux heures chacune : aucune classe n'est chargée,
            // c'est bien le service de l'enseignant qui ne tient pas.
            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "SPORT", DEUX_HEURES),
                            attendu("7B", "SPORT", DEUX_HEURES),
                            attendu("7C", "SPORT", DEUX_HEURES)),
                    base(1L, "7A", "SPORT", DEUX_HEURES).teacher(surcharge).build(),
                    base(2L, "7B", "SPORT", DEUX_HEURES).teacher(surcharge).build(),
                    base(3L, "7C", "SPORT", DEUX_HEURES).teacher(surcharge).build()));

            assertThat(rapport.bloquants()).singleElement().satisfies(c -> {
                assertThat(c.code()).isEqualTo(PreGenerationValidator.SERVICE_IMPOSSIBLE);
                assertThat(c.message()).contains("6 h").contains("2 h");
            });
        }

        @Test
        @DisplayName("Le même service tient dès que l'enseignant est là deux jours")
        void serviceTenable() {
            // Le même service — six heures — chez un enseignant présent les deux
            // jours et plafonné à trois heures : il tient exactement.
            TeacherRef present = TeacherRef.builder()
                    .id(9L).code("T9").name("Mme Présente").maxHoursPerDay(3).build();

            assertThat(validateur.valider(probleme(
                    List.of(attendu("7A", "SPORT", DEUX_HEURES),
                            attendu("7B", "SPORT", DEUX_HEURES),
                            attendu("7C", "SPORT", DEUX_HEURES)),
                    base(1L, "7A", "SPORT", DEUX_HEURES).teacher(present).build(),
                    base(2L, "7B", "SPORT", DEUX_HEURES).teacher(present).build(),
                    base(3L, "7C", "SPORT", DEUX_HEURES).teacher(present).build()))
                    .estConforme())
                    .isTrue();
        }

        @Test
        @DisplayName("Une classe dont le programme dépasse les créneaux de la semaine")
        void semaineTropCourte() {
            // Quatre heures de créneaux ouverts, cinq heures de cours à placer.
            List<Lesson> seances = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                seances.add(base((long) i, "7A", "MATH", UNE_HEURE).build());
            }

            ValidationReport rapport = validateur.valider(probleme(
                    List.of(attendu("7A", "MATH", 5 * UNE_HEURE)), seances.toArray(new Lesson[0])));

            assertThat(rapport.bloquants()).singleElement().satisfies(c -> {
                assertThat(c.code()).isEqualTo(PreGenerationValidator.SEMAINE_TROP_COURTE);
                assertThat(c.message()).contains("5 h").contains("4 h");
            });
        }
    }

    @Nested
    @DisplayName("Frontière du contrôle")
    class Frontiere {

        @Test
        @DisplayName("Un problème nul ne fait rien tomber")
        void problemeNul() {
            assertThat(validateur.valider(null).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Passer le contrôle n'est pas une promesse de faisabilité")
        void aucunePromesse() {
            // Deux classes, un seul enseignant, un seul créneau utile : le
            // contrôle ne voit aucune impossibilité — le service tient, les
            // salles existent, les volumes concordent — et pourtant le solveur
            // ne placera jamais les deux séances au même créneau. C'est assumé :
            // ce validateur écarte l'évident, il ne cherche pas.
            Lesson a = base(1L, "7A", "MATH", UNE_HEURE).build();
            Lesson b = base(2L, "7B", "MATH", UNE_HEURE).build();

            assertThat(validateur.valider(probleme(
                    List.of(attendu("7A", "MATH", UNE_HEURE), attendu("7B", "MATH", UNE_HEURE)),
                    a, b)).estConforme())
                    .isTrue();
        }
    }

    // ── décor ─────────────────────────────────────────────────────────────────

    private final RoomRef salleOrdinaire = RoomRef.builder()
            .id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build();

    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Mme Ben Salah").maxHoursPerDay(6).build();

    /** Huit créneaux ouverts sur deux jours — une semaine volontairement courte. */
    private final List<TimeSlotRef> creneaux = List.of(
            creneau(1L, DayOfWeek.MONDAY, 1, "08:00"),
            creneau(2L, DayOfWeek.MONDAY, 2, "08:30"),
            creneau(3L, DayOfWeek.MONDAY, 3, "09:00"),
            creneau(4L, DayOfWeek.MONDAY, 4, "09:30"),
            creneau(5L, DayOfWeek.TUESDAY, 1, "08:00"),
            creneau(6L, DayOfWeek.TUESDAY, 2, "08:30"),
            creneau(7L, DayOfWeek.TUESDAY, 3, "09:00"),
            creneau(8L, DayOfWeek.TUESDAY, 4, "09:30"));

    private TimetableSolution probleme(List<ExpectedCourse> programme, Lesson... seances) {
        return TimetableSolution.builder()
                .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                .timeSlots(creneaux)
                .rooms(List.of(salleOrdinaire))
                .teachers(List.of(prof))
                .lessons(List.of(seances))
                .expectedCurriculum(programme)
                .build();
    }

    private static ExpectedCourse attendu(String classe, String matiere, int creneaux) {
        return new ExpectedCourse(classe, matiere, matiere, creneaux);
    }

    private Lesson cours(Long id, String classe, String matiere, int duree) {
        return base(id, classe, matiere, duree).build();
    }

    /**
     * Les séances n'ont ni créneau ni salle : c'est exactement l'état du
     * problème quand ce validateur le reçoit, avant que le solveur ait placé
     * quoi que ce soit.
     */
    private Lesson.LessonBuilder base(Long id, String classe, String matiere, int duree) {
        return Lesson.builder()
                .id(id).studentClassName(classe).subjectCode(matiere).subjectName(matiere)
                .teachingAssignmentId(id).classStudentCount(30)
                .sessionType(SessionType.COURS).groupIndex(0)
                .durationSlots(duree).officialWeeklySlots(0)
                .teacher(prof);
    }

    private static TimeSlotRef creneau(Long id, DayOfWeek jour, int rang, String debut) {
        LocalTime heure = LocalTime.parse(debut);
        return TimeSlotRef.builder()
                .id(id).day(jour).orderIndex(rang).period(DayPeriod.MORNING)
                .startTime(heure).endTime(heure.plusMinutes(30))
                .active(true).maxDurationSlots(8)
                .build();
    }
}
