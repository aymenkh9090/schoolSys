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
import tn.wtm.school.org.dto.request.SchoolYearRequest;
import tn.wtm.school.org.dto.response.SchoolYearResponse;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.mapper.SchoolYearMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.AcademicYearService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicYearServiceImpl extends TenantService implements AcademicYearService {

    private final SchoolYearRepository schoolYearRepository;
    private final ClassGroupRepository classGroupRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final SchoolYearMapper schoolYearMapper;
    private final ObjectsValidator<SchoolYearRequest> validator;

    @Override
    @Transactional
    public SchoolYearResponse createAcademicYear(SchoolYearRequest request) {
        String tenantId = currentTenant();
        validator.validate(request);
        if (schoolYearRepository.existsByTenantIdAndNom(tenantId, request.getNom())) {
            throw new ConflictException("Une annee scolaire avec le nom " + request.getNom() + " existe deja");
        }
        SchoolYear schoolYear = schoolYearMapper.toEntity(request);
        return schoolYearMapper.toResponse(schoolYearRepository.save(schoolYear));
    }

    @Override
    public SchoolYearResponse getAcademicYearById(Long id) {
        return schoolYearMapper.toResponse(findById(id));
    }

    @Override
    public Page<SchoolYearResponse> getAcademicYears(Pageable pageable) {
        return schoolYearRepository.findByTenantId(currentTenant(), pageable).map(schoolYearMapper::toResponse);
    }

    @Override
    public List<SchoolYearResponse> getAcademicYearsByTenant() {
        return schoolYearRepository.findByTenantId(currentTenant()).stream()
                .map(schoolYearMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SchoolYearResponse updateAcademicYear(Long id, SchoolYearRequest request) {
        String tenantId = currentTenant();
        validator.validate(request);
        SchoolYear schoolYear = findById(id);
        if (schoolYearRepository.existsByTenantIdAndNomAndIdAnneeNot(tenantId, request.getNom(), id)) {
            throw new ConflictException("Une annee scolaire avec le nom " + request.getNom() + " existe deja");
        }
        schoolYearMapper.updateFromDto(request, schoolYear);
        return schoolYearMapper.toResponse(schoolYearRepository.save(schoolYear));
    }

    @Override
    @Transactional
    public SchoolYearResponse toggleAcademicYearStatus(Long id, Boolean active) {
        if (active == null) {
            throw new BadRequestException("Le statut de l'annee scolaire est obligatoire");
        }
        SchoolYear schoolYear = findById(id);
        schoolYear.setEstActive(active);
        return schoolYearMapper.toResponse(schoolYearRepository.save(schoolYear));
    }

    @Override
    @Transactional
    public void deleteAcademicYear(Long id) {
        SchoolYear schoolYear = findById(id);
        if (!classGroupRepository.findBySchoolYear_IdAnnee(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer une annee rattachee a des classes");
        }
        if (!teachingAssignmentRepository.findBySchoolYear_IdAnnee(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer une annee rattachee a des affectations");
        }
        schoolYearRepository.delete(schoolYear);
    }

    private SchoolYear findById(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant de l'annee scolaire est obligatoire");
        }
        return schoolYearRepository.findByTenantIdAndIdAnnee(currentTenant(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Annee scolaire avec ID " + id + " introuvable"));
    }
}
