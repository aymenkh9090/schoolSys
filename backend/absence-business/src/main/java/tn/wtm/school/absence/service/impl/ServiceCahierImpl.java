package tn.wtm.school.absence.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.absence.dto.requete.EnregistrementCahierRequete;
import tn.wtm.school.absence.dto.reponse.CahierCorpusReponse;
import tn.wtm.school.absence.dto.reponse.CahierSeanceReponse;
import tn.wtm.school.absence.entity.CahierSeance;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.absence.mapper.CahierSeanceMapper;
import tn.wtm.school.absence.port.PortContexteScolaire;
import tn.wtm.school.absence.repository.CahierSeanceRepository;
import tn.wtm.school.absence.repository.projection.CahierCorpusRow;
import tn.wtm.school.absence.repository.SeanceAppelRepository;
import tn.wtm.school.absence.service.ServiceCahier;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCahierImpl extends TenantService implements ServiceCahier {

    private final CahierSeanceRepository cahierRepository;
    private final SeanceAppelRepository seanceAppelRepository;
    private final CahierSeanceMapper cahierMapper;
    private final PortContexteScolaire portContexteScolaire;

    /**
     * Plafond dur du corpus, quelle que soit la limite demandée.
     *
     * <p>L'appelant est un assistant qui va vectoriser chaque séance : au-delà
     * de quelques milliers de documents, le coût d'indexation dépasse largement
     * le gain de pertinence, et une requête sans borne finirait un jour par
     * ramener trois ans d'historique d'un seul coup.</p>
     */
    private static final int CORPUS_MAX = 2_000;

    @Override
    @Transactional
    public CahierSeanceReponse enregistrer(Long seanceAppelId, EnregistrementCahierRequete requete) {
        if (seanceAppelId == null) {
            throw new BadRequestException("L'identifiant de la séance est obligatoire");
        }
        String tenantId = currentTenant();
        SeanceAppel seance = trouverSeance(tenantId, seanceAppelId);

        CahierSeance cahier = cahierRepository.findByTenantIdAndSeanceAppel_Id(tenantId, seanceAppelId)
                .orElse(null);

        if (cahier == null) {
            cahier = CahierSeance.builder()
                    .seanceAppel(seance)
                    .enseignantId(seance.getEnseignantId())
                    .estVerrouille(false)
                    .build();
        } else if (Boolean.TRUE.equals(cahier.getEstVerrouille())) {
            throw new BusinessException("Le cahier de séance est verrouillé, modification impossible");
        }

        cahierMapper.updateFromRequete(requete, cahier);
        return cahierMapper.toResponse(cahierRepository.save(cahier));
    }

    @Override
    @Transactional
    public void verrouiller(Long seanceAppelId) {
        if (seanceAppelId == null) {
            throw new BadRequestException("L'identifiant de la séance est obligatoire");
        }
        String tenantId = currentTenant();
        CahierSeance cahier = cahierRepository.findByTenantIdAndSeanceAppel_Id(tenantId, seanceAppelId)
                .orElseThrow(() -> new ResourceNotFoundException("Cahier de séance introuvable pour la séance : " + seanceAppelId));

        cahier.setEstVerrouille(true);
        cahier.setVerrouillageAt(LocalDateTime.now());
        cahierRepository.save(cahier);
    }

    @Override
    public CahierSeanceReponse recuperer(Long seanceAppelId) {
        if (seanceAppelId == null) {
            throw new BadRequestException("L'identifiant de la séance est obligatoire");
        }
        return cahierMapper.toResponse(
                cahierRepository.findByTenantIdAndSeanceAppel_Id(currentTenant(), seanceAppelId)
                        .orElseThrow(() -> new ResourceNotFoundException("Cahier de séance introuvable pour la séance : " + seanceAppelId)));
    }

    @Override
    public Page<CahierSeanceReponse> listerParEnseignant(Long enseignantId, LocalDateTime debut,
                                                          LocalDateTime fin, Pageable pageable) {
        if (enseignantId == null) {
            throw new BadRequestException("L'identifiant de l'enseignant est obligatoire");
        }
        String tenantId = currentTenant();

        // Seuls les critères renseignés deviennent des prédicats : un filtre
        // absent n'est pas envoyé comme paramètre nul (que PostgreSQL refuse).
        Specification<CahierSeance> criteres = (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();
            predicats.add(cb.equal(racine.get("tenantId"), tenantId));
            predicats.add(cb.equal(racine.get("enseignantId"), enseignantId));
            if (debut != null) {
                predicats.add(cb.greaterThanOrEqualTo(
                        racine.get("seanceAppel").get("ouvertureAt"), debut));
            }
            if (fin != null) {
                predicats.add(cb.lessThanOrEqualTo(
                        racine.get("seanceAppel").get("ouvertureAt"), fin));
            }
            return cb.and(predicats.toArray(new Predicate[0]));
        };

        // La séance la plus récente d'abord, sauf tri explicite de l'appelant :
        // c'est l'ordre que portait le JPQL remplacé, et celui qu'attend
        // l'historique affiché à l'enseignant.
        Pageable trie = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                 Sort.by(Sort.Direction.DESC, "seanceAppel.ouvertureAt"));

        return cahierRepository.findAll(criteres, trie).map(cahierMapper::toResponse);
    }


    @Override
    public List<CahierCorpusReponse> construireCorpus(LocalDate depuis, int limite) {
        String tenantId = currentTenant();

        // Périmètre déduit du compte, jamais reçu en paramètre : enseignant → ses
        // séances, administrateur → tout l'établissement. Voir ServiceCahier.
        Long enseignantId = portContexteScolaire.idEnseignantCourant().orElse(null);

        int taille = Math.min(Math.max(limite, 1), CORPUS_MAX);

        // « Aucune borne » se traduit par une borne plancher plutôt que par un
        // null : la requête refuse un paramètre date nul (voir son javadoc), et
        // aucun cahier de séance n'est antérieur à 1970.
        LocalDate borne = depuis != null ? depuis : LocalDate.EPOCH;

        List<CahierCorpusRow> lignes = cahierRepository.rechercherPourCorpus(
                tenantId, enseignantId, borne, PageRequest.of(0, taille));

        if (lignes.isEmpty()) {
            return List.of();
        }

        // Trois requêtes de libellés pour tout le corpus, et non trois par séance :
        // sur 2 000 séances, la version naïve ferait 6 000 allers-retours.
        Map<Long, String> enseignants = portContexteScolaire.nomsEnseignants(
                tenantId, idsDistincts(lignes, CahierCorpusRow::enseignantId));
        Map<Long, String> classes = portContexteScolaire.codesClasses(
                tenantId, idsDistincts(lignes, CahierCorpusRow::groupeClasseId));
        Map<Long, String> matieres = portContexteScolaire.libellesMatieres(
                tenantId, idsDistincts(lignes, CahierCorpusRow::matiereId));

        return lignes.stream()
                .map(l -> CahierCorpusReponse.builder()
                        .id(l.id())
                        .seanceAppelId(l.seanceAppelId())
                        .enseignantId(l.enseignantId())
                        .enseignantNom(enseignants.get(l.enseignantId()))
                        .groupeClasseId(l.groupeClasseId())
                        .classeCode(classes.get(l.groupeClasseId()))
                        .matiereId(l.matiereId())
                        .matiereLibelle(matieres.get(l.matiereId()))
                        .dateSeance(l.dateSeance())
                        .anneeAcademique(l.anneeAcademique())
                        .sujet(l.sujet())
                        .chapitre(l.chapitre())
                        .activites(l.activites())
                        .remarques(l.remarques())
                        .travailDemande(l.travailDemande())
                        .dateEcheance(l.dateEcheance())
                        .build())
                .toList();
    }

    private static Set<Long> idsDistincts(Collection<CahierCorpusRow> lignes,
                                          Function<CahierCorpusRow, Long> extracteur) {
        return lignes.stream()
                .map(extracteur)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // ── Privé ────────────────────────────────────────────────────────────────────

    private SeanceAppel trouverSeance(String tenantId, Long seanceAppelId) {
        return seanceAppelRepository.findByTenantIdAndId(tenantId, seanceAppelId)
                .orElseThrow(() -> new ResourceNotFoundException("Séance d'appel introuvable : " + seanceAppelId));
    }
}
