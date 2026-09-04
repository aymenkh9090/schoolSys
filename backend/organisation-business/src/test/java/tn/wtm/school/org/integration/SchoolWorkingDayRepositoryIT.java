package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.SchoolWorkingDay;
import tn.wtm.school.org.repository.WorkingDayRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolWorkingDayRepositoryIT extends AbstractIntegrationTest {

    @Autowired WorkingDayRepository repo;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_jours_de_tenantB() {
        savedDay("wd-t1", DayOfWeek.MONDAY, true);
        savedDay("wd-t2", DayOfWeek.MONDAY, true);

        assertThat(repo.findByTenantId("wd-t1")).hasSize(1)
                .allMatch(d -> "wd-t1".equals(d.getTenantId()));
        assertThat(repo.findByTenantId("wd-t2")).hasSize(1)
                .allMatch(d -> "wd-t2".equals(d.getTenantId()));
    }

    @Test
    void isolation_findByTenantIdAndDayOfWeek_ne_traverse_pas_les_tenants() {
        savedDay("wd-iso1", DayOfWeek.FRIDAY, true);
        savedDay("wd-iso2", DayOfWeek.FRIDAY, true);

        assertThat(repo.findByTenantIdAndDayOfWeek("wd-iso1", DayOfWeek.FRIDAY))
                .isPresent().get().extracting(SchoolWorkingDay::getTenantId).isEqualTo("wd-iso1");
        assertThat(repo.findByTenantIdAndDayOfWeek("wd-iso2", DayOfWeek.FRIDAY))
                .isPresent().get().extracting(SchoolWorkingDay::getTenantId).isEqualTo("wd-iso2");
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_et_findById() {
        SchoolWorkingDay day = savedDay("wd-save", DayOfWeek.TUESDAY, true);
        assertThat(day.getSchoolWorkingDayId()).isNotNull();
        assertThat(repo.findById(day.getSchoolWorkingDayId())).isPresent();
    }

    @Test
    void crud_delete_par_id() {
        SchoolWorkingDay day = savedDay("wd-del", DayOfWeek.WEDNESDAY, true);
        Long id = day.getSchoolWorkingDayId();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_active_passe_a_false() {
        SchoolWorkingDay day = savedDay("wd-upd", DayOfWeek.THURSDAY, true);
        day.setActive(false);
        assertThat(repo.saveAndFlush(day).getActive()).isFalse();
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void findByTenantIdOrderByDayOfWeek_retourne_jours_tries() {
        savedDay("wd-ord", DayOfWeek.FRIDAY, true);
        savedDay("wd-ord", DayOfWeek.MONDAY, true);
        savedDay("wd-ord", DayOfWeek.WEDNESDAY, true);

        List<SchoolWorkingDay> days = repo.findByTenantIdOrderByDayOfWeekAsc("wd-ord");
        assertThat(days).hasSize(3);
        assertThat(days.getFirst().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void findByTenantIdAndActive_filtre_par_etat() {
        savedDay("wd-act", DayOfWeek.MONDAY, true);
        savedDay("wd-act", DayOfWeek.TUESDAY, false);
        savedDay("wd-act", DayOfWeek.WEDNESDAY, true);

        assertThat(repo.findByTenantIdAndActive("wd-act", true)).hasSize(2)
                .allMatch(SchoolWorkingDay::getActive);
        assertThat(repo.findByTenantIdAndActive("wd-act", false)).hasSize(1)
                .allMatch(d -> !d.getActive());
    }

    @Test
    void findByTenantIdAndDayOfWeek_retourne_le_bon_jour() {
        savedDay("wd-day", DayOfWeek.MONDAY, true);
        savedDay("wd-day", DayOfWeek.TUESDAY, true);

        Optional<SchoolWorkingDay> result = repo.findByTenantIdAndDayOfWeek("wd-day", DayOfWeek.MONDAY);
        assertThat(result).isPresent()
                .get().extracting(SchoolWorkingDay::getDayOfWeek).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void existsByTenantIdAndDayOfWeek_vrai_si_jour_configure() {
        savedDay("wd-ex", DayOfWeek.SATURDAY, true);
        assertThat(repo.existsByTenantIdAndDayOfWeek("wd-ex", DayOfWeek.SATURDAY)).isTrue();
        assertThat(repo.existsByTenantIdAndDayOfWeek("wd-ex", DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void deleteByTenantId_supprime_tous_les_jours_du_tenant() {
        savedDay("wd-deltid", DayOfWeek.MONDAY, true);
        savedDay("wd-deltid", DayOfWeek.TUESDAY, true);
        savedDay("wd-deltid", DayOfWeek.WEDNESDAY, true);

        repo.deleteByTenantId("wd-deltid");
        assertThat(repo.findByTenantId("wd-deltid")).isEmpty();
    }

    @Test
    void deleteByTenantId_ne_supprime_pas_les_jours_des_autres_tenants() {
        savedDay("wd-del-ok", DayOfWeek.MONDAY, true);
        savedDay("wd-del-other", DayOfWeek.MONDAY, true);

        repo.deleteByTenantId("wd-del-ok");

        assertThat(repo.findByTenantId("wd-del-ok")).isEmpty();
        assertThat(repo.findByTenantId("wd-del-other")).hasSize(1);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private SchoolWorkingDay savedDay(String tenantId, DayOfWeek day, boolean active) {
        TenantContext.setTenantId(tenantId);
        return repo.saveAndFlush(SchoolWorkingDay.builder()
                .dayOfWeek(day)
                .morningStart(LocalTime.of(8, 0)).morningEnd(LocalTime.of(12, 0))
                .afternoonStart(LocalTime.of(14, 0)).afternoonEnd(LocalTime.of(18, 0))
                .active(active)
                .build());
    }
}
