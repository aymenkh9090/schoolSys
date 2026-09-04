package tn.wtm.school.absence.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.absence.enums.RaisonVerrouillage;
import tn.wtm.school.absence.repository.SeanceAppelRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PlanificateurVerrouillageSeance {

    private final SeanceAppelRepository seanceAppelRepository;

    @Scheduled(fixedRateString = "${absence.planificateur.intervalle-ms:900000}")
    @Transactional
    public void verrouillerSeancesExpirees() {
        List<SeanceAppel> seancesExpirees = seanceAppelRepository
                .findSeancesExpiresNonVerrouillees(LocalDateTime.now());

        for (SeanceAppel seance : seancesExpirees) {
            seance.setEstVerrouille(true);
            seance.setVerrouillageAt(LocalDateTime.now());
            seance.setRaisonVerrouillage(RaisonVerrouillage.FIN_SEANCE_AUTOMATIQUE);
            seanceAppelRepository.save(seance);
        }
    }
}
