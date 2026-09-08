package tn.wtm.school.pointage.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.pointage.dto.reponse.JustificatifPointageReponse;
import tn.wtm.school.pointage.dto.requete.SoumissionJustificatifPointageRequete;
import tn.wtm.school.pointage.dto.requete.TraitementJustificatifPointageRequete;
import tn.wtm.school.pointage.entity.JustificatifPointage;
import tn.wtm.school.pointage.entity.PresencePersonnel;
import tn.wtm.school.pointage.enums.StatutJustificatifPointage;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.mapper.JustificatifPointageMapper;
import tn.wtm.school.pointage.port.PortMembrePersonnel;
import tn.wtm.school.pointage.repository.JustificatifPointageRepository;
import tn.wtm.school.pointage.repository.PresencePersonnelRepository;
import tn.wtm.school.pointage.service.ServiceJustificatifPointage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ServiceJustificatifPointageImpl extends TenantService implements ServiceJustificatifPointage {

    private final JustificatifPointageRepository justificatifRepository;
    private final PresencePersonnelRepository presenceRepository;
    private final JustificatifPointageMapper justificatifMapper;
    private final PortMembrePersonnel portMembrePersonnel;

    @Override
    @Transactional
    public JustificatifPointageReponse soumettreJustificatif(SoumissionJustificatifPointageRequete requete) {
        String tenantId = currentTenant();

        PresencePersonnel presence = presenceRepository.findByTenantIdAndId(tenantId, requete.getPresencePersonnelId())
                .orElseThrow(() -> new ResourceNotFoundException("Pointage introuvable : " + requete.getPresencePersonnelId()));

        Set<StatutPresencePersonnel> statutsAcceptes = Set.of(
                StatutPresencePersonnel.ABSENT, StatutPresencePersonnel.ABSENCE_NON_JUSTIFIEE);
        if (!statutsAcceptes.contains(presence.getStatut())) {
            throw new BusinessException("Le justificatif ne peut être soumis que pour une absence");
        }

        JustificatifPointage justificatif = JustificatifPointage.builder()
                .membrePersonnelId(presence.getMembrePersonnelId())
                .presencePersonnelId(presence.getId())
                .typeDocument(requete.getTypeDocument())
                .description(requete.getDescription())
                .cheminDocument(requete.getCheminDocument())
                .commentaireAdmin(requete.getCommentaireAdmin())
                .statut(StatutJustificatifPointage.EN_ATTENTE)
                .soumisA(LocalDateTime.now())
                .build();
        justificatif.setTenantId(tenantId);

        return justificatifMapper.toResponse(justificatifRepository.save(justificatif));
    }

    @Override
    @Transactional
    public JustificatifPointageReponse traiterJustificatif(Long justificatifId, TraitementJustificatifPointageRequete requete) {
        String tenantId = currentTenant();

        if (!requete.getApprouve() && (requete.getMotifRejet() == null || requete.getMotifRejet().isBlank())) {
            throw new BadRequestException("Le motif de rejet est obligatoire");
        }

        JustificatifPointage justificatif = justificatifRepository.findByTenantIdAndId(tenantId, justificatifId)
                .orElseThrow(() -> new ResourceNotFoundException("Justificatif introuvable : " + justificatifId));

        if (justificatif.getStatut() != StatutJustificatifPointage.EN_ATTENTE) {
            throw new BusinessException("Ce justificatif a déjà été traité");
        }

        if (requete.getApprouve()) {
            justificatif.setStatut(StatutJustificatifPointage.APPROUVE);

            presenceRepository.findByTenantIdAndId(tenantId, justificatif.getPresencePersonnelId())
                    .ifPresent(p -> {
                        p.setStatut(StatutPresencePersonnel.ABSENCE_JUSTIFIEE);
                        presenceRepository.save(p);
                    });
        } else {
            justificatif.setStatut(StatutJustificatifPointage.REJETE);
            justificatif.setCommentaireAdmin(requete.getMotifRejet());
        }

        justificatif.setTraitePar(requete.getTraitePar());
        justificatif.setTraiteA(LocalDateTime.now());

        return justificatifMapper.toResponse(justificatifRepository.save(justificatif));
    }

    @Override
    public List<JustificatifPointageReponse> listerJustificatifsEnAttente() {
        String tenantId = currentTenant();
        return justificatifRepository.findByTenantIdAndStatut(tenantId, StatutJustificatifPointage.EN_ATTENTE)
                .stream()
                .map(j -> enrichir(tenantId, justificatifMapper.toResponse(j), j.getPresencePersonnelId()))
                .toList();
    }

    @Override
    public JustificatifPointageReponse obtenirJustificatifParPresence(Long presenceId) {
        String tenantId = currentTenant();
        return justificatifRepository.findByTenantIdAndPresencePersonnelId(tenantId, presenceId)
                .map(j -> enrichir(tenantId, justificatifMapper.toResponse(j), presenceId))
                .orElseThrow(() -> new ResourceNotFoundException("Justificatif introuvable pour le pointage : " + presenceId));
    }

    /**
     * Ajoute le contexte du pointage justifié (nom du membre, type, jour, créneau).
     * Sans lui, un justificatif ne porte que des identifiants — et le nom d'un
     * membre ne peut même pas se déduire du seul {@code membrePersonnelId}, dont
     * la signification dépend du type de personnel.
     */
    private JustificatifPointageReponse enrichir(String tenantId, JustificatifPointageReponse reponse, Long presenceId) {
        presenceRepository.findByTenantIdAndId(tenantId, presenceId).ifPresent(presence -> {
            reponse.setTypePersonnel(presence.getTypePersonnel());
            reponse.setDatePointage(presence.getDatePointage());
            reponse.setPeriode(presence.getPeriode());
            reponse.setNomMembre(portMembrePersonnel
                    .nomsParIds(tenantId, presence.getTypePersonnel(), List.of(presence.getMembrePersonnelId()))
                    .get(presence.getMembrePersonnelId()));
        });
        return reponse;
    }
}
