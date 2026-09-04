package tn.wtm.school.planning.solver.dto.response;

import lombok.*;
import tn.wtm.school.planning.solver.enums.SolverStatus;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TimetableJobResponse {

    private Long jobId;
    private Long schoolYearId;
    private Long constraintProfileId;
    private SolverStatus status;
    private String scoreAchieved;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
}
