package tn.wtm.school.api.pointage;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.pointage.dto.requete.SoumissionJustificatifPointageRequete;
import tn.wtm.school.pointage.dto.requete.TraitementJustificatifPointageRequete;
import tn.wtm.school.pointage.dto.reponse.JustificatifPointageReponse;
import tn.wtm.school.pointage.service.ServiceJustificatifPointage;
import tn.wtm.school.org.service.SchoolUserService;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/pointage/justificatifs")
@RequiredArgsConstructor
@Tag(name = "Pointage - Justificatifs", description = "Gestion des justificatifs de pointage")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class JustificatifPointageController {

    private final ServiceJustificatifPointage serviceJustificatif;
    private final SchoolUserService schoolUserService;

    @PostMapping
    public ResponseEntity<JustificatifPointageReponse> soumettre(
            @Valid @RequestBody SoumissionJustificatifPointageRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceJustificatif.soumettreJustificatif(requete));
    }

    @GetMapping("/en-attente")
    public ResponseEntity<List<JustificatifPointageReponse>> listerEnAttente() {
        return ResponseEntity.ok(serviceJustificatif.listerJustificatifsEnAttente());
    }

    @GetMapping("/presence/{presenceId}")
    public ResponseEntity<JustificatifPointageReponse> getByPresence(@PathVariable Long presenceId) {
        return ResponseEntity.ok(serviceJustificatif.obtenirJustificatifParPresence(presenceId));
    }

    /** L'auteur de la décision est le compte connecté, jamais un nom saisi par le client. */
    @PatchMapping("/{id}/traiter")
    public ResponseEntity<JustificatifPointageReponse> traiter(@PathVariable Long id,
                                                                @Valid @RequestBody TraitementJustificatifPointageRequete requete) {
        requete.setTraitePar(nomUtilisateurCourant());
        return ResponseEntity.ok(serviceJustificatif.traiterJustificatif(id, requete));
    }

    private String nomUtilisateurCourant() {
        Long userId = schoolUserService.getCurrentUserId();
        return userId == null ? null : schoolUserService.getUserById(userId).getNomComplet();
    }
}
