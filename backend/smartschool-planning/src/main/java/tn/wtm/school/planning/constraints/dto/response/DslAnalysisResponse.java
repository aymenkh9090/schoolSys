package tn.wtm.school.planning.constraints.dto.response;

import lombok.*;

import java.util.List;

/**
 * Verdict complet sur une règle candidate : est-elle valide, que va-t-elle
 * changer, et rend-elle l'emploi du temps impossible ?
 *
 * <p>Les trois questions sont renvoyées ensemble parce que l'utilisateur les
 * pose ensemble au moment de confirmer. Séparer « la règle est syntaxiquement
 * correcte » de « la règle est irréalisable avec vos données » ferait valider
 * des règles qui bloqueront la génération quelques minutes plus tard, sans que
 * personne ne comprenne pourquoi.</p>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DslAnalysisResponse {

    // ── 1. validation ─────────────────────────────────────────────────────────

    private boolean      valid;
    private List<String> errors;
    private List<String> warnings;

    /** Reformulation française de la règle telle que le moteur l'appliquera. */
    private String summary;

    // ── 2. impact estimé ──────────────────────────────────────────────────────

    /** Nombre de séances de l'année concernées par les conditions de la règle. */
    private Integer matchedLessons;

    /** Nombre total de séances à placer, pour donner l'échelle. */
    private Integer totalLessons;

    /** Quelques séances concernées, décrites en clair. */
    private List<String> examples;

    // ── 3. conflits avec les données existantes ───────────────────────────────

    /**
     * False quand la règle, telle qu'écrite, rend le placement impossible :
     * une séance sans aucun créneau admissible, ou une charge obligatoire
     * supérieure au plafond demandé.
     */
    private boolean feasible;

    private List<Conflict> conflicts;

    /** Phrase de synthèse destinée à l'écran de confirmation et à l'assistant. */
    private String verdict;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Conflict {
        /** Type de blocage : NO_ADMISSIBLE_SLOT ou WORKLOAD_EXCEEDS_LIMIT. */
        private String type;
        /** Entité concernée : une séance, un enseignant, une classe, une salle. */
        private String subject;
        /** Explication en français, chiffrée. */
        private String detail;
    }
}
