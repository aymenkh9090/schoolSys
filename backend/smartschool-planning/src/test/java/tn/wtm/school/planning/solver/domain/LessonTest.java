package tn.wtm.school.planning.solver.domain;

import org.junit.jupiter.api.Test;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import static org.assertj.core.api.Assertions.assertThat;

class LessonTest {

    @Test
    void fullClassLessonIsNotDemiGroup() {
        Lesson lesson = Lesson.builder().id(1L).groupIndex(0).build();
        assertThat(lesson.isDemiGroup()).isFalse();
    }

    @Test
    void groupALessonIsDemiGroup() {
        Lesson lesson = Lesson.builder().id(2L).groupIndex(1).build();
        assertThat(lesson.isDemiGroup()).isTrue();
    }

    @Test
    void groupBLessonIsDemiGroup() {
        Lesson lesson = Lesson.builder().id(3L).groupIndex(2).build();
        assertThat(lesson.isDemiGroup()).isTrue();
    }

    @Test
    void lessonWithPairedIdIsPaired() {
        Lesson lesson = Lesson.builder().id(1L).pairedLessonId(99L).build();
        assertThat(lesson.isPaired()).isTrue();
    }

    @Test
    void lessonWithoutPairedIdIsNotPaired() {
        Lesson lesson = Lesson.builder().id(1L).build();
        assertThat(lesson.isPaired()).isFalse();
    }

    @Test
    void lessonIsNotAssignedWhenPlanningVariablesAreNull() {
        Lesson lesson = Lesson.builder().id(1L).build();
        assertThat(lesson.isAssigned()).isFalse();
    }

    @Test
    void lessonIsAssignedWhenTimeslotAndRoomAreSet() {
        Lesson lesson = Lesson.builder()
                .id(1L)
                .timeSlot(TimeSlotRef.builder().id(10L).build())
                .room(RoomRef.builder().id(20L).type(RoomType.NORMALE).build())
                .build();
        assertThat(lesson.isAssigned()).isTrue();
    }

    @Test
    void lessonRequiresSpecialRoomFlagIsReadable() {
        Lesson withLab = Lesson.builder().id(1L)
                .sessionType(SessionType.TP)
                .requiredRoomType(RoomType.LABPHYSIQUE)
                .requiresSpecialRoom(true)
                .build();

        Lesson standard = Lesson.builder().id(2L)
                .sessionType(SessionType.COURS)
                .requiresSpecialRoom(false)
                .build();

        assertThat(withLab.isRequiresSpecialRoom()).isTrue();
        assertThat(standard.isRequiresSpecialRoom()).isFalse();
    }

    @Test
    void equalityBasedOnIdOnly() {
        Lesson a = Lesson.builder().id(5L).subjectCode("MATH").groupIndex(0).build();
        Lesson b = Lesson.builder().id(5L).subjectCode("PHYS").groupIndex(1).build();
        Lesson c = Lesson.builder().id(6L).subjectCode("MATH").groupIndex(0).build();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void planningVariablesStartNullByDefault() {
        Lesson lesson = Lesson.builder().id(1L).build();
        assertThat(lesson.getTimeSlot()).isNull();
        assertThat(lesson.getTeacher()).isNull();
        assertThat(lesson.getRoom()).isNull();
    }

    @Test
    void planningVariablesAreWriteable() {
        Lesson lesson = Lesson.builder().id(1L).build();

        TimeSlotRef slot = TimeSlotRef.builder().id(10L).build();
        TeacherRef teacher = TeacherRef.builder().id(20L).build();
        RoomRef room = RoomRef.builder().id(30L).type(RoomType.LABPHYSIQUE).build();

        lesson.setTimeSlot(slot);
        lesson.setTeacher(teacher);
        lesson.setRoom(room);

        assertThat(lesson.getTimeSlot()).isSameAs(slot);
        assertThat(lesson.getTeacher()).isSameAs(teacher);
        assertThat(lesson.getRoom()).isSameAs(room);
    }
}
