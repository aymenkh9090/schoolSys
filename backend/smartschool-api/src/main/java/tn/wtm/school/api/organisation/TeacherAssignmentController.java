package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.AssignSubjectRequest;
import tn.wtm.school.org.dto.request.TeachingAssignmentRequest;
import tn.wtm.school.org.dto.response.AssignSubjectResult;
import tn.wtm.school.org.dto.response.MissingAssignmentEntry;
import tn.wtm.school.org.dto.response.TeachingAssignmentResponse;
import tn.wtm.school.org.service.TeachingAssignmentService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/teaching-assignments")
@RequiredArgsConstructor
@Tag(name = "Affectations enseignants", description = "Gestion des affectations enseignant-classe-matière")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class TeacherAssignmentController {

    private final TeachingAssignmentService teachingAssignmentService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<TeachingAssignmentResponse> create(@Valid @RequestBody TeachingAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teachingAssignmentService.createTeachingAssignment(request));
    }

    @GetMapping("/missing")
    public ResponseEntity<List<MissingAssignmentEntry>> getMissing(
            @RequestParam Long schoolYearId) {
        return ResponseEntity.ok(teachingAssignmentService.getMissingAssignments(schoolYearId));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/assign-subject")
    public ResponseEntity<AssignSubjectResult> assignSubject(@Valid @RequestBody AssignSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teachingAssignmentService.assignSubjectToClasses(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeachingAssignmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(teachingAssignmentService.getTeachingAssignmentById(id));
    }

    @GetMapping("/{id}/full")
    public ResponseEntity<TeachingAssignmentResponse> getFullyLoaded(@PathVariable Long id) {
        return ResponseEntity.ok(teachingAssignmentService.getTeachingAssignmentFullyLoaded(id));
    }

    @GetMapping
    public ResponseEntity<Page<TeachingAssignmentResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(teachingAssignmentService.getAllTeachingAssignments(pageable));
    }

    @GetMapping("/by-teacher/{teacherId}")
    public ResponseEntity<List<TeachingAssignmentResponse>> getByTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teachingAssignmentService.getTeachingAssignmentsByTeacher(teacherId));
    }

    @GetMapping("/by-class/{classId}")
    public ResponseEntity<List<TeachingAssignmentResponse>> getByClass(@PathVariable Long classId) {
        return ResponseEntity.ok(teachingAssignmentService.getTeachingAssignmentsByClassGroup(classId));
    }

    @GetMapping("/by-year/{schoolYearId}")
    public ResponseEntity<List<TeachingAssignmentResponse>> getByYear(@PathVariable Long schoolYearId) {
        return ResponseEntity.ok(teachingAssignmentService.getTeachingAssignmentsBySchoolYear(schoolYearId));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TeachingAssignmentResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody TeachingAssignmentRequest request) {
        return ResponseEntity.ok(teachingAssignmentService.updateTeachingAssignment(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TeachingAssignmentResponse> toggleStatus(@PathVariable Long id,
                                                                   @RequestParam Boolean isActive) {
        return ResponseEntity.ok(teachingAssignmentService.toggleTeachingAssignmentStatus(id, isActive));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teachingAssignmentService.deleteTeachingAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
