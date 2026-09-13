package tn.wtm.school.org.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.org.dto.request.ApplyNationalPatternRequest;
import tn.wtm.school.org.dto.response.NationalPatternResponse;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.NationalPattern;
import tn.wtm.school.org.entity.NationalPatternDetail;
import tn.wtm.school.org.entity.NationalPatternSession;
import tn.wtm.school.org.entity.Pattern;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.enums.PatternType;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.WeekParity;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.NationalPatternRepository;
import tn.wtm.school.org.repository.PatternDetailRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.service.NationalPatternService.ApplyNationalResult;
import tn.wtm.school.org.service.NationalPatternService.NationalPatternApplyResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Appliquer un programme national à un établissement : le niveau, les matières
 * et les liens matière-niveau manquants sont créés, puis un pattern par matière,
 * sans jamais dupliquer ce qui existe déjà.
 *
 * Les dépôts de matières et de liens matière-niveau sont simulés en mémoire :
 * ce qui est enregistré pendant la mise en place est retrouvé ensuite, comme en base.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NationalPatternServiceImplTest {

    @Mock NationalPatternRepository    nationalPatternRepository;
    @Mock SubjectRepository            subjectRepository;
    @Mock LevelRepository              levelRepository;
    @Mock SubjectLevelRepository       subjectLevelRepository;
    @Mock PatternRepository            patternRepository;
    @Mock PatternDetailRepository      patternDetailRepository;
    @Mock SchoolYearRepository         schoolYearRepository;
    @Mock SubjectSessionTypeRepository subjectSessionTypeRepository;

    NationalPatternServiceImpl service;

    static final String TENANT = "tenant-1";
    static final Long   NP_ID  = 100L;

    final Map<String, Subject>      matieres        = new HashMap<>();
    final Map<String, SubjectLevel> matieresNiveaux = new HashMap<>();
    long sequence = 1;

    @BeforeEach
    void setUp() {
        service = new NationalPatternServiceImpl(nationalPatternRepository, subjectRepository, levelRepository,
                subjectLevelRepository, patternRepository, patternDetailRepository, schoolYearRepository,
                subjectSessionTypeRepository);
        TenantContext.setTenantId(TENANT);

        when(levelRepository.save(any())).thenAnswer(inv -> {
            Level l = inv.getArgument(0);
            l.setIdNiveau(sequence++);
            return l;
        });
        when(subjectRepository.findByTenantIdAndCodeMatiere(eq(TENANT), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(matieres.get(inv.<String>getArgument(1))));
        when(subjectRepository.save(any())).thenAnswer(inv -> {
            Subject s = inv.getArgument(0);
            s.setIdMatiere(sequence++);
            matieres.put(s.getCodeMatiere(), s);
            return s;
        });
        when(subjectLevelRepository.findBySubject_IdMatiereAndLevel_IdNiveau(anyLong(), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(matieresNiveaux.get(inv.getArgument(0) + "/" + inv.getArgument(1))));
        when(subjectLevelRepository.save(any())).thenAnswer(inv -> {
            SubjectLevel sl = inv.getArgument(0);
            sl.setIdNiveauMatiere(sequence++);
            matieresNiveaux.put(sl.getSubject().getIdMatiere() + "/" + sl.getLevel().getIdNiveau(), sl);
            return sl;
        });
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── données ───────────────────────────────────────────────────────────────

    static NationalPatternSession seance(int ordre, SessionType type, double duree,
                                         String groupement, String salle, String parite) {
        return NationalPatternSession.builder().idNationalPatternSession((long) ordre).sessionOrder(ordre)
                .sessionType(type).duration(duree).groupingType(groupement).requiredRoomType(salle)
                .weekParity(parite).build();
    }

    static NationalPatternDetail detail(String matiere, double heures, NationalPatternSession... seances) {
        return NationalPatternDetail.builder().subjectCode(matiere).totalHoursPerWeek(heures)
                .repartition("1+1").sessions(new ArrayList<>(List.of(seances))).build();
    }

    static NationalPattern programme(String niveau, NationalPatternDetail... details) {
        return NationalPattern.builder().idNationalPattern(NP_ID).code("TN_" + niveau).name("Programme " + niveau)
                .version(1).academicYear(2026).active(true).countryCode("TN").levelCode(niveau)
                .details(new ArrayList<>(List.of(details))).build();
    }

    /** Le programme type : maths (deux cours), sciences (un cours + un TP en demi-groupe, une semaine sur deux), sport. */
    static NationalPattern programme7eme() {
        return programme("7EME",
                detail("MATH", 2.5,
                        seance(2, SessionType.COURSE, 1.0, null, null, null),
                        seance(1, SessionType.COURSE, 1.5, null, null, null)),
                detail("SCI", 2.0,
                        seance(1, SessionType.COURSE, 1.0, "CLASSE", null, null),
                        seance(2, SessionType.TP, 1.0, "DEMI_GROUP", "LAB_SCIENCE", "BIWEEKLY")),
                detail("SPORT", 2.0,
                        seance(1, SessionType.SPORT, 2.0, null, "SALLE_SPORT", null)));
    }

    List<Pattern> patternsEnregistres(int nombre) {
        ArgumentCaptor<Pattern> captor = ArgumentCaptor.forClass(Pattern.class);
        verify(patternRepository, org.mockito.Mockito.times(nombre)).save(captor.capture());
        return captor.getAllValues();
    }

    Pattern patternDe(List<Pattern> patterns, String nom) {
        return patterns.stream().filter(p -> p.getName().equals(nom)).findFirst().orElseThrow();
    }

    // ── application à l'établissement ─────────────────────────────────────────

    @Nested
    class Application {

        @Test
        void sansTenant_refuse() {
            TenantContext.clear();
            assertThatThrownBy(() -> service.applyToTenant(NP_ID, null))
                    .isInstanceOf(tn.wtm.school.common.exceptions.TenantSecurityException.class);
        }

        @Test
        void programmeInconnu_introuvable() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyToTenant(NP_ID, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(NP_ID.toString());
        }

        @Test
        void anneeScolaireInconnue_introuvable() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, 9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyToTenant(NP_ID, 9L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Année scolaire");
            verify(patternRepository, never()).save(any());
        }

        @Test
        void etablissementVide_creeNiveauMatieresLiensEtPatterns() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));
            when(levelRepository.findByTenantIdAndCode(TENANT, "7EME")).thenReturn(Optional.empty());

            NationalPatternApplyResult resultat = service.applyToTenant(NP_ID, null);

            assertThat(resultat.created()).isEqualTo(3);
            assertThat(resultat.skipped()).isZero();
            assertThat(resultat.message()).isEqualTo("Créé : 3, ignoré : 0");

            ArgumentCaptor<Level> niveau = ArgumentCaptor.forClass(Level.class);
            verify(levelRepository).save(niveau.capture());
            assertThat(niveau.getValue().getNom()).isEqualTo("7ème de Base");
            assertThat(niveau.getValue().getTenantId()).isEqualTo(TENANT);

            assertThat(matieres).containsOnlyKeys("MATH", "SCI", "SPORT");
            assertThat(matieres.get("SCI").getLibMatiere()).isEqualTo("Sciences de la Vie et de la Terre");
            assertThat(matieres.get("SCI").getNecessiteLab()).isTrue();
            assertThat(matieres.get("SCI").getNecessiteSport()).isFalse();
            assertThat(matieres.get("SPORT").getNecessiteSport()).isTrue();
            assertThat(matieres.get("MATH").getNecessiteLab()).isFalse();
            assertThat(matieresNiveaux).hasSize(3);
        }

        @Test
        void patternHebdomadaire_seancesTrieesParOrdre() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));

            service.applyToTenant(NP_ID, null);

            Pattern maths = patternDe(patternsEnregistres(3), "TN_7EME_MATH");
            assertThat(maths.getPatternType()).isEqualTo(PatternType.WEEKLY_IDENTICAL);
            assertThat(maths.getTotalHours()).isEqualTo(2.5);
            assertThat(maths.getSessionCount()).isEqualTo(2);
            assertThat(maths.getTenantId()).isEqualTo(TENANT);
            assertThat(maths.getPatternDetails()).extracting(PatternDetail::getDuration).containsExactly(1.5, 1.0);
            assertThat(maths.getPatternDetails()).allSatisfy(d -> {
                assertThat(d.getWeekParity()).isEqualTo(WeekParity.ALL);
                assertThat(d.getRequiredRoomType()).isEqualTo(RoomType.NORMALE);
                assertThat(d.getIsSplit()).isFalse();
                assertThat(d.getPattern()).isSameAs(maths);
            });
        }

        @Test
        void tpEnDemiGroupeUneSemaineSurDeux_patternBimensuelEnLabo() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));

            service.applyToTenant(NP_ID, null);

            Pattern sciences = patternDe(patternsEnregistres(3), "TN_7EME_SCI");
            assertThat(sciences.getPatternType()).isEqualTo(PatternType.BIWEEKLY);
            PatternDetail tp = sciences.getPatternDetails().get(1);
            assertThat(tp.getType()).isEqualTo(SessionType.TP);
            assertThat(tp.getIsSplit()).isTrue();
            assertThat(tp.getWeekParity()).isEqualTo(WeekParity.BIWEEKLY);
            assertThat(tp.getRequiredRoomType()).isEqualTo(RoomType.LABSCIENCE);
        }

        @Test
        void anneeScolaireFournie_rattacheeAuxPatterns() {
            SchoolYear annee = SchoolYear.builder().idAnnee(9L).nom("2026-2027").build();
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));
            when(schoolYearRepository.findByTenantIdAndIdAnnee(TENANT, 9L)).thenReturn(Optional.of(annee));

            service.applyToTenant(NP_ID, 9L);

            assertThat(patternsEnregistres(3)).allSatisfy(p -> assertThat(p.getSchoolYear()).isSameAs(annee));
            verify(patternRepository).existsByNameAndSubjectLevelAndYear(eq("TN_7EME_MATH"), anyLong(), eq(9L), isNull());
        }

        @Test
        void patternDejaPresent_ignore_niveauExistantReutilise() {
            Level existant = Level.builder().idNiveau(50L).code("7EME").nom("7ème").build();
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));
            when(levelRepository.findByTenantIdAndCode(TENANT, "7EME")).thenReturn(Optional.of(existant));
            when(patternRepository.existsByNameAndSubjectLevelAndYear(eq("TN_7EME_SPORT"), anyLong(), isNull(), isNull()))
                    .thenReturn(true);

            NationalPatternApplyResult resultat = service.applyToTenant(NP_ID, null);

            assertThat(resultat.created()).isEqualTo(2);
            assertThat(resultat.skipped()).isEqualTo(1);
            verify(levelRepository, never()).save(any());
            assertThat(matieresNiveaux.values()).allSatisfy(sl -> assertThat(sl.getLevel()).isSameAs(existant));
        }

        @Test
        void typesDeSeance_seulsLesManquantsSontCrees() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(
                    programme("7EME", detail("SCI", 2.0,
                            seance(1, SessionType.COURSE, 1.0, null, null, null),
                            seance(2, SessionType.TP, 1.5, "DEMI_GROUP", "LAB_SCIENCE", null),
                            seance(3, SessionType.TP, 1.0, null, "LAB_SCIENCE", null)))));
            when(subjectSessionTypeRepository.findBySubjectLevel_IdNiveauMatiere(anyLong()))
                    .thenReturn(List.of(SubjectSessionType.builder().type(SessionType.COURSE).build()));

            service.applyToTenant(NP_ID, null);

            ArgumentCaptor<SubjectSessionType> cree = ArgumentCaptor.forClass(SubjectSessionType.class);
            verify(subjectSessionTypeRepository).save(cree.capture());
            SubjectSessionType tp = cree.getValue();
            assertThat(tp.getType()).isEqualTo(SessionType.TP);
            assertThat(tp.getDuration()).isEqualTo(1.5);
            assertThat(tp.getRequiresSplit()).isTrue();
            assertThat(tp.getGroupCount()).isEqualTo(2);
            assertThat(tp.getTenantId()).isEqualTo(TENANT);
        }

        @Test
        void typeDeSeanceSansDemiGroupe_pasDeNombreDeGroupes() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(
                    programme("7EME", detail("MATH", 1.0, seance(1, SessionType.COURSE, 1.0, null, null, null)))));

            service.applyToTenant(NP_ID, null);

            ArgumentCaptor<SubjectSessionType> cree = ArgumentCaptor.forClass(SubjectSessionType.class);
            verify(subjectSessionTypeRepository).save(cree.capture());
            assertThat(cree.getValue().getRequiresSplit()).isFalse();
            assertThat(cree.getValue().getGroupCount()).isNull();
        }

        @Test
        void matiereOuLienIntrouvablesApresMiseEnPlace_ignores() {
            // Dépôts qui « perdent » ce qu'on leur confie : la mise en place passe,
            // mais la boucle ne retrouve ni la matière ni le lien matière-niveau.
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(
                    programme("8EME",
                            detail("FR", 4.0, seance(1, SessionType.COURSE, 1.0, null, null, null)),
                            detail("AR", 5.0, seance(1, SessionType.COURSE, 1.0, null, null, null)))));
            Subject arabe = Subject.builder().idMatiere(77L).codeMatiere("AR").build();
            when(subjectRepository.findByTenantIdAndCodeMatiere(TENANT, "FR")).thenReturn(Optional.empty());
            when(subjectRepository.findByTenantIdAndCodeMatiere(TENANT, "AR")).thenReturn(Optional.of(arabe));
            // doAnswer : `when(save(any()))` rappellerait la réponse de setUp avec un argument nul.
            doAnswer(inv -> {
                Subject s = inv.getArgument(0);
                s.setIdMatiere(78L);
                return s;
            }).when(subjectRepository).save(any());
            when(subjectLevelRepository.findBySubject_IdMatiereAndLevel_IdNiveau(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());
            doAnswer(inv -> inv.getArgument(0)).when(subjectLevelRepository).save(any());

            NationalPatternApplyResult resultat = service.applyToTenant(NP_ID, null);

            assertThat(resultat.created()).isZero();
            assertThat(resultat.skipped()).isEqualTo(2);
            verify(patternRepository, never()).save(any());
        }
    }

    // ── application par pays et niveaux ───────────────────────────────────────

    @Test
    void parRequete_chaqueNiveauEstRapporte_memeSansProgramme() {
        when(nationalPatternRepository.findActiveWithDetailsByCountryAndLevel("TN", "7EME"))
                .thenReturn(List.of(programme7eme()));
        when(nationalPatternRepository.findActiveWithDetailsByCountryAndLevel("TN", "8EME"))
                .thenReturn(List.of());
        when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));

        ApplyNationalResult resultat = service.applyByRequest(
                new ApplyNationalPatternRequest("TN", List.of("7EME", "8EME"), null));

        assertThat(resultat.totalCreated()).isEqualTo(3);
        assertThat(resultat.totalSkipped()).isZero();
        assertThat(resultat.levels()).hasSize(2);
        assertThat(resultat.levels().get(1).message()).isEqualTo("Aucun pattern national trouvé pour 8EME");
    }

    // ── consultation ──────────────────────────────────────────────────────────

    @Nested
    class Consultation {

        @Test
        void actifs_avecOuSansAnnee() {
            when(nationalPatternRepository.findByCountryCodeAndAcademicYearAndActiveTrue("TN", 2026))
                    .thenReturn(List.of(programme7eme()));
            when(nationalPatternRepository.findByCountryCodeAndActiveTrue("TN")).thenReturn(List.of());

            assertThat(service.findAllActive("TN", 2026)).hasSize(1);
            assertThat(service.findAllActive("TN", null)).isEmpty();
        }

        @Test
        void parId_reponseComplete_seancesTriees() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.of(programme7eme()));

            NationalPatternResponse reponse = service.findById(NP_ID);

            assertThat(reponse.getCode()).isEqualTo("TN_7EME");
            assertThat(reponse.getLevelCode()).isEqualTo("7EME");
            assertThat(reponse.getDetails()).hasSize(3);
            assertThat(reponse.getDetails().get(0).getSessions())
                    .extracting(NationalPatternResponse.SessionResponse::getSessionOrder).containsExactly(1, 2);
            assertThat(reponse.getDetails().get(1).getSessions().get(1).getSessionType()).isEqualTo("TP");
        }

        @Test
        void parId_inconnu_introuvable() {
            when(nationalPatternRepository.findByIdWithDetails(NP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(NP_ID)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void reponse_toleranteAuxListesEtTypesAbsents() {
            NationalPattern sansDetails = programme("9EME");
            sansDetails.setDetails(null);
            NationalPatternDetail sansSeances = detail("MUS", 1.0);
            sansSeances.setSessions(null);
            NationalPattern avecTrous = programme("9EME", sansSeances,
                    detail("DESSIN", 1.0, seance(1, null, 1.0, null, null, null)));
            when(nationalPatternRepository.findActiveWithDetailsByCountryAndLevel("TN", "9EME"))
                    .thenReturn(List.of(avecTrous, sansDetails));
            when(nationalPatternRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(sansDetails));

            NationalPatternResponse reponse = service.findByLevelCode("TN", "9EME");

            assertThat(reponse.getDetails().get(0).getSessions()).isEmpty();
            assertThat(reponse.getDetails().get(1).getSessions().get(0).getSessionType()).isNull();
            assertThat(service.findById(1L).getDetails()).isEmpty();
        }

        @Test
        void parNiveau_aucunProgramme_introuvable() {
            when(nationalPatternRepository.findActiveWithDetailsByCountryAndLevel("TN", "7EME")).thenReturn(List.of());

            assertThatThrownBy(() -> service.findByLevelCode("TN", "7EME"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("level=7EME");
        }
    }

    // ── correspondances ───────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(nullValues = "NUL", value = {
            "NUL, ALL", "biweekly, BIWEEKLY", "ODD, ODD", "even, EVEN", "WEEKLY, ALL"})
    void parite(String brut, WeekParity attendue) {
        assertThat(NationalPatternServiceImpl.mapWeekParity(brut)).isEqualTo(attendue);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NUL", value = {
            "NUL, NORMALE", "LABPHYSIQUE, LABPHYSIQUE", "lab_physics, LABPHYSIQUE",
            "LABSCIENCE, LABSCIENCE", "LAB_SCIENCE, LABSCIENCE",
            "LABINFORMATIQUE, LABINFORMATIQUE", "computer, LABINFORMATIQUE",
            "LABTECHNIQUE, LABTECHNIQUE", "LAB_TECH, LABTECHNIQUE",
            "SALLESPORT, SALLESPORT", "salle_sport, SALLESPORT", "AMPHI, NORMALE"})
    void typeDeSalle(String brut, RoomType attendu) {
        assertThat(NationalPatternServiceImpl.mapRoomType(brut, SessionType.COURSE)).isEqualTo(attendu);
    }
}
