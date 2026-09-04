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
import tn.wtm.school.org.dto.request.TeacherRequest;
import tn.wtm.school.org.dto.response.TeacherImportResult;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.service.TeacherService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/organisation/teachers")
@RequiredArgsConstructor
@Tag(name = "Enseignants", description = "Gestion des enseignants")
// Lecture ouverte au surveillant (pointage du personnel, planning enseignant) ;
// les écritures restent épinglées méthode par méthode.
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class TeacherController {

    private final TeacherService teacherService;

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping
    public ResponseEntity<TeacherResponse> create(@Valid @RequestBody TeacherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.createTeacher(request));
    }

    /** Fiche (avec affectations) de l'enseignant connecté — espace enseignant Web/mobile. */
    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/me")
    public ResponseEntity<TeacherResponse> me() {
        return ResponseEntity.ok(teacherService.getCurrentTeacher());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getTeacherById(id));
    }

    @GetMapping
    public ResponseEntity<Page<TeacherResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(teacherService.getAllTeachersPaginated(pageable));
    }

    @GetMapping("/list")
    public ResponseEntity<List<TeacherResponse>> list() {
        return ResponseEntity.ok(teacherService.getAllTeachers());
    }

    @GetMapping("/actifs")
    public ResponseEntity<List<TeacherResponse>> getActifs() {
        return ResponseEntity.ok(teacherService.getActiveTeachers());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<TeacherResponse>> search(@RequestParam String q, Pageable pageable) {
        return ResponseEntity.ok(teacherService.searchTeachers(q, pageable));
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<TeacherResponse> getWithAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getTeacherWithAssignments(id));
    }

    @GetMapping("/workload")
    public ResponseEntity<List<TeacherResponse>> getWorkload() {
        return ResponseEntity.ok(teacherService.getTeacherWorkload());
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TeacherResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody TeacherRequest request) {
        return ResponseEntity.ok(teacherService.updateTeacher(id, request));
    }

    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<TeacherResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.deactivateTeacher(id));
    }

    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<TeacherResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.reactivateTeacher(id));
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return ResponseEntity.noContent().build();
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        String csv = """
                # Modèle d'import enseignants — Colonnes obligatoires : codeEnseignant, numIdentite, nom, prenom
                # Types valides pour maxHeuresSemaine : nombre entier entre 1 et 40
                codeEnseignant,numIdentite,nom,prenom,email,telephone,maxHeuresSemaine,maxHeuresJour,minHeuresJour,specialite
                T001,12345678,Ben Ali,Mohamed,m.ali@ecole.tn,+216 20 000 001,20,6,3,MATHEMATIQUES
                T002,87654321,Trabelsi,Fatma,f.trabelsi@ecole.tn,,18,,,LANGUE_FRANCAISE
                """;
        return csvResponse(csv, "template_enseignants.csv");
    }

    @GetMapping("/import/template/excel")
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        String[] headers = {
                "codeEnseignant", "numIdentite", "nom", "prenom",
                "email", "telephone", "maxHeuresSemaine", "maxHeuresJour", "minHeuresJour", "specialite"
        };
        Object[][] samples = {
                {"T001", "12345678", "Ben Ali",  "Mohamed", "m.ali@ecole.tn",       "+216 20 000 001", 20, 6, 3, "MATHEMATIQUES"},
                {"T002", "87654321", "Trabelsi", "Fatma",   "f.trabelsi@ecole.tn",  "",                18, "", "", "LANGUE_FRANCAISE"},
        };
        return excelResponse(headers, samples, "template_enseignants.xlsx");
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<TeacherImportResult> importFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Le fichier est vide");
        }
        try {
            if (isExcelFile(file)) {
                return ResponseEntity.ok(teacherService.importFromExcel(file.getInputStream()));
            }
            if (isCsvFile(file)) {
                return ResponseEntity.ok(teacherService.importFromCsv(file.getInputStream()));
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
