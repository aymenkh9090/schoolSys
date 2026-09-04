package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.PatternRequest;
import tn.wtm.school.org.dto.response.PatternResponse;
import tn.wtm.school.org.service.PatternService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/patterns")
@RequiredArgsConstructor
@Tag(name = "Patterns de séances", description = "Gestion des patterns horaires")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class PatternController {

    private final PatternService patternService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<PatternResponse> create(@Valid @RequestBody PatternRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patternService.createPattern(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatternResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(patternService.getPatternById(id));
    }

    // `/{id}/details` appartient a PatternDetailController (collection des details) :
    // les deux mappings se marchaient dessus et tout GET sur ce chemin repondait
    // « Ambiguous handler methods ». Ici on renvoie le pattern AVEC ses details.
    @GetMapping("/{id}/with-details")
    public ResponseEntity<PatternResponse> getWithDetails(@PathVariable Long id) {
        return ResponseEntity.ok(patternService.getPatternWithDetails(id));
    }

    @GetMapping("/by-subject-level/{subjectLevelId}")
    public ResponseEntity<List<PatternResponse>> getBySubjectLevel(@PathVariable Long subjectLevelId) {
        return ResponseEntity.ok(patternService.getPatternsBySubjectLevel(subjectLevelId));
    }

    @GetMapping("/by-subject-level/{subjectLevelId}/paged")
    public ResponseEntity<Page<PatternResponse>> getBySubjectLevelPaged(@PathVariable Long subjectLevelId,
                                                                         Pageable pageable) {
        return ResponseEntity.ok(patternService.getPatternsBySubjectLevelPaginated(subjectLevelId, pageable));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<PatternResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody PatternRequest request) {
        return ResponseEntity.ok(patternService.updatePattern(id, request));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patternService.deletePattern(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<PatternResponse> toggleStatus(@PathVariable Long id,
                                                          @RequestParam Boolean active) {
        return ResponseEntity.ok(patternService.togglePatternActive(id, active));
    }

    @GetMapping("/inconsistants")
    public ResponseEntity<List<PatternResponse>> getInconsistants() {
        return ResponseEntity.ok(patternService.findInconsistentPatterns());
    }
}
