package tn.wtm.school.org.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.common.validations.ObjectsValidator;
import org.springframework.util.StringUtils;
import tn.wtm.school.org.dto.request.TeacherRequest;
import tn.wtm.school.org.dto.response.TeacherImportResult;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.mapper.TeacherMapper;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.TeacherService;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;
import tn.wtm.school.org.util.ExcelImportHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TeacherServiceImpl extends TenantService implements TeacherService {

    private final TeacherRepository teacherRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final SubjectLevelRepository subjectLevelRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final TeacherMapper teacherMapper;
    private final ObjectsValidator<TeacherRequest> teacherValidator;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    // ----- CRUD --------------------------------------------------------------

    @Override
    @Transactional
    public TeacherResponse createTeacher(TeacherRequest dto) {
        String tenantId = currentTenant();
        teacherValidator.validate(dto);
        validateTeacherUniqueness(dto, null, tenantId);

        Teacher teacher = teacherMapper.toEntity(dto);
        return teacherMapper.toResponseLight(teacherRepository.save(teacher));
    }

    @Override
    public TeacherResponse getTeacherById(Long id) {
        return teacherMapper.toResponseLight(findTeacherById(id));
    }

    @Override
    @Transactional
    public TeacherResponse getCurrentTeacher() {
        Teacher teacher = resoudreEnseignantCourant();
        if (teacher == null) {
            throw new ResourceNotFoundException(
                    "Aucune fiche enseignant n'est liée à votre compte. "
                    + "Demandez à l'administration de créer votre fiche dans Gestion des enseignants.");
        }
        return getTeacherWithAssignments(teacher.getIdEnseignant());
    }

    @Override
    @Transactional
    public Optional<Long> getCurrentTeacherId() {
        Teacher teacher = resoudreEnseignantCourant();
        return teacher == null ? Optional.empty() : Optional.of(teacher.getIdEnseignant());
    }

    /**
     * Fiche enseignant du compte connecté, ou null s'il n'en a pas.
     *
     * <p>Extrait de {@link #getCurrentTeacher()} pour être partagé avec
     * {@link #getCurrentTeacherId()} : la résolution est subtile (compte lié,
     * repli par email, auto-liaison des comptes anciens) et deux copies
     * finiraient par ne plus reconnaître les mêmes enseignants.</p>
     */
    private Teacher resoudreEnseignantCourant() {
        String tenantId = currentTenant();
        String keycloakUserId = jwtClaimsExtractor.getUserId();

        SchoolUser account = keycloakUserId == null ? null
                : schoolUserRepository.findByKeycloakUserId(keycloakUserId)
                        .filter(u -> tenantId.equals(u.getTenantId()))
                        .orElse(null);

        Teacher teacher = account != null ? account.getTeacher() : null;
        if (teacher != null) {
            return teacher;
        }

        // Repli par email : comptes créés avant la FK enseignant_id, ou JWT sans school_user
        String email = account != null ? account.getEmail() : jwtClaimsExtractor.getEmail();
        if (StringUtils.hasText(email)) {
            teacher = teacherRepository.findByTenantIdAndEmail(tenantId, email).orElse(null);
        }
        if (teacher != null && account != null) {
            account.setTeacher(teacher);
            schoolUserRepository.save(account);
            log.info("[Teacher/me] Compte school_user id={} auto-lié à la fiche enseignant id={}",
                    account.getId(), teacher.getIdEnseignant());
        }
        return teacher;
    }

    @Override
    public TeacherResponse getTeacherByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code enseignant est obligatoire");
        }
        Teacher teacher = teacherRepository.findByTenantIdAndCodeEnseignant(currentTenant(), code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant avec code " + code + " introuvable"));
        return teacherMapper.toResponseLight(teacher);
    }

    @Override
    public TeacherResponse getTeacherByNumIdentite(String numIdentite) {
        if (numIdentite == null || numIdentite.isBlank()) {
            throw new BadRequestException("Le numero d'identite est obligatoire");
        }
        Teacher teacher = teacherRepository.findByTenantIdAndNumIdentite(currentTenant(), numIdentite.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Enseignant avec numero d'identite " + numIdentite + " introuvable"));
        return teacherMapper.toResponseLight(teacher);
    }

    @Override
    public List<TeacherResponse> getAllTeachers() {
        return teacherMapper.toResponseLightList(teacherRepository.findByTenantId(currentTenant()));
    }

    @Override
    public List<TeacherResponse> getActiveTeachers() {
        return teacherMapper.toResponseLightList(
                teacherRepository.findByTenantIdAndEstEnPosteTrueOrderByNomAscPrenomAsc(currentTenant()));
    }

    @Override
    public Page<TeacherResponse> getAllTeachersPaginated(Pageable pageable) {
        return teacherRepository.findByTenantId(currentTenant(), pageable)
                .map(teacherMapper::toResponseLight);
    }

    @Override
    public Page<TeacherResponse> getTeachersByStatus(Boolean estEnPoste, Pageable pageable) {
        if (estEnPoste == null) {
            return getAllTeachersPaginated(pageable);
        }
        return teacherRepository.findByTenantIdAndEstEnPoste(currentTenant(), estEnPoste, pageable)
                .map(teacherMapper::toResponseLight);
    }

    @Override
    @Transactional
    public TeacherResponse updateTeacher(Long id, TeacherRequest dto) {
        String tenantId = currentTenant();
        teacherValidator.validate(dto);

        Teacher teacher = findTeacherById(id);
        validateTeacherUniqueness(dto, id, tenantId);

        teacherMapper.updateFromDto(dto, teacher);
        return teacherMapper.toResponseLight(teacherRepository.save(teacher));
    }

    @Override
    @Transactional
    public TeacherResponse deactivateTeacher(Long id) {
        Teacher teacher = findTeacherById(id);
        teacher.setEstEnPoste(Boolean.FALSE);
        return teacherMapper.toResponseLight(teacherRepository.save(teacher));
    }

    @Override
    @Transactional
    public TeacherResponse reactivateTeacher(Long id) {
        Teacher teacher = findTeacherById(id);
        teacher.setEstEnPoste(Boolean.TRUE);
        return teacherMapper.toResponseLight(teacherRepository.save(teacher));
    }

    @Override
    @Transactional
    public void deleteTeacher(Long id) {
        Teacher teacher = findTeacherById(id);
        if (!teachingAssignmentRepository.findByTeacher_IdEnseignant(id).isEmpty()) {
            throw new ConflictException("Impossible de supprimer un enseignant avec des affectations");
        }
        teacherRepository.delete(teacher);
    }

    // ----- Metier ------------------------------------------------------------

    @Override
    public Page<TeacherResponse> searchTeachers(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return getAllTeachersPaginated(pageable);
        }
        return teacherRepository.searchTeachers(search.trim(), pageable)
                .map(teacherMapper::toResponseLight);
    }

    @Override
    public TeacherResponse getTeacherWithAssignments(Long id) {
        Teacher teacher = teacherRepository.findByIdWithAssignments(requireId(id, "enseignant"))
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant avec ID " + id + " introuvable"));

        TeacherResponse response = teacherMapper.toResponse(teacher);
        response.setTotalHeures(calculateTotalHours(teacher.getTeachingAssignments()));
        return response;
    }

    @Override
    public List<TeacherResponse> getTeachersBySubjectLevel(Long subjectLevelId) {
        ensureSubjectLevelExists(subjectLevelId);
        return teacherMapper.toResponseLightList(teacherRepository.findBySubjectLevel(subjectLevelId));
    }

    @Override
    public List<TeacherResponse> getTeacherWorkload() {
        return teacherRepository.findByTenantIdAndEstEnPosteTrueOrderByNomAscPrenomAsc(currentTenant())
                .stream()
                .map(this::toWorkloadResponse)
                .toList();
    }

    @Override
    public boolean isOverloaded(Long teacherId, Double additionalHours) {
        Teacher teacher = findTeacherById(teacherId);
        if (teacher.getMaxHeuresSemaine() == null) {
            return false;
        }

        double additional = additionalHours != null ? additionalHours : 0.0;
        if (additional < 0) {
            throw new BadRequestException("Les heures additionnelles doivent etre positives ou nulles");
        }

        double currentLoad = calculateTotalHours(teachingAssignmentRepository.findByTeacher_IdEnseignant(teacherId));
        return currentLoad + additional > teacher.getMaxHeuresSemaine();
    }

    // ----- Import CSV --------------------------------------------------------

    @Override
    @Transactional
    public TeacherImportResult importFromCsv(InputStream csvStream) {
        String tenantId = currentTenant();
        List<TeacherImportResult.RowError> rowErrors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setCommentMarker('#')
                .build();

        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8), format)) {

            validateHeaders(parser.getHeaderNames());

            for (CSVRecord record : parser) {
                int lineNumber = (int) record.getRecordNumber() + 1;
                Map<String, String> row = record.toMap();
                String code = col(row, "codeEnseignant");
                try {
                    TeacherRequest req = parseRecord(row);
                    teacherValidator.validate(req);
                    int[] counts = processTeacherRow(req, lineNumber, code, tenantId, rowErrors);
                    imported += counts[0];
                    skipped  += counts[1];
                } catch (Exception ex) {
                    rowErrors.add(error(lineNumber, code, ex.getMessage()));
                }
            }

        } catch (IOException ex) {
            throw new BadRequestException("Impossible de lire le fichier CSV : " + ex.getMessage());
        }

        log.info("[import-teachers] tenant={} imported={} skipped={} errors={}",
                tenantId, imported, skipped, rowErrors.size() - skipped);

        return buildResult(imported, skipped, rowErrors);
    }

    // ----- Import Excel ------------------------------------------------------

    @Override
    @Transactional
    public TeacherImportResult importFromExcel(InputStream excelStream) {
        String tenantId = currentTenant();
        List<TeacherImportResult.RowError> rowErrors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        ExcelImportHelper.ExcelData data = ExcelImportHelper.parse(excelStream);
        validateHeaders(data.headers());

        int lineOffset = 2;
        for (int i = 0; i < data.rows().size(); i++) {
            int line = i + lineOffset;
            Map<String, String> row = data.rows().get(i);
            String code = col(row, "codeEnseignant");
            try {
                TeacherRequest req = parseRecord(row);
                teacherValidator.validate(req);
                int[] counts = processTeacherRow(req, line, code, tenantId, rowErrors);
                imported += counts[0];
                skipped  += counts[1];
            } catch (Exception ex) {
                rowErrors.add(error(line, code, ex.getMessage()));
            }
        }

        log.info("[import-teachers-excel] tenant={} imported={} skipped={} errors={}",
                tenantId, imported, skipped, rowErrors.size() - skipped);

        return buildResult(imported, skipped, rowErrors);
    }

    // ----- Shared row processing ---------------------------------------------

    /** Returns [importedDelta, skippedDelta] */
    private int[] processTeacherRow(TeacherRequest req, int line, String code,
                                     String tenantId,
                                     List<TeacherImportResult.RowError> rowErrors) {
        if (teacherRepository.existsByTenantIdAndCodeEnseignant(tenantId, req.getCodeEnseignant())) {
            rowErrors.add(error(line, code, "code déjà existant : " + req.getCodeEnseignant()));
            return new int[]{0, 1};
        }
        if (teacherRepository.existsByTenantIdAndNumIdentite(tenantId, req.getNumIdentite())) {
            rowErrors.add(error(line, code, "numIdentite déjà existant : " + req.getNumIdentite()));
            return new int[]{0, 1};
        }
        if (hasText(req.getEmail())
                && teacherRepository.existsByTenantIdAndEmail(tenantId, req.getEmail().trim())) {
            rowErrors.add(error(line, code, "email déjà existant : " + req.getEmail()));
            return new int[]{0, 1};
        }
        Teacher teacher = teacherMapper.toEntity(req);
        teacherRepository.save(teacher);
        return new int[]{1, 0};
    }

    // ----- Parsing helpers ---------------------------------------------------

    private void validateHeaders(List<String> headers) {
        List<String> required = List.of("codeEnseignant", "numIdentite", "nom", "prenom");
        List<String> missing = required.stream()
                .filter(h -> !headers.contains(h))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Colonnes manquantes : " + missing);
        }
    }

    private TeacherRequest parseRecord(Map<String, String> r) {
        return TeacherRequest.builder()
                .codeEnseignant(col(r, "codeEnseignant"))
                .numIdentite(col(r, "numIdentite"))
                .nom(col(r, "nom"))
                .prenom(col(r, "prenom"))
                .email(col(r, "email"))
                .telephone(col(r, "telephone"))
                .maxHeuresSemaine(parseInt(r, "maxHeuresSemaine"))
                .maxHeuresJour(parseInt(r, "maxHeuresJour"))
                .minHeuresJour(parseInt(r, "minHeuresJour"))
                .specialite(col(r, "specialite"))
                .estEnPoste(true)
                .build();
    }

    private String col(Map<String, String> r, String name) {
        String val = r.get(name);
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    private Integer parseInt(Map<String, String> r, String name) {
        String val = col(r, name);
        if (val == null) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Valeur numérique invalide pour " + name + " : " + val);
        }
    }

    private TeacherImportResult.RowError error(int line, String code, String reason) {
        return TeacherImportResult.RowError.builder()
                .line(line).codeEnseignant(code).reason(reason).build();
    }

    private TeacherImportResult buildResult(int imported, int skipped,
                                             List<TeacherImportResult.RowError> rowErrors) {
        return TeacherImportResult.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(rowErrors.size() - skipped)
                .rowErrors(rowErrors)
                .build();
    }

    // ----- Privé -------------------------------------------------------------

    private Teacher findTeacherById(Long id) {
        return teacherRepository.findByTenantIdAndIdEnseignant(currentTenant(), requireId(id, "enseignant"))
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant avec ID " + id + " introuvable"));
    }

    private void ensureSubjectLevelExists(Long subjectLevelId) {
        if (subjectLevelId == null) {
            throw new BadRequestException("L'identifiant matiere-niveau est obligatoire");
        }
        if (!subjectLevelRepository.existsById(subjectLevelId)) {
            throw new ResourceNotFoundException("Matiere-niveau avec ID " + subjectLevelId + " introuvable");
        }
    }

    private Long requireId(Long id, String label) {
        if (id == null) {
            throw new BadRequestException("L'identifiant " + label + " est obligatoire");
        }
        return id;
    }

    private void validateTeacherUniqueness(TeacherRequest dto, Long excludeId, String tenantId) {
        if (excludeId == null) {
            if (teacherRepository.existsByTenantIdAndCodeEnseignant(tenantId, dto.getCodeEnseignant())) {
                throw new ConflictException("Un enseignant avec le code " + dto.getCodeEnseignant() + " existe deja");
            }
            if (teacherRepository.existsByTenantIdAndNumIdentite(tenantId, dto.getNumIdentite())) {
                throw new ConflictException("Un enseignant avec ce numero d'identite existe deja");
            }
            if (hasText(dto.getEmail()) && teacherRepository.existsByTenantIdAndEmail(tenantId, dto.getEmail().trim())) {
                throw new ConflictException("Un enseignant avec l'email " + dto.getEmail() + " existe deja");
            }
            return;
        }

        if (teacherRepository.existsByCodeEnseignantAndIdEnseignantNot(dto.getCodeEnseignant(), excludeId)) {
            throw new ConflictException("Un enseignant avec le code " + dto.getCodeEnseignant() + " existe deja");
        }

        boolean numIdentiteExists = teacherRepository.findByTenantIdAndNumIdentite(tenantId, dto.getNumIdentite())
                .filter(existing -> !Objects.equals(existing.getIdEnseignant(), excludeId))
                .isPresent();
        if (numIdentiteExists) {
            throw new ConflictException("Un enseignant avec ce numero d'identite existe deja");
        }

        if (hasText(dto.getEmail())
                && teacherRepository.existsByEmailAndIdEnseignantNot(dto.getEmail().trim(), excludeId)) {
            throw new ConflictException("Un enseignant avec l'email " + dto.getEmail() + " existe deja");
        }
    }

    private TeacherResponse toWorkloadResponse(Teacher teacher) {
        List<TeachingAssignment> assignments = teachingAssignmentRepository
                .findByTeacher_IdEnseignant(teacher.getIdEnseignant())
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .toList();

        double totalHours = calculateTotalHours(assignments);

        return TeacherResponse.builder()
                .idEnseignant(teacher.getIdEnseignant())
                .codeEnseignant(teacher.getCodeEnseignant())
                .numIdentite(teacher.getNumIdentite())
                .nom(teacher.getNom())
                .prenom(teacher.getPrenom())
                .nomComplet(teacher.getPrenom() + " " + teacher.getNom())
                .email(teacher.getEmail())
                .telephone(teacher.getTelephone())
                .maxHeuresSemaine(teacher.getMaxHeuresSemaine())
                .maxHeuresJour(teacher.getMaxHeuresJour())
                .minHeuresJour(teacher.getMinHeuresJour())
                .estEnPoste(teacher.getEstEnPoste())
                .photo(teacher.getPhoto())
                .specialite(teacher.getSpecialite())
                .nombreAffectations(assignments.size())
                .totalHeures(totalHours)
                .affectations(null)
                .build();
    }

    private double calculateTotalHours(List<TeachingAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return 0.0;
        }
        return assignments.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .map(TeachingAssignment::getSubjectSessionType)
                .filter(Objects::nonNull)
                .map(sst -> sst.getDuration() != null ? sst.getDuration() : 0.0)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
