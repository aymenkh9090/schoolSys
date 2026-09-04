package tn.wtm.school.planning.constraints.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.planning.constraints.dto.request.ConstraintProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.CreateDefaultProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.ConstraintSettingRequest;
import tn.wtm.school.planning.constraints.dto.response.ConstraintDefinitionResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintProfileResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSettingResponse;
import tn.wtm.school.planning.constraints.service.ConstraintDefinitionService;
import tn.wtm.school.planning.constraints.service.ConstraintProfileService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/planning/constraints")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class ConstraintController {

    private final ConstraintDefinitionService definitionService;
    private final ConstraintProfileService profileService;

    @GetMapping("/definitions")
    public List<ConstraintDefinitionResponse> getDefinitions() {
        return definitionService.findAll();
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ConstraintProfileResponse createProfile(@Valid @RequestBody ConstraintProfileRequest request) {
        return profileService.create(request);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/profiles/create-default")
    @ResponseStatus(HttpStatus.CREATED)
    public ConstraintProfileResponse createDefaultProfile(
            @Valid @RequestBody CreateDefaultProfileRequest request) {
        return profileService.createDefault(request);
    }

    @GetMapping("/profiles")
    public List<ConstraintProfileResponse> getProfiles() {
        return profileService.findAll();
    }

    @GetMapping("/profiles/{id}")
    public ConstraintProfileResponse getProfile(@PathVariable Long id) {
        return profileService.findById(id);
    }

    /** Rend ce profil le seul actif de son annee scolaire. */
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/profiles/{id}/activate")
    public ConstraintProfileResponse activateProfile(@PathVariable Long id) {
        return profileService.activate(id);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@PathVariable Long id) {
        profileService.deleteProfile(id);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/profiles/{id}/settings")
    @ResponseStatus(HttpStatus.CREATED)
    public ConstraintSettingResponse addSetting(
            @PathVariable Long id,
            @Valid @RequestBody ConstraintSettingRequest request) {
        return profileService.addSetting(id, request);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/settings/{id}")
    public ConstraintSettingResponse updateSetting(
            @PathVariable Long id,
            @Valid @RequestBody ConstraintSettingRequest request) {
        return profileService.updateSetting(id, request);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/settings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSetting(@PathVariable Long id) {
        profileService.deleteSetting(id);
    }
}
