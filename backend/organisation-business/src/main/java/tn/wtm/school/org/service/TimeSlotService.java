package tn.wtm.school.org.service;

import tn.wtm.school.org.dto.response.TimeSlotResponseDTO;
import tn.wtm.school.org.entity.SchoolWorkingDay;

import java.time.DayOfWeek;
import java.util.List;

public interface TimeSlotService {

    List<TimeSlotResponseDTO> listTimeSlots();

    List<TimeSlotResponseDTO> listTimeSlotsByDay(DayOfWeek day);

    List<TimeSlotResponseDTO> generate(List<SchoolWorkingDay> workingDays, int durationMinutes);
}
