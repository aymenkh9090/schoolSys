package tn.wtm.school.org.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.LevelRequest;
import tn.wtm.school.org.dto.request.RoomRequest;
import tn.wtm.school.org.dto.request.SchoolYearRequest;
import tn.wtm.school.org.dto.request.SubjectRequest;
import tn.wtm.school.org.dto.request.TeacherRequest;
import tn.wtm.school.org.dto.request.TeachingAssignmentRequest;
import tn.wtm.school.org.dto.response.LevelResponse;
import tn.wtm.school.org.dto.response.RoomResponse;
import tn.wtm.school.org.dto.response.SchoolYearResponse;
import tn.wtm.school.org.dto.response.SubjectResponse;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.dto.response.TeachingAssignmentResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.mapper.ClassGroupMapper;
import tn.wtm.school.org.mapper.LevelMapper;
import tn.wtm.school.org.mapper.RoomMapper;
import tn.wtm.school.org.mapper.SchoolYearMapper;
import tn.wtm.school.org.mapper.SubjectLevelMapper;
import tn.wtm.school.org.mapper.SubjectMapper;
import tn.wtm.school.org.mapper.SubjectSessionTypeMapper;
import tn.wtm.school.org.mapper.TeacherMapper;
import tn.wtm.school.org.mapper.TeachingAssignmentMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.org.service.impl.AcademicYearServiceImpl;
import tn.wtm.school.org.service.impl.LevelServiceImpl;
import tn.wtm.school.org.service.impl.RoomServiceImpl;
import tn.wtm.school.org.service.impl.SubjectServiceImpl;
import tn.wtm.school.org.service.impl.TeacherServiceImpl;
import tn.wtm.school.org.service.impl.TeachingAssignmentServiceImpl;
import tn.wtm.school.org.service.impl.TimeSlotGenerationServiceImpl;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganisationServicesUnitTest {

    @Mock LevelRepository levelRepository;
    @Mock ClassGroupRepository classGroupRepository;
    @Mock SchoolYearRepository schoolYearRepository;
    @Mock SubjectLevelRepository subjectLevelRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SubjectSessionTypeRepository subjectSessionTypeRepository;
    @Mock PatternRepository patternRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock SchoolUserRepository schoolUserRepository;
    @Mock JwtClaimsExtractor jwtClaimsExtractor;
    @Mock TeachingAssignmentRepository teachingAssignmentRepository;
    @Mock RoomRepository roomRepository;
    @Mock TimeSlotRepository timeSlotRepository;
    @Mock LevelMapper levelMapper;
    @Mock ClassGroupMapper classGroupMapper;
    @Mock SchoolYearMapper schoolYearMapper;
    @Mock SubjectMapper subjectMapper;
    @Mock SubjectLevelMapper subjectLevelMapper;
    @Mock SubjectSessionTypeMapper subjectSessionTypeMapper;
    @Mock TeacherMapper teacherMapper;
    @Mock TeachingAssignmentMapper teachingAssignmentMapper;
    @Mock RoomMapper roomMapper;
    @Mock ObjectsValidator<LevelRequest> levelValidator;
    @Mock ObjectsValidator<tn.wtm.school.org.dto.request.ClassGroupRequest> classGroupValidator;
    @Mock ObjectsValidator<SubjectRequest> subjectValidator;
    @Mock ObjectsValidator<tn.wtm.school.org.dto.request.SubjectLevelRequest> subjectLevelValidator;
    @Mock ObjectsValidator<tn.wtm.school.org.dto.request.SubjectSessionTypeRequest> subjectSessionTypeValidator;
    @Mock ObjectsValidator<TeacherRequest> teacherValidator;
    @Mock ObjectsValidator<TeachingAssignmentRequest> teachingAssignmentValidator;
    @Mock ObjectsValidator<SchoolYearRequest> schoolYearValidator;
    @Mock ObjectsValidator<RoomRequest> roomValidator;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void roomServiceRequiresTenantAndRejectsDuplicateCode() {
        RoomServiceImpl service = new RoomServiceImpl(roomRepository, roomMapper, roomValidator);
        RoomRequest request = roomRequest("S1");

        assertThatThrownBy(() -> service.createRoom(request))
                .isInstanceOf(TenantSecurityException.class);

        TenantContext.setTenantId("tenant-1");
        when(roomRepository.existsByTenantIdAndCodeSalle("tenant-1", "S1")).thenReturn(true);

        assertThatThrownBy(() -> service.createRoom(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void roomServiceCreatesUpdatesFindsAndDeletesWithinTenant() {
        TenantContext.setTenantId("tenant-1");
        RoomServiceImpl service = new RoomServiceImpl(roomRepository, roomMapper, roomValidator);
        RoomRequest request = roomRequest("S1");
        Room room = Room.builder().idSalle(1L).codeSalle("S1").typeSalle(tn.wtm.school.org.enums.RoomType.NORMALE).build();
        RoomResponse response = RoomResponse.builder().idSalle(1L).codeSalle("S1").build();

        when(roomMapper.toEntity(request)).thenReturn(room);
        when(roomRepository.save(room)).thenReturn(room);
        when(roomMapper.toResponse(room)).thenReturn(response);
        when(roomRepository.findByTenantIdAndIdSalle("tenant-1", 1L)).thenReturn(Optional.of(room));

        assertThat(service.createRoom(request).getCodeSalle()).isEqualTo("S1");
        assertThat(service.getRoomById(1L).getIdSalle()).isEqualTo(1L);
        assertThat(service.updateRoom(1L, request).getCodeSalle()).isEqualTo("S1");

        service.deleteRoom(1L);
        verify(roomRepository).delete(room);
    }

    @Test
    void academicYearServiceCreatesListsAndTogglesByTenant() {
        TenantContext.setTenantId("tenant-1");
        AcademicYearServiceImpl service = new AcademicYearServiceImpl(
                schoolYearRepository,
                classGroupRepository,
                teachingAssignmentRepository,
                schoolYearMapper,
                schoolYearValidator
        );
        SchoolYearRequest request = SchoolYearRequest.builder()
                .nom("2024-2025")
                .dateDebut(LocalDate.of(2024, 9, 15))
                .dateFin(LocalDate.of(2025, 6, 30))
                .build();
        SchoolYear year = SchoolYear.builder().idAnnee(1L).nom("2024-2025").estActive(true).build();
        SchoolYearResponse response = SchoolYearResponse.builder().idAnnee(1L).nom("2024-2025").estActive(true).build();

        when(schoolYearMapper.toEntity(request)).thenReturn(year);
        when(schoolYearRepository.save(year)).thenReturn(year);
        when(schoolYearMapper.toResponse(year)).thenReturn(response);
        when(schoolYearRepository.findByTenantId("tenant-1")).thenReturn(List.of(year));
        when(schoolYearRepository.findByTenantIdAndIdAnnee("tenant-1", 1L)).thenReturn(Optional.of(year));

        assertThat(service.createAcademicYear(request).getNom()).isEqualTo("2024-2025");
        assertThat(service.getAcademicYearsByTenant()).hasSize(1);
        assertThat(service.toggleAcademicYearStatus(1L, false).getEstActive()).isTrue();
        assertThat(year.getEstActive()).isFalse();
    }

    @Test
    void levelServiceCreatesRejectsDuplicatesAndSoftToggles() {
        TenantContext.setTenantId("tenant-1");
        LevelServiceImpl service = new LevelServiceImpl(
                levelRepository,
                classGroupRepository,
                schoolYearRepository,
                subjectLevelRepository,
                levelMapper,
                classGroupMapper,
                levelValidator,
                classGroupValidator
        );
        LevelRequest request = LevelRequest.builder().code("L7").nom("7eme").description("7eme").build();
        Level level = Level.builder().idNiveau(1L).code("L7").estActif(true).build();
        LevelResponse response = LevelResponse.builder().idNiveau(1L).code("L7").estActif(true).build();

        when(levelMapper.toEntity(request)).thenReturn(level);
        when(levelRepository.save(level)).thenReturn(level);
        when(levelMapper.toResponse(level)).thenReturn(response);
        when(levelRepository.findByTenantIdAndIdNiveau("tenant-1", 1L)).thenReturn(Optional.of(level));

        assertThat(service.createLevel(request).getCode()).isEqualTo("L7");
        service.toggleLevelStatus(1L, false);
        assertThat(level.getEstActif()).isFalse();

        when(levelRepository.existsByTenantIdAndCode("tenant-1", "L7")).thenReturn(true);
        assertThatThrownBy(() -> service.createLevel(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void subjectServiceCreatesSubjectAndRejectsDuplicateSessionType() {
        TenantContext.setTenantId("tenant-1");
        SubjectServiceImpl service = subjectService();
        SubjectRequest request = SubjectRequest.builder().codeMatiere("MATH").libMatiere("Mathematiques").build();
        Subject subject = Subject.builder().idMatiere(1L).codeMatiere("MATH").build();
        SubjectResponse response = SubjectResponse.builder().idMatiere(1L).codeMatiere("MATH").build();

        when(subjectMapper.toEntity(request)).thenReturn(subject);
        when(subjectRepository.save(subject)).thenReturn(subject);
        when(subjectMapper.toResponseLight(subject)).thenReturn(response);

        assertThat(service.createSubject(request).getCodeMatiere()).isEqualTo("MATH");

        when(subjectRepository.existsByTenantIdAndCodeMatiere("tenant-1", "MATH")).thenReturn(true);
        assertThatThrownBy(() -> service.createSubject(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void teacherServiceCreatesDeactivatesAndValidatesRequiredId() {
        TenantContext.setTenantId("tenant-1");
        TeacherServiceImpl service = new TeacherServiceImpl(
                teacherRepository,
                teachingAssignmentRepository,
                subjectLevelRepository,
                schoolUserRepository,
                teacherMapper,
                teacherValidator,
                jwtClaimsExtractor
        );
        TeacherRequest request = teacherRequest("T1");
        Teacher teacher = Teacher.builder().idEnseignant(1L).codeEnseignant("T1").nom("Ben Ali").prenom("Amel").estEnPoste(true).build();
        TeacherResponse response = TeacherResponse.builder().idEnseignant(1L).codeEnseignant("T1").estEnPoste(true).build();

        when(teacherMapper.toEntity(request)).thenReturn(teacher);
        when(teacherRepository.save(teacher)).thenReturn(teacher);
        when(teacherMapper.toResponseLight(teacher)).thenReturn(response);
        when(teacherRepository.findByTenantIdAndIdEnseignant("tenant-1", 1L)).thenReturn(Optional.of(teacher));

        assertThat(service.createTeacher(request).getCodeEnseignant()).isEqualTo("T1");
        service.deactivateTeacher(1L);
        assertThat(teacher.getEstEnPoste()).isFalse();
        assertThatThrownBy(() -> service.getTeacherById(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void teachingAssignmentServiceCreatesRelationsAndRejectsDuplicates() {
        TeachingAssignmentServiceImpl service = teachingAssignmentService();
        TeachingAssignmentRequest request = TeachingAssignmentRequest.builder()
                .schoolYearId(1L)
                .teacherId(2L)
                .classGroupId(3L)
                .subjectLevelId(4L)
                .subjectSessionTypeId(5L)
                .priority(1)
                .build();
        SchoolYear year = SchoolYear.builder().idAnnee(1L).estActive(true).build();
        Level level = Level.builder().idNiveau(10L).build();
        Teacher teacher = Teacher.builder().idEnseignant(2L).estEnPoste(true).maxHeuresSemaine(20).build();
        ClassGroup classGroup = ClassGroup.builder().idClasse(3L).schoolYear(year).level(level).estActif(true).build();
        SubjectLevel subjectLevel = SubjectLevel.builder().idNiveauMatiere(4L).level(level).build();
        SubjectSessionType sessionType = SubjectSessionType.builder()
                .idSubjectSessionType(5L)
                .subjectLevel(subjectLevel)
                .duration(1.0)
                .estActif(true)
                .build();
        TeachingAssignment assignment = TeachingAssignment.builder().idTeachingAssignment(9L).build();
        TeachingAssignmentResponse response = TeachingAssignmentResponse.builder().idTeachingAssignment(9L).build();

        mockAssignmentRefs(year, teacher, classGroup, subjectLevel, sessionType);
        when(teachingAssignmentMapper.toEntity(request)).thenReturn(assignment);
        when(teachingAssignmentRepository.save(assignment)).thenReturn(assignment);
        when(teachingAssignmentMapper.toResponse(assignment)).thenReturn(response);

        assertThat(service.createTeachingAssignment(request).getIdTeachingAssignment()).isEqualTo(9L);
        assertThat(assignment.getTeacher()).isEqualTo(teacher);
        assertThat(assignment.getClassGroup()).isEqualTo(classGroup);

        when(teachingAssignmentRepository.existsDuplicate(1L, 2L, 3L, 4L, 5L, null)).thenReturn(true);
        assertThatThrownBy(() -> service.createTeachingAssignment(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void timeSlotGenerationCreatesThirtyMinuteSlotsSkipsInactiveDaysAndRequiresTenant() {
        TimeSlotGenerationServiceImpl service = new TimeSlotGenerationServiceImpl(timeSlotRepository);
        SchoolWorkingDay monday = SchoolWorkingDay.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .morningStart(LocalTime.of(8, 0))
                .morningEnd(LocalTime.of(9, 0))
                .afternoonStart(LocalTime.of(14, 0))
                .afternoonEnd(LocalTime.of(15, 0))
                .active(true)
                .build();

        assertThatThrownBy(() -> service.generateForDay(monday, 30)).isInstanceOf(TenantSecurityException.class);

        TenantContext.setTenantId("tenant-1");
        when(timeSlotRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<tn.wtm.school.org.entity.TimeSlot> slots = service.generateForDay(monday, 30);

        assertThat(slots).hasSize(4);
        assertThat(slots).allSatisfy(slot -> assertThat(java.time.Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes()).isEqualTo(30));
        verify(timeSlotRepository).deleteByTenantIdAndDayOfWeek("tenant-1", DayOfWeek.MONDAY);

        SchoolWorkingDay inactive = SchoolWorkingDay.builder()
                .dayOfWeek(DayOfWeek.TUESDAY)
                .active(false)
                .build();
        assertThat(service.generateForDay(inactive, 30)).isEmpty();
        verify(timeSlotRepository, never()).saveAll(List.of());
    }

    private SubjectServiceImpl subjectService() {
        return new SubjectServiceImpl(
                subjectRepository,
                subjectLevelRepository,
                subjectSessionTypeRepository,
                levelRepository,
                patternRepository,
                teachingAssignmentRepository,
                subjectMapper,
                subjectLevelMapper,
                subjectSessionTypeMapper,
                subjectValidator,
                subjectLevelValidator,
                subjectSessionTypeValidator
        );
    }

    private TeachingAssignmentServiceImpl teachingAssignmentService() {
        return new TeachingAssignmentServiceImpl(
                teachingAssignmentRepository,
                schoolYearRepository,
                teacherRepository,
                classGroupRepository,
                subjectLevelRepository,
                subjectSessionTypeRepository,
                teachingAssignmentMapper,
                teachingAssignmentValidator
        );
    }

    private void mockAssignmentRefs(
            SchoolYear year,
            Teacher teacher,
            ClassGroup classGroup,
            SubjectLevel subjectLevel,
            SubjectSessionType sessionType
    ) {
        when(schoolYearRepository.findById(1L)).thenReturn(Optional.of(year));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher));
        when(classGroupRepository.findById(3L)).thenReturn(Optional.of(classGroup));
        when(subjectLevelRepository.findById(4L)).thenReturn(Optional.of(subjectLevel));
        when(subjectSessionTypeRepository.findById(5L)).thenReturn(Optional.of(sessionType));
        when(teachingAssignmentRepository.findByTeacher_IdEnseignantAndSchoolYear_IdAnnee(2L, 1L))
                .thenReturn(List.of());
    }

    private RoomRequest roomRequest(String code) {
        return RoomRequest.builder()
                .codeSalle(code)
                .typeSalle(tn.wtm.school.org.enums.RoomType.NORMALE)
                .capacite(30)
                .build();
    }

    private TeacherRequest teacherRequest(String code) {
        return TeacherRequest.builder()
                .codeEnseignant(code)
                .numIdentite("CIN-" + code)
                .nom("Ben Ali")
                .prenom("Amel")
                .email(code.toLowerCase() + "@demo.test")
                .telephone("+216 71 111 111")
                .maxHeuresSemaine(20)
                .maxHeuresJour(6)
                .minHeuresJour(0)
                .build();
    }
}
