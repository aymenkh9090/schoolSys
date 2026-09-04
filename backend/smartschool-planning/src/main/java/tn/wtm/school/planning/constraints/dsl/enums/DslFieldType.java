package tn.wtm.school.planning.constraints.dsl.enums;

/**
 * Type d'un champ du catalogue DSL. Détermine les opérateurs autorisés et la
 * façon dont la valeur JSON fournie par l'utilisateur (ou par le LLM) est
 * convertie avant comparaison.
 */
public enum DslFieldType {

    /** Chaîne libre — comparaison insensible à la casse et aux accents. */
    STRING,

    /** Nombre entier ou décimal. */
    NUMBER,

    /** Heure « HH:mm » (ou « HH:mm:ss »). */
    TIME,

    /** Jour de la semaine : MONDAY … SATURDAY, ou « lundi », « vendredi »… */
    DAY,

    /** Valeur prise dans une liste fermée (type de salle, type de séance…). */
    ENUM,

    /** Booléen : true / false. */
    BOOLEAN
}
