package tn.wtm.school.planning.solver.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Persisted result of a solved Lesson placement.
 * One row per lesson once the solver has produced a valid timetable.
 * The Liquibase migration (006-create-timetable-session.yaml) is added in Step 6.
 */
@Entity
@Table(
        name = "planning_timetable_session",
        indexes = {
                @Index(name = "IDX_SESSION_TENANT",     columnList = "tenant_id"),
                @Index(name = "IDX_SESSION_JOB",        columnList = "tenant_id,job_id"),
                @Index(name = "IDX_SESSION_CLASS_DAY",  columnList = "tenant_id,student_class_name,day")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class TimetableSession extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_seq")
    @SequenceGenerator(name = "planning_seq", sequenceName = "SEQ_PLANNING", allocationSize = 1)
    @Column(name = "id_timetable_session")
    private Long idTimetableSession;

    /** FK to the TimetableJob that produced this session. */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    /** Original TeachingAssignment.id — traceability back to org-module. */
    @Column(name = "teaching_assignment_id")
    private Long teachingAssignmentId;

    /**
     * Identifiant de la {@link Lesson} dont cette ligne est le résultat — le
     * {@code @PlanningId} vu par Timefold.
     *
     * <h4>Pourquoi cette colonne existe</h4>
     *
     * Timefold explique un score en désignant des objets : un conflit
     * d'enseignant incrimine deux {@code Lesson}. Sans ce report, rien ne
     * reliait ces objets aux lignes de cette table — l'explication ne pouvait
     * donc être qu'une phrase (« Maths · 7A · Ahmed · lundi 08:00 »), jamais un
     * identifiant. L'interface ne pouvait pas surligner la case fautive, et une
     * suggestion de correction ne pouvait pas s'accrocher au
     * {@code PATCH /jobs/{id}/sessions/{sessionId}} qui sait pourtant déplacer
     * cette séance.
     *
     * <p><b>Portée de l'identifiant.</b> Il vient d'un compteur remis à zéro à
     * chaque construction du problème ({@code LessonGenerator}) : il n'est
     * unique qu'à l'intérieur d'un job. La clé de rapprochement est donc le
     * couple {@code (jobId, lessonId)}, jamais {@code lessonId} seul.
     *
     * <p>Nullable : les lignes produites avant cette colonne n'en portent pas,
     * et l'explication doit rester lisible sur ces jobs-là — sans identifiant,
     * simplement.
     */
    @Column(name = "lesson_id")
    private Long lessonId;

    // ── snapshot fields — denormalised for fast grid rendering ────────────────

    @Column(name = "subject_code", length = 30)
    private String subjectCode;

    @Column(name = "subject_name", length = 100)
    private String subjectName;

    @Column(name = "student_class_name", length = 30)
    private String studentClassName;

    @Column(name = "teacher_code", length = 20)
    private String teacherCode;

    @Column(name = "teacher_name", length = 120)
    private String teacherName;

    @Column(name = "room_code", length = 20)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", length = 20)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false, length = 15)
    private DayOfWeek day;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", length = 20)
    private SessionType sessionType;

    /**
     * 0 = full class, 1 = demi-group A, 2 = demi-group B.
     * Mirrors Lesson.groupIndex.
     */
    @Column(name = "group_index", nullable = false)
    @Builder.Default
    private int groupIndex = 0;
}
