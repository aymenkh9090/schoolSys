package tn.wtm.school.security.keycloak.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.wtm.school.security.exception.KeycloakIntegrationException;
import tn.wtm.school.security.keycloak.dto.KeycloakCreatedUserDTO;
import tn.wtm.school.security.utils.PasswordGeneratorUtil;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

    private final Keycloak keycloak;
    private final PasswordGeneratorUtil passwordGeneratorUtil;

    @Value("${keycloak.admin.realm}")
    private String realm;

    // ── Groupes (= Tenants) ────────────────────────────────────────────────

    @Override
    public String createTenantGroup(String tenantId, String tenantName) {
        log.info("[Keycloak] Création groupe tenant='{}' id='{}'", tenantName, tenantId);

        GroupRepresentation group = new GroupRepresentation();
        group.setName(tenantName);
        // Attribut tenant_id propagé dans le JWT via Protocol Mapper (Group Attribute Mapper)
        group.setAttributes(Map.of("tenant_id", List.of(tenantId)));

        try (Response response = getRealm().groups().add(group)) {
            if (response.getStatus() != 201) {
                String body = response.readEntity(String.class);
                throw new KeycloakIntegrationException(
                        String.format("[Keycloak] Échec création groupe. Status=%d Body=%s",
                                response.getStatus(), body));
            }
            String location = response.getLocation().getPath();
            String groupId  = location.substring(location.lastIndexOf('/') + 1);
            log.info("[Keycloak] Groupe créé. groupId={}", groupId);
            return groupId;
        }
    }

    @Override
    public void deleteTenantGroup(String groupId) {
        log.warn("[Keycloak][Rollback] Suppression groupe groupId={}", groupId);
        try {
            getRealm().groups().group(groupId).remove();
        } catch (Exception e) {
            log.error("[Keycloak][Rollback] Échec suppression groupe {}: {}", groupId, e.getMessage());
        }
    }

    // ── Utilisateurs ───────────────────────────────────────────────────────

    @Override
    public KeycloakCreatedUserDTO createUser(String email, String fullName,
                                              String tenantId, String groupId,
                                              String roleName) {
        log.info("[Keycloak] Création user email='{}' role='{}' tenant='{}'", email, roleName, tenantId);

        String tempPassword = passwordGeneratorUtil.generate();
        String username     = buildUsername(email, fullName);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(extractFirstName(fullName));
        user.setLastName(extractLastName(fullName));
        user.setEnabled(true);
        user.setEmailVerified(true);
        // must_change_password : forçage applicatif du changement de mot de passe
        // (propagé dans le JWT via Protocol Mapper, comme tenant_id) — voir completeFirstLogin().
        user.setAttributes(Map.of(
                "tenant_id", List.of(tenantId),
                "must_change_password", List.of("true")
        ));
        // Pas de required action UPDATE_PASSWORD : le login custom (grant password,
        // sans page Keycloak) ne peut pas satisfaire les required actions —
        // Keycloak renverrait "Account is not fully set up".
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(tempPassword);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        try (Response response = getRealm().users().create(user)) {
            if (response.getStatus() == 409) {
                throw new KeycloakIntegrationException("[Keycloak] Utilisateur déjà existant : " + email);
            }
            if (response.getStatus() != 201) {
                String body = response.readEntity(String.class);
                throw new KeycloakIntegrationException(
                        String.format("[Keycloak] Échec création user. Status=%d Body=%s",
                                response.getStatus(), body));
            }

            String location = response.getLocation().getPath();
            String userId   = location.substring(location.lastIndexOf('/') + 1);
            log.info("[Keycloak] User créé. userId={} username={}", userId, username);

            assignRealmRole(userId, roleName);
            addUserToGroup(userId, groupId);

            return KeycloakCreatedUserDTO.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .tempPassword(tempPassword)
                    .build();
        }
    }

    @Override
    public void assignRealmRole(String userId, String roleName) {
        log.debug("[Keycloak] Assignation rôle '{}' userId='{}'", roleName, userId);
        RoleRepresentation role = getRealm().roles().get(roleName).toRepresentation();
        getRealm().users().get(userId).roles().realmLevel().add(List.of(role));
        log.info("[Keycloak] Rôle '{}' assigné à userId='{}'", roleName, userId);
    }

    @Override
    public void addUserToGroup(String userId, String groupId) {
        getRealm().users().get(userId).joinGroup(groupId);
        log.info("[Keycloak] User '{}' ajouté au groupe '{}'", userId, groupId);
    }

    @Override
    public void enableUser(String userId) {
        UserRepresentation user = getRealm().users().get(userId).toRepresentation();
        user.setEnabled(true);
        getRealm().users().get(userId).update(user);
        log.info("[Keycloak] User activé: userId='{}'", userId);
    }

    @Override
    public void disableUser(String userId) {
        UserRepresentation user = getRealm().users().get(userId).toRepresentation();
        user.setEnabled(false);
        getRealm().users().get(userId).update(user);
        log.info("[Keycloak] User désactivé: userId='{}'", userId);
    }

    @Override
    public void completeFirstLogin(String userId, String newPassword) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(false);
        getRealm().users().get(userId).resetPassword(credential);

        UserResource resource = getRealm().users().get(userId);
        UserRepresentation user = resource.toRepresentation();
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes == null) {
            attributes = new java.util.HashMap<>();
        }
        attributes.put("must_change_password", List.of("false"));
        user.setAttributes(attributes);
        resource.update(user);

        log.info("[Keycloak] Premier login finalisé. userId={}", userId);
    }

    @Override
    public void deleteUser(String userId) {
        log.warn("[Keycloak][Rollback] Suppression user userId='{}'", userId);
        try {
            getRealm().users().get(userId).remove();
        } catch (Exception e) {
            log.error("[Keycloak][Rollback] Échec suppression user {}: {}", userId, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private RealmResource getRealm() {
        return keycloak.realm(realm);
    }

    /**
     * Keycloak exige un username de 3 à 255 caractères. La partie locale de
     * l'email (avant le '@') est parfois trop courte (initiales, adresse de
     * test) — on complète alors avec le nom complet, puis avec des '0' en
     * dernier recours pour garantir la longueur minimale.
     */
    private String buildUsername(String email, String fullName) {
        String base = normalizeForUsername(email.split("@")[0]);
        if (base.length() < 3) {
            String fromName = normalizeForUsername(fullName == null ? "" : fullName.replaceAll("\\s+", "."));
            base = (base.isEmpty() ? fromName : base + "." + fromName);
        }
        if (base.length() < 3) {
            base = (base + "000").substring(0, 3);
        }
        return base;
    }

    private String normalizeForUsername(String value) {
        if (value == null || value.isBlank()) return "";
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // Les deux alternatives sont ancrées, chacune de son côté : un point en
        // tête OU un point en queue. Les groupes non capturants le disent
        // explicitement — sans eux, la portée du `|` face aux ancres se lit de
        // deux façons, et seule la lecture du JLS tranche.
        return withoutAccents.toLowerCase().replaceAll("[^a-z0-9]+", ".").replaceAll("(?:^\\.)|(?:\\.$)", "");
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        return fullName.trim().split("\\s+", 2)[0];
    }

    private String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}
