package tn.wtm.school.org.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.BulkCreateClassGroupRequest;
import tn.wtm.school.org.dto.request.ClassGroupRequest;
import tn.wtm.school.org.dto.request.LevelRequest;
import tn.wtm.school.org.dto.response.BulkCreateClassGroupResult;
import tn.wtm.school.org.dto.response.ClassGroupResponse;
import tn.wtm.school.org.dto.response.LevelResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.mapper.ClassGroupMapper;
import tn.wtm.school.org.mapper.LevelMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.service.LevelService;

import tn.wtm.school.org.enums.Specialite;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LevelServiceImpl extends TenantService implements LevelService {

    private final LevelRepository levelRepository;
    private final ClassGroupRepository classGroupRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final SubjectLevelRepository subjectLevelRepository;
    private final LevelMapper levelMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ObjectsValidator<LevelRequest> levelValidator;
    private final ObjectsValidator<ClassGroupRequest> classGroupValidator;

    // ----- Level -------------------------------------------------------------

    @Override
    @Transactional
    public LevelResponse createLevel(LevelRequest levelRequest) {
        levelValidator.validate(levelRequest);
        String tenantId = currentTenant();

        if (levelRepository.existsByTenantIdAndCode(tenantId, levelRequest.getCode())) {
            throw new ConflictException("Un niveau avec le code " + levelRequest.getCode() + " existe deja");
        }

        Level level = levelMapper.toEntity(levelRequest);
        return levelMapper.toResponse(levelRepository.save(level));
    }

    @Override
    public LevelResponse getLevelById(Long id) {
        return levelMapper.toResponse(findLevelById(id));
    }

    @Override
    public Page<LevelResponse> getAllLevels(Pageable pageable) {
        return levelRepository.findByTenantId(currentTenant(), pageable)
                .map(levelMapper::toResponse);
    }

    @Override
    public List<LevelResponse> getActivesLevels() {
        return levelMapper.toResponseList(
                levelRepository.findByTenantIdAndEstActifTrueOrderByNomAsc(currentTenant()));
    }

    @Override
    @Transactional
    public LevelResponse updateLevel(LevelRequest levelRequest, Long id) {
        levelValidator.validate(levelRequest);
        String tenantId = currentTenant();

        Level level = findLevelById(id);
        if (levelRepository.existsByTenantIdAndCodeAndIdNiveauNot(tenantId, levelRequest.getCode(), id)) {
            throw new ConflictException("Un niveau avec le code " + levelRequest.getCode() + " existe deja");
        }

        levelMapper.updateFromDto(levelRequest, level);
        return levelMapper.toResponse(levelRepository.save(level));
    }

    @Override
    @Transactional
    public LevelResponse toggleLevelStatus(Long id, Boolean estActif) {
        if (estActif == null) {
            throw new BadRequestException("Le statut du niveau est obligatoire");
        }

        Level level = findLevelById(id);
        level.setEstActif(estActif);
        return levelMapper.toResponse(levelRepository.save(level));
    }

    @Override
    @Transactional
    public void deleteLevel(Long id) {
        Level level = findLevelById(id);

        if (!classGroupRepository.findByLevel_IdNiveau(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer un niveau rattache a des classes");
        }

        if (!subjectLevelRepository.findByLevel_IdNiveau(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer un niveau rattache a des matieres");
        }

        levelRepository.delete(level);
    }

    @Override
    public Boolean existsByCode(String code) {
        if (code == null || code.isBlank()) {
            return Boolean.FALSE;
        }
        return levelRepository.existsByTenantIdAndCode(currentTenant(), code);
    }

    @Override
    public LevelResponse getLevelWithClasses(Long id) {
        Level level = levelRepository.findByIdWithClasses(requireId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Niveau avec ID " + id + " introuvable"));
        return levelMapper.toResponse(level);
    }

    @Override
    public LevelResponse getLevelWithSubjects(Long id) {
        Level level = levelRepository.findByIdWithSubjects(requireId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Niveau avec ID " + id + " introuvable"));
        return levelMapper.toResponse(level);
    }

    // ----- Class Group -------------------------------------------------------

    @Override
    @Transactional
    public ClassGroupResponse createClass(ClassGroupRequest classGroupRequest) {
        classGroupValidator.validate(classGroupRequest);

        Level level = findLevelById(classGroupRequest.getLevelId());
        SchoolYear schoolYear = findSchoolYearById(classGroupRequest.getSchoolYearId());
        validateClassGroupCodeUniqueness(
                classGroupRequest.getCode(),
                classGroupRequest.getLevelId(),
                classGroupRequest.getSchoolYearId(),
                null
        );

        ClassGroup classGroup = classGroupMapper.toEntity(classGroupRequest);
        classGroup.setLevel(level);
        classGroup.setSchoolYear(schoolYear);

        return classGroupMapper.toResponse(classGroupRepository.save(classGroup));
    }

    @Override
    public ClassGroupResponse getClassGroupById(Long id) {
        return classGroupMapper.toResponse(findClassGroupById(id));
    }

    @Override
    public List<ClassGroupResponse> getAllClassGroups() {
        return classGroupMapper.toResponseList(classGroupRepository.findByTenantId(currentTenant()));
    }

    @Override
    public List<ClassGroupResponse> getClassGroupsByLevel(Long levelId) {
        ensureLevelExists(levelId);
        return classGroupMapper.toResponseList(
                classGroupRepository.findByTenantIdAndLevel_IdNiveau(currentTenant(), levelId));
    }

    @Override
    public List<ClassGroupResponse> getClassGroupsBySchoolYear(Long schoolYearId) {
        ensureSchoolYearExists(schoolYearId);
        return classGroupMapper.toResponseList(
                classGroupRepository.findByTenantIdAndSchoolYear_IdAnnee(currentTenant(), schoolYearId));
    }

    @Override
    public List<ClassGroupResponse> getClassGroupsByLevelAndYear(Long levelId, Long schoolYearId) {
        ensureLevelExists(levelId);
        ensureSchoolYearExists(schoolYearId);
        return classGroupMapper.toResponseList(
                classGroupRepository.findByTenantIdAndLevel_IdNiveauAndSchoolYear_IdAnnee(
                        currentTenant(), levelId, schoolYearId));
    }

    @Override
    public Page<ClassGroupResponse> getClassGroupsByYearPaged(Long schoolId, Pageable pageable) {
        ensureSchoolYearExists(schoolId);
        return classGroupRepository.findByTenantIdAndSchoolYear_IdAnnee(currentTenant(), schoolId, pageable)
                .map(classGroupMapper::toResponse);
    }

    @Override
    @Transactional
    public ClassGroupResponse updateClassGroup(Long id, ClassGroupRequest request) {
        classGroupValidator.validate(request);

        ClassGroup classGroup = findClassGroupById(id);
        Level level = findLevelById(request.getLevelId());
        SchoolYear schoolYear = findSchoolYearById(request.getSchoolYearId());
        validateClassGroupCodeUniqueness(
                request.getCode(),
                request.getLevelId(),
                request.getSchoolYearId(),
                id
        );

        classGroupMapper.updateFromDto(request, classGroup);
        classGroup.setLevel(level);
        classGroup.setSchoolYear(schoolYear);

        return classGroupMapper.toResponse(classGroupRepository.save(classGroup));
    }

    @Override
    @Transactional
    public ClassGroupResponse toggleClassGroupStatus(Long id, Boolean estActif) {
        if (estActif == null) {
            throw new BadRequestException("Le statut de la classe est obligatoire");
        }

        ClassGroup classGroup = findClassGroupById(id);
        classGroup.setEstActif(estActif);
        return classGroupMapper.toResponse(classGroupRepository.save(classGroup));
    }

    @Override
    @Transactional
    public void deleteClassGroup(Long id) {
        ClassGroup classGroup = classGroupRepository.findByIdWithAssignments(requireId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Classe avec ID " + id + " introuvable"));

        if (classGroup.getTeachingAssignments() != null && !classGroup.getTeachingAssignments().isEmpty()) {
            throw new ConflictException("Impossible de supprimer une classe avec des affectations");
        }

        classGroupRepository.delete(classGroup);
    }

    @Override
    public ClassGroupResponse getClassGroupWithAssignments(Long id) {
        ClassGroup classGroup = classGroupRepository.findByIdWithAssignments(requireId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Classe avec ID " + id + " introuvable"));
        return classGroupMapper.toResponse(classGroup);
    }

    @Override
    public Boolean existsByCodeInLevelAndYear(String code, Long levelId, Long schoolYearId) {
        if (code == null || code.isBlank() || levelId == null || schoolYearId == null) {
            return Boolean.FALSE;
        }
        return classGroupRepository.existsByCodeInLevelAndYear(code, levelId, schoolYearId, null);
    }

    @Override
    @Transactional
    public BulkCreateClassGroupResult bulkCreateClassGroups(BulkCreateClassGroupRequest request) {
        String tenantId = currentTenant();
        SchoolYear schoolYear = findSchoolYearById(request.schoolYearId());

        List<ClassGroupResponse> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (BulkCreateClassGroupRequest.LevelEntry entry : request.levels()) {
            Level level = levelRepository.findByTenantIdAndCode(tenantId, entry.levelCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Niveau introuvable pour ce tenant : " + entry.levelCode()));

            Specialite specialite = entry.specialite() != null ? entry.specialite() : Specialite.TCOM;

            for (int i = 1; i <= entry.count(); i++) {
                String code = entry.prefix() + i;

                if (classGroupRepository.existsByTenantIdAndCode(tenantId, code)) {
                    skipped.add(code);
                    continue;
                }

                ClassGroup classGroup = ClassGroup.builder()
                        .code(code)
                        .codeSpecialite(specialite)
                        .nbEleve(request.defaultSize())
                        .estActif(true)
                        .schoolYear(schoolYear)
                        .level(level)
                        .build();
                classGroup.setTenantId(tenantId);

                created.add(classGroupMapper.toResponse(classGroupRepository.save(classGroup)));
            }
        }

        return new BulkCreateClassGroupResult(created.size(), skipped.size(), created, skipped);
    }

    private Level findLevelById(Long id) {
        return levelRepository.findByTenantIdAndIdNiveau(currentTenant(), requireId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Niveau avec ID " + id + " introuvable"));
    }

    private SchoolYear findSchoolYearById(Long id) {
        return schoolYearRepository.findByTenantIdAndIdAnnee(currentTenant(), requireId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Annee scolaire avec ID " + id + " introuvable"));
    }

    private ClassGroup findClassGroupById(Long id) {
        return classGroupRepository.findByTenantIdAndIdClasse(currentTenant(), requireId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Classe avec ID " + id + " introuvable"));
    }

    private Long requireId(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant est obligatoire");
        }
        return id;
    }

    private void ensureLevelExists(Long levelId) {
        findLevelById(levelId);
    }

    private void ensureSchoolYearExists(Long schoolYearId) {
        findSchoolYearById(schoolYearId);
    }

    private void validateClassGroupCodeUniqueness(
            String code,
            Long levelId,
            Long schoolYearId,
            Long excludeId
    ) {
        if (classGroupRepository.existsByCodeInLevelAndYear(code, levelId, schoolYearId, excludeId)) {
            throw new ConflictException("Une classe avec le code " + code + " existe deja pour ce niveau et cette annee");
        }

        if (excludeId == null && classGroupRepository.existsByTenantIdAndCode(currentTenant(), code)) {
            throw new ConflictException("Une classe avec le code " + code + " existe deja");
        }

        if (excludeId != null && classGroupRepository.existsByCodeAndIdClasseNot(code, excludeId)) {
            throw new ConflictException("Une classe avec le code " + code + " existe deja");
        }
    }
}
