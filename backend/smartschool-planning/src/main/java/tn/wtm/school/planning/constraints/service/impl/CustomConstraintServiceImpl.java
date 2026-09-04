package tn.wtm.school.planning.constraints.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslCompiler;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslParser;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslValidator;
import tn.wtm.school.planning.constraints.dsl.DslValidationResult;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dto.request.CustomConstraintRequest;
import tn.wtm.school.planning.constraints.dto.request.DslAnalysisRequest;
import tn.wtm.school.planning.constraints.dto.response.CustomConstraintResponse;
import tn.wtm.school.planning.constraints.dto.response.DslAnalysisResponse;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;
import tn.wtm.school.planning.constraints.entity.CustomConstraint;
import tn.wtm.school.planning.constraints.enums.ConstraintSource;
import tn.wtm.school.planning.constraints.repository.ConstraintProfileRepository;
import tn.wtm.school.planning.constraints.repository.CustomConstraintRepository;
import tn.wtm.school.planning.constraints.service.CustomConstraintService;
import tn.wtm.school.planning.constraints.service.PlanningConflictService;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomConstraintServiceImpl extends TenantService implements CustomConstraintService {

    private final CustomConstraintRepository  repository;
    private final ConstraintProfileRepository profileRepository;
    private final ConstraintDslParser         parser;
    private final ConstraintDslValidator      validator;
    private final ConstraintDslCompiler       compiler;
    private final PlanningConflictService     conflictService;

    // ── analyse ───────────────────────────────────────────────────────────────

    @Override
    public DslAnalysisResponse analyze(DslAnalysisRequest request) {
        return conflictService.analyze(
                request.getDsl(),
                currentTenant(),
                request.getSchoolYearId(),
                request.getConstraintProfileId());
    }

    // ── écriture ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CustomConstraintResponse create(CustomConstraintRequest request) {
        String tenantId = currentTenant();
        String code     = normalizeCode(request.getCode());

        if (repository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw new ConflictException("Une règle personnalisée porte déjà le code « " + code + " ».");
        }

        ConstraintProfile profile = loadProfile(request.getConstraintProfileId(), tenantId);
        ConstraintDsl dsl = requireValid(request.getDsl());

        CustomConstraint entity = CustomConstraint.builder()
                .profile(profile)
                .code(code)
                .name(request.getName())
                .description(request.getDescription())
                .dslJson(parser.write(dsl))
                .scope(dsl.getScope())
                .severity(dsl.getSeverity())
                .weight(dsl.getWeight())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .source(request.getSource() == null ? ConstraintSource.MANUAL : request.getSource())
                .naturalLanguageRequest(request.getNaturalLanguageRequest())
                .summary(compiler.summarize(dsl))
                .build();
        entity.setTenantId(tenantId);

        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public CustomConstraintResponse update(Long id, CustomConstraintRequest request) {
        String tenantId = currentTenant();
        CustomConstraint entity = load(id, tenantId);

        if (request.getCode() != null) {
            String code = normalizeCode(request.getCode());
            if (!code.equalsIgnoreCase(entity.getCode())
                    && repository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
                throw new ConflictException("Une règle personnalisée porte déjà le code « " + code + " ».");
            }
            entity.setCode(code);
        }
        if (request.getConstraintProfileId() != null) {
            entity.setProfile(loadProfile(request.getConstraintProfileId(), tenantId));
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        if (request.getDsl() != null) {
            // Le DSL est revalidé intégralement : une règle enregistrée est une
            // règle qui a passé le contrôle, à chaque écriture et sans exception.
            ConstraintDsl dsl = requireValid(request.getDsl());
            entity.setDslJson(parser.write(dsl));
            entity.setScope(dsl.getScope());
            entity.setSeverity(dsl.getSeverity());
            entity.setWeight(dsl.getWeight());
            entity.setSummary(compiler.summarize(dsl));
        }

        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public CustomConstraintResponse setEnabled(Long id, boolean enabled) {
        CustomConstraint entity = load(id, currentTenant());
        entity.setEnabled(enabled);
        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(load(id, currentTenant()));
    }

    // ── lecture ───────────────────────────────────────────────────────────────

    @Override
    public List<CustomConstraintResponse> findAll(Long profileId) {
        String tenantId = currentTenant();
        List<CustomConstraint> rules = profileId == null
                ? repository.findByTenantIdOrderByCodeAsc(tenantId)
                : repository.findByTenantIdAndProfile_IdConstraintProfileOrderByCodeAsc(tenantId, profileId);
        return rules.stream().map(this::toResponse).toList();
    }

    @Override
    public CustomConstraintResponse findById(Long id) {
        return toResponse(load(id, currentTenant()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConstraintDsl requireValid(ConstraintDsl dsl) {
        if (dsl == null) {
            throw new BadRequestException("La définition DSL est obligatoire.");
        }
        DslValidationResult validation = validator.validate(dsl);
        if (!validation.isValid()) {
            throw new BadRequestException("Règle invalide : " + validation.errorMessage());
        }
        return dsl;
    }

    private CustomConstraint load(Long id, String tenantId) {
        return repository.findByIdCustomConstraintAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Règle personnalisée introuvable avec l'ID : " + id));
    }

    private ConstraintProfile loadProfile(Long profileId, String tenantId) {
        if (profileId == null) {
            throw new BadRequestException("Le profil de contraintes est obligatoire.");
        }
        return profileRepository.findByIdConstraintProfileAndTenantId(profileId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil de contraintes introuvable avec l'ID : " + profileId));
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code de la règle est obligatoire.");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CustomConstraintResponse toResponse(CustomConstraint entity) {
        return CustomConstraintResponse.builder()
                .idCustomConstraint(entity.getIdCustomConstraint())
                .constraintProfileId(entity.getProfile() == null
                        ? null : entity.getProfile().getIdConstraintProfile())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .dsl(parser.parse(entity.getDslJson()))
                .scope(entity.getScope())
                .severity(entity.getSeverity())
                .weight(entity.getWeight())
                .enabled(entity.getEnabled())
                .source(entity.getSource())
                .naturalLanguageRequest(entity.getNaturalLanguageRequest())
                .summary(entity.getSummary())
                .build();
    }
}
