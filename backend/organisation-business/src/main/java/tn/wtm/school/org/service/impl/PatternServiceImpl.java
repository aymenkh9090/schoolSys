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
import tn.wtm.school.org.dto.request.CustomizePatternRequest;
import tn.wtm.school.org.dto.request.PatternDetailRequest;
import tn.wtm.school.org.dto.request.PatternRequest;
import tn.wtm.school.org.dto.response.PatternDetailResponse;
import tn.wtm.school.org.dto.response.PatternResponse;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Pattern;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.mapper.PatternDetailMapper;
import tn.wtm.school.org.mapper.PatternMapper;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.PatternDetailRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.service.PatternService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternServiceImpl extends TenantService implements PatternService {

    private static final double EPSILON = 0.01;

    private final PatternRepository patternRepository;
    private final PatternDetailRepository patternDetailRepository;
    private final SubjectLevelRepository subjectLevelRepository;
    private final SubjectSessionTypeRepository subjectSessionTypeRepository;
    private final LevelRepository levelRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final PatternMapper patternMapper;
    private final PatternDetailMapper patternDetailMapper;
    private final ObjectsValidator<PatternRequest> patternValidator;
    private final ObjectsValidator<PatternDetailRequest> patternDetailValidator;

    // ----- Pattern CRUD ------------------------------------------------------

    @Override
    @Transactional
    public PatternResponse createPattern(PatternRequest dto) {
        patternValidator.validate(dto);
        validateRequestedDetails(dto.getDetails(), dto.getTotalHours(), dto.getSessionCount());

        SubjectLevel subjectLevel = findSubjectLevelById(dto.getSubjectLevelId());
        SchoolYear schoolYear = resolveSchoolYear(dto.getSchoolYearId());
        validatePatternNameUniqueness(dto.getName(), dto.getSubjectLevelId(), dto.getSchoolYearId(), null);

        Pattern pattern = patternMapper.toEntity(dto);
        pattern.setSubjectLevel(subjectLevel);
        pattern.setSchoolYear(schoolYear);

        List<PatternDetail> details = buildDetails(dto.getDetails(), pattern, subjectLevel);
        pattern.setPatternDetails(details);

        Pattern savedPattern = patternRepository.save(pattern);
        sortPatternDetails(savedPattern);
        return patternMapper.toResponse(savedPattern);
    }

    @Override
    public PatternResponse getPatternById(Long id) {
        return toResponseWithoutDetails(findPatternById(id));
    }

    @Override
    public PatternResponse getPatternWithDetails(Long id) {
        Pattern pattern = findPatternWithDetails(id);
        sortPatternDetails(pattern);
        return patternMapper.toResponse(pattern);
    }

    @Override
    public List<PatternResponse> getPatternsBySubjectLevel(Long subjectLevelId) {
        ensureSubjectLevelExists(subjectLevelId);
        return patternRepository.findBySubjectLevel_IdNiveauMatiere(subjectLevelId)
                .stream()
                .map(this::toResponseWithoutDetails)
                .toList();
    }

    @Override
    public List<PatternResponse> getPatternsBySubjectLevelWithDetails(Long subjectLevelId) {
        ensureSubjectLevelExists(subjectLevelId);
        return patternRepository.findBySubjectLevelWithDetails(subjectLevelId)
                .stream()
                .peek(this::sortPatternDetails)
                .map(patternMapper::toResponse)
                .toList();
    }

    @Override
    public Page<PatternResponse> getPatternsBySubjectLevelPaginated(Long subjectLevelId, Pageable pageable) {
        ensureSubjectLevelExists(subjectLevelId);
        return patternRepository.findBySubjectLevel_IdNiveauMatiere(subjectLevelId, pageable)
                .map(this::toResponseWithoutDetails);
    }

    @Override
    @Transactional
    public PatternResponse updatePattern(Long id, PatternRequest dto) {
        patternValidator.validate(dto);
        validateRequestedDetails(dto.getDetails(), dto.getTotalHours(), dto.getSessionCount());

        Pattern pattern = findPatternWithDetails(id);
        SubjectLevel subjectLevel = findSubjectLevelById(dto.getSubjectLevelId());
        SchoolYear schoolYear = resolveSchoolYear(dto.getSchoolYearId());
        validatePatternNameUniqueness(dto.getName(), dto.getSubjectLevelId(), dto.getSchoolYearId(), id);

        patternMapper.updateFromDto(dto, pattern);
        pattern.setSubjectLevel(subjectLevel);
        pattern.setSchoolYear(schoolYear);

        patternDetailRepository.deleteByPattern_IdPattern(id);
        pattern.getPatternDetails().clear();

        List<PatternDetail> details = buildDetails(dto.getDetails(), pattern, subjectLevel);
        List<PatternDetail> savedDetails = patternDetailRepository.saveAll(details);
        pattern.setPatternDetails(new ArrayList<>(savedDetails));

        Pattern savedPattern = patternRepository.save(pattern);
        sortPatternDetails(savedPattern);
        return patternMapper.toResponse(savedPattern);
    }

    @Override
    @Transactional
    public void deletePattern(Long id) {
        Pattern pattern = findPatternById(id);
        patternDetailRepository.deleteByPattern_IdPattern(id);
        patternRepository.delete(pattern);
    }

    @Override
    @Transactional
    public PatternResponse togglePatternActive(Long id, Boolean active) {
        if (active == null) {
            throw new BadRequestException("Le statut actif du pattern est obligatoire");
        }
        Pattern pattern = findPatternById(id);
        pattern.setActive(active);
        return toResponseWithoutDetails(patternRepository.save(pattern));
    }

    // ----- Pattern metier ----------------------------------------------------

    @Override
    public List<PatternResponse> getPatternsForGeneration(Long levelId, Long schoolYearId) {
        ensureLevelExists(levelId);
        if (schoolYearId == null) {
            throw new BadRequestException("L'identifiant de l'année scolaire est obligatoire pour la génération");
        }
        return patternRepository.findAllByLevelForGeneration(levelId, schoolYearId)
                .stream()
                .peek(this::sortPatternDetails)
                .map(patternMapper::toResponse)
                .toList();
    }

    @Override
    public void validatePatternConsistency(Long patternId) {
        Pattern pattern = findPatternWithDetails(patternId);
        List<String> errors = getConsistencyErrors(pattern);
        if (!errors.isEmpty()) {
            throw new BadRequestException("Pattern incoherent: " + String.join(", ", errors));
        }
    }

    @Override
    public List<PatternResponse> findInconsistentPatterns() {
        return patternRepository.findByTenantId(currentTenant())
                .stream()
                .filter(pattern -> !getConsistencyErrors(pattern).isEmpty())
                .peek(this::sortPatternDetails)
                .map(patternMapper::toResponse)
                .toList();
    }

    // ----- Pattern detail CRUD ----------------------------------------------

    @Override
    @Transactional
    public PatternDetailResponse addDetail(Long patternId, PatternDetailRequest dto) {
        patternDetailValidator.validate(dto);
        validateRoomTypeValue(dto.getRequiredRoomType());

        Pattern pattern = findPatternWithDetails(patternId);
        PatternDetail detail = buildDetail(dto, pattern, pattern.getSubjectLevel());

        pattern.getPatternDetails().add(detail);
        validateEntityDetails(pattern.getPatternDetails());
        refreshPatternSummary(pattern);

        PatternDetail savedDetail = patternDetailRepository.save(detail);
        patternRepository.save(pattern);

        return patternDetailMapper.toResponse(savedDetail);
    }

    @Override
    public PatternDetailResponse getDetailById(Long id) {
        return patternDetailMapper.toResponse(findPatternDetailById(id));
    }

    @Override
    public List<PatternDetailResponse> getDetailsByPattern(Long patternId) {
        ensurePatternExists(patternId);
        return patternDetailMapper.toResponseList(
                patternDetailRepository.findByPattern_IdPatternOrderBySessionOrderAsc(patternId)
        );
    }

    @Override
    @Transactional
    public PatternDetailResponse updateDetail(Long id, PatternDetailRequest dto) {
        patternDetailValidator.validate(dto);
        validateRoomTypeValue(dto.getRequiredRoomType());

        PatternDetail detail = findPatternDetailById(id);
        Pattern pattern = findPatternWithDetails(detail.getPattern().getIdPattern());

        patternDetailMapper.updateFromDto(dto, detail);
        detail.setSubjectSessionType(resolveSubjectSessionType(dto.getSubjectSessionTypeId(), pattern.getSubjectLevel()));

        validateEntityDetails(pattern.getPatternDetails());
        refreshPatternSummary(pattern);

        patternRepository.save(pattern);
        return patternDetailMapper.toResponse(patternDetailRepository.save(detail));
    }

    @Override
    @Transactional
    public void deleteDetail(Long id) {
        PatternDetail detail = findPatternDetailById(id);
        Pattern pattern = findPatternWithDetails(detail.getPattern().getIdPattern());

        if (pattern.getPatternDetails().size() <= 1) {
            throw new BadRequestException("Impossible de supprimer le dernier detail d'un pattern");
        }

        Integer deletedOrder = detail.getSessionOrder();
        pattern.getPatternDetails().removeIf(pd -> Objects.equals(pd.getIdPatternDetail(), id));

        pattern.getPatternDetails().forEach(pd -> {
            if (deletedOrder != null && pd.getSessionOrder() != null && pd.getSessionOrder() > deletedOrder) {
                pd.setSessionOrder(pd.getSessionOrder() - 1);
            }
        });

        validateEntityDetails(pattern.getPatternDetails());
        refreshPatternSummary(pattern);

        patternDetailRepository.delete(detail);
        patternRepository.save(pattern);
    }

    // ----- Pattern detail metier --------------------------------------------

    @Override
    public List<PatternDetailResponse> getDetailsByType(Long patternId, SessionType type) {
        ensurePatternExists(patternId);
        if (type == null) {
            throw new BadRequestException("Le type de seance est obligatoire");
        }

        return patternDetailMapper.toResponseList(
                sortedDetails(patternDetailRepository.findByPattern_IdPatternAndType(patternId, type))
        );
    }

    @Override
    public List<PatternDetailResponse> getDetailsByRoomType(Long patternId, RoomType roomType) {
        ensurePatternExists(patternId);
        if (roomType == null) {
            throw new BadRequestException("Le type de salle est obligatoire");
        }

        return patternDetailMapper.toResponseList(
                patternDetailRepository.findByPattern_IdPatternOrderBySessionOrderAsc(patternId)
                        .stream()
                        .filter(detail -> detail.getRequiredRoomType() == roomType)
                        .toList()
        );
    }

    @Override
    public List<PatternDetailResponse> getSplitDetails(Long patternId) {
        ensurePatternExists(patternId);
        return patternDetailMapper.toResponseList(
                sortedDetails(patternDetailRepository.findByPattern_IdPatternAndIsSplitTrue(patternId))
        );
    }

    @Override
    @Transactional
    public List<PatternDetailResponse> reorderDetails(Long patternId, List<Long> orderedIds) {
        Pattern pattern = findPatternWithDetails(patternId);
        validateOrderedIds(pattern, orderedIds);

        Map<Long, PatternDetail> detailsById = pattern.getPatternDetails().stream()
                .collect(Collectors.toMap(
                        PatternDetail::getIdPatternDetail,
                        detail -> detail,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        for (int index = 0; index < orderedIds.size(); index++) {
            detailsById.get(orderedIds.get(index)).setSessionOrder(index + 1);
        }

        validateEntityDetails(pattern.getPatternDetails());
        refreshPatternSummary(pattern);

        patternRepository.save(pattern);
        return patternDetailMapper.toResponseList(sortedDetails(pattern.getPatternDetails()));
    }

    // ----- Customisation post-apply-national ---------------------------------

    @Override
    public List<PatternResponse> getPatternsByLevelCode(String levelCode) {
        if (levelCode == null || levelCode.isBlank()) {
            throw new BadRequestException("Le code niveau est obligatoire");
        }
        return patternRepository.findByLevelCodeWithDetails(currentTenant(), levelCode)
                .stream()
                .peek(this::sortPatternDetails)
                .map(patternMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PatternResponse customizePattern(String levelCode, String subjectCode,
                                             CustomizePatternRequest request) {
        String tenantId = currentTenant();

        Pattern pattern = patternRepository
                .findDefaultByLevelAndSubject(tenantId, levelCode, subjectCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun pattern par défaut trouvé pour niveau=" + levelCode
                        + " matière=" + subjectCode + " — appliquer d'abord apply-national"));

        int sessionCount = request.sessions().size();
        double totalHours = request.sessions().stream()
                .mapToDouble(PatternDetailRequest::getDuration)
                .sum();
        String repartition = request.repartition() != null
                ? request.repartition()
                : request.sessions().stream()
                        .map(s -> formatDuration(s.getDuration()))
                        .collect(Collectors.joining("+"));

        validateRequestedDetails(request.sessions(), request.totalHours(), sessionCount);

        patternDetailRepository.deleteByPattern_IdPattern(pattern.getIdPattern());
        pattern.getPatternDetails().clear();

        pattern.setTotalHours(totalHours);
        pattern.setSessionCount(sessionCount);
        pattern.setRepartition(repartition);

        List<PatternDetail> details = buildDetails(request.sessions(), pattern, pattern.getSubjectLevel());
        List<PatternDetail> savedDetails = patternDetailRepository.saveAll(details);
        pattern.setPatternDetails(new ArrayList<>(savedDetails));

        Pattern saved = patternRepository.save(pattern);
        sortPatternDetails(saved);
        return patternMapper.toResponse(saved);
    }

    private Pattern findPatternById(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant du pattern est obligatoire");
        }
        return patternRepository.findByTenantIdAndIdPattern(currentTenant(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Pattern avec ID " + id + " introuvable"));
    }

    private Pattern findPatternWithDetails(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant du pattern est obligatoire");
        }
        return patternRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pattern avec ID " + id + " introuvable"));
    }

    private PatternDetail findPatternDetailById(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant du detail est obligatoire");
        }
        return patternDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Detail de pattern avec ID " + id + " introuvable"));
    }

    private SubjectLevel findSubjectLevelById(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant subjectLevel est obligatoire");
        }
        return subjectLevelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubjectLevel avec ID " + id + " introuvable"));
    }

    private void ensurePatternExists(Long patternId) {
        findPatternById(patternId);
    }

    private void ensureSubjectLevelExists(Long subjectLevelId) {
        findSubjectLevelById(subjectLevelId);
    }

    private void ensureLevelExists(Long levelId) {
        if (levelId == null) {
            throw new BadRequestException("L'identifiant du niveau est obligatoire");
        }
        levelRepository.findByTenantIdAndIdNiveau(currentTenant(), levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Niveau avec ID " + levelId + " introuvable"));
    }

    private void validatePatternNameUniqueness(String name, Long subjectLevelId, Long schoolYearId, Long excludeId) {
        if (patternRepository.existsByNameAndSubjectLevelAndYear(name, subjectLevelId, schoolYearId, excludeId)) {
            String scope = schoolYearId == null ? "cette matiere-niveau (pattern par defaut)" : "cette matiere-niveau et cette annee scolaire";
            throw new ConflictException("Un pattern avec le nom '" + name + "' existe deja pour " + scope);
        }
    }

    private SchoolYear resolveSchoolYear(Long schoolYearId) {
        if (schoolYearId == null) {
            return null;
        }
        return schoolYearRepository.findByTenantIdAndIdAnnee(currentTenant(), schoolYearId)
                .orElseThrow(() -> new ResourceNotFoundException("Annee scolaire avec ID " + schoolYearId + " introuvable"));
    }

    private List<PatternDetail> buildDetails(
            List<PatternDetailRequest> requests,
            Pattern pattern,
            SubjectLevel subjectLevel
    ) {
        return requests.stream()
                .map(dto -> buildDetail(dto, pattern, subjectLevel))
                .sorted(Comparator.comparing(PatternDetail::getSessionOrder))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private PatternDetail buildDetail(
            PatternDetailRequest dto,
            Pattern pattern,
            SubjectLevel subjectLevel
    ) {
        validateRoomTypeValue(dto.getRequiredRoomType());

        PatternDetail detail = patternDetailMapper.toEntity(dto);
        detail.setPattern(pattern);
        detail.setSubjectSessionType(resolveSubjectSessionType(dto.getSubjectSessionTypeId(), subjectLevel));
        return detail;
    }

    private SubjectSessionType resolveSubjectSessionType(Long id, SubjectLevel subjectLevel) {
        if (id == null) {
            return null;
        }

        SubjectSessionType subjectSessionType = subjectSessionTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de seance avec ID " + id + " introuvable"));

        Long sessionSubjectLevelId = subjectSessionType.getSubjectLevel() != null
                ? subjectSessionType.getSubjectLevel().getIdNiveauMatiere()
                : null;

        if (!Objects.equals(sessionSubjectLevelId, subjectLevel.getIdNiveauMatiere())) {
            throw new BadRequestException("Le type de seance n'appartient pas a la matiere-niveau du pattern");
        }

        if (Boolean.FALSE.equals(subjectSessionType.getEstActif())) {
            throw new BadRequestException("Le type de seance est inactif");
        }

        return subjectSessionType;
    }

    private void validateRequestedDetails(
            List<PatternDetailRequest> details,
            Double totalHours,
            Integer sessionCount
    ) {
        if (details == null || details.isEmpty()) {
            throw new BadRequestException("Au moins un detail de pattern est requis");
        }

        details.forEach(detail -> validateRoomTypeValue(detail.getRequiredRoomType()));

        validateSessionOrders(details.stream()
                .map(PatternDetailRequest::getSessionOrder)
                .toList());

        double detailsTotal = details.stream()
                .map(PatternDetailRequest::getDuration)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalHours != null && Math.abs(totalHours - detailsTotal) > EPSILON) {
            throw new BadRequestException("La somme des durees des details doit correspondre au totalHours");
        }

        if (sessionCount != null && !Objects.equals(sessionCount, details.size())) {
            throw new BadRequestException("Le sessionCount doit correspondre au nombre de details");
        }
    }

    private void validateEntityDetails(List<PatternDetail> details) {
        if (details == null || details.isEmpty()) {
            throw new BadRequestException("Au moins un detail de pattern est requis");
        }

        validateSessionOrders(details.stream()
                .map(PatternDetail::getSessionOrder)
                .toList());
    }

    private void validateSessionOrders(List<Integer> orders) {
        if (orders.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("L'ordre de chaque seance est obligatoire");
        }

        Set<Integer> uniqueOrders = new HashSet<>(orders);
        if (uniqueOrders.size() != orders.size()) {
            throw new BadRequestException("Les ordres des seances doivent etre uniques");
        }

        for (int expectedOrder = 1; expectedOrder <= orders.size(); expectedOrder++) {
            if (!uniqueOrders.contains(expectedOrder)) {
                throw new BadRequestException("Les ordres des seances doivent etre consecutifs a partir de 1");
            }
        }
    }

    private List<String> getConsistencyErrors(Pattern pattern) {
        List<String> errors = new ArrayList<>();
        List<PatternDetail> details = pattern.getPatternDetails() != null
                ? pattern.getPatternDetails()
                : List.of();

        if (details.isEmpty()) {
            errors.add("aucun detail");
            return errors;
        }

        double detailsTotal = details.stream()
                .map(PatternDetail::getDuration)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        if (pattern.getTotalHours() == null) {
            errors.add("totalHours manquant");
        } else if (Math.abs(pattern.getTotalHours() - detailsTotal) > EPSILON) {
            errors.add("totalHours different de la somme des details");
        }

        if (pattern.getSessionCount() == null) {
            errors.add("sessionCount manquant");
        } else if (!Objects.equals(pattern.getSessionCount(), details.size())) {
            errors.add("sessionCount different du nombre de details");
        }

        try {
            validateSessionOrders(details.stream()
                    .map(PatternDetail::getSessionOrder)
                    .toList());
        } catch (BadRequestException ex) {
            errors.add(ex.getMessage());
        }

        return errors;
    }

    private void refreshPatternSummary(Pattern pattern) {
        List<PatternDetail> details = sortedDetails(pattern.getPatternDetails());
        double totalHours = details.stream()
                .map(PatternDetail::getDuration)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        pattern.setTotalHours(totalHours);
        pattern.setSessionCount(details.size());
        pattern.setRepartition(details.stream()
                .map(PatternDetail::getDuration)
                .map(this::formatDuration)
                .collect(Collectors.joining("+")));
        pattern.setPatternDetails(new ArrayList<>(details));
    }

    private void validateOrderedIds(Pattern pattern, List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new BadRequestException("La liste des details ordonnes est obligatoire");
        }

        List<PatternDetail> details = pattern.getPatternDetails();
        if (orderedIds.size() != details.size()) {
            throw new BadRequestException("La liste doit contenir tous les details du pattern");
        }

        Set<Long> requestedIds = new HashSet<>(orderedIds);
        if (requestedIds.size() != orderedIds.size()) {
            throw new BadRequestException("La liste contient des identifiants dupliques");
        }

        Set<Long> existingIds = details.stream()
                .map(PatternDetail::getIdPatternDetail)
                .collect(Collectors.toSet());

        if (!existingIds.equals(requestedIds)) {
            throw new BadRequestException("La liste doit contenir uniquement les details du pattern");
        }
    }

    private void validateRoomTypeValue(String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        try {
            RoomType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Type de salle invalide: " + value);
        }
    }

    private void sortPatternDetails(Pattern pattern) {
        if (pattern.getPatternDetails() != null) {
            pattern.getPatternDetails().sort(detailOrderComparator());
        }
    }

    private List<PatternDetail> sortedDetails(List<PatternDetail> details) {
        return details.stream()
                .sorted(detailOrderComparator())
                .toList();
    }

    private Comparator<PatternDetail> detailOrderComparator() {
        return Comparator.comparing(
                PatternDetail::getSessionOrder,
                Comparator.nullsLast(Integer::compareTo)
        );
    }

    private String formatDuration(Double value) {
        return BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }

    private PatternResponse toResponseWithoutDetails(Pattern pattern) {
        SubjectLevel subjectLevel = pattern.getSubjectLevel();
        Subject subject = subjectLevel != null ? subjectLevel.getSubject() : null;
        Level level = subjectLevel != null ? subjectLevel.getLevel() : null;
        SchoolYear schoolYear = pattern.getSchoolYear();

        return PatternResponse.builder()
                .idPattern(pattern.getIdPattern())
                .name(pattern.getName())
                .totalHours(pattern.getTotalHours())
                .sessionCount(pattern.getSessionCount())
                .repartition(pattern.getRepartition())
                .patternType(pattern.getPatternType())
                .active(pattern.getActive())
                .schoolYearId(schoolYear != null ? schoolYear.getIdAnnee() : null)
                .schoolYearNom(schoolYear != null ? schoolYear.getNom() : null)
                .subjectLevelId(subjectLevel != null ? subjectLevel.getIdNiveauMatiere() : null)
                .subjectCode(subject != null ? subject.getCodeMatiere() : null)
                .subjectLib(subject != null ? subject.getLibMatiere() : null)
                .levelCode(level != null ? level.getCode() : null)
                .levelNom(level != null ? level.getNom() : null)
                .details(null)
                .build();
    }
}
