package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.Subject;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends TenantAwareRepository<Subject, Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<Subject> findByCodeMatiere(String codeMatiere);

    Optional<Subject> findByTenantIdAndIdMatiere(String tenantId, Long id);

    /** Chargement en lot, pour résoudre des libellés sans requête par identifiant. */
    List<Subject> findByTenantIdAndIdMatiereIn(String tenantId, Collection<Long> ids);

    Optional<Subject> findByTenantIdAndCodeMatiere(String tenantId, String codeMatiere);

    boolean existsByCodeMatiere(String codeMatiere);

    boolean existsByTenantIdAndCodeMatiere(String tenantId, String codeMatiere);

    boolean existsByTenantIdAndCodeMatiereAndIdMatiereNot(String tenantId, String codeMatiere, Long id);

    boolean existsByCodeMatiereAndIdMatiereNot(String codeMatiere, Long id);

    // ── Filtres simples ───────────────────────────────────────────────────────

    List<Subject> findByEstEnseigneeTrue();

    List<Subject> findByEstPrincipaleTrue();

    List<Subject> findByNecessiteLabTrue();

    List<Subject> findByNecessiteSportTrue();

    List<Subject> findByEstEnseigneeTrueOrderByCodeMatiereAsc();

    List<Subject> findByTenantIdAndEstEnseigneeTrueOrderByCodeMatiereAsc(String tenantId);

    List<Subject> findByTenantIdAndEstPrincipaleTrue(String tenantId);

    Page<Subject> findAll(Pageable pageable);

    Page<Subject> findByEstEnseignee(Boolean estEnseignee, Pageable pageable);

    // ── Recherche par libellé ─────────────────────────────────────────────────

    @Query("""
        SELECT s FROM Subject s
        WHERE LOWER(s.libMatiere) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(s.codeMatiere) LIKE LOWER(CONCAT('%', :search, '%'))
        ORDER BY s.codeMatiere ASC
        """)
    List<Subject> searchByLibOrCode(@Param("search") String search);

    // ── Matières non encore affectées à un niveau donné ───────────────────────

    @Query("""
        SELECT s FROM Subject s
        WHERE s.estEnseignee = true
          AND s.idMatiere NOT IN (
              SELECT sl.subject.idMatiere
              FROM SubjectLevel sl
              WHERE sl.level.idNiveau = :levelId
          )
        ORDER BY s.codeMatiere ASC
        """)
    List<Subject> findSubjectsNotInLevel(@Param("levelId") Long levelId);

    // ── Avec niveaux chargés ──────────────────────────────────────────────────

    @Query("""
        SELECT DISTINCT s FROM Subject s
        LEFT JOIN FETCH s.subjectLevels sl
        LEFT JOIN FETCH sl.level
        WHERE s.idMatiere = :id
        """)
    Optional<Subject> findByIdWithLevels(@Param("id") Long id);

}
