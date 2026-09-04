package tn.wtm.school.org.repository;


import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.SchoolWorkingDay;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface WorkingDayRepository extends TenantAwareRepository<SchoolWorkingDay,Long> {


    // Hibernate Filter applique tenantId auto
    List<SchoolWorkingDay> findAll();

    List<SchoolWorkingDay> findByTenantIdOrderByDayOfWeekAsc(String tenantId);

    List<SchoolWorkingDay> findByActive(Boolean active);

    List<SchoolWorkingDay> findByTenantIdAndActive(String tenantId, Boolean active);

    Optional<SchoolWorkingDay> findByDayOfWeek(DayOfWeek day);

    Optional<SchoolWorkingDay> findByTenantIdAndDayOfWeek(String tenantId, DayOfWeek day);

    boolean existsByDayOfWeek(DayOfWeek day);

    boolean existsByTenantIdAndDayOfWeek(String tenantId, DayOfWeek day);

    void deleteByDayOfWeek(DayOfWeek day);

    void deleteByTenantId(String tenantId);


}
