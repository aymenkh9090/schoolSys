package tn.wtm.school.absence.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import tn.wtm.school.absence.dto.reponse.ResumeAbsenceEleveReponse;
import tn.wtm.school.absence.dto.reponse.StatistiquesAbsenceReponse;
import tn.wtm.school.absence.entity.LigneAppel;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.absence.repository.LigneAppelRepository;
import tn.wtm.school.absence.support.CapturePredicats;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les compteurs d'absentéisme, élève par élève puis classe par classe.
 *
 * <p>Le service ne fait rien d'autre que compter, mais c'est de ces chiffres
 * que sortent les tableaux de bord montrés à l'administration : une absence
 * comptée deux fois, ou un taux calculé sur le mauvais dénominateur, ne se voit
 * nulle part ailleurs qu'ici.</p>
 */
@ExtendWith(MockitoExtension.class)
class ServiceStatistiquesAbsenceImplTest {

    private static final String TENANT = "28";
    private static final String ANNEE = "2025-2026";

    @Mock private LigneAppelRepository ligneAppelRepository;

    private ServiceStatistiquesAbsenceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServiceStatistiquesAbsenceImpl(ligneAppelRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Résumé par élève ─────────────────────────────────────────────────────────

    @Test
    void ventileLesStatutsDeLeleveSurLannee() {
        when(ligneAppelRepository.findByEleveEtAnnee(TENANT, 12L, ANNEE)).thenReturn(List.of(
                ligne(1L, StatutPresence.ABSENT, true),
                ligne(2L, StatutPresence.ABSENT, false),
                ligne(3L, StatutPresence.ABSENT, false),
                ligne(4L, StatutPresence.RETARD, false),
                ligne(5L, StatutPresence.EXCLU, false),
                ligne(6L, StatutPresence.PRESENT, false)));

        ResumeAbsenceEleveReponse resume = service.resumeParEleve(12L, ANNEE);

        assertThat(resume.getTotalAbsences()).isEqualTo(3);
        assertThat(resume.getAbsencesJustifiees()).isEqualTo(1);
        assertThat(resume.getAbsencesNonJustifiees()).isEqualTo(2);
        assertThat(resume.getTotalRetards()).isEqualTo(1);
        assertThat(resume.getTotalExclusions()).isEqualTo(1);
        assertThat(resume.getPeriode()).isEqualTo(ANNEE);
    }

    /**
     * Régression : le drapeau {@code estJustifie} est nullable en base. Un
     * {@code Boolean} nul déréférencé aurait fait tomber le résumé, et un
     * justificatif porté par une ligne présente ne doit pas compter.
     */
    @Test
    void ignoreLeDrapeauJustifieQuandIlNestPasRenseigneOuPorteParUnePresence() {
        LigneAppel sansDrapeau = ligne(1L, StatutPresence.ABSENT, null);
        when(ligneAppelRepository.findByEleveEtAnnee(TENANT, 12L, ANNEE)).thenReturn(List.of(
                sansDrapeau,
                ligne(2L, StatutPresence.PRESENT, true)));

        ResumeAbsenceEleveReponse resume = service.resumeParEleve(12L, ANNEE);

        assertThat(resume.getTotalAbsences()).isEqualTo(1);
        assertThat(resume.getAbsencesJustifiees()).isZero();
        assertThat(resume.getAbsencesNonJustifiees()).isEqualTo(1);
    }

    @Test
    void refuseUnResumeSansEleve() {
        assertThatThrownBy(() -> service.resumeParEleve(null, ANNEE))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuseUnResumeSansAnneeAcademique() {
        assertThatThrownBy(() -> service.resumeParEleve(12L, "  "))
                .isInstanceOf(BadRequestException.class);
    }

    // ── Résumé par classe ────────────────────────────────────────────────────────

    @Test
    void compteLesSeancesDistinctesEtNonLesLignesDappel() {
        lignesDeLaClasse(List.of(
                ligne(1L, StatutPresence.PRESENT, false),
                ligne(1L, StatutPresence.ABSENT, false),
                ligne(2L, StatutPresence.PRESENT, false)));

        StatistiquesAbsenceReponse stats = service.resumeParClasse(3L, null, null);

        assertThat(stats.getTotalSeances()).isEqualTo(2);
        assertThat(stats.getGroupeClasseId()).isEqualTo(3L);
    }

    /** Le taux est un pourcentage arrondi au centième, pas une fraction brute. */
    @Test
    void arrondiLeTauxDabsenteismeAuCentieme() {
        lignesDeLaClasse(List.of(
                ligne(1L, StatutPresence.ABSENT, false),
                ligne(1L, StatutPresence.PRESENT, false),
                ligne(1L, StatutPresence.PRESENT, false)));

        assertThat(service.resumeParClasse(3L, null, null).getTauxAbsenteisme()).isEqualTo(33.33);
    }

    /** Sans ligne d'appel, le taux vaut zéro et non une division par zéro. */
    @Test
    void rendUnTauxNulQuandAucuneLigneNaEteRelevee() {
        lignesDeLaClasse(List.of());

        StatistiquesAbsenceReponse stats = service.resumeParClasse(3L, null, null);

        assertThat(stats.getTauxAbsenteisme()).isZero();
        assertThat(stats.getTotalSeances()).isZero();
    }

    @Test
    void refuseUnResumeDeClasseSansGroupe() {
        assertThatThrownBy(() -> service.resumeParClasse(null, null, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void neFiltreQueSurLetablissementEtLaClasseQuandLaPeriodeEstOuverte() {
        lignesDeLaClasse(List.of());

        service.resumeParClasse(3L, null, null);

        assertThat(CapturePredicats.assembles(specificationCaptee())).hasSize(2);
    }

    @Test
    void ajouteUnPredicatParBorneDePeriode() {
        lignesDeLaClasse(List.of());

        service.resumeParClasse(3L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(CapturePredicats.assembles(specificationCaptee())).hasSize(4);
    }

    /** Les bornes demandées sont rendues telles quelles, et libellent la période. */
    @Test
    void rappelleLesBornesDeLaPeriodeDansLaReponse() {
        lignesDeLaClasse(List.of());

        StatistiquesAbsenceReponse stats =
                service.resumeParClasse(3L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(stats.getDebut()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(stats.getFin()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(stats.getPeriode()).isEqualTo("2026-01-01 → 2026-01-31");
    }

    // ── Tableau de bord ──────────────────────────────────────────────────────────

    @Test
    void leTableauDeBordCouvreToutLetablissementSansFiltreDeClasse() {
        when(ligneAppelRepository.findByTenantId(TENANT)).thenReturn(List.of(
                ligne(1L, StatutPresence.ABSENT, true),
                ligne(1L, StatutPresence.EXCLU, false)));

        StatistiquesAbsenceReponse stats = service.dashboardAdmin(null, "Septembre 2026");

        assertThat(stats.getGroupeClasseId()).isNull();
        assertThat(stats.getTenantId()).isEqualTo(TENANT);
        assertThat(stats.getPeriode()).isEqualTo("Septembre 2026");
        assertThat(stats.getTotalJustifiees()).isEqualTo(1);
        assertThat(stats.getTotalExclusions()).isEqualTo(1);
    }

    /**
     * L'établissement peut être imposé par l'appelant — c'est ce qui permet à
     * une tâche d'administration de balayer plusieurs tenants — et il ne doit
     * alors pas être écrasé par celui du contexte.
     */
    @Test
    void respecteLetablissementImposeParLappelant() {
        when(ligneAppelRepository.findByTenantId("31")).thenReturn(List.of());

        StatistiquesAbsenceReponse stats = service.dashboardAdmin("31", "Septembre 2026");

        assertThat(stats.getTenantId()).isEqualTo("31");
        verify(ligneAppelRepository).findByTenantId("31");
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private LigneAppel ligne(Long seanceId, StatutPresence statut, Boolean justifie) {
        return LigneAppel.builder()
                .seanceAppel(SeanceAppel.builder().id(seanceId).build())
                .statut(statut)
                .estJustifie(justifie)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void lignesDeLaClasse(List<LigneAppel> lignes) {
        when(ligneAppelRepository.findAll(any(Specification.class))).thenReturn(lignes);
    }

    @SuppressWarnings("unchecked")
    private Specification<LigneAppel> specificationCaptee() {
        ArgumentCaptor<Specification<LigneAppel>> capteur = ArgumentCaptor.forClass(Specification.class);
        verify(ligneAppelRepository).findAll(capteur.capture());
        return capteur.getValue();
    }
}
