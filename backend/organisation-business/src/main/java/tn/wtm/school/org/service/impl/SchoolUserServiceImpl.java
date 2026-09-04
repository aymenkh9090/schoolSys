package tn.wtm.school.org.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.security.constants.RoleConstants;
import tn.wtm.school.security.email.EmailService;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;
import tn.wtm.school.security.email.WelcomeEmailData;
import tn.wtm.school.security.keycloak.dto.KeycloakCreatedUserDTO;
import tn.wtm.school.security.keycloak.service.KeycloakAdminService;
import tn.wtm.school.tenant.service.TenantService;
import tn.wtm.school.org.dto.request.CreateSchoolUserRequest;
import tn.wtm.school.org.dto.request.UpdateSchoolUserRequest;
import tn.wtm.school.org.dto.response.SchoolUserResponse;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.enums.UserRole;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.service.SchoolUserService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolUserServiceImpl implements SchoolUserService {

    private final SchoolUserRepository schoolUserRepository;
    private final TeacherRepository     teacherRepository;
    private final KeycloakAdminService  keycloakAdminService;
    private final TenantService         tenantService;
    private final EmailService          emailService;
    private final JwtClaimsExtractor    jwtClaimsExtractor;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    // ── Création ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SchoolUserResponse createUser(CreateSchoolUserRequest request) {
        String tenantId = TenantContext.getRequiredTenantId();
        log.info("[SchoolUser] Création user email='{}' role='{}' tenant='{}'",
                 request.getEmail(), request.getRole(), tenantId);

        // Pour un enseignant, le compte se greffe sur une fiche existante du module
        // Enseignants (source unique) : email, nom, matière et téléphone en sont dérivés.
        Teacher teacher = null;
        String email;
        String nomComplet;
        String matiere;
        String telephone;

        if (request.getRole() == UserRole.TEACHER) {
            if (request.getTeacherId() == null) {
                throw new IllegalArgumentException(
                        "Sélectionnez la fiche enseignant à lier (teacherId). "
                        + "Créez-la d'abord dans Gestion des enseignants si nécessaire.");
            }
            teacher = teacherRepository.findById(request.getTeacherId())
                    .filter(t -> tenantId.equals(t.getTenantId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Fiche enseignant introuvable : " + request.getTeacherId()));
            if (!StringUtils.hasText(teacher.getEmail())) {
                throw new IllegalArgumentException(
                        "La fiche de " + teacher.getNom() + " " + teacher.getPrenom()
                        + " n'a pas d'email — complétez-la dans Gestion des enseignants avant de créer le compte.");
            }
            if (schoolUserRepository.existsByTenantIdAndTeacher_IdEnseignant(
                    tenantId, teacher.getIdEnseignant())) {
                throw new ConflictException(
                        "Un compte existe déjà pour l'enseignant " + teacher.getNom() + " " + teacher.getPrenom());
            }
            email      = teacher.getEmail();
            nomComplet = (teacher.getNom() + " " + teacher.getPrenom()).trim();
            matiere    = teacher.getSpecialite();
            telephone  = teacher.getTelephone();
        } else {
            if (!StringUtils.hasText(request.getEmail())) {
                throw new IllegalArgumentException("L'email est obligatoire");
            }
            if (!StringUtils.hasText(request.getNomComplet())) {
                throw new IllegalArgumentException("Le nom complet est obligatoire");
            }
            email      = request.getEmail();
            nomComplet = request.getNomComplet();
            matiere    = null; // la matière n'a de sens que pour un enseignant
            telephone  = request.getTelephone();
        }

        if (schoolUserRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new ConflictException(
                    "Un utilisateur avec l'email '" + email + "' existe déjà dans cet établissement");
        }

        String keycloakGroupId = tenantService.getKeycloakGroupId(tenantId);
        String keycloakRole    = mapToKeycloakRole(request.getRole());

        String keycloakUserId = null;
        KeycloakCreatedUserDTO keycloakUser;

        try {
            keycloakUser  = keycloakAdminService.createUser(
                    email, nomComplet,
                    tenantId, keycloakGroupId, keycloakRole);
            keycloakUserId = keycloakUser.getUserId();
            log.info("[SchoolUser] User Keycloak créé: userId={}", keycloakUserId);
        } catch (Exception e) {
            log.error("[SchoolUser] Échec création Keycloak email={}: {}", email, e.getMessage(), e);
            throw e;
        }

        try {
            SchoolUser user = SchoolUser.builder()
                    .keycloakUserId(keycloakUserId)
                    .email(email)
                    .nomComplet(nomComplet)
                    .role(request.getRole())
                    .matiere(matiere)
                    .telephone(telephone)
                    .teacher(teacher)
                    .actif(true)
                    .build();

            SchoolUser saved = schoolUserRepository.save(user);
            log.info("[SchoolUser] User BDD sauvegardé: id={}", saved.getId());

            emailService.sendWelcomeEmail(new WelcomeEmailData(
                    email, nomComplet, keycloakUser.getUsername(),
                    keycloakUser.getTempPassword(),
                    frontendBaseUrl + "/etablissement/login",
                    null
            ));

            SchoolUserResponse response = SchoolUserResponse.from(saved);
            response.setUsername(keycloakUser.getUsername());
            response.setTempPassword(keycloakUser.getTempPassword());
            return response;

        } catch (Exception e) {
            log.error("[SchoolUser] Échec BDD — rollback Keycloak userId={}", keycloakUserId, e);
            if (keycloakUserId != null) {
                try {
                    keycloakAdminService.deleteUser(keycloakUserId);
                    log.warn("[SchoolUser][Rollback] User Keycloak supprimé: {}", keycloakUserId);
                } catch (Exception rollbackEx) {
                    log.error("[SchoolUser][Rollback] Échec rollback Keycloak userId={}: {}",
                              keycloakUserId, rollbackEx.getMessage());
                }
            }
            throw e;
        }
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    @Override
    public List<SchoolUserResponse> getAllUsers() {
        String tenantId = TenantContext.getRequiredTenantId();
        return schoolUserRepository.findByTenantId(tenantId)
                .stream().map(SchoolUserResponse::from).toList();
    }

    @Override
    public List<SchoolUserResponse> getActiveUsers() {
        String tenantId = TenantContext.getRequiredTenantId();
        return schoolUserRepository.findByTenantIdAndActifTrue(tenantId)
                .stream().map(SchoolUserResponse::from).toList();
    }

    @Override
    public List<SchoolUserResponse> getUsersByRole(UserRole role) {
        String tenantId = TenantContext.getRequiredTenantId();
        return schoolUserRepository.findByTenantIdAndRole(tenantId, role)
                .stream().map(SchoolUserResponse::from).toList();
    }

    @Override
    public SchoolUserResponse getUserById(Long userId) {
        String tenantId = TenantContext.getRequiredTenantId();
        SchoolUser user = schoolUserRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable : " + userId));
        return SchoolUserResponse.from(user);
    }

    @Override
    public Long getCurrentUserId() {
        String keycloakUserId = jwtClaimsExtractor.getUserId();
        if (keycloakUserId == null) {
            return null;
        }
        String tenantId = TenantContext.getRequiredTenantId();
        return schoolUserRepository.findByKeycloakUserId(keycloakUserId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .map(SchoolUser::getId)
                .orElse(null);
    }

    // ── Modification ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SchoolUserResponse updateUser(Long userId, UpdateSchoolUserRequest request) {
        String tenantId = TenantContext.getRequiredTenantId();
        SchoolUser user = schoolUserRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable : " + userId));

        if (StringUtils.hasText(request.getNomComplet())) {
            user.setNomComplet(request.getNomComplet());
        }
        if (request.getMatiere() != null) {
            user.setMatiere(request.getMatiere());
        }
        if (request.getTelephone() != null) {
            user.setTelephone(request.getTelephone());
        }

        return SchoolUserResponse.from(schoolUserRepository.save(user));
    }

    // ── Activation / Désactivation ────────────────────────────────────────────

    @Override
    @Transactional
    public SchoolUserResponse deactivateUser(Long userId) {
        String tenantId = TenantContext.getRequiredTenantId();
        SchoolUser user = schoolUserRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable : " + userId));

        keycloakAdminService.disableUser(user.getKeycloakUserId());
        user.setActif(false);
        log.info("[SchoolUser] User désactivé: id={}", userId);
        return SchoolUserResponse.from(schoolUserRepository.save(user));
    }

    @Override
    @Transactional
    public SchoolUserResponse reactivateUser(Long userId) {
        String tenantId = TenantContext.getRequiredTenantId();
        SchoolUser user = schoolUserRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable : " + userId));

        keycloakAdminService.enableUser(user.getKeycloakUserId());
        user.setActif(true);
        log.info("[SchoolUser] User réactivé: id={}", userId);
        return SchoolUserResponse.from(schoolUserRepository.save(user));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String mapToKeycloakRole(UserRole role) {
        return switch (role) {
            case SCHOOL_ADMIN -> RoleConstants.SCHOOL_ADMIN;
            case TEACHER      -> RoleConstants.TEACHER;
            case SURVEILLANT  -> RoleConstants.SURVEILLANT;
            case PARENT       -> RoleConstants.PARENT;
            case STUDENT      -> RoleConstants.STUDENT;
        };
    }
}
