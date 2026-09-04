package tn.wtm.school.planning.solver.dto.response;

import lombok.*;
import tn.wtm.school.planning.solver.enums.SolverStatus;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TimetableSolutionResponse {

    private Long jobId;
    private Long academicYearId;
    private SolverStatus status;
    private String scoreAchieved;
    private int totalSessions;
    private List<TimetableSessionResponse> sessions;
}
