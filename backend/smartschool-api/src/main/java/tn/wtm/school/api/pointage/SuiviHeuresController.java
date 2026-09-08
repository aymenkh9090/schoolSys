package tn.wtm.school.api.pointage;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.pointage.dto.requete.MiseAJourHeuresRequete;
import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.service.ServiceSuiviHeures;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/pointage/suivi-heures")
@RequiredArgsConstructor
@Tag(name = "Pointage - Suivi Heures", description = "Suivi des heures des enseignants")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class SuiviHeuresController {

    private final ServiceSuiviHeures serviceSuiviHeures;

    @PostMapping
    public ResponseEntity<SuiviHeuresEnseignantReponse> mettreAJour(@Valid @RequestBody MiseAJourHeuresRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceSuiviHeures.mettreAJourHeures(requete));
    }

    @GetMapping("/enseignant/{enseignantId}/semaine/{semaine}")
    public ResponseEntity<SuiviHeuresEnseignantReponse> getSemaine(
            @PathVariable Long enseignantId,
            @PathVariable int semaine,
            @RequestParam String anneeAcademique) {
        return ResponseEntity.ok(serviceSuiviHeures.obtenirResumeSemaine(enseignantId, semaine, anneeAcademique));
    }

    @GetMapping("/enseignant/{enseignantId}/annuel")
    public ResponseEntity<List<SuiviHeuresEnseignantReponse>> getAnnuel(
            @PathVariable Long enseignantId,
            @RequestParam String anneeAcademique) {
        return ResponseEntity.ok(serviceSuiviHeures.obtenirResumeAnnuel(enseignantId, anneeAcademique));
    }
}
