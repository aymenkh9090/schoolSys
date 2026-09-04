package tn.wtm.school.planning.solver.builder;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.planning.constraints.dsl.CompiledConstraint;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;
import tn.wtm.school.planning.constraints.repository.ConstraintProfileRepository;
import tn.wtm.school.planning.solver.builder.LessonGenerator;
import tn.wtm.school.planning.solver.constraint.ActiveConstraintParam;
import tn.wtm.school.planning.solver.constraint.ConstraintWeightMapper;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assembles the TimetableSolution problem from organisation-module data
 * (PLANNING_MODULE.md §8).
 *
 * Every persisted TimeSlot (creneaux_horaires) already falls within a working
 * block configured by the school (morning/afternoon bounds) — gaps such as the
 * lunch break are simply never generated as rows, so all slots read here are
 * genuinely assignable.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableProblemBuilder {

    private final TimeSlotRepository          timeSlotRepository;
    private final RoomRepository              roomRepository;
    private final TeacherRepository           teacherRepository;
    private final ConstraintProfileRepository constraintProfileRepository;
    private final LessonGenerator             lessonGenerator;
    private final ConstraintWeightMapper      constraintWeightMapper;
    private final CustomConstraintLoader      customConstraintLoader;

    public TimetableSolution build(String tenantId, Long academicYearId, Long constraintProfileId) {
        if (academicYearId == null) {
            throw new BadRequestException("L'identifiant de l'annee scolaire est obligatoire");
        }

        ConstraintProfile profile = resolveProfile(tenantId, academicYearId, constraintProfileId);

        List<TimeSlotRef>         timeSlots        = buildTimeSlots(tenantId);
        List<TeacherRef>          teachers         = buildTeachers(tenantId);
        List<RoomRef>             rooms            = buildRooms(tenantId);
        List<Lesson>              lessons          = lessonGenerator.generate(tenantId, academicYearId);
        List<ActiveConstraintParam> constraintParams =
                constraintWeightMapper.load(tenantId, profile.getIdConstraintProfile());
        List<CompiledConstraint>    customConstraints =
                customConstraintLoader.load(tenantId, profile.getIdConstraintProfile());

        if (timeSlots.isEmpty()) {
            throw new BadRequestException("Aucun creneau horaire configure pour ce tenant");
        }
        if (rooms.isEmpty()) {
            throw new BadRequestException("Aucune salle disponible pour ce tenant");
        }
        if (lessons.isEmpty()) {
            throw new BadRequestException("Aucune affectation active trouvee pour cette annee scolaire");
        }

        return TimetableSolution.builder()
                .tenantId(tenantId)
                .academicYearId(academicYearId)
                .constraintProfileId(profile.getIdConstraintProfile())
                .timeSlots(timeSlots)
                .teachers(teachers)
                .rooms(rooms)
                .lessons(lessons)
                .activeConstraintParams(constraintParams)
                .customConstraints(customConstraints)
                .build();
    }

    // ── data loaders ──────────────────────────────────────────────────────────

    private List<TimeSlotRef> buildTimeSlots(String tenantId) {
        // Only active slots are assignable — break slots (12h–14h) are excluded
        // from the value range entirely so the solver never wastes effort there.
        List<TimeSlotRef> active = timeSlotRepository
                .findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(tenantId)
                .stream()
                .map(this::toTimeSlotRef)
                .filter(TimeSlotRef::isActive)
                .toList();

        // Compute, per work block (same day + period), how many contiguous slots
        // remain from each slot to the block's end. A multi-slot session may not
        // exceed this without crossing the break or the day boundary.
        Map<String, List<TimeSlotRef>> byBlock = active.stream()
                .collect(Collectors.groupingBy(ts -> ts.getDay() + "|" + ts.getPeriod()));
        for (List<TimeSlotRef> block : byBlock.values()) {
            block.sort(Comparator.comparing(TimeSlotRef::getStartTime));
            int size = block.size();
            for (int i = 0; i < size; i++) {
                block.get(i).setMaxDurationSlots(size - i);
            }
        }
        return active;
    }

    private List<TeacherRef> buildTeachers(String tenantId) {
        return teacherRepository.findByTenantId(tenantId)
                .stream()
                .filter(t -> Boolean.TRUE.equals(t.getEstEnPoste()))
                .map(this::toTeacherRef)
                .toList();
    }

    private List<RoomRef> buildRooms(String tenantId) {
        return roomRepository.findByTenantId(tenantId)
                .stream()
                .filter(r -> r.getCapacite() != null && r.getCapacite() > 0)
                .filter(r -> !Boolean.FALSE.equals(r.getEstDisponible()))
                .map(this::toRoomRef)
                .toList();
    }

    // ── org-module → ref mappers ──────────────────────────────────────────────

    private TimeSlotRef toTimeSlotRef(TimeSlot ts) {
        return TimeSlotRef.builder()
                .id(ts.getIdTimeSlot())
                .day(ts.getDayOfWeek())
                .startTime(ts.getStartTime())
                .endTime(ts.getEndTime())
                .orderIndex(ts.getOrderIndex())
                .active(true)
                .period(ts.getDayPeriod())
                .build();
    }

    private TeacherRef toTeacherRef(Teacher t) {
        String name = ((t.getPrenom() != null ? t.getPrenom() : "") + " "
                + (t.getNom() != null ? t.getNom() : "")).trim();
        int maxHours = t.getMaxHeuresJour() != null ? t.getMaxHeuresJour() : 6;
        return TeacherRef.builder()
                .id(t.getIdEnseignant())
                .code(t.getCodeEnseignant())
                .name(name)
                .maxHoursPerDay(maxHours)
                .unavailableDays(Collections.emptySet()) // expanded in future (teacher schedule)
                .build();
    }

    private RoomRef toRoomRef(Room r) {
        RoomType type = mapRoomType(r.getTypeSalle());
        return RoomRef.builder()
                .id(r.getIdSalle())
                .code(r.getCodeSalle())
                .type(type)
                .capacity(r.getCapacite() != null ? r.getCapacite() : 0)
                .build();
    }

    // ── utilities ─────────────────────────────────────────────────────────────

    private ConstraintProfile resolveProfile(String tenantId, Long academicYearId, Long profileId) {
        if (profileId != null) {
            return constraintProfileRepository
                    .findByIdConstraintProfileAndTenantId(profileId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Profil de contraintes introuvable avec l'ID : " + profileId));
        }
        return constraintProfileRepository
                .findFirstByTenantIdAndAcademicYearIdAndActiveTrueOrderByIdConstraintProfileDesc(
                        tenantId, academicYearId)
                .or(() -> constraintProfileRepository
                        .findFirstByTenantIdAndActiveTrueOrderByIdConstraintProfileDesc(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun profil de contraintes actif pour ce tenant"));
    }

    static RoomType mapRoomType(tn.wtm.school.org.enums.RoomType orgType) {
        if (orgType == null) return RoomType.NORMALE;
        return switch (orgType) {
            case LABSCIENCE      -> RoomType.LABSCIENCE;
            case LABPHYSIQUE     -> RoomType.LABPHYSIQUE;
            case LABINFORMATIQUE -> RoomType.LABINFORMATIQUE;
            case SALLESPORT      -> RoomType.SALLESPORT;
            case SALLEDESSIN     -> RoomType.SALLEDESSIN;
            case SALLEMUSIQUE    -> RoomType.SALLEMUSIQUE;
            case AMPHI           -> RoomType.AMPHI;
            case BIBLIOTHEQUE    -> RoomType.BIBLIOTHEQUE;
            default              -> RoomType.NORMALE;
        };
    }
}
