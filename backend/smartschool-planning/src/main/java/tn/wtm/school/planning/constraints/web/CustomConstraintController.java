package tn.wtm.school.planning.constraints.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.wtm.school.planning.constraints.dto.request.CustomConstraintRequest;
import tn.wtm.school.planning.constraints.dto.request.DslAnalysisRequest;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSuggestionResponse;
import tn.wtm.school.planning.constraints.dto.response.CustomConstraintResponse;
import tn.wtm.school.planning.constraints.dto.response.DslAnalysisResponse;
import tn.wtm.school.planning.constraints.dto.response.DslSchemaResponse;
import tn.wtm.school.planning.constraints.service.ConstraintSuggestionService;
import tn.wtm.school.planning.constraints.service.CustomConstraintService;
import tn.wtm.school.planning.constraints.service.DslSchemaService;

import java.util.List;

/**
 * API des contraintes personnalisées : catalogue DSL, analyse d'une règle
 * candidate, gestion des règles, et suggestions issues du dernier planning.
 *
 * <p>Isolation multi-tenant : aucune méthode ne prend de {@code tenantId} en
 * paramètre. Le tenant provient du {@code TenantContext}, alimenté par le
 * {@code TenantFilter} à partir du JWT Keycloak validé. Une école ne peut donc
 * pas lire ni modifier les règles d'une autre, même en devinant un identifiant :
 * chaque requête de repository filtre sur le tenant courant.</p>
 *
 * <p>L'assistant IA appelle ces mêmes routes, avec le jeton de l'utilisateur qui
 * lui parle. Il n'a ni compte de service, ni accès direct à la base : ce qui lui
 * est interdit ici lui est interdit partout.</p>
 */
@RestController
@RequestMapping("/api/planning/constraints")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
public class CustomConstraintController {

    private final CustomConstraintService     customConstraintService;
    private final DslSchemaService            schemaService;
    private final ConstraintSuggestionService suggestionService;

    // ── catalogue DSL ─────────────────────────────────────────────────────────

    /**
     * Champs, opérateurs et portées utilisables dans une règle.
     * Alimente les listes déroulantes de l'interface et le prompt de l'assistant.
     */
    @GetMapping("/dsl/schema")
    public DslSchemaResponse getDslSchema() {
        return schemaService.describe();
    }

    // ── analyse avant enregistrement ──────────────────────────────────────────

    /**
     * Valide une règle candidate, mesure son impact et cherche les blocages,
     * sans rien écrire. C'est l'appel que fait l'écran de confirmation.
     */
    @PostMapping("/custom/analyze")
    public DslAnalysisResponse analyze(@Valid @RequestBody DslAnalysisRequest request) {
        return customConstraintService.analyze(request);
    }

    // ── gestion des règles ────────────────────────────────────────────────────

    @GetMapping("/custom")
    public List<CustomConstraintResponse> list(@RequestParam(required = false) Long profileId) {
        return customConstraintService.findAll(profileId);
    }

    @GetMapping("/custom/{id}")
    public CustomConstraintResponse get(@PathVariable Long id) {
        return customConstraintService.findById(id);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomConstraintResponse create(@Valid @RequestBody CustomConstraintRequest request) {
        return customConstraintService.create(request);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PutMapping("/custom/{id}")
    public CustomConstraintResponse update(@PathVariable Long id,
                                           @RequestBody CustomConstraintRequest request) {
        return customConstraintService.update(id, request);
    }

    /** Désactiver plutôt que supprimer : la règle reste consultable et réactivable. */
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @PatchMapping("/custom/{id}/enabled")
    public CustomConstraintResponse setEnabled(@PathVariable Long id,
                                               @RequestParam boolean enabled) {
        return customConstraintService.setEnabled(id, enabled);
    }

    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @DeleteMapping("/custom/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customConstraintService.delete(id);
    }

    // ── suggestions ───────────────────────────────────────────────────────────

    /**
     * Propositions de règles déduites du dernier emploi du temps généré.
     * Purement statistique : rien n'est enregistré, l'utilisateur décide.
     */
    @GetMapping("/custom/suggestions")
    public ConstraintSuggestionResponse suggestions(@RequestParam(required = false) Long schoolYearId,
                                                    @RequestParam(required = false) Long jobId) {
        return suggestionService.suggest(schoolYearId, jobId);
    }
}
