package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.*;
import tn.wtm.school.org.enums.PatternType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectLevelRepositoryIT extends AbstractIntegrationTest {

    @Autowired SubjectRepository subjectRepo;
    @Autowired LevelRepository levelRepo;
    @Autowired SubjectLevelRepository repo;
    @Autowired SubjectSessionTypeRepository subjectSessionTypeRepo;
    @Autowired PatternRepository patternRepo;
    @Autowired SchoolYearRepository schoolYearRepo;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_subjectLevels_de_tenantB() {
        buildSubjectLevel("sl-t1", "MATH1", "L7");
        buildSubjectLevel("sl-t2", "MATH2", "L8");

        assertThat(repo.findByTenantId("sl-t1")).hasSize(1)
                .allMatch(sl -> "sl-t1".equals(sl.getTenantId()));
        assertThat(repo.findByTenantId("sl-t2")).hasSize(1)
                .allMatch(sl -> "sl-t2".equals(sl.getTenantId()));
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_et_findById() {
        SubjectLevel sl = buildSubjectLevel("sl-save", "PHYS", "L8");
        assertThat(sl.getIdNiveauMatiere()).isNotNull();
        assertThat(repo.findById(sl.getIdNiveauMatiere())).isPresent();
    }

    @Test
    void crud_delete_par_id() {
        SubjectLevel sl = buildSubjectLevel("sl-del", "CHIMIE", "L9");
        Long id = sl.getIdNiveauMatiere();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_heuresSemaine() {
        SubjectLevel sl = buildSubjectLevel("sl-upd", "HIST", "L7");
        sl.setHeuresSemaine(6.0);
        assertThat(repo.saveAndFlush(sl).getHeuresSemaine()).isEqualTo(6.0);
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void findBySubjectAndLevel_retourne_la_combinaison_unique() {
        SubjectLevel sl = buildSubjectLevel("sl-comb", "GEO", "L7");
        assertThat(repo.findBySubject_IdMatiereAndLevel_IdNiveau(
                sl.getSubject().getIdMatiere(), sl.getLevel().getIdNiveau())).isPresent();
    }

    @Test
    void existsBySubjectAndLevel_vrai_si_combinaison_existe() {
        SubjectLevel sl = buildSubjectLevel("sl-exist", "ART", "L8");
        assertThat(repo.existsBySubject_IdMatiereAndLevel_IdNiveau(
                sl.getSubject().getIdMatiere(), sl.getLevel().getIdNiveau())).isTrue();
    }

    @Test
    void findByLevel_retourne_tous_les_subjectLevels_du_niveau() {
        TenantContext.setTenantId("sl-lvl");
        Level level = savedLevel("sl-lvl", "L7");
        Subject s1 = savedSubject("sl-lvl", "MATH-A");
        Subject s2 = savedSubject("sl-lvl", "PHYS-A");
        savedSubjectLevel("sl-lvl", s1, level, 4.0, true);
        savedSubjectLevel("sl-lvl", s2, level, 3.0, true);

        assertThat(repo.findByLevel_IdNiveau(level.getIdNiveau())).hasSize(2)
                .allMatch(sl -> sl.getLevel().getIdNiveau().equals(level.getIdNiveau()));
    }

    @Test
    void findBySubject_retourne_tous_les_niveaux_de_cette_matiere() {
        TenantContext.setTenantId("sl-subj");
        Subject subject = savedSubject("sl-subj", "MATH-B");
        Level l7 = savedLevel("sl-subj", "L7");
        Level l8 = savedLevel("sl-subj", "L8");
        savedSubjectLevel("sl-subj", subject, l7, 4.0, true);
        savedSubjectLevel("sl-subj", subject, l8, 3.0, true);

        assertThat(repo.findBySubject_IdMatiere(subject.getIdMatiere())).hasSize(2)
                .allMatch(sl -> sl.getSubject().getIdMatiere().equals(subject.getIdMatiere()));
    }

    @Test
    void findByLevelAndObligatoire_exclut_les_optionnelles() {
        TenantContext.setTenantId("sl-obl");
        Level level = savedLevel("sl-obl", "L7");
        savedSubjectLevel("sl-obl", savedSubject("sl-obl", "MATH-C"), level, 4.0, true);
        savedSubjectLevel("sl-obl", savedSubject("sl-obl", "MUSIC"), level, 2.0, false);

        assertThat(repo.findByLevel_IdNiveauAndEstObligatoireTrue(level.getIdNiveau()))
                .hasSize(1).allMatch(SubjectLevel::getEstObligatoire);
    }

    @Test
    void findByIdFullyLoaded_charge_toutes_les_associations() {
        TenantContext.setTenantId("sl-full");
        SchoolYear sy = schoolYearRepo.saveAndFlush(SchoolYear.builder()
                .nom("sl-full-SY").dateDebut(LocalDate.of(2024, 9, 1))
                .dateFin(LocalDate.of(2025, 6, 30)).estActive(true).estCourante(true).build());
        Level level = savedLevel("sl-full", "L7");
        SubjectLevel sl = savedSubjectLevel("sl-full", savedSubject("sl-full", "MATH-D"), level, 4.0, true);
        subjectSessionTypeRepo.saveAndFlush(SubjectSessionType.builder()
                .subjectLevel(sl).type(SessionType.COURSE).duration(2.0)
                .requiresSplit(false).estActif(true).build());
        patternRepo.saveAndFlush(Pattern.builder()
                .name("pat-full").totalHours(4.0).sessionCount(2)
                .patternType(PatternType.WEEKLY_IDENTICAL).schoolYear(sy).subjectLevel(sl).build());

        Optional<SubjectLevel> loaded = repo.findByIdFullyLoaded(sl.getIdNiveauMatiere());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getSubject()).isNotNull();
        assertThat(loaded.get().getLevel()).isNotNull();
        assertThat(loaded.get().getSubjectSessionTypes()).isNotEmpty();
        assertThat(loaded.get().getPatterns()).isNotEmpty();
    }

    @Test
    void findByLevelWithSessionTypes_charge_les_session_types() {
        TenantContext.setTenantId("sl-sst");
        Level level = savedLevel("sl-sst", "L7");
        SubjectLevel sl = savedSubjectLevel("sl-sst", savedSubject("sl-sst", "MATH-E"), level, 4.0, true);
        subjectSessionTypeRepo.saveAndFlush(SubjectSessionType.builder()
                .subjectLevel(sl).type(SessionType.COURSE).duration(2.0)
                .requiresSplit(false).estActif(true).build());

        List<SubjectLevel> result = repo.findByLevelWithSessionTypes(level.getIdNiveau());
        assertThat(result).hasSize(1).allMatch(s -> !s.getSubjectSessionTypes().isEmpty());
    }

    @Test
    void findWeeklyHoursByLevel_retourne_total_heures_par_niveau() {
        TenantContext.setTenantId("sl-wh");
        Level level = savedLevel("sl-wh", "L7");
        savedSubjectLevel("sl-wh", savedSubject("sl-wh", "MATH-F"), level, 4.0, true);
        savedSubjectLevel("sl-wh", savedSubject("sl-wh", "PHYS-F"), level, 3.0, true);

        List<Object[]> stats = repo.findWeeklyHoursByLevel();
        assertThat(stats).isNotEmpty();
        assertThat(stats.getFirst()).hasSize(3);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private SubjectLevel buildSubjectLevel(String tenantId, String subjectCode, String levelCode) {
        TenantContext.setTenantId(tenantId);
        return savedSubjectLevel(tenantId, savedSubject(tenantId, subjectCode), savedLevel(tenantId, levelCode), 4.0, true);
    }

    private Subject savedSubject(String tenantId, String code) {
        TenantContext.setTenantId(tenantId);
        return subjectRepo.saveAndFlush(Subject.builder()
                .codeMatiere(code).libMatiere(code + " lib")
                .estEnseignee(true).estPrincipale(false).build());
    }

    private Level savedLevel(String tenantId, String code) {
        TenantContext.setTenantId(tenantId);
        return levelRepo.saveAndFlush(Level.builder()
                .code(code).nom(code + " nom").description(code + " desc").estActif(true).build());
    }

    private SubjectLevel savedSubjectLevel(String tenantId, Subject subject, Level level,
                                            double heures, boolean obligatoire) {
        TenantContext.setTenantId(tenantId);
        return repo.saveAndFlush(SubjectLevel.builder()
                .subject(subject).level(level).heuresSemaine(heures)
                .estObligatoire(obligatoire).coefficient(2.0).build());
    }
}
