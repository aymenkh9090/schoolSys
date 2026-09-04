package tn.wtm.school.planning.solver.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.planning.solver.domain.TimetableSession;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimetableSessionRepository extends TenantAwareRepository<TimetableSession, Long> {

    Optional<TimetableSession> findByIdTimetableSessionAndTenantId(Long idTimetableSession, String tenantId);

    List<TimetableSession> findByJobIdAndTenantId(Long jobId, String tenantId);

    List<TimetableSession> findByJobIdAndTenantIdAndStudentClassName(
            Long jobId, String tenantId, String studentClassName);

    List<TimetableSession> findByJobIdAndTenantIdAndTeacherCode(
            Long jobId, String tenantId, String teacherCode);

    List<TimetableSession> findByJobIdAndTenantIdAndRoomCode(
            Long jobId, String tenantId, String roomCode);

    @Query("""
            SELECT DISTINCT s.teacherCode FROM TimetableSession s
            WHERE s.jobId = :jobId AND s.tenantId = :tenantId AND s.day = :day
              AND s.teacherCode IS NOT NULL
            """)
    List<String> findDistinctTeacherCodesByJobIdAndTenantIdAndDay(
            @Param("jobId") Long jobId, @Param("tenantId") String tenantId, @Param("day") DayOfWeek day);

    /**
     * Variante matin/après-midi : ne retient que les séances dont l'heure de
     * début tombe dans la plage horaire de la période demandée (bornes
     * calculées à partir des créneaux configurés pour ce jour/période).
     */
    @Query("""
            SELECT DISTINCT s.teacherCode FROM TimetableSession s
            WHERE s.jobId = :jobId AND s.tenantId = :tenantId AND s.day = :day
              AND s.teacherCode IS NOT NULL
              AND s.startTime >= :debut AND s.startTime < :fin
            """)
    List<String> findDistinctTeacherCodesByJobIdAndTenantIdAndDayAndStartTimeBetween(
            @Param("jobId") Long jobId, @Param("tenantId") String tenantId, @Param("day") DayOfWeek day,
            @Param("debut") LocalTime debut, @Param("fin") LocalTime fin);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM planning_timetable_session WHERE job_id = :jobId AND tenant_id = :tenantId",
           nativeQuery = true)
    void deleteByJobIdAndTenantId(@Param("jobId") Long jobId, @Param("tenantId") String tenantId);
}
