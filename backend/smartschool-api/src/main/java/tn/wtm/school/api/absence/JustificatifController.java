package tn.wtm.school.api.absence;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.absence.dto.requete.SoumissionJustificatifRequete;
import tn.wtm.school.absence.dto.requete.TraitementJustificatifRequete;
import tn.wtm.school.absence.dto.reponse.JustificatifReponse;
import tn.wtm.school.absence.enums.StatutJustificatif;
import tn.wtm.school.absence.service.ServiceJustificatif;
import tn.wtm.school.org.service.SchoolUserService;

import java.time.LocalDateTime;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/justificatifs")
@RequiredArgsConstructor
@Tag(name = "Justificatifs", description = "Gestion des justificatifs d'absence")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class JustificatifController {

    private final ServiceJustificatif serviceJustificatif;
    private final SchoolUserService schoolUserService;

    /**
     * L'auteur du dépôt est le compte connecté : un identifiant transmis par le
     * client serait invérifiable, il est donc systématiquement écrasé.
     */
    @PostMapping
    public ResponseEntity<JustificatifReponse> soumettre(@Valid @RequestBody SoumissionJustificatifRequete requete) {
        requete.setSoumisParId(schoolUserService.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceJustificatif.soumettre(requete));
    }

    /**
     * {@code eleveId} absent = tous les élèves : combiné à {@code statut=EN_ATTENTE},
     * c'est la file des justificatifs que la vie scolaire doit traiter.
     */
    @GetMapping
    public ResponseEntity<Page<JustificatifReponse>> lister(
            @RequestParam(required = false) @Nullable Long eleveId,
            @RequestParam(required = false) StatutJustificatif statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            Pageable pageable) {
        return ResponseEntity.ok(serviceJustificatif.lister(eleveId, statut, debut, fin, pageable));
    }

    @PatchMapping("/{id}/approuver")
    public ResponseEntity<JustificatifReponse> approuver(@PathVariable Long id,
                                                          @Valid @RequestBody TraitementJustificatifRequete requete) {
        requete.setTraiteParId(schoolUserService.getCurrentUserId());
        return ResponseEntity.ok(serviceJustificatif.approuver(id, requete));
    }

    @PatchMapping("/{id}/refuser")
    public ResponseEntity<JustificatifReponse> refuser(@PathVariable Long id,
                                                        @Valid @RequestBody TraitementJustificatifRequete requete) {
        requete.setTraiteParId(schoolUserService.getCurrentUserId());
        return ResponseEntity.ok(serviceJustificatif.refuser(id, requete));
    }
}
