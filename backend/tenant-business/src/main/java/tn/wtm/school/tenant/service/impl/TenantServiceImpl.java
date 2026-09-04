package tn.wtm.school.tenant.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.security.constants.RoleConstants;
import tn.wtm.school.security.email.EmailService;
import tn.wtm.school.security.email.WelcomeEmailData;
import tn.wtm.school.security.exception.KeycloakIntegrationException;
import tn.wtm.school.security.keycloak.dto.KeycloakCreatedUserDTO;
import tn.wtm.school.security.keycloak.service.KeycloakAdminService;
import tn.wtm.school.tenant.dto.CreateTenantRequest;
import tn.wtm.school.tenant.dto.PublicTenantResponse;
import tn.wtm.school.tenant.dto.TenantResponse;
import tn.wtm.school.tenant.dto.UpdateTenantRequest;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.mapper.TenantMapper;
import tn.wtm.school.tenant.repository.TenantRepository;
import tn.wtm.school.tenant.service.TenantService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final ObjectsValidator<CreateTenantRequest> createRequestValidator;
    private final ObjectsValidator<UpdateTenantRequest> updateRequestValidator;
    private final KeycloakAdminService keycloakAdminService;
    private final EmailService emailService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Override
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {

        createRequestValidator.validate(request);

        if (tenantRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new ConflictException("Code Etablissement deja Existe !");
        }
        if (tenantRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ConflictException("Name Etablissement deja Existe !");
        }
        if (request.getType() == null) {
            throw new BadRequestException("Type de l'etablissement est obligatoire !");
        }

        // 1. Sauvegarder le tenant en DB avec statut PENDING
        Tenant tenant = tenantMapper.toEntity(request);
        if (tenant.getPlan() == null) {
            tenant.setPlan(TenantPlan.FREE); // toEntity() ne défaut pas plan à FREE (dto.getPlan() peut être null)
        }
        tenant.setAdminEmail(request.getEmailAdmin()); // ignoré par le mapper (renseigné explicitement ici)
        tenant.setStatus(TenantStatus.PENDING);        // active=false dérivé automatiquement

        Tenant savedTenant = tenantRepository.save(tenant);
        String tenantId = String.valueOf(savedTenant.getTenantId());

        log.info("[Tenant] Tenant sauvegardé en DB. tenantId={} code={}", tenantId, savedTenant.getCode());

        String keycloakGroupId = null;
        String keycloakUserId  = null;

        try {
            // 2. Créer le groupe Keycloak représentant cet établissement
            keycloakGroupId = keycloakAdminService.createTenantGroup(tenantId, request.getName());

            // 3. Créer le compte admin de l'établissement dans Keycloak
            KeycloakCreatedUserDTO adminUser = keycloakAdminService.createUser(
                    request.getEmailAdmin(),
                    request.getNomCompletAdmin(),
                    tenantId,
                    keycloakGroupId,
                    RoleConstants.SCHOOL_ADMIN
            );
            keycloakUserId = adminUser.getUserId();

            // 4. Mettre à jour le tenant avec les IDs Keycloak + passer en ACTIVE
            savedTenant.setKeycloakGroupId(keycloakGroupId);
            savedTenant.setAdminKeycloakId(keycloakUserId);
            savedTenant.setStatus(TenantStatus.ACTIVE); // active=true dérivé automatiquement
            savedTenant = tenantRepository.save(savedTenant);

            log.info("[Tenant] Tenant activé. tenantId={} groupId={} adminId={}",
                    tenantId, keycloakGroupId, keycloakUserId);

            // 5. Envoyer le mail de bienvenue avec le mot de passe temporaire (best-effort)
            emailService.sendWelcomeEmail(new WelcomeEmailData(
                    request.getEmailAdmin(),
                    request.getNomCompletAdmin(),
                    adminUser.getUsername(),
                    adminUser.getTempPassword(),
                    frontendBaseUrl + "/etablissement/login",
                    request.getName()
            ));

            // 6. Construire la réponse avec les credentials admin (affichés une seule fois)
            TenantResponse response = tenantMapper.toResponse(savedTenant);
            response.setAdminUsername(adminUser.getUsername());
            response.setAdminTempPassword(adminUser.getTempPassword());
            response.setAdminEmail(request.getEmailAdmin());
            return response;

        } catch (Exception e) {
            log.error("[Tenant] Échec intégration Keycloak pour tenantId={}. Rollback.", tenantId, e);
            rollbackKeycloak(keycloakUserId, keycloakGroupId);
            throw new KeycloakIntegrationException(
                    "Échec création tenant dans Keycloak: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<TenantResponse> findAllTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(tenantMapper::toResponse);
    }

    @Override
    public TenantResponse findTenantById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Etablissement avec ce ID " + id + " n'existe pas"));
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public TenantResponse findTenantByName(String name) {
        Tenant tenant = tenantRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Etablissement avec le nom " + name + " n'existe pas"));
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public PublicTenantResponse findPublicTenantByCode(String code) {
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(code)
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun établissement actif avec le code " + code));
        return toPublicResponse(tenant);
    }

    @Override
    public PublicTenantResponse findPublicTenantById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etablissement n'existe pas"));
        return toPublicResponse(tenant);
    }

    private PublicTenantResponse toPublicResponse(Tenant tenant) {
        return PublicTenantResponse.builder()
                .id(tenant.getTenantId())
                .code(tenant.getCode())
                .name(tenant.getName())
                .type(tenant.getEtablismentType())
                .logo(tenant.getLogo())
                .build();
    }

    @Override
    @Transactional
    public TenantResponse updateTenant(Long id, UpdateTenantRequest request) {
        updateRequestValidator.validate(request);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etablissement n'existe pas"));

        if (request.getName() != null
                && !request.getName().equalsIgnoreCase(tenant.getName())
                && tenantRepository.existsByNameIgnoreCaseAndTenantIdNot(request.getName(), id)) {
            throw new ConflictException("Tenant name already exists");
        }

        validateStatus(request.getStatus());

        tenantMapper.updateEntityFromRequest(request, tenant); // status (si fourni) déjà appliqué via setStatus()
        // Compat : le client peut encore envoyer "active" seul, sans "status"
        if (request.getStatus() == null && request.getActive() != null) {
            tenant.setStatus(Boolean.TRUE.equals(request.getActive()) ? TenantStatus.ACTIVE : TenantStatus.SUSPENDED);
        }
        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    @Override
    @Transactional
    public void deleteTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etablissement n'existe pas"));
        tenantRepository.delete(tenant);
    }

    @Override
    @Transactional
    public void toggleActive(Long id, boolean active) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etablissement n'existe pas"));
        tenant.setStatus(active ? TenantStatus.ACTIVE : TenantStatus.SUSPENDED);

        // Synchroniser l'état de l'admin dans Keycloak
        if (tenant.getAdminKeycloakId() != null) {
            try {
                if (active) {
                    keycloakAdminService.enableUser(tenant.getAdminKeycloakId());
                } else {
                    keycloakAdminService.disableUser(tenant.getAdminKeycloakId());
                }
            } catch (Exception e) {
                log.warn("[Tenant] Échec sync Keycloak lors du toggle active={} pour tenantId={}: {}",
                        active, id, e.getMessage());
            }
        }

        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    public TenantResponse activateTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etablissement n'existe pas"));
        tenant.setStatus(TenantStatus.ACTIVE);

        if (tenant.getAdminKeycloakId() != null) {
            try {
                keycloakAdminService.enableUser(tenant.getAdminKeycloakId());
            } catch (Exception e) {
                log.warn("[Tenant] Échec activation admin Keycloak pour tenantId={}: {}", id, e.getMessage());
            }
        }

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    @Override
    @Transactional
    public TenantResponse suspendTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etablissement n'existe pas"));
        tenant.setStatus(TenantStatus.SUSPENDED);

        if (tenant.getAdminKeycloakId() != null) {
            try {
                keycloakAdminService.disableUser(tenant.getAdminKeycloakId());
            } catch (Exception e) {
                log.warn("[Tenant] Échec désactivation admin Keycloak pour tenantId={}: {}", id, e.getMessage());
            }
        }

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    @Override
    public long countAll() {
        return tenantRepository.count();
    }

    @Override
    public long countActive() {
        return tenantRepository.countByActiveTrue();
    }

    @Override
    public long countInactive() {
        return tenantRepository.countByActiveFalse();
    }

    @Override
    public Page<TenantResponse> findRecent(Pageable pageable) {
        return tenantRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(tenantMapper::toResponse);
    }

    @Override
    public String getKeycloakGroupId(String tenantId) {
        try {
            Long id = Long.parseLong(tenantId);
            String groupId = tenantRepository.findKeycloakGroupIdById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Etablissement introuvable. tenantId=" + tenantId));
            if (!StringUtils.hasText(groupId)) {
                throw new IllegalStateException(
                        "Etablissement sans groupe Keycloak (statut PENDING?). tenantId=" + tenantId);
            }
            log.debug("[Tenant] keycloakGroupId={} pour tenantId={}", groupId, tenantId);
            return groupId;
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("tenantId invalide: " + tenantId);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void rollbackKeycloak(String userId, String groupId) {
        if (userId != null) {
            keycloakAdminService.deleteUser(userId);
        }
        if (groupId != null) {
            keycloakAdminService.deleteTenantGroup(groupId);
        }
    }

    /**
     * PENDING est un état transitoire interne (avant intégration Keycloak) —
     * un client ne doit jamais pouvoir le re-déclencher via une mise à jour.
     * Note : contrairement à validatePlan (supprimé), ce contrôle est atteignable :
     * TenantStatus a 3 valeurs (PENDING/ACTIVE/SUSPENDED), Jackson laisse donc
     * passer PENDING jusqu'ici.
     */
    private void validateStatus(TenantStatus status) {
        if (status == null) return;
        if (status != TenantStatus.ACTIVE && status != TenantStatus.SUSPENDED) {
            throw new BadRequestException("Statut tenant invalide");
        }
    }
}
