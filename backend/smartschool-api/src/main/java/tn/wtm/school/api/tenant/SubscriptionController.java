package tn.wtm.school.api.tenant;

import io.swagger.v3.oas.annotations.Operation;
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
import tn.wtm.school.tenant.dto.ChangePlanRequest;
import tn.wtm.school.tenant.dto.CreateSubscriptionRequest;
import tn.wtm.school.tenant.dto.RenewSubscriptionRequest;
import tn.wtm.school.tenant.dto.SubscriptionOverviewResponse;
import tn.wtm.school.tenant.dto.SubscriptionResponse;
import tn.wtm.school.tenant.service.SubscriptionService;

@RestController
@RequestMapping("/api/super-admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + RoleConstants.PLATFORM_SUPER_ADMIN + "')")
@Tag(name = "Abonnements", description = "Gestion des abonnements des établissements (Super Admin)")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @Operation(summary = "Vue d'ensemble des abonnements de tous les établissements (paginé)")
    public ResponseEntity<Page<SubscriptionOverviewResponse>> overview(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(subscriptionService.listOverview(pageable));
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Abonnement courant d'un établissement")
    public ResponseEntity<SubscriptionResponse> current(@PathVariable Long tenantId) {
        return ResponseEntity.ok(subscriptionService.getCurrent(tenantId));
    }

    @GetMapping("/tenant/{tenantId}/history")
    @Operation(summary = "Historique des abonnements d'un établissement")
    public ResponseEntity<Page<SubscriptionResponse>> history(
            @PathVariable Long tenantId,
            @PageableDefault(size = 20, sort = "startDate") Pageable pageable) {
        return ResponseEntity.ok(subscriptionService.getHistory(tenantId, pageable));
    }

    @PostMapping
    @Operation(summary = "Créer un abonnement pour un établissement")
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.createSubscription(request));
    }

    @PatchMapping("/tenant/{tenantId}/renew")
    @Operation(summary = "Renouveler l'abonnement d'un établissement")
    public ResponseEntity<SubscriptionResponse> renew(
            @PathVariable Long tenantId,
            @Valid @RequestBody RenewSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.renewSubscription(tenantId, request));
    }

    @PatchMapping("/tenant/{tenantId}/plan")
    @Operation(summary = "Changer le plan de l'abonnement courant")
    public ResponseEntity<SubscriptionResponse> changePlan(
            @PathVariable Long tenantId,
            @Valid @RequestBody ChangePlanRequest request) {
        return ResponseEntity.ok(subscriptionService.changePlan(tenantId, request));
    }

    @PatchMapping("/tenant/{tenantId}/cancel")
    @Operation(summary = "Annuler l'abonnement d'un établissement")
    public ResponseEntity<SubscriptionResponse> cancel(@PathVariable Long tenantId) {
        return ResponseEntity.ok(subscriptionService.cancelSubscription(tenantId));
    }
}
