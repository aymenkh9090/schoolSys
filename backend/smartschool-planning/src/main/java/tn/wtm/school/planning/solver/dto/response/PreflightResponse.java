package tn.wtm.school.planning.solver.dto.response;

import lombok.*;

import java.util.List;

/**
 * Verdict du contrôle qui précède la génération.
 *
 * <p>Il répond à une question simple, et en une seconde : <em>les données de
 * cette année scolaire permettent-elles d'engendrer un emploi du temps ?</em>
 * Rien n'est placé pour y répondre — chaque constat est une preuve tirée d'un
 * dénombrement, pas le résultat d'une recherche.
 *
 * <p>Les constats reprennent la forme de ceux de la validation métier
 * ({@link ScoreExplanationResponse.BusinessFinding}) : l'interface n'a ainsi
 * qu'une seule façon d'afficher un constat, qu'il vienne d'avant ou d'après la
 * génération.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PreflightResponse {

    /** Vrai quand aucun constat bloquant n'a été formulé : la génération peut partir. */
    private boolean ready;

    /** Nombre de constats bloquants — ceux qui empêchent la génération. */
    private int blockingCount;

    /** Nombre d'avertissements — ce qui mérite d'être su sans rien empêcher. */
    private int warningCount;

    /** Les constats, bloquants d'abord. */
    private List<ScoreExplanationResponse.BusinessFinding> findings;
}
