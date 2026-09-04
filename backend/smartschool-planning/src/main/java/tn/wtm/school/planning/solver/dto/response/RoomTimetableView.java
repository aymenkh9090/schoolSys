package tn.wtm.school.planning.solver.dto.response;

import tn.wtm.school.planning.solver.enums.SolverStatus;

import java.time.DayOfWeek;
import java.util.List;

public record RoomTimetableView(
        Long jobId,
        String roomCode,
        String roomType,
        SolverStatus status,
        String scoreAchieved,
        List<DaySchedule> schedule
) {
    public record DaySchedule(
            DayOfWeek day,
            String dayLabel,
            List<SessionView> sessions
    ) {}

    public record SessionView(
            Long id,
            String startTime,
            String endTime,
            String duration,
            String subjectCode,
            String subjectName,
            String classCode,
            String teacherCode,
            String teacherName,
            String sessionType,
            int groupIndex,
            String groupLabel
    ) {}
}
