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
import tn.wtm.school.org.dto.request.EleveRequest;
import tn.wtm.school.org.dto.response.EleveImportResult;
import tn.wtm.school.org.dto.response.EleveResponse;
import tn.wtm.school.org.service.EleveService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/eleves")
@RequiredArgsConstructor
@Tag(name = "Élèves", description = "Gestion des élèves")
// Le surveillant lit les élèves (appel, suivi et justification des absences) ;
// les écritures restent épinglées méthode par méthode.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class EleveController {

    private final EleveService eleveService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<EleveResponse> creer(@Valid @RequestBody EleveRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eleveService.creer(requete));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EleveResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(eleveService.recupererParId(id));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<EleveResponse> modifier(@PathVariable Long id,
                                                   @Valid @RequestBody EleveRequest requete) {
        return ResponseEntity.ok(eleveService.modifier(id, requete));
    }

    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @PatchMapping("/{id}/statut")
    public ResponseEntity<EleveResponse> toggleStatut(@PathVariable Long id,
                                                       @RequestParam Boolean estActif) {
        return ResponseEntity.ok(eleveService.toggleStatut(id, estActif));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        eleveService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // ── Listes ────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<EleveResponse>> listerTous(Pageable pageable) {
        return ResponseEntity.ok(eleveService.listerTous(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<EleveResponse>> rechercher(@RequestParam String q, Pageable pageable) {
        return ResponseEntity.ok(eleveService.rechercher(q, pageable));
    }

    @GetMapping("/classe/{classeId}")
    public ResponseEntity<Page<EleveResponse>> listerParClasse(@PathVariable Long classeId, Pageable pageable) {
        return ResponseEntity.ok(eleveService.listerParClasse(classeId, pageable));
    }

    @GetMapping("/classe/{classeId}/actifs")
    public ResponseEntity<List<EleveResponse>> listerActifsParClasse(@PathVariable Long classeId) {
        return ResponseEntity.ok(eleveService.listerActifsParClasse(classeId));
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        String csv = """
                # Modèle d'import élèves — Colonnes obligatoires : codeEleve, nom, prenom, codeClasse
                # codeClasse : code alphanumérique de la classe (ex: 7A, 8B) — doit exister dans l'établissement
                codeEleve,nom,prenom,codeClasse,numIdentite,email,telephone
                EL001,Ben Salah,Ahmed,7A,20152345,a.bensalah@ecole.tn,+216 90 000 001
                EL002,Trabelsi,Sarra,7B,,s.trabelsi@ecole.tn,
                EL003,Karray,Omar,8A,20164567,,
                """;
        return csvResponse(csv, "template_eleves.csv");
    }

    @GetMapping("/import/template/excel")
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        String[] headers = {"codeEleve", "nom", "prenom", "codeClasse", "numIdentite", "email", "telephone"};
        Object[][] samples = {
                {"EL001", "Ben Salah", "Ahmed", "7A", "20152345", "a.bensalah@ecole.tn", "+216 90 000 001"},
                {"EL002", "Trabelsi",  "Sarra", "7B", "",         "s.trabelsi@ecole.tn", ""},
                {"EL003", "Karray",    "Omar",  "8A", "20164567", "",                    ""},
        };
        return excelResponse(headers, samples, "template_eleves.xlsx");
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<EleveImportResult> importFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Le fichier est vide");
        }
        try {
            if (isExcelFile(file)) {
                return ResponseEntity.ok(eleveService.importFromExcel(file.getInputStream()));
            }
            if (isCsvFile(file)) {
                return ResponseEntity.ok(eleveService.importFromCsv(file.getInputStream()));
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
