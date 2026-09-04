package tn.wtm.school.planning.constraints.dsl.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Opérateurs de comparaison du DSL.
 *
 * <p>Chaque opérateur déclare les types de champ qu'il accepte : c'est ce qui
 * permet au validateur de rejeter « {@code day GREATER_THAN 3} » ou
 * « {@code startTime CONTAINS "15"} » avant que la règle n'atteigne le solveur.
 * L'ensemble est volontairement fermé — un opérateur absent de cette énumération
 * n'existe pas, donc ne peut pas être fabriqué par un utilisateur ni par le LLM.</p>
 */
public enum DslOperator {

    EQUALS("est égal à", all(), 1),
    NOT_EQUALS("est différent de", all(), 1),

    GREATER_THAN("est strictement supérieur à", ordered(), 1),
    GREATER_THAN_OR_EQUAL("est supérieur ou égal à", ordered(), 1),
    LESS_THAN("est strictement inférieur à", ordered(), 1),
    LESS_THAN_OR_EQUAL("est inférieur ou égal à", ordered(), 1),

    /** Compare à une liste de valeurs : {@code "values": ["MONDAY", "FRIDAY"]}. */
    IN("fait partie de", all(), -1),
    NOT_IN("ne fait pas partie de", all(), -1),

    /** Intervalle fermé : {@code "values": ["08:00", "12:00"]}. */
    BETWEEN("est compris entre", ordered(), 2),

    CONTAINS("contient", EnumSet.of(DslFieldType.STRING), 1),
    STARTS_WITH("commence par", EnumSet.of(DslFieldType.STRING), 1);

    private final String                 label;
    private final Set<DslFieldType>      supportedTypes;
    private final int                    arity;

    DslOperator(String label, Set<DslFieldType> supportedTypes, int arity) {
        this.label          = label;
        this.supportedTypes = supportedTypes;
        this.arity          = arity;
    }

    private static Set<DslFieldType> all() {
        return EnumSet.allOf(DslFieldType.class);
    }

    /** Types sur lesquels un ordre total existe — seuls eux acceptent &lt; et &gt;. */
    private static Set<DslFieldType> ordered() {
        return EnumSet.of(DslFieldType.NUMBER, DslFieldType.TIME, DslFieldType.DAY);
    }

    public boolean supports(DslFieldType type) {
        return supportedTypes.contains(type);
    }

    /** Nombre de valeurs attendues : 1 pour value, 2 pour BETWEEN, -1 pour une liste libre. */
    public int arity() {
        return arity;
    }

    /** True quand l'opérateur lit {@code values} (liste) plutôt que {@code value}. */
    public boolean isMultiValue() {
        return arity != 1;
    }

    public String getLabel() {
        return label;
    }

    public Set<DslFieldType> getSupportedTypes() {
        return supportedTypes;
    }
}
