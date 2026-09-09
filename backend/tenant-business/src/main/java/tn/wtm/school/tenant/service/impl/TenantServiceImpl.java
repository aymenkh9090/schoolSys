package tn.wtm.school.tenant.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.security.constants.RoleConstants;
import tn.wtm.school.security.email.EmailService;
import tn.wtm.school.security.email.WelcomeEmailData;
import tn.wtm.school.security.exception.KeycloakIntegrationException;
import tn.wtm.school.security.exception.KeycloakUserAlreadyExistsException;
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
            throw new ConflictException(
                    "Le code « " + request.getCode() + " » est déjà attribué à un autre établissement. "
                            + "Choisissez un code différent.");
        }
        if (tenantRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ConflictException(
                    "Un établissement porte déjà le nom « " + request.getName() + " ». "
                            + "Choisissez un nom différent.");
        }
        if (request.getType() == null) {
            throw new BadRequestException("Le type d'établissement est obligatoire.");
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

        } catch (KeycloakUserAlreadyExistsException e) {
            // Une adresse déjà prise n'est pas une panne : c'est une saisie à
            // corriger. Sans ce cas distinct, l'utilisateur recevait un 502
            // « erreur de communication » suivi de la trace Keycloak.
            log.warn("[Tenant] Adresse admin déjà utilisée dans Keycloak. tenantId={} email={}",
                    tenantId, request.getEmailAdmin());
            rollbackKeycloak(keycloakUserId, keycloakGroupId);
            throw new ConflictException(
                    "L'adresse « " + request.getEmailAdmin() + " » est déjà utilisée par un autre compte. "
                            + "Indiquez une autre adresse pour l'administrateur.");
        } catch (Exception e) {
            log.error("[Tenant] Échec intégration Keycloak pour tenantId={}. Rollback.", tenantId, e);
            rollbackKeycloak(keycloakUserId, keycloakGroupId);
            // Le détail technique reste dans le log ; l'appelant reçoit le message
            // du handler, qui dit ce qui a été fait de sa demande.
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
                        "Cet établissement est introuvable (identifiant " + id + ")."));
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public TenantResponse findTenantByName(String name) {
        Tenant tenant = tenantRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun établissement ne porte le nom « " + name + " »."));
        return tenantMapper.toResponse(tenant);
    }

    @Override
    public PublicTenantResponse findPublicTenantByCode(String code) {
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(code)
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun établissement actif ne correspond au code « " + code + " »."));
        return toPublicResponse(tenant);
    }

    @Override
    public PublicTenantResponse findPublicTenantById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cet établissement est introuvable : il a peut-être été supprimé."));
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
                .orElseThrow(() -> new ResourceNotFoundException("Cet établissement est introuvable : il a peut-être été supprimé."));

        if (request.getName() != null
                && !request.getName().equalsIgnoreCase(tenant.getName())
                && tenantRepository.existsByNameIgnoreCaseAndTenantIdNot(request.getName(), id)) {
            throw new ConflictException(
                    "Un établissement porte déjà le nom « " + request.getName() + " ». "
                            + "Choisissez un nom différent.");
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
                .orElseThrow(() -> new ResourceNotFoundException("Cet établissement est introuvable : il a peut-être été supprimé."));

        // Capturés avant la suppression : l'entité est détachée ensuite.
        String adminKeycloakId = tenant.getAdminKeycloakId();
        String groupId         = tenant.getKeycloakGroupId();

        tenantRepository.delete(tenant);

        // Keycloak n'est pas transactionnel : on ne peut pas l'inscrire dans le
        // rollback JPA. Le nettoyage est donc reporté APRÈS le commit — sinon un
        // échec tardif (violation de clé étrangère depuis `subscriptions`, par
        // exemple) laisserait un établissement bien vivant dont on aurait déjà
        // détruit le compte administrateur.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupKeycloak(id, adminKeycloakId, groupId);
            }
        });
    }

    /**
     * Supprime le compte admin et le groupe Keycloak d'un établissement effacé.
     * Best-effort : la ligne est déjà committée, échouer ici ne doit pas remonter
     * une erreur à l'appelant — mais laisse une trace, car l'orphelin qui subsiste
     * bloquera toute recréation avec la même adresse (Keycloak répondrait 409).
     */
    private void cleanupKeycloak(Long tenantId, String adminKeycloakId, String groupId) {
        if (adminKeycloakId != null) {
            try {
                keycloakAdminService.deleteUser(adminKeycloakId);
                log.info("[Tenant] Compte admin Keycloak supprimé. tenantId={} userId={}",
                        tenantId, adminKeycloakId);
            } catch (Exception e) {
                log.warn("[Tenant] Compte admin Keycloak orphelin. tenantId={} userId={} : {}",
                        tenantId, adminKeycloakId, e.getMessage());
            }
        }
        if (groupId != null) {
            try {
                keycloakAdminService.deleteTenantGroup(groupId);
                log.info("[Tenant] Groupe Keycloak supprimé. tenantId={} groupId={}", tenantId, groupId);
            } catch (Exception e) {
                log.warn("[Tenant] Groupe Keycloak orphelin. tenantId={} groupId={} : {}",
                        tenantId, groupId, e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void toggleActive(Long id, boolean active) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cet établissement est introuvable : il a peut-être été supprimé."));
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
                .orElseThrow(() -> new ResourceNotFoundException("Cet établissement est introuvable : il a peut-être été supprimé."));
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
                .orElseThrow(() -> new ResourceNotFoundException("Cet établissement est introuvable : il a peut-être été supprimé."));
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
            // Existence d'abord : la requête ne ramène qu'une colonne, et une
            // colonne nulle rend le même Optional vide qu'une ligne absente.
            // Sans ce test, un établissement bien présent mais sans groupe
            // Keycloak était annoncé « introuvable » — le message envoyait
            // chercher la panne là où elle n'est pas.
            if (!tenantRepository.existsById(id)) {
                throw new ResourceNotFoundException(
                        "Cet établissement est introuvable (identifiant " + tenantId + ").");
            }
            String groupId = tenantRepository.findKeycloakGroupIdById(id).orElse(null);
            if (!StringUtils.hasText(groupId)) {
                log.warn("[Tenant] Établissement sans groupe Keycloak — inscription inachevée "
                        + "ou tenant écrit hors du parcours de création. tenantId={}", tenantId);
                throw new IllegalStateException(
                        "L'inscription de cet établissement n'est pas terminée : son espace "
                                + "d'authentification n'a pas été créé. Contactez l'administrateur "
                                + "de la plateforme.");
            }
            log.debug("[Tenant] keycloakGroupId={} pour tenantId={}", groupId, tenantId);
            return groupId;
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException(
                    "Identifiant d'établissement invalide : « " + tenantId + " ».");
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
            throw new BadRequestException(
                    "Statut d'établissement invalide : seuls « actif » et « suspendu » peuvent être appliqués.");
        }
    }
}
