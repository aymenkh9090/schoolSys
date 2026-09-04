package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.*;
import tn.wtm.school.org.enums.*;
import tn.wtm.school.org.repository.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatternDetailRepositoryIT extends AbstractIntegrationTest {

    @Autowired SchoolYearRepository schoolYearRepo;
    @Autowired LevelRepository levelRepo;
    @Autowired SubjectRepository subjectRepo;
    @Autowired SubjectLevelRepository subjectLevelRepo;
    @Autowired SubjectSessionTypeRepository subjectSessionTypeRepo;
    @Autowired PatternRepository patternRepo;
    @Autowired PatternDetailRepository repo;
    @Autowired TestEntityManager em;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_details_de_tenantB() {
        buildGraph("pd-t1", "T1SUB");
        buildGraph("pd-t2", "T2SUB");
        TenantContext.setTenantId("pd-t1");
        Graph g1 = buildGraph("pd-t1", "T1SUBX");
        savedDetail(g1, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        TenantContext.setTenantId("pd-t2");
        Graph g2 = buildGraph("pd-t2", "T2SUBX");
        savedDetail(g2, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);

        assertThat(repo.findByTenantId("pd-t1")).hasSize(1)
                .allMatch(d -> "pd-t1".equals(d.getTenantId()));
        assertThat(repo.findByTenantId("pd-t2")).hasSize(1)
                .allMatch(d -> "pd-t2".equals(d.getTenantId()));
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_et_findById() {
        TenantContext.setTenantId("pd-save");
        Graph g = buildGraph("pd-save", "SSUB");
        PatternDetail pd = savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        assertThat(pd.getIdPatternDetail()).isNotNull();
        assertThat(repo.findById(pd.getIdPatternDetail())).isPresent();
    }

    @Test
    void crud_delete_par_id() {
        TenantContext.setTenantId("pd-del");
        Graph g = buildGraph("pd-del", "DSUB");
        PatternDetail pd = savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        Long id = pd.getIdPatternDetail();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_duration() {
        TenantContext.setTenantId("pd-upd");
        Graph g = buildGraph("pd-upd", "USUB");
        PatternDetail pd = savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        pd.setDuration(3.0);
        assertThat(repo.saveAndFlush(pd).getDuration()).isEqualTo(3.0);
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void findByPattern_trieParSessionOrder() {
        TenantContext.setTenantId("pd-ord");
        Graph g = buildGraph("pd-ord", "OSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        savedDetail(g, SessionType.TD, RoomType.NORMALE, WeekParity.ALL, 2, false);
        savedDetail(g, SessionType.TP, RoomType.LABSCIENCE, WeekParity.ALL, 3, false);

        List<PatternDetail> ordered = repo.findByPattern_IdPatternOrderBySessionOrderAsc(g.pattern().getIdPattern());
        assertThat(ordered).extracting(PatternDetail::getSessionOrder).containsExactly(1, 2, 3);
    }

    @Test
    void existsByPattern_retourne_vrai_si_des_details_existent() {
        TenantContext.setTenantId("pd-exist");
        Graph g = buildGraph("pd-exist", "ESUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        assertThat(repo.existsByPattern_IdPattern(g.pattern().getIdPattern())).isTrue();
    }

    @Test
    void deleteByPattern_supprime_tous_les_details_du_pattern() {
        TenantContext.setTenantId("pd-delpat");
        Graph g = buildGraph("pd-delpat", "DPSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        savedDetail(g, SessionType.TD, RoomType.NORMALE, WeekParity.ALL, 2, false);

        repo.deleteByPattern_IdPattern(g.pattern().getIdPattern());
        assertThat(repo.existsByPattern_IdPattern(g.pattern().getIdPattern())).isFalse();
    }

    @Test
    void findByType_filtre_par_type_seance() {
        TenantContext.setTenantId("pd-type");
        Graph g = buildGraph("pd-type", "TYSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        savedDetail(g, SessionType.TP, RoomType.LABSCIENCE, WeekParity.ALL, 2, false);

        List<PatternDetail> courses = repo.findByType(SessionType.COURSE);
        assertThat(courses).isNotEmpty().allMatch(d -> d.getType() == SessionType.COURSE);
    }

    @Test
    void findByPatternAndType_filtre_par_pattern_et_type() {
        TenantContext.setTenantId("pd-pt");
        Graph g = buildGraph("pd-pt", "PTSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        savedDetail(g, SessionType.TP, RoomType.LABSCIENCE, WeekParity.ALL, 2, false);

        List<PatternDetail> result = repo.findByPattern_IdPatternAndType(g.pattern().getIdPattern(), SessionType.COURSE);
        assertThat(result).hasSize(1).allMatch(d -> d.getType() == SessionType.COURSE);
    }

    @Test
    void findByRequiredRoomType_filtre_par_type_salle() {
        TenantContext.setTenantId("pd-rt");
        Graph g = buildGraph("pd-rt", "RTSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        savedDetail(g, SessionType.TP, RoomType.LABSCIENCE, WeekParity.ALL, 2, false);

        List<PatternDetail> labs = repo.findByRequiredRoomType(RoomType.LABSCIENCE);
        assertThat(labs).isNotEmpty().allMatch(d -> d.getRequiredRoomType() == RoomType.LABSCIENCE);
    }

    @Test
    void findByRoomTypeAndLevel_filtre_par_salle_et_niveau() {
        TenantContext.setTenantId("pd-ral");
        Graph g = buildGraph("pd-ral", "RALSUB");
        savedDetail(g, SessionType.TP, RoomType.LABSCIENCE, WeekParity.ALL, 1, false);

        List<PatternDetail> result = repo.findByRoomTypeAndLevel(RoomType.LABSCIENCE, g.level().getIdNiveau());
        assertThat(result).hasSize(1).allMatch(d -> d.getRequiredRoomType() == RoomType.LABSCIENCE);
    }

    @Test
    void findBySplitTrue_retourne_seulement_seances_divisees() {
        TenantContext.setTenantId("pd-spl");
        Graph g = buildGraph("pd-spl", "SPLSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        savedDetail(g, SessionType.TP, RoomType.LABSCIENCE, WeekParity.ALL, 2, true);

        List<PatternDetail> splits = repo.findByPattern_IdPatternAndIsSplitTrue(g.pattern().getIdPattern());
        assertThat(splits).hasSize(1).allMatch(PatternDetail::getIsSplit);
    }

    @Test
    void findByPatternAndWeekParity_filtre_par_parite() {
        TenantContext.setTenantId("pd-par");
        Graph g = buildGraph("pd-par", "PARSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ODD, 1, false);
        savedDetail(g, SessionType.TD, RoomType.NORMALE, WeekParity.EVEN, 2, false);

        List<PatternDetail> odds = repo.findByPattern_IdPatternAndWeekParity(g.pattern().getIdPattern(), WeekParity.ODD);
        assertThat(odds).hasSize(1).allMatch(d -> d.getWeekParity() == WeekParity.ODD);
    }

    @Test
    void reorderAfterDelete_decremente_sessionOrder_apres_suppression() {
        TenantContext.setTenantId("pd-reorder");
        Graph g = buildGraph("pd-reorder", "REOSUB");
        savedDetail(g, SessionType.COURSE, RoomType.NORMALE, WeekParity.ALL, 1, false);
        savedDetail(g, SessionType.TD, RoomType.NORMALE, WeekParity.ALL, 2, false);
        savedDetail(g, SessionType.TP, RoomType.LABSCIENCE, WeekParity.ALL, 3, false);

        // Simule le réordonnancement après suppression fictive du slot d'ordre 2
        repo.reorderAfterDelete(g.pattern().getIdPattern(), 2);
        em.flush();
        em.clear();

        List<PatternDetail> details = repo.findByPattern_IdPatternOrderBySessionOrderAsc(g.pattern().getIdPattern());
        // Les 3 détails sont toujours là ; l'ancien order-3 est passé à order-2
        assertThat(details).hasSize(3);
        assertThat(details.stream().filter(d -> d.getSessionOrder() == 1).count()).isEqualTo(1);
        assertThat(details.stream().filter(d -> d.getSessionOrder() == 2).count()).isEqualTo(2);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private record Graph(Level level, SubjectLevel subjectLevel,
                         SubjectSessionType sst, Pattern pattern) {}

    private Graph buildGraph(String tenantId, String subjectCode) {
        TenantContext.setTenantId(tenantId);
        SchoolYear sy = schoolYearRepo.saveAndFlush(SchoolYear.builder()
                .nom(tenantId + "-" + subjectCode + "-SY")
                .dateDebut(LocalDate.of(2024, 9, 1)).dateFin(LocalDate.of(2025, 6, 30))
                .estActive(true).estCourante(true).build());
        Level level = levelRepo.saveAndFlush(Level.builder()
                .code(tenantId + "-" + subjectCode + "-L")
                .nom("7eme").description("desc").estActif(true).build());
        Subject subject = subjectRepo.saveAndFlush(Subject.builder()
                .codeMatiere(subjectCode).libMatiere(subjectCode + " lib")
                .estEnseignee(true).estPrincipale(false).build());
        SubjectLevel sl = subjectLevelRepo.saveAndFlush(SubjectLevel.builder()
                .subject(subject).level(level).heuresSemaine(4.0)
                .estObligatoire(true).coefficient(2.0).build());
        SubjectSessionType sst = subjectSessionTypeRepo.saveAndFlush(SubjectSessionType.builder()
                .subjectLevel(sl).type(SessionType.COURSE).duration(2.0)
                .requiresSplit(false).estActif(true).build());
        Pattern pattern = patternRepo.saveAndFlush(Pattern.builder()
                .name(tenantId + "-" + subjectCode + "-pat").totalHours(4.0).sessionCount(2)
                .patternType(PatternType.WEEKLY_IDENTICAL).schoolYear(sy).subjectLevel(sl).build());
        return new Graph(level, sl, sst, pattern);
    }

    private PatternDetail savedDetail(Graph g, SessionType type, RoomType roomType,
                                      WeekParity parity, int order, boolean isSplit) {
        return repo.saveAndFlush(PatternDetail.builder()
                .pattern(g.pattern()).subjectSessionType(g.sst())
                .sessionOrder(order).duration(2.0).type(type)
                .requiredRoomType(roomType).weekParity(parity).isSplit(isSplit).build());
    }
}
