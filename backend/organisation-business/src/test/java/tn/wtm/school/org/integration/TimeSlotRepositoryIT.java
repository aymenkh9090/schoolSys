package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.org.repository.TimeSlotRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSlotRepositoryIT extends AbstractIntegrationTest {

    @Autowired TimeSlotRepository repo;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_creneaux_de_tenantB() {
        savedSlot("ts-t1", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        savedSlot("ts-t2", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);

        assertThat(repo.findByTenantId("ts-t1")).hasSize(1)
                .allMatch(s -> "ts-t1".equals(s.getTenantId()));
        assertThat(repo.findByTenantId("ts-t2")).hasSize(1)
                .allMatch(s -> "ts-t2".equals(s.getTenantId()));
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_et_findById() {
        TimeSlot slot = savedSlot("ts-save", DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        assertThat(slot.getIdTimeSlot()).isNotNull();
        assertThat(repo.findById(slot.getIdTimeSlot())).isPresent();
    }

    @Test
    void crud_delete_par_id() {
        TimeSlot slot = savedSlot("ts-del", DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        Long id = slot.getIdTimeSlot();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_orderIndex() {
        TimeSlot slot = savedSlot("ts-upd", DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        slot.setOrderIndex(99);
        assertThat(repo.saveAndFlush(slot).getOrderIndex()).isEqualTo(99);
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void findByTenantId_ordonne_par_jour_puis_index() {
        savedSlot("ts-ord", DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(10, 30), 2, DayPeriod.MORNING);
        savedSlot("ts-ord", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);

        List<TimeSlot> slots = repo.findByTenantIdOrderByDayOfWeekAscOrderIndexAsc("ts-ord");
        assertThat(slots).hasSize(2);
        assertThat(slots.getFirst().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void findByDayOfWeek_retourne_uniquement_ce_jour() {
        TenantContext.setTenantId("ts-dow");
        savedSlot("ts-dow", DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        savedSlot("ts-dow", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);

        List<TimeSlot> fridays = repo.findByDayOfWeek(DayOfWeek.FRIDAY);
        assertThat(fridays).isNotEmpty().allMatch(s -> s.getDayOfWeek() == DayOfWeek.FRIDAY);
    }

    @Test
    void findByTenantIdAndDayOfWeek_filtre_par_tenant_et_jour() {
        savedSlot("ts-tnd", DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        savedSlot("ts-tnd", DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);

        List<TimeSlot> result = repo.findByTenantIdAndDayOfWeekOrderByOrderIndexAsc("ts-tnd", DayOfWeek.TUESDAY);
        assertThat(result).hasSize(1).allMatch(s -> s.getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    void findByDayOfWeekAndDayPeriod_filtre_par_periode() {
        TenantContext.setTenantId("ts-per");
        savedSlot("ts-per", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        savedSlot("ts-per", DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(14, 30), 2, DayPeriod.AFTERNOON);

        List<TimeSlot> mornings = repo.findByDayOfWeekAndDayPeriod(DayOfWeek.MONDAY, DayPeriod.MORNING);
        assertThat(mornings).isNotEmpty().allMatch(s -> s.getDayPeriod() == DayPeriod.MORNING);
    }

    @Test
    void deleteByTenantId_supprime_tous_les_creneaux_du_tenant() {
        savedSlot("ts-deltid", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        savedSlot("ts-deltid", DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);

        repo.deleteByTenantId("ts-deltid");
        assertThat(repo.findByTenantId("ts-deltid")).isEmpty();
    }

    @Test
    void deleteByTenantIdAndDayOfWeek_supprime_uniquement_ce_jour() {
        savedSlot("ts-delday", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);
        savedSlot("ts-delday", DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);

        repo.deleteByTenantIdAndDayOfWeek("ts-delday", DayOfWeek.MONDAY);

        List<TimeSlot> remaining = repo.findByTenantId("ts-delday");
        assertThat(remaining).hasSize(1).allMatch(s -> s.getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    void existsByStartTimeLessThanAndEndTimeGreaterThan_detecte_chevauchement() {
        TenantContext.setTenantId("ts-ov");
        savedSlot("ts-ov", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), 1, DayPeriod.MORNING);

        boolean overlaps = repo.existsByDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
                DayOfWeek.MONDAY, LocalTime.of(9, 30), LocalTime.of(8, 30));
        assertThat(overlaps).isTrue();
    }

    @Test
    void existsByTenantIdAndDayOfWeekAndStartTimeAndEndTime_detecte_creneau_existant() {
        savedSlot("ts-exist", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30), 1, DayPeriod.MORNING);

        assertThat(repo.existsByTenantIdAndDayOfWeekAndStartTimeAndEndTime(
                "ts-exist", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 30))).isTrue();
        assertThat(repo.existsByTenantIdAndDayOfWeekAndStartTimeAndEndTime(
                "ts-exist", DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 30))).isFalse();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private TimeSlot savedSlot(String tenantId, DayOfWeek day, LocalTime start,
                                LocalTime end, int orderIndex, DayPeriod period) {
        TenantContext.setTenantId(tenantId);
        return repo.saveAndFlush(TimeSlot.builder()
                .dayOfWeek(day).startTime(start).endTime(end)
                .orderIndex(orderIndex).dayPeriod(period).build());
    }
}
