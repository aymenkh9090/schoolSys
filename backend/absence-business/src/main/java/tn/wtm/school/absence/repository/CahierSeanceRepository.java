package tn.wtm.school.absence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.absence.entity.CahierSeance;
import tn.wtm.school.absence.repository.projection.CahierCorpusRow;
import tn.wtm.school.common.repository.TenantAwareRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * La recherche à filtres optionnels passe par une
 * {@link org.springframework.data.jpa.domain.Specification} plutôt que par un
 * JPQL à paramètres nullables, pour la raison déjà consignée dans
 * {@link JustificatifAbsenceRepository} : sur PostgreSQL, un
 * {@code (:param IS NULL OR ...)} dont le paramètre vaut {@code null} est rejeté
 * (« could not determine data type of parameter »), le pilote envoyant un NULL
 * sans type. Un CAST ne sauve pas la construction — il la fait échouer
 * autrement (« cannot cast type bytea to date »). Seule la construction
 * dynamique, qui n'envoie aucun paramètre nul, tient sur les deux cas.
 */
@Repository
public interface CahierSeanceRepository
        extends TenantAwareRepository<CahierSeance, Long>, JpaSpecificationExecutor<CahierSeance> {

    Optional<CahierSeance> findByTenantIdAndSeanceAppel_Id(String tenantId, Long seanceAppelId);

    Optional<CahierSeance> findByTenantIdAndId(String tenantId, Long id);

    /**
     * Corpus d'indexation sémantique : les séances effectivement renseignées.
     *
     * <p>Deux partis pris. D'abord les séances vides sont écartées — un cahier
     * ouvert puis abandonné ne porte aucun texte, et l'indexer ne ferait que
     * diluer la recherche avec des documents sans contenu. Ensuite
     * {@code enseignantId} est nullable : null signifie « tout l'établissement »,
     * ce qui sert le pilotage pédagogique du directeur, tandis qu'une valeur
     * restreint aux séances d'un enseignant. C'est le service, et non l'appelant,
     * qui décide laquelle des deux formes s'applique.</p>
     *
     * <p>{@code depuis} est OBLIGATOIRE : voir la note du type sur les
     * paramètres nullables. Cette requête garde un JPQL parce qu'elle projette
     * directement dans {@link CahierCorpusRow} — ce qu'une Specification ne sait
     * pas faire — et c'est le service qui substitue une borne plancher quand
     * l'appelant ne demande aucune limite. Ne pas réintroduire le test de
     * nullité ici : l'erreur ne se voit qu'à l'exécution.</p>
     */
    @Query("""
            SELECT new tn.wtm.school.absence.repository.projection.CahierCorpusRow(
                       c.id, s.id, c.enseignantId,
                       s.groupeClasseId, s.matiereId,
                       s.dateSeance, s.anneeAcademique,
                       c.sujet, c.chapitre, c.activites,
                       c.remarques, c.travailDemande, c.dateEcheance)
            FROM CahierSeance c
              JOIN c.seanceAppel s
            WHERE c.tenantId = :tenantId
              AND (:enseignantId IS NULL OR c.enseignantId = :enseignantId)
              AND s.dateSeance >= :depuis
              AND (
                    (c.sujet IS NOT NULL AND TRIM(c.sujet) <> '')
                 OR (c.chapitre IS NOT NULL AND TRIM(c.chapitre) <> '')
                 OR (c.activites IS NOT NULL AND TRIM(c.activites) <> '')
                 OR (c.travailDemande IS NOT NULL AND TRIM(c.travailDemande) <> '')
              )
            ORDER BY s.dateSeance DESC
            """)
    List<CahierCorpusRow> rechercherPourCorpus(
            @Param("tenantId") String tenantId,
            @Param("enseignantId") Long enseignantId,
            @Param("depuis") LocalDate depuis,
            Pageable pageable);
}
