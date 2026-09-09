package tn.wtm.school.security.keycloak.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tn.wtm.school.security.utils.PasswordGeneratorUtil;

import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Le groupe Keycloak d'un établissement survit à la base applicative.
 *
 * <p>Un {@code docker compose down -v} côté application efface les tenants ;
 * le realm, lui, garde leurs groupes. Au redémarrage, l'amorçage recrée les
 * mêmes établissements sous de nouveaux identifiants : recréer le groupe
 * échouerait en 409 sur le nom, et le groupe survivant porterait un
 * {@code tenant_id} qui ne désigne plus rien — un jeton émis pour ce groupe
 * viserait un établissement disparu.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakEnsureTenantGroupTest {

    @Mock private Keycloak keycloak;
    @Mock private PasswordGeneratorUtil passwordGeneratorUtil;
    @Mock private RealmResource realmResource;
    @Mock private GroupsResource groupsResource;
    @Mock private GroupResource groupResource;
    @Mock private Response response;

    private KeycloakAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KeycloakAdminServiceImpl(keycloak, passwordGeneratorUtil);
        ReflectionTestUtils.setField(service, "realm", "smartschool");
        when(keycloak.realm("smartschool")).thenReturn(realmResource);
        when(realmResource.groups()).thenReturn(groupsResource);
    }

    private GroupRepresentation groupe(String id, String nom, String tenantId) {
        GroupRepresentation rep = new GroupRepresentation();
        rep.setId(id);
        rep.setName(nom);
        if (tenantId != null) {
            Map<String, List<String>> attributs = new HashMap<>();
            attributs.put("tenant_id", List.of(tenantId));
            rep.setAttributes(attributs);
        }
        return rep;
    }

    @Test
    @DisplayName("Le groupe homonyme est réutilisé et son tenant_id réaligné")
    void reutiliseLeGroupeEtRealigneLAttribut() {
        GroupRepresentation survivant = groupe("grp-1", "College Carthage", "5");
        when(groupsResource.groups("College Carthage", 0, 100)).thenReturn(List.of(survivant));
        when(groupsResource.group("grp-1")).thenReturn(groupResource);
        when(groupResource.toRepresentation()).thenReturn(survivant);

        String groupId = service.ensureTenantGroup("2", "College Carthage");

        assertThat(groupId).isEqualTo("grp-1");
        ArgumentCaptor<GroupRepresentation> maj = ArgumentCaptor.forClass(GroupRepresentation.class);
        verify(groupResource).update(maj.capture());
        assertThat(maj.getValue().getAttributes().get("tenant_id")).containsExactly("2");
        verify(groupsResource, never()).add(any());
    }

    @Test
    @DisplayName("Un groupe déjà aligné n'est pas réécrit")
    void neReecritPasUnGroupeDejaAligne() {
        GroupRepresentation aJour = groupe("grp-2", "College Carthage", "2");
        when(groupsResource.groups("College Carthage", 0, 100)).thenReturn(List.of(aJour));
        when(groupsResource.group("grp-2")).thenReturn(groupResource);
        when(groupResource.toRepresentation()).thenReturn(aJour);

        assertThat(service.ensureTenantGroup("2", "College Carthage")).isEqualTo("grp-2");

        verify(groupResource, never()).update(any());
    }

    /**
     * La recherche Keycloak est un « contient » : demander « Carthage » ramène
     * aussi « Carthage Nord ». Sans le filtre sur le nom exact, l'amorçage
     * accrocherait l'établissement au groupe du voisin.
     */
    @Test
    @DisplayName("Une correspondance seulement partielle ne compte pas")
    void ignoreLesHomonymesPartiels() {
        when(groupsResource.groups("College Carthage", 0, 100))
                .thenReturn(List.of(groupe("grp-3", "College Carthage Nord", "7")));
        when(groupsResource.add(any(GroupRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation())
                .thenReturn(URI.create("http://kc/admin/realms/smartschool/groups/grp-neuf"));

        assertThat(service.ensureTenantGroup("2", "College Carthage")).isEqualTo("grp-neuf");

        verify(groupsResource).add(any(GroupRepresentation.class));
        verify(groupsResource, never()).group(anyString());
    }
}
