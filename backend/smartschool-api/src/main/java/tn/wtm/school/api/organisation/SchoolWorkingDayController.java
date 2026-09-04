package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.SchoolConfigRequestDTO;
import tn.wtm.school.org.dto.response.SchoolConfigResponseDTO;
import tn.wtm.school.org.service.impl.SchoolConfigurationService;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/working-days")
@RequiredArgsConstructor
@Tag(name = "Jours ouvrables", description = "Alias vers la configuration des jours ouvrables")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolWorkingDayController {

    private final SchoolConfigurationService schoolConfigurationService;

    @PostMapping("/configure")
    public ResponseEntity<SchoolConfigResponseDTO> configure(@Valid @RequestBody SchoolConfigRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolConfigurationService.configure(request));
    }

    @GetMapping
    public ResponseEntity<SchoolConfigResponseDTO> get() {
        return ResponseEntity.ok(schoolConfigurationService.getConfiguration());
    }
}
