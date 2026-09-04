package tn.wtm.school.planning.solver.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.planning.solver.entity.GeneratedTimetable;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedTimetableRepository
        extends TenantAwareRepository<GeneratedTimetable, Long> {

    /** Résultat lié à un job précis (relation 1-1 via uk_generated_timetable_job). */
    Optional<GeneratedTimetable> findByJobIdAndTenantId(Long jobId, String tenantId);

    /** Résultat par son propre identifiant, avec isolation tenant. */
    Optional<GeneratedTimetable> findByIdGeneratedTimetableAndTenantId(Long id, String tenantId);

    /** Tous les emplois du temps d'un tenant, toutes années confondues. */
    List<GeneratedTimetable> findByTenantIdOrderByIdGeneratedTimetableDesc(String tenantId);

    /** Tous les emplois du temps d'un tenant pour une année scolaire. */
    List<GeneratedTimetable> findByTenantIdAndAcademicYearIdOrderByIdGeneratedTimetableDesc(
            String tenantId, Long academicYearId);

    /** Emplois du temps publiés d'un tenant pour une année scolaire. */
    @Query("""
            SELECT g FROM GeneratedTimetable g
            WHERE g.tenantId     = :tenantId
              AND g.academicYearId = :yearId
              AND g.status       = 'PUBLISHED'
            ORDER BY g.publishedAt DESC
            """)
    List<GeneratedTimetable> findPublishedByTenantIdAndAcademicYearId(
            @Param("tenantId") String tenantId,
            @Param("yearId")   Long academicYearId);

    /** Dernier emploi du temps publié d'un tenant pour une année scolaire. */
    @Query("""
            SELECT g FROM GeneratedTimetable g
            WHERE g.tenantId     = :tenantId
              AND g.academicYearId = :yearId
              AND g.status       = 'PUBLISHED'
            ORDER BY g.publishedAt DESC
            LIMIT 1
            """)
    Optional<GeneratedTimetable> findLatestPublishedByTenantIdAndAcademicYearId(
            @Param("tenantId") String tenantId,
            @Param("yearId")   Long academicYearId);

    /** Vérifie si un résultat existe déjà pour ce job (avant upsert). */
    boolean existsByJobIdAndTenantId(Long jobId, String tenantId);
}
