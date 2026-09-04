package tn.wtm.school.planning.constraints.dsl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import tn.wtm.school.planning.constraints.dsl.enums.DslAggregateMetric;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;

/**
 * Seuil appliqué au cumul d'un groupe, pour les portées agrégées.
 *
 * <p>Exemple — « un enseignant ne doit pas dépasser 3 heures consécutives » se
 * traduit par scope {@code TEACHER_DAY} et
 * {@code {"metric": "TOTAL_HOURS", "operator": "GREATER_THAN", "value": 3}}.</p>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class DslAggregate {

    @Builder.Default
    private DslAggregateMetric metric = DslAggregateMetric.TOTAL_HOURS;

    /** Seuls les opérateurs de comparaison ordonnée ont un sens ici. */
    @Builder.Default
    private DslOperator operator = DslOperator.GREATER_THAN;

    /** Seuil, exprimé dans l'unité de la métrique (heures, créneaux, séances). */
    private Double value;
}
