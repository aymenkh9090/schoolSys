package tn.wtm.school.planning.solver.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.enums.WeekParity;
import tn.wtm.school.planning.solver.ref.ExpectedCourse;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La validation métier de l'étape E.
 *
 * <p>Chaque cas ci-dessous décrit un emploi du temps qu'un score Timefold
 * faisable pouvait laisser passer. Le point commun : soit la contrainte
 * correspondante était désactivable dans le profil, soit le solveur y était
 * structurellement aveugle — une séance jamais engendrée n'est dans aucun flux
 * de contraintes, donc ne coûte rien.
 */
class TimetableBusinessValidatorTest {

    private final TimetableBusinessValidator validateur = new TimetableBusinessValidator();

    // ── décor ─────────────────────────────────────────────────────────────────

    private static final int UNE_HEURE   = 2;
    private static final int DEUX_HEURES = 4;

    private final RoomRef salleA = salle(1L, "A1", RoomType.NORMALE, 30);
    private final RoomRef salleB = salle(2L, "A2", RoomType.NORMALE, 30);
    private final RoomRef placard = salle(3L, "P1", RoomType.NORMALE, 8);
    private final RoomRef gymnase = salle(4L, "GYM", RoomType.SALLESPORT, 60);

    private final TeacherRef prof  = enseignant(1L, "Mme Ben Salah");
    private final TeacherRef autre = enseignant(2L, "M. Trabelsi");

    private final TimeSlotRef lun8h  = creneau(1L, DayOfWeek.MONDAY, 1, "08:00");
    private final TimeSlotRef lun9h  = creneau(2L, DayOfWeek.MONDAY, 3, "09:00");
    private final TimeSlotRef mar8h  = creneau(3L, DayOfWeek.TUESDAY, 1, "08:00");
    /** {@code active=false} : c'est ainsi que la pause méridienne se reconnaît. */
    private final TimeSlotRef pause = TimeSlotRef.builder()
            .id(4L).day(DayOfWeek.MONDAY).orderIndex(9).period(DayPeriod.MORNING)
            .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(12, 30))
            .active(false).maxDurationSlots(4).build();

    // ══════════════════════════════════════════════════════════════════════════
    // Intégrité de la génération
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Intégrité de la génération")
    class Integrite {

        @Test
        @DisplayName("Une séance sans créneau est bloquante")
        void seanceSansCreneau() {
            Lesson orpheline = cours(1L, "7A", "MATH", null, UNE_HEURE, 0);

            ValidationReport rapport = validateur.valider(planning(orpheline));

            assertThat(rapport.estConforme()).isFalse();
            assertThat(codesBloquants(rapport))
                    .containsExactly(TimetableBusinessValidator.SEANCE_NON_PLACEE);
        }

        @Test
        @DisplayName("Une séance sans salle est bloquante, et le dit autrement")
        void seanceSansSalle() {
            Lesson sansSalle = base(1L, "7A", "MATH", lun8h, UNE_HEURE, 0).room(null).build();

            ValidationReport rapport = validateur.valider(planning(sansSalle));

            assertThat(rapport.bloquants()).singleElement()
                    .satisfies(c -> assertThat(c.message()).contains("salle"));
        }

        @Test
        @DisplayName("Deux séances au même identifiant : la même heure comptée deux fois")
        void seanceDupliquee() {
            // Le solveur ne peut pas voir ce défaut : il reçoit deux entités et
            // les place toutes les deux, sans jamais soupçonner qu'elles n'en
            // font qu'une.
            Lesson a = cours(7L, "7A", "MATH", lun8h, UNE_HEURE, 0);
            Lesson b = cours(7L, "7A", "MATH", mar8h, UNE_HEURE, 0);

            assertThat(codesBloquants(validateur.valider(planning(a, b))))
                    .contains(TimetableBusinessValidator.SEANCE_DUPLIQUEE);
        }

        @Test
        @DisplayName("Une séance sans enseignant est bloquante")
        void enseignantManquant() {
            Lesson sansProf = base(1L, "7A", "MATH", lun8h, UNE_HEURE, 0).teacher(null).build();

            assertThat(codesBloquants(validateur.valider(planning(sansProf))))
                    .contains(TimetableBusinessValidator.ENSEIGNANT_MANQUANT);
        }

        @Test
        @DisplayName("Une moitié de classe dédoublée sans sa jumelle est bloquante")
        void demiGroupeOrphelin() {
            Lesson moitie = demiGroupe(1L, "7A", "INFO", lun8h, 1, salleA);

            assertThat(codesBloquants(validateur.valider(planning(moitie))))
                    .contains(TimetableBusinessValidator.DEMI_GROUPE_DESAPPARIE);
        }

        @Test
        @DisplayName("Deux moitiés placées à des heures différentes : la classe est coupée en deux")
        void demiGroupeDesynchronise() {
            Lesson a = demiGroupe(1L, "7A", "INFO", lun8h, 1, salleA);
            Lesson b = demiGroupe(2L, "7A", "INFO", lun9h, 2, salleB);

            assertThat(codesBloquants(validateur.valider(planning(a, b))))
                    .contains(TimetableBusinessValidator.DEMI_GROUPE_DESAPPARIE);
        }

        @Test
        @DisplayName("Deux moitiés au même créneau, en deux salles : c'est le fonctionnement normal")
        void demiGroupeRegulier() {
            Lesson a = demiGroupe(1L, "7A", "INFO", lun8h, 1, salleA);
            Lesson b = demiGroupe(2L, "7A", "INFO", lun8h, 2, salleB);

            assertThat(validateur.valider(planning(a, b)).estConforme()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Impossibilités physiques
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Impossibilités physiques")
    class Impossibilites {

        @Test
        @DisplayName("Un enseignant attendu dans deux classes au même moment")
        void conflitEnseignant() {
            Lesson a = cours(1L, "7A", "MATH", lun8h, UNE_HEURE, 0);
            Lesson b = base(2L, "7B", "MATH", lun8h, UNE_HEURE, 0).room(salleB).build();

            assertThat(codesBloquants(validateur.valider(planning(a, b))))
                    .contains(TimetableBusinessValidator.CONFLIT_ENSEIGNANT);
        }

        @Test
        @DisplayName("Une classe à deux cours en même temps")
        void conflitClasse() {
            Lesson a = cours(1L, "7A", "MATH", lun8h, UNE_HEURE, 0);
            Lesson b = base(2L, "7A", "FRAN", lun8h, UNE_HEURE, 0)
                    .teacher(autre).room(salleB).build();

            assertThat(codesBloquants(validateur.valider(planning(a, b))))
                    .contains(TimetableBusinessValidator.CONFLIT_CLASSE);
        }

        @Test
        @DisplayName("Deux classes dans la même salle")
        void conflitSalle() {
            Lesson a = cours(1L, "7A", "MATH", lun8h, UNE_HEURE, 0);
            Lesson b = base(2L, "7B", "FRAN", lun8h, UNE_HEURE, 0).teacher(autre).build();

            assertThat(codesBloquants(validateur.valider(planning(a, b))))
                    .contains(TimetableBusinessValidator.CONFLIT_SALLE);
        }

        @Test
        @DisplayName("Deux quinzaines opposées ne se heurtent pas")
        void quinzainesOpposees() {
            // Elles occupent le même créneau, la même salle et le même
            // enseignant, mais jamais la même semaine.
            Lesson impaire = base(1L, "7A", "PHYS", lun8h, UNE_HEURE, 0)
                    .weekParity(WeekParity.ODD).build();
            Lesson paire = base(2L, "7B", "SVT", lun8h, UNE_HEURE, 0)
                    .weekParity(WeekParity.EVEN).build();

            assertThat(validateur.valider(planning(impaire, paire)).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Un cours un jour où l'enseignant est indisponible")
        void enseignantIndisponible() {
            TeacherRef enFormation = TeacherRef.builder()
                    .id(3L).code("T3").name("M. Gharbi").maxHoursPerDay(6)
                    .unavailableDays(Set.of(DayOfWeek.MONDAY)).build();
            Lesson l = base(1L, "7A", "MATH", lun8h, UNE_HEURE, 0).teacher(enFormation).build();

            assertThat(codesBloquants(validateur.valider(planning(l))))
                    .contains(TimetableBusinessValidator.ENSEIGNANT_INDISPONIBLE);
        }

        @Test
        @DisplayName("Une classe de trente élèves dans une salle de huit places")
        void capaciteInsuffisante() {
            Lesson l = base(1L, "7A", "MATH", lun8h, UNE_HEURE, 0).room(placard).build();

            assertThat(codesBloquants(validateur.valider(planning(l))))
                    .contains(TimetableBusinessValidator.CAPACITE_SALLE);
        }

        @Test
        @DisplayName("Un demi-groupe se contente de la moitié des places")
        void capaciteDemiGroupe() {
            // Quinze élèves sur trente : la salle de huit reste trop petite, mais
            // c'est bien l'effectif du demi-groupe qui est comparé — sinon toute
            // séance dédoublée serait déclarée à l'étroit.
            Lesson a = demiGroupe(1L, "7A", "INFO", lun8h, 1, salleA);
            Lesson b = demiGroupe(2L, "7A", "INFO", lun8h, 2, salleB);

            assertThat(validateur.valider(planning(a, b)).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Un TP de sport dans une salle ordinaire")
        void salleSpecialiseeManquante() {
            Lesson eps = sport(1L, lun8h, salleA);

            assertThat(codesBloquants(validateur.valider(planning(eps))))
                    .contains(TimetableBusinessValidator.SALLE_INADAPTEE);
        }

        @Test
        @DisplayName("Le même TP au gymnase ne dit rien")
        void salleSpecialiseeCorrecte() {
            Lesson eps = sport(1L, lun8h, gymnase);

            assertThat(validateur.valider(planning(eps)).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Un cours placé sur la pause méridienne")
        void coursPendantLaPause() {
            Lesson l = cours(1L, "7A", "MATH", pause, UNE_HEURE, 0);

            assertThat(codesBloquants(validateur.valider(planning(l))))
                    .contains(TimetableBusinessValidator.COURS_PENDANT_LA_PAUSE);
        }

        @Test
        @DisplayName("Une séance qui déborde de sa demi-journée")
        void seanceDebordante() {
            TimeSlotRef finDeMatinee = TimeSlotRef.builder()
                    .id(9L).day(DayOfWeek.MONDAY).orderIndex(7).period(DayPeriod.MORNING)
                    .startTime(LocalTime.of(11, 30)).endTime(LocalTime.of(12, 0))
                    .active(true).maxDurationSlots(1).build();
            Lesson l = cours(1L, "7A", "MATH", finDeMatinee, DEUX_HEURES, 0);

            assertThat(codesBloquants(validateur.valider(planning(l))))
                    .contains(TimetableBusinessValidator.SEANCE_DEBORDANTE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Programme officiel — § T.1 à T.3
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Volume horaire officiel (§ T.1 à T.3)")
    class VolumeOfficiel {

        @Test
        @DisplayName("Le volume placé qui rejoint le programme ne dit rien")
        void volumeExact() {
            Lesson a = cours(1L, "7A", "HGEO", lun8h, UNE_HEURE, DEUX_HEURES);
            Lesson b = cours(2L, "7A", "HGEO", mar8h, UNE_HEURE, DEUX_HEURES);

            assertThat(validateur.valider(planning(a, b)).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Un volume amputé est bloquant, même si le solveur ne l'a pas vu")
        void volumeAmpute() {
            // Le cas que le profil pouvait masquer : décocher
            // RESPECT_OFFICIAL_SUBJECT_HOURS suffisait à obtenir un planning
            // « faisable » où l'élève perdait la moitié de son histoire-géo.
            Lesson seule = cours(1L, "7A", "HGEO", lun8h, UNE_HEURE, DEUX_HEURES);

            ValidationReport rapport = validateur.valider(planning(seule));

            assertThat(rapport.estConforme()).isFalse();
            assertThat(rapport.bloquants()).singleElement()
                    .satisfies(c -> {
                        assertThat(c.code()).isEqualTo(TimetableBusinessValidator.VOLUME_HORAIRE);
                        assertThat(c.message()).contains("2 h").contains("1 h");
                    });
        }

        @Test
        @DisplayName("Les deux moitiés d'un demi-groupe ne comptent qu'une fois")
        void demiGroupeCompteUneFois() {
            Lesson a = baseDemiGroupe(1L, lun8h, 1, salleA)
                    .durationSlots(DEUX_HEURES).officialWeeklySlots(DEUX_HEURES).build();
            Lesson b = baseDemiGroupe(2L, lun8h, 2, salleB)
                    .durationSlots(DEUX_HEURES).officialWeeklySlots(DEUX_HEURES).build();

            assertThat(validateur.valider(planning(a, b)).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Un volume officiel absent est signalé, pas jugé")
        void volumeInconnu() {
            Lesson l = cours(1L, "7A", "MATH", lun8h, UNE_HEURE, 0);

            ValidationReport rapport = validateur.valider(planning(l));

            assertThat(rapport.estConforme())
                    .as("une donnée manquante ne justifie pas de refuser l'emploi du temps")
                    .isTrue();
            assertThat(rapport.avertissements()).singleElement()
                    .satisfies(c -> assertThat(c.code())
                            .isEqualTo(TimetableBusinessValidator.VOLUME_NON_VERIFIABLE));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Programme attendu — ce qui manque, et non ce qui est faux
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Matière absente de l'emploi du temps")
    class ProgrammeAttendu {

        @Test
        @DisplayName("Une matière du programme sans aucune séance est bloquante")
        void matiereSansAucuneSeance() {
            // Le trou que l'étape E n'avait pas fermé : sans affectation
            // d'enseignant, aucune séance n'est engendrée, le couple
            // n'apparaît dans aucun groupe, et l'emploi du temps passait pour
            // conforme avec la matière purement absente.
            Lesson math = cours(1L, "7A", "MATH", lun8h, DEUX_HEURES, DEUX_HEURES);

            ValidationReport rapport = validateur.valider(planning(
                    List.of(attendu("7A", "MATH", DEUX_HEURES),
                            attendu("7A", "MUS", UNE_HEURE)),
                    math));

            assertThat(rapport.estConforme()).isFalse();
            assertThat(rapport.bloquants()).singleElement()
                    .satisfies(c -> {
                        assertThat(c.code()).isEqualTo(TimetableBusinessValidator.MATIERE_ABSENTE);
                        assertThat(c.scope()).contains("MUS");
                    });
        }

        @Test
        @DisplayName("Un programme entièrement servi ne dit rien")
        void programmeServi() {
            Lesson math = cours(1L, "7A", "MATH", lun8h, DEUX_HEURES, DEUX_HEURES);

            assertThat(validateur.valider(planning(
                    List.of(attendu("7A", "MATH", DEUX_HEURES)), math)).estConforme())
                    .isTrue();
        }

        @Test
        @DisplayName("Le programme répond là où la séance ne porte aucun volume")
        void programmeCombleLeVolumeManquant() {
            // Sans programme attendu, ce cas ressortait en avertissement
            // « volume non vérifiable ». Un contrôle qui peut conclure ne doit
            // pas se déclarer muet.
            Lesson ampute = cours(1L, "7A", "HGEO", lun8h, UNE_HEURE, 0);

            ValidationReport rapport = validateur.valider(planning(
                    List.of(attendu("7A", "HGEO", DEUX_HEURES)), ampute));

            assertThat(rapport.avertissements()).isEmpty();
            assertThat(codesBloquants(rapport))
                    .containsExactly(TimetableBusinessValidator.VOLUME_HORAIRE);
        }

        @Test
        @DisplayName("Sans programme attendu, la validation se comporte comme avant")
        void programmeInconnu() {
            Lesson math = cours(1L, "7A", "MATH", lun8h, DEUX_HEURES, DEUX_HEURES);

            assertThat(validateur.valider(planning(math)).estConforme()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ce que la validation ne juge pas
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Frontière de la validation")
    class Frontiere {

        @Test
        @DisplayName("Une règle pédagogique violée ne refuse pas l'emploi du temps")
        void reglePedagogiqueIgnoree() {
            // Deux séances d'EPS le même jour : le § III.2.b exige vingt-quatre
            // heures d'écart, et le solveur le pénalise en MEDIUM. Ce n'est pas
            // un motif de refus — la validation métier ne réimplémente pas les
            // préférences, elle vérifie ce qui doit être vrai.
            Lesson a = sport(1L, lun8h, gymnase);
            Lesson b = sport(2L, lun9h, gymnase);

            assertThat(validateur.valider(planning(a, b)).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Une solution vide est conforme")
        void solutionVide() {
            assertThat(validateur.valider(planning()).estConforme()).isTrue();
        }

        @Test
        @DisplayName("Une solution nulle ne fait pas tomber la persistance")
        void solutionNulle() {
            assertThat(validateur.valider(null).estConforme()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Le rapport lui-même
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rapport")
    class Rapport {

        @Test
        @DisplayName("Un rapport sans constat produit un résumé vide")
        void resumeVide() {
            assertThat(ValidationReport.conforme().resume()).isEmpty();
        }

        @Test
        @DisplayName("Le résumé regroupe par famille et compte")
        void resumeRegroupe() {
            ValidationReport rapport = validateur.valider(planning(
                    cours(1L, "7A", "MATH", null, UNE_HEURE, 0),
                    cours(2L, "7B", "FRAN", null, UNE_HEURE, 0)));

            assertThat(rapport.resume())
                    .contains("[bloquant]")
                    .contains(TimetableBusinessValidator.SEANCE_NON_PLACEE)
                    .contains("(2)");
        }

        @Test
        @DisplayName("Le résumé borne les exemples et annonce le reste")
        void resumeBorne() {
            List<Lesson> perdues = List.of(
                    cours(1L, "7A", "MATH", null, UNE_HEURE, 0),
                    cours(2L, "7B", "MATH", null, UNE_HEURE, 0),
                    cours(3L, "7C", "MATH", null, UNE_HEURE, 0),
                    cours(4L, "7D", "MATH", null, UNE_HEURE, 0),
                    cours(5L, "7E", "MATH", null, UNE_HEURE, 0));

            String resume = validateur.valider(planning(perdues.toArray(new Lesson[0]))).resume();

            assertThat(resume).contains("(5)").contains("et 2 autres");
        }
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    private static List<String> codesBloquants(ValidationReport rapport) {
        return rapport.bloquants().stream().map(ValidationFinding::code).distinct().toList();
    }

    private TimetableSolution planning(Lesson... seances) {
        return TimetableSolution.builder()
                .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                .lessons(List.of(seances))
                .build();
    }

    /** Le même planning, avec le programme que les classes doivent recevoir. */
    private TimetableSolution planning(List<ExpectedCourse> programme, Lesson... seances) {
        return TimetableSolution.builder()
                .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                .lessons(List.of(seances))
                .expectedCurriculum(programme)
                .build();
    }

    private static ExpectedCourse attendu(String classe, String matiere, int creneaux) {
        return new ExpectedCourse(classe, matiere, matiere, creneaux);
    }

    private Lesson cours(Long id, String classe, String matiere,
                          TimeSlotRef creneau, int duree, int volumeOfficiel) {
        return base(id, classe, matiere, creneau, duree, volumeOfficiel).build();
    }

    /** Le même cours, laissé ouvert : chaque cas n'altère que ce qu'il teste. */
    private Lesson.LessonBuilder base(Long id, String classe, String matiere,
                                       TimeSlotRef creneau, int duree, int volumeOfficiel) {
        return Lesson.builder()
                .id(id).studentClassName(classe).subjectCode(matiere).subjectName(matiere)
                .teachingAssignmentId(id).classStudentCount(30)
                .sessionType(SessionType.COURS).groupIndex(0)
                .durationSlots(duree).officialWeeklySlots(volumeOfficiel)
                .timeSlot(creneau).teacher(prof).room(salleA);
    }

    private Lesson demiGroupe(Long id, String classe, String matiere,
                               TimeSlotRef creneau, int index, RoomRef ou) {
        return baseDemiGroupe(id, creneau, index, ou)
                .studentClassName(classe).subjectCode(matiere).subjectName(matiere)
                .build();
    }

    private Lesson.LessonBuilder baseDemiGroupe(Long id, TimeSlotRef creneau, int index, RoomRef ou) {
        return Lesson.builder()
                .id(id).studentClassName("7A").subjectCode("INFO").subjectName("INFO")
                .teachingAssignmentId(id).classStudentCount(30)
                .sessionType(SessionType.TP).groupIndex(index).pairedLessonId(100L)
                .durationSlots(UNE_HEURE).officialWeeklySlots(0)
                .timeSlot(creneau).teacher(prof).room(ou);
    }

    /** Séance d'EPS exigeant le gymnase — § III.4. */
    private Lesson sport(Long id, TimeSlotRef creneau, RoomRef ou) {
        return base(id, "7A", "SPORT", creneau, UNE_HEURE, 0)
                .sessionType(SessionType.SPORT)
                .requiresSpecialRoom(true).requiredRoomType(RoomType.SALLESPORT)
                .room(ou)
                .build();
    }

    private static RoomRef salle(Long id, String code, RoomType type, int capacite) {
        return RoomRef.builder().id(id).code(code).type(type).capacity(capacite).build();
    }

    private static TeacherRef enseignant(Long id, String nom) {
        return TeacherRef.builder().id(id).code("T" + id).name(nom).maxHoursPerDay(6).build();
    }

    private static TimeSlotRef creneau(Long id, DayOfWeek jour, int ordre, String debut) {
        return TimeSlotRef.builder()
                .id(id).day(jour).orderIndex(ordre).period(DayPeriod.MORNING)
                .startTime(LocalTime.parse(debut)).endTime(LocalTime.parse(debut).plusMinutes(30))
                .active(true).maxDurationSlots(8)
                .build();
    }
}
