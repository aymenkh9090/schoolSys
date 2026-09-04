package tn.wtm.school.api.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.wtm.school.api.auth.dto.CompleteFirstLoginRequest;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;
import tn.wtm.school.security.keycloak.service.KeycloakAdminService;

/**
 * Accessible à tout utilisateur authentifié (anyRequest().authenticated() dans
 * SecurityConfig) : un compte fraîchement créé doit pouvoir finaliser son premier
 * login avant même d'avoir un rôle métier vérifié côté frontend.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAdminService keycloakAdminService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    @PostMapping("/first-login/complete")
    public ResponseEntity<Void> completeFirstLogin(@Valid @RequestBody CompleteFirstLoginRequest request) {
        String userId = jwtClaimsExtractor.getUserId();
        keycloakAdminService.completeFirstLogin(userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
