package tn.wtm.school.planning.constraints.dsl.enums;

/**
 * Effet de la règle sur le score quand elle se déclenche.
 *
 * <p>{@link #PENALIZE} exprime un interdit (« ne doit pas »), {@link #REWARD} une
 * préférence (« de préférence »). Une récompense n'a de sens qu'au niveau MEDIUM
 * ou SOFT : récompenser au niveau HARD rendrait faisable une solution qui ne l'est
 * pas — le validateur le refuse.</p>
 */
public enum DslAction {
    PENALIZE,
    REWARD
}
