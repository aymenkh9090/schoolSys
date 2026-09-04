package tn.wtm.school.org.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.org.dto.request.ApplyNationalPatternRequest;
import tn.wtm.school.org.dto.response.NationalPatternResponse;
import tn.wtm.school.org.entity.*;
import tn.wtm.school.org.enums.PatternType;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.WeekParity;
import tn.wtm.school.org.repository.*;
import tn.wtm.school.org.service.NationalPatternService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NationalPatternServiceImpl extends TenantService implements NationalPatternService {

    private static final Map<String, String> LEVEL_NAMES = Map.of(
            "7EME", "7ème de Base",
            "8EME", "8ème de Base",
            "9EME", "9ème de Base"
    );

    private static final Map<String, String> SUBJECT_NAMES = Map.ofEntries(
            Map.entry("AR",      "Arabe"),
            Map.entry("FR",      "Français"),
            Map.entry("EN",      "Anglais"),
            Map.entry("HISTGEO", "Histoire-Géographie"),
            Map.entry("MATH",    "Mathématiques"),
            Map.entry("SCI",     "Sciences de la Vie et de la Terre"),
            Map.entry("PHY",     "Physique-Chimie"),
            Map.entry("ISL",     "Éducation Islamique"),
            Map.entry("CIV",     "Éducation Civique"),
            Map.entry("INFO",    "Informatique"),
            Map.entry("TECH",    "Technologie"),
            Map.entry("SPORT",   "Éducation Physique"),
            Map.entry("MUS",     "Musique"),
            Map.entry("DESSIN",  "Dessin"),
            Map.entry("THEATRE", "Théâtre")
    );

    private final NationalPatternRepository       nationalPatternRepository;
    private final SubjectRepository               subjectRepository;
    private final LevelRepository                 levelRepository;
    private final SubjectLevelRepository          subjectLevelRepository;
    private final PatternRepository               patternRepository;
    private final PatternDetailRepository         patternDetailRepository;
    private final SchoolYearRepository            schoolYearRepository;
    private final SubjectSessionTypeRepository    subjectSessionTypeRepository;

    // ── queries ───────────────────────────────────────────────────────────────

    @Override
    public List<NationalPatternResponse> findAllActive(String countryCode, Integer academicYear) {
        List<NationalPattern> patterns = (academicYear != null)
                ? nationalPatternRepository.findByCountryCodeAndAcademicYearAndActiveTrue(countryCode, academicYear)
                : nationalPatternRepository.findByCountryCodeAndActiveTrue(countryCode);
        return patterns.stream().map(this::toResponse).toList();
    }

    @Override
    public NationalPatternResponse findById(Long id) {
        NationalPattern np = nationalPatternRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("National pattern introuvable : " + id));
        return toResponse(np);
    }

    @Override
    public NationalPatternResponse findByLevelCode(String countryCode, String levelCode) {
        List<NationalPattern> patterns = nationalPatternRepository
                .findActiveWithDetailsByCountryAndLevel(countryCode, levelCode);
        if (patterns.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Aucun pattern national actif pour country=" + countryCode + " level=" + levelCode);
        }
        return toResponse(patterns.get(0));
    }

    // ── apply to tenant ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public NationalPatternApplyResult applyToTenant(Long nationalPatternId, Long schoolYearId) {
        String tenantId = currentTenant();

        NationalPattern np = nationalPatternRepository.findByIdWithDetails(nationalPatternId)
                .orElseThrow(() -> new ResourceNotFoundException("National pattern introuvable : " + nationalPatternId));

        SchoolYear schoolYear = resolveSchoolYear(tenantId, schoolYearId);
        Level level = ensureStructure(np, tenantId);

        int created = 0;
        int skipped = 0;

        for (NationalPatternDetail detail : np.getDetails()) {
            Optional<Subject> subjectOpt =
                    subjectRepository.findByTenantIdAndCodeMatiere(tenantId, detail.getSubjectCode());

            if (subjectOpt.isEmpty()) {
                log.warn("[national-pattern] Matière '{}' absente pour tenant={}", detail.getSubjectCode(), tenantId);
                skipped++;
                continue;
            }

            Optional<SubjectLevel> slOpt = subjectLevelRepository
                    .findBySubject_IdMatiereAndLevel_IdNiveau(
                            subjectOpt.get().getIdMatiere(), level.getIdNiveau());

            if (slOpt.isEmpty()) {
                log.warn("[national-pattern] SubjectLevel manquant pour {}/{} tenant={}",
                        detail.getSubjectCode(), np.getLevelCode(), tenantId);
                skipped++;
                continue;
            }

            SubjectLevel subjectLevel = slOpt.get();

            // Toujours garantir les types de séance, même si le pattern existe déjà
            // (les affectations enseignants en dépendent)
            ensureSessionTypes(detail, subjectLevel, tenantId);

            String patternName = np.getCode() + "_" + detail.getSubjectCode();

            if (patternRepository.existsByNameAndSubjectLevelAndYear(
                    patternName, subjectLevel.getIdNiveauMatiere(),
                    schoolYearId, null)) {
                log.debug("[national-pattern] Pattern '{}' déjà présent — ignoré", patternName);
                skipped++;
                continue;
            }

            createTenantPattern(detail, subjectLevel, schoolYear, tenantId, patternName, np);
            created++;
        }

        log.info("[national-pattern] Apply {} → tenant={} : created={} skipped={}",
                np.getCode(), tenantId, created, skipped);
        return new NationalPatternApplyResult(created, skipped,
                "Créé : " + created + ", ignoré : " + skipped);
    }

    // ── apply by country + levels ─────────────────────────────────────────────

    @Override
    @Transactional
    public ApplyNationalResult applyByRequest(ApplyNationalPatternRequest request) {
        String tenantId = currentTenant();
        List<LevelResult> results = new ArrayList<>();

        for (String levelCode : request.levels()) {
            List<NationalPattern> patterns = nationalPatternRepository
                    .findActiveWithDetailsByCountryAndLevel(request.country(), levelCode);

            if (patterns.isEmpty()) {
                log.warn("[national-pattern] Aucun pattern actif pour country={} level={}", request.country(), levelCode);
                results.add(new LevelResult(levelCode, 0, 0,
                        "Aucun pattern national trouvé pour " + levelCode));
                continue;
            }

            NationalPattern np = patterns.get(0);

            NationalPatternApplyResult r = applyToTenant(np.getIdNationalPattern(), request.schoolYearId());
            results.add(new LevelResult(levelCode, r.created(), r.skipped(), r.message()));
        }

        int totalCreated = results.stream().mapToInt(LevelResult::created).sum();
        int totalSkipped = results.stream().mapToInt(LevelResult::skipped).sum();
        return new ApplyNationalResult(totalCreated, totalSkipped, results);
    }

    private Level ensureStructure(NationalPattern np, String tenantId) {
        // 1. Niveau
        Level level = levelRepository.findByTenantIdAndCode(tenantId, np.getLevelCode())
                .orElseGet(() -> {
                    String nom = LEVEL_NAMES.getOrDefault(np.getLevelCode(), np.getLevelCode());
                    Level l = Level.builder()
                            .code(np.getLevelCode())
                            .nom(nom)
                            .description(nom)
                            .estActif(true)
                            .build();
                    l.setTenantId(tenantId);
                    log.info("[national-pattern] Niveau '{}' créé pour tenant={}", np.getLevelCode(), tenantId);
                    return levelRepository.save(l);
                });

        // 2. Matières + SubjectLevels
        for (NationalPatternDetail detail : np.getDetails()) {
            Subject subject = subjectRepository
                    .findByTenantIdAndCodeMatiere(tenantId, detail.getSubjectCode())
                    .orElseGet(() -> {
                        boolean needsLab = detail.getSessions().stream()
                                .anyMatch(s -> s.getRequiredRoomType() != null
                                        && s.getRequiredRoomType().toUpperCase().contains("LAB"));
                        boolean needsSport = detail.getSessions().stream()
                                .anyMatch(s -> s.getRequiredRoomType() != null
                                        && s.getRequiredRoomType().toUpperCase().contains("SPORT"));
                        String nom = SUBJECT_NAMES.getOrDefault(detail.getSubjectCode(), detail.getSubjectCode());
                        Subject s = Subject.builder()
                                .codeMatiere(detail.getSubjectCode())
                                .libMatiere(nom)
                                .description(nom)
                                .necessiteLab(needsLab)
                                .necessiteSport(needsSport)
                                .estEnseignee(true)
                                .build();
                        s.setTenantId(tenantId);
                        log.info("[national-pattern] Matière '{}' créée pour tenant={}", detail.getSubjectCode(), tenantId);
                        return subjectRepository.save(s);
                    });

            subjectLevelRepository
                    .findBySubject_IdMatiereAndLevel_IdNiveau(subject.getIdMatiere(), level.getIdNiveau())
                    .orElseGet(() -> {
                        SubjectLevel sl = SubjectLevel.builder()
                                .subject(subject)
                                .level(level)
                                .heuresSemaine(detail.getTotalHoursPerWeek())
                                .estObligatoire(true)
                                .build();
                        sl.setTenantId(tenantId);
                        return subjectLevelRepository.save(sl);
                    });
        }

        return level;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Crée les SubjectSessionType manquants d'un subjectLevel à partir des séances
     * du pattern national (un par type de séance — contrainte d'unicité
     * tenant/subjectLevel/type). Idempotent.
     */
    private void ensureSessionTypes(NationalPatternDetail detail, SubjectLevel subjectLevel, String tenantId) {
        var existingTypes = subjectSessionTypeRepository
                .findBySubjectLevel_IdNiveauMatiere(subjectLevel.getIdNiveauMatiere())
                .stream()
                .map(SubjectSessionType::getType)
                .collect(java.util.stream.Collectors.toSet());

        Map<SessionType, List<NationalPatternSession>> byType = detail.getSessions().stream()
                .collect(java.util.stream.Collectors.groupingBy(NationalPatternSession::getSessionType));

        byType.forEach((type, sessions) -> {
            if (existingTypes.contains(type)) return;
            double duration = sessions.stream()
                    .mapToDouble(NationalPatternSession::getDuration)
                    .max().orElse(1.0);
            boolean split = sessions.stream()
                    .anyMatch(s -> "DEMI_GROUP".equalsIgnoreCase(s.getGroupingType()));
            SubjectSessionType sst = SubjectSessionType.builder()
                    .subjectLevel(subjectLevel)
                    .type(type)
                    .duration(duration)
                    .requiresSplit(split)
                    .groupCount(split ? 2 : null)
                    .estActif(true)
                    .build();
            sst.setTenantId(tenantId);
            subjectSessionTypeRepository.save(sst);
            log.info("[national-pattern] SessionType {} ({}h{}) créé pour subjectLevel={} tenant={}",
                    type, duration, split ? ", demi-groupe" : "",
                    subjectLevel.getIdNiveauMatiere(), tenantId);
        });
    }

    private void createTenantPattern(NationalPatternDetail detail, SubjectLevel subjectLevel,
                                      SchoolYear schoolYear, String tenantId,
                                      String patternName, NationalPattern np) {
        List<NationalPatternSession> sessions = detail.getSessions().stream()
                .sorted((a, b) -> Integer.compare(a.getSessionOrder(), b.getSessionOrder()))
                .toList();

        double totalHours = sessions.stream().mapToDouble(NationalPatternSession::getDuration).sum();
        boolean hasBiweekly = sessions.stream()
                .anyMatch(s -> "BIWEEKLY".equalsIgnoreCase(s.getWeekParity()));

        Pattern pattern = Pattern.builder()
                .name(patternName)
                .totalHours(totalHours)
                .sessionCount(sessions.size())
                .repartition(detail.getRepartition())
                .patternType(hasBiweekly ? PatternType.BIWEEKLY : PatternType.WEEKLY_IDENTICAL)
                .subjectLevel(subjectLevel)
                .schoolYear(schoolYear)
                .build();
        pattern.setTenantId(tenantId);

        List<PatternDetail> details = new ArrayList<>();
        for (NationalPatternSession s : sessions) {
            PatternDetail pd = PatternDetail.builder()
                    .sessionOrder(s.getSessionOrder())
                    .duration(s.getDuration())
                    .type(s.getSessionType())
                    .weekParity(mapWeekParity(s.getWeekParity()))
                    .isSplit("DEMI_GROUP".equalsIgnoreCase(s.getGroupingType()))
                    .requiredRoomType(mapRoomType(s.getRequiredRoomType(), s.getSessionType()))
                    .pattern(pattern)
                    .build();
            pd.setTenantId(tenantId);
            details.add(pd);
        }

        pattern.setPatternDetails(details);
        patternRepository.save(pattern);
    }

    private SchoolYear resolveSchoolYear(String tenantId, Long schoolYearId) {
        if (schoolYearId == null) return null;
        return schoolYearRepository.findByTenantIdAndIdAnnee(tenantId, schoolYearId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année scolaire introuvable : " + schoolYearId));
    }

    // ── enum mappers ──────────────────────────────────────────────────────────

    static WeekParity mapWeekParity(String raw) {
        if (raw == null) return WeekParity.ALL;
        return switch (raw.toUpperCase()) {
            case "BIWEEKLY" -> WeekParity.BIWEEKLY;
            case "ODD"      -> WeekParity.ODD;
            case "EVEN"     -> WeekParity.EVEN;
            default         -> WeekParity.ALL;
        };
    }

    static RoomType mapRoomType(String raw, SessionType sessionType) {
        if (raw == null) return RoomType.NORMALE;
        return switch (raw.toUpperCase()) {
            case "LABPHYSIQUE",     "LAB_PHYSICS" -> RoomType.LABPHYSIQUE;
            case "LABSCIENCE",      "LAB_SCIENCE" -> RoomType.LABSCIENCE;
            case "LABINFORMATIQUE", "COMPUTER"    -> RoomType.LABINFORMATIQUE;
            case "LABTECHNIQUE",    "LAB_TECH"    -> RoomType.LABTECHNIQUE;
            case "SALLESPORT",      "SALLE_SPORT" -> RoomType.SALLESPORT;
            default                               -> RoomType.NORMALE;
        };
    }

    // ── response mapping ──────────────────────────────────────────────────────

    private NationalPatternResponse toResponse(NationalPattern np) {
        List<NationalPatternResponse.DetailResponse> detailResponses = np.getDetails() == null
                ? List.of()
                : np.getDetails().stream().map(d -> NationalPatternResponse.DetailResponse.builder()
                        .idNationalPatternDetail(d.getIdNationalPatternDetail())
                        .subjectCode(d.getSubjectCode())
                        .totalHoursPerWeek(d.getTotalHoursPerWeek())
                        .repartition(d.getRepartition())
                        .sessions(d.getSessions() == null ? List.of() :
                                d.getSessions().stream()
                                        .sorted((a, b) -> Integer.compare(a.getSessionOrder(), b.getSessionOrder()))
                                        .map(s -> NationalPatternResponse.SessionResponse.builder()
                                                .idNationalPatternSession(s.getIdNationalPatternSession())
                                                .sessionOrder(s.getSessionOrder())
                                                .sessionType(s.getSessionType() != null ? s.getSessionType().name() : null)
                                                .duration(s.getDuration())
                                                .groupingType(s.getGroupingType())
                                                .requiredRoomType(s.getRequiredRoomType())
                                                .weekParity(s.getWeekParity())
                                                .build())
                                        .toList())
                        .build())
                .toList();

        return NationalPatternResponse.builder()
                .idNationalPattern(np.getIdNationalPattern())
                .code(np.getCode())
                .name(np.getName())
                .version(np.getVersion())
                .academicYear(np.getAcademicYear())
                .active(np.getActive())
                .countryCode(np.getCountryCode())
                .levelCode(np.getLevelCode())
                .details(detailResponses)
                .build();
    }
}
