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
import tn.wtm.school.org.dto.request.AssignSubjectRequest;
import tn.wtm.school.org.dto.request.TeachingAssignmentRequest;
import tn.wtm.school.org.dto.response.AssignSubjectResult;
import tn.wtm.school.org.dto.response.MissingAssignmentEntry;
import tn.wtm.school.org.dto.response.TeachingAssignmentResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.mapper.TeachingAssignmentMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.TeachingAssignmentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeachingAssignmentServiceImpl extends TenantService implements TeachingAssignmentService {

    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final TeacherRepository teacherRepository;
    private final ClassGroupRepository classGroupRepository;
    private final SubjectLevelRepository subjectLevelRepository;
    private final SubjectSessionTypeRepository subjectSessionTypeRepository;
    private final TeachingAssignmentMapper teachingAssignmentMapper;
    private final ObjectsValidator<TeachingAssignmentRequest> teachingAssignmentValidator;

    // ----- CRUD --------------------------------------------------------------

    @Override
    @Transactional
    public TeachingAssignmentResponse createTeachingAssignment(TeachingAssignmentRequest dto) {
        validateTeachingAssignment(dto, null);
        AssignmentRefs refs = resolveRefs(dto);

        TeachingAssignment teachingAssignment = teachingAssignmentMapper.toEntity(dto);
        applyRefs(teachingAssignment, refs);

        return teachingAssignmentMapper.toResponse(teachingAssignmentRepository.save(teachingAssignment));
    }

    @Override
    public TeachingAssignmentResponse getTeachingAssignmentById(Long id) {
        return teachingAssignmentMapper.toResponse(findTeachingAssignmentById(id));
    }

    @Override
    public TeachingAssignmentResponse getTeachingAssignmentFullyLoaded(Long id) {
        TeachingAssignment teachingAssignment = teachingAssignmentRepository.findByIdFullyLoaded(requireId(id, "affectation"))
                .orElseThrow(() -> new ResourceNotFoundException("Affectation avec ID " + id + " introuvable"));
        return teachingAssignmentMapper.toResponse(teachingAssignment);
    }

    @Override
    public Page<TeachingAssignmentResponse> getAllTeachingAssignments(Pageable pageable) {
        return teachingAssignmentRepository.findByTenantId(currentTenant(), pageable)
                .map(teachingAssignmentMapper::toResponse);
    }

    @Override
    @Transactional
    public TeachingAssignmentResponse updateTeachingAssignment(Long id, TeachingAssignmentRequest dto) {
        TeachingAssignment teachingAssignment = findTeachingAssignmentById(id);
        validateTeachingAssignment(dto, id);
        AssignmentRefs refs = resolveRefs(dto);

        teachingAssignmentMapper.updateFromDto(dto, teachingAssignment);
        applyRefs(teachingAssignment, refs);

        return teachingAssignmentMapper.toResponse(teachingAssignmentRepository.save(teachingAssignment));
    }

    @Override
    @Transactional
    public TeachingAssignmentResponse toggleTeachingAssignmentStatus(Long id, Boolean isActive) {
        if (isActive == null) {
            throw new BadRequestException("Le statut de l'affectation est obligatoire");
        }

        TeachingAssignment teachingAssignment = findTeachingAssignmentById(id);
        if (Boolean.TRUE.equals(isActive)) {
            validateActivation(teachingAssignment);
        }

        teachingAssignment.setIsActive(isActive);
        return teachingAssignmentMapper.toResponse(teachingAssignmentRepository.save(teachingAssignment));
    }

    @Override
    @Transactional
    public void deleteTeachingAssignment(Long id) {
        TeachingAssignment teachingAssignment = findTeachingAssignmentById(id);
        teachingAssignmentRepository.delete(teachingAssignment);
    }

    // ----- Filtres -----------------------------------------------------------

    @Override
    public List<TeachingAssignmentResponse> getTeachingAssignmentsByTeacher(Long teacherId) {
        ensureTeacherExists(teacherId);
        return teachingAssignmentMapper.toResponseList(teachingAssignmentRepository.findByTeacher_IdEnseignant(teacherId));
    }

    @Override
    public List<TeachingAssignmentResponse> getTeachingAssignmentsByClassGroup(Long classGroupId) {
        ensureClassGroupExists(classGroupId);
        return teachingAssignmentMapper.toResponseList(teachingAssignmentRepository.findByClassGroup_IdClasse(classGroupId));
    }

    @Override
    public List<TeachingAssignmentResponse> getTeachingAssignmentsBySubjectLevel(Long subjectLevelId) {
        ensureSubjectLevelExists(subjectLevelId);
        return teachingAssignmentMapper.toResponseList(
                teachingAssignmentRepository.findBySubjectLevel_IdNiveauMatiere(subjectLevelId)
        );
    }

    @Override
    public List<TeachingAssignmentResponse> getTeachingAssignmentsBySchoolYear(Long schoolYearId) {
        ensureSchoolYearExists(schoolYearId);
        return teachingAssignmentMapper.toResponseList(teachingAssignmentRepository.findBySchoolYear_IdAnnee(schoolYearId));
    }

    @Override
    public Page<TeachingAssignmentResponse> getTeachingAssignmentsBySchoolYearPaged(Long schoolYearId, Pageable pageable) {
        ensureSchoolYearExists(schoolYearId);
        return teachingAssignmentRepository.findBySchoolYear_IdAnnee(schoolYearId, pageable)
                .map(teachingAssignmentMapper::toResponse);
    }

    @Override
    public List<TeachingAssignmentResponse> getTeachingAssignmentsByTeacherAndYear(Long teacherId, Long schoolYearId) {
        ensureTeacherExists(teacherId);
        ensureSchoolYearExists(schoolYearId);
        return teachingAssignmentMapper.toResponseList(
                teachingAssignmentRepository.findByTeacher_IdEnseignantAndSchoolYear_IdAnnee(teacherId, schoolYearId)
        );
    }

    @Override
    public List<TeachingAssignmentResponse> getTeachingAssignmentsByClassGroupAndYear(Long classGroupId, Long schoolYearId) {
        ensureClassGroupExists(classGroupId);
        ensureSchoolYearExists(schoolYearId);
        return teachingAssignmentMapper.toResponseList(
                teachingAssignmentRepository.findByClassGroup_IdClasseAndSchoolYear_IdAnnee(classGroupId, schoolYearId)
        );
    }

    @Override
    public List<TeachingAssignmentResponse> getActiveTeachingAssignmentsBySchoolYear(Long schoolYearId) {
        ensureSchoolYearExists(schoolYearId);
        return teachingAssignmentMapper.toResponseList(
                teachingAssignmentRepository.findByIsActiveTrueAndSchoolYear_IdAnnee(schoolYearId)
        );
    }

    @Override
    public List<TeachingAssignmentResponse> getActiveTeachingAssignmentsByTeacher(Long teacherId) {
        ensureTeacherExists(teacherId);
        return teachingAssignmentMapper.toResponseList(
                teachingAssignmentRepository.findByIsActiveTrueAndTeacher_IdEnseignant(teacherId)
        );
    }

    // ----- Generation / metier ----------------------------------------------

    @Override
    public List<TeachingAssignmentResponse> getTeachingAssignmentsForGeneration(Long schoolYearId) {
        ensureSchoolYearExists(schoolYearId);
        return teachingAssignmentMapper.toResponseList(teachingAssignmentRepository.findAllForGeneration(schoolYearId));
    }

    @Override
    public List<Object[]> getTeacherLoadByYear(Long schoolYearId) {
        ensureSchoolYearExists(schoolYearId);
        return teachingAssignmentRepository.findTeacherLoadByYear(schoolYearId);
    }

    @Override
    public List<TeachingAssignmentResponse> findConflicts(Long schoolYearId, Long teacherId, Long subjectLevelId) {
        ensureSchoolYearExists(schoolYearId);
        ensureTeacherExists(teacherId);
        ensureSubjectLevelExists(subjectLevelId);

        return teachingAssignmentMapper.toResponseList(
                teachingAssignmentRepository.findConflicts(schoolYearId, teacherId, subjectLevelId)
        );
    }

    @Override
    public boolean existsTeachingAssignment(
            Long schoolYearId,
            Long teacherId,
            Long classGroupId,
            Long subjectLevelId,
            Long subjectSessionTypeId
    ) {
        if (schoolYearId == null
                || teacherId == null
                || classGroupId == null
                || subjectLevelId == null
                || subjectSessionTypeId == null) {
            return false;
        }

        return teachingAssignmentRepository
                .existsBySchoolYear_IdAnneeAndTeacher_IdEnseignantAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
                        schoolYearId,
                        teacherId,
                        classGroupId,
                        subjectLevelId,
                        subjectSessionTypeId
                );
    }

    @Override
    public boolean existsDuplicateTeachingAssignment(
            Long schoolYearId,
            Long teacherId,
            Long classGroupId,
            Long subjectLevelId,
            Long subjectSessionTypeId,
            Long excludeId
    ) {
        if (schoolYearId == null
                || teacherId == null
                || classGroupId == null
                || subjectLevelId == null
                || subjectSessionTypeId == null) {
            return false;
        }

        return teachingAssignmentRepository.existsDuplicate(
                schoolYearId,
                teacherId,
                classGroupId,
                subjectLevelId,
                subjectSessionTypeId,
                excludeId
        );
    }

    @Override
    public void validateTeachingAssignment(TeachingAssignmentRequest dto, Long excludeId) {
        teachingAssignmentValidator.validate(dto);

        AssignmentRefs refs = resolveRefs(dto);
        validateRefs(dto, refs);

        if (existsDuplicateTeachingAssignment(
                dto.getSchoolYearId(),
                dto.getTeacherId(),
                dto.getClassGroupId(),
                dto.getSubjectLevelId(),
                dto.getSubjectSessionTypeId(),
                excludeId
        )) {
            throw new ConflictException("Cette affectation enseignant existe deja");
        }

        if (willBeActive(dto, excludeId)) {
            validateTeacherWeeklyLoad(dto, refs, excludeId);
        }
    }

    @Override
    public List<MissingAssignmentEntry> getMissingAssignments(Long schoolYearId) {
        ensureSchoolYearExists(schoolYearId);
        String tenantId = currentTenant();

        return teachingAssignmentRepository
                .findMissingAssignments(tenantId, schoolYearId)
                .stream()
                .map(row -> new MissingAssignmentEntry(
                        ((Number) row[0]).longValue(),   // classGroupId
                        (String)  row[1],                // classGroupCode
                        (String)  row[2],                // levelCode
                        ((Number) row[3]).longValue(),   // subjectLevelId
                        (String)  row[4],                // subjectCode
                        (String)  row[5],                // subjectNom
                        ((Number) row[6]).longValue(),   // subjectSessionTypeId
                        (String)  row[7]                 // sessionType
                ))
                .toList();
    }

    @Override
    @Transactional
    public AssignSubjectResult assignSubjectToClasses(AssignSubjectRequest request) {
        List<TeachingAssignmentResponse> assigned = new ArrayList<>();
        List<AssignSubjectResult.SkippedEntry> skippedDetails = new ArrayList<>();

        for (Long classGroupId : request.classGroupIds()) {
            TeachingAssignmentRequest dto = TeachingAssignmentRequest.builder()
                    .schoolYearId(request.schoolYearId())
                    .teacherId(request.teacherId())
                    .classGroupId(classGroupId)
                    .subjectLevelId(request.subjectLevelId())
                    .subjectSessionTypeId(request.subjectSessionTypeId())
                    .priority(request.priority())
                    .isActive(request.isActive() != null ? request.isActive() : Boolean.TRUE)
                    .build();

            try {
                validateTeachingAssignment(dto, null);
                AssignmentRefs refs = resolveRefs(dto);

                TeachingAssignment entity = teachingAssignmentMapper.toEntity(dto);
                applyRefs(entity, refs);
                assigned.add(teachingAssignmentMapper.toResponse(teachingAssignmentRepository.save(entity)));

            } catch (ConflictException ex) {
                skippedDetails.add(new AssignSubjectResult.SkippedEntry(classGroupId, ex.getMessage()));
            }
        }

        return new AssignSubjectResult(
                assigned.size(),
                skippedDetails.size(),
                assigned,
                skippedDetails
        );
    }

    private TeachingAssignment findTeachingAssignmentById(Long id) {
        return teachingAssignmentRepository.findById(requireId(id, "affectation"))
                .orElseThrow(() -> new ResourceNotFoundException("Affectation avec ID " + id + " introuvable"));
    }

    private AssignmentRefs resolveRefs(TeachingAssignmentRequest dto) {
        SchoolYear schoolYear = findSchoolYearById(dto.getSchoolYearId());
        Teacher teacher = findTeacherById(dto.getTeacherId());
        ClassGroup classGroup = findClassGroupById(dto.getClassGroupId());
        SubjectLevel subjectLevel = findSubjectLevelById(dto.getSubjectLevelId());
        SubjectSessionType subjectSessionType = findSubjectSessionTypeById(dto.getSubjectSessionTypeId());

        return new AssignmentRefs(schoolYear, teacher, classGroup, subjectLevel, subjectSessionType);
    }

    private void applyRefs(TeachingAssignment teachingAssignment, AssignmentRefs refs) {
        teachingAssignment.setSchoolYear(refs.schoolYear());
        teachingAssignment.setTeacher(refs.teacher());
        teachingAssignment.setClassGroup(refs.classGroup());
        teachingAssignment.setSubjectLevel(refs.subjectLevel());
        teachingAssignment.setSubjectSessionType(refs.subjectSessionType());
    }

    private void validateRefs(TeachingAssignmentRequest dto, AssignmentRefs refs) {
        if (Boolean.FALSE.equals(refs.schoolYear().getEstActive())) {
            throw new BadRequestException("L'annee scolaire est inactive");
        }

        if (Boolean.FALSE.equals(refs.teacher().getEstEnPoste())) {
            throw new BadRequestException("L'enseignant n'est pas en poste");
        }

        if (Boolean.FALSE.equals(refs.classGroup().getEstActif())) {
            throw new BadRequestException("La classe est inactive");
        }

        Long classSchoolYearId = refs.classGroup().getSchoolYear() != null
                ? refs.classGroup().getSchoolYear().getIdAnnee()
                : null;
        if (!Objects.equals(classSchoolYearId, dto.getSchoolYearId())) {
            throw new BadRequestException("La classe n'appartient pas a l'annee scolaire demandee");
        }

        Long classLevelId = refs.classGroup().getLevel() != null
                ? refs.classGroup().getLevel().getIdNiveau()
                : null;
        Long subjectLevelLevelId = refs.subjectLevel().getLevel() != null
                ? refs.subjectLevel().getLevel().getIdNiveau()
                : null;
        if (!Objects.equals(classLevelId, subjectLevelLevelId)) {
            throw new BadRequestException("La matiere-niveau ne correspond pas au niveau de la classe");
        }

        Long sessionSubjectLevelId = refs.subjectSessionType().getSubjectLevel() != null
                ? refs.subjectSessionType().getSubjectLevel().getIdNiveauMatiere()
                : null;
        if (!Objects.equals(sessionSubjectLevelId, dto.getSubjectLevelId())) {
            throw new BadRequestException("Le type de seance n'appartient pas a la matiere-niveau demandee");
        }

        if (Boolean.FALSE.equals(refs.subjectSessionType().getEstActif())) {
            throw new BadRequestException("Le type de seance est inactif");
        }
    }

    private void validateActivation(TeachingAssignment teachingAssignment) {
        TeachingAssignmentRequest dto = TeachingAssignmentRequest.builder()
                .schoolYearId(teachingAssignment.getSchoolYear().getIdAnnee())
                .teacherId(teachingAssignment.getTeacher().getIdEnseignant())
                .classGroupId(teachingAssignment.getClassGroup().getIdClasse())
                .subjectLevelId(teachingAssignment.getSubjectLevel().getIdNiveauMatiere())
                .subjectSessionTypeId(teachingAssignment.getSubjectSessionType().getIdSubjectSessionType())
                .priority(teachingAssignment.getPriority())
                .isActive(Boolean.TRUE)
                .build();

        validateTeachingAssignment(dto, teachingAssignment.getIdTeachingAssignment());
    }

    private void validateTeacherWeeklyLoad(
            TeachingAssignmentRequest dto,
            AssignmentRefs refs,
            Long excludeId
    ) {
        if (refs.teacher().getMaxHeuresSemaine() == null) {
            return;
        }

        double currentLoad = teachingAssignmentRepository
                .findByTeacher_IdEnseignantAndSchoolYear_IdAnnee(dto.getTeacherId(), dto.getSchoolYearId())
                .stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getIsActive()))
                .filter(assignment -> !Objects.equals(assignment.getIdTeachingAssignment(), excludeId))
                .map(TeachingAssignment::getSubjectLevel)
                .filter(Objects::nonNull)
                .map(SubjectLevel::getHeuresSemaine)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        double addedHours = refs.subjectLevel().getHeuresSemaine() != null
                ? refs.subjectLevel().getHeuresSemaine()
                : 0.0;

        if (currentLoad + addedHours > refs.teacher().getMaxHeuresSemaine()) {
            throw new ConflictException("L'affectation depasse la charge hebdomadaire maximale de l'enseignant");
        }
    }

    private boolean willBeActive(TeachingAssignmentRequest dto, Long excludeId) {
        if (dto.getIsActive() != null) {
            return Boolean.TRUE.equals(dto.getIsActive());
        }

        if (excludeId == null) {
            return true;
        }

        return findTeachingAssignmentById(excludeId).getIsActive();
    }

    private SchoolYear findSchoolYearById(Long id) {
        return schoolYearRepository.findById(requireId(id, "annee scolaire"))
                .orElseThrow(() -> new ResourceNotFoundException("Annee scolaire avec ID " + id + " introuvable"));
    }

    private Teacher findTeacherById(Long id) {
        return teacherRepository.findById(requireId(id, "enseignant"))
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant avec ID " + id + " introuvable"));
    }

    private ClassGroup findClassGroupById(Long id) {
        return classGroupRepository.findById(requireId(id, "classe"))
                .orElseThrow(() -> new ResourceNotFoundException("Classe avec ID " + id + " introuvable"));
    }

    private SubjectLevel findSubjectLevelById(Long id) {
        return subjectLevelRepository.findById(requireId(id, "matiere-niveau"))
                .orElseThrow(() -> new ResourceNotFoundException("Matiere-niveau avec ID " + id + " introuvable"));
    }

    private SubjectSessionType findSubjectSessionTypeById(Long id) {
        return subjectSessionTypeRepository.findById(requireId(id, "type de seance"))
                .orElseThrow(() -> new ResourceNotFoundException("Type de seance avec ID " + id + " introuvable"));
    }

    private void ensureSchoolYearExists(Long schoolYearId) {
        findSchoolYearById(schoolYearId);
    }

    private void ensureTeacherExists(Long teacherId) {
        findTeacherById(teacherId);
    }

    private void ensureClassGroupExists(Long classGroupId) {
        findClassGroupById(classGroupId);
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

    private record AssignmentRefs(
            SchoolYear schoolYear,
            Teacher teacher,
            ClassGroup classGroup,
            SubjectLevel subjectLevel,
            SubjectSessionType subjectSessionType
    ) {
    }
}
