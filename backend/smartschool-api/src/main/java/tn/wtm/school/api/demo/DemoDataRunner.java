package tn.wtm.school.api.demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.config.TenantFilterConstants;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.dto.request.SchoolConfigRequestDTO;
import tn.wtm.school.org.dto.request.WorkingDayRequestDTO;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Pattern;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.enums.PatternType;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.Specialite;
import tn.wtm.school.org.enums.WeekParity;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.PatternDetailRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.repository.WorkingDayRepository;
import tn.wtm.school.org.service.impl.SchoolConfigurationService;
import tn.wtm.school.planning.constraints.entity.ConstraintDefinition;
import tn.wtm.school.planning.constraints.entity.ConstraintProfile;
import tn.wtm.school.planning.constraints.entity.ConstraintSetting;
import tn.wtm.school.planning.constraints.enums.ConstraintCategory;
import tn.wtm.school.planning.constraints.enums.ConstraintType;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;
import tn.wtm.school.planning.constraints.repository.ConstraintDefinitionRepository;
import tn.wtm.school.planning.constraints.repository.ConstraintProfileRepository;
import tn.wtm.school.planning.constraints.repository.ConstraintSettingRepository;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.repository.TenantRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataRunner implements ApplicationRunner {

    private static final String TENANT_IBN      = "tenant-ibn-khaldoun";
    private static final String TENANT_CARTHAGE = "tenant-carthage";

    // ── organisation-module repositories ─────────────────────────────────────
    private final TenantRepository                  tenantRepository;
    private final SchoolYearRepository              schoolYearRepository;
    private final LevelRepository                   levelRepository;
    private final ClassGroupRepository              classGroupRepository;
    private final SubjectRepository                 subjectRepository;
    private final SubjectLevelRepository            subjectLevelRepository;
    private final SubjectSessionTypeRepository      subjectSessionTypeRepository;
    private final TeacherRepository                 teacherRepository;
    private final RoomRepository                    roomRepository;
    private final TeachingAssignmentRepository      teachingAssignmentRepository;
    private final PatternRepository                 patternRepository;
    private final PatternDetailRepository           patternDetailRepository;
    private final WorkingDayRepository              workingDayRepository;
    private final SchoolConfigurationService        schoolConfigurationService;

    // ── planning-module repositories ──────────────────────────────────────────
    private final ConstraintDefinitionRepository    constraintDefinitionRepository;
    private final ConstraintProfileRepository       constraintProfileRepository;
    private final ConstraintSettingRepository       constraintSettingRepository;

    // ─────────────────────────────────────────────────────────────────────────

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Bascule le seeder sur un tenant, des DEUX côtés :
     *   - écriture : TenantContext, lu par TenantEntityListener pour remplir tenant_id ;
     *   - lecture  : le filtre Hibernate tenantFilter, qui restreint tout SELECT
     *                sur un TenantEntity.
     *
     * Sans le second, le filtre reste figé sur la valeur posée par
     * TenantHibernateFilterAspect au premier appel @Transactional du run (celui de
     * SchoolConfigurationService pendant seedIbnKhaldoun) : run() est UNE seule
     * transaction, et l'aspect ne se redéclenche pas sur les appels de repository
     * (proxy d'interface Spring Data, non annoté @Transactional). Les contrôles
     * "existe déjà ?" de seedCarthage lisaient alors tenant-ibn-khaldoun, ne
     * trouvaient rien, et ré-inséraient des lignes déjà présentes
     * → duplicate key sur uk_type_seance_tenant_sl_type.
     */
    private void beginTenant(String tenantId) {
        TenantContext.setTenantId(tenantId);
        entityManager.unwrap(Session.class)
                .enableFilter(TenantFilterConstants.FILTER_NAME)
                .setParameter(TenantFilterConstants.PARAM_TENANT_ID, tenantId);
    }

    /** Symétrique de {@link #beginTenant(String)} : ne laisse aucun tenant actif. */
    private void endTenant() {
        TenantContext.clear();
        entityManager.unwrap(Session.class)
                .disableFilter(TenantFilterConstants.FILTER_NAME);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedConstraintDefinitions();
        seedTenantCatalog();
        seedIbnKhaldoun();
        seedCarthage();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Global constraint catalog (mirrors Liquibase 004-seed-base-constraints)
    // ══════════════════════════════════════════════════════════════════════════

    private void seedConstraintDefinitions() {
        if (constraintDefinitionRepository.count() > 0) return;

        def("ONE_TEACHER_PER_SUBJECT_CLASS",          "One teacher per subject per class",
                ConstraintCategory.TEACHER,    ConstraintType.HARD,   ImportanceLevel.CRITICAL,
                "{\"enabled\":{\"type\":\"boolean\",\"default\":true}}");
        def("MAX_STUDENT_HOURS_PER_DAY",              "Maximum student hours per day",
                ConstraintCategory.STUDENT,    ConstraintType.HARD,   ImportanceLevel.CRITICAL,
                "{\"maxHours\":{\"type\":\"number\",\"default\":6}}");
        def("MAX_TEACHER_HOURS_PER_DAY",              "Maximum teacher hours per day",
                ConstraintCategory.TEACHER,    ConstraintType.HARD,   ImportanceLevel.CRITICAL,
                "{\"maxHours\":{\"type\":\"number\",\"default\":6}}");
        def("MAX_TEACHER_HOURS_FRIDAY_SATURDAY",      "Maximum teacher hours on Friday and Saturday",
                ConstraintCategory.TEACHER,    ConstraintType.HARD,   ImportanceLevel.CRITICAL,
                "{\"maxHours\":{\"type\":\"number\",\"default\":5}}");
        def("RESPECT_OFFICIAL_SUBJECT_HOURS",         "Respect official subject hours",
                ConstraintCategory.PEDAGOGICAL, ConstraintType.HARD,  ImportanceLevel.CRITICAL,
                "{\"enabled\":{\"type\":\"boolean\",\"default\":true}}");
        def("PHYSICAL_EDUCATION_THREE_SESSIONS",      "Physical education three sessions",
                ConstraintCategory.SUBJECT,    ConstraintType.HARD,   ImportanceLevel.CRITICAL,
                "{\"weeklySessions\":{\"type\":\"number\",\"default\":3}}");
        def("MAX_TWO_CONSECUTIVE_SESSIONS_SAME_SUBJECT", "Max two consecutive sessions same subject",
                ConstraintCategory.SUBJECT,    ConstraintType.HARD,   ImportanceLevel.HIGH,
                "{\"maxConsecutiveSessions\":{\"type\":\"number\",\"default\":2}}");
        def("BALANCED_MORNING_AFTERNOON",             "Balanced morning and afternoon sessions",
                ConstraintCategory.TEACHER,    ConstraintType.MEDIUM, ImportanceLevel.HIGH,
                "{\"enabled\":{\"type\":\"boolean\",\"default\":true}}");
        def("TEACHER_MIN_TWO_LEVELS",                 "Teacher teaches minimum two levels",
                ConstraintCategory.TEACHER,    ConstraintType.SOFT,   ImportanceLevel.MEDIUM,
                "{\"minLevels\":{\"type\":\"number\",\"default\":2}}");
        def("BALANCED_TEACHER_WORKLOAD",              "Service réparti sur la semaine",
                ConstraintCategory.TEACHER,    ConstraintType.SOFT,   ImportanceLevel.MEDIUM,
                "{\"workingDays\":{\"type\":\"number\",\"default\":6}}");
        def("TEACHER_WEEKLY_REST_DAY",                "Teacher weekly rest day",
                ConstraintCategory.TEACHER,    ConstraintType.SOFT,   ImportanceLevel.MEDIUM,
                "{\"enabled\":{\"type\":\"boolean\",\"default\":true}}");
        def("AVOID_SUBJECT_CONCENTRATION_SAME_DAY",   "Avoid subject concentration same day",
                ConstraintCategory.SUBJECT,    ConstraintType.SOFT,   ImportanceLevel.MEDIUM,
                "{\"enabled\":{\"type\":\"boolean\",\"default\":true}}");
        def("MAIN_SUBJECT_BALANCED_DISTRIBUTION",     "Matière répartie matin et après-midi",
                ConstraintCategory.PEDAGOGICAL, ConstraintType.SOFT,  ImportanceLevel.MEDIUM,
                "{\"weeklyHours\":{\"type\":\"number\",\"default\":2}}");
        def("THEORY_PRACTICE_SEPARATION",             "Theory practice separation",
                ConstraintCategory.SUBJECT,    ConstraintType.MEDIUM, ImportanceLevel.HIGH,
                "{\"enabled\":{\"type\":\"boolean\",\"default\":true}}");
    }

    private void def(String code, String name,
                     ConstraintCategory category, ConstraintType type,
                     ImportanceLevel importance, String paramSchema) {
        if (constraintDefinitionRepository.existsByCode(code)) return;
        constraintDefinitionRepository.save(ConstraintDefinition.builder()
                .code(code)
                .name(name)
                .category(category)
                .type(type)
                .defaultImportance(importance)
                .defaultEnabled(true)
                .parameterSchema(paramSchema)
                .build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tenant seeds
    // ══════════════════════════════════════════════════════════════════════════

    private void seedTenantCatalog() {
        ensureTenant("IBN_KHALDOUN", "College Ibn Khaldoun", TenantPlan.PREMIUM);
        ensureTenant("CARTHAGE",     "College Carthage",    TenantPlan.STANDARD);
    }

    private void seedIbnKhaldoun() {
        beginTenant(TENANT_IBN);
        try {
            SchoolYear year = ensureYear("2024-2025");

            // ── 3 niveaux ────────────────────────────────────────────────────
            Map<String, Level> levels = Map.of(
                    "7", ensureLevel("7", "7ème de base"),
                    "8", ensureLevel("8", "8ème de base"),
                    "9", ensureLevel("9", "9ème de base")
            );

            // ── 10 matières ──────────────────────────────────────────────────
            Map<String, Subject> subjects = ensureSubjects(List.of(
                    subjectSpec("AR",    "Arabe",               false, false),
                    subjectSpec("FR",    "Français",            false, false),
                    subjectSpec("EN",    "Anglais",             false, false),
                    subjectSpec("MATH",  "Mathématiques",       false, false),
                    subjectSpec("SCI",   "Sciences naturelles", true,  false),
                    subjectSpec("PHY",   "Physique",            true,  false),
                    subjectSpec("TECH",  "Technologie",         true,  false),
                    subjectSpec("ISL",   "Éducation islamique", false, false),
                    subjectSpec("CIV",   "Éducation civique",   false, false),
                    subjectSpec("SPORT", "Éducation physique",  false, true)
            ));

            // ── 13 classes (28 élèves chacune) ───────────────────────────────
            Map<String, ClassGroup> classes = new HashMap<>();
            for (String c : List.of("7A1","7A2","7A3","7A4","7A5"))
                classes.put(c, ensureClass(c, levels.get("7"), year));
            for (String c : List.of("8A1","8A2","8A3","8A4"))
                classes.put(c, ensureClass(c, levels.get("8"), year));
            for (String c : List.of("9A1","9A2","9A3","9A4"))
                classes.put(c, ensureClass(c, levels.get("9"), year));

            // ── 23 enseignants (un seul matiere par enseignant) ───────────────
            Map<String, Teacher> teachersByCode = new HashMap<>();
            for (Teacher t : List.of(
                    ensureTeacher("IBN-T01","CIN-01","Ben Ali",  "Mohamed"),  // AR
                    ensureTeacher("IBN-T02","CIN-02","Trabelsi", "Nadia"),    // AR
                    ensureTeacher("IBN-T03","CIN-03","Cherif",   "Sami"),     // FR
                    ensureTeacher("IBN-T04","CIN-04","Mansour",  "Leila"),    // FR
                    ensureTeacher("IBN-T05","CIN-05","Gharbi",   "Mourad"),   // MATH
                    ensureTeacher("IBN-T06","CIN-06","Mejri",    "Yassine"),  // MATH
                    ensureTeacher("IBN-T07","CIN-07","Ayari",    "Sonia"),    // MATH
                    ensureTeacher("IBN-T08","CIN-08","Jlassi",   "Rania"),    // SCI
                    ensureTeacher("IBN-T09","CIN-09","Kacem",    "Omar"),     // SCI
                    ensureTeacher("IBN-T10","CIN-10","Bouzid",   "Ali"),      // SCI
                    ensureTeacher("IBN-T11","CIN-11","Saidi",    "Imen"),     // TECH
                    ensureTeacher("IBN-T12","CIN-12","Baccar",   "Khaled"),   // TECH
                    ensureTeacher("IBN-T13","CIN-13","Hamdi",    "Meriem"),   // EN
                    ensureTeacher("IBN-T14","CIN-14","Zouari",   "Hatem"),    // EN
                    ensureTeacher("IBN-T15","CIN-15","Feki",     "Nour"),     // ISL
                    ensureTeacher("IBN-T16","CIN-16","Ben Salah","Rim"),      // ISL
                    ensureTeacher("IBN-T17","CIN-17","Ayoub",    "Tarek"),    // SPORT
                    ensureTeacher("IBN-T18","CIN-18","Dridi",    "Fatma"),    // SPORT
                    ensureTeacher("IBN-T19","CIN-19","Chaabane", "Walid"),    // PHY
                    ensureTeacher("IBN-T20","CIN-20","Rekik",    "Asma"),     // PHY
                    ensureTeacher("IBN-T21","CIN-21","Guesmi",   "Bilel"),    // PHY
                    ensureTeacher("IBN-T22","CIN-22","Sassi",    "Ines"),     // CIV
                    ensureTeacher("IBN-T23","CIN-23","Marzouki", "Anis")      // CIV
            )) {
                teachersByCode.put(t.getCodeEnseignant(), t);
            }

            // ── 19 salles ────────────────────────────────────────────────────
            for (int i = 1; i <= 10; i++)
                ensureRoom(String.format("S%02d", i), "NORMALE", 30);
            // Labos sciences (SVT)
            ensureRoom("sc1", "LABSCIENCE", 16);
            ensureRoom("sc2", "LABSCIENCE", 16);
            ensureRoom("sc3", "LABSCIENCE", 16);
            ensureRoom("sc4", "LABSCIENCE", 16);
            // Labos physique-chimie
            ensureRoom("ph1", "LABPHYSIQUE", 16);
            ensureRoom("ph2", "LABPHYSIQUE", 16);
            ensureRoom("ph3", "LABPHYSIQUE", 16);
            ensureRoom("ph4", "LABPHYSIQUE", 16);
            // Salles technologie / info
            ensureRoom("tch1", "LABINFORMATIQUE", 16);
            ensureRoom("tch2", "LABINFORMATIQUE", 16);
            // Salles sport
            ensureRoom("sp1", "SALLESPORT", 40);
            ensureRoom("sp2", "SALLESPORT", 40);
            ensureRoom("sp3", "SALLESPORT", 40);

            // ── SubjectLevels, affectations et patterns ──────────────────────
            Map<String, SubjectLevel> subjectLevels = ensureSubjectLevels(subjects, levels);
            Map<String, Teacher> primaryTeacherMap  = ensureIbnKhaldounAssignments(
                    year, classes, subjectLevels, teachersByCode);
            ensureAdditionalSSTsAndTAs(year, classes, subjectLevels, primaryTeacherMap);
            ensurePatterns(subjectLevels);
            ensureConstraintProfile(year);

            if (workingDayRepository.findByTenantIdOrderByDayOfWeekAsc(TENANT_IBN).isEmpty()) {
                schoolConfigurationService.configure(ibnKhaldounConfig());
            }
        } finally {
            endTenant();
        }
    }

    private void seedCarthage() {
        beginTenant(TENANT_CARTHAGE);
        try {
            SchoolYear year   = ensureYear("2024-2025");
            Map<String, Level>   levels   = Map.of(
                    "7", ensureLevel("7", "7eme"),
                    "8", ensureLevel("8", "8eme")
            );
            Map<String, Subject> subjects = ensureSubjects(List.of(
                    subjectSpec("MATH", "Mathematiques", false, false),
                    subjectSpec("FR",   "Francais",      false, false),
                    subjectSpec("AR",   "Arabe",         false, false),
                    subjectSpec("EN",   "Anglais",       false, false),
                    subjectSpec("SCI",  "Sciences",      true,  false)
            ));
            Map<String, ClassGroup> classes = Map.of(
                    "7A1", ensureClass("7A1", levels.get("7"), year),
                    "8A1", ensureClass("8A1", levels.get("8"), year)
            );
            List<Teacher> teachers = List.of(
                    ensureTeacher("CAR-T01", "CIN-CAR-01", "Saidi",  "Nour"),
                    ensureTeacher("CAR-T02", "CIN-CAR-02", "Baccar", "Ali"),
                    ensureTeacher("CAR-T03", "CIN-CAR-03", "Hamdi",  "Meriem"),
                    ensureTeacher("CAR-T04", "CIN-CAR-04", "Zouari", "Hatem"),
                    ensureTeacher("CAR-T05", "CIN-CAR-05", "Feki",   "Imen")
            );
            for (int i = 1; i <= 4; i++) {
                ensureRoom("C-S0" + i, "NORMALE", 30);
            }
            // Deux laboratoires, dimensionnes pour un demi-groupe. Les seances de
            // sciences en demi-groupe sont marquees is_split : les groupes A et B
            // ont lieu au MEME creneau (contrainte dure) et exigent tous deux un
            // labo. Avec un seul laboratoire, la paire est structurellement
            // improuvable — le solveur doit alors soit desynchroniser les groupes,
            // soit renvoyer un groupe en salle normale, et le planning du college
            // sort INFEASIBLE sans qu'aucune contrainte configurable soit en cause.
            ensureRoom("C-LAB1", "LABSCIENCE", 16);
            ensureRoom("C-LAB2", "LABSCIENCE", 16);

            Map<String, SubjectLevel> subjectLevels = ensureSubjectLevels(subjects, levels);
            Map<String, Teacher>      teacherMap    = ensureAssignments(year, classes, subjectLevels, teachers);
            ensureAdditionalSSTsAndTAs(year, classes, subjectLevels, teacherMap);
            ensurePatterns(subjectLevels);
            if (workingDayRepository.findByTenantIdOrderByDayOfWeekAsc(TENANT_CARTHAGE).isEmpty()) {
                schoolConfigurationService.configure(carthageConfig());
            }
            // Sans profil de contraintes, le college n'a aucun jeu de regles actif
            // pour son annee scolaire : la generation part alors sans les
            // contraintes configurables. Ibn Khaldoun en avait un, pas Carthage.
            ensureConstraintProfile(year);
        } finally {
            endTenant();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Organisation-module helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Tenant ensureTenant(String code, String name, TenantPlan plan) {
        return tenantRepository.findByCodeIgnoreCase(code)
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .code(code)
                        .name(name)
                        .etablismentType(EtablissementType.COLLEGE)
                        .address("Tunisie")
                        .phone("+216 71 000 000")
                        .active(true)
                        .status(TenantStatus.ACTIVE)
                        .plan(plan)
                        .build()));
    }

    private SchoolYear ensureYear(String name) {
        String tenantId = TenantContext.getRequiredTenantId();
        return schoolYearRepository.findByTenantIdAndNom(tenantId, name)
                .orElseGet(() -> schoolYearRepository.save(SchoolYear.builder()
                        .nom(name)
                        .dateDebut(LocalDate.of(2024, 9, 15))
                        .dateFin(LocalDate.of(2025, 6, 30))
                        .estActive(true)
                        .estCourante(true)
                        .build()));
    }

    private Level ensureLevel(String code, String name) {
        String tenantId = TenantContext.getRequiredTenantId();
        return levelRepository.findByTenantIdAndCode(tenantId, code)
                .orElseGet(() -> levelRepository.save(Level.builder()
                        .code(code)
                        .nom(name)
                        .description(name)
                        .estActif(true)
                        .build()));
    }

    /** Couleur hex par code matiere — reutilisee pour un affichage de planning colore. */
    private static final Map<String, String> SUBJECT_COLORS = Map.ofEntries(
            Map.entry("AR",    "#f97316"),
            Map.entry("FR",    "#3b82f6"),
            Map.entry("EN",    "#8b5cf6"),
            Map.entry("MATH",  "#ef4444"),
            Map.entry("SCI",   "#22c55e"),
            Map.entry("PHY",   "#06b6d4"),
            Map.entry("TECH",  "#64748b"),
            Map.entry("ISL",   "#10b981"),
            Map.entry("CIV",   "#f59e0b"),
            Map.entry("SPORT", "#ec4899")
    );

    private Map<String, Subject> ensureSubjects(List<SubjectSpec> specs) {
        Map<String, Subject> subjects = new HashMap<>();
        String tenantId = TenantContext.getRequiredTenantId();
        for (SubjectSpec spec : specs) {
            Subject subject = subjectRepository.findByTenantIdAndCodeMatiere(tenantId, spec.code())
                    .orElseGet(() -> subjectRepository.save(Subject.builder()
                            .codeMatiere(spec.code())
                            .libMatiere(spec.label())
                            .necessiteLab(spec.lab())
                            .necessiteSport(spec.sport())
                            .estPrincipale(true)
                            .estEnseignee(true)
                            .couleur(SUBJECT_COLORS.get(spec.code()))
                            .build()));
            if (subject.getCouleur() == null && SUBJECT_COLORS.containsKey(spec.code())) {
                subject.setCouleur(SUBJECT_COLORS.get(spec.code()));
                subject = subjectRepository.save(subject);
            }
            subjects.put(spec.code(), subject);
        }
        return subjects;
    }

    private ClassGroup ensureClass(String code, Level level, SchoolYear year) {
        String tenantId = TenantContext.getRequiredTenantId();
        return classGroupRepository.findByTenantIdAndCode(tenantId, code)
                .orElseGet(() -> classGroupRepository.save(ClassGroup.builder()
                        .code(code)
                        .codeSpecialite(Specialite.TCOM)
                        .nbEleve(28)
                        .estActif(true)
                        .level(level)
                        .schoolYear(year)
                        .build()));
    }

    private Teacher ensureTeacher(String code, String cin, String lastName, String firstName) {
        String tenantId = TenantContext.getRequiredTenantId();
        return teacherRepository.findByTenantIdAndCodeEnseignant(tenantId, code)
                .orElseGet(() -> teacherRepository.save(Teacher.builder()
                        .codeEnseignant(code)
                        .numIdentite(cin)
                        .nom(lastName)
                        .prenom(firstName)
                        .email(code.toLowerCase() + "@demo.smartschool.test")
                        .telephone("+216 71 111 111")
                        .maxHeuresSemaine(24)
                        .maxHeuresJour(6)
                        .minHeuresJour(0)
                        .estEnPoste(true)
                        .build()));
    }

    private Room ensureRoom(String code, String type, int capacity) {
        String tenantId = TenantContext.getRequiredTenantId();
        RoomType roomType = RoomType.valueOf(type.toUpperCase().trim());
        return roomRepository.findByTenantIdAndCodeSalle(tenantId, code)
                .orElseGet(() -> roomRepository.save(Room.builder()
                        .codeSalle(code)
                        .typeSalle(roomType)
                        .capacite(capacity)
                        .codeBloc("A")
                        .numEtage("0")
                        .build()));
    }

    private Map<String, SubjectLevel> ensureSubjectLevels(Map<String, Subject> subjects,
                                                           Map<String, Level>   levels) {
        Map<String, SubjectLevel> result = new HashMap<>();
        for (Level level : levels.values()) {
            for (Subject subject : subjects.values()) {
                SubjectLevel sl = subjectLevelRepository
                        .findBySubject_IdMatiereAndLevel_IdNiveau(subject.getIdMatiere(), level.getIdNiveau())
                        .orElseGet(() -> subjectLevelRepository.save(SubjectLevel.builder()
                                .subject(subject)
                                .level(level)
                                .heuresSemaine(defaultHours(subject.getCodeMatiere(), level.getCode()))
                                .estObligatoire(true)
                                .coefficient(defaultCoefficient(subject.getCodeMatiere()))
                                .maxHeuresConsecutives(2)
                                .build()));
                ensurePrimarySST(sl);
                result.put(level.getCode() + ":" + subject.getCodeMatiere(), sl);
            }
        }
        return result;
    }

    /** Creates / retrieves the primary SST (one per SubjectLevel) used by the main TA. */
    private SubjectSessionType ensurePrimarySST(SubjectLevel sl) {
        boolean needsLab   = Boolean.TRUE.equals(sl.getSubject().getNecessiteLab());
        boolean needsSport = Boolean.TRUE.equals(sl.getSubject().getNecessiteSport());
        SessionType primaryType = needsSport ? SessionType.SPORT
                                : needsLab   ? SessionType.TP
                                             : SessionType.COURSE;
        // Recherche par (subjectLevel, type) et non "première ligne trouvée" :
        // au re-run, les SST secondaires (TD, TP...) existent déjà et pouvaient
        // être renvoyés comme primaire, rendant le seed non déterministe.
        return ensureSessionTypeOf(sl, primaryType, 1.0, false);
    }

    /**
     * Creates / retrieves an SST for a specific (SubjectLevel, SessionType) pair.
     * Idempotent — looks up by type before saving.
     */
    private SubjectSessionType ensureSessionTypeOf(SubjectLevel sl, SessionType type,
                                                    double duration, boolean requiresSplit) {
        return subjectSessionTypeRepository
                .findBySubjectLevel_IdNiveauMatiere(sl.getIdNiveauMatiere())
                .stream()
                .filter(sst -> sst.getType() == type)
                .findFirst()
                .orElseGet(() -> subjectSessionTypeRepository.save(SubjectSessionType.builder()
                        .subjectLevel(sl)
                        .type(type)
                        .duration(duration)
                        .requiresSplit(requiresSplit)
                        .estActif(true)
                        .build()));
    }

    /**
     * Fixed teacher assignments for Ibn Khaldoun (admin pre-assigned).
     * Format: {subjectCode, teacherCode, class1, class2, ...}
     * The solver does NOT change teachers — it chooses only timeSlot + room.
     */
    private static final List<String[]> IBN_TEACHER_SPECS = List.of(
            new String[]{"AR",   "IBN-T01","7A1","7A2","7A3","8A1","8A2","9A1","9A2"},
            new String[]{"AR",   "IBN-T02","7A4","7A5","8A3","8A4","9A3","9A4"},
            new String[]{"FR",   "IBN-T03","7A1","7A2","7A3","8A1","8A2","9A1","9A2"},
            new String[]{"FR",   "IBN-T04","7A4","7A5","8A3","8A4","9A3","9A4"},
            new String[]{"MATH", "IBN-T05","7A1","7A2","8A1","8A2","9A1"},
            new String[]{"MATH", "IBN-T06","7A3","7A4","8A3","9A2","9A3"},
            new String[]{"MATH", "IBN-T07","7A5","8A4","9A4"},
            new String[]{"SCI",  "IBN-T08","7A1","7A2","7A3","8A1","8A2"},
            new String[]{"SCI",  "IBN-T09","7A4","7A5","8A3","8A4"},
            new String[]{"SCI",  "IBN-T10","9A1","9A2","9A3","9A4"},
            new String[]{"PHY",  "IBN-T19","7A1","7A2","8A1","8A2","9A1"},
            new String[]{"PHY",  "IBN-T20","7A3","7A4","8A3","9A2","9A3"},
            new String[]{"PHY",  "IBN-T21","7A5","8A4","9A4"},
            new String[]{"TECH", "IBN-T11","7A1","7A2","7A3","8A1","8A2"},
            new String[]{"TECH", "IBN-T12","7A4","7A5","8A3","8A4","9A1","9A2","9A3","9A4"},
            new String[]{"EN",   "IBN-T13","7A1","7A2","7A3","8A1","8A2","9A1","9A2"},
            new String[]{"EN",   "IBN-T14","7A4","7A5","8A3","8A4","9A3","9A4"},
            new String[]{"ISL",  "IBN-T15","7A1","7A2","7A3","7A4","7A5","8A1","8A2"},
            new String[]{"ISL",  "IBN-T16","8A3","8A4","9A1","9A2","9A3","9A4"},
            new String[]{"CIV",  "IBN-T22","7A1","7A2","7A3","8A1","8A2","9A1","9A2"},
            new String[]{"CIV",  "IBN-T23","7A4","7A5","8A3","8A4","9A3","9A4"},
            new String[]{"SPORT","IBN-T17","7A1","7A2","7A3","8A1","8A2","8A3","9A1","9A2"},
            new String[]{"SPORT","IBN-T18","7A4","7A5","8A4","9A3","9A4"}
    );

    private Map<String, Teacher> ensureIbnKhaldounAssignments(
            SchoolYear year,
            Map<String, ClassGroup> classes,
            Map<String, SubjectLevel> subjectLevels,
            Map<String, Teacher> teachersByCode) {

        Map<String, Teacher> primaryTeacherMap = new HashMap<>();

        for (String[] spec : IBN_TEACHER_SPECS) {
            String subjectCode = spec[0];
            String teacherCode = spec[1];
            Teacher teacher    = teachersByCode.get(teacherCode);
            if (teacher == null) continue;

            for (int i = 2; i < spec.length; i++) {
                String classCode = spec[i];
                ClassGroup cg    = classes.get(classCode);
                if (cg == null) continue;

                SubjectLevel sl = subjectLevels.get(cg.getLevel().getCode() + ":" + subjectCode);
                if (sl == null) continue;

                SubjectSessionType primarySst = ensurePrimarySST(sl);

                boolean exists = teachingAssignmentRepository
                        .existsBySchoolYear_IdAnneeAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
                                year.getIdAnnee(),
                                cg.getIdClasse(),
                                sl.getIdNiveauMatiere(),
                                primarySst.getIdSubjectSessionType());
                if (!exists) {
                    teachingAssignmentRepository.save(TeachingAssignment.builder()
                            .schoolYear(year)
                            .teacher(teacher)
                            .classGroup(cg)
                            .subjectLevel(sl)
                            .subjectSessionType(primarySst)
                            .priority(1)
                            .isActive(true)
                            .build());
                }
                primaryTeacherMap.put(classCode + ":" + subjectCode, teacher);
            }
        }
        return primaryTeacherMap;
    }

    /**
     * Creates one TA per (classGroup, subjectLevel) using the primary SST.
     * Returns a map (classCode:subjectCode → teacher) so callers can reuse the same
     * teacher when adding secondary TAs for the same subject.
     */
    private Map<String, Teacher> ensureAssignments(
            SchoolYear year,
            Map<String, ClassGroup> classes,
            Map<String, SubjectLevel> subjectLevels,
            List<Teacher> teachers) {

        Map<String, Teacher> teacherMap = new HashMap<>();
        int teacherIndex = 0;

        for (ClassGroup classGroup : classes.values()) {
            for (String subjectCode : subjectCodesForTenant()) {
                SubjectLevel sl = subjectLevels.get(classGroup.getLevel().getCode() + ":" + subjectCode);
                if (sl == null) continue;

                SubjectSessionType primarySst = ensurePrimarySST(sl);
                Teacher teacher = teachers.get(teacherIndex % teachers.size());
                teacherIndex++;

                // Check by (year, class, subjectLevel, sst) — teacher NOT included,
                // so re-running the demo never creates a second assignment for the same slot.
                boolean exists = teachingAssignmentRepository
                        .existsBySchoolYear_IdAnneeAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
                                year.getIdAnnee(),
                                classGroup.getIdClasse(),
                                sl.getIdNiveauMatiere(),
                                primarySst.getIdSubjectSessionType());
                if (!exists) {
                    teachingAssignmentRepository.save(TeachingAssignment.builder()
                            .schoolYear(year)
                            .teacher(teacher)
                            .classGroup(classGroup)
                            .subjectLevel(sl)
                            .subjectSessionType(primarySst)
                            .priority(1)
                            .isActive(true)
                            .build());
                }
                teacherMap.put(classGroup.getCode() + ":" + subjectCode, teacher);
            }
        }
        return teacherMap;
    }

    /**
     * Adds secondary SSTs (e.g. TD for MATH, COURSE for SCI) and their TAs.
     * Uses the same teacher as the primary TA for each subject/class pair.
     */
    private void ensureAdditionalSSTsAndTAs(
            SchoolYear year,
            Map<String, ClassGroup> classes,
            Map<String, SubjectLevel> subjectLevels,
            Map<String, Teacher> primaryTeacherMap) {

        for (ClassGroup cg : classes.values()) {
            String levelCode = cg.getLevel().getCode();
            for (var entry : secondarySSTSpecs().entrySet()) {
                String subjectCode = entry.getKey();
                SubjectLevel sl = subjectLevels.get(levelCode + ":" + subjectCode);
                if (sl == null) continue;

                Teacher teacher = primaryTeacherMap.get(cg.getCode() + ":" + subjectCode);
                if (teacher == null) continue;

                for (SSTSpec spec : entry.getValue()) {
                    SubjectSessionType sst = ensureSessionTypeOf(sl, spec.type(), spec.duration(), spec.requiresSplit());
                    boolean exists = teachingAssignmentRepository
                            .existsBySchoolYear_IdAnneeAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
                                    year.getIdAnnee(),
                                    cg.getIdClasse(),
                                    sl.getIdNiveauMatiere(),
                                    sst.getIdSubjectSessionType());
                    if (!exists) {
                        teachingAssignmentRepository.save(TeachingAssignment.builder()
                                .schoolYear(year)
                                .teacher(teacher)
                                .classGroup(cg)
                                .subjectLevel(sl)
                                .subjectSessionType(sst)
                                .priority(1)
                                .isActive(true)
                                .build());
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Pattern creation (Étape 2)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates one Pattern + its PatternDetails for every SubjectLevel in the map.
     * The map key format is "levelCode:subjectCode" (e.g. "7:MATH").
     */
    private void ensurePatterns(Map<String, SubjectLevel> subjectLevels) {
        for (Map.Entry<String, SubjectLevel> entry : subjectLevels.entrySet()) {
            String[] parts      = entry.getKey().split(":");
            String levelCode    = parts[0];
            String subjectCode  = parts[1];
            ensurePatternForSubjectLevel(entry.getValue(), subjectCode, levelCode);
        }
    }

    private void ensurePatternForSubjectLevel(SubjectLevel sl,
                                               String subjectCode,
                                               String levelCode) {
        String patternName = "Officiel " + subjectCode + " " + levelCode + "eme";

        if (patternRepository.existsByNameAndSubjectLevelAndYear(
                patternName, sl.getIdNiveauMatiere(), null, null)) {
            return;
        }

        List<PDSpec> specs = patternDetailsFor(subjectCode, levelCode);
        if (specs.isEmpty()) return;

        double totalHours   = specs.stream().mapToDouble(PDSpec::duration).sum();
        int    sessionCount = specs.size();
        String repartition  = specs.stream()
                .map(s -> String.valueOf((int) s.duration()))
                .collect(Collectors.joining("+"));

        Pattern pattern = patternRepository.save(Pattern.builder()
                .name(patternName)
                .subjectLevel(sl)
                .totalHours(totalHours)
                .sessionCount(sessionCount)
                .repartition(repartition)
                .patternType(PatternType.WEEKLY_IDENTICAL)
                .build());

        for (PDSpec spec : specs) {
            patternDetailRepository.save(PatternDetail.builder()
                    .pattern(pattern)
                    .sessionOrder(spec.order())
                    .type(spec.type())
                    .duration(spec.duration())
                    .isSplit(spec.isSplit())
                    .requiredRoomType(spec.requiredRoomType())
                    .weekParity(WeekParity.ALL)
                    .build());
        }
    }

    /**
     * Returns the official Tunisian curriculum breakdown for a given subject + level.
     *
     * Each PDSpec maps to one PatternDetail row.
     * isSplit=true generates group A + group B lessons in the solver.
     * requiredRoomType=LABPHYSIQUE/LABSCIENCE means lab; SALLESPORT means sports hall.
     */
    /**
     * Official Tunisian curriculum patterns per subject + level.
     * duration=2.0 → 2h session (2 slots of 30min); duration=1.0 → 1h (1 slot).
     * isSplit=true → generates demi-group pair (groupIndex 1 + 2, same timeSlot).
     */
    private List<PDSpec> patternDetailsFor(String subjectCode, String levelCode) {
        return switch (subjectCode) {

            // ── AR : Arabe ────────────────────────────────────────────────────
            // 7ème: 2+1+1+1 = 5h | 8ème+9ème: 2+1+1 = 4h
            case "AR" -> "7".equals(levelCode)
                    ? List.of(
                            new PDSpec(1, SessionType.COURSE, 2.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(3, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(4, SessionType.TD,     1.0, false, RoomType.NORMALE))
                    : List.of(
                            new PDSpec(1, SessionType.COURSE, 2.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(3, SessionType.TD,     1.0, false, RoomType.NORMALE));

            // ── FR : Français ─────────────────────────────────────────────────
            // Tous niveaux: 2+1+1 = 4h
            case "FR" -> List.of(
                            new PDSpec(1, SessionType.COURSE, 2.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(3, SessionType.TD,     1.0, false, RoomType.NORMALE));

            // ── EN : Anglais ──────────────────────────────────────────────────
            // 7ème: 1+1+1 = 3h | 8ème+9ème: 1+1+1 = 3h
            case "EN" -> List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(3, SessionType.TD,     1.0, false, RoomType.NORMALE));

            // ── MATH : Mathématiques ──────────────────────────────────────────
            // 7ème+8ème: 2+1+1+1 = 5h | 9ème: 2+1+1 = 4h
            case "MATH" -> "9".equals(levelCode)
                    ? List.of(
                            new PDSpec(1, SessionType.COURSE, 2.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(3, SessionType.TD,     1.0, false, RoomType.NORMALE))
                    : List.of(
                            new PDSpec(1, SessionType.COURSE, 2.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(3, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(4, SessionType.TD,     1.0, false, RoomType.NORMALE));

            // ── SCI : Sciences naturelles ─────────────────────────────────────
            // 7ème: 1+TP+TP+TD+TD = 5h | 8ème: 1+TP+TP+TD = 4h | 9ème: 1+TP+TD = 4h
            case "SCI" -> "9".equals(levelCode)
                    ? List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TP,     2.0, true,  RoomType.LABSCIENCE),
                            new PDSpec(3, SessionType.TD,     1.0, false, RoomType.NORMALE))
                    : "7".equals(levelCode)
                    ? List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TP,     1.0, true,  RoomType.LABSCIENCE),
                            new PDSpec(3, SessionType.TP,     1.0, true,  RoomType.LABSCIENCE),
                            new PDSpec(4, SessionType.TD,     1.0, false, RoomType.NORMALE),
                            new PDSpec(5, SessionType.TD,     1.0, false, RoomType.NORMALE))
                    : List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TP,     1.0, true,  RoomType.LABSCIENCE),
                            new PDSpec(3, SessionType.TP,     1.0, true,  RoomType.LABSCIENCE),
                            new PDSpec(4, SessionType.TD,     1.0, false, RoomType.NORMALE));

            // ── PHY : Physique ────────────────────────────────────────────────
            // 7ème: 1+TP+TP = 3h | 8ème: 1+TP+TP = 3h | 9ème: 1+TP+TP+TD = 4h
            case "PHY" -> "9".equals(levelCode)
                    ? List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TP,     1.0, true,  RoomType.LABPHYSIQUE),
                            new PDSpec(3, SessionType.TP,     1.0, true,  RoomType.LABPHYSIQUE),
                            new PDSpec(4, SessionType.TD,     1.0, false, RoomType.NORMALE))
                    : List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TP,     1.0, true,  RoomType.LABPHYSIQUE),
                            new PDSpec(3, SessionType.TP,     1.0, true,  RoomType.LABPHYSIQUE));

            // ── TECH : Technologie ────────────────────────────────────────────
            // 7ème: 1+TP = 2h | 8ème+9ème: 1+TP = 2h
            case "TECH" -> List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TP,     1.0, true,  RoomType.LABINFORMATIQUE));

            // ── ISL : Islamique ───────────────────────────────────────────────
            // Tous niveaux: 2+1 = 3h
            case "ISL" -> List.of(
                            new PDSpec(1, SessionType.COURSE, 2.0, false, RoomType.NORMALE),
                            new PDSpec(2, SessionType.TD,     1.0, false, RoomType.NORMALE));

            // ── CIV : Civique ─────────────────────────────────────────────────
            // Tous niveaux: 1 = 1h
            case "CIV" -> List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE));

            // ── SPORT : EPS ───────────────────────────────────────────────────
            // Tous niveaux: 2h demi-groupe (groupe A + groupe B en parallèle)
            case "SPORT" -> List.of(
                            new PDSpec(1, SessionType.SPORT,  2.0, true,  RoomType.SALLESPORT));

            default -> List.of(
                            new PDSpec(1, SessionType.COURSE, 1.0, false, RoomType.NORMALE));
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Constraint profile (Étape 6)
    // ══════════════════════════════════════════════════════════════════════════

    private void ensureConstraintProfile(SchoolYear year) {
        String tenantId = TenantContext.getRequiredTenantId();
        Long   yearId   = year.getIdAnnee();
        String name     = "Profil Standard Demo";

        if (constraintProfileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(
                tenantId, name, yearId)) {
            return;
        }

        // Un seul profil actif par annee scolaire (index unique en base) : le jeu de
        // demo ne prend la main que si l'annee n'a pas deja son profil actif.
        boolean active = constraintProfileRepository
                .findByTenantIdAndAcademicYearIdAndActiveTrue(tenantId, yearId)
                .isEmpty();

        ConstraintProfile profile = constraintProfileRepository.save(ConstraintProfile.builder()
                .name(name)
                .academicYearId(yearId)
                .active(active)
                .build());

        addConstraintSetting(profile, "MAX_TEACHER_HOURS_PER_DAY",         ImportanceLevel.CRITICAL, 10, "{\"maxHours\":6}");
        addConstraintSetting(profile, "MAX_STUDENT_HOURS_PER_DAY",         ImportanceLevel.CRITICAL, 10, "{\"maxHours\":6}");
        addConstraintSetting(profile, "MAX_TEACHER_HOURS_FRIDAY_SATURDAY",  ImportanceLevel.CRITICAL, 10, "{\"maxHours\":5}");
        addConstraintSetting(profile, "SPECIAL_ROOM_REQUIRED",              ImportanceLevel.CRITICAL, 10, "{\"enabled\":true}");
        addConstraintSetting(profile, "BALANCED_MORNING_AFTERNOON",         ImportanceLevel.MEDIUM,    5, "{\"enabled\":true}");
        addConstraintSetting(profile, "TEACHER_WEEKLY_REST_DAY",            ImportanceLevel.LOW,        1, "{\"enabled\":true}");
    }

    private void addConstraintSetting(ConstraintProfile profile,
                                       String code,
                                       ImportanceLevel importance,
                                       int weight,
                                       String parametersJson) {
        constraintDefinitionRepository.findByCode(code).ifPresent(def ->
                constraintSettingRepository.save(ConstraintSetting.builder()
                        .profile(profile)
                        .definition(def)
                        .enabled(true)
                        .importance(importance)
                        .weight(weight)
                        .parametersJson(parametersJson)
                        .build()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // School schedule configuration
    // ══════════════════════════════════════════════════════════════════════════

    private SchoolConfigRequestDTO ibnKhaldounConfig() {
        return new SchoolConfigRequestDTO(List.of(
                workingDay(DayOfWeek.MONDAY,    "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.TUESDAY,   "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.WEDNESDAY, "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.THURSDAY,  "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.FRIDAY,    "08:00", "12:00", "14:00", "17:00"),
                workingDay(DayOfWeek.SATURDAY,  "08:00", "12:00", null,    null)
        ), 30);
    }

    private SchoolConfigRequestDTO carthageConfig() {
        return new SchoolConfigRequestDTO(List.of(
                workingDay(DayOfWeek.MONDAY,    "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.TUESDAY,   "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.WEDNESDAY, "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.THURSDAY,  "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.FRIDAY,    "08:00", "12:00", "14:00", "16:00")
        ), 30);
    }

    private WorkingDayRequestDTO workingDay(DayOfWeek day,
                                             String morningStart, String morningEnd,
                                             String afternoonStart, String afternoonEnd) {
        return new WorkingDayRequestDTO(
                day,
                true,
                LocalTime.parse(morningStart),
                LocalTime.parse(morningEnd),
                afternoonStart != null ? LocalTime.parse(afternoonStart) : null,
                afternoonEnd   != null ? LocalTime.parse(afternoonEnd)   : null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Static lookup tables / default values
    // ══════════════════════════════════════════════════════════════════════════

    private List<String> subjectCodesForTenant() {
        return TENANT_CARTHAGE.equals(TenantContext.getRequiredTenantId())
                ? List.of("MATH", "FR", "AR", "EN", "SCI")
                : List.of("AR", "FR", "EN", "MATH", "SCI", "PHY", "TECH", "ISL", "CIV", "SPORT");
    }

    /**
     * Secondary SSTs to add per subject (beyond the primary SST created in ensurePrimarySST).
     *
     * MATH/FR/AR/EN/ISL : primary=COURSE → add TD
     * SCI/PHY/TECH      : primary=TP     → add COURSE
     * SPORT/CIV         : no secondary needed
     */
    private Map<String, List<SSTSpec>> secondarySSTSpecs() {
        return Map.of(
                "MATH", List.of(new SSTSpec(SessionType.TD,     1.0, false)),
                "FR",   List.of(new SSTSpec(SessionType.TD,     1.0, false)),
                "AR",   List.of(new SSTSpec(SessionType.TD,     1.0, false)),
                "EN",   List.of(new SSTSpec(SessionType.TD,     1.0, false)),
                "ISL",  List.of(new SSTSpec(SessionType.TD,     1.0, false)),
                "SCI",  List.of(new SSTSpec(SessionType.COURSE, 1.0, false)),
                "PHY",  List.of(new SSTSpec(SessionType.COURSE, 1.0, false)),
                "TECH", List.of(new SSTSpec(SessionType.COURSE, 1.0, false))
        );
    }

    private double defaultHours(String subjectCode, String levelCode) {
        return switch (subjectCode) {
            case "AR"   -> "7".equals(levelCode) ? 5.0 : 4.0;
            case "FR"   -> 4.0;
            case "EN"   -> 3.0;
            case "MATH" -> "9".equals(levelCode) ? 4.0 : 5.0;
            case "SCI"  -> "7".equals(levelCode) ? 5.0 : 4.0;
            case "PHY"  -> "9".equals(levelCode) ? 4.0 : 3.0;
            case "TECH" -> "7".equals(levelCode) ? 3.0 : 2.0;
            case "ISL"  -> 3.0;
            case "CIV"  -> 1.0;
            case "SPORT"-> 2.0;
            default     -> 3.0;
        };
    }

    private double defaultCoefficient(String subjectCode) {
        return switch (subjectCode) {
            case "MATH", "FR", "AR" -> 2.0;
            default                 -> 1.0;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Records / value objects
    // ══════════════════════════════════════════════════════════════════════════

    private SubjectSpec subjectSpec(String code, String label, boolean lab, boolean sport) {
        return new SubjectSpec(code, label, lab, sport);
    }

    private record SubjectSpec(String code, String label, boolean lab, boolean sport) {}

    /** Spec for a PatternDetail row. */
    private record PDSpec(int order, SessionType type, double duration,
                           boolean isSplit, RoomType requiredRoomType) {}

    /** Spec for a secondary SubjectSessionType to create per SubjectLevel. */
    private record SSTSpec(SessionType type, double duration, boolean requiresSplit) {}
}
