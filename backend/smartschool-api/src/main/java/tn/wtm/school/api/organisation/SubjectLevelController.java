package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.SubjectLevelRequest;
import tn.wtm.school.org.dto.request.SubjectSessionTypeRequest;
import tn.wtm.school.org.dto.response.SubjectLevelResponse;
import tn.wtm.school.org.dto.response.SubjectSessionTypeResponse;
import tn.wtm.school.org.service.SubjectService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/subject-levels")
@RequiredArgsConstructor
@Tag(name = "Matières-Niveaux", description = "Association matières et niveaux")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class SubjectLevelController {

    private final SubjectService subjectService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<SubjectLevelResponse> create(@Valid @RequestBody SubjectLevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.createSubjectLevel(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectLevelResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.getSubjectLevelById(id));
    }

    @GetMapping("/by-level/{levelId}")
    public ResponseEntity<List<SubjectLevelResponse>> getByLevel(@PathVariable Long levelId) {
        return ResponseEntity.ok(subjectService.getSubjectLevelsByLevelWithSessionTypes(levelId));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<SubjectLevelResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody SubjectLevelRequest request) {
        return ResponseEntity.ok(subjectService.updateSubjectLevel(id, request));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.deleteSubjectLevel(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/session-types")
    public ResponseEntity<SubjectSessionTypeResponse> createSessionType(
            @Valid @RequestBody SubjectSessionTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subjectService.createSessionType(request));
    }
}
