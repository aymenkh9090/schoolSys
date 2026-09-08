package tn.wtm.school.api.pointage;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.solver.service.PersonnelDisponibleService;
import tn.wtm.school.pointage.dto.requete.PointageMasseRequete;
import tn.wtm.school.pointage.dto.requete.PointageRequete;
import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.reponse.RapportJournalierReponse;
import tn.wtm.school.pointage.dto.reponse.ResultatPointageMasseReponse;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.service.ServicePointage;

import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/pointage")
@RequiredArgsConstructor
@Tag(name = "Pointage", description = "Gestion du pointage du personnel")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class PointageController {

    private final ServicePointage servicePointage;
    private final PersonnelDisponibleService personnelDisponibleService;

    @PostMapping
    public ResponseEntity<PresencePersonnelReponse> pointer(@Valid @RequestBody PointageRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicePointage.pointer(requete));
    }

    @PostMapping("/masse")
    public ResponseEntity<ResultatPointageMasseReponse> pointerEnMasse(@Valid @RequestBody PointageMasseRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicePointage.pointerEnMasse(requete));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PresencePersonnelReponse> modifier(@PathVariable Long id,
                                                              @Valid @RequestBody PointageRequete requete) {
        return ResponseEntity.ok(servicePointage.modifierPointage(id, requete));
    }

    @GetMapping("/rapport")
    public ResponseEntity<RapportJournalierReponse> rapportJournalier(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(servicePointage.obtenirRapportJournalier(date));
    }

    /**
     * Enseignants disponibles pour le pointage d'une date, filtrés sur le jour
     * de la semaine (via l'emploi du temps publié). Le paramètre {@code periode}
     * (optionnel) restreint en plus aux enseignants ayant une séance sur ce
     * créneau précis — matin ou après-midi.
     */
    @GetMapping("/enseignants-disponibles")
    public ResponseEntity<List<TeacherResponse>> enseignantsDisponibles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Nullable Periode periode) {
        return ResponseEntity.ok(personnelDisponibleService.findTeachersWorkingOn(date, versDayPeriod(periode)));
    }

    private static DayPeriod versDayPeriod(@Nullable Periode periode) {
        if (periode == null) return null;
        return periode == Periode.MATIN ? DayPeriod.MORNING : DayPeriod.AFTERNOON;
    }

    @GetMapping("/personnel/{membreId}/historique")
    public ResponseEntity<List<PresencePersonnelReponse>> historique(
            @PathVariable Long membreId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(servicePointage.obtenirHistoriquePersonnel(membreId, debut, fin));
    }
}
