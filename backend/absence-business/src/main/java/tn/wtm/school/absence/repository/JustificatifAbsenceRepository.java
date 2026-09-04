package tn.wtm.school.absence.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import tn.wtm.school.absence.entity.JustificatifAbsence;
import tn.wtm.school.common.repository.TenantAwareRepository;

import java.util.Optional;

/**
 * La recherche multi-critères passe par une {@link org.springframework.data.jpa.domain.Specification}
 * plutôt que par un JPQL à filtres optionnels : sur PostgreSQL, un
 * {@code (:param IS NULL OR ...)} dont le paramètre vaut {@code null} est rejeté
 * (« could not determine data type of parameter »), le pilote envoyant alors un
 * NULL sans type. La construction dynamique n'ajoute que les prédicats utiles,
 * donc aucun paramètre nul n'est transmis.
 */
@Repository
public interface JustificatifAbsenceRepository
        extends TenantAwareRepository<JustificatifAbsence, Long>, JpaSpecificationExecutor<JustificatifAbsence> {

    Optional<JustificatifAbsence> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByTenantIdAndLigneAppel_Id(String tenantId, Long ligneAppelId);
}
