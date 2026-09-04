package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.enums.Specialite;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClassGroupRepositoryIT extends AbstractIntegrationTest {

    @Autowired SchoolYearRepository schoolYearRepo;
    @Autowired LevelRepository levelRepo;
    @Autowired ClassGroupRepository repo;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_classes_de_tenantB() {
        buildClassGroup("cg-t1", "7A1");
        buildClassGroup("cg-t2", "7A1");

        assertThat(repo.findByTenantId("cg-t1")).hasSize(1)
                .allMatch(c -> "cg-t1".equals(c.getTenantId()));
        assertThat(repo.findByTenantId("cg-t2")).hasSize(1)
                .allMatch(c -> "cg-t2".equals(c.getTenantId()));
    }

    @Test
    void isolation_findByTenantIdAndCode_est_scope_par_tenant() {
        buildClassGroup("cg-iso1", "7B1");
        buildClassGroup("cg-iso2", "7B1");

        assertThat(repo.findByTenantIdAndCode("cg-iso1", "7B1"))
                .isPresent().get().extracting(ClassGroup::getTenantId).isEqualTo("cg-iso1");
        assertThat(repo.findByTenantIdAndCode("cg-iso2", "7B1"))
                .isPresent().get().extracting(ClassGroup::getTenantId).isEqualTo("cg-iso2");
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_et_findById() {
        ClassGroup cg = buildClassGroup("cg-save", "7C1");
        assertThat(cg.getIdClasse()).isNotNull();
        assertThat(repo.findById(cg.getIdClasse())).isPresent();
    }

    @Test
    void crud_delete_par_id() {
        ClassGroup cg = buildClassGroup("cg-del", "7D1");
        Long id = cg.getIdClasse();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_nbEleve() {
        ClassGroup cg = buildClassGroup("cg-upd", "7E1");
        cg.setNbEleve(35);
        assertThat(repo.saveAndFlush(cg).getNbEleve()).isEqualTo(35);
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void findByTenantIdAndCode_retourne_la_classe_correcte() {
        buildClassGroup("cg-code", "9A1");
        assertThat(repo.findByTenantIdAndCode("cg-code", "9A1"))
                .isPresent().get().extracting(ClassGroup::getCode).isEqualTo("9A1");
    }

    @Test
    void existsByTenantIdAndCode_vrai_si_code_existe() {
        buildClassGroup("cg-ex", "8A1");
        assertThat(repo.existsByTenantIdAndCode("cg-ex", "8A1")).isTrue();
        assertThat(repo.existsByTenantIdAndCode("cg-ex", "8Z9")).isFalse();
    }

    @Test
    void findByLevel_retourne_les_classes_de_ce_niveau() {
        TenantContext.setTenantId("cg-lvl");
        SchoolYear sy = savedYear("cg-lvl");
        Level l1 = savedLevel("cg-lvl", "L7");
        Level l2 = savedLevel("cg-lvl", "L8");
        savedClassGroup("cg-lvl", "7A1", sy, l1);
        savedClassGroup("cg-lvl", "7A2", sy, l1);
        savedClassGroup("cg-lvl", "8A1", sy, l2);

        assertThat(repo.findByLevel_IdNiveau(l1.getIdNiveau())).hasSize(2)
                .allMatch(c -> c.getLevel().getIdNiveau().equals(l1.getIdNiveau()));
    }

    @Test
    void findBySchoolYear_retourne_les_classes_de_cette_annee() {
        TenantContext.setTenantId("cg-sy");
        SchoolYear sy1 = schoolYearRepo.saveAndFlush(SchoolYear.builder()
                .nom("2024-SY1").dateDebut(LocalDate.of(2024, 9, 1))
                .dateFin(LocalDate.of(2025, 6, 30)).estActive(true).estCourante(false).build());
        SchoolYear sy2 = schoolYearRepo.saveAndFlush(SchoolYear.builder()
                .nom("2025-SY2").dateDebut(LocalDate.of(2025, 9, 1))
                .dateFin(LocalDate.of(2026, 6, 30)).estActive(false).estCourante(false).build());
        Level level = savedLevel("cg-sy", "L7");
        savedClassGroup("cg-sy", "7A1", sy1, level);
        savedClassGroup("cg-sy", "7A2", sy2, level);

        assertThat(repo.findBySchoolYear_IdAnnee(sy1.getIdAnnee())).hasSize(1)
                .allMatch(c -> c.getSchoolYear().getIdAnnee().equals(sy1.getIdAnnee()));
    }

    @Test
    void findByEstActifTrue_exclut_les_classes_inactives() {
        TenantContext.setTenantId("cg-act");
        SchoolYear sy = savedYear("cg-act");
        Level level = savedLevel("cg-act", "L7");
        ClassGroup active = savedClassGroup("cg-act", "7A1", sy, level);
        ClassGroup inactive = savedClassGroup("cg-act", "7A2", sy, level);
        inactive.setEstActif(false);
        repo.saveAndFlush(inactive);

        List<ClassGroup> actives = repo.findByEstActifTrueAndSchoolYear_IdAnnee(sy.getIdAnnee());
        assertThat(actives).hasSize(1)
                .allMatch(c -> c.getIdClasse().equals(active.getIdClasse()));
    }

    @Test
    void existsByCodeInLevelAndYear_detecte_doublon_dans_meme_niveau_et_annee() {
        TenantContext.setTenantId("cg-dup");
        SchoolYear sy = savedYear("cg-dup");
        Level level = savedLevel("cg-dup", "L7");
        ClassGroup existing = savedClassGroup("cg-dup", "7A1", sy, level);

        assertThat(repo.existsByCodeInLevelAndYear("7A1", level.getIdNiveau(), sy.getIdAnnee(), null)).isTrue();
        assertThat(repo.existsByCodeInLevelAndYear("7A1", level.getIdNiveau(), sy.getIdAnnee(), existing.getIdClasse())).isFalse();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private ClassGroup buildClassGroup(String tenantId, String code) {
        TenantContext.setTenantId(tenantId);
        SchoolYear sy = savedYear(tenantId);
        Level level = savedLevel(tenantId, "L7");
        return savedClassGroup(tenantId, code, sy, level);
    }

    private SchoolYear savedYear(String tenantId) {
        TenantContext.setTenantId(tenantId);
        return schoolYearRepo.saveAndFlush(SchoolYear.builder()
                .nom(tenantId + "-AY").dateDebut(LocalDate.of(2024, 9, 1))
                .dateFin(LocalDate.of(2025, 6, 30)).estActive(true).estCourante(true).build());
    }

    private Level savedLevel(String tenantId, String code) {
        TenantContext.setTenantId(tenantId);
        return levelRepo.saveAndFlush(Level.builder()
                .code(code).nom(code + " nom").description(code + " desc").estActif(true).build());
    }

    private ClassGroup savedClassGroup(String tenantId, String code, SchoolYear sy, Level level) {
        TenantContext.setTenantId(tenantId);
        return repo.saveAndFlush(ClassGroup.builder()
                .code(code).codeSpecialite(Specialite.TCOM).nbEleve(28)
                .estActif(true).schoolYear(sy).level(level).build());
    }
}
