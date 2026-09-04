package tn.wtm.school.planning.solver.dto.response;

import lombok.*;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TimetableSessionResponse {

    private Long idTimetableSession;
    private String subjectCode;
    private String subjectName;
    private String studentClassName;
    private String teacherCode;
    private String teacherName;
    private String roomCode;
    private RoomType roomType;
    private DayOfWeek day;
    private LocalTime startTime;
    private LocalTime endTime;
    private SessionType sessionType;
    private int groupIndex;
}
