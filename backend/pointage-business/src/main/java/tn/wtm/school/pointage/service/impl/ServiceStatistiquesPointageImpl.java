package tn.wtm.school.pointage.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.pointage.dto.reponse.StatistiquesPresenceReponse;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.repository.PresencePersonnelRepository;
import tn.wtm.school.pointage.service.ServiceStatistiquesPointage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ServiceStatistiquesPointageImpl extends TenantService implements ServiceStatistiquesPointage {

    private final PresencePersonnelRepository presenceRepository;

    @Override
    public StatistiquesPresenceReponse obtenirStatistiquesPersonnel(Long membrePersonnelId, LocalDate debut, LocalDate fin) {
        String tenantId = currentTenant();

        long joursPresent = presenceRepository.compterParMembreEtStatutEtPeriode(tenantId, membrePersonnelId, debut, fin, StatutPresencePersonnel.PRESENT);
        long joursAbsent = presenceRepository.compterParMembreEtStatutEtPeriode(tenantId, membrePersonnelId, debut, fin, StatutPresencePersonnel.ABSENT)
                + presenceRepository.compterParMembreEtStatutEtPeriode(tenantId, membrePersonnelId, debut, fin, StatutPresencePersonnel.ABSENCE_NON_JUSTIFIEE)
                + presenceRepository.compterParMembreEtStatutEtPeriode(tenantId, membrePersonnelId, debut, fin, StatutPresencePersonnel.ABSENCE_JUSTIFIEE);
        long joursEnRetard = presenceRepository.compterParMembreEtStatutEtPeriode(tenantId, membrePersonnelId, debut, fin, StatutPresencePersonnel.EN_RETARD);
        long joursEnConge = presenceRepository.compterParMembreEtStatutEtPeriode(tenantId, membrePersonnelId, debut, fin, StatutPresencePersonnel.EN_CONGE);

        long totalJours = ChronoUnit.DAYS.between(debut, fin) + 1;
        double taux = totalJours > 0 ? (joursPresent * 100.0) / totalJours : 0.0;

        String periode = debut.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));

        return StatistiquesPresenceReponse.builder()
                .membrePersonnelId(membrePersonnelId)
                .periode(periode)
                .joursPresent((int) joursPresent)
                .joursAbsent((int) joursAbsent)
                .joursEnRetard((int) joursEnRetard)
                .joursEnConge((int) joursEnConge)
                .tauxPresence(taux)
                .build();
    }

    @Override
    public List<StatistiquesPresenceReponse> obtenirStatistiquesParTypePersonnel(TypePersonnel type, LocalDate debut, LocalDate fin) {
        String tenantId = currentTenant();
        return presenceRepository.findByTenantIdAndDatePointage(tenantId, debut)
                .stream()
                .filter(p -> p.getTypePersonnel() == type)
                .map(p -> obtenirStatistiquesPersonnel(p.getMembrePersonnelId(), debut, fin))
                .toList();
    }
}
