package tn.wtm.school.pointage.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.pointage.dto.reponse.StatistiquesPresenceReponse;
import tn.wtm.school.pointage.entity.PresencePersonnel;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.repository.PresencePersonnelRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Les statistiques de présence du personnel.
 *
 * <p>Deux chiffres méritent d'être fixés par un test, parce qu'ils se lisent
 * mal et se contestent : les absences agrègent <b>trois</b> statuts distincts,
 * et le dénominateur du taux est le nombre de jours de la période — bornes
 * incluses — et non le nombre de jours pointés. Un agent présent tous les jours
 * ouvrés d'un mois n'atteint donc jamais 100 %, week-ends compris au
 * dénominateur : c'est une décision de calcul, pas un défaut, et elle doit être
 * visible.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceStatistiquesPointageImplTest {

    private static final String TENANT = "28";
    private static final Long MEMBRE = 2980L;
    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 10);

    @Mock private PresencePersonnelRepository presenceRepository;

    private ServiceStatistiquesPointageImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServiceStatistiquesPointageImpl(presenceRepository);
        compte(StatutPresencePersonnel.PRESENT, 0);
        compte(StatutPresencePersonnel.ABSENT, 0);
        compte(StatutPresencePersonnel.ABSENCE_JUSTIFIEE, 0);
        compte(StatutPresencePersonnel.ABSENCE_NON_JUSTIFIEE, 0);
        compte(StatutPresencePersonnel.EN_RETARD, 0);
        compte(StatutPresencePersonnel.EN_CONGE, 0);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Par membre ───────────────────────────────────────────────────────────────

    /**
     * Le total des absences additionne les trois statuts d'absence. Ne compter
     * que {@code ABSENT} sous-évaluerait le chiffre dès qu'un justificatif est
     * traité — l'approbation d'un justificatif requalifie précisément le
     * pointage en {@code ABSENCE_JUSTIFIEE}.
     */
    @Test
    void additionneLesTroisFormesDabsence() {
        compte(StatutPresencePersonnel.ABSENT, 2);
        compte(StatutPresencePersonnel.ABSENCE_NON_JUSTIFIEE, 1);
        compte(StatutPresencePersonnel.ABSENCE_JUSTIFIEE, 3);

        assertThat(service.obtenirStatistiquesPersonnel(MEMBRE, DEBUT, FIN).getJoursAbsent()).isEqualTo(6);
    }

    @Test
    void rapporteChaqueStatutSurSaPropreLigne() {
        compte(StatutPresencePersonnel.PRESENT, 7);
        compte(StatutPresencePersonnel.EN_RETARD, 2);
        compte(StatutPresencePersonnel.EN_CONGE, 1);

        StatistiquesPresenceReponse stats = service.obtenirStatistiquesPersonnel(MEMBRE, DEBUT, FIN);

        assertThat(stats.getMembrePersonnelId()).isEqualTo(MEMBRE);
        assertThat(stats.getJoursPresent()).isEqualTo(7);
        assertThat(stats.getJoursEnRetard()).isEqualTo(2);
        assertThat(stats.getJoursEnConge()).isEqualTo(1);
    }

    /**
     * Le dénominateur inclut les deux bornes : du 1er au 10 septembre fait dix
     * jours, pas neuf. Sept jours présents valent donc 70 %.
     */
    @Test
    void compteLesDeuxBornesDeLaPeriodeAuDenominateurDuTaux() {
        compte(StatutPresencePersonnel.PRESENT, 7);

        assertThat(service.obtenirStatistiquesPersonnel(MEMBRE, DEBUT, FIN).getTauxPresence())
                .isCloseTo(70.0, within(0.001));
    }

    /** Une période d'un seul jour reste un dénominateur valide. */
    @Test
    void traiteUneJourneeIsoleeCommeUnePeriodeDunJour() {
        compte(StatutPresencePersonnel.PRESENT, 1);

        assertThat(service.obtenirStatistiquesPersonnel(MEMBRE, DEBUT, DEBUT).getTauxPresence())
                .isCloseTo(100.0, within(0.001));
    }

    @Test
    void rendUnTauxNulQuandAucunJourNestPresent() {
        compte(StatutPresencePersonnel.ABSENT, 10);

        assertThat(service.obtenirStatistiquesPersonnel(MEMBRE, DEBUT, FIN).getTauxPresence()).isZero();
    }

    /**
     * Une période inversée — la fin avant le début — donne un taux nul plutôt
     * qu'un pourcentage négatif. Le service ne refuse pas la saisie, mais il ne
     * publie pas non plus un chiffre absurde.
     */
    @Test
    void rendUnTauxNulSurUnePeriodeInversee() {
        compte(StatutPresencePersonnel.PRESENT, 5);

        assertThat(service.obtenirStatistiquesPersonnel(MEMBRE, FIN, DEBUT).getTauxPresence()).isZero();
    }

    /** La période est libellée en français : c'est un intitulé de rapport. */
    @Test
    void libelleLaPeriodeEnFrancaisDepuisLeMoisDeDebut() {
        assertThat(service.obtenirStatistiquesPersonnel(MEMBRE, DEBUT, FIN).getPeriode())
                .isEqualTo("septembre 2026");
    }

    // ── Par type de personnel ────────────────────────────────────────────────────

    @Test
    void neRetientQueLesMembresDuTypeDemande() {
        when(presenceRepository.findByTenantIdAndDatePointage(TENANT, DEBUT)).thenReturn(List.of(
                presence(10L, TypePersonnel.ENSEIGNANT),
                presence(11L, TypePersonnel.ENSEIGNANT),
                presence(20L, TypePersonnel.SURVEILLANT),
                presence(30L, TypePersonnel.ADMINISTRATIF)));

        assertThat(service.obtenirStatistiquesParTypePersonnel(TypePersonnel.ENSEIGNANT, DEBUT, FIN))
                .hasSize(2)
                .extracting(StatistiquesPresenceReponse::getMembrePersonnelId)
                .containsExactly(10L, 11L);
    }

    @Test
    void rendUneListeVideQuandAucunMembreDuTypeNaEtePointe() {
        when(presenceRepository.findByTenantIdAndDatePointage(TENANT, DEBUT)).thenReturn(List.of(
                presence(20L, TypePersonnel.SURVEILLANT)));

        assertThat(service.obtenirStatistiquesParTypePersonnel(TypePersonnel.ENSEIGNANT, DEBUT, FIN)).isEmpty();
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private void compte(StatutPresencePersonnel statut, long valeur) {
        when(presenceRepository.compterParMembreEtStatutEtPeriode(
                anyString(), anyLong(), any(LocalDate.class), any(LocalDate.class), eqStatut(statut)))
                .thenReturn(valeur);
    }

    private static StatutPresencePersonnel eqStatut(StatutPresencePersonnel statut) {
        return org.mockito.ArgumentMatchers.eq(statut);
    }

    private PresencePersonnel presence(Long membreId, TypePersonnel type) {
        return PresencePersonnel.builder()
                .id(membreId)
                .membrePersonnelId(membreId)
                .typePersonnel(type)
                .datePointage(DEBUT)
                .periode(Periode.MATIN)
                .statut(StatutPresencePersonnel.PRESENT)
                .build();
    }
}
