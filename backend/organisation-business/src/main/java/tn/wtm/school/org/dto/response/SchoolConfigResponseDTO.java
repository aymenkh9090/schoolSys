package tn.wtm.school.org.dto.response;

import java.util.List;

public record SchoolConfigResponseDTO(
        List<WorkingDayResponseDTO> workingDays,
        List<TimeSlotResponseDTO> timeSlots,
        Integer                     totalSlotsPerWeek,
        Boolean                     isReadyForGeneration
) {}
