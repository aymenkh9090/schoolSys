package tn.wtm.school.pointage.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.dto.requete.MiseAJourHeuresRequete;
import tn.wtm.school.pointage.entity.SuiviHeuresEnseignant;
import tn.wtm.school.pointage.mapper.SuiviHeuresMapper;
import tn.wtm.school.pointage.repository.SuiviHeuresEnseignantRepository;
import tn.wtm.school.pointage.service.ServiceSuiviHeures;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ServiceSuiviHeuresImpl extends TenantService implements ServiceSuiviHeures {

    private final SuiviHeuresEnseignantRepository suiviRepository;
    private final SuiviHeuresMapper suiviMapper;

    @Override
    @Transactional
    public SuiviHeuresEnseignantReponse mettreAJourHeures(MiseAJourHeuresRequete requete) {
        String tenantId = currentTenant();

        SuiviHeuresEnseignant suivi = suiviRepository
                .findByTenantIdAndEnseignantIdAndNumeroSemaineAndAnneeAcademique(
                        tenantId, requete.getEnseignantId(), requete.getNumeroSemaine(), requete.getAnneeAcademique())
                .orElse(new SuiviHeuresEnseignant());

        suivi.setTenantId(tenantId);
        suivi.setEnseignantId(requete.getEnseignantId());
        suivi.setNumeroSemaine(requete.getNumeroSemaine());
        suivi.setAnneeAcademique(requete.getAnneeAcademique());
        suivi.setHeuresPrevues(requete.getHeuresPrevues());
        suivi.setHeuresRealisees(requete.getHeuresRealisees());

        double manquees = Math.max(0.0, requete.getHeuresPrevues() - requete.getHeuresRealisees());
        double taux = requete.getHeuresPrevues() > 0
                ? Math.min(100.0, (requete.getHeuresRealisees() / requete.getHeuresPrevues()) * 100)
                : 0.0;

        suivi.setHeuresManquees(manquees);
        suivi.setTauxPresence(taux);
        suivi.setNotes(requete.getNotes());

        return suiviMapper.toResponse(suiviRepository.save(suivi));
    }

    @Override
    public SuiviHeuresEnseignantReponse obtenirResumeSemaine(Long enseignantId, int numeroSemaine, String anneeAcademique) {
        String tenantId = currentTenant();
        return suiviRepository.findByTenantIdAndEnseignantIdAndNumeroSemaineAndAnneeAcademique(
                        tenantId, enseignantId, numeroSemaine, anneeAcademique)
                .map(suiviMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Suivi introuvable pour semaine " + numeroSemaine + "/" + anneeAcademique));
    }

    @Override
    public List<SuiviHeuresEnseignantReponse> obtenirResumeAnnuel(Long enseignantId, String anneeAcademique) {
        String tenantId = currentTenant();
        return suiviRepository.findByTenantIdAndEnseignantIdAndAnneeAcademique(tenantId, enseignantId, anneeAcademique)
                .stream()
                .map(suiviMapper::toResponse)
                .toList();
    }

    @Override
    public void synchroniserDepuisPlanning(Long enseignantId, int numeroSemaine, String anneeAcademique, double heuresPrevues) {
        throw new UnsupportedOperationException(
                "TODO: Intégration planning-module non encore implémentée. " +
                "Sera développée lors de l'intégration complète du module planning.");
    }
}
