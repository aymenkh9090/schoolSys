package tn.wtm.school.planning.solver.ref;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherRefTest {

    @Test
    void unavailableOnReturnsTrueWhenDayInSet() {
        TeacherRef teacher = TeacherRef.builder()
                .id(1L).code("08").name("Ben Ali")
                .maxHoursPerDay(6)
                .unavailableDays(Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY))
                .build();

        assertThat(teacher.isUnavailableOn(DayOfWeek.WEDNESDAY)).isTrue();
        assertThat(teacher.isUnavailableOn(DayOfWeek.SATURDAY)).isTrue();
    }

    @Test
    void unavailableOnReturnsFalseWhenDayNotInSet() {
        TeacherRef teacher = TeacherRef.builder()
                .id(1L).code("08").name("Ben Ali")
                .maxHoursPerDay(6)
                .unavailableDays(Set.of(DayOfWeek.WEDNESDAY))
                .build();

        assertThat(teacher.isUnavailableOn(DayOfWeek.MONDAY)).isFalse();
    }

    @Test
    void unavailableOnReturnsFalseWhenSetIsEmpty() {
        TeacherRef teacher = TeacherRef.builder()
                .id(1L).code("08").name("Ben Ali")
                .maxHoursPerDay(6)
                .build();

        assertThat(teacher.isUnavailableOn(DayOfWeek.FRIDAY)).isFalse();
    }

    @Test
    void equalityBasedOnIdOnly() {
        TeacherRef a = TeacherRef.builder().id(3L).code("A").name("X").maxHoursPerDay(6).build();
        TeacherRef b = TeacherRef.builder().id(3L).code("B").name("Y").maxHoursPerDay(4).build();
        TeacherRef c = TeacherRef.builder().id(4L).code("A").name("X").maxHoursPerDay(6).build();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }
}
