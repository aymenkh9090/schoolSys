package tn.wtm.school.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tn.wtm.school.common.context.TenantContext;

import java.util.Optional;

/**
 * order = HIGHEST_PRECEDENCE : force le proxy transactionnel Spring à être le plus
 * "externe" de la chaîne d'advices. Sans ça, l'ordre entre lui et
 * {@link TenantHibernateFilterAspect} (qui doit s'exécuter APRÈS le début de la
 * transaction, pour avoir une Session Hibernate déjà liée au thread) n'est pas garanti.
 */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class JpaConfig {


    /**
     * Branché sur TenantContext (rempli par TenantFilter depuis le claim JWT
     * preferred_username) : createdBy/updatedBy reflètent le vrai utilisateur.
     * Fallback "system" pour les écritures hors requête HTTP (DemoDataRunner,
     * jobs planifiés, thread async du solveur) où TenantContext est vide.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(TenantContext.getUsername())
                .filter(username -> !username.isBlank())
                .or(() -> Optional.of("system"));
    }


}
