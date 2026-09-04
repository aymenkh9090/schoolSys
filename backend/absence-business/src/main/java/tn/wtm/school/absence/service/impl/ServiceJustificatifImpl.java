package tn.wtm.school.absence.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.absence.dto.requete.SoumissionJustificatifRequete;
import tn.wtm.school.absence.dto.requete.TraitementJustificatifRequete;
import tn.wtm.school.absence.dto.reponse.JustificatifReponse;
import tn.wtm.school.absence.entity.JustificatifAbsence;
import tn.wtm.school.absence.entity.LigneAppel;
import tn.wtm.school.absence.enums.StatutJustificatif;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.absence.mapper.JustificatifMapper;
import tn.wtm.school.absence.repository.JustificatifAbsenceRepository;
import tn.wtm.school.absence.repository.LigneAppelRepository;
import tn.wtm.school.absence.service.ServiceJustificatif;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceJustificatifImpl extends TenantService implements ServiceJustificatif {

    private final JustificatifAbsenceRepository justificatifRepository;
    private final LigneAppelRepository ligneAppelRepository;
    private final JustificatifMapper justificatifMapper;

    @Override
    @Transactional
    public JustificatifReponse soumettre(SoumissionJustificatifRequete requete) {
        String tenantId = currentTenant();

        LigneAppel ligne = ligneAppelRepository.findByTenantIdAndId(tenantId, requete.getLigneAppelId())
                .orElseThrow(() -> new ResourceNotFoundException("Ligne d'appel introuvable : " + requete.getLigneAppelId()));

        if (ligne.getStatut() != StatutPresence.ABSENT) {
            throw new BusinessException("Un justificatif ne peut être soumis que pour une absence (statut = ABSENT)");
        }

        // L'auteur vient du compte connecté : sans fiche utilisateur rattachée au
        // tenant, le dépôt ne serait imputable à personne.
        if (requete.getSoumisParId() == null) {
            throw new BadRequestException(
                    "Aucun compte utilisateur de l'établissement n'est associé à la session : dépôt impossible");
        }

        JustificatifAbsence justificatif = JustificatifAbsence.builder()
                .ligneAppel(ligne)
                .eleveId(ligne.getEleveId())
                .soumisAt(LocalDateTime.now())
                .soumisParId(requete.getSoumisParId())
                .typeDocument(requete.getTypeDocument())
                .referenceDocument(requete.getReferenceDocument())
                .statut(StatutJustificatif.EN_ATTENTE)
                .build();

        return justificatifMapper.toResponse(justificatifRepository.save(justificatif));
    }

    @Override
    @Transactional
    public JustificatifReponse approuver(Long justificatifId, TraitementJustificatifRequete requete) {
        validerDecision(requete, StatutJustificatif.VALIDE);

        JustificatifAbsence justificatif = trouverJustificatif(justificatifId);
        verifierStatutTerminal(justificatif);

        justificatif.setStatut(StatutJustificatif.VALIDE);
        justificatif.setTraiteAt(LocalDateTime.now());
        justificatif.setTraiteParId(requete.getTraiteParId());
        justificatif.setNotesAdmin(requete.getNotesAdmin());

        LigneAppel ligne = justificatif.getLigneAppel();
        ligne.setEstJustifie(true);
        ligneAppelRepository.save(ligne);

        return justificatifMapper.toResponse(justificatifRepository.save(justificatif));
    }

    @Override
    @Transactional
    public JustificatifReponse refuser(Long justificatifId, TraitementJustificatifRequete requete) {
        validerDecision(requete, StatutJustificatif.REFUSE);

        JustificatifAbsence justificatif = trouverJustificatif(justificatifId);
        verifierStatutTerminal(justificatif);

        justificatif.setStatut(StatutJustificatif.REFUSE);
        justificatif.setTraiteAt(LocalDateTime.now());
        justificatif.setTraiteParId(requete.getTraiteParId());
        justificatif.setNotesAdmin(requete.getNotesAdmin());

        return justificatifMapper.toResponse(justificatifRepository.save(justificatif));
    }

    @Override
    public Page<JustificatifReponse> lister(Long eleveId, StatutJustificatif statut,
                                             LocalDateTime debut, LocalDateTime fin, Pageable pageable) {
        String tenantId = currentTenant();

        // Seuls les critères renseignés deviennent des prédicats : un filtre
        // absent n'est pas envoyé comme paramètre nul (que PostgreSQL refuse).
        Specification<JustificatifAbsence> criteres = (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();
            predicats.add(cb.equal(racine.get("tenantId"), tenantId));
            if (eleveId != null) predicats.add(cb.equal(racine.get("eleveId"), eleveId));
            if (statut != null) predicats.add(cb.equal(racine.get("statut"), statut));
            if (debut != null) predicats.add(cb.greaterThanOrEqualTo(racine.get("soumisAt"), debut));
            if (fin != null) predicats.add(cb.lessThanOrEqualTo(racine.get("soumisAt"), fin));
            return cb.and(predicats.toArray(new Predicate[0]));
        };

        // Le plus récent d'abord, sauf tri explicite de l'appelant.
        Pageable trie = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "soumisAt"));

        return justificatifRepository.findAll(criteres, trie).map(justificatifMapper::toResponse);
    }

    // ── Privé ────────────────────────────────────────────────────────────────────

    private JustificatifAbsence trouverJustificatif(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant du justificatif est obligatoire");
        }
        return justificatifRepository.findByTenantIdAndId(currentTenant(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Justificatif introuvable : " + id));
    }

    private void verifierStatutTerminal(JustificatifAbsence justificatif) {
        if (justificatif.getStatut() != StatutJustificatif.EN_ATTENTE) {
            throw new BusinessException("Ce justificatif a déjà été traité (statut : " + justificatif.getStatut() + ")");
        }
    }

    private void validerDecision(TraitementJustificatifRequete requete, StatutJustificatif attendu) {
        if (requete.getDecision() == null) {
            throw new BadRequestException("La décision est obligatoire");
        }
        if (requete.getDecision() == StatutJustificatif.EN_ATTENTE) {
            throw new BadRequestException("EN_ATTENTE n'est pas une décision valide");
        }
        if (requete.getDecision() != attendu) {
            throw new BadRequestException("Décision incohérente pour cette opération");
        }
    }
}
