package tn.wtm.school.tenant.dto;

import lombok.*;
import tn.wtm.school.tenant.enums.EtablissementType;

/**
 * Réponse publique (sans auth) pour la page de login établissement :
 * l'utilisateur saisit le code de son école, on affiche nom + logo.
 * Ne doit exposer aucune donnée sensible (plan, admin, Keycloak...).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicTenantResponse {

    private Long id;
    private String code;
    private String name;
    private EtablissementType type;
    private String logo;
}
