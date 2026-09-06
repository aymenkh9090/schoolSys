package tn.wtm.school.planning.solver.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.planning.solver.enums.SolverStatus;

import java.time.Instant;

@Entity
@Table(
        name = "planning_timetable_job",
        indexes = {
                @Index(name = "IDX_TIMETABLE_JOB_TENANT",      columnList = "tenant_id"),
                @Index(name = "IDX_TIMETABLE_JOB_YEAR_STATUS", columnList = "tenant_id,academic_year_id,status")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class TimetableJob extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_seq")
    @SequenceGenerator(name = "planning_seq", sequenceName = "SEQ_PLANNING", allocationSize = 1)
    @Column(name = "id_timetable_job")
    private Long idTimetableJob;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "constraint_profile_id")
    private Long constraintProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SolverStatus status = SolverStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Timefold HardMediumSoftScore as string, e.g. "0hard/0medium/-5soft". */
    @Column(name = "score_achieved", length = 60)
    private String scoreAchieved;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * Verdict de {@code TimetableBusinessValidator}, déjà résumé et lisible.
     *
     * <p>Distinct de {@link #errorMessage}, qui reste réservé aux plantages du
     * solveur : ici l'emploi du temps existe et il est consultable, c'est sa
     * conformité qui est en cause. Confondre les deux ferait afficher « la
     * génération a échoué » sur un planning parfaitement complet.
     *
     * <p>{@code null} quand la validation ne trouve rien à redire.
     */
    @Column(name = "validation_report", columnDefinition = "TEXT")
    private String validationReport;

    /** Timefold SolverManager problem ID — same as idTimetableJob but stored for auditability. */
    @Column(name = "solver_job_id", length = 50)
    private String solverJobId;
}
