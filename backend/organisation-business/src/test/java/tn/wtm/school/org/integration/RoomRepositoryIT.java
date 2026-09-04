package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.repository.RoomRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomRepositoryIT extends AbstractIntegrationTest {

    @Autowired RoomRepository repo;

    // ─── BLOC 1 : Isolation tenant ─────────────────────────────────────────────

    @Test
    void isolation_tenantA_ne_voit_pas_les_salles_de_tenantB() {
        savedRoom("rm-t1", "S101", RoomType.NORMALE, 30, null, null);
        savedRoom("rm-t2", "S101", RoomType.NORMALE, 30, null, null);

        assertThat(repo.findByTenantId("rm-t1")).hasSize(1)
                .allMatch(r -> "rm-t1".equals(r.getTenantId()));
        assertThat(repo.findByTenantId("rm-t2")).hasSize(1)
                .allMatch(r -> "rm-t2".equals(r.getTenantId()));
    }

    @Test
    void isolation_findByTenantIdAndCodeSalle_ne_traverse_pas_les_tenants() {
        savedRoom("rm-iso1", "S200", RoomType.NORMALE, 25, null, null);
        savedRoom("rm-iso2", "S200", RoomType.NORMALE, 25, null, null);

        assertThat(repo.findByTenantIdAndCodeSalle("rm-iso1", "S200"))
                .isPresent().get().extracting(Room::getTenantId).isEqualTo("rm-iso1");
        assertThat(repo.findByTenantIdAndCodeSalle("rm-iso2", "S200"))
                .isPresent().get().extracting(Room::getTenantId).isEqualTo("rm-iso2");
    }

    // ─── BLOC 2 : CRUD ─────────────────────────────────────────────────────────

    @Test
    void crud_save_et_findById() {
        Room room = savedRoom("rm-save", "S102", RoomType.NORMALE, 30, "A", "1");
        assertThat(room.getIdSalle()).isNotNull();
        assertThat(repo.findById(room.getIdSalle())).isPresent();
    }

    @Test
    void crud_delete_par_id() {
        Room room = savedRoom("rm-del", "S103", RoomType.NORMALE, 30, null, null);
        Long id = room.getIdSalle();
        repo.deleteById(id);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    void crud_update_capacite() {
        Room room = savedRoom("rm-upd", "S104", RoomType.NORMALE, 20, null, null);
        room.setCapacite(40);
        assertThat(repo.saveAndFlush(room).getCapacite()).isEqualTo(40);
    }

    // ─── BLOC 3 : Requêtes métier ──────────────────────────────────────────────

    @Test
    void findByTenantIdAndCodeSalle_retourne_salle_correcte() {
        savedRoom("rm-code", "LAB01", RoomType.LABSCIENCE, 20, null, null);
        assertThat(repo.findByTenantIdAndCodeSalle("rm-code", "LAB01"))
                .isPresent().get().extracting(Room::getCodeSalle).isEqualTo("LAB01");
    }

    @Test
    void existsByTenantIdAndCodeSalle_vrai_si_code_existe() {
        savedRoom("rm-ex1", "S201", RoomType.NORMALE, 30, null, null);
        assertThat(repo.existsByTenantIdAndCodeSalle("rm-ex1", "S201")).isTrue();
        assertThat(repo.existsByTenantIdAndCodeSalle("rm-ex1", "S999")).isFalse();
    }

    @Test
    void existsByTenantIdAndCodeSalleAndIdSalleNot_detecte_doublon_en_modification() {
        Room r1 = savedRoom("rm-excl", "S301", RoomType.NORMALE, 30, null, null);
        Room r2 = savedRoom("rm-excl", "S302", RoomType.NORMALE, 30, null, null);

        assertThat(repo.existsByTenantIdAndCodeSalleAndIdSalleNot("rm-excl", "S301", r2.getIdSalle())).isTrue();
        assertThat(repo.existsByTenantIdAndCodeSalleAndIdSalleNot("rm-excl", "S301", r1.getIdSalle())).isFalse();
    }

    @Test
    void findByTypeSalle_filtre_par_type() {
        savedRoom("rm-typ", "S401", RoomType.LABSCIENCE, 20, null, null);
        savedRoom("rm-typ", "S402", RoomType.NORMALE, 30, null, null);

        List<Room> labs = repo.findByTypeSalleOrderByCodeSalleAsc(RoomType.LABSCIENCE);
        assertThat(labs).isNotEmpty().allMatch(r -> RoomType.LABSCIENCE == r.getTypeSalle());
    }

    @Test
    void findByCodeBloc_retourne_salles_du_bloc() {
        savedRoom("rm-bloc", "B101", RoomType.NORMALE, 30, "BLOC-A", "1");
        savedRoom("rm-bloc", "B102", RoomType.NORMALE, 30, "BLOC-A", "2");
        savedRoom("rm-bloc", "B201", RoomType.NORMALE, 30, "BLOC-B", "1");

        List<Room> blocA = repo.findByCodeBloc("BLOC-A");
        assertThat(blocA).hasSize(2).allMatch(r -> "BLOC-A".equals(r.getCodeBloc()));
    }

    @Test
    void findCompatibleRooms_filtre_par_type_et_capacite_minimale() {
        savedRoom("rm-comp", "C101", RoomType.LABSCIENCE, 20, null, null);
        savedRoom("rm-comp", "C102", RoomType.LABSCIENCE, 40, null, null);
        savedRoom("rm-comp", "C103", RoomType.LABSCIENCE, 10, null, null);

        List<Room> compatible = repo.findCompatibleRooms(RoomType.LABSCIENCE, 20);
        assertThat(compatible).hasSize(2)
                .allMatch(r -> RoomType.LABSCIENCE == r.getTypeSalle() && r.getCapacite() >= 20);
    }

    @Test
    void findByBlocAndEtage_filtre_par_bloc_et_etage() {
        savedRoom("rm-be", "E101", RoomType.NORMALE, 30, "BL1", "E1");
        savedRoom("rm-be", "E201", RoomType.NORMALE, 30, "BL1", "E2");
        savedRoom("rm-be", "E301", RoomType.NORMALE, 30, "BL2", "E1");

        List<Room> result = repo.findByBlocAndEtage("BL1", "E1");
        assertThat(result).hasSize(1)
                .allMatch(r -> "BL1".equals(r.getCodeBloc()) && "E1".equals(r.getNumEtage()));
    }

    @Test
    void findCapacityStatsByType_retourne_stats_par_type() {
        savedRoom("rm-stat", "ST01", RoomType.NORMALE, 30, null, null);
        savedRoom("rm-stat", "ST02", RoomType.NORMALE, 20, null, null);
        savedRoom("rm-stat", "ST03", RoomType.LABSCIENCE, 15, null, null);

        List<Object[]> stats = repo.findCapacityStatsByType();
        assertThat(stats).isNotEmpty();
        assertThat(stats.getFirst()).hasSize(4);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Room savedRoom(String tenantId, String code, RoomType type,
                            int capacite, String bloc, String etage) {
        TenantContext.setTenantId(tenantId);
        return repo.saveAndFlush(Room.builder()
                .codeSalle(code).typeSalle(type).capacite(capacite)
                .codeBloc(bloc).numEtage(etage).build());
    }
}
