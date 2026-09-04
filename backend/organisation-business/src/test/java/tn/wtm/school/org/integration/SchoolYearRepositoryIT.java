package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
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

class SchoolYearRepositoryIT extends AbstractIntegrationTest {

    @Autowired SchoolYearRepository repo;
    @Autowired LevelRepository levelRepo;
    @Autowired ClassGroupRepository classGroupRepo;
    @Autowired TestEntityManager em;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_annees_de_tenantB() {
        savedYear("sy-t1", "2024-2025", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        savedYear("sy-t2", "2024-2025", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);

        assertThat(repo.findByTenantId("sy-t1")).hasSize(1)
                .allMatch(y -> "sy-t1".equals(y.getTenantId()));
        assertThat(repo.findByTenantId("sy-t2")).hasSize(1)
                .allMatch(y -> "sy-t2".equals(y.getTenantId()));
    }

    @Test
    void isolation_findByTenantIdAndNom_scope_par_tenant() {
        savedYear("sy-iso1", "AN-2024", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        savedYear("sy-iso2", "AN-2024", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);

        assertThat(repo.findByTenantIdAndNom("sy-iso1", "AN-2024"))
                .isPresent().get().extracting(SchoolYear::getTenantId).isEqualTo("sy-iso1");
        assertThat(repo.findByTenantIdAndNom("sy-iso2", "AN-2024"))
                .isPresent().get().extracting(SchoolYear::getTenantId).isEqualTo("sy-iso2");
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_et_findById() {
        SchoolYear sy = savedYear("sy-save", "2024-SAVE",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        assertThat(sy.getIdAnnee()).isNotNull();
        assertThat(repo.findById(sy.getIdAnnee())).isPresent();
    }

    @Test
    void crud_delete_par_id() {
        SchoolYear sy = savedYear("sy-del", "2024-DEL",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, false);
        Long id = sy.getIdAnnee();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_estActive() {
        SchoolYear sy = savedYear("sy-upd", "2024-UPD",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        sy.setEstActive(false);
        assertThat(repo.saveAndFlush(sy).getEstActive()).isFalse();
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void findByTenantIdAndNom_retourne_annee_correcte() {
        savedYear("sy-nom", "ANNEE-2024",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        assertThat(repo.findByTenantIdAndNom("sy-nom", "ANNEE-2024"))
                .isPresent().get().extracting(SchoolYear::getNom).isEqualTo("ANNEE-2024");
    }

    @Test
    void existsByTenantIdAndNom_vrai_si_nom_existe() {
        savedYear("sy-ex", "ANNEE-EX",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        assertThat(repo.existsByTenantIdAndNom("sy-ex", "ANNEE-EX")).isTrue();
        assertThat(repo.existsByTenantIdAndNom("sy-ex", "ANNEE-INEX")).isFalse();
    }

    @Test
    void existsByTenantIdAndNomAndIdAnneeNot_detecte_doublon_en_modification() {
        SchoolYear sy1 = savedYear("sy-dup", "AN-DUP",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        SchoolYear sy2 = savedYear("sy-dup", "AN-DUP2",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30), false, false);

        assertThat(repo.existsByTenantIdAndNomAndIdAnneeNot("sy-dup", "AN-DUP", sy2.getIdAnnee())).isTrue();
        assertThat(repo.existsByTenantIdAndNomAndIdAnneeNot("sy-dup", "AN-DUP", sy1.getIdAnnee())).isFalse();
    }

    @Test
    void findByEstActiveTrue_retourne_seulement_les_actives() {
        TenantContext.setTenantId("sy-act");
        savedYear("sy-act", "ACTIVE",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        savedYear("sy-act", "INACTIVE",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30), false, false);

        assertThat(repo.findByEstActiveTrue()).isNotEmpty().allMatch(SchoolYear::getEstActive);
    }

    @Test
    void findByEstCouranteTrue_retourne_lannee_courante() {
        TenantContext.setTenantId("sy-curr");
        savedYear("sy-curr", "COURANTE",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        savedYear("sy-curr", "ANCIENNE",
                LocalDate.of(2023, 9, 1), LocalDate.of(2024, 6, 30), false, false);

        assertThat(repo.findByEstCouranteTrue())
                .isPresent().get().extracting(SchoolYear::getNom).isEqualTo("COURANTE");
    }

    @Test
    void findAllByOrderByDateDebutDesc_retourne_par_date_decroissante() {
        TenantContext.setTenantId("sy-ord");
        savedYear("sy-ord", "2022-SY", LocalDate.of(2022, 9, 1), LocalDate.of(2023, 6, 30), false, false);
        savedYear("sy-ord", "2024-SY", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        savedYear("sy-ord", "2023-SY", LocalDate.of(2023, 9, 1), LocalDate.of(2024, 6, 30), false, false);

        List<SchoolYear> ordered = repo.findAllByOrderByDateDebutDesc();
        assertThat(ordered).hasSizeGreaterThanOrEqualTo(3);
        assertThat(ordered.getFirst().getDateDebut())
                .isAfterOrEqualTo(ordered.getLast().getDateDebut());
    }

    @Test
    void existsOverlappingPeriod_detecte_chevauchement() {
        TenantContext.setTenantId("sy-ov");
        savedYear("sy-ov", "2024-OV",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);

        assertThat(repo.existsOverlappingPeriod(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null)).isTrue();
    }

    @Test
    void existsOverlappingPeriod_false_si_pas_de_chevauchement() {
        TenantContext.setTenantId("sy-nov");
        savedYear("sy-nov", "2024-NOV",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);

        assertThat(repo.existsOverlappingPeriod(
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30), null)).isFalse();
    }

    @Test
    void resetCouranteExcept_desactive_toutes_les_annees_sauf_une() {
        TenantContext.setTenantId("sy-reset");
        SchoolYear sy1 = savedYear("sy-reset", "COURANTE",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        SchoolYear sy2 = savedYear("sy-reset", "ANCIENNE",
                LocalDate.of(2023, 9, 1), LocalDate.of(2024, 6, 30), false, true);

        repo.resetCouranteExcept(sy1.getIdAnnee());
        em.flush();
        em.clear();

        assertThat(repo.findById(sy1.getIdAnnee()))
                .isPresent().get().extracting(SchoolYear::getEstCourante).isEqualTo(true);
        assertThat(repo.findById(sy2.getIdAnnee()))
                .isPresent().get().extracting(SchoolYear::getEstCourante).isEqualTo(false);
    }

    @Test
    void findYearsWithClassCount_retourne_nombre_de_classes_par_annee() {
        TenantContext.setTenantId("sy-cc");
        SchoolYear sy = savedYear("sy-cc", "CC-2024",
                LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 30), true, true);
        Level level = levelRepo.saveAndFlush(Level.builder()
                .code("L7").nom("7eme").description("desc").estActif(true).build());
        classGroupRepo.saveAndFlush(ClassGroup.builder()
                .code("7A1").codeSpecialite(Specialite.TCOM).nbEleve(28)
                .estActif(true).schoolYear(sy).level(level).build());
        classGroupRepo.saveAndFlush(ClassGroup.builder()
                .code("7A2").codeSpecialite(Specialite.TCOM).nbEleve(25)
                .estActif(true).schoolYear(sy).level(level).build());

        List<Object[]> result = repo.findYearsWithClassCount();
        Optional<Object[]> row = result.stream()
                .filter(r -> "CC-2024".equals(r[0])).findFirst();
        assertThat(row).isPresent();
        assertThat(((Number) row.get()[1]).longValue()).isEqualTo(2L);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private SchoolYear savedYear(String tenantId, String nom,
                                  LocalDate debut, LocalDate fin,
                                  boolean active, boolean courante) {
        TenantContext.setTenantId(tenantId);
        return repo.saveAndFlush(SchoolYear.builder()
                .nom(nom).dateDebut(debut).dateFin(fin)
                .estActive(active).estCourante(courante).build());
    }
}
