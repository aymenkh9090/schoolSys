package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.ApplyNationalPatternRequest;
import tn.wtm.school.org.dto.request.CustomizePatternRequest;
import tn.wtm.school.org.dto.request.SchoolConfigRequestDTO;
import tn.wtm.school.org.dto.response.PatternResponse;
import tn.wtm.school.org.dto.response.SchoolConfigResponseDTO;
import tn.wtm.school.org.service.NationalPatternService;
import tn.wtm.school.org.service.NationalPatternService.ApplyNationalResult;
import tn.wtm.school.org.service.PatternService;
import tn.wtm.school.org.service.impl.SchoolConfigurationService;

import java.time.DayOfWeek;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/school-config")
@RequiredArgsConstructor
@Tag(name = "Configuration école", description = "Configuration des jours ouvrables et créneaux")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolConfigController {

    private final SchoolConfigurationService schoolConfigurationService;
    private final NationalPatternService nationalPatternService;
    private final PatternService patternService;

    @PostMapping
    public ResponseEntity<SchoolConfigResponseDTO> configure(@Valid @RequestBody SchoolConfigRequestDTO request) {
        return ResponseEntity.ok(schoolConfigurationService.configure(request));
    }

    @GetMapping
    public ResponseEntity<SchoolConfigResponseDTO> getConfiguration() {
        return ResponseEntity.ok(schoolConfigurationService.getConfiguration());
    }

    @PatchMapping("/days/{day}/toggle")
    public ResponseEntity<SchoolConfigResponseDTO> toggleDay(@PathVariable DayOfWeek day,
                                                             @RequestParam boolean active) {
        return ResponseEntity.ok(schoolConfigurationService.toggleDay(day, active));
    }

    @PostMapping("/apply-national")
    public ResponseEntity<ApplyNationalResult> applyNationalPattern(
            @Valid @RequestBody ApplyNationalPatternRequest request) {
        return ResponseEntity.ok(nationalPatternService.applyByRequest(request));
    }

    @GetMapping("/patterns/{levelCode}")
    public ResponseEntity<List<PatternResponse>> getPatternsByLevel(@PathVariable String levelCode) {
        return ResponseEntity.ok(patternService.getPatternsByLevelCode(levelCode));
    }

    @PutMapping("/patterns/{levelCode}/{subjectCode}")
    public ResponseEntity<PatternResponse> customizePattern(
            @PathVariable String levelCode,
            @PathVariable String subjectCode,
            @Valid @RequestBody CustomizePatternRequest request) {
        return ResponseEntity.ok(patternService.customizePattern(levelCode, subjectCode, request));
    }
}
