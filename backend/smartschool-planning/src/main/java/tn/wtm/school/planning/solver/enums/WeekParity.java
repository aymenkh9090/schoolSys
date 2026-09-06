package tn.wtm.school.planning.solver.enums;

/**
 * Semaines pendant lesquelles une séance a effectivement lieu.
 *
 * <p>Traduit la notation {@code ①} de la circulaire n°66 — « séance de quinzaine
 * pour la classe entière » (§ N.1). Une séance de quinzaine n'occupe son créneau
 * qu'une semaine sur deux : deux séances de quinzaine opposées peuvent donc
 * partager le même créneau, la même salle et le même enseignant sans se heurter.
 *
 * <p>C'est tout l'intérêt de porter la parité jusqu'au solveur. Sans elle,
 * {@code LessonGenerator} n'avait que deux mauvaises options, et prenait les
 * deux : supprimer les séances {@code ODD}/{@code EVEN} — la séance disparaît
 * de l'emploi du temps —, ou traiter {@code BIWEEKLY} comme hebdomadaire — le
 * volume horaire double. Les deux faussent les volumes du § T.1.
 *
 * <p>Le modèle organisation connaît une quatrième valeur, {@code BIWEEKLY} :
 * « une semaine sur deux », sans dire laquelle. Elle n'existe pas ici — une
 * séance doit savoir quand elle a lieu pour qu'on puisse raisonner dessus. La
 * conversion est faite à la génération ({@code LessonGenerator}).
 */
public enum WeekParity {

    /** Toutes les semaines — le cas ordinaire. */
    ALL,

    /** Semaines impaires seulement. */
    ODD,

    /** Semaines paires seulement. */
    EVEN;

    /**
     * Deux séances peuvent-elles tomber la même semaine ?
     *
     * <p>Seul {@code ODD} face à {@code EVEN} garantit qu'elles ne se croisent
     * jamais. {@code ALL} croise tout, y compris lui-même. C'est volontairement
     * la relation la plus prudente : on ne déclare l'absence de conflit que
     * lorsqu'elle est certaine.
     */
    public boolean overlapsWith(WeekParity other) {
        if (this == ALL || other == ALL) {
            return true;
        }
        return this == other;
    }
}
