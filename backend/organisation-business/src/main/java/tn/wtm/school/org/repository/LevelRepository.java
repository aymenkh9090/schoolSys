package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.Level;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelRepository extends TenantAwareRepository<Level,Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<Level> findByCode(String code);

    Optional<Level> findByTenantIdAndIdNiveau(String tenantId, Long id);

    Optional<Level> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByCode(String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNiveauNot(String tenantId, String code, Long id);

    boolean existsByCodeAndIdNiveauNot(String code, Long id);

    // ── Filtres simples ───────────────────────────────────────────────────────

    List<Level> findByEstActifTrue();

    List<Level> findByEstActifTrueOrderByNomAsc();

    List<Level> findByTenantIdAndEstActifTrueOrderByNomAsc(String tenantId);

    Page<Level> findAll(Pageable pageable);

    // ── Avec classes et matières chargées ─────────────────────────────────────

    @Query("""
        SELECT DISTINCT l FROM Level l
        LEFT JOIN FETCH l.classGroups
        WHERE l.idNiveau = :id
        """)
    Optional<Level> findByIdWithClasses(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT l FROM Level l
        LEFT JOIN FETCH l.subjectLevels sl
        LEFT JOIN FETCH sl.subject
        WHERE l.idNiveau = :id
        """)
    Optional<Level> findByIdWithSubjects(@Param("id") Long id);

    // ── Stats par niveau ──────────────────────────────────────────────────────

    @Query("""
        SELECT l.code,
               COUNT(DISTINCT c.idClasse)      AS nbClasses,
               COUNT(DISTINCT sl.idNiveauMatiere) AS nbMatieres
        FROM Level l
        LEFT JOIN l.classGroups   c
        LEFT JOIN l.subjectLevels sl
        WHERE l.estActif = true
        GROUP BY l.idNiveau, l.code
        ORDER BY l.nom ASC
        """)
    List<Object[]> findLevelStats();






}
