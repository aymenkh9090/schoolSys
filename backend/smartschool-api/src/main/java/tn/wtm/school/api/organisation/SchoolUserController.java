package tn.wtm.school.api.organisation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.request.CreateSchoolUserRequest;
import tn.wtm.school.org.dto.request.UpdateSchoolUserRequest;
import tn.wtm.school.org.dto.response.SchoolUserResponse;
import tn.wtm.school.org.enums.UserRole;
import tn.wtm.school.org.service.SchoolUserService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class SchoolUserController {

    private final SchoolUserService schoolUserService;

    @PostMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolUserResponse> createUser(
            @Valid @RequestBody CreateSchoolUserRequest request) {
        log.info("[API] POST /api/admin/users email='{}' role='{}'",
                 request.getEmail(), request.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolUserService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'PLATFORM_SUPER_ADMIN', 'TEACHER', 'SURVEILLANT')")
    public ResponseEntity<List<SchoolUserResponse>> getUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        log.info("[API] GET /api/admin/users role={} activeOnly={}", role, activeOnly);
        List<SchoolUserResponse> users;
        if (role != null) {
            users = schoolUserService.getUsersByRole(role);
        } else if (activeOnly) {
            users = schoolUserService.getActiveUsers();
        } else {
            users = schoolUserService.getAllUsers();
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'PLATFORM_SUPER_ADMIN', 'TEACHER', 'SURVEILLANT')")
    public ResponseEntity<SchoolUserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(schoolUserService.getUserById(userId));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolUserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateSchoolUserRequest request) {
        return ResponseEntity.ok(schoolUserService.updateUser(userId, request));
    }

    @PutMapping("/{userId}/deactivate")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolUserResponse> deactivateUser(@PathVariable Long userId) {
        log.info("[API] PUT /api/admin/users/{}/deactivate", userId);
        return ResponseEntity.ok(schoolUserService.deactivateUser(userId));
    }

    @PutMapping("/{userId}/reactivate")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolUserResponse> reactivateUser(@PathVariable Long userId) {
        log.info("[API] PUT /api/admin/users/{}/reactivate", userId);
        return ResponseEntity.ok(schoolUserService.reactivateUser(userId));
    }
}
