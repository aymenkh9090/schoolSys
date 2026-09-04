package tn.wtm.school.planning.solver.builder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;
import tn.wtm.school.planning.constraints.repository.ConstraintProfileRepository;
import tn.wtm.school.planning.solver.constraint.ConstraintWeightMapper;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimetableProblemBuilderTest {

    @Mock private TimeSlotRepository          timeSlotRepository;
    @Mock private RoomRepository              roomRepository;
    @Mock private TeacherRepository           teacherRepository;
    @Mock private ConstraintProfileRepository constraintProfileRepository;
    @Mock private LessonGenerator             lessonGenerator;
    @Mock private ConstraintWeightMapper      constraintWeightMapper;
    @Mock private CustomConstraintLoader      customConstraintLoader;

    private TimetableProblemBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new TimetableProblemBuilder(
                timeSlotRepository, roomRepository, teacherRepository,
                constraintProfileRepository, lessonGenerator, constraintWeightMapper,
                customConstraintLoader);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── tenant isolation ──────────────────────────────────────────────────────

    @Test
    void throwsNotFoundWhenProfileBelongsToAnotherTenant() {
        when(constraintProfileRepository.findByIdConstraintProfileAndTenantId(99L, "school-a"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> builder.build("school-a", 2026L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(timeSlotRepository, roomRepository, lessonGenerator);
    }

    // ── room type mapping ─────────────────────────────────────────────────────

    @Test
    void nullTypeSalleMapsToNormale() {
        assertThat(TimetableProblemBuilder.mapRoomType(null)).isEqualTo(RoomType.NORMALE);
    }

    @Test
    void labPhysiqueTypeSalleMapsToLabPhysique() {
        assertThat(TimetableProblemBuilder.mapRoomType(tn.wtm.school.org.enums.RoomType.LABPHYSIQUE))
                .isEqualTo(RoomType.LABPHYSIQUE);
    }

    @Test
    void labInformatiqueTypeSalleMapsToLabInformatique() {
        assertThat(TimetableProblemBuilder.mapRoomType(tn.wtm.school.org.enums.RoomType.LABINFORMATIQUE))
                .isEqualTo(RoomType.LABINFORMATIQUE);
    }

    @Test
    void normaleTypeSalleMapsToNormale() {
        assertThat(TimetableProblemBuilder.mapRoomType(tn.wtm.school.org.enums.RoomType.NORMALE))
                .isEqualTo(RoomType.NORMALE);
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    void throwsBadRequestWhenAcademicYearIdIsNull() {
        assertThatThrownBy(() -> builder.build("school-1", null, null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

}
