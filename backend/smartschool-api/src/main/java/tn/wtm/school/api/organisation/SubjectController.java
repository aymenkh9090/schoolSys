package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.SubjectRequest;
import tn.wtm.school.org.dto.response.SubjectResponse;
import tn.wtm.school.org.service.SubjectService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/subjects")
@RequiredArgsConstructor
@Tag(name = "Matières", description = "Gestion des matières")
// Lecture ouverte au surveillant (libellé des matières dans les appels) ; écritures épinglées ci-dessous.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class SubjectController {

    private final SubjectService subjectService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.createSubject(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.getSubjectById(id));
    }

    @GetMapping
    public ResponseEntity<Page<SubjectResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(subjectService.getAllSubjects(pageable));
    }

    @GetMapping("/enseignees")
    public ResponseEntity<List<SubjectResponse>> getEnseignees() {
        return ResponseEntity.ok(subjectService.getEnseignedSubjects());
    }

    @GetMapping("/principales")
    public ResponseEntity<List<SubjectResponse>> getPrincipales() {
        return ResponseEntity.ok(subjectService.getPrincipaleSubjects());
    }

    @GetMapping("/search")
    public ResponseEntity<List<SubjectResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(subjectService.searchSubjects(q));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<SubjectResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(subjectService.updateSubject(id, request));
    }

    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @PatchMapping("/{id}/enseignee")
    public ResponseEntity<SubjectResponse> toggleEnseignee(@PathVariable Long id,
                                                           @RequestParam Boolean estEnseignee) {
        return ResponseEntity.ok(subjectService.toggleEnseignee(id, estEnseignee));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }
}
