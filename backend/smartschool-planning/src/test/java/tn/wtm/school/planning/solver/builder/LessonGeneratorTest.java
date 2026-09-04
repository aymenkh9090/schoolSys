package tn.wtm.school.planning.solver.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.org.entity.*;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.WeekParity;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.planning.solver.domain.Lesson;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonGeneratorTest {

    @Mock
    private TeachingAssignmentRepository assignmentRepository;

    private LessonGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new LessonGenerator(assignmentRepository);
    }

    // ── full-class session ────────────────────────────────────────────────────

    @Test
    void fullClassSessionOf1hGeneratesOneLessonWithGroupIndex0() {
        when(assignmentRepository.findAllForGeneration(2026L))
                .thenReturn(List.of(assignment("MATH", 2026L, SessionType.COURSE, 1.0, false, null)));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        assertThat(lessons).hasSize(1);
        assertThat(lessons.getFirst().getGroupIndex()).isZero();
        assertThat(lessons.getFirst().isPaired()).isFalse();
        assertThat(lessons.getFirst().getSubjectCode()).isEqualTo("MATH");
        assertThat(lessons.getFirst().getStudentClassName()).isEqualTo("7B1");
    }

    @Test
    void fullClassSessionOf2hGeneratesOneLessonWithFourSlots() {
        when(assignmentRepository.findAllForGeneration(2026L))
                .thenReturn(List.of(assignment("MATH", 2026L, SessionType.COURSE, 2.0, false, null)));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        // 2h → 1 seule leçon occupant 4 créneaux de 30min (multi-slot)
        assertThat(lessons).hasSize(1);
        assertThat(lessons.getFirst().getGroupIndex()).isZero();
        assertThat(lessons.getFirst().isPaired()).isFalse();
        assertThat(lessons.getFirst().getDurationSlots()).isEqualTo(4);
    }

    @Test
    void lessonDurationSlotsMatchesHours() {
        when(assignmentRepository.findAllForGeneration(2026L))
                .thenReturn(List.of(assignment("MATH", 2026L, SessionType.COURSE, 1.5, false, null)));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        assertThat(lessons).hasSize(1);
        assertThat(lessons.getFirst().getDurationSlots()).isEqualTo(3); // 1h30 → 3 slots de 30min
    }

    // ── demi-group session ────────────────────────────────────────────────────

    @Test
    void splitSessionOf2hGeneratesOnePairedDemiGroupPair() {
        when(assignmentRepository.findAllForGeneration(2026L))
                .thenReturn(List.of(assignment("PHYS", 2026L, SessionType.TP, 2.0, true, null)));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        // 2h split → 1 paire demi-groupe (A+B), chaque leçon occupe 4 slots de 30min
        assertThat(lessons).hasSize(2);
        Lesson groupA = lessons.stream().filter(l -> l.getGroupIndex() == 1).findFirst().orElseThrow();
        Lesson groupB = lessons.stream().filter(l -> l.getGroupIndex() == 2).findFirst().orElseThrow();
        assertThat(groupA.getDurationSlots()).isEqualTo(4);
        assertThat(groupB.getDurationSlots()).isEqualTo(4);
    }

    @Test
    void splitSessionPairsShareTheSamePairedLessonId() {
        when(assignmentRepository.findAllForGeneration(2026L))
                .thenReturn(List.of(assignment("PHYS", 2026L, SessionType.TP, 1.0, true, null)));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        assertThat(lessons).hasSize(2);
        Lesson groupA = lessons.stream().filter(l -> l.getGroupIndex() == 1).findFirst().orElseThrow();
        Lesson groupB = lessons.stream().filter(l -> l.getGroupIndex() == 2).findFirst().orElseThrow();
        assertThat(groupA.getPairedLessonId()).isNotNull();
        assertThat(groupA.getPairedLessonId()).isEqualTo(groupB.getPairedLessonId());
    }

    @Test
    void differentSplitSessionsHaveDistinctPairedLessonIds() {
        when(assignmentRepository.findAllForGeneration(2026L)).thenReturn(List.of(
                assignment("PHYS", 2026L, SessionType.TP, 1.0, true, null),
                assignment("SVT",  2026L, SessionType.TP, 1.0, true, null)));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        List<Long> pairIds = lessons.stream()
                .map(Lesson::getPairedLessonId).distinct().toList();
        assertThat(pairIds).hasSize(2); // two distinct pair IDs
    }

    // ── pattern detail priority ───────────────────────────────────────────────

    @Test
    void patternDetailsOverrideSessionTypeDefaultWhenTypeMatches() {
        SubjectSessionType sst = sst(SessionType.COURSE, 1.0, false);
        Subject subject = subject("MATH");
        SubjectLevel level = subjectLevel(subject, List.of());
        ClassGroup cg = classGroup("7B1", 30);
        SchoolYear sy = schoolYear(2026L);

        // Pattern with a COURSE detail (2h) matching the SST type
        PatternDetail detail = PatternDetail.builder()
                .idPatternDetail(1L)
                .sessionOrder(1)
                .duration(2.0)
                .isSplit(false)
                .type(SessionType.COURSE)
                .weekParity(WeekParity.ALL)
                .requiredRoomType(null)
                .build();

        Pattern pattern = Pattern.builder()
                .idPattern(1L)
                .name("MATH 7eme")
                .patternDetails(List.of(detail))
                .build();

        SubjectLevel levelWithPattern = SubjectLevel.builder()
                .idNiveauMatiere(1L)
                .subject(subject)
                .patterns(new java.util.LinkedHashSet<>(List.of(pattern)))
                .build();

        TeachingAssignment ta = ta(1L, "school-1", sy, teacher("T01"), cg, levelWithPattern, sst);

        when(assignmentRepository.findAllForGeneration(2026L)).thenReturn(List.of(ta));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        // Pattern says 2h COURSE → 1 leçon multi-slot de 4 créneaux (pas 2 séances d'1h)
        assertThat(lessons).hasSize(1);
        assertThat(lessons.getFirst().getDurationSlots()).isEqualTo(4);
    }

    // ── session type mapping ──────────────────────────────────────────────────

    @Test
    void courseSessionTypeMapsToCOURS() {
        assertThat(LessonGenerator.mapSessionType(SessionType.COURSE))
                .isEqualTo(tn.wtm.school.planning.solver.enums.SessionType.COURS);
    }

    @Test
    void tpSessionTypeMapsToTP() {
        assertThat(LessonGenerator.mapSessionType(SessionType.TP))
                .isEqualTo(tn.wtm.school.planning.solver.enums.SessionType.TP);
    }

    @Test
    void sportSessionTypeMapsToSPORT() {
        assertThat(LessonGenerator.mapSessionType(SessionType.SPORT))
                .isEqualTo(tn.wtm.school.planning.solver.enums.SessionType.SPORT);
    }

    // ── room type mapping ─────────────────────────────────────────────────────

    @Test
    void labPhysiqueRoomTypeMapsToLabPhysique() {
        Subject s = subject("PHYS");
        assertThat(LessonGenerator.mapRoomType(RoomType.LABPHYSIQUE, s))
                .isEqualTo(tn.wtm.school.planning.solver.enums.RoomType.LABPHYSIQUE);
    }

    @Test
    void labInformatiqueRoomTypeMapsToLabInformatique() {
        Subject s = subject("INFO");
        assertThat(LessonGenerator.mapRoomType(RoomType.LABINFORMATIQUE, s))
                .isEqualTo(tn.wtm.school.planning.solver.enums.RoomType.LABINFORMATIQUE);
    }

    @Test
    void normaleRoomTypeMapsToNormale() {
        Subject s = subject("MATH");
        assertThat(LessonGenerator.mapRoomType(RoomType.NORMALE, s))
                .isEqualTo(tn.wtm.school.planning.solver.enums.RoomType.NORMALE);
    }

    @Test
    void subjectNecessiteLabMapsToLabPhysiqueWhenNoRoomTypeSet() {
        Subject s = Subject.builder()
                .idMatiere(1L).codeMatiere("PHYS").libMatiere("Physique")
                .necessiteLab(true).necessiteSport(false).build();
        assertThat(LessonGenerator.mapRoomType(null, s))
                .isEqualTo(tn.wtm.school.planning.solver.enums.RoomType.LABPHYSIQUE);
    }

    // ── tenant isolation ──────────────────────────────────────────────────────

    @Test
    void assignmentsFromOtherTenantsAreExcluded() {
        TeachingAssignment foreignTA = assignment("MATH", 2026L, SessionType.COURSE, 1.0, false, null);
        foreignTA.setTenantId("other-school");

        when(assignmentRepository.findAllForGeneration(2026L)).thenReturn(List.of(foreignTA));

        List<Lesson> lessons = generator.generate("school-1", 2026L);

        assertThat(lessons).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TeachingAssignment assignment(String subjectCode, Long yearId,
                                           SessionType sessionType, Double duration,
                                           boolean split, RoomType roomType) {
        Subject subject = subject(subjectCode);
        SubjectLevel level = subjectLevel(subject, List.of());
        ClassGroup cg = classGroup("7B1", 30);
        SchoolYear sy = schoolYear(yearId);
        SubjectSessionType sst = sst(sessionType, duration, split);
        TeachingAssignment ta = ta(1L, "school-1", sy, teacher("T01"), cg, level, sst);
        return ta;
    }

    private Subject subject(String code) {
        return Subject.builder()
                .idMatiere(1L).codeMatiere(code).libMatiere(code + " label")
                .necessiteLab(false).necessiteSport(false).build();
    }

    private SubjectLevel subjectLevel(Subject subject, List<Pattern> patterns) {
        return SubjectLevel.builder()
                .idNiveauMatiere(1L).subject(subject).patterns(new java.util.LinkedHashSet<>(patterns)).build();
    }

    private ClassGroup classGroup(String code, int nbEleve) {
        return ClassGroup.builder().idClasse(1L).code(code).nbEleve(nbEleve).build();
    }

    private SchoolYear schoolYear(Long id) {
        SchoolYear sy = new SchoolYear();
        sy.setIdAnnee(id);
        return sy;
    }

    private SubjectSessionType sst(SessionType type, Double duration, boolean split) {
        return SubjectSessionType.builder()
                .idSubjectSessionType(1L)
                .type(type).duration(duration)
                .requiresSplit(split).groupCount(2).build();
    }

    private Teacher teacher(String code) {
        return Teacher.builder()
                .idEnseignant(1L).codeEnseignant(code)
                .nom("Enseignant").prenom("Test")
                .maxHeuresJour(6).estEnPoste(true).build();
    }

    private TeachingAssignment ta(Long id, String tenantId, SchoolYear sy,
                                   Teacher teacher, ClassGroup cg,
                                   SubjectLevel level, SubjectSessionType sst) {
        TeachingAssignment ta = TeachingAssignment.builder()
                .idTeachingAssignment(id)
                .schoolYear(sy).teacher(teacher)
                .classGroup(cg).subjectLevel(level)
                .subjectSessionType(sst)
                .isActive(true).build();
        ta.setTenantId(tenantId);
        return ta;
    }
}
