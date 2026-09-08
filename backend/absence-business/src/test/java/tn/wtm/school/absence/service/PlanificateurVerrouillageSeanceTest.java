package tn.wtm.school.absence.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.absence.enums.RaisonVerrouillage;
import tn.wtm.school.absence.repository.SeanceAppelRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fermeture automatique des appels dont l'heure est passée.
 *
 * <p>C'est le seul écrivain du système qui n'a pas d'utilisateur derrière lui :
 * si la raison de verrouillage n'était pas posée, plus rien ne distinguerait
 * une séance fermée par le planificateur d'une séance fermée à la main par
 * l'administration — et c'est précisément cette distinction qu'un enseignant
 * conteste quand il découvre son appel clos.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlanificateurVerrouillageSeanceTest {

    @Mock private SeanceAppelRepository seanceAppelRepository;

    @Test
    void verrouilleChaqueSeanceExpireeEnDisantQueLaFermetureEstAutomatique() {
        SeanceAppel premiere = SeanceAppel.builder().id(1L).estVerrouille(false).build();
        SeanceAppel seconde = SeanceAppel.builder().id(2L).estVerrouille(false).build();
        when(seanceAppelRepository.findSeancesExpiresNonVerrouillees(any(LocalDateTime.class)))
                .thenReturn(List.of(premiere, seconde));

        new PlanificateurVerrouillageSeance(seanceAppelRepository).verrouillerSeancesExpirees();

        assertThat(List.of(premiere, seconde)).allSatisfy(seance -> {
            assertThat(seance.getEstVerrouille()).isTrue();
            assertThat(seance.getVerrouillageAt()).isNotNull();
            assertThat(seance.getRaisonVerrouillage()).isEqualTo(RaisonVerrouillage.FIN_SEANCE_AUTOMATIQUE);
        });
        verify(seanceAppelRepository).save(premiere);
        verify(seanceAppelRepository).save(seconde);
    }

    @Test
    void nEcritRienQuandAucuneSeanceNestExpiree() {
        when(seanceAppelRepository.findSeancesExpiresNonVerrouillees(any(LocalDateTime.class)))
                .thenReturn(List.of());

        new PlanificateurVerrouillageSeance(seanceAppelRepository).verrouillerSeancesExpirees();

        verify(seanceAppelRepository, never()).save(any());
    }

    /**
     * La borne est l'instant du passage, et non une date figée : c'est elle qui
     * décide ce qui est « expiré ».
     */
    @Test
    void interrogeLaBaseAvecLinstantDuPassage() {
        LocalDateTime avant = LocalDateTime.now();
        when(seanceAppelRepository.findSeancesExpiresNonVerrouillees(any(LocalDateTime.class)))
                .thenReturn(List.of());

        new PlanificateurVerrouillageSeance(seanceAppelRepository).verrouillerSeancesExpirees();

        ArgumentCaptor<LocalDateTime> capteur = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(seanceAppelRepository).findSeancesExpiresNonVerrouillees(capteur.capture());
        assertThat(capteur.getValue()).isBetween(avant, LocalDateTime.now());
    }
}
