package tn.wtm.school.security.keycloak.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO retourné après création d'un utilisateur dans Keycloak.
 * Ne jamais logger le champ tempPassword.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakCreatedUserDTO {

    private String userId;
    private String username;
    private String email;
    private String tempPassword;
}
