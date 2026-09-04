package tn.wtm.school.planning.constraints.dsl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;

import java.util.List;

/**
 * Une condition élémentaire du DSL : « ce champ, cet opérateur, cette valeur ».
 *
 * <p>{@code value} porte la valeur unique (EQUALS, GREATER_THAN…), {@code values}
 * la liste (IN, NOT_IN, BETWEEN). Les deux sont typés {@code String} : la
 * conversion vers heure / jour / nombre est faite par le compilateur, à partir du
 * type déclaré du champ dans le catalogue — jamais devinée depuis le JSON.</p>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class DslCondition {

    /** Nom du champ, obligatoirement présent dans le catalogue (ex. {@code subject.code}). */
    private String field;

    private DslOperator operator;

    /** Valeur unique — utilisée par les opérateurs d'arité 1. */
    private String value;

    /** Valeurs multiples — utilisées par IN, NOT_IN et BETWEEN. */
    private List<String> values;

    /** Les valeurs effectives de la condition, quel que soit le champ renseigné. */
    public List<String> effectiveValues() {
        if (values != null && !values.isEmpty()) {
            return values;
        }
        return value == null ? List.of() : List.of(value);
    }
}
