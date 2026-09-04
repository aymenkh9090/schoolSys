package tn.wtm.school.org.service.impl;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.org.repository.TimeSlotRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TimeSlotGenerationServiceImpl extends TenantService {



    private final TimeSlotRepository timeSlotRepo;

    /**
     * Génère et sauvegarde les créneaux pour UN jour
     */
    public List<TimeSlot> generateForDay(
            SchoolWorkingDay workingDay,
            int durationMinutes) {

        String tenantId = currentTenant();

        if (workingDay == null || workingDay.getDayOfWeek() == null) {
            return List.of();
        }

        timeSlotRepo.deleteByTenantIdAndDayOfWeek(tenantId, workingDay.getDayOfWeek());

        if (Boolean.FALSE.equals(workingDay.getActive())) {
            return List.of();
        }

        List<TimeSlot> slots = new ArrayList<>();

        if (hasValidPeriod(workingDay.getMorningStart(), workingDay.getMorningEnd())) {
            slots.addAll(buildPeriodSlots(
                    workingDay.getDayOfWeek(),
                    workingDay.getMorningStart(),
                    workingDay.getMorningEnd(),
                    DayPeriod.MORNING,
                    durationMinutes
            ));
        }

        // Après-midi — optionnel
        if (hasAfternoon(workingDay)) {
            slots.addAll(buildPeriodSlots(
                    workingDay.getDayOfWeek(),
                    workingDay.getAfternoonStart(),
                    workingDay.getAfternoonEnd(),
                    DayPeriod.AFTERNOON,
                    durationMinutes
            ));
        }

        // orderIndex continu sur la journée: 0,1,2,3...
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setOrderIndex(i);
        }

        return timeSlotRepo.saveAll(slots);
    }

    /**
     * Génère les créneaux pour TOUS les jours
     */
    public List<TimeSlot> generateForAllDays(
            List<SchoolWorkingDay> workingDays,
            int durationMinutes) {

        return workingDays.stream()
                .flatMap(wd -> generateForDay(wd, durationMinutes).stream())
                .toList();
    }

    // ── Private ──────────────────────────────────────────────

    private List<TimeSlot> buildPeriodSlots(
            DayOfWeek day,
            LocalTime start,
            LocalTime end,
            DayPeriod period,
            int durationMinutes) {

        List<TimeSlot> slots = new ArrayList<>();
        LocalTime current = start;

        while (!current.plusMinutes(durationMinutes).isAfter(end)) {
            slots.add(TimeSlot.builder()
                    .dayOfWeek(day)
                    .startTime(current)
                    .endTime(current.plusMinutes(durationMinutes))
                    .dayPeriod(period)
                    .build()
            );
            current = current.plusMinutes(durationMinutes);
        }

        return slots;
    }

    private boolean hasAfternoon(SchoolWorkingDay wd) {
        return hasValidPeriod(wd.getAfternoonStart(), wd.getAfternoonEnd());
    }

    private boolean hasValidPeriod(LocalTime start, LocalTime end) {
        return start != null && end != null && start.isBefore(end);
    }




}
