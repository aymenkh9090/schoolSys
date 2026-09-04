package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.response.TimeSlotResponseDTO;
import tn.wtm.school.org.service.TimeSlotService;

import java.time.DayOfWeek;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/time-slots")
@RequiredArgsConstructor
@Tag(name = "Créneaux horaires", description = "Consultation des créneaux horaires")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    @GetMapping
    public ResponseEntity<List<TimeSlotResponseDTO>> listAll() {
        return ResponseEntity.ok(timeSlotService.listTimeSlots());
    }

    @GetMapping("/day/{day}")
    public ResponseEntity<List<TimeSlotResponseDTO>> listByDay(@PathVariable DayOfWeek day) {
        return ResponseEntity.ok(timeSlotService.listTimeSlotsByDay(day));
    }
}
