package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.*;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.Specialite;
import tn.wtm.school.org.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingAssignmentRepositoryIT extends AbstractIntegrationTest {

    @Autowired SchoolYearRepository schoolYearRepo;
    @Autowired LevelRepository levelRepo;
    @Autowired SubjectRepository subjectRepo;
    @Autowired SubjectLevelRepository subjectLevelRepo;
    @Autowired SubjectSessionTypeRepository subjectSessionTypeRepo;
    @Autowired ClassGroupRepository classGroupRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired TeachingAssignmentRepository repo;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_affectations_de_tenantB() {
        buildGraph("ta-t1", "AY1", "AT1", "7A1", "AMATH");
        buildGraph("ta-t2", "BY1", "BT1", "7B1", "BMATH");

        assertThat(repo.findByTenantId("ta-t1")).hasSize(1)
                .allMatch(a -> "ta-t1".equals(a.getTenantId()));
        assertThat(repo.findByTenantId("ta-t2")).hasSize(1)
                .allMatch(a -> "ta-t2".equals(a.getTenantId()));
        assertThat(repo.findByTenantId("ta-t1").getFirst().getIdTeachingAssignment())
                .isNotEqualTo(repo.findByTenantId("ta-t2").getFirst().getIdTeachingAssignment());
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_retourne_entite_avec_id() {
        Graph g = buildGraph("ta-save", "SY1", "ST1", "7S1", "SMATH");
        assertThat(g.assignment().getIdTeachingAssignment()).isNotNull();
        assertThat(repo.findById(g.assignment().getIdTeachingAssignment())).isPresent();
    }

    @Test
    void crud_delete_supprime_entite() {
        Graph g = buildGraph("ta-del", "DY1", "DT1", "7D1", "DMATH");
        Long id = g.assignment().getIdTeachingAssignment();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_priority_et_isActive() {
        Graph g = buildGraph("ta-upd", "UY1", "UT1", "7U1", "UMATH");
        g.assignment().setPriority(9);
        g.assignment().setIsActive(false);
        TeachingAssignment updated = repo.saveAndFlush(g.assignment());
        assertThat(updated.getPriority()).isEqualTo(9);
        assertThat(updated.getIsActive()).isFalse();
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void existsDuplicate_retourne_vrai_si_meme_combinaison() {
        Graph g = buildGraph("ta-dup", "PY1", "PT1", "7P1", "PMATH");
        TeachingAssignment ta = g.assignment();
        boolean existe = repo.existsDuplicate(
                ta.getSchoolYear().getIdAnnee(), ta.getTeacher().getIdEnseignant(),
                ta.getClassGroup().getIdClasse(), ta.getSubjectLevel().getIdNiveauMatiere(),
                ta.getSubjectSessionType().getIdSubjectSessionType(), null);
        assertThat(existe).isTrue();
    }

    @Test
    void existsDuplicate_exclut_la_propre_entite_en_modification() {
        Graph g = buildGraph("ta-excl", "EY1", "ET1", "7E1", "EMATH");
        TeachingAssignment ta = g.assignment();
        boolean existe = repo.existsDuplicate(
                ta.getSchoolYear().getIdAnnee(), ta.getTeacher().getIdEnseignant(),
                ta.getClassGroup().getIdClasse(), ta.getSubjectLevel().getIdNiveauMatiere(),
                ta.getSubjectSessionType().getIdSubjectSessionType(), ta.getIdTeachingAssignment());
        assertThat(existe).isFalse();
    }

    @Test
    void findByTeacher_retourne_les_affectations_de_cet_enseignant() {
        Graph g = buildGraph("ta-tch", "TY1", "TT1", "7T1", "TMATH");
        List<TeachingAssignment> result = repo.findByTeacher_IdEnseignant(g.teacher().getIdEnseignant());
        assertThat(result).hasSize(1)
                .allMatch(a -> a.getTeacher().getIdEnseignant().equals(g.teacher().getIdEnseignant()));
    }

    @Test
    void findByClassGroup_retourne_les_affectations_de_cette_classe() {
        Graph g = buildGraph("ta-cg", "GY1", "GT1", "7G1", "GMATH");
        List<TeachingAssignment> result = repo.findByClassGroup_IdClasse(g.classGroup().getIdClasse());
        assertThat(result).hasSize(1)
                .allMatch(a -> a.getClassGroup().getIdClasse().equals(g.classGroup().getIdClasse()));
    }

    @Test
    void findBySchoolYear_retourne_les_affectations_de_cette_annee() {
        Graph g = buildGraph("ta-sy", "FY1", "FT1", "7F1", "FMATH");
        List<TeachingAssignment> result = repo.findBySchoolYear_IdAnnee(g.schoolYear().getIdAnnee());
        assertThat(result).hasSize(1)
                .allMatch(a -> a.getSchoolYear().getIdAnnee().equals(g.schoolYear().getIdAnnee()));
    }

    @Test
    void findByTeacherAndYear_filtre_par_deux_criteres() {
        Graph g = buildGraph("ta-ty", "HY1", "HT1", "7H1", "HMATH");
        List<TeachingAssignment> result = repo.findByTeacher_IdEnseignantAndSchoolYear_IdAnnee(
                g.teacher().getIdEnseignant(), g.schoolYear().getIdAnnee());
        assertThat(result).hasSize(1);
    }

    @Test
    void findByIsActiveTrueAndYear_exclut_les_inactives() {
        Graph g = buildGraph("ta-act", "IY1", "IT1", "7I1", "IMATH");
        g.assignment().setIsActive(false);
        repo.saveAndFlush(g.assignment());
        assertThat(repo.findByIsActiveTrueAndSchoolYear_IdAnnee(g.schoolYear().getIdAnnee())).isEmpty();
    }

    @Test
    void findByIdFullyLoaded_charge_toutes_les_associations() {
        Graph g = buildGraph("ta-full", "JY1", "JT1", "7J1", "JMATH");
        Optional<TeachingAssignment> loaded = repo.findByIdFullyLoaded(g.assignment().getIdTeachingAssignment());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getTeacher()).isNotNull();
        assertThat(loaded.get().getClassGroup()).isNotNull();
        assertThat(loaded.get().getSubjectLevel()).isNotNull();
        assertThat(loaded.get().getSubjectSessionType()).isNotNull();
    }

    @Test
    void findAllForGeneration_retourne_actives_avec_enseignant_en_poste() {
        Graph g = buildGraph("ta-gen", "KY1", "KT1", "7K1", "KMATH");
        List<TeachingAssignment> result = repo.findAllForGeneration(g.schoolYear().getIdAnnee());
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getIsActive()).isTrue();
        assertThat(result.getFirst().getTeacher().getEstEnPoste()).isTrue();
    }

    @Test
    void findAllForGeneration_exclut_enseignant_non_en_poste() {
        Graph g = buildGraph("ta-hors", "LY1", "LT1", "7L1", "LMATH");
        g.teacher().setEstEnPoste(false);
        teacherRepo.saveAndFlush(g.teacher());
        assertThat(repo.findAllForGeneration(g.schoolYear().getIdAnnee())).isEmpty();
    }

    @Test
    void findTeacherLoadByYear_retourne_total_heures_par_enseignant() {
        Graph g = buildGraph("ta-load", "MY1", "MT1", "7M1", "MMATH");
        List<Object[]> load = repo.findTeacherLoadByYear(g.schoolYear().getIdAnnee());
        assertThat(load).hasSize(1);
        assertThat(load.getFirst()[0]).isEqualTo(g.teacher().getIdEnseignant());
    }

    @Test
    void findConflicts_detecte_meme_enseignant_meme_subjectLevel_classes_differentes() {
        Graph g = buildGraph("ta-conf", "NY1", "NT1", "7N1", "NMATH");
        TenantContext.setTenantId("ta-conf");
        ClassGroup cg2 = classGroupRepo.saveAndFlush(ClassGroup.builder()
                .code("7N2").codeSpecialite(Specialite.TCOM).nbEleve(20).estActif(true)
                .schoolYear(g.schoolYear()).level(g.level()).build());
        repo.saveAndFlush(TeachingAssignment.builder()
                .schoolYear(g.schoolYear()).teacher(g.teacher()).classGroup(cg2)
                .subjectLevel(g.subjectLevel()).subjectSessionType(g.sst())
                .priority(2).isActive(true).build());

        List<TeachingAssignment> conflicts = repo.findConflicts(
                g.schoolYear().getIdAnnee(), g.teacher().getIdEnseignant(), g.subjectLevel().getIdNiveauMatiere());
        assertThat(conflicts).hasSize(2);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private record Graph(SchoolYear schoolYear, Level level, SubjectLevel subjectLevel,
                         SubjectSessionType sst, ClassGroup classGroup,
                         Teacher teacher, TeachingAssignment assignment) {}

    private Graph buildGraph(String tenantId, String yearNom, String teacherCode,
                             String classCode, String subjectCode) {
        TenantContext.setTenantId(tenantId);
        SchoolYear sy = schoolYearRepo.saveAndFlush(SchoolYear.builder()
                .nom(yearNom).dateDebut(LocalDate.of(2024, 9, 1))
                .dateFin(LocalDate.of(2025, 6, 30)).estActive(true).estCourante(true).build());
        Level level = levelRepo.saveAndFlush(Level.builder()
                .code(tenantId + "-L7").nom("7eme").description("niveau 7").estActif(true).build());
        Subject subject = subjectRepo.saveAndFlush(Subject.builder()
                .codeMatiere(subjectCode).libMatiere(subjectCode + " lib")
                .estEnseignee(true).estPrincipale(false).build());
        SubjectLevel sl = subjectLevelRepo.saveAndFlush(SubjectLevel.builder()
                .subject(subject).level(level).heuresSemaine(4.0)
                .estObligatoire(true).coefficient(2.0).build());
        SubjectSessionType sst = subjectSessionTypeRepo.saveAndFlush(SubjectSessionType.builder()
                .subjectLevel(sl).type(SessionType.COURSE).duration(2.0)
                .requiresSplit(false).estActif(true).build());
        ClassGroup cg = classGroupRepo.saveAndFlush(ClassGroup.builder()
                .code(classCode).codeSpecialite(Specialite.TCOM).nbEleve(25)
                .estActif(true).schoolYear(sy).level(level).build());
        Teacher teacher = teacherRepo.saveAndFlush(Teacher.builder()
                .codeEnseignant(teacherCode).numIdentite(teacherCode + "-ID")
                .nom("Nom").prenom("Prenom").email(teacherCode + "@test.com")
                .maxHeuresSemaine(20).estEnPoste(true).build());
        TeachingAssignment ta = repo.saveAndFlush(TeachingAssignment.builder()
                .schoolYear(sy).teacher(teacher).classGroup(cg)
                .subjectLevel(sl).subjectSessionType(sst).priority(1).isActive(true).build());
        return new Graph(sy, level, sl, sst, cg, teacher, ta);
    }
}
