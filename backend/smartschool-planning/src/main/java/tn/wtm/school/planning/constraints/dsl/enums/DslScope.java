package tn.wtm.school.planning.constraints.dsl.enums;

/**
 * Portée d'une règle DSL : sur quel « objet » la condition est évaluée.
 *
 * <p>Deux familles :</p>
 * <ul>
 *   <li>{@link #LESSON} — la règle filtre des séances individuelles. Chaque séance
 *       qui satisfait toutes les conditions est pénalisée (ou récompensée).</li>
 *   <li>Les portées agrégées ({@code *_DAY}, {@code *_WEEK}) — les séances retenues
 *       par les conditions sont regroupées par entité (+ jour), puis un seuil défini
 *       dans {@code aggregate} est comparé au cumul du groupe.</li>
 * </ul>
 *
 * Le regroupement est fait par Timefold via {@code groupBy}, donc incrémental :
 * une portée agrégée ne coûte pas plus cher qu'une contrainte écrite à la main.
 */
public enum DslScope {

    /** Une séance à la fois. Exemple : « pas de maths le vendredi après 15h ». */
    LESSON(false, "Séance"),

    /** Cumul par enseignant et par jour. Exemple : « max 5h par jour ». */
    TEACHER_DAY(true, "Enseignant / jour"),

    /** Cumul par classe et par jour. Exemple : « max 6h par jour pour une classe ». */
    CLASS_DAY(true, "Classe / jour"),

    /** Cumul par salle et par jour. Exemple : « max 8h d'occupation du labo ». */
    ROOM_DAY(true, "Salle / jour"),

    /** Cumul par enseignant sur la semaine. Exemple : « max 18h hebdomadaires ». */
    TEACHER_WEEK(true, "Enseignant / semaine"),

    /** Cumul par classe sur la semaine. */
    CLASS_WEEK(true, "Classe / semaine");

    private final boolean aggregate;
    private final String  label;

    DslScope(boolean aggregate, String label) {
        this.aggregate = aggregate;
        this.label     = label;
    }

    /** True quand la portée exige un bloc {@code aggregate} (seuil sur un cumul). */
    public boolean isAggregate() {
        return aggregate;
    }

    public String getLabel() {
        return label;
    }
}
