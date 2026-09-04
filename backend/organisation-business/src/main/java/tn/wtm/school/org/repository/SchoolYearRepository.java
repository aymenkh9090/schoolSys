package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.SchoolYear;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolYearRepository extends TenantAwareRepository<SchoolYear,Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<SchoolYear> findByNom(String nom);

    boolean existsByNom(String nom);

    Optional<SchoolYear> findByTenantIdAndIdAnnee(String tenantId, Long id);

    Optional<SchoolYear> findByTenantIdAndNom(String tenantId, String nom);

    boolean existsByTenantIdAndNom(String tenantId, String nom);

    boolean existsByTenantIdAndNomAndIdAnneeNot(String tenantId, String nom, Long id);

    // ── Filtres simples ───────────────────────────────────────────────────────

    List<SchoolYear> findByEstActiveTrue();

    Optional<SchoolYear> findByEstCouranteTrue();

    Optional<SchoolYear> findByTenantIdAndEstCouranteTrue(String tenantId);

    List<SchoolYear> findAllByOrderByDateDebutDesc();

    Page<SchoolYear> findAllByOrderByDateDebutDesc(Pageable pageable);

    // ── Chevauchement de dates ────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(s) > 0 FROM SchoolYear s
        WHERE s.dateDebut <= :dateFin
          AND s.dateFin   >= :dateDebut
          AND (:excludeId IS NULL OR s.idAnnee <> :excludeId)
        """)
    boolean existsOverlappingPeriod(
            @Param("dateDebut")  LocalDate dateDebut,
            @Param("dateFin")    LocalDate dateFin,
            @Param("excludeId")  Long excludeId
    );

    // ── Désactiver toutes les années sauf une (une seule courante à la fois) ──

    @Modifying
    @Query("UPDATE SchoolYear s SET s.estCourante = false WHERE s.idAnnee <> :id")
    void resetCouranteExcept(@Param("id") Long id);

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Query("""
        SELECT s.nom, COUNT(c) as nbClasses
        FROM SchoolYear s
        LEFT JOIN s.classGroups c
        GROUP BY s.idAnnee, s.nom
        ORDER BY s.dateDebut DESC
        """)
    List<Object[]> findYearsWithClassCount();



}
