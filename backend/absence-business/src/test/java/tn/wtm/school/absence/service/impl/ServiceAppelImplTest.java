package tn.wtm.school.absence.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.wtm.school.absence.dto.reponse.AppelReponse;
import tn.wtm.school.absence.dto.reponse.SignalementEleveReponse;
import tn.wtm.school.absence.dto.requete.OuvertureAppelRequete;
import tn.wtm.school.absence.entity.LigneAppel;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.absence.mapper.HistoriqueAppelMapper;
import tn.wtm.school.absence.mapper.LigneAppelMapper;
import tn.wtm.school.absence.mapper.SeanceAppelMapper;
import tn.wtm.school.absence.port.PortContexteScolaire;
import tn.wtm.school.absence.port.PortEleveGroupe;
import tn.wtm.school.absence.port.PortSeancePlanning;
import tn.wtm.school.absence.port.PortSeancePlanning.CreneauSeance;
import tn.wtm.school.absence.repository.HistoriqueAppelRepository;
import tn.wtm.school.absence.repository.LigneAppelRepository;
import tn.wtm.school.absence.repository.SeanceAppelRepository;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceAppelImplTest {

    private static final String TENANT = "28";
    private static final Long PLANNING_ID = 5733L;

    @Mock private SeanceAppelRepository seanceAppelRepository;
    @Mock private LigneAppelRepository ligneAppelRepository;
    @Mock private HistoriqueAppelRepository historiqueAppelRepository;
    @Mock private SeanceAppelMapper seanceAppelMapper;
    @Mock private LigneAppelMapper ligneAppelMapper;
    @Mock private HistoriqueAppelMapper historiqueAppelMapper;
    @Mock private PortEleveGroupe portEleveGroupe;
    @Mock private PortSeancePlanning portSeancePlanning;
    @Mock private PortContexteScolaire portContexteScolaire;

    private ServiceAppelImpl service;

    /** Un mercredi, pour rester cohérent avec le créneau utilisé par les tests. */
    private LocalDate mercrediPasse;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServiceAppelImpl(seanceAppelRepository, ligneAppelRepository, historiqueAppelRepository,
                seanceAppelMapper, ligneAppelMapper, historiqueAppelMapper, portEleveGroupe, portSeancePlanning,
                portContexteScolaire);

        mercrediPasse = LocalDate.now().minusDays(7);
        while (mercrediPasse.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            mercrediPasse = mercrediPasse.minusDays(1);
        }

        when(portSeancePlanning.trouverCreneau(anyString(), anyLong()))
                .thenReturn(Optional.of(new CreneauSeance(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))));
        when(portEleveGroupe.trouverIdsElevesParGroupe(anyString(), anyLong())).thenReturn(List.of(1L, 2L));
        when(ligneAppelRepository.saveAll(any())).thenReturn(List.of());
        when(seanceAppelRepository.save(any(SeanceAppel.class))).thenAnswer(i -> i.getArgument(0));
        when(seanceAppelMapper.toResponse(any())).thenReturn(new AppelReponse());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private OuvertureAppelRequete requete(LocalDate jour) {
        return OuvertureAppelRequete.builder()
                .seancePlanningId(PLANNING_ID)
                .enseignantId(1L)
                .groupeClasseId(1L)
                .anneeAcademique("2025-2026")
                .dateSeance(jour)
                .build();
    }

    private SeanceAppel seanceCaptee() {
        ArgumentCaptor<SeanceAppel> captor = ArgumentCaptor.forClass(SeanceAppel.class);
        verify(seanceAppelRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void ouvreUnNouvelAppelQuandAucunNExistePourCeJourDeCours() {
        when(seanceAppelRepository.findByTenantIdAndSeancePlanningIdAndDateSeance(TENANT, PLANNING_ID, mercrediPasse))
                .thenReturn(Optional.empty());

        service.ouvrirOuRecupererAppel(requete(mercrediPasse));

        assertThat(seanceCaptee().getDateSeance()).isEqualTo(mercrediPasse);
    }

    /**
     * Régression : la séance du planning étant hebdomadaire, un appel ouvert une
     * semaine ne doit pas être resservi les semaines suivantes.
     */
    @Test
    void ouvreUnAppelDistinctPourChaqueSemaineDeLaMemeSeanceDePlanning() {
        LocalDate semaineSuivante = mercrediPasse.plusWeeks(1);
        when(seanceAppelRepository.findByTenantIdAndSeancePlanningIdAndDateSeance(TENANT, PLANNING_ID, mercrediPasse))
                .thenReturn(Optional.of(SeanceAppel.builder().id(1L).dateSeance(mercrediPasse).build()));
        when(seanceAppelRepository.findByTenantIdAndSeancePlanningIdAndDateSeance(TENANT, PLANNING_ID, semaineSuivante))
                .thenReturn(Optional.empty());

        service.ouvrirOuRecupererAppel(requete(semaineSuivante));

        assertThat(seanceCaptee().getDateSeance()).isEqualTo(semaineSuivante);
    }

    @Test
    void reutiliseLAppelExistantDuMemeJourDeCours() {
        SeanceAppel existante = SeanceAppel.builder().id(1L).dateSeance(mercrediPasse).build();
        when(seanceAppelRepository.findByTenantIdAndSeancePlanningIdAndDateSeance(TENANT, PLANNING_ID, mercrediPasse))
                .thenReturn(Optional.of(existante));

        service.ouvrirOuRecupererAppel(requete(mercrediPasse));

        verify(seanceAppelRepository, never()).save(any(SeanceAppel.class));
        verify(seanceAppelMapper).toResponse(existante);
    }

    /** Sans fermetureAt, le verrouillage automatique de fin de séance ne partait jamais. */
    @Test
    void renseigneFermetureAtAvecLaFinDuCreneauDuJour() {
        when(seanceAppelRepository.findByTenantIdAndSeancePlanningIdAndDateSeance(TENANT, PLANNING_ID, mercrediPasse))
                .thenReturn(Optional.empty());

        service.ouvrirOuRecupererAppel(requete(mercrediPasse));

        assertThat(seanceCaptee().getFermetureAt()).isEqualTo(mercrediPasse.atTime(11, 0));
    }

    @Test
    void laisseFermetureAtNullQuandLaSeanceNestPasDansLEmploiDuTemps() {
        when(portSeancePlanning.trouverCreneau(anyString(), anyLong())).thenReturn(Optional.empty());
        when(seanceAppelRepository.findByTenantIdAndSeancePlanningIdAndDateSeance(eq(TENANT), eq(PLANNING_ID), any()))
                .thenReturn(Optional.empty());

        service.ouvrirOuRecupererAppel(requete(mercrediPasse));

        assertThat(seanceCaptee().getFermetureAt()).isNull();
    }

    @Test
    void refuseUnJourQuiNeCorrespondPasAuCreneauDeLaSeance() {
        LocalDate jeudi = mercrediPasse.plusDays(1);

        assertThatThrownBy(() -> service.ouvrirOuRecupererAppel(requete(jeudi)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("WEDNESDAY");

        verify(seanceAppelRepository, never()).save(any(SeanceAppel.class));
    }

    @Test
    void refuseUneSeanceAVenir() {
        LocalDate demain = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> service.ouvrirOuRecupererAppel(requete(demain)))
                .isInstanceOf(BadRequestException.class);

        verify(seanceAppelRepository, never()).save(any(SeanceAppel.class));
    }

    // ── Suivi des absences d'une séance à l'autre ────────────────────────────

    private LigneAppel ligneSignalee(Long eleveId, StatutPresence statut, LocalDate jour) {
        SeanceAppel seance = SeanceAppel.builder()
                .id(9L)
                .seancePlanningId(PLANNING_ID)
                .groupeClasseId(1L)
                .matiereId(3L)
                .enseignantId(4L)
                .dateSeance(jour)
                .build();
        return LigneAppel.builder()
                .id(77L)
                .eleveId(eleveId)
                .statut(statut)
                .seanceAppel(seance)
                .estJustifie(false)
                .justificatifs(List.of())
                .build();
    }

    /**
     * Le cas d'usage complet : l'élève absent chez un collègue apparaît, avec
     * les libellés qu'un enseignant peut lire — pas des identifiants.
     */
    @Test
    void remonteLesAbsencesNonJustifieesDeLaClasseAvecLeursLibelles() {
        when(ligneAppelRepository.findSignalementsClasse(eq(TENANT), eq(1L), anyCollection(), any(), any()))
                .thenReturn(List.of(ligneSignalee(12L, StatutPresence.ABSENT, mercrediPasse)));
        when(portContexteScolaire.libellesMatieres(eq(TENANT), anyCollection()))
                .thenReturn(Map.of(3L, "Mathématiques"));
        when(portContexteScolaire.nomsEnseignants(eq(TENANT), anyCollection()))
                .thenReturn(Map.of(4L, "Mme Trabelsi"));

        List<SignalementEleveReponse> signalements = service.listerSignalementsClasse(1L, null, null);

        assertThat(signalements).singleElement().satisfies(s -> {
            assertThat(s.getEleveId()).isEqualTo(12L);
            assertThat(s.getStatut()).isEqualTo(StatutPresence.ABSENT);
            assertThat(s.getMatiere()).isEqualTo("Mathématiques");
            assertThat(s.getEnseignant()).isEqualTo("Mme Trabelsi");
            assertThat(s.getHeureDebut()).isEqualTo(LocalTime.of(10, 0));
        });
    }

    /** Fenêtre par défaut : les sept derniers jours, bornes comprises. */
    @Test
    void observeLaSemaineEcouleeQuandAucuneBorneNestDonnee() {
        service.listerSignalementsClasse(1L, null, null);

        verify(ligneAppelRepository).findSignalementsClasse(
                eq(TENANT), eq(1L), anyCollection(),
                eq(LocalDate.now().minusDays(7)), eq(LocalDate.now()));
    }

    @Test
    void refuseUneFenetreInversee() {
        assertThatThrownBy(() ->
                service.listerSignalementsClasse(1L, LocalDate.now(), LocalDate.now().minusDays(3)))
                .isInstanceOf(BadRequestException.class);
    }
}
