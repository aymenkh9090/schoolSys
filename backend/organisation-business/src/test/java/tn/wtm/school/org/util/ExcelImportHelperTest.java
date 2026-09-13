package tn.wtm.school.org.util;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tn.wtm.school.common.exceptions.BadRequestException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le classeur saisi par un secrétariat ne contient pas que du texte : des
 * nombres, des dates, des formules, des lignes laissées vides. Chaque cellule
 * doit revenir sous la forme qu'un humain y lit.
 */
class ExcelImportHelperTest {

    static InputStream ecrire(Workbook wb, Consumer<Sheet> remplir) throws IOException {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            remplir.accept(wb.createSheet("feuille"));
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    void chaqueTypeDeCellule_luCommeUnHumainLeLit() throws IOException {
        InputStream fichier = ecrire(new XSSFWorkbook(), feuille -> {
            Row entete = feuille.createRow(0);
            String[] colonnes = {"texte", "entier", "decimal", "date", "booleen", "formule", "formuleTexte", "vide"};
            for (int j = 0; j < colonnes.length; j++) entete.createCell(j).setCellValue(colonnes[j]);

            CellStyle styleDate = feuille.getWorkbook().createCellStyle();
            styleDate.setDataFormat(feuille.getWorkbook().getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Row ligne = feuille.createRow(1);
            ligne.createCell(0).setCellValue("  Ben Ali  ");
            ligne.createCell(1).setCellValue(30);
            ligne.createCell(2).setCellValue(1.5);
            ligne.createCell(3).setCellValue(LocalDate.of(2026, 9, 15));
            ligne.getCell(3).setCellStyle(styleDate);
            ligne.createCell(4).setCellValue(true);
            ligne.createCell(5).setCellFormula("2+3");
            ligne.createCell(6).setCellFormula("\"7B\"&\"1\"");
            ligne.createCell(7).setCellValue("   ");
            feuille.getWorkbook().getCreationHelper().createFormulaEvaluator().evaluateAll();
        });

        ExcelImportHelper.ExcelData donnees = ExcelImportHelper.parse(fichier);

        assertThat(donnees.headers()).containsExactly(
                "texte", "entier", "decimal", "date", "booleen", "formule", "formuleTexte", "vide");
        assertThat(donnees.rows()).singleElement().satisfies(ligne -> {
            assertThat(ligne.get("texte")).isEqualTo("Ben Ali");
            assertThat(ligne.get("entier")).isEqualTo("30");
            assertThat(ligne.get("decimal")).isEqualTo("1.5");
            assertThat(ligne.get("date")).isEqualTo("2026-09-15");
            assertThat(ligne.get("booleen")).isEqualTo("true");
            assertThat(ligne.get("formule")).isEqualTo("5");
            assertThat(ligne.get("formuleTexte")).isEqualTo("7B1");
            assertThat(ligne.get("vide")).isNull();
        });
    }

    @Test
    void lignesVidesOuAbsentes_ignorees_cellulesManquantes_nulles() throws IOException {
        InputStream fichier = ecrire(new HSSFWorkbook(), feuille -> {
            Row entete = feuille.createRow(0);
            entete.createCell(0).setCellValue("code");
            entete.createCell(1).setCellValue("nom");
            feuille.createRow(1).createCell(0).setCellValue("E1");
            feuille.createRow(2).createCell(1).setCellValue(" ");
            // ligne 3 jamais créée
            feuille.createRow(4).createCell(1).setCellValue("Sami");
        });

        ExcelImportHelper.ExcelData donnees = ExcelImportHelper.parse(fichier);

        assertThat(donnees.rows()).hasSize(2);
        assertThat(donnees.rows().get(0)).containsEntry("code", "E1").containsEntry("nom", null);
        assertThat(donnees.rows().get(1)).containsEntry("code", null).containsEntry("nom", "Sami");
    }

    @Test
    void feuilleSansEntete_refusee() throws IOException {
        InputStream fichier = ecrire(new XSSFWorkbook(), feuille -> { });

        assertThatThrownBy(() -> ExcelImportHelper.parse(fichier))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("En-tête");
    }

    @Test
    void fichierQuiNEstPasUnClasseur_refuse() {
        InputStream texte = new ByteArrayInputStream("ceci n'est pas un classeur".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> ExcelImportHelper.parse(texte))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Impossible de lire le fichier Excel");
    }
}
