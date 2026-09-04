package tn.wtm.school.org.dto.response;

import tn.wtm.school.org.enums.DayPeriod;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record TimeSlotResponseDTO(
        Long id,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer orderIndex,
        DayPeriod dayPeriod
) {}