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
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.TeacherRequest;
import tn.wtm.school.org.dto.response.TeacherImportResult;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.mapper.TeacherMapper;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.service.impl.TeacherServiceImpl;
import tn.wtm.school.security.jwt.JwtClaimsExtractor;

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

/**
 * Complète TeacherServiceTest : la fiche de l'enseignant connecté, les
 * recherches, et l'import des enseignants depuis un fichier.
 */
@ExtendWith(MockitoExtension.class)
class TeacherCompteEtImportTest {

    @Mock TeacherRepository             teacherRepository;
    @Mock TeachingAssignmentRepository  teachingAssignmentRepository;
    @Mock SubjectLevelRepository        subjectLevelRepository;
    @Mock SchoolUserRepository          schoolUserRepository;
    @Mock TeacherMapper                 teacherMapper;
    @Mock ObjectsValidator<TeacherRequest> teacherValidator;
    @Mock JwtClaimsExtractor            jwtClaimsExtractor;

    TeacherServiceImpl service;

    static final String   TENANT = "tenant-1";
    static final Long     T_ID   = 1L;
    static final Pageable PAGE   = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        service = new TeacherServiceImpl(teacherRepository, teachingAssignmentRepository, subjectLevelRepository,
                schoolUserRepository, teacherMapper, teacherValidator, jwtClaimsExtractor);
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    static Teacher fiche() {
        return Teacher.builder().idEnseignant(T_ID).codeEnseignant("ENS1").nom("Ben Salah").prenom("Leila")
                .email("leila@ecole.tn").build();
    }

    static SchoolUser compte(String tenant, Teacher fiche) {
        SchoolUser u = SchoolUser.builder().id(20L).keycloakUserId("kc-1").email("leila@ecole.tn").teacher(fiche).build();
        u.setTenantId(tenant);
        return u;
    }

    static TeacherResponse reponse() {
        return TeacherResponse.builder().idEnseignant(T_ID).codeEnseignant("ENS1").build();
    }

    // ── l'enseignant connecté ─────────────────────────────────────────────────

    @Nested
    class EnseignantConnecte {

        void ficheAvecAffectations() {
            Teacher f = fiche();
            when(teacherRepository.findByIdWithAssignments(T_ID)).thenReturn(Optional.of(f));
            when(teacherMapper.toResponse(f)).thenReturn(reponse());
        }

        @Test
        void compteLieAUneFiche_laFicheEstRenvoyeeAvecSesHeures() {
            when(jwtClaimsExtractor.getUserId()).thenReturn("kc-1");
            when(schoolUserRepository.findByKeycloakUserId("kc-1")).thenReturn(Optional.of(compte(TENANT, fiche())));
            ficheAvecAffectations();

            TeacherResponse r = service.getCurrentTeacher();

            assertThat(r.getIdEnseignant()).isEqualTo(T_ID);
            assertThat(r.getTotalHeures()).isZero();
            verify(teacherRepository, never()).findByTenantIdAndEmail(any(), any());
        }

        @Test
        void compteAncienSansFiche_liéAutomatiquementParEmail() {
            SchoolUser ancien = compte(TENANT, null);
            when(jwtClaimsExtractor.getUserId()).thenReturn("kc-1");
            when(schoolUserRepository.findByKeycloakUserId("kc-1")).thenReturn(Optional.of(ancien));
            when(teacherRepository.findByTenantIdAndEmail(TENANT, "leila@ecole.tn")).thenReturn(Optional.of(fiche()));

            assertThat(service.getCurrentTeacherId()).contains(T_ID);

            assertThat(ancien.getTeacher().getIdEnseignant()).isEqualTo(T_ID);
            verify(schoolUserRepository).save(ancien);
        }

        @Test
        void sansCompte_replisurLEmailDuJeton_sansRienEnregistrer() {
            when(jwtClaimsExtractor.getUserId()).thenReturn(null);
            when(jwtClaimsExtractor.getEmail()).thenReturn("leila@ecole.tn");
            when(teacherRepository.findByTenantIdAndEmail(TENANT, "leila@ecole.tn")).thenReturn(Optional.of(fiche()));

            assertThat(service.getCurrentTeacherId()).contains(T_ID);
            verify(schoolUserRepository, never()).save(any());
        }

        @Test
        void compteDUnAutreEtablissement_ignore() {
            when(jwtClaimsExtractor.getUserId()).thenReturn("kc-1");
            when(schoolUserRepository.findByKeycloakUserId("kc-1")).thenReturn(Optional.of(compte("autre", fiche())));
            when(jwtClaimsExtractor.getEmail()).thenReturn(null);

            assertThat(service.getCurrentTeacherId()).isEmpty();
        }

        @Test
        void aucuneFiche_messagePourLUtilisateur() {
            when(jwtClaimsExtractor.getUserId()).thenReturn("kc-1");
            when(schoolUserRepository.findByKeycloakUserId("kc-1")).thenReturn(Optional.of(compte(TENANT, null)));
            when(teacherRepository.findByTenantIdAndEmail(TENANT, "leila@ecole.tn")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCurrentTeacher())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Gestion des enseignants");
            verify(schoolUserRepository, never()).save(any());
        }

        @Test
        void ficheAvecAffectations_idNul_badRequest() {
            assertThatThrownBy(() -> service.getTeacherWithAssignments(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void ficheAvecAffectations_inconnue_introuvable() {
            when(teacherRepository.findByIdWithAssignments(T_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeacherWithAssignments(T_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── recherches ────────────────────────────────────────────────────────────

    @Nested
    class Recherches {

        @Test
        void parCodeEtParIdentite_nettoyesPuisCherches() {
            Teacher f = fiche();
            when(teacherRepository.findByTenantIdAndCodeEnseignant(TENANT, "ENS1")).thenReturn(Optional.of(f));
            when(teacherRepository.findByTenantIdAndNumIdentite(TENANT, "0123")).thenReturn(Optional.of(f));
            when(teacherMapper.toResponseLight(f)).thenReturn(reponse());

            assertThat(service.getTeacherByCode(" ENS1 ").getCodeEnseignant()).isEqualTo("ENS1");
            assertThat(service.getTeacherByNumIdentite(" 0123 ").getCodeEnseignant()).isEqualTo("ENS1");
        }

        @Test
        void parCodeEtParIdentite_inconnus_introuvables() {
            when(teacherRepository.findByTenantIdAndCodeEnseignant(TENANT, "X")).thenReturn(Optional.empty());
            when(teacherRepository.findByTenantIdAndNumIdentite(TENANT, "Y")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTeacherByCode("X")).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.getTeacherByNumIdentite("Y")).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void tous() {
            List<Teacher> fiches = List.of(fiche());
            when(teacherRepository.findByTenantId(TENANT)).thenReturn(fiches);
            when(teacherMapper.toResponseLightList(fiches)).thenReturn(List.of(reponse()));

            assertThat(service.getAllTeachers()).hasSize(1);
        }

        @Test
        void parStatut_nulDonneTout_sinonFiltre() {
            Teacher f = fiche();
            when(teacherRepository.findByTenantId(TENANT, PAGE)).thenReturn(new PageImpl<>(List.of(f)));
            when(teacherRepository.findByTenantIdAndEstEnPoste(TENANT, false, PAGE)).thenReturn(Page.empty());
            when(teacherMapper.toResponseLight(f)).thenReturn(reponse());

            assertThat(service.getTeachersByStatus(null, PAGE).getTotalElements()).isEqualTo(1);
            assertThat(service.getTeachersByStatus(false, PAGE)).isEmpty();
        }

        @Test
        void recherche_termeVideDonneTout_sinonTermeNettoye() {
            when(teacherRepository.findByTenantId(TENANT, PAGE)).thenReturn(Page.empty());
            when(teacherRepository.searchTeachers("ben", PAGE)).thenReturn(Page.empty());

            service.searchTeachers(" ", PAGE);
            service.searchTeachers(null, PAGE);
            service.searchTeachers(" ben ", PAGE);

            verify(teacherRepository, times(2)).findByTenantId(TENANT, PAGE);
            verify(teacherRepository).searchTeachers("ben", PAGE);
        }
    }

    // ── import ────────────────────────────────────────────────────────────────

    @Nested
    class Import {

        InputStream csv(String contenu) {
            return new ByteArrayInputStream(contenu.getBytes(StandardCharsets.UTF_8));
        }

        InputStream classeur(String[]... lignes) throws IOException {
            try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet feuille = wb.createSheet("enseignants");
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
        void csv_colonneManquante_refuseLeFichier() {
            assertThatThrownBy(() -> service.importFromCsv(csv("codeEnseignant,nom,prenom\nE1,Ben,Ali\n")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("numIdentite");
        }

        @Test
        void csv_fluxIllisible_badRequest() {
            InputStream casse = new InputStream() {
                @Override public int read() throws IOException { throw new IOException("réseau"); }
            };

            assertThatThrownBy(() -> service.importFromCsv(casse))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("réseau");
        }

        @Test
        void csv_doublonsIgnores_erreursRapportees_restantImporte() {
            when(teacherRepository.existsByTenantIdAndCodeEnseignant(TENANT, "E1")).thenReturn(false);
            when(teacherRepository.existsByTenantIdAndNumIdentite(TENANT, "111")).thenReturn(false);
            when(teacherRepository.existsByTenantIdAndEmail(TENANT, "a@ecole.tn")).thenReturn(false);
            when(teacherRepository.existsByTenantIdAndCodeEnseignant(TENANT, "E2")).thenReturn(true);
            when(teacherRepository.existsByTenantIdAndCodeEnseignant(TENANT, "E3")).thenReturn(false);
            when(teacherRepository.existsByTenantIdAndNumIdentite(TENANT, "333")).thenReturn(true);
            when(teacherRepository.existsByTenantIdAndCodeEnseignant(TENANT, "E4")).thenReturn(false);
            when(teacherRepository.existsByTenantIdAndNumIdentite(TENANT, "444")).thenReturn(false);
            when(teacherRepository.existsByTenantIdAndEmail(TENANT, "pris@ecole.tn")).thenReturn(true);

            String contenu = """
                    codeEnseignant,numIdentite,nom,prenom,email,maxHeuresSemaine,maxHeuresJour,minHeuresJour,specialite
                    E1,111,Ben Salah,Leila,a@ecole.tn,18,6,2,Maths
                    E2,222,Double,Code,,,,,
                    E3,333,Double,Identite,,,,,
                    E4,444,Double,Email,pris@ecole.tn,,,,
                    E5,555,Heures,Fausses,,dix-huit,,,
                    """;

            TeacherImportResult resultat = service.importFromCsv(csv(contenu));

            assertThat(resultat.getImported()).isEqualTo(1);
            assertThat(resultat.getSkipped()).isEqualTo(3);
            assertThat(resultat.getErrors()).isEqualTo(1);
            assertThat(resultat.getRowErrors()).extracting(TeacherImportResult.RowError::getReason).containsExactly(
                    "code déjà existant : E2",
                    "numIdentite déjà existant : 333",
                    "email déjà existant : pris@ecole.tn",
                    "Valeur numérique invalide pour maxHeuresSemaine : dix-huit");

            ArgumentCaptor<TeacherRequest> lue = ArgumentCaptor.forClass(TeacherRequest.class);
            verify(teacherMapper).toEntity(lue.capture());
            TeacherRequest e1 = lue.getValue();
            assertThat(e1.getMaxHeuresSemaine()).isEqualTo(18);
            assertThat(e1.getMaxHeuresJour()).isEqualTo(6);
            assertThat(e1.getMinHeuresJour()).isEqualTo(2);
            assertThat(e1.getSpecialite()).isEqualTo("Maths");
            assertThat(e1.getEstEnPoste()).isTrue();
        }

        @Test
        void excel_colonneManquante_refuseLeFichier() throws IOException {
            InputStream fichier = classeur(new String[]{"codeEnseignant", "numIdentite", "nom"});

            assertThatThrownBy(() -> service.importFromExcel(fichier))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("prenom");
        }

        @Test
        void excel_ligneValideImportee_ligneFautiveNumeroteeDepuisDeux() throws IOException {
            when(teacherRepository.existsByTenantIdAndCodeEnseignant(TENANT, "E1")).thenReturn(false);
            when(teacherRepository.existsByTenantIdAndNumIdentite(TENANT, "111")).thenReturn(false);

            TeacherImportResult resultat = service.importFromExcel(classeur(
                    new String[]{"codeEnseignant", "numIdentite", "nom", "prenom", "maxHeuresJour"},
                    new String[]{"E1", "111", "Ben Salah", "Leila", "6"},
                    new String[]{"E2", "222", "Trabelsi", "Ines", "six"}));

            assertThat(resultat.getImported()).isEqualTo(1);
            assertThat(resultat.getRowErrors()).singleElement().satisfies(e -> {
                assertThat(e.getLine()).isEqualTo(3);
                assertThat(e.getCodeEnseignant()).isEqualTo("E2");
            });
        }
    }
}
