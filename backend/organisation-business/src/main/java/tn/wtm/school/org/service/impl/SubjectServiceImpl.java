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
import tn.wtm.school.org.dto.request.SubjectLevelRequest;
import tn.wtm.school.org.dto.request.SubjectRequest;
import tn.wtm.school.org.dto.request.SubjectSessionTypeRequest;
import tn.wtm.school.org.dto.response.SubjectLevelResponse;
import tn.wtm.school.org.dto.response.SubjectResponse;
import tn.wtm.school.org.dto.response.SubjectSessionTypeResponse;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.mapper.SubjectLevelMapper;
import tn.wtm.school.org.mapper.SubjectMapper;
import tn.wtm.school.org.mapper.SubjectSessionTypeMapper;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.SubjectService;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectServiceImpl extends TenantService implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final SubjectLevelRepository subjectLevelRepository;
    private final SubjectSessionTypeRepository subjectSessionTypeRepository;
    private final LevelRepository levelRepository;
    private final PatternRepository patternRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final SubjectMapper subjectMapper;
    private final SubjectLevelMapper subjectLevelMapper;
    private final SubjectSessionTypeMapper subjectSessionTypeMapper;
    private final ObjectsValidator<SubjectRequest> subjectValidator;
    private final ObjectsValidator<SubjectLevelRequest> subjectLevelValidator;
    private final ObjectsValidator<SubjectSessionTypeRequest> subjectSessionTypeValidator;

    // ----- Subject -----------------------------------------------------------

    @Override
    @Transactional
    public SubjectResponse createSubject(SubjectRequest subjectRequest) {
        subjectValidator.validate(subjectRequest);

        if (subjectRepository.existsByTenantIdAndCodeMatiere(currentTenant(), subjectRequest.getCodeMatiere())) {
            throw new ConflictException("Une matiere avec le code " + subjectRequest.getCodeMatiere() + " existe deja");
        }

        Subject subject = subjectMapper.toEntity(subjectRequest);
        return subjectMapper.toResponseLight(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    public SubjectResponse updateSubject(Long id, SubjectRequest subjectRequest) {
        subjectValidator.validate(subjectRequest);

        Subject subject = findSubjectById(id);
        if (subjectRepository.existsByTenantIdAndCodeMatiereAndIdMatiereNot(currentTenant(), subjectRequest.getCodeMatiere(), id)) {
            throw new ConflictException("Une matiere avec le code " + subjectRequest.getCodeMatiere() + " existe deja");
        }

        subjectMapper.updateFromDto(subjectRequest, subject);
        return subjectMapper.toResponseLight(subjectRepository.save(subject));
    }

    @Override
    public SubjectResponse getSubjectById(Long id) {
        Subject subject = subjectRepository.findByIdWithLevels(requireId(id, "matiere"))
                .orElseThrow(() -> new ResourceNotFoundException("Matiere avec ID " + id + " introuvable"));
        return subjectMapper.toResponse(subject);
    }

    @Override
    public Page<SubjectResponse> getAllSubjects(Pageable pageable) {
        return subjectRepository.findByTenantId(currentTenant(), pageable)
                .map(subjectMapper::toResponseLight);
    }

    @Override
    public List<SubjectResponse> getEnseignedSubjects() {
        return subjectRepository.findByTenantIdAndEstEnseigneeTrueOrderByCodeMatiereAsc(currentTenant())
                .stream()
                .map(subjectMapper::toResponseLight)
                .toList();
    }

    @Override
    public List<SubjectResponse> getPrincipaleSubjects() {
        return subjectRepository.findByTenantIdAndEstPrincipaleTrue(currentTenant())
                .stream()
                .map(subjectMapper::toResponseLight)
                .toList();
    }

    @Override
    public List<SubjectResponse> searchSubjects(String search) {
        if (search == null || search.isBlank()) {
            return getEnseignedSubjects();
        }

        return subjectRepository.searchByLibOrCode(search.trim())
                .stream()
                .map(subjectMapper::toResponseLight)
                .toList();
    }

    @Override
    @Transactional
    public SubjectResponse toggleEnseignee(Long id, Boolean estEnseignee) {
        if (estEnseignee == null) {
            throw new BadRequestException("Le statut d'enseignement est obligatoire");
        }

        Subject subject = findSubjectById(id);
        subject.setEstEnseignee(estEnseignee);
        return subjectMapper.toResponseLight(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    public void deleteSubject(Long id) {
        Subject subject = findSubjectById(id);

        if (!subjectLevelRepository.findBySubject_IdMatiere(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer une matiere rattachee a des niveaux");
        }

        subjectRepository.delete(subject);
    }

    @Override
    public Boolean existsByCodeMatiere(String codeMatiere) {
        if (codeMatiere == null || codeMatiere.isBlank()) {
            return Boolean.FALSE;
        }
        return subjectRepository.existsByTenantIdAndCodeMatiere(currentTenant(), codeMatiere);
    }

    // ----- SubjectLevel ------------------------------------------------------

    @Override
    @Transactional
    public SubjectLevelResponse createSubjectLevel(SubjectLevelRequest dto) {
        subjectLevelValidator.validate(dto);

        Subject subject = findSubjectById(dto.getSubjectId());
        Level level = findLevelById(dto.getLevelId());

        if (subjectLevelRepository.existsBySubject_IdMatiereAndLevel_IdNiveau(dto.getSubjectId(), dto.getLevelId())) {
            throw new ConflictException("Cette matiere est deja affectee a ce niveau");
        }

        SubjectLevel subjectLevel = subjectLevelMapper.toEntity(dto);
        subjectLevel.setSubject(subject);
        subjectLevel.setLevel(level);

        return subjectLevelMapper.toResponseLight(subjectLevelRepository.save(subjectLevel));
    }

    @Override
    public SubjectLevelResponse getSubjectLevelById(Long id) {
        SubjectLevel subjectLevel = subjectLevelRepository
                .findByIdFullyLoaded(requireId(id, "matiere-niveau"))
                .orElseThrow(() -> new ResourceNotFoundException("Matiere-niveau avec ID " + id + " introuvable"));
        return subjectLevelMapper.toResponse(subjectLevel);
    }

    @Override
    public List<SubjectLevelResponse> getSubjectLevelsByLevel(Long levelId) {
        ensureLevelExists(levelId);
        return subjectLevelRepository.findByLevel_IdNiveau(levelId)
                .stream()
                .map(subjectLevelMapper::toResponseLight)
                .toList();
    }

    @Override
    public List<SubjectLevelResponse> getSubjectLevelsBySubject(Long subjectId) {
        ensureSubjectExists(subjectId);
        return subjectLevelRepository.findBySubject_IdMatiere(subjectId)
                .stream()
                .map(subjectLevelMapper::toResponseLight)
                .toList();
    }

    @Override
    public List<SubjectLevelResponse> getSubjectLevelsByLevelWithSessionTypes(Long levelId) {
        ensureLevelExists(levelId);
        return subjectLevelRepository.findByLevelWithSessionTypes(levelId)
                .stream()
                .map(subjectLevelMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubjectLevelResponse updateSubjectLevel(Long id, SubjectLevelRequest dto) {
        subjectLevelValidator.validate(dto);

        SubjectLevel subjectLevel = findSubjectLevelById(id);
        Subject subject = findSubjectById(dto.getSubjectId());
        Level level = findLevelById(dto.getLevelId());

        boolean duplicateExists = subjectLevelRepository
                .findBySubject_IdMatiereAndLevel_IdNiveau(dto.getSubjectId(), dto.getLevelId())
                .filter(existing -> !Objects.equals(existing.getIdNiveauMatiere(), id))
                .isPresent();
        if (duplicateExists) {
            throw new ConflictException("Cette matiere est deja affectee a ce niveau");
        }

        subjectLevelMapper.updateFromDto(dto, subjectLevel);
        subjectLevel.setSubject(subject);
        subjectLevel.setLevel(level);

        return subjectLevelMapper.toResponseLight(subjectLevelRepository.save(subjectLevel));
    }

    @Override
    @Transactional
    public void deleteSubjectLevel(Long id) {
        SubjectLevel subjectLevel = findSubjectLevelById(id);

        if (!patternRepository.findBySubjectLevel_IdNiveauMatiere(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer une matiere-niveau rattachee a des patterns");
        }

        if (!teachingAssignmentRepository.findBySubjectLevel_IdNiveauMatiere(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer une matiere-niveau rattachee a des affectations");
        }

        subjectLevelRepository.delete(subjectLevel);
    }

    @Override
    public boolean existsSubjectLevel(Long subjectId, Long levelId) {
        if (subjectId == null || levelId == null) {
            return false;
        }
        return subjectLevelRepository.existsBySubject_IdMatiereAndLevel_IdNiveau(subjectId, levelId);
    }

    @Override
    public SubjectLevelResponse getSubjectLevelFullyLoaded(Long id) {
        SubjectLevel subjectLevel = subjectLevelRepository
                .findByIdFullyLoaded(requireId(id, "matiere-niveau"))
                .orElseThrow(() -> new ResourceNotFoundException("Matiere-niveau avec ID " + id + " introuvable"));
        return subjectLevelMapper.toResponse(subjectLevel);
    }

    // ----- SubjectSessionType -----------------------------------------------

    @Override
    @Transactional
    public SubjectSessionTypeResponse createSessionType(SubjectSessionTypeRequest dto) {
        subjectSessionTypeValidator.validate(dto);

        SubjectLevel subjectLevel = findSubjectLevelById(dto.getSubjectLevelId());

        if (subjectSessionTypeRepository.existsBySubjectLevel_IdNiveauMatiereAndType(
                dto.getSubjectLevelId(), dto.getType())) {
            throw new ConflictException(
                    "Un type de séance " + dto.getType() + " existe déjà pour cette matière-niveau");
        }

        SubjectSessionType sessionType = subjectSessionTypeMapper.toEntity(dto);
        sessionType.setSubjectLevel(subjectLevel);
        normalizeSessionType(sessionType);

        return subjectSessionTypeMapper.toResponse(subjectSessionTypeRepository.save(sessionType));
    }

    @Override
    public SubjectSessionTypeResponse getSessionTypeById(Long id) {
        return subjectSessionTypeMapper.toResponse(findSessionTypeById(id));
    }

    @Override
    public List<SubjectSessionTypeResponse> getSessionTypesBySubjectLevel(Long subjectLevelId) {
        ensureSubjectLevelExists(subjectLevelId);
        return subjectSessionTypeMapper.toResponseList(
                subjectSessionTypeRepository.findBySubjectLevel_IdNiveauMatiere(subjectLevelId)
        );
    }

    @Override
    public List<SubjectSessionTypeResponse> getActiveSessionTypesBySubjectLevel(Long subjectLevelId) {
        ensureSubjectLevelExists(subjectLevelId);
        return subjectSessionTypeMapper.toResponseList(
                subjectSessionTypeRepository.findBySubjectLevel_IdNiveauMatiereAndEstActifTrue(subjectLevelId)
        );
    }

    @Override
    @Transactional
    public SubjectSessionTypeResponse updateSessionType(Long id, SubjectSessionTypeRequest dto) {
        subjectSessionTypeValidator.validate(dto);

        SubjectSessionType sessionType = findSessionTypeById(id);
        SubjectLevel subjectLevel = findSubjectLevelById(dto.getSubjectLevelId());

        if (subjectSessionTypeRepository.existsBySubjectLevelAndType(
                dto.getSubjectLevelId(), dto.getType(), id)) {
            throw new ConflictException(
                    "Un type de séance " + dto.getType() + " existe déjà pour cette matière-niveau");
        }

        subjectSessionTypeMapper.updateFromDto(dto, sessionType);
        sessionType.setSubjectLevel(subjectLevel);
        normalizeSessionType(sessionType);

        return subjectSessionTypeMapper.toResponse(subjectSessionTypeRepository.save(sessionType));
    }

    @Override
    @Transactional
    public SubjectSessionTypeResponse toggleSessionTypeStatus(Long id, boolean estActif) {
        SubjectSessionType sessionType = findSessionTypeById(id);

        sessionType.setEstActif(estActif);
        return subjectSessionTypeMapper.toResponse(subjectSessionTypeRepository.save(sessionType));
    }

    @Override
    @Transactional
    public void deleteSessionType(Long id) {
        SubjectSessionType sessionType = findSessionTypeById(id);

        if (sessionType.getPatternDetails() != null && !sessionType.getPatternDetails().isEmpty()) {
            throw new ConflictException("Impossible de supprimer un type de seance utilise dans des patterns");
        }

        if (sessionType.getTeachingAssignments() != null && !sessionType.getTeachingAssignments().isEmpty()) {
            throw new ConflictException("Impossible de supprimer un type de seance utilise dans des affectations");
        }

        subjectSessionTypeRepository.delete(sessionType);
    }

    private Subject findSubjectById(Long id) {
        return subjectRepository.findByTenantIdAndIdMatiere(currentTenant(), requireId(id, "matiere"))
                .orElseThrow(() -> new ResourceNotFoundException("Matiere avec ID " + id + " introuvable"));
    }

    private Level findLevelById(Long id) {
        return levelRepository.findByTenantIdAndIdNiveau(currentTenant(), requireId(id, "niveau"))
                .orElseThrow(() -> new ResourceNotFoundException("Niveau avec ID " + id + " introuvable"));
    }

    private SubjectLevel findSubjectLevelById(Long id) {
        return subjectLevelRepository.findById(requireId(id, "matiere-niveau"))
                .orElseThrow(() -> new ResourceNotFoundException("Matiere-niveau avec ID " + id + " introuvable"));
    }

    private SubjectSessionType findSessionTypeById(Long id) {
        return subjectSessionTypeRepository.findById(requireId(id, "type de seance"))
                .orElseThrow(() -> new ResourceNotFoundException("Type de seance avec ID " + id + " introuvable"));
    }

    private void ensureSubjectExists(Long subjectId) {
        findSubjectById(subjectId);
    }

    private void ensureLevelExists(Long levelId) {
        findLevelById(levelId);
    }

    private void ensureSubjectLevelExists(Long subjectLevelId) {
        findSubjectLevelById(subjectLevelId);
    }

    private Long requireId(Long id, String label) {
        if (id == null) {
            throw new BadRequestException("L'identifiant " + label + " est obligatoire");
        }
        return id;
    }

    private void normalizeSessionType(SubjectSessionType sessionType) {
        if (sessionType.getRequiresSplit() == null) {
            sessionType.setRequiresSplit(Boolean.FALSE);
        }

        if (Boolean.TRUE.equals(sessionType.getRequiresSplit())) {
            if (sessionType.getGroupCount() == null || sessionType.getGroupCount() < 2) {
                throw new BadRequestException("Le nombre de groupes est obligatoire quand la seance est divisee");
            }
        } else {
            sessionType.setGroupCount(null);
        }

        if (sessionType.getEstActif() == null) {
            sessionType.setEstActif(Boolean.TRUE);
        }
    }

}
