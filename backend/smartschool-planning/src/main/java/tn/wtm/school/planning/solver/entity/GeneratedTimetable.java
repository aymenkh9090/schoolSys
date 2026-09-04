package tn.wtm.school.planning.solver.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;

import java.time.Instant;

/**
 * Résultat global d'un emploi du temps produit par le solver.
 *
 * Un enregistrement est créé (ou mis à jour) lorsque {@code TimetableSolverService}
 * finalise un job avec le statut {@link SolverStatus#SOLVED}.
 * Il agrège le score Timefold, les compteurs de violations et l'état de publication,
 * évitant de recalculer ces valeurs à chaque consultation.
 *
 * Relation : un TimetableJob → 0..1 GeneratedTimetable
 * (contrainte unique {@code uk_generated_timetable_job} en base).
 */
@Entity
@Table(
        name = "planning_generated_timetable",
        indexes = {
                @Index(name = "IDX_GENERATED_TIMETABLE_TENANT",  columnList = "tenant_id"),
                @Index(name = "IDX_GENERATED_TIMETABLE_YEAR",    columnList = "tenant_id,academic_year_id"),
                @Index(name = "IDX_GENERATED_TIMETABLE_STATUS",  columnList = "tenant_id,status")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class GeneratedTimetable extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_seq")
    @SequenceGenerator(name = "planning_seq", sequenceName = "SEQ_PLANNING", allocationSize = 1)
    @Column(name = "id_generated_timetable")
    private Long idGeneratedTimetable;

    // ── liens ─────────────────────────────────────────────────────────────────

    /** FK vers le TimetableJob source (unique — un résultat par job). */
    @Column(name = "job_id", nullable = false, unique = true)
    private Long jobId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "constraint_profile_id")
    private Long constraintProfileId;

    // ── score Timefold ────────────────────────────────────────────────────────

    /** Score textuel Timefold, ex. {@code "0hard/0medium/-5soft"}. */
    @Column(name = "score_achieved", length = 60)
    private String scoreAchieved;

    /** {@code true} si le score ne contient aucune violation hard (solution valide). */
    @Column(name = "feasible", nullable = false)
    @Builder.Default
    private boolean feasible = false;

    // ── statistiques snapshot ─────────────────────────────────────────────────

    @Column(name = "total_sessions", nullable = false)
    @Builder.Default
    private int totalSessions = 0;

    @Column(name = "hard_violations", nullable = false)
    @Builder.Default
    private int hardViolations = 0;

    @Column(name = "medium_violations", nullable = false)
    @Builder.Default
    private int mediumViolations = 0;

    // ── cycle de vie publication ──────────────────────────────────────────────

    /**
     * État de publication.
     * <ul>
     *   <li>{@code DRAFT}     — résultat interne, non visible par les enseignants</li>
     *   <li>{@code PUBLISHED} — diffusé, visible via l'API publique</li>
     *   <li>{@code ARCHIVED}  — remplacé par un emploi du temps plus récent</li>
     * </ul>
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    /** Horodatage de la dernière publication. {@code null} si jamais publié. */
    @Column(name = "published_at")
    private Instant publishedAt;
}
