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

    /**
     * Ce que la validation métier reproche à cet emploi du temps, en français.
     *
     * <p>Renseigné dès que la validation a trouvé quelque chose — y compris sur
     * un job {@code SOLVED}, qui peut porter des avertissements sans être
     * refusé. {@code null} quand tout est conforme.
     */
    private String validationReport;
}
