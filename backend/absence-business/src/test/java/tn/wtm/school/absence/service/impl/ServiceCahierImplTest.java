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
import tn.wtm.school.absence.dto.reponse.CahierCorpusReponse;
import tn.wtm.school.absence.dto.reponse.CahierSeanceReponse;
import tn.wtm.school.absence.dto.requete.EnregistrementCahierRequete;
import tn.wtm.school.absence.entity.CahierSeance;
import tn.wtm.school.absence.entity.SeanceAppel;
import tn.wtm.school.absence.mapper.CahierSeanceMapper;
import tn.wtm.school.absence.port.PortContexteScolaire;
import tn.wtm.school.absence.repository.CahierSeanceRepository;
import tn.wtm.school.absence.repository.SeanceAppelRepository;
import tn.wtm.school.absence.repository.projection.CahierCorpusRow;
import tn.wtm.school.absence.support.CapturePredicats;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.BusinessException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le cahier de séance : ce que l'enseignant écrit après son cours, et ce que
 * l'assistant en indexe.
 *
 * <p>Deux responsabilités que rien ne relie sinon la table. La première est un
 * cycle d'écriture verrouillable, la seconde la construction du corpus
 * d'indexation sémantique — dont le périmètre, le plafond et la borne basse
 * sont décidés ici et nulle part ailleurs.</p>
 */
@ExtendWith(MockitoExtension.class)
class ServiceCahierImplTest {

    private static final String TENANT = "28";
    private static final Long SEANCE_ID = 77L;

    @Mock private CahierSeanceRepository cahierRepository;
    @Mock private SeanceAppelRepository seanceAppelRepository;
    @Mock private CahierSeanceMapper cahierMapper;
    @Mock private PortContexteScolaire portContexteScolaire;

    private ServiceCahierImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new ServiceCahierImpl(cahierRepository, seanceAppelRepository, cahierMapper, portContexteScolaire);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Écriture ─────────────────────────────────────────────────────────────────

    @Test
    void ouvreUnCahierRattacheALaSeanceEtAsonEnseignantQuandAucunNexiste() {
        when(seanceAppelRepository.findByTenantIdAndId(TENANT, SEANCE_ID)).thenReturn(Optional.of(seance()));
        when(cahierRepository.findByTenantIdAndSeanceAppel_Id(TENANT, SEANCE_ID)).thenReturn(Optional.empty());
        ecritureRelue();

        service.enregistrer(SEANCE_ID, requete());

        CahierSeance enregistre = cahierCapte();
        assertThat(enregistre.getSeanceAppel().getId()).isEqualTo(SEANCE_ID);
        assertThat(enregistre.getEnseignantId()).isEqualTo(4L);
        assertThat(enregistre.getEstVerrouille()).isFalse();
        verify(cahierMapper).updateFromRequete(any(EnregistrementCahierRequete.class), eq(enregistre));
    }

    /** Une seconde saisie complète le cahier existant, elle n'en crée pas un second. */
    @Test
    void completeLeCahierExistantAuLieuDenOuvrirUnSecond() {
        CahierSeance existant = cahier(false);
        when(seanceAppelRepository.findByTenantIdAndId(TENANT, SEANCE_ID)).thenReturn(Optional.of(seance()));
        when(cahierRepository.findByTenantIdAndSeanceAppel_Id(TENANT, SEANCE_ID)).thenReturn(Optional.of(existant));
        ecritureRelue();

        service.enregistrer(SEANCE_ID, requete());

        assertThat(cahierCapte()).isSameAs(existant);
    }

    @Test
    void refuseDecrireDansUnCahierVerrouille() {
        when(seanceAppelRepository.findByTenantIdAndId(TENANT, SEANCE_ID)).thenReturn(Optional.of(seance()));
        when(cahierRepository.findByTenantIdAndSeanceAppel_Id(TENANT, SEANCE_ID)).thenReturn(Optional.of(cahier(true)));

        assertThatThrownBy(() -> service.enregistrer(SEANCE_ID, requete()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("verrouillé");
        verify(cahierRepository, never()).save(any());
    }

    @Test
    void refuseUnEnregistrementSansSeance() {
        assertThatThrownBy(() -> service.enregistrer(null, requete()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuseUnEnregistrementSurUneSeanceInconnue() {
        when(seanceAppelRepository.findByTenantIdAndId(TENANT, SEANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(SEANCE_ID, requete()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Verrouillage et lecture ──────────────────────────────────────────────────

    @Test
    void leVerrouillageHorodateLaFermetureDuCahier() {
        CahierSeance existant = cahier(false);
        when(cahierRepository.findByTenantIdAndSeanceAppel_Id(TENANT, SEANCE_ID)).thenReturn(Optional.of(existant));

        service.verrouiller(SEANCE_ID);

        assertThat(existant.getEstVerrouille()).isTrue();
        assertThat(existant.getVerrouillageAt()).isNotNull();
        verify(cahierRepository).save(existant);
    }

    @Test
    void refuseDeVerrouillerSansSeance() {
        assertThatThrownBy(() -> service.verrouiller(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuseDeVerrouillerUnCahierIntrouvable() {
        when(cahierRepository.findByTenantIdAndSeanceAppel_Id(TENANT, SEANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verrouiller(SEANCE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void relitLeCahierDuneSeance() {
        when(cahierRepository.findByTenantIdAndSeanceAppel_Id(TENANT, SEANCE_ID)).thenReturn(Optional.of(cahier(false)));
        when(cahierMapper.toResponse(any())).thenReturn(new CahierSeanceReponse());

        assertThat(service.recuperer(SEANCE_ID)).isNotNull();
    }

    @Test
    void refuseDeRelireSansSeance() {
        assertThatThrownBy(() -> service.recuperer(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuseDeRelireUnCahierIntrouvable() {
        when(cahierRepository.findByTenantIdAndSeanceAppel_Id(TENANT, SEANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recuperer(SEANCE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Historique de l'enseignant ───────────────────────────────────────────────

    @Test
    void refuseUnHistoriqueSansEnseignant() {
        assertThatThrownBy(() -> service.listerParEnseignant(null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class);
    }

    /** L'historique s'ouvre sur la dernière séance faite, pas sur la première de l'année. */
    @Test
    void classeLhistoriqueDeLaSeanceLaPlusRecenteALaPlusAncienne() {
        pageVide();

        service.listerParEnseignant(4L, null, null, PageRequest.of(0, 20));

        assertThat(pageableCapte().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "seanceAppel.ouvertureAt"));
    }

    @Test
    void respecteLeTriExpliciteDeLappelant() {
        pageVide();
        Pageable demande = PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "sujet"));

        service.listerParEnseignant(4L, null, null, demande);

        assertThat(pageableCapte()).isEqualTo(demande);
    }

    @Test
    void neFiltreQueSurLetablissementEtLenseignantQuandLaPeriodeEstOuverte() {
        pageVide();

        service.listerParEnseignant(4L, null, null, PageRequest.of(0, 20));

        assertThat(CapturePredicats.assembles(specificationCaptee())).hasSize(2);
    }

    @Test
    void ajouteUnPredicatParBorneDePeriode() {
        pageVide();

        service.listerParEnseignant(4L, LocalDateTime.now().minusWeeks(1), LocalDateTime.now(), PageRequest.of(0, 20));

        assertThat(CapturePredicats.assembles(specificationCaptee())).hasSize(4);
    }

    // ── Corpus d'indexation ──────────────────────────────────────────────────────

    /**
     * Le périmètre se déduit du compte : un enseignant n'indexe que ses propres
     * séances. Il n'est jamais reçu en paramètre — ce serait la porte ouverte à
     * la lecture du cahier du voisin.
     */
    @Test
    void restreintLeCorpusAuxSeancesDeLenseignantConnecte() {
        when(portContexteScolaire.idEnseignantCourant()).thenReturn(Optional.of(4L));
        corpusEnBase(List.of());

        service.construireCorpus(LocalDate.of(2026, 1, 1), 50);

        verify(cahierRepository).rechercherPourCorpus(eq(TENANT), eq(4L), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void ouvreLeCorpusAToutLetablissementQuandLappelantNestPasEnseignant() {
        when(portContexteScolaire.idEnseignantCourant()).thenReturn(Optional.empty());
        corpusEnBase(List.of());

        service.construireCorpus(LocalDate.of(2026, 1, 1), 50);

        verify(cahierRepository).rechercherPourCorpus(eq(TENANT), isNull(), any(LocalDate.class), any(Pageable.class));
    }

    /** Le plafond est dur : une limite démesurée ne ramène pas trois ans d'historique. */
    @Test
    void plafonneLaLimiteDemandee() {
        when(portContexteScolaire.idEnseignantCourant()).thenReturn(Optional.empty());
        corpusEnBase(List.of());

        service.construireCorpus(null, 100_000);

        assertThat(pageableCorpusCapte().getPageSize()).isEqualTo(2_000);
    }

    @Test
    void rameneUneLimiteNulleOuNegativeAUneSeuleSeance() {
        when(portContexteScolaire.idEnseignantCourant()).thenReturn(Optional.empty());
        corpusEnBase(List.of());

        service.construireCorpus(null, 0);

        assertThat(pageableCorpusCapte().getPageSize()).isEqualTo(1);
    }

    /**
     * « Aucune borne » se traduit par une borne plancher et non par un null :
     * la requête refuse un paramètre date nul.
     */
    @Test
    void substitueUneBornePlancherQuandAucuneDateNestDemandee() {
        when(portContexteScolaire.idEnseignantCourant()).thenReturn(Optional.empty());
        corpusEnBase(List.of());

        service.construireCorpus(null, 50);

        ArgumentCaptor<LocalDate> capteur = ArgumentCaptor.forClass(LocalDate.class);
        verify(cahierRepository).rechercherPourCorpus(anyString(), isNull(), capteur.capture(), any(Pageable.class));
        assertThat(capteur.getValue()).isEqualTo(LocalDate.EPOCH);
    }

    /**
     * Les libellés sont résolus en trois requêtes pour tout le corpus, et non
     * trois par séance : sur 2 000 séances, la version naïve ferait 6 000
     * allers-retours.
     */
    @Test
    void resoutLesLibellesEnTroisRequetesPourToutLeCorpus() {
        when(portContexteScolaire.idEnseignantCourant()).thenReturn(Optional.empty());
        corpusEnBase(List.of(ligneCorpus(1L, 4L, 9L, 2L), ligneCorpus(2L, 4L, 9L, 3L)));
        when(portContexteScolaire.nomsEnseignants(anyString(), anyCollection())).thenReturn(Map.of(4L, "Mme Trabelsi"));
        when(portContexteScolaire.codesClasses(anyString(), anyCollection())).thenReturn(Map.of(9L, "7B"));
        when(portContexteScolaire.libellesMatieres(anyString(), anyCollection())).thenReturn(Map.of(2L, "SVT"));

        List<CahierCorpusReponse> corpus = service.construireCorpus(null, 50);

        assertThat(corpus).hasSize(2);
        assertThat(corpus.get(0).getEnseignantNom()).isEqualTo("Mme Trabelsi");
        assertThat(corpus.get(0).getClasseCode()).isEqualTo("7B");
        assertThat(corpus.get(0).getMatiereLibelle()).isEqualTo("SVT");
        // La matière 3 n'a pas de libellé connu : le champ reste vide, la séance reste.
        assertThat(corpus.get(1).getMatiereLibelle()).isNull();
        assertThat(idsDemandesAuxEnseignants()).containsExactly(4L);
    }

    /** Un corpus vide n'appelle aucune requête de libellés. */
    @Test
    void neResoutAucunLibelleQuandLeCorpusEstVide() {
        when(portContexteScolaire.idEnseignantCourant()).thenReturn(Optional.empty());
        corpusEnBase(List.of());

        assertThat(service.construireCorpus(null, 50)).isEmpty();
        verify(portContexteScolaire, never()).nomsEnseignants(anyString(), anyCollection());
        verify(portContexteScolaire, never()).codesClasses(anyString(), anyCollection());
        verify(portContexteScolaire, never()).libellesMatieres(anyString(), anyCollection());
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private SeanceAppel seance() {
        return SeanceAppel.builder().id(SEANCE_ID).enseignantId(4L).build();
    }

    private CahierSeance cahier(boolean verrouille) {
        return CahierSeance.builder()
                .id(5L)
                .seanceAppel(seance())
                .enseignantId(4L)
                .estVerrouille(verrouille)
                .build();
    }

    private EnregistrementCahierRequete requete() {
        return EnregistrementCahierRequete.builder()
                .sujet("Les fonctions affines")
                .chapitre("Chapitre 3")
                .build();
    }

    private CahierCorpusRow ligneCorpus(Long id, Long enseignantId, Long classeId, Long matiereId) {
        return new CahierCorpusRow(id, SEANCE_ID, enseignantId, classeId, matiereId,
                LocalDate.of(2026, 3, 2), "2025-2026",
                "Les fonctions affines", "Chapitre 3", "Exercices 12 à 18",
                null, "Exercice 19", LocalDate.of(2026, 3, 9));
    }

    /** Le couple save + mapper, stubé seulement là où l'écriture aboutit. */
    private void ecritureRelue() {
        when(cahierRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cahierMapper.toResponse(any())).thenReturn(new CahierSeanceReponse());
    }

    @SuppressWarnings("unchecked")
    private void pageVide() {
        when(cahierRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    }

    private void corpusEnBase(List<CahierCorpusRow> lignes) {
        when(cahierRepository.rechercherPourCorpus(anyString(), any(), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(lignes);
    }

    private CahierSeance cahierCapte() {
        ArgumentCaptor<CahierSeance> capteur = ArgumentCaptor.forClass(CahierSeance.class);
        verify(cahierRepository).save(capteur.capture());
        return capteur.getValue();
    }

    @SuppressWarnings("unchecked")
    private Pageable pageableCapte() {
        ArgumentCaptor<Pageable> capteur = ArgumentCaptor.forClass(Pageable.class);
        verify(cahierRepository).findAll(any(Specification.class), capteur.capture());
        return capteur.getValue();
    }

    @SuppressWarnings("unchecked")
    private Specification<CahierSeance> specificationCaptee() {
        ArgumentCaptor<Specification<CahierSeance>> capteur = ArgumentCaptor.forClass(Specification.class);
        verify(cahierRepository).findAll(capteur.capture(), any(Pageable.class));
        return capteur.getValue();
    }

    private Pageable pageableCorpusCapte() {
        ArgumentCaptor<Pageable> capteur = ArgumentCaptor.forClass(Pageable.class);
        verify(cahierRepository).rechercherPourCorpus(anyString(), any(), any(LocalDate.class), capteur.capture());
        return capteur.getValue();
    }

    @SuppressWarnings("unchecked")
    private Set<Long> idsDemandesAuxEnseignants() {
        ArgumentCaptor<Set<Long>> capteur = ArgumentCaptor.forClass(Set.class);
        verify(portContexteScolaire).nomsEnseignants(anyString(), capteur.capture());
        return capteur.getValue();
    }
}
