package tn.wtm.school.api.organisation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.response.NationalPatternResponse;
import tn.wtm.school.org.service.NationalPatternService;
import tn.wtm.school.org.service.NationalPatternService.NationalPatternApplyResult;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/national-patterns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class NationalPatternController {

    private final NationalPatternService nationalPatternService;

    @GetMapping
    public ResponseEntity<List<NationalPatternResponse>> list(
            @RequestParam(defaultValue = "TN") String countryCode,
            @RequestParam(required = false) Integer academicYear) {
        return ResponseEntity.ok(nationalPatternService.findAllActive(countryCode, academicYear));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NationalPatternResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(nationalPatternService.findById(id));
    }

    @GetMapping("/patterns/{levelCode}")
    public ResponseEntity<NationalPatternResponse> getByLevelCode(
            @PathVariable String levelCode,
            @RequestParam(defaultValue = "TN") String countryCode) {
        return ResponseEntity.ok(nationalPatternService.findByLevelCode(countryCode, levelCode));
    }

    /**
     * Applique un national pattern au tenant courant.
     * Crée les Pattern + PatternDetail pour chaque matière du niveau.
     *
     * @param id          ID du national pattern
     * @param schoolYearId  ID de l'année scolaire (optionnel — null = pattern par défaut)
     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<NationalPatternApplyResult> apply(
            @PathVariable Long id,
            @RequestParam(required = false) Long schoolYearId) {
        return ResponseEntity.ok(nationalPatternService.applyToTenant(id, schoolYearId));
    }
}
