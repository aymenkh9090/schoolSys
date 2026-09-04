package tn.wtm.school.planning.solver.repository;

import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.planning.solver.entity.TimetableJob;
import tn.wtm.school.planning.solver.enums.SolverStatus;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import tn.wtm.school.common.metrics.TenantInterval;
import tn.wtm.school.common.metrics.TenantStatusCount;

@Repository
public interface TimetableJobRepository extends TenantAwareRepository<TimetableJob, Long> {

    Optional<TimetableJob> findByIdTimetableJobAndTenantId(Long id, String tenantId);

    List<TimetableJob> findByTenantIdOrderByIdTimetableJobDesc(String tenantId);

    List<TimetableJob> findByTenantIdAndAcademicYearIdOrderByIdTimetableJobDesc(
            String tenantId, Long academicYearId);

    List<TimetableJob> findByTenantIdAndStatusOrderByIdTimetableJobDesc(
            String tenantId, SolverStatus status);

    // ── Métriques plateforme ───────────────────────────────────────────────
    // Volontairement non filtrées par tenant : appelées par le collecteur de
    // métriques, hors contexte de requête, pour balayer tous les établissements
    // en une passe. Le filtre Hibernate n'est pas actif faute de TenantContext.

    /** Générations par établissement et par statut, depuis le début. */
    @Query("""
           SELECT new tn.wtm.school.common.metrics.TenantStatusCount(j.tenantId, CAST(j.status AS string), COUNT(j))
           FROM TimetableJob j
           GROUP BY j.tenantId, j.status
           """)
    List<TenantStatusCount> countGroupedByTenantAndStatus();

    /**
     * Bornes de la dernière génération terminée de chaque établissement.
     *
     * Le MAX(id) en sous-requête désigne la dernière ligne écrite plutôt que la
     * plus récemment terminée : l'identifiant est une séquence, il est monotone,
     * alors que {@code finished_at} peut être identique à la seconde près sur
     * deux jobs enchaînés.
     */
    @Query("""
           SELECT new tn.wtm.school.common.metrics.TenantInterval(j.tenantId, j.startedAt, j.finishedAt)
           FROM TimetableJob j
           WHERE j.startedAt IS NOT NULL
             AND j.finishedAt IS NOT NULL
             AND j.idTimetableJob = (
                   SELECT MAX(j2.idTimetableJob) FROM TimetableJob j2
                   WHERE j2.tenantId = j.tenantId
                     AND j2.startedAt IS NOT NULL
                     AND j2.finishedAt IS NOT NULL)
           """)
    List<TenantInterval> findLastGenerationIntervalByTenant();
}
