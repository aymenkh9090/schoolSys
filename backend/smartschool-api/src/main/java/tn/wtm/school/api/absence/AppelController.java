package tn.wtm.school.api.absence;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.absence.dto.requete.ModificationStatutRequete;
import tn.wtm.school.absence.dto.requete.OuvertureAppelRequete;
import tn.wtm.school.absence.dto.reponse.AbsenceEleveReponse;
import tn.wtm.school.absence.dto.reponse.AppelReponse;
import tn.wtm.school.absence.dto.reponse.HistoriqueAppelReponse;
import tn.wtm.school.absence.dto.reponse.LigneAppelReponse;
import tn.wtm.school.absence.dto.reponse.SignalementEleveReponse;
import tn.wtm.school.absence.service.ServiceAppel;
import tn.wtm.school.org.service.SchoolUserService;

import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/appel")
@RequiredArgsConstructor
@Tag(name = "Appel", description = "Gestion des séances d'appel")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')")
public class AppelController {

    private final ServiceAppel serviceAppel;
    private final SchoolUserService schoolUserService;

    @PostMapping("/seances/ouvrir")
    public ResponseEntity<AppelReponse> ouvrir(@Valid @RequestBody OuvertureAppelRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceAppel.ouvrirOuRecupererAppel(requete));
    }

    @GetMapping("/seances/{seanceAppelId}")
    public ResponseEntity<AppelReponse> getById(@PathVariable Long seanceAppelId) {
        return ResponseEntity.ok(serviceAppel.recupererAppel(seanceAppelId));
    }

    /**
     * Liste les séances d'appel d'une journée (par défaut aujourd'hui), avec
     * filtres optionnels par classe/enseignant/verrouillage — pour parcourir
     * les séances existantes sans avoir à connaître leur ID.
     */
    @GetMapping("/seances")
    public ResponseEntity<List<AppelReponse>> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
            @RequestParam(required = false) @Nullable Long groupeClasseId,
            @RequestParam(required = false) @Nullable Long enseignantId,
            @RequestParam(required = false) @Nullable Boolean estVerrouille) {
        return ResponseEntity.ok(serviceAppel.listerSeances(date, groupeClasseId, enseignantId, estVerrouille));
    }

    /**
     * Dossier d'absences d'un élève : les lignes d'appel où il n'était pas
     * présent, avec leur séance et l'état du justificatif éventuel. C'est ce que
     * la vie scolaire parcourt pour justifier une absence — sans jamais avoir à
     * saisir un identifiant de ligne d'appel.
     */
    @GetMapping("/eleves/{eleveId}/absences")
    public ResponseEntity<List<AbsenceEleveReponse>> absencesEleve(
            @PathVariable Long eleveId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate fin,
            @RequestParam(required = false, defaultValue = "true") boolean inclureRetards) {
        return ResponseEntity.ok(serviceAppel.listerAbsencesEleve(eleveId, debut, fin, inclureRetards));
    }

    /**
     * Absences et exclusions non justifiées d'une CLASSE, sur une période.
     * <p>
     * L'enseignant qui ouvre sa feuille d'appel voit ainsi ce que ses collègues
     * ont signalé plus tôt dans la journée ou la semaine : un élève absent en
     * première heure, un autre exclu la veille. La liste se vide quand la vie
     * scolaire valide un justificatif, jamais depuis la classe.
     * <p>
     * Bornes par défaut : les sept derniers jours jusqu'à aujourd'hui.
     */
    @GetMapping("/classes/{groupeClasseId}/signalements")
    public ResponseEntity<List<SignalementEleveReponse>> signalementsClasse(
            @PathVariable Long groupeClasseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate depuis,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate jusqua) {
        return ResponseEntity.ok(serviceAppel.listerSignalementsClasse(groupeClasseId, depuis, jusqua));
    }

    @PatchMapping("/lignes/{ligneAppelId}/statut")
    public ResponseEntity<LigneAppelReponse> modifierStatut(@PathVariable Long ligneAppelId,
                                                              @Valid @RequestBody ModificationStatutRequete requete) {
        return ResponseEntity.ok(serviceAppel.modifierStatutEleve(ligneAppelId, requete));
    }

    /**
     * Clôture manuelle : l'auteur est le compte connecté, jamais un identifiant
     * saisi par l'utilisateur.
     */
    @PostMapping("/seances/{seanceAppelId}/verrouiller")
    public ResponseEntity<Void> verrouiller(@PathVariable Long seanceAppelId) {
        serviceAppel.verrouillerSeance(seanceAppelId, schoolUserService.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/seances/{seanceAppelId}/historique")
    public ResponseEntity<List<HistoriqueAppelReponse>> historique(@PathVariable Long seanceAppelId) {
        return ResponseEntity.ok(serviceAppel.recupererHistorique(seanceAppelId));
    }
}
