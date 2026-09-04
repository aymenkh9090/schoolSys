package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSlotRepository extends TenantAwareRepository<TimeSlot, Long> {


    // Hibernate Filter applique tenantId auto
    List<TimeSlot> findAllByOrderByDayOfWeekAscOrderIndexAsc();

    List<TimeSlot> findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(String tenantId);

    List<TimeSlot> findByDayOfWeek(DayOfWeek day);

    List<TimeSlot> findByTenantIdAndDayOfWeekOrderByOrderIndexAsc(String tenantId, DayOfWeek day);

    List<TimeSlot> findByDayOfWeekAndDayPeriod(DayOfWeek day, DayPeriod period);

    void deleteByDayOfWeek(DayOfWeek day);

    void deleteByTenantId(String tenantId);

    void deleteByTenantIdAndDayOfWeek(String tenantId, DayOfWeek day);

    // Pour vérifier chevauchement
    boolean existsByDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
            DayOfWeek day,
            LocalTime endTime,
            LocalTime startTime
    );

    boolean existsByTenantIdAndDayOfWeekAndStartTimeAndEndTime(
            String tenantId,
            DayOfWeek day,
            LocalTime startTime,
            LocalTime endTime
    );



}
