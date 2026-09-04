package tn.wtm.school.org.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.dto.request.SchoolConfigRequestDTO;
import tn.wtm.school.org.dto.request.WorkingDayRequestDTO;
import tn.wtm.school.org.dto.response.SchoolConfigResponseDTO;
import tn.wtm.school.org.dto.response.TimeSlotResponseDTO;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.org.mapper.TimeSlotMapper;
import tn.wtm.school.org.service.impl.SchoolConfigurationService;
import tn.wtm.school.org.service.impl.TimeSlotGenerationServiceImpl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@EnabledIfSystemProperty(
        named = "testcontainers.enabled",
        matches = "true",
        disabledReason = "PostgreSQL/Testcontainers tests require Docker; run with -Dtestcontainers.enabled=true"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = OrganisationPostgresIntegrationTest.TestApplication.class)
class OrganisationPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    private final SchoolYearRepository schoolYearRepository;
    private final LevelRepository levelRepository;
    private final ClassGroupRepository classGroupRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectLevelRepository subjectLevelRepository;
    private final SubjectSessionTypeRepository subjectSessionTypeRepository;
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final WorkingDayRepository workingDayRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SchoolConfigurationService schoolConfigurationService;

    OrganisationPostgresIntegrationTest(
            SchoolYearRepository schoolYearRepository,
            LevelRepository levelRepository,
            ClassGroupRepository classGroupRepository,
            SubjectRepository subjectRepository,
            SubjectLevelRepository subjectLevelRepository,
            SubjectSessionTypeRepository subjectSessionTypeRepository,
            TeacherRepository teacherRepository,
            RoomRepository roomRepository,
            TeachingAssignmentRepository teachingAssignmentRepository,
            WorkingDayRepository workingDayRepository,
            TimeSlotRepository timeSlotRepository,
            SchoolConfigurationService schoolConfigurationService
    ) {
        this.schoolYearRepository = schoolYearRepository;
        this.levelRepository = levelRepository;
        this.classGroupRepository = classGroupRepository;
        this.subjectRepository = subjectRepository;
        this.subjectLevelRepository = subjectLevelRepository;
        this.subjectSessionTypeRepository = subjectSessionTypeRepository;
        this.teacherRepository = teacherRepository;
        this.roomRepository = roomRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.workingDayRepository = workingDayRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.schoolConfigurationService = schoolConfigurationService;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void insertsRelationsAndFiltersByTenantId() {
        DemoGraph tenantOne = seedGraph("tenant-1", "IBN", "7B1");
        DemoGraph tenantTwo = seedGraph("tenant-2", "CAR", "7A1");

        assertThat(levelRepository.findByTenantId("tenant-1")).extracting(Level::getCode).containsExactly("IBN-L7");
        assertThat(levelRepository.findByTenantId("tenant-2")).extracting(Level::getCode).containsExactly("CAR-L7");
        assertThat(classGroupRepository.findByTenantId("tenant-1")).extracting(ClassGroup::getCode).containsExactly("7B1");
        assertThat(classGroupRepository.findByTenantId("tenant-1")).extracting(ClassGroup::getCode).doesNotContain("7A1");
        assertThat(teacherRepository.findByTenantId("tenant-2")).extracting(Teacher::getCodeEnseignant).containsExactly("CAR-T1");
        assertThat(subjectRepository.findByTenantId("tenant-1")).extracting(Subject::getCodeMatiere).containsExactly("IBNMATH");

        TeachingAssignment assignment = teachingAssignmentRepository
                .findByTenantId("tenant-1")
                .getFirst();
        assertThat(assignment.getTeacher().getIdEnseignant()).isEqualTo(tenantOne.teacher().getIdEnseignant());
        assertThat(assignment.getClassGroup().getIdClasse()).isEqualTo(tenantOne.classGroup().getIdClasse());
        assertThat(assignment.getSubjectLevel().getIdNiveauMatiere()).isEqualTo(tenantOne.subjectLevel().getIdNiveauMatiere());

        assertThat(teachingAssignmentRepository.findByTenantId("tenant-2"))
                .extracting(TeachingAssignment::getIdTeachingAssignment)
                .containsExactly(tenantTwo.assignment().getIdTeachingAssignment());
    }

    @Test
    void enforcesUniqueLevelCodeWithinTenant() {
        TenantContext.setTenantId("tenant-1");
        levelRepository.saveAndFlush(level("L7", "7eme"));

        assertThatThrownBy(() -> levelRepository.saveAndFlush(level("L7", "Autre 7eme")))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void allowsSameLevelCodeAcrossDifferentTenants() {
        TenantContext.setTenantId("tenant-1");
        levelRepository.saveAndFlush(level("L7", "7eme"));

        TenantContext.setTenantId("tenant-2");
        Level second = levelRepository.saveAndFlush(level("L7", "7eme"));

        assertThat(second.getTenantId()).isEqualTo("tenant-2");
        assertThat(levelRepository.findByTenantId("tenant-1")).hasSize(1);
        assertThat(levelRepository.findByTenantId("tenant-2")).hasSize(1);
    }

    @Test
    void configuresWorkingDaysAndGeneratesExactThirtyMinuteSlotsForTenantOne() {
        TenantContext.setTenantId("tenant-1");

        SchoolConfigResponseDTO response = schoolConfigurationService.configure(tenantOneConfig());
        SchoolConfigResponseDTO rerun = schoolConfigurationService.configure(tenantOneConfig());

        assertThat(response.totalSlotsPerWeek()).isEqualTo(86);
        assertThat(rerun.totalSlotsPerWeek()).isEqualTo(86);
        assertThat(workingDayRepository.findByTenantIdOrderByDayOfWeekAsc("tenant-1")).hasSize(6);
        assertThat(timeSlotRepository.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc("tenant-1")).hasSize(86);

        assertThat(response.timeSlots()).allSatisfy(slot ->
                assertThat(Duration.between(slot.startTime(), slot.endTime()).toMinutes()).isEqualTo(30)
        );
        assertThat(response.timeSlots()).noneMatch(slot ->
                slot.dayOfWeek() == DayOfWeek.MONDAY
                        && !slot.startTime().isBefore(LocalTime.NOON)
                        && !slot.endTime().isAfter(LocalTime.of(14, 0))
        );
        assertThat(response.timeSlots()).noneMatch(slot -> slot.dayOfWeek() == DayOfWeek.SUNDAY);
        assertThat(response.timeSlots()).filteredOn(slot -> slot.dayOfWeek() == DayOfWeek.FRIDAY)
                .extracting(TimeSlotResponseDTO::endTime)
                .contains(LocalTime.of(17, 0))
                .doesNotContain(LocalTime.of(18, 0));
        assertThat(response.timeSlots()).filteredOn(slot -> slot.dayPeriod() == DayPeriod.MORNING).isNotEmpty();
        assertThat(response.timeSlots()).filteredOn(slot -> slot.dayPeriod() == DayPeriod.AFTERNOON).isNotEmpty();
    }

    @Test
    void isolatesGeneratedTimeSlotsBetweenTenantsAndDisablesNonWorkingDay() {
        TenantContext.setTenantId("tenant-1");
        schoolConfigurationService.configure(tenantOneConfig());

        TenantContext.setTenantId("tenant-2");
        schoolConfigurationService.configure(tenantTwoConfig());
        schoolConfigurationService.toggleDay(DayOfWeek.SATURDAY, false);

        assertThat(timeSlotRepository.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc("tenant-1")).hasSize(86);
        assertThat(timeSlotRepository.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc("tenant-2")).hasSize(60);
        assertThat(timeSlotRepository.findByTenantIdAndDayOfWeekOrderByOrderIndexAsc("tenant-2", DayOfWeek.SATURDAY))
                .isEmpty();
        assertThat(timeSlotRepository.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc("tenant-2"))
                .allSatisfy(slot -> assertThat(slot.getTenantId()).isEqualTo("tenant-2"));
    }

    private DemoGraph seedGraph(String tenantId, String prefix, String classCode) {
        TenantContext.setTenantId(tenantId);

        SchoolYear schoolYear = schoolYearRepository.saveAndFlush(SchoolYear.builder()
                .nom("2024-2025-" + prefix)
                .dateDebut(LocalDate.of(2024, 9, 15))
                .dateFin(LocalDate.of(2025, 6, 30))
                .estActive(true)
                .estCourante(true)
                .build());
        Level level = levelRepository.saveAndFlush(level(prefix + "-L7", "7eme"));
        Subject subject = subjectRepository.saveAndFlush(Subject.builder()
                .codeMatiere(prefix + "MATH")
                .libMatiere("Mathematiques")
                .estEnseignee(true)
                .estPrincipale(true)
                .build());
        SubjectLevel subjectLevel = subjectLevelRepository.saveAndFlush(SubjectLevel.builder()
                .subject(subject)
                .level(level)
                .heuresSemaine(5.0)
                .estObligatoire(true)
                .coefficient(2.0)
                .build());
        SubjectSessionType sessionType = subjectSessionTypeRepository.saveAndFlush(SubjectSessionType.builder()
                .subjectLevel(subjectLevel)
                .duration(1.0)
                .requiresSplit(false)
                .estActif(true)
                .build());
        ClassGroup classGroup = classGroupRepository.saveAndFlush(ClassGroup.builder()
                .code(classCode)
                .codeSpecialite(tn.wtm.school.org.enums.Specialite.TCOM)
                .nbEleve(28)
                .estActif(true)
                .schoolYear(schoolYear)
                .level(level)
                .build());
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .codeEnseignant(prefix + "-T1")
                .numIdentite(prefix + "-CIN1")
                .nom("Ben " + prefix)
                .prenom("Amel")
                .email(prefix.toLowerCase() + ".teacher@demo.test")
                .telephone("+216 71 111 111")
                .maxHeuresSemaine(20)
                .estEnPoste(true)
                .build());
        roomRepository.saveAndFlush(Room.builder()
                .codeSalle(prefix + "-S1")
                .typeSalle(tn.wtm.school.org.enums.RoomType.NORMALE)
                .capacite(30)
                .build());
        TeachingAssignment assignment = teachingAssignmentRepository.saveAndFlush(TeachingAssignment.builder()
                .schoolYear(schoolYear)
                .teacher(teacher)
                .classGroup(classGroup)
                .subjectLevel(subjectLevel)
                .subjectSessionType(sessionType)
                .priority(1)
                .isActive(true)
                .build());

        return new DemoGraph(schoolYear, level, subject, subjectLevel, sessionType, classGroup, teacher, assignment);
    }

    private Level level(String code, String name) {
        return Level.builder()
                .code(code)
                .nom(name)
                .description(name)
                .estActif(true)
                .build();
    }

    private SchoolConfigRequestDTO tenantOneConfig() {
        return new SchoolConfigRequestDTO(List.of(
                workingDay(DayOfWeek.MONDAY, "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.TUESDAY, "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.WEDNESDAY, "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.THURSDAY, "08:00", "12:00", "14:00", "18:00"),
                workingDay(DayOfWeek.FRIDAY, "08:00", "12:00", "14:00", "17:00"),
                workingDay(DayOfWeek.SATURDAY, "08:00", "12:00", null, null)
        ), 30);
    }

    private SchoolConfigRequestDTO tenantTwoConfig() {
        return new SchoolConfigRequestDTO(List.of(
                workingDay(DayOfWeek.MONDAY, "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.TUESDAY, "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.WEDNESDAY, "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.THURSDAY, "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.FRIDAY, "08:00", "12:00", "14:00", "16:00"),
                workingDay(DayOfWeek.SATURDAY, "08:00", "12:00", null, null)
        ), 30);
    }

    private WorkingDayRequestDTO workingDay(
            DayOfWeek day,
            String morningStart,
            String morningEnd,
            String afternoonStart,
            String afternoonEnd
    ) {
        return new WorkingDayRequestDTO(
                day,
                true,
                LocalTime.parse(morningStart),
                LocalTime.parse(morningEnd),
                afternoonStart != null ? LocalTime.parse(afternoonStart) : null,
                afternoonEnd != null ? LocalTime.parse(afternoonEnd) : null
        );
    }

    private record DemoGraph(
            SchoolYear schoolYear,
            Level level,
            Subject subject,
            SubjectLevel subjectLevel,
            SubjectSessionType sessionType,
            ClassGroup classGroup,
            Teacher teacher,
            TeachingAssignment assignment
    ) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    @EntityScan(basePackageClasses = SchoolYear.class)
    @EnableJpaRepositories(basePackageClasses = SchoolYearRepository.class)
    @Import({SchoolConfigurationService.class, TimeSlotGenerationServiceImpl.class})
    static class TestApplication {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("test");
        }

        @Bean
        TimeSlotMapper timeSlotMapper() {
            return new TimeSlotMapper() {
                @Override
                public TimeSlotResponseDTO toResponse(TimeSlot entity) {
                    return new TimeSlotResponseDTO(
                            entity.getIdTimeSlot(),
                            entity.getDayOfWeek(),
                            entity.getStartTime(),
                            entity.getEndTime(),
                            entity.getOrderIndex(),
                            entity.getDayPeriod()
                    );
                }

                @Override
                public List<TimeSlotResponseDTO> toResponseList(List<TimeSlot> list) {
                    return list == null ? null : list.stream().map(this::toResponse).toList();
                }
            };
        }
    }
}
