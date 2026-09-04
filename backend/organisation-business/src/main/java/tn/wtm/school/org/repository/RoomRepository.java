package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.enums.RoomType;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends TenantAwareRepository<Room,Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<Room> findByCodeSalle(String codeSalle);

    Optional<Room> findByTenantIdAndIdSalle(String tenantId, Long id);

    Optional<Room> findByTenantIdAndCodeSalle(String tenantId, String codeSalle);

    boolean existsByCodeSalle(String codeSalle);

    boolean existsByTenantIdAndCodeSalle(String tenantId, String codeSalle);

    boolean existsByTenantIdAndCodeSalleAndIdSalleNot(String tenantId, String codeSalle, Long id);

    boolean existsByCodeSalleAndIdSalleNot(String codeSalle, Long id);

    // ── Filtres simples ───────────────────────────────────────────────────────

    List<Room> findByTypeSalle(RoomType typeSalle);

    List<Room> findByTypeSalleOrderByCodeSalleAsc(RoomType typeSalle);

    List<Room> findByCodeBloc(String codeBloc);

    Page<Room> findAll(Pageable pageable);

    Page<Room> findByTypeSalle(RoomType typeSalle, Pageable pageable);

    // ── Pour la génération : salles compatibles avec capacité et type ─────────

    @Query("""
        SELECT r FROM Room r
        WHERE r.typeSalle  = :type
          AND r.capacite  >= :capaciteMin
        ORDER BY r.capacite ASC
        """)
    List<Room> findCompatibleRooms(
            @Param("type")         RoomType type,
            @Param("capaciteMin")  int capaciteMin
    );

    // ── Par bloc / étage ──────────────────────────────────────────────────────

    @Query("""
        SELECT r FROM Room r
        WHERE (:bloc  IS NULL OR r.codeBloc  = :bloc)
          AND (:etage IS NULL OR r.numEtage  = :etage)
        ORDER BY r.codeBloc, r.codeSalle
        """)
    List<Room> findByBlocAndEtage(
            @Param("bloc")  String bloc,
            @Param("etage") String etage
    );

    // ── Stats capacité par type ───────────────────────────────────────────────

    @Query("""
        SELECT r.typeSalle,
               COUNT(r)         AS nbSalles,
               SUM(r.capacite)  AS capaciteTotale,
               AVG(r.capacite)  AS capaciteMoyenne
        FROM Room r
        GROUP BY r.typeSalle
        ORDER BY r.typeSalle
        """)
    List<Object[]> findCapacityStatsByType();



}
