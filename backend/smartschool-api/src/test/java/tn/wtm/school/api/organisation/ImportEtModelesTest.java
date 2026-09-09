package tn.wtm.school.api.organisation;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.org.dto.response.EleveImportResult;
import tn.wtm.school.org.dto.response.RoomImportResult;
import tn.wtm.school.org.dto.response.TeacherImportResult;
import tn.wtm.school.org.service.EleveService;
import tn.wtm.school.org.service.RoomService;
import tn.wtm.school.org.service.TeacherService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le contrat d'import et de modèle, vérifié sur les trois écrans qui le portent.
 *
 * <p>Enseignants, salles et élèves proposent le même parcours — télécharger un
 * modèle, le remplir, le renvoyer — et chacun des trois contrôleurs en réécrit
 * le code : détection du format, refus du fichier vide, génération du classeur.
 * Cette triplication est un fait du dépôt, pas un choix défendu ici ; ces tests
 * la prennent pour ce qu'elle est en vérifiant les trois, de sorte qu'une
 * divergence entre eux se voie.</p>
 *
 * <p>Le format n'est pas déduit du seul nom de fichier : un navigateur qui
 * n'envoie pas de type MIME et un client qui n'envoie pas d'extension doivent
 * l'un comme l'autre aboutir. C'est ce que vérifie chaque bloc ci-dessous.</p>
 */
@ExtendWith(MockitoExtension.class)
class ImportEtModelesTest {

    private static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Nested
    class Enseignants {

        @Mock private TeacherService teacherService;

        private TeacherController controller() {
            return new TeacherController(teacherService);
        }

        @Test
        void aiguilleUnFichierCsvVersLimportCsv() {
            when(teacherService.importFromCsv(any())).thenReturn(resultatEnseignants());

            controller().importFile(fichier("enseignants.csv", "text/csv"));

            verify(teacherService).importFromCsv(any(InputStream.class));
            verify(teacherService, never()).importFromExcel(any());
        }

        @Test
        void aiguilleUnClasseurExcelVersLimportExcel() {
            when(teacherService.importFromExcel(any())).thenReturn(resultatEnseignants());

            controller().importFile(fichier("enseignants.xlsx", MIME_XLSX));

            verify(teacherService).importFromExcel(any(InputStream.class));
        }

        /** Sans extension, le type MIME suffit — et réciproquement. */
        @Test
        void reconnaitLeFormatParLeTypeMimeQuandLextensionManque() {
            when(teacherService.importFromCsv(any())).thenReturn(resultatEnseignants());

            controller().importFile(fichier("export", "text/csv"));

            verify(teacherService).importFromCsv(any(InputStream.class));
        }

        @Test
        void refuseUnFichierVide() {
            assertThatThrownBy(() -> controller().importFile(
                    new MockMultipartFile("file", "enseignants.csv", "text/csv", new byte[0])))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("vide");
        }

        @Test
        void refuseUnFormatQuiNestNiCsvNiExcel() {
            assertThatThrownBy(() -> controller().importFile(fichier("enseignants.pdf", "application/pdf")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Format non supporté");
        }

        /** Un flux illisible devient un 400 explicite, et non une trace de pile. */
        @Test
        void traduitUnFluxIllisibleEnRefusExplicite() {
            assertThatThrownBy(() -> controller().importFile(fichierIllisible("enseignants.csv", "text/csv")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Impossible de lire");
        }

        @Test
        void livreUnModeleCsvNommeEtEncodeEnUtf8() {
            verifierModeleCsv(controller().downloadCsvTemplate(), "template_enseignants.csv", "codeEnseignant");
        }

        @Test
        void livreUnModeleExcelDontLenTeteAnnonceLesColonnes() {
            verifierModeleExcel(controller().downloadExcelTemplate(), "template_enseignants.xlsx", "codeEnseignant", 2);
        }
    }

    @Nested
    class Salles {

        @Mock private RoomService roomService;

        private RoomController controller() {
            return new RoomController(roomService);
        }

        @Test
        void aiguilleChaqueFormatVersLimportCorrespondant() {
            when(roomService.importFromCsv(any())).thenReturn(resultatSalles());
            when(roomService.importFromExcel(any())).thenReturn(resultatSalles());

            controller().importFile(fichier("salles.csv", null));
            controller().importFile(fichier("salles.xls", "application/vnd.ms-excel"));

            verify(roomService).importFromCsv(any(InputStream.class));
            verify(roomService).importFromExcel(any(InputStream.class));
        }

        @Test
        void refuseUnFichierVideOuDunFormatInconnu() {
            assertThatThrownBy(() -> controller().importFile(
                    new MockMultipartFile("file", "salles.csv", "text/csv", new byte[0])))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> controller().importFile(fichier("salles.json", "application/json")))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void livreSesDeuxModeles() {
            verifierModeleCsv(controller().downloadCsvTemplate(), "template_salles.csv", "codeSalle");
            verifierModeleExcel(controller().downloadExcelTemplate(), "template_salles.xlsx", "codeSalle", 4);
        }
    }

    @Nested
    class Eleves {

        @Mock private EleveService eleveService;

        private EleveController controller() {
            return new EleveController(eleveService);
        }

        @Test
        void aiguilleChaqueFormatVersLimportCorrespondant() {
            when(eleveService.importFromCsv(any())).thenReturn(resultatEleves());
            when(eleveService.importFromExcel(any())).thenReturn(resultatEleves());

            controller().importFile(fichier("eleves.csv", "application/csv"));
            controller().importFile(fichier("eleves.xlsx", null));

            verify(eleveService).importFromCsv(any(InputStream.class));
            verify(eleveService).importFromExcel(any(InputStream.class));
        }

        @Test
        void refuseUnFichierVideOuDunFormatInconnu() {
            assertThatThrownBy(() -> controller().importFile(
                    new MockMultipartFile("file", "eleves.csv", "text/csv", new byte[0])))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> controller().importFile(fichier("eleves.txt", "text/plain")))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void livreSesDeuxModeles() {
            verifierModeleCsv(controller().downloadCsvTemplate(), "template_eleves.csv", "codeEleve");
            verifierModeleExcel(controller().downloadExcelTemplate(), "template_eleves.xlsx", "codeEleve", 3);
        }
    }

    // ── Assertions partagées ─────────────────────────────────────────────────────

    /**
     * Le nom du fichier voyage dans {@code Content-Disposition} : c'est lui que
     * le navigateur écrit sur le disque, et le seul indice que l'utilisateur
     * aura pour retrouver le modèle qu'il vient de télécharger.
     */
    private static void verifierModeleCsv(ResponseEntity<byte[]> reponse, String fichier, String colonne) {
        assertThat(reponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains(fichier);
        assertThat(reponse.getHeaders().getContentType()).hasToString("text/csv;charset=UTF-8");

        String contenu = new String(reponse.getBody(), StandardCharsets.UTF_8);
        assertThat(contenu).contains(colonne);
        assertThat(reponse.getHeaders().getContentLength()).isEqualTo(reponse.getBody().length);
    }

    /** Le classeur est relu par POI : un en-tête faux ne se verrait pas sur des octets. */
    private static void verifierModeleExcel(ResponseEntity<byte[]> reponse, String fichier,
                                            String colonne, int lignesDexemple) {
        assertThat(reponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains(fichier);

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(reponse.getBody()))) {
            Sheet feuille = classeur.getSheetAt(0);
            assertThat(cellules(feuille.getRow(0))).contains(colonne);
            // L'en-tête plus les lignes d'exemple : le modèle ne dit pas seulement
            // quelles colonnes remplir, il montre à quoi ressemble une ligne juste.
            assertThat(feuille.getLastRowNum()).isEqualTo(lignesDexemple);
        } catch (IOException e) {
            throw new AssertionError("Classeur illisible", e);
        }
    }

    private static List<String> cellules(Row ligne) {
        return java.util.stream.StreamSupport.stream(ligne.spliterator(), false)
                .map(cellule -> cellule.getStringCellValue())
                .toList();
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private static MultipartFile fichier(String nom, String typeMime) {
        return new MockMultipartFile("file", nom, typeMime, "une ligne\n".getBytes(StandardCharsets.UTF_8));
    }

    /** Un fichier non vide dont l'ouverture du flux échoue. */
    private static MultipartFile fichierIllisible(String nom, String typeMime) {
        MultipartFile fichier = mock(MultipartFile.class);
        try {
            when(fichier.isEmpty()).thenReturn(false);
            when(fichier.getOriginalFilename()).thenReturn(nom);
            when(fichier.getContentType()).thenReturn(typeMime);
            when(fichier.getInputStream()).thenThrow(new IOException("flux interrompu"));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return fichier;
    }

    private static TeacherImportResult resultatEnseignants() {
        return TeacherImportResult.builder().imported(1).rowErrors(List.of()).build();
    }

    private static RoomImportResult resultatSalles() {
        return RoomImportResult.builder().imported(1).rowErrors(List.of()).build();
    }

    private static EleveImportResult resultatEleves() {
        return EleveImportResult.builder().imported(1).rowErrors(List.of()).build();
    }
}
