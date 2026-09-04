package tn.wtm.school.common.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import tn.wtm.school.common.context.TenantContext;

/**
 * Active le filtre Hibernate {@code tenantFilter} (déclaré sur
 * {@link tn.wtm.school.common.base.TenantEntity}) sur la Session courante avant
 * l'exécution de toute méthode transactionnelle.
 *
 * Avant cet aspect, le filtre était défini mais jamais activé (personne n'appelait
 * session.enableFilter(...)) : l'isolation reposait uniquement sur les
 * .findByTenantId(...) explicites de chaque repository, plus TenantEntityListener
 * côté écriture. Cet aspect ajoute une seconde ligne de défense au niveau Hibernate
 * lui-même : même une requête qui "oublie" de filtrer par tenant (findAll(),
 * findById() sur un TenantEntity, jointure JPQL, etc.) ne retournera plus que les
 * lignes du tenant courant.
 *
 * Ordre d'exécution garanti par {@link JpaConfig#EnableTransactionManagement} :
 * le proxy transactionnel Spring (HIGHEST_PRECEDENCE) démarre la transaction et
 * lie l'EntityManager au thread AVANT que cet aspect (ordre par défaut,
 * LOWEST_PRECEDENCE) ne s'exécute — donc entityManager.unwrap(Session.class)
 * renvoie toujours une Session valide ici.
 *
 * Limite connue : les transactions ouvertes via TransactionTemplate/txTemplate
 * (ex: TimetableSolverService.persistResult/onFailed, exécutés depuis un thread
 * async CompletableFuture) ne passent PAS par un appel de méthode annoté
 * @Transactional — ce pointcut ne les intercepte donc pas. Ces méthodes restent
 * protégées uniquement par les requêtes explicitement filtrées par tenantId.
 */
@Aspect
@Component
@Slf4j
public class TenantHibernateFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) "
            + "|| @within(org.springframework.transaction.annotation.Transactional)")
    public void enableTenantFilter() {
        if (!TenantContext.hasTenant()) {
            // Endpoints plateforme (ex: /api/tenants, PLATFORM_SUPER_ADMIN) :
            // aucun tenant en contexte, volontairement non filtré.
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(TenantFilterConstants.FILTER_NAME)
                .setParameter(TenantFilterConstants.PARAM_TENANT_ID, TenantContext.getTenantId());
    }
}
