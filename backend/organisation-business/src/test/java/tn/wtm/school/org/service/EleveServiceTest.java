package tn.wtm.school.org.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.org.dto.request.EleveRequest;
import tn.wtm.school.org.dto.response.EleveImportResult;
import tn.wtm.school.org.dto.response.EleveResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Eleve;
import tn.wtm.school.org.mapper.EleveMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.EleveRepository;
import tn.wtm.school.org.service.impl.EleveServiceImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EleveServiceTest {

    @Mock EleveRepository      eleveRepository;
    @Mock ClassGroupRepository classGroupRepository;
    @Mock EleveMapper          eleveMapper;

    EleveServiceImpl service;

    static final String   TENANT    = "tenant-1";
    static final Long     ELEVE_ID  = 7L;
    static final Long     CLASSE_ID = 3L;
    static final Pageable PAGE      = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        service = new EleveServiceImpl(eleveRepository, classGroupRepository, eleveMapper);
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── données ───────────────────────────────────────────────────────────────

    EleveRequest requete(String code) {
        return EleveRequest.builder().codeEleve(code).nom("Ben Ali").prenom("Sami").classeId(CLASSE_ID).build();
    }

    Eleve eleve(String code) {
        return Eleve.builder().idEleve(ELEVE_ID).codeEleve(code).nom("Ben Ali").prenom("Sami").build();
    }

    ClassGroup classe() {
        return ClassGroup.builder().idClasse(CLASSE_ID).code("7B1").build();
    }

    EleveResponse reponse(String code) {
        return EleveResponse.builder().codeEleve(code).build();
    }

    void classeTrouvee() {
        when(classGroupRepository.findByTenantIdAndIdClasse(TENANT, CLASSE_ID)).thenReturn(Optional.of(classe()));
    }

    void eleveTrouve(Eleve eleve) {
        when(eleveRepository.findByTenantIdAndIdEleve(TENANT, ELEVE_ID)).thenReturn(Optional.of(eleve));
    }

    // ── création ──────────────────────────────────────────────────────────────

    @Nested
    class Creation {

        @Test
        void sansTenant_refuse() {
            TenantContext.clear();
            assertThatThrownBy(() -> service.creer(requete("E1")))
                    .isInstanceOf(TenantSecurityException.class);
            verify(eleveRepository, never()).save(any());
        }

        @Test
        void codeDejaPris_conflit() {
            when(eleveRepository.existsByTenantIdAndCodeEleve(TENANT, "E1")).thenReturn(true);

            assertThatThrownBy(() -> service.creer(requete("E1")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("E1");
        }

        @Test
        void classeAbsente_badRequest() {
            EleveRequest sansClasse = requete("E1");
            sansClasse.setClasseId(null);

            assertThatThrownBy(() -> service.creer(sansClasse))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void classeInconnue_introuvable() {
            when(classGroupRepository.findByTenantIdAndIdClasse(TENANT, CLASSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(requete("E1")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(CLASSE_ID.toString());
        }

        @Test
        void cheminNominal_rattacheLaClasseEtEnregistre() {
            classeTrouvee();
            Eleve entite = eleve("E1");
            when(eleveMapper.toEntity(any())).thenReturn(entite);
            when(eleveRepository.save(entite)).thenReturn(entite);
            when(eleveMapper.toResponse(entite)).thenReturn(reponse("E1"));

            EleveResponse resultat = service.creer(requete("E1"));

            assertThat(resultat.getCodeEleve()).isEqualTo("E1");
            assertThat(entite.getClasseGroup().getIdClasse()).isEqualTo(CLASSE_ID);
        }
    }

    // ── lecture ───────────────────────────────────────────────────────────────

    @Nested
    class Lecture {

        @Test
        void parId_idNul_badRequest() {
            assertThatThrownBy(() -> service.recupererParId(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void parId_inconnu_introuvable() {
            when(eleveRepository.findByTenantIdAndIdEleve(TENANT, ELEVE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recupererParId(ELEVE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void parId_trouve() {
            Eleve entite = eleve("E1");
            eleveTrouve(entite);
            when(eleveMapper.toResponse(entite)).thenReturn(reponse("E1"));

            assertThat(service.recupererParId(ELEVE_ID).getCodeEleve()).isEqualTo("E1");
        }

        @Test
        void listerTous_filtreParTenant() {
            Eleve entite = eleve("E1");
            when(eleveRepository.findByTenantId(TENANT, PAGE)).thenReturn(new PageImpl<>(List.of(entite)));
            when(eleveMapper.toResponse(entite)).thenReturn(reponse("E1"));

            Page<EleveResponse> page = service.listerTous(PAGE);

            assertThat(page.getContent()).extracting(EleveResponse::getCodeEleve).containsExactly("E1");
        }

        @Test
        void rechercher_termeVide_listeTout() {
            when(eleveRepository.findByTenantId(TENANT, PAGE)).thenReturn(Page.empty());

            service.rechercher("   ", PAGE);
            service.rechercher(null, PAGE);

            verify(eleveRepository, times(2)).findByTenantId(TENANT, PAGE);
            verify(eleveRepository, never()).searchEleves(any(), any(), any());
        }

        @Test
        void rechercher_termeNettoye() {
            when(eleveRepository.searchEleves(TENANT, "sami", PAGE)).thenReturn(Page.empty());

            service.rechercher("  sami ", PAGE);

            verify(eleveRepository).searchEleves(TENANT, "sami", PAGE);
        }

        @Test
        void listerParClasse_classeNulle_badRequest() {
            assertThatThrownBy(() -> service.listerParClasse(null, PAGE))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void listerParClasse() {
            Eleve entite = eleve("E1");
            when(eleveRepository.findByTenantIdAndClasseGroup_IdClasse(TENANT, CLASSE_ID, PAGE))
                    .thenReturn(new PageImpl<>(List.of(entite)));
            when(eleveMapper.toResponse(entite)).thenReturn(reponse("E1"));

            assertThat(service.listerParClasse(CLASSE_ID, PAGE).getTotalElements()).isEqualTo(1);
        }

        @Test
        void listerActifsParClasse_classeNulle_badRequest() {
            assertThatThrownBy(() -> service.listerActifsParClasse(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void listerActifsParClasse() {
            List<Eleve> actifs = List.of(eleve("E1"));
            when(eleveRepository.findByTenantIdAndClasseGroup_IdClasseAndEstActifTrue(TENANT, CLASSE_ID))
                    .thenReturn(actifs);
            when(eleveMapper.toResponseList(actifs)).thenReturn(List.of(reponse("E1")));

            assertThat(service.listerActifsParClasse(CLASSE_ID)).hasSize(1);
        }

        @Test
        void listerIds_classeNulle_badRequest() {
            assertThatThrownBy(() -> service.listerIdsParClasse(TENANT, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void listerIds_tenantExplicite_prioritaire() {
            when(eleveRepository.findIdsByTenantIdAndClasseId("autre", CLASSE_ID)).thenReturn(List.of(1L, 2L));

            assertThat(service.listerIdsParClasse("autre", CLASSE_ID)).containsExactly(1L, 2L);
        }

        @Test
        void listerIds_tenantVide_retombeSurLeContexte() {
            when(eleveRepository.findIdsByTenantIdAndClasseId(TENANT, CLASSE_ID)).thenReturn(List.of(5L));

            assertThat(service.listerIdsParClasse(" ", CLASSE_ID)).containsExactly(5L);
            assertThat(service.listerIdsParClasse(null, CLASSE_ID)).containsExactly(5L);
        }
    }

    // ── modification, statut, suppression ────────────────────────────────────

    @Nested
    class Modification {

        @Test
        void codePrisParUnAutre_conflit() {
            eleveTrouve(eleve("E1"));
            when(eleveRepository.existsByTenantIdAndCodeEleveAndIdEleveNot(TENANT, "E2", ELEVE_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.modifier(ELEVE_ID, requete("E2")))
                    .isInstanceOf(ConflictException.class);
            verify(eleveRepository, never()).save(any());
        }

        @Test
        void cheminNominal_appliqueLaRequeteEtLaClasse() {
            Eleve entite = eleve("E1");
            eleveTrouve(entite);
            classeTrouvee();
            when(eleveRepository.save(entite)).thenReturn(entite);
            when(eleveMapper.toResponse(entite)).thenReturn(reponse("E2"));

            EleveRequest requete = requete("E2");
            assertThat(service.modifier(ELEVE_ID, requete).getCodeEleve()).isEqualTo("E2");

            verify(eleveMapper).updateFromRequest(requete, entite);
            assertThat(entite.getClasseGroup().getIdClasse()).isEqualTo(CLASSE_ID);
        }

        @Test
        void statutNul_badRequest() {
            assertThatThrownBy(() -> service.toggleStatut(ELEVE_ID, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void statut_desactiveLEleve() {
            Eleve entite = eleve("E1");
            eleveTrouve(entite);
            when(eleveRepository.save(entite)).thenReturn(entite);
            when(eleveMapper.toResponse(entite)).thenReturn(reponse("E1"));

            service.toggleStatut(ELEVE_ID, false);

            assertThat(entite.getEstActif()).isFalse();
        }

        @Test
        void suppression() {
            Eleve entite = eleve("E1");
            eleveTrouve(entite);

            service.supprimer(ELEVE_ID);

            verify(eleveRepository).delete(entite);
        }
    }

    // ── import CSV ────────────────────────────────────────────────────────────

    @Nested
    class ImportCsv {

        InputStream csv(String contenu) {
            return new ByteArrayInputStream(contenu.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void colonneManquante_refuseLeFichier() {
            InputStream fichier = csv("codeEleve,nom,prenom\nE1,Ben Ali,Sami\n");

            assertThatThrownBy(() -> service.importFromCsv(fichier))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("codeClasse");
        }

        @Test
        void fluxIllisible_badRequest() {
            InputStream casse = new InputStream() {
                @Override public int read() throws IOException { throw new IOException("disque"); }
            };

            assertThatThrownBy(() -> service.importFromCsv(casse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("disque");
        }

        @Test
        void chaqueLigneFautiveEstRapporteeSansBloquerLesAutres() {
            ClassGroup classe = classe();
            when(classGroupRepository.findByTenantIdAndCode(TENANT, "7B1")).thenReturn(Optional.of(classe));
            when(classGroupRepository.findByTenantIdAndCode(TENANT, "9Z9")).thenReturn(Optional.empty());
            when(eleveRepository.existsByTenantIdAndCodeEleve(TENANT, "E1")).thenReturn(false);
            when(eleveRepository.existsByTenantIdAndCodeEleve(TENANT, "DUP")).thenReturn(true);
            when(eleveRepository.existsByTenantIdAndCodeEleve(TENANT, "E3")).thenReturn(false);
            when(eleveRepository.existsByTenantIdAndCodeEleve(TENANT, "E4")).thenReturn(false);

            String contenu = """
                    codeEleve,nom,prenom,codeClasse,email
                    # une ligne de commentaire est ignorée
                    E1,Ben Ali,Sami,7B1,sami@ecole.tn
                    E2,Trabelsi,Ines,,
                    E5,Gharbi,Amel,9Z9,
                    ,Sans,Code,7B1,
                    DUP,Double,Code,7B1,
                    E3,,Nour,7B1,
                    E4,Jaziri,,7B1,
                    """;

            EleveImportResult resultat = service.importFromCsv(csv(contenu));

            assertThat(resultat.getImported()).isEqualTo(1);
            assertThat(resultat.getSkipped()).isEqualTo(1);
            assertThat(resultat.getErrors()).isEqualTo(5);
            assertThat(resultat.getRowErrors()).extracting(EleveImportResult.RowError::getReason)
                    .containsExactly(
                            "codeClasse est obligatoire",
                            "classe introuvable : 9Z9",
                            "codeEleve est obligatoire",
                            "code déjà existant : DUP",
                            "nom est obligatoire",
                            "prenom est obligatoire");

            ArgumentCaptor<Eleve> enregistre = ArgumentCaptor.forClass(Eleve.class);
            verify(eleveRepository).save(enregistre.capture());
            assertThat(enregistre.getValue().getCodeEleve()).isEqualTo("E1");
            assertThat(enregistre.getValue().getEmail()).isEqualTo("sami@ecole.tn");
            assertThat(enregistre.getValue().getTenantId()).isEqualTo(TENANT);
            assertThat(enregistre.getValue().getClasseGroup()).isSameAs(classe);
        }

        @Test
        void erreurInattendue_rapporteeSurLaLigne() {
            when(classGroupRepository.findByTenantIdAndCode(TENANT, "7B1")).thenThrow(new IllegalStateException("base indisponible"));

            EleveImportResult resultat = service.importFromCsv(csv("codeEleve,nom,prenom,codeClasse\nE1,Ben Ali,Sami,7B1\n"));

            assertThat(resultat.getImported()).isZero();
            assertThat(resultat.getRowErrors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.getLine()).isEqualTo(2);
                        assertThat(e.getReason()).isEqualTo("base indisponible");
                    });
        }
    }

    // ── import Excel ──────────────────────────────────────────────────────────

    @Nested
    class ImportExcel {

        InputStream classeur(String[]... lignes) throws IOException {
            try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet feuille = wb.createSheet("eleves");
                for (int i = 0; i < lignes.length; i++) {
                    Row ligne = feuille.createRow(i);
                    for (int j = 0; j < lignes[i].length; j++) {
                        if (lignes[i][j] != null) ligne.createCell(j).setCellValue(lignes[i][j]);
                    }
                }
                wb.write(out);
                return new ByteArrayInputStream(out.toByteArray());
            }
        }

        @Test
        void colonneManquante_refuseLeFichier() throws IOException {
            InputStream fichier = classeur(new String[]{"codeEleve", "nom"});

            assertThatThrownBy(() -> service.importFromExcel(fichier))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("prenom");
        }

        @Test
        void importeLesLignesValidesEtNumeroteDepuisLaDeuxieme() throws IOException {
            when(classGroupRepository.findByTenantIdAndCode(TENANT, "7B1")).thenReturn(Optional.of(classe()));
            when(eleveRepository.existsByTenantIdAndCodeEleve(TENANT, "E1")).thenReturn(false);

            EleveImportResult resultat = service.importFromExcel(classeur(
                    new String[]{"codeEleve", "nom", "prenom", "codeClasse"},
                    new String[]{"E1", "Ben Ali", "Sami", "7B1"},
                    new String[]{"E2", "Trabelsi", "Ines", null}));

            assertThat(resultat.getImported()).isEqualTo(1);
            assertThat(resultat.getRowErrors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.getLine()).isEqualTo(3);
                        assertThat(e.getCodeEleve()).isEqualTo("E2");
                    });
        }

        @Test
        void erreurInattendue_rapporteeSurLaLigne() throws IOException {
            when(classGroupRepository.findByTenantIdAndCode(TENANT, "7B1")).thenThrow(new IllegalStateException("panne"));

            EleveImportResult resultat = service.importFromExcel(classeur(
                    new String[]{"codeEleve", "nom", "prenom", "codeClasse"},
                    new String[]{"E1", "Ben Ali", "Sami", "7B1"}));

            assertThat(resultat.getRowErrors()).extracting(EleveImportResult.RowError::getReason)
                    .containsExactly("panne");
        }
    }
}
