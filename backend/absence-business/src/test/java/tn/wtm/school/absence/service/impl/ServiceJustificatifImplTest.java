package tn.wtm.school.absence.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import tn.wtm.school.absence.dto.reponse.JustificatifReponse;
import tn.wtm.school.absence.dto.requete.SoumissionJustificatifRequete;
import tn.wtm.school.absence.dto.requete.TraitementJustificatifRequete;
import tn.wtm.school.absence.entity.JustificatifAbsence;
import tn.wtm.school.absence.entity.LigneAppel;
import tn.wtm.school.absence.enums.StatutJustificatif;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.absence.enums.TypeJustificatif;
import tn.wtm.school.absence.mapper.JustificatifMapper;
import tn.wtm.school.absence.repository.JustificatifAbsenceRepository;
import tn.wtm.school.absence.repository.LigneAppelRepository;
import tn.wtm.school.absence.support.CapturePredicats;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le cycle de vie d'un justificatif : dépôt, approbation, refus, consultation.
 *
 * <p>Le service ne fait aucun calcul mais porte quatre règles que rien
 * n'empêchait de violer jusqu'ici : un justificatif ne se dépose que sur une
 * absence, il est imputable à un compte, il ne se traite qu'une fois, et seule
 * l'approbation rend l'absence justifiée.</p>
 */
@ExtendWith(MockitoExtension.class)
class ServiceJustificatifImplTest {

    private static final String TENANT = "28";
    private static final Long LIGNE_ID = 9L;
    private static final Long JUSTIFICATIF_ID = 44L;

    @Mock private JustificatifAbsenceRepository justificatifRepository;
    @Mock private LigneAppelRepository ligneAppelRepository;
    @Mock private JustificatifMapper justificatifMapper;

    private ServiceJustificatifImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServiceJustificatifImpl(justificatifRepository, ligneAppelRepository, justificatifMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Dépôt ────────────────────────────────────────────────────────────────────

    @Test
    void deposeUnJustificatifEnAttenteRattacheALaLigneEtALEleve() {
        ligneEnBase(ligne(StatutPresence.ABSENT));
        ecritureRelue();

        service.soumettre(soumission(7L));

        JustificatifAbsence enregistre = justificatifCapte();
        assertThat(enregistre.getStatut()).isEqualTo(StatutJustificatif.EN_ATTENTE);
        assertThat(enregistre.getEleveId()).isEqualTo(12L);
        assertThat(enregistre.getSoumisParId()).isEqualTo(7L);
        assertThat(enregistre.getTypeDocument()).isEqualTo(TypeJustificatif.MEDICAL);
        assertThat(enregistre.getSoumisAt()).isNotNull();
        assertThat(enregistre.getTraiteAt()).isNull();
    }

    @Test
    void refuseUnJustificatifSurUneLigneQuiNestPasUneAbsence() {
        ligneEnBase(ligne(StatutPresence.RETARD));

        assertThatThrownBy(() -> service.soumettre(soumission(7L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ABSENT");
    }

    /** Sans fiche utilisateur rattachée au tenant, le dépôt n'est imputable à personne. */
    @Test
    void refuseUnDepotQueAucunCompteNeSigne() {
        ligneEnBase(ligne(StatutPresence.ABSENT));

        assertThatThrownBy(() -> service.soumettre(soumission(null)))
                .isInstanceOf(BadRequestException.class);
        verify(justificatifRepository, never()).save(any());
    }

    @Test
    void refuseUnDepotSurUneLigneDappelInconnue() {
        when(ligneAppelRepository.findByTenantIdAndId(TENANT, LIGNE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.soumettre(soumission(7L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Décision ─────────────────────────────────────────────────────────────────

    @Test
    void lapprobationRendLAbsenceJustifieeEtHorodateLaDecision() {
        LigneAppel ligne = ligne(StatutPresence.ABSENT);
        JustificatifAbsence justificatif = justificatifEnBase(ligne, StatutJustificatif.EN_ATTENTE);
        ecritureRelue();

        service.approuver(JUSTIFICATIF_ID, decision(StatutJustificatif.VALIDE, 3L, "Certificat conforme"));

        assertThat(justificatif.getStatut()).isEqualTo(StatutJustificatif.VALIDE);
        assertThat(justificatif.getTraiteAt()).isNotNull();
        assertThat(justificatif.getTraiteParId()).isEqualTo(3L);
        assertThat(justificatif.getNotesAdmin()).isEqualTo("Certificat conforme");
        assertThat(ligne.getEstJustifie()).isTrue();
        verify(ligneAppelRepository).save(ligne);
    }

    /**
     * Le refus est une décision, pas un non-événement : il se trace comme
     * l'approbation, mais laisse l'absence non justifiée.
     */
    @Test
    void leRefusNeTouchePasAuCaractereJustifieDeLAbsence() {
        LigneAppel ligne = ligne(StatutPresence.ABSENT);
        JustificatifAbsence justificatif = justificatifEnBase(ligne, StatutJustificatif.EN_ATTENTE);
        ecritureRelue();

        service.refuser(JUSTIFICATIF_ID, decision(StatutJustificatif.REFUSE, 3L, "Document illisible"));

        assertThat(justificatif.getStatut()).isEqualTo(StatutJustificatif.REFUSE);
        assertThat(justificatif.getTraiteAt()).isNotNull();
        assertThat(ligne.getEstJustifie()).isFalse();
        verify(ligneAppelRepository, never()).save(any());
    }

    @Test
    void refuseDeTraiterDeuxFoisLeMemeJustificatif() {
        justificatifEnBase(ligne(StatutPresence.ABSENT), StatutJustificatif.VALIDE);

        assertThatThrownBy(() -> service.approuver(JUSTIFICATIF_ID, decision(StatutJustificatif.VALIDE, 3L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été traité");
    }

    @Test
    void refuseUneDecisionAbsente() {
        assertThatThrownBy(() -> service.approuver(JUSTIFICATIF_ID, decision(null, 3L, null)))
                .isInstanceOf(BadRequestException.class);
    }

    /** {@code EN_ATTENTE} est un état de départ, pas une décision d'arrivée. */
    @Test
    void refuseEnAttenteCommeDecision() {
        assertThatThrownBy(() -> service.refuser(JUSTIFICATIF_ID, decision(StatutJustificatif.EN_ATTENTE, 3L, null)))
                .isInstanceOf(BadRequestException.class);
    }

    /**
     * Régression : la décision portée par le corps de la requête doit
     * correspondre à l'opération appelée, sinon l'URL et le contenu disent deux
     * choses différentes et c'est l'URL qui gagne silencieusement.
     */
    @Test
    void refuseUneDecisionIncoherenteAvecLoperationAppelee() {
        assertThatThrownBy(() -> service.approuver(JUSTIFICATIF_ID, decision(StatutJustificatif.REFUSE, 3L, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("incohérente");
    }

    @Test
    void refuseUnIdentifiantDeJustificatifAbsent() {
        assertThatThrownBy(() -> service.approuver(null, decision(StatutJustificatif.VALIDE, 3L, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuseUnJustificatifIntrouvable() {
        when(justificatifRepository.findByTenantIdAndId(TENANT, JUSTIFICATIF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refuser(JUSTIFICATIF_ID, decision(StatutJustificatif.REFUSE, 3L, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Consultation ─────────────────────────────────────────────────────────────

    @Test
    void listeDuPlusRecentAuPlusAncienQuandAucunTriNestDemande() {
        pageVide();

        service.lister(null, null, null, null, PageRequest.of(0, 20));

        assertThat(pageableCapte().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "soumisAt"));
    }

    @Test
    void respecteLeTriExpliciteDeLappelant() {
        pageVide();
        Pageable demande = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "eleveId"));

        service.lister(null, null, null, null, demande);

        assertThat(pageableCapte()).isEqualTo(demande);
    }

    /** Sans critère, seul le cloisonnement par établissement subsiste. */
    @Test
    void neFiltreQueSurLetablissementQuandAucunCritereNestRenseigne() {
        pageVide();

        service.lister(null, null, null, null, PageRequest.of(0, 20));

        assertThat(CapturePredicats.assembles(specificationCaptee())).hasSize(1);
    }

    @Test
    void ajouteUnPredicatParCritereRenseigne() {
        pageVide();

        service.lister(12L, StatutJustificatif.EN_ATTENTE,
                LocalDateTime.now().minusMonths(1), LocalDateTime.now(), PageRequest.of(0, 20));

        assertThat(CapturePredicats.assembles(specificationCaptee())).hasSize(5);
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private LigneAppel ligne(StatutPresence statut) {
        return LigneAppel.builder()
                .id(LIGNE_ID)
                .eleveId(12L)
                .statut(statut)
                .estJustifie(false)
                .build();
    }

    private void ligneEnBase(LigneAppel ligne) {
        when(ligneAppelRepository.findByTenantIdAndId(TENANT, LIGNE_ID)).thenReturn(Optional.of(ligne));
    }

    /** Le couple save + mapper, stubé seulement là où l'écriture aboutit. */
    private void ecritureRelue() {
        when(justificatifRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(justificatifMapper.toResponse(any())).thenReturn(new JustificatifReponse());
    }

    private JustificatifAbsence justificatifEnBase(LigneAppel ligne, StatutJustificatif statut) {
        JustificatifAbsence justificatif = JustificatifAbsence.builder()
                .id(JUSTIFICATIF_ID)
                .ligneAppel(ligne)
                .eleveId(ligne.getEleveId())
                .statut(statut)
                .build();
        when(justificatifRepository.findByTenantIdAndId(TENANT, JUSTIFICATIF_ID)).thenReturn(Optional.of(justificatif));
        return justificatif;
    }

    private SoumissionJustificatifRequete soumission(Long soumisParId) {
        return SoumissionJustificatifRequete.builder()
                .ligneAppelId(LIGNE_ID)
                .typeDocument(TypeJustificatif.MEDICAL)
                .referenceDocument("CM-2026-118")
                .soumisParId(soumisParId)
                .build();
    }

    private TraitementJustificatifRequete decision(StatutJustificatif decision, Long traiteParId, String notes) {
        return TraitementJustificatifRequete.builder()
                .decision(decision)
                .traiteParId(traiteParId)
                .notesAdmin(notes)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void pageVide() {
        when(justificatifRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    private JustificatifAbsence justificatifCapte() {
        ArgumentCaptor<JustificatifAbsence> capteur = ArgumentCaptor.forClass(JustificatifAbsence.class);
        verify(justificatifRepository).save(capteur.capture());
        return capteur.getValue();
    }

    @SuppressWarnings("unchecked")
    private Pageable pageableCapte() {
        ArgumentCaptor<Pageable> capteur = ArgumentCaptor.forClass(Pageable.class);
        verify(justificatifRepository).findAll(any(Specification.class), capteur.capture());
        return capteur.getValue();
    }

    @SuppressWarnings("unchecked")
    private Specification<JustificatifAbsence> specificationCaptee() {
        ArgumentCaptor<Specification<JustificatifAbsence>> capteur = ArgumentCaptor.forClass(Specification.class);
        verify(justificatifRepository).findAll(capteur.capture(), any(Pageable.class));
        return capteur.getValue();
    }
}
