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
import tn.wtm.school.absence.dto.reponse.LigneAppelReponse;
import tn.wtm.school.absence.dto.reponse.SignalementEleveReponse;
import tn.wtm.school.absence.dto.requete.ModificationStatutRequete;
import tn.wtm.school.absence.dto.requete.OuvertureAppelRequete;
import tn.wtm.school.absence.entity.HistoriqueAppel;
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
import tn.wtm.school.common.exceptions.BusinessException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // ── modifierStatutEleve ───────────────────────────────────────────────────
    //
    // Le retard n'est pas un drapeau : c'est un nombre de minutes qui finira sur
    // le bulletin de l'élève et dans le suivi de l'établissement. Il se mesure
    // entre l'ouverture de l'appel et l'arrivée effective, et il se mesure comme
    // une durée écoulée — les deux bornes sont rattachées à leur zone avant
    // soustraction, sans quoi un changement d'heure entre elles ferait varier le
    // résultat d'une heure entière.

    /** Ouverture de l'appel à 10:00, séance déverrouillée. */
    private LigneAppel ligneOuverte() {
        SeanceAppel seance = SeanceAppel.builder()
                .id(1L)
                .estVerrouille(false)
                .ouvertureAt(mercrediPasse.atTime(10, 0))
                .build();
        return LigneAppel.builder()
                .id(9L)
                .eleveId(12L)
                .statut(StatutPresence.ABSENT)
                .seanceAppel(seance)
                .build();
    }

    private void ligneEnBase(LigneAppel ligne) {
        when(ligneAppelRepository.findByTenantIdAndId(TENANT, ligne.getId())).thenReturn(Optional.of(ligne));
        when(ligneAppelMapper.toResponse(any())).thenReturn(new LigneAppelReponse());
    }

    @Test
    void compteLesMinutesDeRetardEntreLOuvertureDeLAppelEtLArrivee() {
        LigneAppel ligne = ligneOuverte();
        ligneEnBase(ligne);

        service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.RETARD)
                .arriveeAt(mercrediPasse.atTime(10, 12))
                .build());

        assertThat(ligne.getStatut()).isEqualTo(StatutPresence.RETARD);
        assertThat(ligne.getMinutesRetard()).isEqualTo(12);
        assertThat(ligne.getArriveeAt()).isEqualTo(mercrediPasse.atTime(10, 12));
    }

    /**
     * Un élève arrivé avant l'ouverture de l'appel n'est pas en avance de
     * -5 minutes : le retard est plancher à zéro. Sans le `Math.max`, un
     * enseignant qui ouvre l'appel en retard fabriquerait des retards négatifs
     * pour toute sa classe.
     */
    @Test
    void neFabriquePasDeRetardNegatifQuandLArriveePrecedeLOuverture() {
        LigneAppel ligne = ligneOuverte();
        ligneEnBase(ligne);

        service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.RETARD)
                .arriveeAt(mercrediPasse.atTime(9, 55))
                .build());

        assertThat(ligne.getMinutesRetard()).isZero();
    }

    /**
     * Le franchissement d'un changement d'heure est la raison d'être du rattachement
     * à la zone : sur l'horloge murale, 01:30 → 03:10 fait 1 h 40, alors qu'il ne
     * s'est écoulé que 40 minutes. Le test s'exécute dans la zone de la JVM ; il
     * vérifie donc l'invariant qui vaut partout — la durée mesurée est la durée
     * réellement écoulée entre les deux instants, jamais la différence des
     * cadrans.
     */
    @Test
    void mesureUneDureeEcouleeEtNonUneDifferenceDeCadrans() {
        LigneAppel ligne = ligneOuverte();
        ligneEnBase(ligne);
        LocalDateTime ouverture = ligne.getSeanceAppel().getOuvertureAt();
        LocalDateTime arrivee = ouverture.plusMinutes(95);

        service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.RETARD)
                .arriveeAt(arrivee)
                .build());

        long ecoule = java.time.Duration.between(
                ouverture.atZone(java.time.ZoneId.systemDefault()),
                arrivee.atZone(java.time.ZoneId.systemDefault())).toMinutes();
        assertThat(ligne.getMinutesRetard()).isEqualTo((int) ecoule);
    }

    @Test
    void refuseUnRetardSansHeureDArrivee() {
        ligneEnBase(ligneOuverte());

        assertThatThrownBy(() -> service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.RETARD)
                .build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuseUneExclusionSansRaison() {
        ligneEnBase(ligneOuverte());

        assertThatThrownBy(() -> service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.EXCLU)
                .raisonExclusion("   ")
                .build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void horodateLExclusionEtRetientQuiLaPrononcee() {
        LigneAppel ligne = ligneOuverte();
        ligneEnBase(ligne);

        service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.EXCLU)
                .raisonExclusion("Comportement")
                .modifiePar(77L)
                .build());

        assertThat(ligne.getRaisonExclusion()).isEqualTo("Comportement");
        assertThat(ligne.getExcluPar()).isEqualTo(77L);
        assertThat(ligne.getExclusionAt()).isNotNull();
    }

    /** Une séance verrouillée est close : plus aucun statut n'y bouge. */
    @Test
    void refuseDeModifierUneSeanceVerrouillee() {
        LigneAppel ligne = ligneOuverte();
        ligne.getSeanceAppel().setEstVerrouille(true);
        when(ligneAppelRepository.findByTenantIdAndId(TENANT, 9L)).thenReturn(Optional.of(ligne));

        assertThatThrownBy(() -> service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.PRESENT)
                .build()))
                .isInstanceOf(BusinessException.class);

        verify(ligneAppelRepository, never()).save(any());
    }

    /**
     * La traçabilité est le point sensible de l'appel : une modification de statut
     * doit laisser d'où l'on vient et où l'on va, sans quoi une contestation de
     * famille est indéfendable.
     */
    @Test
    void archiveLAncienEtLeNouveauStatutDansLHistorique() {
        LigneAppel ligne = ligneOuverte();
        ligneEnBase(ligne);

        service.modifierStatutEleve(9L, ModificationStatutRequete.builder()
                .statut(StatutPresence.PRESENT)
                .modifiePar(77L)
                .adresseIp("10.0.0.4")
                .build());

        ArgumentCaptor<HistoriqueAppel> captor = ArgumentCaptor.forClass(HistoriqueAppel.class);
        verify(historiqueAppelRepository).save(captor.capture());
        HistoriqueAppel trace = captor.getValue();
        assertThat(trace.getStatutPrecedent()).isEqualTo(StatutPresence.ABSENT);
        assertThat(trace.getNouveauStatut()).isEqualTo(StatutPresence.PRESENT);
        assertThat(trace.getModifiePar()).isEqualTo(77L);
        assertThat(trace.getAdresseIp()).isEqualTo("10.0.0.4");
    }

    @Test
    void refuseUneModificationSansStatut() {
        assertThatThrownBy(() -> service.modifierStatutEleve(9L, new ModificationStatutRequete()))
                .isInstanceOf(BadRequestException.class);
    }
}
