package tn.wtm.school.tenant.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.tenant.dto.CreateTenantRequest;
import tn.wtm.school.tenant.dto.PublicTenantResponse;
import tn.wtm.school.tenant.dto.TenantResponse;
import tn.wtm.school.tenant.dto.UpdateTenantRequest;

import java.util.List;

public interface TenantService {

    TenantResponse createTenant(CreateTenantRequest request);
    Page<TenantResponse> findAllTenants(Pageable pageable);
    TenantResponse findTenantById(Long id);
    TenantResponse findTenantByName(String name);

    /**
     * Résolution publique d'un établissement par son code (page de login établissement).
     * Ne renvoie que les tenants actifs, sans données sensibles.
     */
    PublicTenantResponse findPublicTenantByCode(String code);

    /**
     * Résolution publique de l'établissement de l'utilisateur connecté (nom, logo)
     * — utilisée pour afficher le branding du tenant dans le dashboard après login.
     */
    PublicTenantResponse findPublicTenantById(Long id);
    TenantResponse updateTenant(Long id, UpdateTenantRequest request);
    void deleteTenant(Long id);

    // ===== BUSINESS =====

    /**
     * Activer / désactiver un tenant
     */
    void toggleActive(Long id, boolean active);

    TenantResponse activateTenant(Long id);

    TenantResponse suspendTenant(Long id);

    // ===== STATS =====

    /**
     * Nombre total de tenants
     */
    long countAll();


    /**
     * Nombre de tenants actifs
     */
    long countActive();


    /**
     * Nombre de tenants inactifs
     */
    long countInactive();

    /**
     * Retourne les derniers tenants créés
     */
    Page<TenantResponse> findRecent(Pageable pageable);

    /**
     * Retourne le keycloakGroupId d'un tenant.
     * Utilisé par SchoolUserServiceImpl avant de créer un user Keycloak.
     *
     * @param tenantId identifiant String du tenant (depuis TenantContext)
     * @throws ResourceNotFoundException si tenantId inconnu
     * @throws IllegalStateException si le groupe Keycloak n'a pas encore été créé (statut PENDING)
     */
    String getKeycloakGroupId(String tenantId);






}
