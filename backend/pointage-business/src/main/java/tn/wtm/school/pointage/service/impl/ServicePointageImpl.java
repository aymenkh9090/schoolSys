package tn.wtm.school.pointage.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.reponse.RapportJournalierReponse;
import tn.wtm.school.pointage.dto.reponse.ResultatPointageMasseReponse;
import tn.wtm.school.pointage.dto.requete.PointageMasseRequete;
import tn.wtm.school.pointage.dto.requete.PointageRequete;
import tn.wtm.school.pointage.entity.PresencePersonnel;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.mapper.JustificatifPointageMapper;
import tn.wtm.school.pointage.mapper.PresencePersonnelMapper;
import tn.wtm.school.pointage.port.PortMembrePersonnel;
import tn.wtm.school.pointage.repository.JustificatifPointageRepository;
import tn.wtm.school.pointage.repository.PresencePersonnelRepository;
import tn.wtm.school.pointage.service.ServicePointage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ServicePointageImpl extends TenantService implements ServicePointage {

    private final PresencePersonnelRepository presenceRepository;
    private final JustificatifPointageRepository justificatifRepository;
    private final PresencePersonnelMapper presenceMapper;
    private final JustificatifPointageMapper justificatifMapper;
    private final PortMembrePersonnel portMembrePersonnel;

    @Override
    @Transactional
    public PresencePersonnelReponse pointer(PointageRequete requete) {
        String tenantId = currentTenant();

        if (presenceRepository.existsByTenantIdAndMembrePersonnelIdAndDatePointageAndPeriode(
                tenantId, requete.getMembrePersonnelId(), requete.getDatePointage(), requete.getPeriode())) {
            throw new BusinessException("Un pointage existe déjà pour ce membre à cette date et ce créneau");
        }

        if (requete.getStatut() == StatutPresencePersonnel.EN_RETARD
                && (requete.getMinutesRetard() == null || requete.getMinutesRetard() <= 0)) {
            throw new BadRequestException("Le nombre de minutes de retard est obligatoire pour un retard");
        }

        PresencePersonnel presence = presenceMapper.toEntity(requete);
        presence.setTenantId(tenantId);
        presence.setSaisiPar(utilisateurCourant());
        presence.setSaisiA(LocalDateTime.now());

        return enrichir(presenceMapper.toResponse(presenceRepository.save(presence)), tenantId);
    }

    @Override
    @Transactional
    public ResultatPointageMasseReponse pointerEnMasse(PointageMasseRequete requete) {
        List<PresencePersonnelReponse> resultats = new ArrayList<>();
        List<ResultatPointageMasseReponse.EchecPointageItem> echecs = new ArrayList<>();

        for (PointageRequete p : requete.getPointages()) {
            try {
                resultats.add(pointer(p));
            } catch (Exception e) {
                echecs.add(new ResultatPointageMasseReponse.EchecPointageItem(p.getMembrePersonnelId(), e.getMessage()));
                log.warn("[Pointage] Échec pointage membre={}: {}", p.getMembrePersonnelId(), e.getMessage());
            }
        }

        return ResultatPointageMasseReponse.builder()
                .total(requete.getPointages().size())
                .reussis(resultats.size())
                .echoues(echecs.size())
                .resultats(resultats)
                .echecs(echecs)
                .build();
    }

    @Override
    @Transactional
    public PresencePersonnelReponse modifierPointage(Long presenceId, PointageRequete requete) {
        String tenantId = currentTenant();
        PresencePersonnel presence = trouverPresence(tenantId, presenceId);

        if (requete.getStatut() == StatutPresencePersonnel.EN_RETARD
                && (requete.getMinutesRetard() == null || requete.getMinutesRetard() <= 0)) {
            throw new BadRequestException("Le nombre de minutes de retard est obligatoire pour un retard");
        }

        presence.setStatut(requete.getStatut());
        presence.setHeureArrivee(requete.getHeureArrivee());
        presence.setHeureDepart(requete.getHeureDepart());
        presence.setMinutesRetard(requete.getMinutesRetard());
        presence.setNote(requete.getNote());
        presence.setSaisiPar(utilisateurCourant());
        presence.setSaisiA(LocalDateTime.now());

        return enrichir(presenceMapper.toResponse(presenceRepository.save(presence)), tenantId);
    }

    @Override
    public RapportJournalierReponse obtenirRapportJournalier(LocalDate date) {
        String tenantId = currentTenant();
        List<PresencePersonnel> enregistrements = presenceRepository.findByTenantIdAndDatePointage(tenantId, date);

        List<PresencePersonnelReponse> reponses = enrichirEnLot(enregistrements, tenantId);

        int presents = (int) enregistrements.stream().filter(p -> p.getStatut() == StatutPresencePersonnel.PRESENT).count();
        int absents = (int) enregistrements.stream().filter(p -> p.getStatut() == StatutPresencePersonnel.ABSENT).count();
        int enRetard = (int) enregistrements.stream().filter(p -> p.getStatut() == StatutPresencePersonnel.EN_RETARD).count();
        int enConge = (int) enregistrements.stream().filter(p -> p.getStatut() == StatutPresencePersonnel.EN_CONGE).count();
        int absJust = (int) enregistrements.stream().filter(p -> p.getStatut() == StatutPresencePersonnel.ABSENCE_JUSTIFIEE).count();
        int absNonJust = (int) enregistrements.stream().filter(p -> p.getStatut() == StatutPresencePersonnel.ABSENCE_NON_JUSTIFIEE).count();

        return RapportJournalierReponse.builder()
                .date(date)
                .totalPresents(presents)
                .totalAbsents(absents)
                .totalEnRetard(enRetard)
                .totalEnConge(enConge)
                .totalAbsencesJustifiees(absJust)
                .totalAbsencesNonJustifiees(absNonJust)
                .enregistrements(reponses)
                .build();
    }

    @Override
    public List<PresencePersonnelReponse> obtenirHistoriquePersonnel(Long membrePersonnelId, LocalDate debut, LocalDate fin) {
        String tenantId = currentTenant();
        return enrichirEnLot(
                presenceRepository.findByTenantIdAndMembrePersonnelIdAndDatePointageBetween(
                        tenantId, membrePersonnelId, debut, fin),
                tenantId);
    }

    private PresencePersonnel trouverPresence(String tenantId, Long presenceId) {
        return presenceRepository.findByTenantIdAndId(tenantId, presenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Pointage introuvable : " + presenceId));
    }

    /** Username du JWT (rempli par TenantFilter) ; "system" hors requête HTTP (jobs, seeders). */
    private String utilisateurCourant() {
        String username = TenantContext.getUsername();
        return (username == null || username.isBlank()) ? "system" : username;
    }

    private PresencePersonnelReponse enrichir(PresencePersonnelReponse reponse, String tenantId) {
        justificatifRepository.findByTenantIdAndPresencePersonnelId(tenantId, reponse.getId())
                .ifPresent(j -> reponse.setJustificatif(justificatifMapper.toResponse(j)));
        portMembrePersonnel
                .nomsParIds(tenantId, reponse.getTypePersonnel(), List.of(reponse.getMembrePersonnelId()))
                .forEach((id, nom) -> reponse.setNomMembre(nom));
        return reponse;
    }

    /**
     * Mappe une liste d'enregistrements en résolvant les noms par lot — une requête
     * par type de personnel présent, plutôt qu'une par ligne.
     */
    private List<PresencePersonnelReponse> enrichirEnLot(List<PresencePersonnel> enregistrements, String tenantId) {
        Map<TypePersonnel, Set<Long>> idsParType = enregistrements.stream()
                .collect(Collectors.groupingBy(
                        PresencePersonnel::getTypePersonnel,
                        Collectors.mapping(PresencePersonnel::getMembrePersonnelId, Collectors.toSet())));

        Map<TypePersonnel, Map<Long, String>> nomsParType = new HashMap<>();
        idsParType.forEach((type, ids) -> nomsParType.put(type, portMembrePersonnel.nomsParIds(tenantId, type, ids)));

        return enregistrements.stream()
                .map(p -> {
                    PresencePersonnelReponse reponse = presenceMapper.toResponse(p);
                    justificatifRepository.findByTenantIdAndPresencePersonnelId(tenantId, reponse.getId())
                            .ifPresent(j -> reponse.setJustificatif(justificatifMapper.toResponse(j)));
                    reponse.setNomMembre(nomsParType
                            .getOrDefault(p.getTypePersonnel(), Map.of())
                            .get(p.getMembrePersonnelId()));
                    return reponse;
                })
                .toList();
    }
}
