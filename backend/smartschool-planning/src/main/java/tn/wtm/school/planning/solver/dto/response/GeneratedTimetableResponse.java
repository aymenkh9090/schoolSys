package tn.wtm.school.planning.solver.dto.response;

import lombok.*;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GeneratedTimetableResponse {

    private Long   id;
    private Long   jobId;
    private Long   academicYearId;
    private Long   constraintProfileId;

    private String  scoreAchieved;
    private boolean feasible;

    private int    totalSessions;
    private int    hardViolations;
    private int    mediumViolations;

    /** DRAFT | PUBLISHED | ARCHIVED */
    private String  status;
    private Instant publishedAt;
}
