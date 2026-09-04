package tn.wtm.school.api.absence;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.absence.dto.requete.EnregistrementCahierRequete;
import tn.wtm.school.absence.dto.reponse.CahierCorpusReponse;
import tn.wtm.school.absence.dto.reponse.CahierSeanceReponse;
import tn.wtm.school.absence.service.ServiceCahier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/cahier")
@RequiredArgsConstructor
@Tag(name = "Cahier", description = "Gestion du cahier de séance")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class CahierController {

    private final ServiceCahier serviceCahier;

    @PostMapping("/seances/{seanceAppelId}")
    public ResponseEntity<CahierSeanceReponse> enregistrer(@PathVariable Long seanceAppelId,
                                                             @Valid @RequestBody EnregistrementCahierRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceCahier.enregistrer(seanceAppelId, requete));
    }

    @GetMapping("/seances/{seanceAppelId}")
    public ResponseEntity<CahierSeanceReponse> recuperer(@PathVariable Long seanceAppelId) {
        return ResponseEntity.ok(serviceCahier.recuperer(seanceAppelId));
    }

    @PostMapping("/seances/{seanceAppelId}/verrouiller")
    public ResponseEntity<Void> verrouiller(@PathVariable Long seanceAppelId) {
        serviceCahier.verrouiller(seanceAppelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<CahierSeanceReponse>> lister(
            @RequestParam Long enseignantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            Pageable pageable) {
        return ResponseEntity.ok(serviceCahier.listerParEnseignant(enseignantId, debut, fin, pageable));
    }

    /**
     * Corpus des séances renseignées, pour l'indexation sémantique de l'assistant.
     *
     * <p>Le périmètre découle du rôle et n'est pas paramétrable : un enseignant
     * obtient ses propres séances, un administrateur celles de son établissement.
     * Contrairement à {@code GET /api/v1/cahier}, il n'existe donc pas de
     * paramètre {@code enseignantId} par lequel demander le cahier d'un collègue.</p>
     *
     * @param depuis borne basse sur la date de séance (défaut : tout l'historique)
     * @param limite nombre maximum de séances, plafonné par le service
     */
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @GetMapping("/corpus")
    public ResponseEntity<List<CahierCorpusReponse>> corpus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(defaultValue = "500") int limite) {
        return ResponseEntity.ok(serviceCahier.construireCorpus(depuis, limite));
    }
}
