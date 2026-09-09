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
import tn.wtm.school.pointage.dto.requete.SoumissionJustificatifPointageRequete;
import tn.wtm.school.pointage.dto.requete.TraitementJustificatifPointageRequete;
import tn.wtm.school.pointage.entity.JustificatifPointage;
import tn.wtm.school.pointage.entity.PresencePersonnel;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutJustificatifPointage;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypeJustificatifPointage;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.mapper.JustificatifPointageMapper;
import tn.wtm.school.pointage.port.PortMembrePersonnel;
import tn.wtm.school.pointage.repository.JustificatifPointageRepository;
import tn.wtm.school.pointage.repository.PresencePersonnelRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le justificatif d'absence du personnel : dépôt, décision, consultation.
 *
 * <p>Ce service porte la seule écriture croisée du module : approuver un
 * justificatif ne change pas que le justificatif, il fait basculer le pointage
 * lui-même en {@code ABSENCE_JUSTIFIEE}. C'est cette bascule qui décide de ce
 * que compteront les statistiques et la retenue sur salaire — et rien ne la
 * garantissait.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceJustificatifPointageImplTest {

    private static final String TENANT = "28";
    private static final Long PRESENCE_ID = 7L;
    private static final Long JUSTIFICATIF_ID = 44L;
    private static final Long MEMBRE = 2980L;
    private static final LocalDate JOUR = LocalDate.of(2026, 9, 8);

    @Mock private JustificatifPointageRepository justificatifRepository;
    @Mock private PresencePersonnelRepository presenceRepository;
    @Mock private JustificatifPointageMapper justificatifMapper;
    @Mock private PortMembrePersonnel portMembrePersonnel;

    private ServiceJustificatifPointageImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServiceJustificatifPointageImpl(justificatifRepository, presenceRepository,
                justificatifMapper, portMembrePersonnel);

        when(justificatifRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(justificatifMapper.toResponse(any())).thenAnswer(i -> new JustificatifPointageReponse());
        when(portMembrePersonnel.nomsParIds(anyString(), any(), anyCollection())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Dépôt ────────────────────────────────────────────────────────────────────

    @Test
    void deposeUnJustificatifEnAttenteRattacheAuPointageEtAuMembre() {
        presenceEnBase(StatutPresencePersonnel.ABSENT);

        service.soumettreJustificatif(soumission());

        JustificatifPointage depose = justificatifCapte();
        assertThat(depose.getStatut()).isEqualTo(StatutJustificatifPointage.EN_ATTENTE);
        assertThat(depose.getPresencePersonnelId()).isEqualTo(PRESENCE_ID);
        assertThat(depose.getMembrePersonnelId()).isEqualTo(MEMBRE);
        assertThat(depose.getTenantId()).isEqualTo(TENANT);
        assertThat(depose.getSoumisA()).isNotNull();
        assertThat(depose.getTraiteA()).isNull();
    }

    /**
     * Le membre est repris du pointage, jamais de la requête : un dépôt ne peut
     * donc pas être imputé à quelqu'un d'autre que l'agent effectivement absent.
     */
    @Test
    void reprendLeMembreDuPointageEtNonDeLaRequete() {
        PresencePersonnel presence = presenceEnBase(StatutPresencePersonnel.ABSENT);
        presence.setMembrePersonnelId(3007L);

        service.soumettreJustificatif(soumission());

        assertThat(justificatifCapte().getMembrePersonnelId()).isEqualTo(3007L);
    }

    /** Les deux formes d'absence acceptent un justificatif. */
    @Test
    void accepteUnDepotSurUneAbsenceDeclareeOuNonJustifiee() {
        presenceEnBase(StatutPresencePersonnel.ABSENT);
        assertThatCode(() -> service.soumettreJustificatif(soumission())).doesNotThrowAnyException();

        presenceEnBase(StatutPresencePersonnel.ABSENCE_NON_JUSTIFIEE);
        assertThatCode(() -> service.soumettreJustificatif(soumission())).doesNotThrowAnyException();
    }

    /**
     * Tout le reste est refusé — y compris {@code ABSENCE_JUSTIFIEE}, qui
     * signale un justificatif déjà accepté : en accepter un second rouvrirait
     * une décision prise.
     */
    @Test
    void refuseUnDepotSurToutStatutQuiNestPasUneAbsenceOuverte() {
        for (StatutPresencePersonnel statut : List.of(
                StatutPresencePersonnel.PRESENT,
                StatutPresencePersonnel.EN_RETARD,
                StatutPresencePersonnel.EN_CONGE,
                StatutPresencePersonnel.ABSENCE_JUSTIFIEE)) {
            presenceEnBase(statut);

            assertThatThrownBy(() -> service.soumettreJustificatif(soumission()))
                    .as("statut %s", statut)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("absence");
        }
    }

    @Test
    void refuseUnDepotSurUnPointageIntrouvable() {
        when(presenceRepository.findByTenantIdAndId(TENANT, PRESENCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.soumettreJustificatif(soumission()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(justificatifRepository, never()).save(any());
    }

    // ── Décision ─────────────────────────────────────────────────────────────────

    /** L'écriture croisée : approuver requalifie l'absence elle-même. */
    @Test
    void lapprobationRequalifieLabsenceEnAbsenceJustifiee() {
        JustificatifPointage justificatif = justificatifEnBase(StatutJustificatifPointage.EN_ATTENTE);
        PresencePersonnel presence = presenceEnBase(StatutPresencePersonnel.ABSENT);

        service.traiterJustificatif(JUSTIFICATIF_ID, decision(true, null, "directrice"));

        assertThat(justificatif.getStatut()).isEqualTo(StatutJustificatifPointage.APPROUVE);
        assertThat(justificatif.getTraitePar()).isEqualTo("directrice");
        assertThat(justificatif.getTraiteA()).isNotNull();
        assertThat(presence.getStatut()).isEqualTo(StatutPresencePersonnel.ABSENCE_JUSTIFIEE);
        verify(presenceRepository).save(presence);
    }

    /**
     * Un pointage disparu entre le dépôt et la décision ne fait pas échouer le
     * traitement : le justificatif est archivé, il n'y a simplement plus rien à
     * requalifier.
     */
    @Test
    void traiteQuandMemeLorsqueLePointageAdisparu() {
        JustificatifPointage justificatif = justificatifEnBase(StatutJustificatifPointage.EN_ATTENTE);
        when(presenceRepository.findByTenantIdAndId(TENANT, PRESENCE_ID)).thenReturn(Optional.empty());

        service.traiterJustificatif(JUSTIFICATIF_ID, decision(true, null, "directrice"));

        assertThat(justificatif.getStatut()).isEqualTo(StatutJustificatifPointage.APPROUVE);
        verify(presenceRepository, never()).save(any());
    }

    @Test
    void leRejetLaisseLabsenceEnLetatEtConsigneLeMotif() {
        JustificatifPointage justificatif = justificatifEnBase(StatutJustificatifPointage.EN_ATTENTE);
        PresencePersonnel presence = presenceEnBase(StatutPresencePersonnel.ABSENT);

        service.traiterJustificatif(JUSTIFICATIF_ID, decision(false, "Document illisible", "directrice"));

        assertThat(justificatif.getStatut()).isEqualTo(StatutJustificatifPointage.REJETE);
        assertThat(justificatif.getCommentaireAdmin()).isEqualTo("Document illisible");
        assertThat(presence.getStatut()).isEqualTo(StatutPresencePersonnel.ABSENT);
        verify(presenceRepository, never()).save(any());
    }

    /** Un refus sans motif n'est pas opposable à l'agent : il est refusé. */
    @Test
    void refuseUnRejetSansMotif() {
        assertThatThrownBy(() -> service.traiterJustificatif(JUSTIFICATIF_ID, decision(false, null, "directrice")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("motif");
        assertThatThrownBy(() -> service.traiterJustificatif(JUSTIFICATIF_ID, decision(false, "   ", "directrice")))
                .isInstanceOf(BadRequestException.class);
    }

    /**
     * Le motif est exigé avant même la lecture en base : une décision mal formée
     * ne doit pas consommer une requête, ni révéler l'existence du justificatif.
     */
    @Test
    void controleLeMotifAvantDeLireLeJustificatif() {
        assertThatThrownBy(() -> service.traiterJustificatif(JUSTIFICATIF_ID, decision(false, null, "directrice")))
                .isInstanceOf(BadRequestException.class);

        verify(justificatifRepository, never()).findByTenantIdAndId(anyString(), any());
    }

    @Test
    void refuseDeTraiterDeuxFoisLeMemeJustificatif() {
        justificatifEnBase(StatutJustificatifPointage.APPROUVE);

        assertThatThrownBy(() -> service.traiterJustificatif(JUSTIFICATIF_ID, decision(true, null, "directrice")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été traité");
    }

    @Test
    void refuseDeTraiterUnJustificatifIntrouvable() {
        when(justificatifRepository.findByTenantIdAndId(TENANT, JUSTIFICATIF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.traiterJustificatif(JUSTIFICATIF_ID, decision(true, null, "directrice")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Consultation ─────────────────────────────────────────────────────────────

    /**
     * Un justificatif ne porte que des identifiants. Le contexte du pointage est
     * rapporté ici, sans quoi la file d'attente de l'administration afficherait
     * des numéros — et le nom ne se déduit même pas du seul identifiant du
     * membre, dont la signification dépend du type de personnel.
     */
    @Test
    void jointLeContexteDuPointageAuxJustificatifsEnAttente() {
        when(justificatifRepository.findByTenantIdAndStatut(TENANT, StatutJustificatifPointage.EN_ATTENTE))
                .thenReturn(List.of(justificatif(StatutJustificatifPointage.EN_ATTENTE)));
        presenceEnBase(StatutPresencePersonnel.ABSENT);
        when(portMembrePersonnel.nomsParIds(anyString(), any(), anyCollection()))
                .thenReturn(Map.of(MEMBRE, "Ben Ali Mehdi"));

        assertThat(service.listerJustificatifsEnAttente()).singleElement().satisfies(reponse -> {
            assertThat(reponse.getNomMembre()).isEqualTo("Ben Ali Mehdi");
            assertThat(reponse.getTypePersonnel()).isEqualTo(TypePersonnel.ENSEIGNANT);
            assertThat(reponse.getDatePointage()).isEqualTo(JOUR);
            assertThat(reponse.getPeriode()).isEqualTo(Periode.MATIN);
        });
    }

    @Test
    void rendUneFileVideQuandAucunJustificatifNattend() {
        when(justificatifRepository.findByTenantIdAndStatut(TENANT, StatutJustificatifPointage.EN_ATTENTE))
                .thenReturn(List.of());

        assertThat(service.listerJustificatifsEnAttente()).isEmpty();
    }

    @Test
    void retrouveLeJustificatifDunPointage() {
        when(justificatifRepository.findByTenantIdAndPresencePersonnelId(TENANT, PRESENCE_ID))
                .thenReturn(Optional.of(justificatif(StatutJustificatifPointage.EN_ATTENTE)));
        presenceEnBase(StatutPresencePersonnel.ABSENT);

        assertThat(service.obtenirJustificatifParPresence(PRESENCE_ID)).isNotNull();
    }

    @Test
    void refuseUnPointageSansJustificatif() {
        when(justificatifRepository.findByTenantIdAndPresencePersonnelId(TENANT, PRESENCE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenirJustificatifParPresence(PRESENCE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private SoumissionJustificatifPointageRequete soumission() {
        return SoumissionJustificatifPointageRequete.builder()
                .presencePersonnelId(PRESENCE_ID)
                .typeDocument(TypeJustificatifPointage.CERTIFICAT_MEDICAL)
                .description("Arrêt de travail de 2 jours")
                .cheminDocument("/documents/cm-2026-118.pdf")
                .build();
    }

    private TraitementJustificatifPointageRequete decision(boolean approuve, String motifRejet, String traitePar) {
        return TraitementJustificatifPointageRequete.builder()
                .approuve(approuve)
                .motifRejet(motifRejet)
                .traitePar(traitePar)
                .build();
    }

    private PresencePersonnel presenceEnBase(StatutPresencePersonnel statut) {
        PresencePersonnel presence = PresencePersonnel.builder()
                .id(PRESENCE_ID)
                .membrePersonnelId(MEMBRE)
                .typePersonnel(TypePersonnel.ENSEIGNANT)
                .datePointage(JOUR)
                .periode(Periode.MATIN)
                .statut(statut)
                .build();
        presence.setTenantId(TENANT);
        when(presenceRepository.findByTenantIdAndId(TENANT, PRESENCE_ID)).thenReturn(Optional.of(presence));
        return presence;
    }

    private JustificatifPointage justificatif(StatutJustificatifPointage statut) {
        JustificatifPointage justificatif = JustificatifPointage.builder()
                .id(JUSTIFICATIF_ID)
                .membrePersonnelId(MEMBRE)
                .presencePersonnelId(PRESENCE_ID)
                .typeDocument(TypeJustificatifPointage.CERTIFICAT_MEDICAL)
                .statut(statut)
                .build();
        justificatif.setTenantId(TENANT);
        return justificatif;
    }

    private JustificatifPointage justificatifEnBase(StatutJustificatifPointage statut) {
        JustificatifPointage justificatif = justificatif(statut);
        when(justificatifRepository.findByTenantIdAndId(TENANT, JUSTIFICATIF_ID)).thenReturn(Optional.of(justificatif));
        return justificatif;
    }

    private JustificatifPointage justificatifCapte() {
        ArgumentCaptor<JustificatifPointage> capteur = ArgumentCaptor.forClass(JustificatifPointage.class);
        verify(justificatifRepository).save(capteur.capture());
        return capteur.getValue();
    }
}
