package tn.wtm.school.planning.solver.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tn.wtm.school.planning.solver.enums.WeekParity;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La parité de semaine — notation {@code ①} de la circulaire n°66, § N.1.
 *
 * <p>Ce que ces tests protègent : deux séances de quinzaine opposées occupent le
 * même créneau sans se heurter, parce qu'elles n'ont jamais lieu la même
 * semaine. C'est la seule raison d'être du champ, et c'est ce qui permet à une
 * séance de quinzaine d'exister sans bloquer un créneau une semaine sur deux.
 */
class LessonWeekParityTest {

    private static TimeSlotRef creneau(LocalTime debut) {
        return TimeSlotRef.builder()
                .id(1L).day(DayOfWeek.MONDAY).startTime(debut)
                .endTime(debut.plusMinutes(30)).orderIndex(1).active(true).build();
    }

    private static Lesson seance(WeekParity parite, LocalTime debut, int slots) {
        return Lesson.builder()
                .id(1L).subjectCode("PHY").studentClassName("7A")
                .weekParity(parite).durationSlots(slots).timeSlot(creneau(debut))
                .build();
    }

    @Nested
    @DisplayName("Recouvrement des semaines")
    class Recouvrement {

        @Test
        @DisplayName("Deux quinzaines opposées ne se croisent jamais")
        void quinzainesOpposees() {
            assertThat(WeekParity.ODD.overlapsWith(WeekParity.EVEN)).isFalse();
            assertThat(WeekParity.EVEN.overlapsWith(WeekParity.ODD)).isFalse();
        }

        @Test
        @DisplayName("Une séance hebdomadaire croise tout, y compris une quinzaine")
        void hebdomadaireCroiseTout() {
            assertThat(WeekParity.ALL.overlapsWith(WeekParity.ODD)).isTrue();
            assertThat(WeekParity.ALL.overlapsWith(WeekParity.EVEN)).isTrue();
            assertThat(WeekParity.ODD.overlapsWith(WeekParity.ALL)).isTrue();
            assertThat(WeekParity.ALL.overlapsWith(WeekParity.ALL)).isTrue();
        }

        @Test
        @DisplayName("Deux quinzaines de même parité se croisent")
        void memeParite() {
            assertThat(WeekParity.ODD.overlapsWith(WeekParity.ODD)).isTrue();
            assertThat(WeekParity.EVEN.overlapsWith(WeekParity.EVEN)).isTrue();
        }
    }

    @Nested
    @DisplayName("Chevauchement horaire")
    class Chevauchement {

        @Test
        @DisplayName("Même créneau, quinzaines opposées : aucun conflit")
        void memeCreneauParitesOpposees() {
            Lesson impaire = seance(WeekParity.ODD, LocalTime.of(8, 0), 2);
            Lesson paire   = seance(WeekParity.EVEN, LocalTime.of(8, 0), 2);

            assertThat(impaire.overlapsInTime(paire))
                    .as("deux séances qui n'ont jamais lieu la même semaine "
                            + "ne se disputent ni le créneau, ni la salle, ni l'enseignant")
                    .isFalse();
            assertThat(paire.overlapsInTime(impaire)).isFalse();
        }

        @Test
        @DisplayName("Même créneau, même parité : conflit")
        void memeCreneauMemeParite() {
            Lesson a = seance(WeekParity.ODD, LocalTime.of(8, 0), 2);
            Lesson b = seance(WeekParity.ODD, LocalTime.of(8, 0), 2);
            assertThat(a.overlapsInTime(b)).isTrue();
        }

        @Test
        @DisplayName("Une hebdomadaire et une quinzaine sur le même créneau : conflit")
        void hebdomadaireContreQuinzaine() {
            Lesson hebdo    = seance(WeekParity.ALL, LocalTime.of(8, 0), 2);
            Lesson quinzaine = seance(WeekParity.ODD, LocalTime.of(8, 0), 2);
            assertThat(hebdo.overlapsInTime(quinzaine))
                    .as("la semaine impaire, les deux séances tombent bien ensemble")
                    .isTrue();
        }

        @Test
        @DisplayName("La parité ne masque pas un décalage horaire réel")
        void pariteNeMasquePasLHoraire() {
            Lesson a = seance(WeekParity.ALL, LocalTime.of(8, 0), 2);   // 8h00 → 9h00
            Lesson b = seance(WeekParity.ALL, LocalTime.of(9, 0), 2);   // 9h00 → 10h00
            assertThat(a.overlapsInTime(b))
                    .as("séances contiguës mais disjointes")
                    .isFalse();
        }

        @Test
        @DisplayName("Parité nulle traitée comme hebdomadaire")
        void pariteNulle() {
            Lesson sansParite = Lesson.builder()
                    .id(9L).studentClassName("7A").durationSlots(2)
                    .timeSlot(creneau(LocalTime.of(8, 0))).weekParity(null).build();
            Lesson hebdo = seance(WeekParity.ALL, LocalTime.of(8, 0), 2);

            assertThat(sansParite.sharesWeeksWith(hebdo))
                    .as("une parité absente ne doit jamais faire disparaître un conflit")
                    .isTrue();
            assertThat(sansParite.overlapsInTime(hebdo)).isTrue();
        }
    }

    @Test
    @DisplayName("Par défaut, une séance a lieu toutes les semaines")
    void defautHebdomadaire() {
        assertThat(Lesson.builder().id(1L).build().getWeekParity()).isEqualTo(WeekParity.ALL);
    }
}
