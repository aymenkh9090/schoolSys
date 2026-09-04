package tn.wtm.school.org.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.org.dto.response.TimeSlotResponseDTO;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.mapper.TimeSlotMapper;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.org.service.TimeSlotService;

import java.time.DayOfWeek;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeSlotServiceImpl extends TenantService implements TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final TimeSlotGenerationServiceImpl generator;
    private final TimeSlotMapper mapper;

    @Override
    public List<TimeSlotResponseDTO> listTimeSlots() {
        return mapper.toResponseList(timeSlotRepository.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(currentTenant()));
    }

    @Override
    public List<TimeSlotResponseDTO> listTimeSlotsByDay(DayOfWeek day) {
        return mapper.toResponseList(timeSlotRepository.findByTenantIdAndDayOfWeekOrderByOrderIndexAsc(currentTenant(), day));
    }

    @Override
    @Transactional
    public List<TimeSlotResponseDTO> generate(List<SchoolWorkingDay> workingDays, int durationMinutes) {
        return mapper.toResponseList(generator.generateForAllDays(workingDays, durationMinutes));
    }
}
