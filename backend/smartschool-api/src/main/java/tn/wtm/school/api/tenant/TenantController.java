package tn.wtm.school.api.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.security.constants.RoleConstants;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;
import tn.wtm.school.tenant.dto.CreateTenantRequest;
import tn.wtm.school.tenant.dto.PublicTenantResponse;
import tn.wtm.school.tenant.dto.TenantResponse;
import tn.wtm.school.tenant.dto.UpdateTenantRequest;
import tn.wtm.school.tenant.service.TenantService;

import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + RoleConstants.PLATFORM_SUPER_ADMIN + "')")
@Tag(name = "Tenants", description = "Gestion des établissements scolaires (Super Admin)")
public class TenantController {

    private final TenantService tenantService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Établissement de l'utilisateur connecté (nom, logo) — branding du dashboard")
    public ResponseEntity<PublicTenantResponse> me() {
        String tenantId = jwtClaimsExtractor.getTenantId();
        if (tenantId == null || !tenantId.matches("\\d+")) {
            // Super admin (tenant_id="system") ou claim absent : pas d'établissement à afficher
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tenantService.findPublicTenantById(Long.parseLong(tenantId)));
    }

    @PostMapping
    @Operation(summary = "Créer un établissement")
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer un établissement par ID")
    public ResponseEntity<TenantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.findTenantById(id));
    }

    @GetMapping
    @Operation(summary = "Lister tous les établissements (paginé)")
    public ResponseEntity<Page<TenantResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(tenantService.findAllTenants(pageable));
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher un établissement par nom")
    public ResponseEntity<TenantResponse> findByName(
            @Parameter(description = "Nom de l'établissement") @RequestParam String name) {
        return ResponseEntity.ok(tenantService.findTenantByName(name));
    }

    @GetMapping("/recent")
    @Operation(summary = "Derniers établissements créés")
    public ResponseEntity<Page<TenantResponse>> recent(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(tenantService.findRecent(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un établissement")
    public ResponseEntity<TenantResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un établissement")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activer un établissement")
    public ResponseEntity<TenantResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.activateTenant(id));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspendre un établissement")
    public ResponseEntity<TenantResponse> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.suspendTenant(id));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Basculer l'état actif/inactif d'un établissement")
    public ResponseEntity<Void> toggle(
            @PathVariable Long id,
            @RequestParam boolean active) {
        tenantService.toggleActive(id, active);
        return ResponseEntity.noContent().build();
    }

    // ── Statistiques ──────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales des établissements")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "total",    tenantService.countAll(),
                "active",   tenantService.countActive(),
                "inactive", tenantService.countInactive()
        ));
    }
}
