package tn.wtm.school.api.pointage;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.pointage.dto.reponse.StatistiquesPresenceReponse;
import tn.wtm.school.pointage.enums.TypePersonnel;
import tn.wtm.school.pointage.service.ServiceStatistiquesPointage;

import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/statistiques/pointage")
@RequiredArgsConstructor
@Tag(name = "Statistiques Pointage", description = "Statistiques de présence du personnel")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class StatistiquesPointageController {

    private final ServiceStatistiquesPointage serviceStatistiques;

    @GetMapping("/personnel/{membreId}")
    public ResponseEntity<StatistiquesPresenceReponse> statistiquesPersonnel(
            @PathVariable Long membreId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(serviceStatistiques.obtenirStatistiquesPersonnel(membreId, debut, fin));
    }

    @GetMapping("/type/{typePersonnel}")
    public ResponseEntity<List<StatistiquesPresenceReponse>> statistiquesParType(
            @PathVariable TypePersonnel typePersonnel,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(serviceStatistiques.obtenirStatistiquesParTypePersonnel(typePersonnel, debut, fin));
    }
}
