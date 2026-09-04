package tn.wtm.school.org.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.org.dto.request.SchoolConfigRequestDTO;
import tn.wtm.school.org.dto.request.WorkingDayRequestDTO;
import tn.wtm.school.org.dto.response.SchoolConfigResponseDTO;
import tn.wtm.school.org.dto.response.TimeSlotResponseDTO;
import tn.wtm.school.org.dto.response.WorkingDayResponseDTO;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.mapper.TimeSlotMapper;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.org.repository.WorkingDayRepository;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolConfigurationService extends TenantService {

    public static final int DEFAULT_SLOT_DURATION_MINUTES = 30;

    private final WorkingDayRepository workingDayRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final TimeSlotGenerationServiceImpl slotGenerator;
    private final TimeSlotMapper timeSlotMapper;

    // ── CONFIGURE TOUT ────────────────────────────────────────
    public SchoolConfigResponseDTO configure(SchoolConfigRequestDTO dto) {

        String tenantId = currentTenant();
        validate(dto);

        // 1. Reset complet
        timeSlotRepo.deleteByTenantId(tenantId);
        workingDayRepo.deleteByTenantId(tenantId);
        // Flush obligatoire : Hibernate exécute les INSERT avant les DELETE dans un même
        // flush, ce qui viole la contrainte unique (tenant_id, day_of_week) lors du re-save.
        workingDayRepo.flush();
        timeSlotRepo.flush();

        // 2. Pour chaque jour → save + générer créneaux
        List<SchoolWorkingDay> savedDays = new ArrayList<>();

        for (WorkingDayRequestDTO dayDTO : dto.workingDays()) {

            SchoolWorkingDay wd = workingDayRepo.save(
                    SchoolWorkingDay.builder()
                            .dayOfWeek(dayDTO.dayOfWeek())
                            .morningStart(dayDTO.morningStart())
                            .morningEnd(dayDTO.morningEnd())
                            .afternoonStart(dayDTO.afternoonStart())
                            .afternoonEnd(dayDTO.afternoonEnd())
                            .active(dayDTO.active() == null || dayDTO.active())
                            .build()
            );

            savedDays.add(wd);
        }

        // 3. Générer tous les créneaux
        slotGenerator.generateForAllDays(savedDays, dto.slotDurationMinutes());

        return buildResponse();
    }

    // ── ACTIVER / DÉSACTIVER UN JOUR ──────────────────────────
    public SchoolConfigResponseDTO toggleDay(DayOfWeek day, boolean active) {
        String tenantId = currentTenant();
        SchoolWorkingDay wd = workingDayRepo.findByTenantIdAndDayOfWeek(tenantId, day)
                .orElseThrow(()-> new ResourceNotFoundException("Day " + day.name()+ " not found"));

        wd.setActive(active);
        workingDayRepo.save(wd);

        if (active) {
            slotGenerator.generateForDay(wd, DEFAULT_SLOT_DURATION_MINUTES);
        } else {
            timeSlotRepo.deleteByTenantIdAndDayOfWeek(tenantId, day);
        }

        return buildResponse();
    }

    // ── READ ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public SchoolConfigResponseDTO getConfiguration() {
        return buildResponse();
    }

    // ── VALIDATE ──────────────────────────────────────────────
    private void validate(SchoolConfigRequestDTO dto) {
        if (dto == null) {
            throw new BadRequestException("La configuration est obligatoire");
        }
        if (dto.slotDurationMinutes() == null || dto.slotDurationMinutes() < DEFAULT_SLOT_DURATION_MINUTES) {
            throw new BadRequestException("La durée des créneaux doit être d'au moins " + DEFAULT_SLOT_DURATION_MINUTES + " minutes");
        }

        long distinct = dto.workingDays().stream()
                .map(WorkingDayRequestDTO::dayOfWeek)
                .distinct().count();
        if (distinct != dto.workingDays().size()) {
            throw new BadRequestException("Les jours de travail ne peuvent pas contenir de doublons");
        }

        dto.workingDays().forEach(day -> {

            if (!day.morningStart().isBefore(day.morningEnd())) {
                throw new BadRequestException("L'heure de début du matin doit être avant la fin pour " + day.dayOfWeek());
            }

            if (day.afternoonStart() != null && day.afternoonEnd() != null) {

                if (!day.afternoonStart().isBefore(day.afternoonEnd())) {
                    throw new BadRequestException("L'heure de début de l'après-midi doit être avant la fin pour " + day.dayOfWeek());
                }

                if (!day.morningEnd().isBefore(day.afternoonStart())) {
                    throw new BadRequestException("La pause entre matin et après-midi est invalide pour " + day.dayOfWeek());
                }
            }

            if ((day.afternoonStart() == null) != (day.afternoonEnd() == null)) {
                throw new BadRequestException("Les heures de début et de fin de l'après-midi sont toutes les deux obligatoires pour " + day.dayOfWeek());
            }
        });
    }

    // ── BUILD RESPONSE ────────────────────────────────────────
    private SchoolConfigResponseDTO buildResponse() {

        String tenantId = currentTenant();
        List<SchoolWorkingDay> days  = workingDayRepo.findByTenantIdOrderByDayOfWeekAsc(tenantId);
        List<TimeSlot>         slots = timeSlotRepo.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc(tenantId);

        List<WorkingDayResponseDTO> dayDTOs = days.stream()
                .map(d -> new WorkingDayResponseDTO(
                        d.getSchoolWorkingDayId(),
                        d.getDayOfWeek(),
                        d.getMorningStart(),
                        d.getMorningEnd(),
                        d.getAfternoonStart(),
                        d.getAfternoonEnd(),
                        d.getActive(),
                        (int) slots.stream()
                                .filter(s -> s.getDayOfWeek() == d.getDayOfWeek())
                                .count()
                ))
                .toList();

        List<TimeSlotResponseDTO> slotDTOs = timeSlotMapper.toResponseList(slots);

        return new SchoolConfigResponseDTO(
                dayDTOs,
                slotDTOs,
                slots.size(),
                !days.isEmpty() && !slots.isEmpty()
        );
    }
}
