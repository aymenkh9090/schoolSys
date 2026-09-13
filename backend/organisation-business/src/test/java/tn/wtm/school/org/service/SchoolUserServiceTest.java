package tn.wtm.school.org.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.org.dto.request.CreateSchoolUserRequest;
import tn.wtm.school.org.dto.request.UpdateSchoolUserRequest;
import tn.wtm.school.org.dto.response.SchoolUserResponse;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.enums.UserRole;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.service.impl.SchoolUserServiceImpl;
import tn.wtm.school.security.email.EmailService;
import tn.wtm.school.security.email.WelcomeEmailData;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;
import tn.wtm.school.security.keycloak.dto.KeycloakCreatedUserDTO;
import tn.wtm.school.security.keycloak.service.KeycloakAdminService;
import tn.wtm.school.tenant.service.TenantService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolUserServiceTest {

    @Mock SchoolUserRepository schoolUserRepository;
    @Mock TeacherRepository    teacherRepository;
    @Mock KeycloakAdminService keycloakAdminService;
    @Mock TenantService        tenantService;
    @Mock EmailService         emailService;
    @Mock JwtClaimsExtractor   jwtClaimsExtractor;

    SchoolUserServiceImpl service;

    static final String TENANT    = "tenant-1";
    static final String GROUPE    = "groupe-kc";
    static final Long   USER_ID   = 11L;
    static final Long   TEACHER_ID = 4L;

    @BeforeEach
    void setUp() {
        service = new SchoolUserServiceImpl(schoolUserRepository, teacherRepository, keycloakAdminService,
                tenantService, emailService, jwtClaimsExtractor);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://ecole.tn");
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── données ───────────────────────────────────────────────────────────────

    CreateSchoolUserRequest requete(UserRole role) {
        CreateSchoolUserRequest r = new CreateSchoolUserRequest();
        r.setRole(role);
        r.setEmail("surveillant@ecole.tn");
        r.setNomComplet("Mounir Haddad");
        r.setTelephone("22123456");
        return r;
    }

    Teacher enseignant(String tenant, String email) {
        Teacher t = Teacher.builder().idEnseignant(TEACHER_ID).nom("Ben Salah").prenom("Leila")
                .email(email).specialite("Mathématiques").telephone("98765432").build();
        t.setTenantId(tenant);
        return t;
    }

    SchoolUser utilisateur(String tenant) {
        SchoolUser u = SchoolUser.builder().id(USER_ID).keycloakUserId("kc-11").email("a@ecole.tn")
                .nomComplet("Ali Mansour").role(UserRole.SURVEILLANT).build();
        u.setTenantId(tenant);
        return u;
    }

    KeycloakCreatedUserDTO compteKeycloak() {
        return KeycloakCreatedUserDTO.builder().userId("kc-new").username("mhaddad").tempPassword("Tmp!123").build();
    }

    void keycloakCree(String role) {
        when(tenantService.getKeycloakGroupId(TENANT)).thenReturn(GROUPE);
        when(keycloakAdminService.createUser(anyString(), anyString(), eq(TENANT), eq(GROUPE), eq(role)))
                .thenReturn(compteKeycloak());
    }

    // ── création ──────────────────────────────────────────────────────────────

    @Nested
    class Creation {

        @Test
        void sansTenant_refuse() {
            TenantContext.clear();
            assertThatThrownBy(() -> service.createUser(requete(UserRole.SURVEILLANT)))
                    .isInstanceOf(TenantSecurityException.class);
        }

        @Test
        void enseignantSansFiche_refuse() {
            assertThatThrownBy(() -> service.createUser(requete(UserRole.TEACHER)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("teacherId");
        }

        @Test
        void ficheEnseignantDUnAutreEtablissement_introuvable() {
            CreateSchoolUserRequest r = requete(UserRole.TEACHER);
            r.setTeacherId(TEACHER_ID);
            when(teacherRepository.findById(TEACHER_ID)).thenReturn(Optional.of(enseignant("autre", "l@ecole.tn")));

            assertThatThrownBy(() -> service.createUser(r))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(TEACHER_ID.toString());
        }

        @Test
        void ficheEnseignantSansEmail_refuse() {
            CreateSchoolUserRequest r = requete(UserRole.TEACHER);
            r.setTeacherId(TEACHER_ID);
            when(teacherRepository.findById(TEACHER_ID)).thenReturn(Optional.of(enseignant(TENANT, " ")));

            assertThatThrownBy(() -> service.createUser(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Ben Salah Leila");
        }

        @Test
        void enseignantAyantDejaUnCompte_conflit() {
            CreateSchoolUserRequest r = requete(UserRole.TEACHER);
            r.setTeacherId(TEACHER_ID);
            when(teacherRepository.findById(TEACHER_ID)).thenReturn(Optional.of(enseignant(TENANT, "l@ecole.tn")));
            when(schoolUserRepository.existsByTenantIdAndTeacher_IdEnseignant(TENANT, TEACHER_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(r))
                    .isInstanceOf(ConflictException.class);
            verify(keycloakAdminService, never()).createUser(any(), any(), any(), any(), any());
        }

        @Test
        void enseignant_lesDonneesViennentDeLaFiche() {
            CreateSchoolUserRequest r = requete(UserRole.TEACHER);
            r.setTeacherId(TEACHER_ID);
            Teacher fiche = enseignant(TENANT, "l@ecole.tn");
            when(teacherRepository.findById(TEACHER_ID)).thenReturn(Optional.of(fiche));
            keycloakCree("TEACHER");
            when(schoolUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SchoolUserResponse reponse = service.createUser(r);

            ArgumentCaptor<SchoolUser> enregistre = ArgumentCaptor.forClass(SchoolUser.class);
            verify(schoolUserRepository).save(enregistre.capture());
            SchoolUser u = enregistre.getValue();
            assertThat(u.getEmail()).isEqualTo("l@ecole.tn");
            assertThat(u.getNomComplet()).isEqualTo("Ben Salah Leila");
            assertThat(u.getMatiere()).isEqualTo("Mathématiques");
            assertThat(u.getTelephone()).isEqualTo("98765432");
            assertThat(u.getTeacher()).isSameAs(fiche);
            assertThat(reponse.getEnseignantId()).isEqualTo(TEACHER_ID);
        }

        @Test
        void horsEnseignant_emailObligatoire() {
            CreateSchoolUserRequest r = requete(UserRole.SURVEILLANT);
            r.setEmail("");

            assertThatThrownBy(() -> service.createUser(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email");
        }

        @Test
        void horsEnseignant_nomObligatoire() {
            CreateSchoolUserRequest r = requete(UserRole.SURVEILLANT);
            r.setNomComplet(null);

            assertThatThrownBy(() -> service.createUser(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nom complet");
        }

        @Test
        void emailDejaUtilise_conflit() {
            when(schoolUserRepository.existsByTenantIdAndEmail(TENANT, "surveillant@ecole.tn")).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(requete(UserRole.SURVEILLANT)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("surveillant@ecole.tn");
        }

        @ParameterizedTest
        @CsvSource({"SCHOOL_ADMIN", "SURVEILLANT", "PARENT", "STUDENT"})
        void chaqueRoleDonneLeRoleKeycloakDuMemeNom(UserRole role) {
            keycloakCree(role.name());
            when(schoolUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SchoolUserResponse reponse = service.createUser(requete(role));

            assertThat(reponse.getRole()).isEqualTo(role);
            assertThat(reponse.getMatiere()).isNull();
        }

        @Test
        void cheminNominal_envoieLEmailDeBienvenueEtRenvoieLesIdentifiants() {
            keycloakCree("SURVEILLANT");
            when(schoolUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SchoolUserResponse reponse = service.createUser(requete(UserRole.SURVEILLANT));

            assertThat(reponse.getUsername()).isEqualTo("mhaddad");
            assertThat(reponse.getTempPassword()).isEqualTo("Tmp!123");
            assertThat(reponse.getKeycloakUserId()).isEqualTo("kc-new");

            ArgumentCaptor<WelcomeEmailData> email = ArgumentCaptor.forClass(WelcomeEmailData.class);
            verify(emailService).sendWelcomeEmail(email.capture());
            assertThat(email.getValue().toEmail()).isEqualTo("surveillant@ecole.tn");
            assertThat(email.getValue().loginUrl()).isEqualTo("https://ecole.tn/etablissement/login");
        }

        @Test
        void echecKeycloak_rienNEstEnregistre() {
            when(tenantService.getKeycloakGroupId(TENANT)).thenReturn(GROUPE);
            when(keycloakAdminService.createUser(any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("Keycloak injoignable"));

            assertThatThrownBy(() -> service.createUser(requete(UserRole.SURVEILLANT)))
                    .hasMessage("Keycloak injoignable");
            verify(schoolUserRepository, never()).save(any());
        }

        @Test
        void echecBase_leCompteKeycloakEstSupprime() {
            keycloakCree("SURVEILLANT");
            when(schoolUserRepository.save(any())).thenThrow(new IllegalStateException("contrainte violée"));

            assertThatThrownBy(() -> service.createUser(requete(UserRole.SURVEILLANT)))
                    .hasMessage("contrainte violée");
            verify(keycloakAdminService).deleteUser("kc-new");
            verify(emailService, never()).sendWelcomeEmail(any());
        }

        @Test
        void echecDuRollbackKeycloak_nemasquePasLErreurDOrigine() {
            keycloakCree("SURVEILLANT");
            when(schoolUserRepository.save(any())).thenThrow(new IllegalStateException("contrainte violée"));
            doThrow(new IllegalStateException("suppression refusée")).when(keycloakAdminService).deleteUser("kc-new");

            assertThatThrownBy(() -> service.createUser(requete(UserRole.SURVEILLANT)))
                    .hasMessage("contrainte violée");
        }
    }

    // ── lecture ───────────────────────────────────────────────────────────────

    @Nested
    class Lecture {

        @Test
        void listes_filtreesParTenant() {
            when(schoolUserRepository.findByTenantId(TENANT)).thenReturn(List.of(utilisateur(TENANT)));
            when(schoolUserRepository.findByTenantIdAndActifTrue(TENANT)).thenReturn(List.of());
            when(schoolUserRepository.findByTenantIdAndRole(TENANT, UserRole.SURVEILLANT))
                    .thenReturn(List.of(utilisateur(TENANT)));

            assertThat(service.getAllUsers()).extracting(SchoolUserResponse::getId).containsExactly(USER_ID);
            assertThat(service.getActiveUsers()).isEmpty();
            assertThat(service.getUsersByRole(UserRole.SURVEILLANT)).hasSize(1);
        }

        @Test
        void parId_trouve() {
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.of(utilisateur(TENANT)));

            assertThat(service.getUserById(USER_ID).getEmail()).isEqualTo("a@ecole.tn");
        }

        @Test
        void parId_dUnAutreEtablissement_introuvable() {
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.of(utilisateur("autre")));

            assertThatThrownBy(() -> service.getUserById(USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void utilisateurCourant_sansJeton_null() {
            when(jwtClaimsExtractor.getUserId()).thenReturn(null);

            assertThat(service.getCurrentUserId()).isNull();
            verify(schoolUserRepository, never()).findByKeycloakUserId(any());
        }

        @Test
        void utilisateurCourant_duTenant() {
            when(jwtClaimsExtractor.getUserId()).thenReturn("kc-11");
            when(schoolUserRepository.findByKeycloakUserId("kc-11")).thenReturn(Optional.of(utilisateur(TENANT)));

            assertThat(service.getCurrentUserId()).isEqualTo(USER_ID);
        }

        @Test
        void utilisateurCourant_dUnAutreTenant_null() {
            when(jwtClaimsExtractor.getUserId()).thenReturn("kc-11");
            when(schoolUserRepository.findByKeycloakUserId("kc-11")).thenReturn(Optional.of(utilisateur("autre")));

            assertThat(service.getCurrentUserId()).isNull();
        }
    }

    // ── modification, activation ─────────────────────────────────────────────

    @Nested
    class Modification {

        @Test
        void seulsLesChampsRenseignesSontModifies() {
            SchoolUser u = utilisateur(TENANT);
            u.setMatiere("Arabe");
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.of(u));
            when(schoolUserRepository.save(u)).thenReturn(u);

            service.updateUser(USER_ID, new UpdateSchoolUserRequest(" ", null, "55111222"));

            assertThat(u.getNomComplet()).isEqualTo("Ali Mansour");
            assertThat(u.getMatiere()).isEqualTo("Arabe");
            assertThat(u.getTelephone()).isEqualTo("55111222");
        }

        @Test
        void tousLesChampsModifies() {
            SchoolUser u = utilisateur(TENANT);
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.of(u));
            when(schoolUserRepository.save(u)).thenReturn(u);

            SchoolUserResponse r = service.updateUser(USER_ID, new UpdateSchoolUserRequest("Ali M.", "Physique", null));

            assertThat(r.getNomComplet()).isEqualTo("Ali M.");
            assertThat(r.getMatiere()).isEqualTo("Physique");
        }

        @Test
        void modificationDUnAutreEtablissement_introuvable() {
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateUser(USER_ID, new UpdateSchoolUserRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void desactivation_coupeAussiKeycloak() {
            SchoolUser u = utilisateur(TENANT);
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.of(u));
            when(schoolUserRepository.save(u)).thenReturn(u);

            assertThat(service.deactivateUser(USER_ID).isActif()).isFalse();
            verify(keycloakAdminService).disableUser("kc-11");
        }

        @Test
        void reactivation_rouvreAussiKeycloak() {
            SchoolUser u = utilisateur(TENANT);
            u.setActif(false);
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.of(u));
            when(schoolUserRepository.save(u)).thenReturn(u);

            assertThat(service.reactivateUser(USER_ID).isActif()).isTrue();
            verify(keycloakAdminService).enableUser("kc-11");
        }

        @Test
        void desactivationEtReactivation_dUnAutreEtablissement_introuvables() {
            when(schoolUserRepository.findById(USER_ID)).thenReturn(Optional.of(utilisateur("autre")));

            assertThatThrownBy(() -> service.deactivateUser(USER_ID)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.reactivateUser(USER_ID)).isInstanceOf(ResourceNotFoundException.class);
            verify(keycloakAdminService, never()).disableUser(any());
            verify(keycloakAdminService, never()).enableUser(any());
        }
    }
}
