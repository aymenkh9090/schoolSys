package tn.wtm.school.planning.constraints.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.planning.constraints.dto.request.ConstraintProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.CreateDefaultProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.ConstraintSettingRequest;
import tn.wtm.school.planning.constraints.dto.response.ConstraintProfileResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSettingResponse;
import tn.wtm.school.planning.constraints.entity.ConstraintDefinition;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;
import tn.wtm.school.planning.constraints.entity.ConstraintSetting;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;
import tn.wtm.school.planning.constraints.mapper.ConstraintMapper;
import tn.wtm.school.planning.constraints.repository.ConstraintDefinitionRepository;
import tn.wtm.school.planning.constraints.repository.ConstraintProfileRepository;
import tn.wtm.school.planning.constraints.repository.ConstraintSettingRepository;
import tn.wtm.school.planning.constraints.repository.CustomConstraintRepository;
import tn.wtm.school.planning.constraints.service.ConstraintParameters;
import tn.wtm.school.planning.constraints.service.ConstraintProfileService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConstraintProfileServiceImpl extends TenantService implements ConstraintProfileService {

    private final ConstraintProfileRepository profileRepository;
    private final ConstraintSettingRepository settingRepository;
    private final ConstraintDefinitionRepository definitionRepository;
    private final CustomConstraintRepository customConstraintRepository;
    private final ConstraintMapper mapper;
    private final ConstraintParameters constraintParameters;

    @Override
    @Transactional
    public ConstraintProfileResponse create(ConstraintProfileRequest request) {
        String tenantId = tenantId();
        if (profileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(
                tenantId, request.getName(), request.getAcademicYearId())) {
            throw new ConflictException(
                    "Un profil avec ce nom existe deja pour cette annee scolaire");
        }
        ConstraintProfile profile = mapper.toProfile(request);
        profile.setTenantId(tenantId);
        // Un seul profil actif par annee scolaire : c'est l'hypothese du solveur,
        // qui retient le profil actif de l'annee quand la generation n'en precise
        // aucun. Un nouveau profil ne prend donc la main que si on le demande
        // explicitement (on desactive alors l'ancien), ou s'il est le premier.
        boolean claimsActive = Boolean.TRUE.equals(profile.getActive());
        boolean active = claimsActive || !hasActiveProfile(tenantId, profile.getAcademicYearId());
        profile.setActive(active);
        if (active) {
            // Avant d'inserer : l'index unique partiel refuserait le nouvel actif
            // tant que l'ancien l'est encore (Hibernate ordonne les INSERT avant
            // les UPDATE au flush, d'ou le flush explicite dans deactivateSiblings).
            deactivateSiblings(tenantId, profile.getAcademicYearId(), null);
        }
        return mapper.toProfileResponse(profileRepository.save(profile));
    }

    @Override
    @Transactional
    public ConstraintProfileResponse createDefault(CreateDefaultProfileRequest request) {
        String tenantId = tenantId();

        if (profileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(
                tenantId, request.getName(), request.getSchoolYearId())) {
            throw new ConflictException(
                    "Un profil avec ce nom existe deja pour cette annee scolaire");
        }

        ConstraintProfile profile = ConstraintProfile.builder()
                .name(request.getName())
                .academicYearId(request.getSchoolYearId())
                // Creer un profil de travail ne doit pas changer sous les pieds de
                // l'utilisateur le profil que le solveur utilisera : on n'active que
                // le premier profil de l'annee, l'activation des suivants est un
                // geste explicite (voir activate).
                .active(!hasActiveProfile(tenantId, request.getSchoolYearId()))
                .build();
        profile.setTenantId(tenantId);
        profile = profileRepository.save(profile);

        List<ConstraintDefinition> definitions = definitionRepository.findAllByOrderByCategoryAscCodeAsc();
        for (ConstraintDefinition def : definitions) {
            ImportanceLevel importance = def.getDefaultImportance() != null
                    ? def.getDefaultImportance()
                    : ImportanceLevel.MEDIUM;

            // On stocke les VALEURS par défaut, pas le schéma : le solveur lit des
            // seuils, pas des descriptions de champs. Copier le schéma tel quel
            // produisait un profil d'apparence correcte dont aucun seuil n'était
            // exploitable — la contrainte s'affichait active et ne pénalisait rien.
            ConstraintSetting setting = ConstraintSetting.builder()
                    .enabled(Boolean.TRUE.equals(def.getDefaultEnabled()))
                    .importance(importance)
                    .weight(importanceToWeight(importance))
                    .parametersJson(constraintParameters.materializeDefaults(def.getParameterSchema()))
                    .build();
            setting.setTenantId(tenantId);
            setting.setProfile(profile);
            setting.setDefinition(def);
            settingRepository.save(setting);
            // Les deux cotes de la relation : dans la meme transaction, le profil
            // relu vient du contexte de persistance, avec sa collection telle
            // qu'on l'a laissee. Sans cet ajout, la reponse de creation annonce
            // « 0 contrainte » alors que la base en contient 18.
            profile.getSettings().add(setting);
        }

        return mapper.toProfileResponse(loadProfileWithSettings(profile.getIdConstraintProfile()));
    }

    @Override
    public List<ConstraintProfileResponse> findAll() {
        return mapper.toProfileResponseList(
                profileRepository.findByTenantIdOrderByNameAsc(tenantId()));
    }

    @Override
    public ConstraintProfileResponse findById(Long id) {
        return mapper.toProfileResponse(loadProfileWithSettings(id));
    }

    @Override
    @Transactional
    public ConstraintProfileResponse activate(Long id) {
        String tenantId = tenantId();
        ConstraintProfile profile = loadProfile(id, tenantId);
        deactivateSiblings(tenantId, profile.getAcademicYearId(), profile.getIdConstraintProfile());
        profile.setActive(Boolean.TRUE);
        profileRepository.save(profile);
        return mapper.toProfileResponse(loadProfileWithSettings(id));
    }

    @Override
    @Transactional
    public void deleteProfile(Long id) {
        String tenantId = tenantId();
        ConstraintProfile profile = loadProfile(id, tenantId);
        boolean wasActive = Boolean.TRUE.equals(profile.getActive());
        Long academicYearId = profile.getAcademicYearId();

        // Les reglages du catalogue partent en cascade JPA (orphanRemoval), pas les
        // regles personnalisees : rien ne les rattache au profil cote entite. Sans
        // cette suppression explicite, la cle etrangere fait echouer la requete.
        customConstraintRepository.deleteByTenantIdAndProfile_IdConstraintProfile(tenantId, id);

        profileRepository.delete(profile);
        profileRepository.flush();   // le DELETE doit precéder la promotion : un seul actif a la fois

        // Supprimer le profil actif laisserait l'annee sans profil de reference :
        // la generation echouerait sur "Aucun profil de contraintes actif". On
        // promeut le profil restant le plus recent de la meme annee.
        if (wasActive) {
            profileRepository
                    .findFirstByTenantIdAndAcademicYearIdOrderByIdConstraintProfileDesc(tenantId, academicYearId)
                    .ifPresent(replacement -> {
                        replacement.setActive(Boolean.TRUE);
                        profileRepository.save(replacement);
                    });
        }
    }

    @Override
    @Transactional
    public ConstraintSettingResponse addSetting(Long profileId, ConstraintSettingRequest request) {
        String tenantId = tenantId();
        ConstraintProfile profile = loadProfile(profileId, tenantId);
        ConstraintDefinition definition = loadDefinition(requireId(request.getConstraintDefinitionId()));

        if (settingRepository.existsByTenantIdAndProfile_IdConstraintProfileAndDefinition_IdConstraintDefinition(
                tenantId, profile.getIdConstraintProfile(), definition.getIdConstraintDefinition())) {
            throw new ConflictException("Cette contrainte est deja configuree dans ce profil");
        }

        ConstraintSetting setting = mapper.toSetting(request);
        setting.setTenantId(tenantId);
        setting.setProfile(profile);
        setting.setDefinition(definition);
        applyDefaults(setting, definition);

        return mapper.toSettingResponse(settingRepository.save(setting));
    }

    @Override
    @Transactional
    public ConstraintSettingResponse updateSetting(Long settingId, ConstraintSettingRequest request) {
        ConstraintSetting setting = loadSetting(settingId, tenantId());

        if (request.getEnabled() != null) {
            setting.setEnabled(request.getEnabled());
        }
        if (request.getImportance() != null) {
            setting.setImportance(request.getImportance());
            if (request.getWeight() == null) {
                setting.setWeight(importanceToWeight(request.getImportance()));
            }
        }
        if (request.getWeight() != null) {
            setting.setWeight(request.getWeight());
        }
        if (request.getParametersJson() != null) {
            setting.setParametersJson(request.getParametersJson());
        }

        return mapper.toSettingResponse(settingRepository.save(setting));
    }

    @Override
    @Transactional
    public void deleteSetting(Long settingId) {
        settingRepository.delete(loadSetting(settingId, tenantId()));
    }

    @Override
    public List<ConstraintSettingResponse> findActiveSettings(Long profileId) {
        String tenantId = tenantId();
        loadProfile(profileId, tenantId);
        return mapper.toSettingResponseList(
                settingRepository.findActiveByProfileAndTenantId(profileId, tenantId));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private boolean hasActiveProfile(String tenantId, Long academicYearId) {
        return !profileRepository
                .findByTenantIdAndAcademicYearIdAndActiveTrue(tenantId, academicYearId)
                .isEmpty();
    }

    /**
     * Desactive tous les autres profils actifs de la meme annee scolaire. La
     * boucle traite volontairement une liste : avant l'introduction de cette
     * regle, tous les profils etaient actifs, et le rattrapage doit fonctionner
     * meme si la base en contient encore plusieurs.
     */
    private void deactivateSiblings(String tenantId, Long academicYearId, Long keepId) {
        for (ConstraintProfile other : profileRepository
                .findByTenantIdAndAcademicYearIdAndActiveTrue(tenantId, academicYearId)) {
            if (keepId == null || !keepId.equals(other.getIdConstraintProfile())) {
                other.setActive(Boolean.FALSE);
                // saveAndFlush : l'index unique partiel est verifie immediatement,
                // la desactivation doit donc atteindre la base avant l'ecriture du
                // profil qui prend la main.
                profileRepository.saveAndFlush(other);
            }
        }
    }

    private void applyDefaults(ConstraintSetting setting, ConstraintDefinition definition) {
        if (setting.getEnabled() == null) {
            setting.setEnabled(definition.getDefaultEnabled());
        }
        ImportanceLevel importance = setting.getImportance() != null
                ? setting.getImportance()
                : definition.getDefaultImportance();
        setting.setImportance(importance);
        if (setting.getWeight() == null) {
            setting.setWeight(importanceToWeight(importance));
        }
        if (setting.getParametersJson() == null || setting.getParametersJson().isBlank()) {
            setting.setParametersJson(definition.getParameterSchema());
        }
    }

    private static int importanceToWeight(ImportanceLevel level) {
        if (level == null) return 0;
        return switch (level) {
            case CRITICAL -> 1000;
            case HIGH     -> 100;
            case MEDIUM   -> 10;
            case LOW      -> 1;
        };
    }

    private ConstraintProfile loadProfile(Long id, String tenantId) {
        return profileRepository.findByIdConstraintProfileAndTenantId(requireId(id), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil de contraintes introuvable avec l'ID : " + id));
    }

    private ConstraintProfile loadProfileWithSettings(Long id) {
        return profileRepository.findByIdWithSettingsAndTenantId(requireId(id), tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil de contraintes introuvable avec l'ID : " + id));
    }

    private ConstraintDefinition loadDefinition(Long id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contrainte introuvable avec l'ID : " + id));
    }

    private ConstraintSetting loadSetting(Long id, String tenantId) {
        return settingRepository.findByIdConstraintSettingAndTenantId(requireId(id), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Configuration de contrainte introuvable avec l'ID : " + id));
    }

    private static Long requireId(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant est obligatoire");
        }
        return id;
    }

    private String tenantId() {
        return currentTenant();
    }
}
