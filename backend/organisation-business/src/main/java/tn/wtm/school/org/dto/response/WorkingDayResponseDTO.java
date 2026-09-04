package tn.wtm.school.org.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingDayResponseDTO(
        Long id,
        DayOfWeek dayOfWeek,
        LocalTime morningStart,
        LocalTime morningEnd,
        LocalTime afternoonStart,
        LocalTime afternoonEnd,
        Boolean active,
        Integer slotsCount
) {}