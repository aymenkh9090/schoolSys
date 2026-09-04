package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.BulkCreateClassGroupRequest;
import tn.wtm.school.org.dto.request.ClassGroupRequest;
import tn.wtm.school.org.dto.response.BulkCreateClassGroupResult;
import tn.wtm.school.org.dto.response.ClassGroupResponse;
import tn.wtm.school.org.service.LevelService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/classes")
@RequiredArgsConstructor
@Tag(name = "Classes", description = "Gestion des groupes classes")
// Le surveillant lit les classes (appel, absences, consultation du planning) ;
// les écritures restent épinglées méthode par méthode.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class ClassGroupController {

    private final LevelService levelService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<ClassGroupResponse> create(@Valid @RequestBody ClassGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(levelService.createClass(request));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/bulk-create")
    public ResponseEntity<BulkCreateClassGroupResult> bulkCreate(
            @Valid @RequestBody BulkCreateClassGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(levelService.bulkCreateClassGroups(request));
    }

    @GetMapping
    public ResponseEntity<List<ClassGroupResponse>> getAll() {
        return ResponseEntity.ok(levelService.getAllClassGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassGroupResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(levelService.getClassGroupById(id));
    }

    @GetMapping("/by-level/{levelId}")
    public ResponseEntity<List<ClassGroupResponse>> getByLevel(@PathVariable Long levelId) {
        return ResponseEntity.ok(levelService.getClassGroupsByLevel(levelId));
    }

    @GetMapping("/by-year/{schoolYearId}")
    public ResponseEntity<List<ClassGroupResponse>> getByYear(@PathVariable Long schoolYearId) {
        return ResponseEntity.ok(levelService.getClassGroupsBySchoolYear(schoolYearId));
    }

    @GetMapping("/by-level/{levelId}/year/{schoolYearId}")
    public ResponseEntity<List<ClassGroupResponse>> getByLevelAndYear(@PathVariable Long levelId,
                                                                       @PathVariable Long schoolYearId) {
        return ResponseEntity.ok(levelService.getClassGroupsByLevelAndYear(levelId, schoolYearId));
    }

    @GetMapping("/by-year/{schoolYearId}/paged")
    public ResponseEntity<Page<ClassGroupResponse>> getByYearPaged(@PathVariable Long schoolYearId,
                                                                    Pageable pageable) {
        return ResponseEntity.ok(levelService.getClassGroupsByYearPaged(schoolYearId, pageable));
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<ClassGroupResponse> getWithAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(levelService.getClassGroupWithAssignments(id));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ClassGroupResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody ClassGroupRequest request) {
        return ResponseEntity.ok(levelService.updateClassGroup(id, request));
    }

    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ClassGroupResponse> toggleStatus(@PathVariable Long id,
                                                           @RequestParam Boolean estActif) {
        return ResponseEntity.ok(levelService.toggleClassGroupStatus(id, estActif));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        levelService.deleteClassGroup(id);
        return ResponseEntity.noContent().build();
    }
}
