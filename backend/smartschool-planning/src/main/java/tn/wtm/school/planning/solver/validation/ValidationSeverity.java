package tn.wtm.school.planning.solver.validation;

/**
 * Gravité d'un constat de la validation métier.
 *
 * <p>La distinction n'est pas cosmétique : seul {@link #BLOQUANT} empêche un job
 * de passer {@code SOLVED}. Ranger un constat ici, c'est décider s'il justifie de
 * refuser un emploi du temps à un établissement.
 */
public enum ValidationSeverity {

    /**
     * L'emploi du temps ne peut pas être remis en l'état.
     *
     * <p>Trois familles, et trois seulement : une impossibilité physique (deux
     * cours dans la même salle au même moment), un défaut de génération (une
     * séance jamais placée, une séance en double) ou un volume horaire qui
     * s'écarte du programme officiel. Aucune préférence pédagogique n'entre ici.
     */
    BLOQUANT,

    /**
     * L'emploi du temps est remettable, mais quelque chose mérite d'être dit.
     *
     * <p>Sert notamment à signaler ce que la validation <em>n'a pas pu</em>
     * vérifier — un volume officiel absent des données, par exemple. Un contrôle
     * qui se tait faute de donnée et ne le dit pas est pire qu'un contrôle
     * absent : il laisse croire que la vérification a eu lieu.
     */
    AVERTISSEMENT
}
