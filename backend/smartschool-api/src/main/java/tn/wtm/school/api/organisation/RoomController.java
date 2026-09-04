package tn.wtm.school.api.organisation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.org.dto.request.RoomRequest;
import tn.wtm.school.org.dto.response.RoomImportResult;
import tn.wtm.school.org.dto.response.RoomResponse;
import tn.wtm.school.org.service.RoomService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/rooms")
@RequiredArgsConstructor
@Tag(name = "Salles", description = "Gestion des salles")
// Lecture ouverte au surveillant (consultation du planning par salle) ; toutes les
// écritures de ce controller sont déjà réservées à SCHOOL_ADMIN.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class RoomController {

    private final RoomService roomService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<RoomResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(roomService.getRoomByCode(code));
    }

    @GetMapping
    public ResponseEntity<Page<RoomResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(roomService.getRooms(pageable));
    }

    @GetMapping("/list")
    public ResponseEntity<List<RoomResponse>> list() {
        return ResponseEntity.ok(roomService.getRoomsByTenant());
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<RoomResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        String csv = """
                # Modèle d'import salles — Colonnes obligatoires : codeSalle, typeSalle
                # Types valides pour typeSalle : NORMALE, LABSCIENCE, LABPHYSIQUE, LABINFORMATIQUE, SALLESPORT
                codeSalle,typeSalle,capacite,codeBloc,numEtage
                S101,NORMALE,35,A,1
                LABO-PHY,LABPHYSIQUE,24,B,0
                LABO-SCI,LABSCIENCE,24,B,0
                SPORT-01,SALLESPORT,80,C,0
                """;
        return csvResponse(csv, "template_salles.csv");
    }

    @GetMapping("/import/template/excel")
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        String[] headers = {"codeSalle", "typeSalle", "capacite", "codeBloc", "numEtage"};
        Object[][] samples = {
                {"S101",      "NORMALE",         35, "A", 1},
                {"LABO-PHY",  "LABPHYSIQUE",     24, "B", 0},
                {"LABO-SCI",  "LABSCIENCE",      24, "B", 0},
                {"SPORT-01",  "SALLESPORT",      80, "C", 0},
        };
        return excelResponse(headers, samples, "template_salles.xlsx");
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<RoomImportResult> importFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Le fichier est vide");
        }
        try {
            if (isExcelFile(file)) {
                return ResponseEntity.ok(roomService.importFromExcel(file.getInputStream()));
            }
            if (isCsvFile(file)) {
                return ResponseEntity.ok(roomService.importFromCsv(file.getInputStream()));
            }
            throw new BadRequestException("Format non supporté. Utilisez .csv, .xlsx ou .xls");
        } catch (IOException e) {
            throw new BadRequestException("Impossible de lire le fichier : " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isCsvFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        String ct   = file.getContentType();
        return (name != null && name.toLowerCase().endsWith(".csv"))
                || "text/csv".equals(ct)
                || "application/csv".equals(ct);
    }

    private boolean isExcelFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        String ct   = file.getContentType();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return true;
        }
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(ct)
                || "application/vnd.ms-excel".equals(ct);
    }

    private ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    private ResponseEntity<byte[]> excelResponse(String[] headers, Object[][] rows, String filename) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Import");

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Cell cell = row.createCell(c);
                    Object val = rows[r][c];
                    if (val instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                    }
                }
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            byte[] bytes = out.toByteArray();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(bytes);

        } catch (IOException e) {
            throw new BadRequestException("Erreur lors de la génération du template Excel : " + e.getMessage());
        }
    }
}
