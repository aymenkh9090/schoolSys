package tn.wtm.school.pointage.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.pointage.dto.reponse.JustificatifPointageReponse;
import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.reponse.RapportJournalierReponse;
import tn.wtm.school.pointage.dto.reponse.ResultatPointageMasseReponse;
import tn.wtm.school.pointage.dto.requete.PointageMasseRequete;
import tn.wtm.school.pointage.dto.requete.PointageRequete;
import tn.wtm.school.pointage.entity.JustificatifPointage;
import tn.wtm.school.pointage.entity.PresencePersonnel;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.mapper.JustificatifPointageMapper;
import tn.wtm.school.pointage.mapper.PresencePersonnelMapper;
import tn.wtm.school.pointage.port.PortMembrePersonnel;
import tn.wtm.school.pointage.repository.JustificatifPointageRepository;
import tn.wtm.school.pointage.repository.PresencePersonnelRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le pointage du personnel : la saisie, sa reprise, et le rapport du jour.
 *
 * <p>Trois règles se jouent ici et nulle part ailleurs : un membre n'est pointé
 * qu'une fois par créneau, un retard sans durée n'est pas un retard, et l'auteur
 * de la saisie vient du compte connecté — jamais de la requête, qui pourrait
 * désigner n'importe qui.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicePointageImplTest {

    private static final String TENANT = "28";
    private static final Long MEMBRE = 2980L;
    private static final LocalDate JOUR = LocalDate.of(2026, 9, 8);

    @Mock private PresencePersonnelRepository presenceRepository;
    @Mock private JustificatifPointageRepository justificatifRepository;
    @Mock private PresencePersonnelMapper presenceMapper;
    @Mock private JustificatifPointageMapper justificatifMapper;
    @Mock private PortMembrePersonnel portMembrePersonnel;

    private ServicePointageImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServicePointageImpl(presenceRepository, justificatifRepository,
                presenceMapper, justificatifMapper, portMembrePersonnel);

        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(presenceMapper.toEntity(any())).thenAnswer(i -> depuis(i.getArgument(0)));
        when(presenceMapper.toResponse(any(PresencePersonnel.class))).thenAnswer(i -> reponseDe(i.getArgument(0)));
        when(justificatifRepository.findByTenantIdAndPresencePersonnelId(anyString(), any()))
                .thenReturn(Optional.empty());
        when(portMembrePersonnel.nomsParIds(anyString(), any(), anyCollection())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Saisie ───────────────────────────────────────────────────────────────────

    @Test
    void enregistreLePointageAvecSonEtablissementEtSonHorodatage() {
        service.pointer(requete(StatutPresencePersonnel.PRESENT, null));

        PresencePersonnel enregistre = presenceCaptee();
        assertThat(enregistre.getTenantId()).isEqualTo(TENANT);
        assertThat(enregistre.getSaisiA()).isNotNull();
        assertThat(enregistre.getMembrePersonnelId()).isEqualTo(MEMBRE);
        assertThat(enregistre.getPeriode()).isEqualTo(Periode.MATIN);
    }

    /**
     * L'auteur de la saisie vient du compte connecté, posé par TenantFilter, et
     * jamais de la requête : c'est lui qui rend un pointage contestable devant
     * l'agent qu'il concerne.
     */
    @Test
    void imputeLaSaisieAuCompteConnecte() {
        TenantContext.setUsername("aymen.bouraoui");

        service.pointer(requete(StatutPresencePersonnel.PRESENT, null));

        assertThat(presenceCaptee().getSaisiPar()).isEqualTo("aymen.bouraoui");
    }

    /** Hors requête HTTP — tâche planifiée, seeder — la saisie s'impute au système. */
    @Test
    void imputeLaSaisieAuSystemeHorsRequeteHttp() {
        service.pointer(requete(StatutPresencePersonnel.PRESENT, null));

        assertThat(presenceCaptee().getSaisiPar()).isEqualTo("system");
    }

    /**
     * Un nom d'utilisateur vide vaut absence de nom. Sans ce repli, la colonne
     * porterait une chaîne blanche — indistinguable d'une saisie anonyme à la
     * lecture, mais bien distincte pour toute recherche.
     */
    @Test
    void imputeLaSaisieAuSystemeQuandLeNomDutilisateurEstVide() {
        TenantContext.setUsername("   ");

        service.pointer(requete(StatutPresencePersonnel.PRESENT, null));

        assertThat(presenceCaptee().getSaisiPar()).isEqualTo("system");
    }

    @Test
    void refuseUnSecondPointageDuMemeMembreSurLeMemeCreneau() {
        when(presenceRepository.existsByTenantIdAndMembrePersonnelIdAndDatePointageAndPeriode(
                TENANT, MEMBRE, JOUR, Periode.MATIN)).thenReturn(true);

        assertThatThrownBy(() -> service.pointer(requete(StatutPresencePersonnel.PRESENT, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("existe déjà");
        verify(presenceRepository, never()).save(any());
    }

    /** Le même membre peut en revanche être pointé le matin et l'après-midi. */
    @Test
    void autoriseLesDeuxCreneauxDuMemeJour() {
        when(presenceRepository.existsByTenantIdAndMembrePersonnelIdAndDatePointageAndPeriode(
                TENANT, MEMBRE, JOUR, Periode.MATIN)).thenReturn(true);

        PointageRequete apresMidi = requete(StatutPresencePersonnel.PRESENT, null);
        apresMidi.setPeriode(Periode.APRES_MIDI);

        service.pointer(apresMidi);

        assertThat(presenceCaptee().getPeriode()).isEqualTo(Periode.APRES_MIDI);
    }

    @Test
    void refuseUnRetardSansDuree() {
        assertThatThrownBy(() -> service.pointer(requete(StatutPresencePersonnel.EN_RETARD, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("minutes de retard");
    }

    /** Zéro minute n'est pas un retard : c'est une arrivée à l'heure mal saisie. */
    @Test
    void refuseUnRetardDeZeroMinuteOuNegatif() {
        assertThatThrownBy(() -> service.pointer(requete(StatutPresencePersonnel.EN_RETARD, 0)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.pointer(requete(StatutPresencePersonnel.EN_RETARD, -5)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void accepteUnRetardChiffre() {
        service.pointer(requete(StatutPresencePersonnel.EN_RETARD, 12));

        assertThat(presenceCaptee().getMinutesRetard()).isEqualTo(12);
    }

    /** La durée n'est exigée que pour un retard : une absence n'en porte pas. */
    @Test
    void nExigePasDeDureePourLesAutresStatuts() {
        service.pointer(requete(StatutPresencePersonnel.ABSENT, null));

        assertThat(presenceCaptee().getStatut()).isEqualTo(StatutPresencePersonnel.ABSENT);
    }

    @Test
    void jointAuPointageSonJustificatifEtLeNomDuMembre() {
        when(justificatifRepository.findByTenantIdAndPresencePersonnelId(anyString(), any()))
                .thenReturn(Optional.of(new JustificatifPointage()));
        when(justificatifMapper.toResponse(any())).thenReturn(new JustificatifPointageReponse());
        when(portMembrePersonnel.nomsParIds(anyString(), any(), anyCollection()))
                .thenReturn(Map.of(MEMBRE, "Ben Ali Mehdi"));

        PresencePersonnelReponse reponse = service.pointer(requete(StatutPresencePersonnel.ABSENT, null));

        assertThat(reponse.getJustificatif()).isNotNull();
        assertThat(reponse.getNomMembre()).isEqualTo("Ben Ali Mehdi");
    }

    // ── Saisie en masse ──────────────────────────────────────────────────────────

    /**
     * Un échec n'arrête pas le lot : l'appel de toute une école se fait en un
     * envoi, et un doublon sur un agent ne doit pas perdre les vingt autres.
     */
    @Test
    void poursuitLeLotMalgreUnEchecEtRendLeDetailDesDeux() {
        PointageRequete valide = requete(StatutPresencePersonnel.PRESENT, null);
        PointageRequete enDoublon = requete(StatutPresencePersonnel.PRESENT, null);
        enDoublon.setMembrePersonnelId(3007L);
        when(presenceRepository.existsByTenantIdAndMembrePersonnelIdAndDatePointageAndPeriode(
                TENANT, 3007L, JOUR, Periode.MATIN)).thenReturn(true);

        ResultatPointageMasseReponse resultat = service.pointerEnMasse(
                PointageMasseRequete.builder().pointages(List.of(valide, enDoublon)).build());

        assertThat(resultat.getTotal()).isEqualTo(2);
        assertThat(resultat.getReussis()).isEqualTo(1);
        assertThat(resultat.getEchoues()).isEqualTo(1);
        assertThat(resultat.getEchecs()).singleElement()
                .satisfies(echec -> {
                    assertThat(echec.membrePersonnelId()).isEqualTo(3007L);
                    assertThat(echec.raison()).contains("existe déjà");
                });
    }

    @Test
    void rendUnResultatVidePourUnLotVide() {
        ResultatPointageMasseReponse resultat = service.pointerEnMasse(
                PointageMasseRequete.builder().pointages(List.of()).build());

        assertThat(resultat.getTotal()).isZero();
        assertThat(resultat.getResultats()).isEmpty();
        assertThat(resultat.getEchecs()).isEmpty();
    }

    // ── Reprise d'une saisie ─────────────────────────────────────────────────────

    @Test
    void remplaceLesChampsDeLaSaisieEtReimputeLaCorrection() {
        PresencePersonnel existant = presenceEnBase(StatutPresencePersonnel.ABSENT);
        TenantContext.setUsername("chef.etablissement");

        PointageRequete correction = requete(StatutPresencePersonnel.EN_RETARD, 20);
        correction.setHeureArrivee(LocalTime.of(8, 20));
        correction.setNote("Retard signalé après coup");

        service.modifierPointage(7L, correction);

        assertThat(existant.getStatut()).isEqualTo(StatutPresencePersonnel.EN_RETARD);
        assertThat(existant.getMinutesRetard()).isEqualTo(20);
        assertThat(existant.getHeureArrivee()).isEqualTo(LocalTime.of(8, 20));
        assertThat(existant.getNote()).isEqualTo("Retard signalé après coup");
        assertThat(existant.getSaisiPar()).isEqualTo("chef.etablissement");
        assertThat(existant.getSaisiA()).isNotNull();
    }

    @Test
    void refuseDeCorrigerEnRetardSansDuree() {
        presenceEnBase(StatutPresencePersonnel.ABSENT);

        assertThatThrownBy(() -> service.modifierPointage(7L, requete(StatutPresencePersonnel.EN_RETARD, null)))
                .isInstanceOf(BadRequestException.class);
    }

    /** La correction applique la même règle que la saisie : zéro minute n'est pas un retard. */
    @Test
    void refuseDeCorrigerEnRetardDeZeroMinute() {
        presenceEnBase(StatutPresencePersonnel.ABSENT);

        assertThatThrownBy(() -> service.modifierPointage(7L, requete(StatutPresencePersonnel.EN_RETARD, 0)))
                .isInstanceOf(BadRequestException.class);
    }

    /** Corriger vers un statut sans durée n'exige évidemment aucune durée. */
    @Test
    void corrigeVersUnStatutSansDureeSansRienExiger() {
        PresencePersonnel existant = presenceEnBase(StatutPresencePersonnel.EN_RETARD);

        service.modifierPointage(7L, requete(StatutPresencePersonnel.EN_CONGE, null));

        assertThat(existant.getStatut()).isEqualTo(StatutPresencePersonnel.EN_CONGE);
        assertThat(existant.getMinutesRetard()).isNull();
    }

    @Test
    void refuseDeCorrigerUnPointageIntrouvable() {
        when(presenceRepository.findByTenantIdAndId(TENANT, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.modifierPointage(7L, requete(StatutPresencePersonnel.PRESENT, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Rapport du jour ──────────────────────────────────────────────────────────

    @Test
    void ventileLesSixStatutsDuRapportJournalier() {
        when(presenceRepository.findByTenantIdAndDatePointage(TENANT, JOUR)).thenReturn(List.of(
                enBase(1L, MEMBRE, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.PRESENT),
                enBase(2L, 2L, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.PRESENT),
                enBase(3L, 3L, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.ABSENT),
                enBase(4L, 4L, TypePersonnel.SURVEILLANT, StatutPresencePersonnel.EN_RETARD),
                enBase(5L, 5L, TypePersonnel.SURVEILLANT, StatutPresencePersonnel.EN_CONGE),
                enBase(6L, 6L, TypePersonnel.ADMINISTRATIF, StatutPresencePersonnel.ABSENCE_JUSTIFIEE),
                enBase(7L, 7L, TypePersonnel.ADMINISTRATIF, StatutPresencePersonnel.ABSENCE_NON_JUSTIFIEE)));

        RapportJournalierReponse rapport = service.obtenirRapportJournalier(JOUR);

        assertThat(rapport.getDate()).isEqualTo(JOUR);
        assertThat(rapport.getTotalPresents()).isEqualTo(2);
        assertThat(rapport.getTotalAbsents()).isEqualTo(1);
        assertThat(rapport.getTotalEnRetard()).isEqualTo(1);
        assertThat(rapport.getTotalEnConge()).isEqualTo(1);
        assertThat(rapport.getTotalAbsencesJustifiees()).isEqualTo(1);
        assertThat(rapport.getTotalAbsencesNonJustifiees()).isEqualTo(1);
        assertThat(rapport.getEnregistrements()).hasSize(7);
    }

    /**
     * Les noms sont résolus par lot — une requête par type de personnel présent,
     * et non une par ligne. Sur le rapport d'une école entière, la version naïve
     * ferait une centaine d'allers-retours pour afficher une centaine de noms.
     */
    @Test
    void resoutLesNomsParTypeDePersonnelEtNonParLigne() {
        when(presenceRepository.findByTenantIdAndDatePointage(TENANT, JOUR)).thenReturn(List.of(
                enBase(1L, 10L, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.PRESENT),
                enBase(2L, 11L, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.PRESENT),
                enBase(3L, 12L, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.ABSENT),
                enBase(4L, 20L, TypePersonnel.SURVEILLANT, StatutPresencePersonnel.PRESENT)));
        when(portMembrePersonnel.nomsParIds(eq(TENANT), eq(TypePersonnel.ENSEIGNANT), anyCollection()))
                .thenReturn(Map.of(10L, "Sahli Aya", 11L, "Guesmi Omar"));
        when(portMembrePersonnel.nomsParIds(eq(TENANT), eq(TypePersonnel.SURVEILLANT), anyCollection()))
                .thenReturn(Map.of(20L, "Hammami Ahmed"));

        RapportJournalierReponse rapport = service.obtenirRapportJournalier(JOUR);

        // Deux types présents, donc deux requêtes — pas quatre.
        verify(portMembrePersonnel, times(2)).nomsParIds(anyString(), any(), anyCollection());
        assertThat(rapport.getEnregistrements()).extracting(PresencePersonnelReponse::getNomMembre)
                .containsExactly("Sahli Aya", "Guesmi Omar", null, "Hammami Ahmed");
    }

    /**
     * Le rapport joint à chaque ligne son justificatif, quand il en existe un.
     * C'est ce qui permet de distinguer, d'un coup d'œil sur la journée, une
     * absence déjà justifiée d'une absence à traiter — sans quoi l'écran
     * n'afficherait qu'un statut et il faudrait ouvrir chaque ligne.
     */
    @Test
    void jointSonJustificatifALaLigneQuiEnPorteUn() {
        when(presenceRepository.findByTenantIdAndDatePointage(TENANT, JOUR)).thenReturn(List.of(
                enBase(1L, 10L, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.PRESENT),
                enBase(2L, 11L, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.ABSENT)));
        when(justificatifRepository.findByTenantIdAndPresencePersonnelId(TENANT, 2L))
                .thenReturn(Optional.of(new JustificatifPointage()));
        when(justificatifMapper.toResponse(any())).thenReturn(new JustificatifPointageReponse());

        RapportJournalierReponse rapport = service.obtenirRapportJournalier(JOUR);

        assertThat(rapport.getEnregistrements().get(0).getJustificatif()).isNull();
        assertThat(rapport.getEnregistrements().get(1).getJustificatif()).isNotNull();
    }

    /** Un identifiant sans nom connu laisse le champ vide : la ligne reste. */
    @Test
    void gardeLaLigneQuandLeNomDuMembreEstInconnu() {
        when(presenceRepository.findByTenantIdAndDatePointage(TENANT, JOUR)).thenReturn(List.of(
                enBase(1L, 99L, TypePersonnel.ADMINISTRATIF, StatutPresencePersonnel.PRESENT)));

        RapportJournalierReponse rapport = service.obtenirRapportJournalier(JOUR);

        assertThat(rapport.getEnregistrements()).singleElement()
                .satisfies(ligne -> assertThat(ligne.getNomMembre()).isNull());
    }

    @Test
    void rendUnRapportVideQuandPersonneNaEtePointe() {
        when(presenceRepository.findByTenantIdAndDatePointage(TENANT, JOUR)).thenReturn(List.of());

        RapportJournalierReponse rapport = service.obtenirRapportJournalier(JOUR);

        assertThat(rapport.getEnregistrements()).isEmpty();
        assertThat(rapport.getTotalPresents()).isZero();
        verify(portMembrePersonnel, never()).nomsParIds(anyString(), any(), anyCollection());
    }

    // ── Historique ───────────────────────────────────────────────────────────────

    @Test
    void rendLhistoriqueDunMembreSurLaPeriodeDemandee() {
        LocalDate fin = JOUR.plusDays(30);
        when(presenceRepository.findByTenantIdAndMembrePersonnelIdAndDatePointageBetween(TENANT, MEMBRE, JOUR, fin))
                .thenReturn(List.of(enBase(1L, MEMBRE, TypePersonnel.ENSEIGNANT, StatutPresencePersonnel.PRESENT)));

        assertThat(service.obtenirHistoriquePersonnel(MEMBRE, JOUR, fin)).hasSize(1);
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private PointageRequete requete(StatutPresencePersonnel statut, Integer minutesRetard) {
        return PointageRequete.builder()
                .membrePersonnelId(MEMBRE)
                .typePersonnel(TypePersonnel.ENSEIGNANT)
                .datePointage(JOUR)
                .periode(Periode.MATIN)
                .statut(statut)
                .minutesRetard(minutesRetard)
                .build();
    }

    /** Ce que fait le mapper réel : la requête recopiée, sans les champs ignorés. */
    private PresencePersonnel depuis(PointageRequete requete) {
        return PresencePersonnel.builder()
                .membrePersonnelId(requete.getMembrePersonnelId())
                .typePersonnel(requete.getTypePersonnel())
                .datePointage(requete.getDatePointage())
                .periode(requete.getPeriode())
                .statut(requete.getStatut())
                .heureArrivee(requete.getHeureArrivee())
                .heureDepart(requete.getHeureDepart())
                .minutesRetard(requete.getMinutesRetard())
                .note(requete.getNote())
                .build();
    }

    private PresencePersonnelReponse reponseDe(PresencePersonnel presence) {
        PresencePersonnelReponse reponse = new PresencePersonnelReponse();
        reponse.setId(presence.getId());
        reponse.setMembrePersonnelId(presence.getMembrePersonnelId());
        reponse.setTypePersonnel(presence.getTypePersonnel());
        reponse.setStatut(presence.getStatut());
        return reponse;
    }

    private PresencePersonnel enBase(Long id, Long membreId, TypePersonnel type, StatutPresencePersonnel statut) {
        PresencePersonnel presence = PresencePersonnel.builder()
                .id(id)
                .membrePersonnelId(membreId)
                .typePersonnel(type)
                .datePointage(JOUR)
                .periode(Periode.MATIN)
                .statut(statut)
                .build();
        presence.setTenantId(TENANT);
        return presence;
    }

    private PresencePersonnel presenceEnBase(StatutPresencePersonnel statut) {
        PresencePersonnel presence = enBase(7L, MEMBRE, TypePersonnel.ENSEIGNANT, statut);
        when(presenceRepository.findByTenantIdAndId(TENANT, 7L)).thenReturn(Optional.of(presence));
        return presence;
    }

    private PresencePersonnel presenceCaptee() {
        ArgumentCaptor<PresencePersonnel> capteur = ArgumentCaptor.forClass(PresencePersonnel.class);
        verify(presenceRepository).save(capteur.capture());
        return capteur.getValue();
    }
}
