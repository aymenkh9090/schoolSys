package tn.wtm.school.tenant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.security.email.EmailService;
import tn.wtm.school.security.keycloak.dto.KeycloakCreatedUserDTO;
import tn.wtm.school.security.keycloak.service.KeycloakAdminService;
import tn.wtm.school.tenant.dto.CreateTenantRequest;
import tn.wtm.school.tenant.dto.TenantResponse;
import tn.wtm.school.tenant.dto.UpdateTenantRequest;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.mapper.TenantMapper;
import tn.wtm.school.tenant.mapper.TenantMapperImpl;
import tn.wtm.school.tenant.repository.TenantRepository;
import tn.wtm.school.tenant.service.impl.TenantServiceImpl;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private ObjectsValidator<CreateTenantRequest> createValidator;
    @Mock private ObjectsValidator<UpdateTenantRequest> updateValidator;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private EmailService emailService;

    private TenantServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantMapper mapper = new TenantMapperImpl();
        service = new TenantServiceImpl(tenantRepository, mapper, createValidator, updateValidator, keycloakAdminService, emailService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
    }

    @Test
    void createsTenantAndIntegratesKeycloak() {
        CreateTenantRequest request = request("IBN", "College Ibn Khaldoun", TenantPlan.STANDARD);

        when(tenantRepository.existsByCodeIgnoreCase("IBN")).thenReturn(false);
        when(tenantRepository.existsByNameIgnoreCase("College Ibn Khaldoun")).thenReturn(false);

        // Premier save → PENDING (retourne le tenant avec ID)
        // Deuxième save → ACTIVE (après Keycloak)
        AtomicInteger saveCount = new AtomicInteger(0);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            t.setTenantId(1L);
            saveCount.incrementAndGet();
            return t;
        });

        when(keycloakAdminService.createTenantGroup(anyString(), anyString())).thenReturn("group-uuid-123");
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(KeycloakCreatedUserDTO.builder()
                        .userId("user-uuid-456")
                        .username("admin.ibn")
                        .email("admin@ibn.tn")
                        .tempPassword("Temp1234!")
                        .build());

        TenantResponse response = service.createTenant(request);

        // Deux saves : PENDING puis ACTIVE
        assertThat(saveCount.get()).isEqualTo(2);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getType()).isEqualTo(EtablissementType.COLLEGE);
        assertThat(response.getAdminUsername()).isEqualTo("admin.ibn");
        assertThat(response.getAdminTempPassword()).isEqualTo("Temp1234!");

        verify(keycloakAdminService).createTenantGroup("1", "College Ibn Khaldoun");
        verify(keycloakAdminService).createUser(
                eq("admin@ibn.tn"), eq("Mohamed Ben Ali"), eq("1"), eq("group-uuid-123"), anyString());
    }

    @Test
    void rejectsDuplicateTenantCode() {
        when(tenantRepository.existsByCodeIgnoreCase("IBN")).thenReturn(true);

        assertThatThrownBy(() -> service.createTenant(request("IBN", "College Ibn Khaldoun", TenantPlan.FREE)))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void rejectsDuplicateTenantName() {
        when(tenantRepository.existsByCodeIgnoreCase("IBN")).thenReturn(false);
        when(tenantRepository.existsByNameIgnoreCase("College Ibn Khaldoun")).thenReturn(true);

        assertThatThrownBy(() -> service.createTenant(request("IBN", "College Ibn Khaldoun", TenantPlan.FREE)))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(keycloakAdminService);
    }

    @Test
    void activatesTenantAndEnablesKeycloakUser() {
        Tenant tenant = tenant(1L, false, TenantStatus.SUSPENDED, TenantPlan.FREE);
        tenant.setAdminKeycloakId("user-uuid-456");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantResponse response = service.activateTenant(1L);

        assertThat(response.getActive()).isTrue();
        assertThat(response.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(keycloakAdminService).enableUser("user-uuid-456");
    }

    @Test
    void suspendsTenantAndDisablesKeycloakUser() {
        Tenant tenant = tenant(1L, true, TenantStatus.ACTIVE, TenantPlan.PREMIUM);
        tenant.setAdminKeycloakId("user-uuid-456");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantResponse response = service.suspendTenant(1L);

        assertThat(response.getActive()).isFalse();
        assertThat(response.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        verify(keycloakAdminService).disableUser("user-uuid-456");
    }

    @Test
    void toggleActivePersistsSuspendedStatus() {
        Tenant tenant = tenant(1L, true, TenantStatus.ACTIVE, TenantPlan.STANDARD);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        service.toggleActive(1L, false);

        assertThat(tenant.getActive()).isFalse();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void updatesTenantPlanAndStatus() {
        Tenant tenant = tenant(1L, true, TenantStatus.ACTIVE, TenantPlan.FREE);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantResponse response = service.updateTenant(1L, UpdateTenantRequest.builder()
                .name("College Ibn Khaldoun")
                .type(EtablissementType.COLLEGE)
                .plan(TenantPlan.PREMIUM)
                .status(TenantStatus.SUSPENDED)
                .build());

        assertThat(response.getPlan()).isEqualTo(TenantPlan.PREMIUM);
        assertThat(response.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(response.getActive()).isFalse();
    }

    @Test
    void failsWhenTenantDoesNotExist() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendTenant(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private CreateTenantRequest request(String code, String name, TenantPlan plan) {
        return CreateTenantRequest.builder()
                .code(code)
                .name(name)
                .type(EtablissementType.COLLEGE)
                .plan(plan)
                .address("Rue de l'ecole")
                .phone("+216 71 000 000")
                .emailAdmin("admin@ibn.tn")
                .nomCompletAdmin("Mohamed Ben Ali")
                .build();
    }

    private Tenant tenant(Long id, boolean active, TenantStatus status, TenantPlan plan) {
        return Tenant.builder()
                .tenantId(id)
                .code("IBN")
                .name("College Ibn Khaldoun")
                .etablismentType(EtablissementType.COLLEGE)
                .address("Rue de l'ecole")
                .phone("+216 71 000 000")
                .active(active)
                .status(status)
                .plan(plan)
                .build();
    }
}
