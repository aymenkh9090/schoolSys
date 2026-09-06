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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les règles que la circulaire n°66 énonce et que rien n'appliquait — étape D
 * du plan de mise en conformité.
 *
 * <p>Là où l'étape C câblait deux codes qui existaient déjà au catalogue sans
 * flux, celle-ci fait entrer dans le système cinq articles qu'aucune ligne ne
 * représentait, corrige une contrainte qui mesurait la mauvaise chose
 * (§ II.4) et en réveille une dernière restée à l'état de constante (§ II.5).
 *
 * <p><b>Le décor est en créneaux de trente minutes</b>, comme la production :
 * {@code orderIndex} et heure de début avancent du même pas. C'est nécessaire
 * ici parce que les contraintes dures toujours actives — {@code noStudentIdleGaps}
 * en particulier — raisonnent sur {@code orderIndex} tandis que les conflits
 * raisonnent sur l'heure. Un décor où les deux divergent produit des violations
 * dures parasites qui rendraient illisible le score des règles testées.
 */
class ConformiteCirculaireEtapeDTest {

    private static final SolverConfig CONFIG = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

    private final SolutionManager<TimetableSolution, HardMediumSoftScore> scorer =
            SolutionManager.create(SolverFactory.create(CONFIG));

    // ── décor ─────────────────────────────────────────────────────────────────

    /** Une heure de cours = deux créneaux de 30 min. */
    private static final int UNE_HEURE = 2;
    private static final int DEUX_HEURES = 4;

    private final RoomRef salleA = RoomRef.builder()
            .id(1L).code("A1").type(RoomType.NORMALE).capacity(30).build();
    private final RoomRef salleB = RoomRef.builder()
            .id(2L).code("A2").type(RoomType.NORMALE).capacity(30).build();
    private final RoomRef gymnase = RoomRef.builder()
            .id(3L).code("GYM").type(RoomType.SALLESPORT).capacity(60).build();

    private final TeacherRef prof = TeacherRef.builder()
            .id(1L).code("T1").name("Prof").maxHoursPerDay(6).build();

    // ══════════════════════════════════════════════════════════════════════════
    // § III.2.c — une matière à 2 h/semaine, jamais deux jours de suite
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Matière à deux heures : pas deux jours consécutifs (§ III.2.c)")
    class DeuxHeuresJoursConsecutifs {

        @Test
        @DisplayName("Lundi puis mercredi : conforme")
        void joursEspaces() {
            Lesson a = cours(1L, "7A", "HGEO", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, DEUX_HEURES);
            Lesson b = cours(2L, "7A", "HGEO", matin(DayOfWeek.WEDNESDAY, 1), UNE_HEURE, DEUX_HEURES);

            assertThat(medium(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS, a, b)).isZero();
        }

        @Test
        @DisplayName("Lundi puis mardi : violation")
        void joursConsecutifs() {
            Lesson a = cours(1L, "7A", "HGEO", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, DEUX_HEURES);
            Lesson b = cours(2L, "7A", "HGEO", matin(DayOfWeek.TUESDAY, 1), UNE_HEURE, DEUX_HEURES);

            assertThat(medium(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS, a, b))
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("Une seule séance de deux heures ne peut pas violer la règle")
        void seanceUnique() {
            Lesson bloc = cours(1L, "7A", "HGEO", matin(DayOfWeek.MONDAY, 1), DEUX_HEURES, DEUX_HEURES);

            assertThat(medium(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS, bloc)).isZero();
        }

        @Test
        @DisplayName("Une matière à quatre heures n'est pas visée par le texte")
        void volumeSuperieur() {
            // Le § III.2.c désigne les matières par leur volume, pas par leur nom :
            // au-delà de deux heures, la contiguïté redevient inévitable.
            Lesson a = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 8);
            Lesson b = cours(2L, "7A", "MATH", matin(DayOfWeek.TUESDAY, 1), UNE_HEURE, 8);

            assertThat(medium(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS, a, b)).isZero();
        }

        @Test
        @DisplayName("Samedi puis lundi : un dimanche les sépare, ce n'est pas consécutif")
        void samediPuisLundi() {
            Lesson a = cours(1L, "7A", "HGEO", matin(DayOfWeek.SATURDAY, 1), UNE_HEURE, DEUX_HEURES);
            Lesson b = cours(2L, "7A", "HGEO", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, DEUX_HEURES);

            assertThat(medium(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS, a, b)).isZero();
        }

        @Test
        @DisplayName("Deux classes différentes ne se gênent pas")
        void classesDifferentes() {
            Lesson a = cours(1L, "7A", "HGEO", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, DEUX_HEURES);
            Lesson b = cours(2L, "7B", "HGEO", matin(DayOfWeek.TUESDAY, 1), UNE_HEURE, DEUX_HEURES);

            assertThat(medium(ConstraintCodes.SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS, a, b)).isZero();
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            Lesson a = cours(1L, "7A", "HGEO", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, DEUX_HEURES);
            Lesson b = cours(2L, "7A", "HGEO", matin(DayOfWeek.TUESDAY, 1), UNE_HEURE, DEUX_HEURES);

            assertThat(scoreAvec(null, a, b).mediumScore()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § III.2.b — vingt-quatre heures entre deux séances d'EPS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Espacement des séances d'EPS (§ III.2.b)")
    class EspacementEps {

        @Test
        @DisplayName("Lundi, mercredi, vendredi : les trois séances sont espacées")
        void troisSeancesEspacees() {
            Lesson a = sport(1L, matin(DayOfWeek.MONDAY, 1));
            Lesson b = sport(2L, matin(DayOfWeek.WEDNESDAY, 1));
            Lesson c = sport(3L, matin(DayOfWeek.FRIDAY, 1));

            assertThat(medium(ConstraintCodes.PHYSICAL_EDUCATION_SESSION_SPACING, a, b, c)).isZero();
        }

        @Test
        @DisplayName("Deux séances le même jour : violation")
        void memeJour() {
            Lesson a = sport(1L, matin(DayOfWeek.MONDAY, 1));
            Lesson b = sport(2L, matin(DayOfWeek.MONDAY, 5));

            assertThat(medium(ConstraintCodes.PHYSICAL_EDUCATION_SESSION_SPACING, a, b))
                    .as("deux heures d'écart au lieu de vingt-quatre")
                    .isEqualTo(-22);
        }

        @Test
        @DisplayName("Lundi 10 h puis mardi 8 h : vingt-deux heures, c'est trop peu")
        void lendemainPlusTot() {
            // Le cas que la mesure « de début à début » attrape et que la mesure
            // « de fin à début » laisserait passer — voir § 1.6 du plan.
            Lesson a = sport(1L, matin(DayOfWeek.MONDAY, 5));   // 10:00
            Lesson b = sport(2L, matin(DayOfWeek.TUESDAY, 1));  // 08:00

            assertThat(medium(ConstraintCodes.PHYSICAL_EDUCATION_SESSION_SPACING, a, b))
                    .isEqualTo(-2);
        }

        @Test
        @DisplayName("Lundi 8 h puis mardi 8 h : exactement vingt-quatre heures, conforme")
        void exactementVingtQuatreHeures() {
            Lesson a = sport(1L, matin(DayOfWeek.MONDAY, 1));
            Lesson b = sport(2L, matin(DayOfWeek.TUESDAY, 1));

            assertThat(medium(ConstraintCodes.PHYSICAL_EDUCATION_SESSION_SPACING, a, b))
                    .as("la circulaire écrit « au moins », la borne est donc atteignable")
                    .isZero();
        }

        @Test
        @DisplayName("Les matières autres que l'EPS ne sont pas concernées")
        void autreMatiere() {
            Lesson a = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);
            Lesson b = cours(2L, "7A", "MATH", matin(DayOfWeek.MONDAY, 5), UNE_HEURE, 0);

            assertThat(medium(ConstraintCodes.PHYSICAL_EDUCATION_SESSION_SPACING, a, b)).isZero();
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            assertThat(scoreAvec(null,
                    sport(1L, matin(DayOfWeek.MONDAY, 1)),
                    sport(2L, matin(DayOfWeek.MONDAY, 5))).mediumScore()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § I.2 — deux heures au minimum par demi-journée
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plancher de deux heures par demi-journée (§ I.2)")
    class PlancherDemiJournee {

        @Test
        @DisplayName("Deux heures dans la matinée : conforme")
        void deuxHeuresPleines() {
            Lesson a = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);
            Lesson b = cours(2L, "7A", "ARABE", matin(DayOfWeek.MONDAY, 3), UNE_HEURE, 0);

            assertThat(hard(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY, a, b)).isZero();
        }

        @Test
        @DisplayName("Une heure seule : la classe s'est déplacée pour rien")
        void uneHeureSeule() {
            Lesson seule = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);

            assertThat(hard(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY, seule))
                    .as("deux créneaux placés contre quatre exigés")
                    .isEqualTo(-2);
        }

        @Test
        @DisplayName("Une heure d'EPS seule : le texte l'exclut nommément")
        void epsSeul() {
            assertThat(hard(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY,
                    sport(1L, matin(DayOfWeek.MONDAY, 1)))).isZero();
        }

        @Test
        @DisplayName("Une heure d'EPS et une heure de maths atteignent le plancher")
        void epsAccompagne() {
            Lesson eps   = sport(1L, matin(DayOfWeek.MONDAY, 1));
            Lesson maths = cours(2L, "7A", "MATH", matin(DayOfWeek.MONDAY, 3), UNE_HEURE, 0);

            assertThat(hard(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY, eps, maths))
                    .as("l'exemption vise la demi-journée entièrement sportive, "
                            + "pas les heures d'EPS prises une à une")
                    .isZero();
        }

        @Test
        @DisplayName("Matin plein, après-midi d'une heure : seul l'après-midi est en défaut")
        void unePeriodeSurDeux() {
            Lesson m1 = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), DEUX_HEURES, 0);
            Lesson a1 = cours(2L, "7A", "ARABE", apresMidi(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);

            assertThat(hard(ConstraintCodes.MIN_STUDENT_HOURS_PER_HALF_DAY, m1, a1))
                    .isEqualTo(-2);
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            assertThat(scoreAvec(null,
                    cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0)).hardScore())
                    .isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § III.2.a — trois quarts des matières fondamentales le matin
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Quota matinal des matières fondamentales (§ III.2.a)")
    class QuotaMatinal {

        /** Quatre heures de mathématiques, dont la part matinale varie. */
        private Lesson[] mathsReparties(int seancesLeMatin) {
            List<Lesson> seances = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                boolean leMatin = i < seancesLeMatin;
                TimeSlotRef creneau = leMatin
                        ? matin(DayOfWeek.of(i + 1), 1)
                        : apresMidi(DayOfWeek.of(i + 1), 1);
                seances.add(fondamentale(i + 1L, "MATH", creneau));
            }
            return seances.toArray(new Lesson[0]);
        }

        @Test
        @DisplayName("Trois séances sur quatre le matin : le quota est atteint")
        void quotaAtteint() {
            assertThat(medium(ConstraintCodes.MAIN_SUBJECTS_MORNING_QUOTA, mathsReparties(3)))
                    .as("six créneaux matinaux récompensés sur huit placés")
                    .isEqualTo(6);
        }

        @Test
        @DisplayName("Tout le matin ne rapporte pas plus que les trois quarts")
        void quotaPlafonne() {
            // La circulaire réserve un quart de l'horaire à l'après-midi : une
            // récompense qui croîtrait jusqu'à huit pousserait le solveur à vider
            // l'après-midi, c'est-à-dire à violer le texte qu'elle sert.
            assertThat(medium(ConstraintCodes.MAIN_SUBJECTS_MORNING_QUOTA, mathsReparties(4)))
                    .isEqualTo(6);
        }

        @Test
        @DisplayName("Une fondamentale reléguée l'après-midi ne rapporte rien")
        void toutLApresMidi() {
            assertThat(medium(ConstraintCodes.MAIN_SUBJECTS_MORNING_QUOTA, mathsReparties(0)))
                    .isZero();
        }

        @Test
        @DisplayName("Une matière ordinaire n'entre pas dans le quota")
        void matiereOrdinaire() {
            Lesson musique = cours(1L, "7A", "MUS", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);

            assertThat(medium(ConstraintCodes.MAIN_SUBJECTS_MORNING_QUOTA, musique))
                    .as("mainSubject vient de Subject.estPrincipale : "
                            + "c'est l'établissement qui désigne ses fondamentales")
                    .isZero();
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune récompense")
        void desactivee() {
            assertThat(scoreAvec(null, mathsReparties(3)).mediumScore()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § I.4 — la classe ne change pas de salle dans une demi-journée
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stabilité de la salle sur une demi-journée (§ I.4)")
    class StabiliteSalle {

        @Test
        @DisplayName("Deux cours dans la même salle : conforme")
        void memeSalle() {
            Lesson a = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);
            Lesson b = cours(2L, "7A", "ARABE", matin(DayOfWeek.MONDAY, 3), UNE_HEURE, 0);

            assertThat(medium(ConstraintCodes.CLASS_ROOM_STABILITY_PER_HALF_DAY, a, b)).isZero();
        }

        @Test
        @DisplayName("Un changement de salle ordinaire coûte une violation")
        void salleDifferente() {
            Lesson a = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);
            Lesson b = coursEn(2L, "ARABE", matin(DayOfWeek.MONDAY, 3), UNE_HEURE, salleB);

            assertThat(medium(ConstraintCodes.CLASS_ROOM_STABILITY_PER_HALF_DAY, a, b))
                    .isEqualTo(-1);
        }

        @Test
        @DisplayName("Le passage au gymnase n'est pas un changement de salle reprochable")
        void salleSpecialisee() {
            // § III.4 : le sport se fait au gymnase. Pénaliser ce déplacement
            // opposerait deux articles de la même circulaire.
            Lesson maths = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);
            Lesson eps   = sport(2L, matin(DayOfWeek.MONDAY, 3));

            assertThat(medium(ConstraintCodes.CLASS_ROOM_STABILITY_PER_HALF_DAY, maths, eps))
                    .isZero();
        }

        @Test
        @DisplayName("Changer de salle entre le matin et l'après-midi est permis")
        void demiJourneesDistinctes() {
            Lesson a = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), DEUX_HEURES, 0);
            Lesson b = coursEn(2L, "ARABE", apresMidi(DayOfWeek.MONDAY, 1), DEUX_HEURES, salleB);

            assertThat(medium(ConstraintCodes.CLASS_ROOM_STABILITY_PER_HALF_DAY, a, b))
                    .as("le texte borne la stabilité à la demi-journée")
                    .isZero();
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            Lesson a = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);
            Lesson b = coursEn(2L, "ARABE", matin(DayOfWeek.MONDAY, 3), UNE_HEURE, salleB);

            assertThat(scoreAvec(null, a, b).mediumScore()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § II.4 — l'alternance ne porte que sur les quatre premiers jours
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Alternance matin / après-midi sur les quatre premiers jours (§ II.4)")
    class AlternanceQuatreJours {

        @Test
        @DisplayName("Le vendredi ne compte plus dans l'équilibre")
        void vendrediIgnore() {
            // La correction de l'étape D. Vendredi et samedi sont sans après-midi
            // dans beaucoup d'établissements : les compter creusait un déséquilibre
            // que rien ne pouvait combler, et le solveur payait pour un emploi du
            // temps régulier.
            Lesson vendredi = cours(1L, "7A", "MATH", matin(DayOfWeek.FRIDAY, 1), UNE_HEURE, 0);

            assertThat(medium(ConstraintCodes.BALANCED_MORNING_AFTERNOON, vendredi)).isZero();
        }

        @Test
        @DisplayName("Le samedi non plus")
        void samediIgnore() {
            Lesson samedi = cours(1L, "7A", "MATH", matin(DayOfWeek.SATURDAY, 1), UNE_HEURE, 0);

            assertThat(medium(ConstraintCodes.BALANCED_MORNING_AFTERNOON, samedi)).isZero();
        }

        @Test
        @DisplayName("Un lundi uniquement matinal reste un déséquilibre")
        void lundiDesequilibre() {
            Lesson lundi = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);

            assertThat(medium(ConstraintCodes.BALANCED_MORNING_AFTERNOON, lundi)).isEqualTo(-1);
        }

        @Test
        @DisplayName("Un matin et un après-midi dans les quatre premiers jours s'équilibrent")
        void alternanceRespectee() {
            Lesson lundi = cours(1L, "7A", "MATH", matin(DayOfWeek.MONDAY, 1), UNE_HEURE, 0);
            Lesson mardi = cours(2L, "7A", "MATH", apresMidi(DayOfWeek.TUESDAY, 1), UNE_HEURE, 0);

            assertThat(medium(ConstraintCodes.BALANCED_MORNING_AFTERNOON, lundi, mardi)).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // § II.5 — au moins deux niveaux par enseignant
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deux niveaux minimum par enseignant (§ II.5)")
    class DeuxNiveaux {

        @Test
        @DisplayName("Un enseignant sur deux niveaux : conforme")
        void deuxNiveaux() {
            Lesson a = niveau(1L, "7A", "7EME", matin(DayOfWeek.MONDAY, 1));
            Lesson b = niveau(2L, "8A", "8EME", matin(DayOfWeek.TUESDAY, 1));

            assertThat(soft(ConstraintCodes.TEACHER_MIN_TWO_LEVELS, a, b)).isZero();
        }

        @Test
        @DisplayName("Un enseignant enfermé sur un seul niveau : pénalité pondérée")
        void niveauUnique() {
            Lesson a = niveau(1L, "7A", "7EME", matin(DayOfWeek.MONDAY, 1));
            Lesson b = niveau(2L, "7B", "7EME", matin(DayOfWeek.TUESDAY, 1));

            assertThat(soft(ConstraintCodes.TEACHER_MIN_TWO_LEVELS, a, b))
                    .as("un niveau manquant, multiplié par le poids du réglage")
                    .isEqualTo(-1000);
        }

        @Test
        @DisplayName("Une séance sans niveau renseigné n'en tient pas lieu")
        void niveauAbsent() {
            Lesson connu   = niveau(1L, "7A", "7EME", matin(DayOfWeek.MONDAY, 1));
            Lesson inconnu = niveau(2L, "7B", null, matin(DayOfWeek.TUESDAY, 1));

            assertThat(soft(ConstraintCodes.TEACHER_MIN_TWO_LEVELS, connu, inconnu))
                    .as("une donnée absente est une donnée absente, pas un second niveau")
                    .isEqualTo(-1000);
        }

        @Test
        @DisplayName("Contrainte désactivée : aucune pénalité")
        void desactivee() {
            assertThat(scoreAvec(null,
                    niveau(1L, "7A", "7EME", matin(DayOfWeek.MONDAY, 1))).softScore()).isZero();
        }
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    private int medium(String code, Lesson... lessons) {
        return scoreAvec(param(code), lessons).mediumScore();
    }

    private int hard(String code, Lesson... lessons) {
        return scoreAvec(param(code), lessons).hardScore();
    }

    private int soft(String code, Lesson... lessons) {
        return scoreAvec(param(code), lessons).softScore();
    }

    private static ActiveConstraintParam param(String code) {
        return new ActiveConstraintParam(code, 0, 1000);
    }

    private HardMediumSoftScore scoreAvec(ActiveConstraintParam param, Lesson... lessons) {
        return scorer.update(TimetableSolution.builder()
                .tenantId("ecole-1").academicYearId(2026L).constraintProfileId(1L)
                .timeSlots(tousLesCreneaux())
                .teachers(List.of(prof))
                .rooms(List.of(salleA, salleB, gymnase))
                .lessons(List.of(lessons))
                .activeConstraintParams(param == null ? List.of() : List.of(param))
                .build());
    }

    // ── fabriques de séances ──────────────────────────────────────────────────

    private Lesson cours(Long id, String classe, String matiere,
                          TimeSlotRef creneau, int duree, int volumeOfficiel) {
        return base(id, classe, matiere, creneau, duree, volumeOfficiel)
                .sessionType(SessionType.COURS).room(salleA).build();
    }

    /** Même chose, dans une salle nommée : c'est la salle que le § I.4 observe. */
    private Lesson coursEn(Long id, String matiere, TimeSlotRef creneau, int duree, RoomRef ou) {
        return base(id, "7A", matiere, creneau, duree, 0)
                .sessionType(SessionType.COURS).room(ou).build();
    }

    /** Matière fondamentale au sens de {@code Subject.estPrincipale} — § III.2.a. */
    private Lesson fondamentale(Long id, String matiere, TimeSlotRef creneau) {
        return base(id, "7A", matiere, creneau, UNE_HEURE, 0)
                .sessionType(SessionType.COURS).room(salleA).mainSubject(true).build();
    }

    /** Séance d'EPS d'une heure, au gymnase comme l'exige le § III.4. */
    private Lesson sport(Long id, TimeSlotRef creneau) {
        return base(id, "7A", "SPORT", creneau, UNE_HEURE, 0)
                .sessionType(SessionType.SPORT)
                .requiresSpecialRoom(true).requiredRoomType(RoomType.SALLESPORT)
                .room(gymnase)
                .build();
    }

    /** Séance portant un niveau de classe explicite — § II.5. */
    private Lesson niveau(Long id, String classe, String niveauClasse, TimeSlotRef creneau) {
        return base(id, classe, "MATH", creneau, UNE_HEURE, 0)
                .sessionType(SessionType.COURS).room(salleA)
                .studentClassLevel(niveauClasse).build();
    }

    private Lesson.LessonBuilder base(Long id, String classe, String matiere,
                                       TimeSlotRef creneau, int duree, int volumeOfficiel) {
        return Lesson.builder()
                .id(id).studentClassName(classe).subjectCode(matiere)
                .teachingAssignmentId(id).classStudentCount(30)
                .durationSlots(duree).officialWeeklySlots(volumeOfficiel)
                .timeSlot(creneau).teacher(prof);
    }

    // ── créneaux ──────────────────────────────────────────────────────────────
    //
    // Un créneau dure trente minutes ; `rang` est son numéro dans la demi-journée,
    // à partir de 1. Le matin commence à 8 h, l'après-midi à 14 h, et l'orderIndex
    // est continu sur la journée pour que noStudentIdleGaps mesure juste.

    private static final int CRENEAUX_PAR_DEMI_JOURNEE = 8;

    private static final List<DayOfWeek> SEMAINE_SCOLAIRE = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    /**
     * La semaine entière, construite une fois pour toutes.
     *
     * <p><b>L'unicité des instances est nécessaire, pas cosmétique.</b> Timefold
     * vérifie qu'une variable de planification tombe bien dans son domaine de
     * valeurs, et {@code TimeSlotRef} ne porte pas de {@code @PlanningId} : la
     * vérification se fait donc par identité. Une fabrique qui renvoie un
     * nouveau créneau à chaque appel — même égal au précédent — fait échouer
     * l'affectation avec « outside of the related value range ».
     */
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
        for (DayOfWeek jour : SEMAINE_SCOLAIRE) {
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

    /** Toutes les demi-heures de la semaine — le domaine de valeurs du solveur. */
    private static List<TimeSlotRef> tousLesCreneaux() {
        return new ArrayList<>(CRENEAUX.values());
    }
}
