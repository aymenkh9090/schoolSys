package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.PatternDetailRequest;
import tn.wtm.school.org.dto.response.PatternDetailResponse;
import tn.wtm.school.org.service.PatternService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/patterns/{patternId}/details")
@RequiredArgsConstructor
@Tag(name = "Détails de pattern", description = "Gestion des séances dans un pattern")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class PatternDetailController {

    private final PatternService patternService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<PatternDetailResponse> addDetail(@PathVariable Long patternId,
                                                           @Valid @RequestBody PatternDetailRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patternService.addDetail(patternId, request));
    }

    @GetMapping
    public ResponseEntity<List<PatternDetailResponse>> getDetails(@PathVariable Long patternId) {
        return ResponseEntity.ok(patternService.getDetailsByPattern(patternId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatternDetailResponse> getById(@PathVariable Long patternId,
                                                         @PathVariable Long id) {
        return ResponseEntity.ok(patternService.getDetailById(id));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<PatternDetailResponse> update(@PathVariable Long patternId,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody PatternDetailRequest request) {
        return ResponseEntity.ok(patternService.updateDetail(id, request));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long patternId, @PathVariable Long id) {
        patternService.deleteDetail(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/reorder")
    public ResponseEntity<List<PatternDetailResponse>> reorder(@PathVariable Long patternId,
                                                               @RequestBody List<Long> orderedIds) {
        return ResponseEntity.ok(patternService.reorderDetails(patternId, orderedIds));
    }
}
