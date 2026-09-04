package tn.wtm.school.security.keycloak.service;

import tn.wtm.school.security.keycloak.dto.KeycloakCreatedUserDTO;

public interface KeycloakAdminService {

    /**
     * Crée un groupe Keycloak représentant un établissement (tenant).
     * L'attribut tenant_id sur le groupe est injecté dans le JWT via Protocol Mapper.
     */
    String createTenantGroup(String tenantId, String tenantName);

    /**
     * Crée un utilisateur Keycloak avec mot de passe temporaire.
     * Assigne le rôle et ajoute l'user au groupe automatiquement.
     */
    KeycloakCreatedUserDTO createUser(String email, String fullName,
                                      String tenantId, String groupId,
                                      String roleName);

    void assignRealmRole(String userId, String roleName);

    void addUserToGroup(String userId, String groupId);

    void enableUser(String userId);

    void disableUser(String userId);

    /**
     * Finalise le premier login : remplace le mot de passe temporaire par celui
     * choisi par l'utilisateur, et retombe l'attribut must_change_password à false.
     */
    void completeFirstLogin(String userId, String newPassword);

    void deleteUser(String userId);

    void deleteTenantGroup(String groupId);
}
