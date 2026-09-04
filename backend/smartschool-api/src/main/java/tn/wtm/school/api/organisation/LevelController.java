package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.LevelRequest;
import tn.wtm.school.org.dto.response.LevelResponse;
import tn.wtm.school.org.service.LevelService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/levels")
@RequiredArgsConstructor
@Tag(name = "Niveaux", description = "Gestion des niveaux scolaires")
// Lecture ouverte au surveillant (filtres classes) ; écritures épinglées ci-dessous.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class LevelController {

    private final LevelService levelService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<LevelResponse> create(@Valid @RequestBody LevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(levelService.createLevel(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LevelResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(levelService.getLevelById(id));
    }

    @GetMapping
    public ResponseEntity<Page<LevelResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(levelService.getAllLevels(pageable));
    }

    @GetMapping("/actifs")
    public ResponseEntity<List<LevelResponse>> getActifs() {
        return ResponseEntity.ok(levelService.getActivesLevels());
    }

    @GetMapping("/{id}/classes")
    public ResponseEntity<LevelResponse> getWithClasses(@PathVariable Long id) {
        return ResponseEntity.ok(levelService.getLevelWithClasses(id));
    }

    @GetMapping("/{id}/subjects")
    public ResponseEntity<LevelResponse> getWithSubjects(@PathVariable Long id) {
        return ResponseEntity.ok(levelService.getLevelWithSubjects(id));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<LevelResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody LevelRequest request) {
        return ResponseEntity.ok(levelService.updateLevel(request, id));
    }

    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<LevelResponse> toggleStatus(@PathVariable Long id,
                                                      @RequestParam Boolean estActif) {
        return ResponseEntity.ok(levelService.toggleLevelStatus(id, estActif));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        levelService.deleteLevel(id);
        return ResponseEntity.noContent().build();
    }
}
