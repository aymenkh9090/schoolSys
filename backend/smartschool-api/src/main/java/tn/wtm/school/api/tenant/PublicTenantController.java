package tn.wtm.school.api.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.tenant.dto.PublicTenantResponse;
import tn.wtm.school.tenant.service.TenantService;

/**
 * Endpoints publics (sans authentification) — utilisés par la page de login
 * établissement : l'utilisateur saisit le code de son école, l'app affiche
 * son nom et son logo avant l'étape email/mot de passe.
 */
@RestController
@RequestMapping("/api/public/tenants")
@RequiredArgsConstructor
@Tag(name = "Public Tenants", description = "Résolution publique d'un établissement (page de login)")
public class PublicTenantController {

    private final TenantService tenantService;

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Résoudre un établissement actif par son code (public)")
    public ResponseEntity<PublicTenantResponse> byCode(
            @Parameter(description = "Code unique de l'établissement (ex: lycee-alfarabi)")
            @PathVariable String code) {
        return ResponseEntity.ok(tenantService.findPublicTenantByCode(code));
    }
}
