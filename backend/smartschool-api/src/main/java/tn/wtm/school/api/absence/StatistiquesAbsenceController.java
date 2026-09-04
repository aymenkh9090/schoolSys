package tn.wtm.school.api.absence;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.absence.dto.reponse.ResumeAbsenceEleveReponse;
import tn.wtm.school.absence.dto.reponse.StatistiquesAbsenceReponse;
import tn.wtm.school.absence.service.ServiceStatistiquesAbsence;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/statistiques/absences")
@RequiredArgsConstructor
@Tag(name = "Statistiques Absences", description = "Statistiques et tableaux de bord des absences")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class StatistiquesAbsenceController {

    private final ServiceStatistiquesAbsence serviceStatistiques;

    @GetMapping("/eleves/{eleveId}")
    public ResponseEntity<ResumeAbsenceEleveReponse> resumeEleve(@PathVariable Long eleveId,
                                                                   @RequestParam String anneeAcademique) {
        return ResponseEntity.ok(serviceStatistiques.resumeParEleve(eleveId, anneeAcademique));
    }

    @GetMapping("/classes/{groupeClasseId}")
    public ResponseEntity<StatistiquesAbsenceReponse> resumeClasse(
            @PathVariable Long groupeClasseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(serviceStatistiques.resumeParClasse(groupeClasseId, debut, fin));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<StatistiquesAbsenceReponse> dashboard(@RequestParam(required = false) String periode) {
        return ResponseEntity.ok(serviceStatistiques.dashboardAdmin(null, periode));
    }
}
