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
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.dto.requete.MiseAJourHeuresRequete;
import tn.wtm.school.pointage.entity.SuiviHeuresEnseignant;
import tn.wtm.school.pointage.mapper.SuiviHeuresMapper;
import tn.wtm.school.pointage.repository.SuiviHeuresEnseignantRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le suivi des heures d'un enseignant, semaine par semaine.
 *
 * <p>Le service ne stocke pas seulement ce qu'on lui donne : il en dérive deux
 * chiffres — les heures manquées et le taux de présence — qui sont ceux que
 * l'administration lira. Les deux sont bornés, et c'est le bornage qui compte :
 * un enseignant qui dépasse son quota n'a pas des heures manquées négatives, et
 * son taux ne dépasse pas 100 %.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceSuiviHeuresImplTest {

    private static final String TENANT = "28";
    private static final Long ENSEIGNANT = 2980L;
    private static final String ANNEE = "2026-2027";
    private static final int SEMAINE = 37;

    @Mock private SuiviHeuresEnseignantRepository suiviRepository;
    @Mock private SuiviHeuresMapper suiviMapper;

    private ServiceSuiviHeuresImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServiceSuiviHeuresImpl(suiviRepository, suiviMapper);

        when(suiviRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(suiviMapper.toResponse(any())).thenReturn(new SuiviHeuresEnseignantReponse());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Écriture ─────────────────────────────────────────────────────────────────

    @Test
    void ouvreUnSuiviRattacheALetablissementQuandLaSemaineEstVierge() {
        semaineEnBase(null);

        service.mettreAJourHeures(requete(20.0, 18.0));

        SuiviHeuresEnseignant enregistre = suiviCapte();
        assertThat(enregistre.getTenantId()).isEqualTo(TENANT);
        assertThat(enregistre.getEnseignantId()).isEqualTo(ENSEIGNANT);
        assertThat(enregistre.getNumeroSemaine()).isEqualTo(SEMAINE);
        assertThat(enregistre.getAnneeAcademique()).isEqualTo(ANNEE);
    }

    /**
     * Une seconde saisie sur la même semaine corrige la ligne existante. Sans
     * cette relecture, chaque mise à jour créerait un doublon et le résumé
     * annuel compterait la même semaine plusieurs fois.
     */
    @Test
    void corrigeLaSemaineExistanteAuLieuDenCreerUneSeconde() {
        SuiviHeuresEnseignant existant = semaineEnBase(new SuiviHeuresEnseignant());

        service.mettreAJourHeures(requete(20.0, 15.0));

        assertThat(suiviCapte()).isSameAs(existant);
    }

    @Test
    void deduitLesHeuresManqueesEtLeTauxDePresence() {
        semaineEnBase(null);

        service.mettreAJourHeures(requete(20.0, 15.0));

        SuiviHeuresEnseignant enregistre = suiviCapte();
        assertThat(enregistre.getHeuresManquees()).isEqualTo(5.0);
        assertThat(enregistre.getTauxPresence()).isCloseTo(75.0, within(0.001));
    }

    /** Des heures faites au-delà du quota ne fabriquent pas un manque négatif. */
    @Test
    void planchesLesHeuresManqueesAZeroQuandLeQuotaEstDepasse() {
        semaineEnBase(null);

        service.mettreAJourHeures(requete(18.0, 22.0));

        assertThat(suiviCapte().getHeuresManquees()).isZero();
    }

    /** Et le taux reste plafonné à 100 % : « 122 % de présence » ne veut rien dire. */
    @Test
    void plafonneLeTauxDePresenceACentPourCent() {
        semaineEnBase(null);

        service.mettreAJourHeures(requete(18.0, 22.0));

        assertThat(suiviCapte().getTauxPresence()).isEqualTo(100.0);
    }

    /**
     * Une semaine sans heures prévues — vacances, arrêt — donne un taux nul et
     * non une division par zéro.
     */
    @Test
    void rendUnTauxNulQuandAucuneHeureNetaitPrevue() {
        semaineEnBase(null);

        service.mettreAJourHeures(requete(0.0, 0.0));

        SuiviHeuresEnseignant enregistre = suiviCapte();
        assertThat(enregistre.getTauxPresence()).isZero();
        assertThat(enregistre.getHeuresManquees()).isZero();
    }

    @Test
    void reporteLaNoteDeLaSaisie() {
        semaineEnBase(null);
        MiseAJourHeuresRequete requete = requete(20.0, 18.0);
        requete.setNotes("Deux heures rendues en fin de semaine");

        service.mettreAJourHeures(requete);

        assertThat(suiviCapte().getNotes()).isEqualTo("Deux heures rendues en fin de semaine");
    }

    // ── Lecture ──────────────────────────────────────────────────────────────────

    @Test
    void relitLeResumeDuneSemaine() {
        semaineEnBase(new SuiviHeuresEnseignant());

        assertThat(service.obtenirResumeSemaine(ENSEIGNANT, SEMAINE, ANNEE)).isNotNull();
    }

    @Test
    void refuseUneSemaineJamaisSaisie() {
        semaineEnBase(null);

        assertThatThrownBy(() -> service.obtenirResumeSemaine(ENSEIGNANT, SEMAINE, ANNEE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(SEMAINE));
    }

    @Test
    void rendLesSemainesDeLanneeDemandee() {
        when(suiviRepository.findByTenantIdAndEnseignantIdAndAnneeAcademique(TENANT, ENSEIGNANT, ANNEE))
                .thenReturn(List.of(new SuiviHeuresEnseignant(), new SuiviHeuresEnseignant()));

        assertThat(service.obtenirResumeAnnuel(ENSEIGNANT, ANNEE)).hasSize(2);
    }

    @Test
    void rendUneAnneeVideQuandRienNaEteSaisi() {
        when(suiviRepository.findByTenantIdAndEnseignantIdAndAnneeAcademique(TENANT, ENSEIGNANT, ANNEE))
                .thenReturn(List.of());

        assertThat(service.obtenirResumeAnnuel(ENSEIGNANT, ANNEE)).isEmpty();
    }

    // ── Ce qui n'existe pas encore ───────────────────────────────────────────────

    /**
     * La synchronisation depuis le planning n'est pas implémentée, et échoue
     * bruyamment plutôt que de ne rien faire.
     *
     * <p>Ce test n'existe pas pour couvrir une ligne mais pour tenir la
     * distinction : un {@code UnsupportedOperationException} est une déclaration
     * d'intention, un retour silencieux serait un bogue. Le jour où le module
     * planning sera branché, ce test tombera — et c'est exactement ce qu'on
     * attend de lui.</p>
     */
    @Test
    void refuseExplicitementLaSynchronisationDepuisLePlanningTantQuelleNexistePas() {
        assertThatThrownBy(() -> service.synchroniserDepuisPlanning(ENSEIGNANT, SEMAINE, ANNEE, 20.0))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("planning");
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private MiseAJourHeuresRequete requete(double prevues, double realisees) {
        return MiseAJourHeuresRequete.builder()
                .enseignantId(ENSEIGNANT)
                .numeroSemaine(SEMAINE)
                .anneeAcademique(ANNEE)
                .heuresPrevues(prevues)
                .heuresRealisees(realisees)
                .build();
    }

    /** {@code null} pour une semaine vierge, une entité pour une semaine déjà saisie. */
    private SuiviHeuresEnseignant semaineEnBase(SuiviHeuresEnseignant existant) {
        when(suiviRepository.findByTenantIdAndEnseignantIdAndNumeroSemaineAndAnneeAcademique(
                TENANT, ENSEIGNANT, SEMAINE, ANNEE))
                .thenReturn(Optional.ofNullable(existant));
        return existant;
    }

    private SuiviHeuresEnseignant suiviCapte() {
        ArgumentCaptor<SuiviHeuresEnseignant> capteur = ArgumentCaptor.forClass(SuiviHeuresEnseignant.class);
        verify(suiviRepository).save(capteur.capture());
        return capteur.getValue();
    }
}
