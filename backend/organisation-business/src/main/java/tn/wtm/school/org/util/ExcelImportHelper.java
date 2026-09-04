package tn.wtm.school.org.util;

import org.apache.poi.ss.usermodel.*;
import tn.wtm.school.common.exceptions.BadRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Reads an Excel workbook (.xls or .xlsx) and converts the first sheet into
 * a structured result: a list of column headers (from the first non-empty row)
 * and a list of data rows, each represented as a header-to-value map.
 */
public final class ExcelImportHelper {

    private ExcelImportHelper() {}

    public record ExcelData(List<String> headers, List<Map<String, String>> rows) {}

    public static ExcelData parse(InputStream stream) {
        try (Workbook workbook = WorkbookFactory.create(stream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BadRequestException("Le fichier Excel ne contient aucune feuille");
            }

            int firstRowIdx = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(firstRowIdx);
            if (headerRow == null) {
                throw new BadRequestException("En-tête introuvable dans le fichier Excel");
            }

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cellToString(cell));
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = firstRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? null : cellToString(cell).trim();
                    rowMap.put(headers.get(j), value == null || value.isBlank() ? null : value);
                }
                rows.add(rowMap);
            }

            return new ExcelData(Collections.unmodifiableList(headers), rows);

        } catch (IOException e) {
            throw new BadRequestException("Impossible de lire le fichier Excel : " + e.getMessage());
        }
    }

    private static String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double val = cell.getNumericCellValue();
                yield (val == Math.floor(val) && !Double.isInfinite(val))
                        ? String.valueOf((long) val)
                        : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                CellType cached = cell.getCachedFormulaResultType();
                yield cached == CellType.NUMERIC
                        ? String.valueOf((long) cell.getNumericCellValue())
                        : cell.getStringCellValue();
            }
            default -> "";
        };
    }

    private static boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cell.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }
}
