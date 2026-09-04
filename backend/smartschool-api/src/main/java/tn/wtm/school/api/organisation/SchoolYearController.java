package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.SchoolYearRequest;
import tn.wtm.school.org.dto.response.SchoolYearResponse;
import tn.wtm.school.org.service.AcademicYearService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/school-years")
@RequiredArgsConstructor
@Tag(name = "Années scolaires", description = "Gestion des années scolaires")
// Lecture ouverte au surveillant (année courante des appels) ; écritures épinglées ci-dessous.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class SchoolYearController {

    private final AcademicYearService academicYearService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<SchoolYearResponse> create(@Valid @RequestBody SchoolYearRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicYearService.createAcademicYear(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchoolYearResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(academicYearService.getAcademicYearById(id));
    }

    @GetMapping
    public ResponseEntity<Page<SchoolYearResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(academicYearService.getAcademicYears(pageable));
    }

    @GetMapping("/list")
    public ResponseEntity<List<SchoolYearResponse>> list() {
        return ResponseEntity.ok(academicYearService.getAcademicYearsByTenant());
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<SchoolYearResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody SchoolYearRequest request) {
        return ResponseEntity.ok(academicYearService.updateAcademicYear(id, request));
    }

    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<SchoolYearResponse> toggleStatus(@PathVariable Long id,
                                                           @RequestParam Boolean active) {
        return ResponseEntity.ok(academicYearService.toggleAcademicYearStatus(id, active));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        academicYearService.deleteAcademicYear(id);
        return ResponseEntity.noContent().build();
    }
}
