package tn.wtm.school.planning.solver.constraint;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;

/**
 * Lightweight problem fact that activates a configurable constraint at solve time.
 *
 * Presence in {@link tn.wtm.school.planning.solver.domain.TimetableSolution#activeConstraintParams}
 * enables the constraint. Absence disables it — the JOIN in the constraint stream returns no
 * matches, producing zero penalty naturally.
 *
 * Thread-safe: immutable, one instance per active constraint per solve job.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ActiveConstraintParam {

    /** Constraint code — must match {@code constraint_definition.code} exactly. */
    @EqualsAndHashCode.Include
    private final String code;

    /**
     * Primary integer parameter extracted from {@code parametersJson}.
     * e.g. maxHours=6, maxConsecutiveSessions=2, weeklySessions=3.
     * 0 when the constraint has no numeric parameter.
     */
    private final int intParam;

    /**
     * Soft-constraint penalty multiplier derived from {@link tn.wtm.school.planning.constraints.enums.ImportanceLevel}.
     * CRITICAL=1000 | HIGH=100 | MEDIUM=10 | LOW=1.
     * Used as a match-weight multiplier for SOFT constraints.
     */
    private final int softWeight;

    /**
     * Tous les paramètres de la contrainte, par nom, tels que configurés par
     * l'établissement dans {@code constraint_setting.parameters_json}.
     *
     * <p>{@link #intParam} n'expose que le premier paramètre numérique, ce qui
     * suffit aux contraintes à un seul seuil mais devient ambigu dès qu'une
     * contrainte en a deux (« au moins 2 niveaux ET au plus 4 classes »). Les
     * flux qui en ont besoin lisent ici par nom, sans dépendre de l'ordre des
     * clés JSON — un ordre sur lequel il serait imprudent de compter.</p>
     */
    private final Map<String, Object> params;

    public ActiveConstraintParam(String code, int intParam, int softWeight) {
        this(code, intParam, softWeight, Map.of());
    }

    public ActiveConstraintParam(String code, int intParam, int softWeight, Map<String, Object> params) {
        this.code       = code;
        this.intParam   = intParam;
        this.softWeight = softWeight;
        this.params     = params == null ? Map.of() : Map.copyOf(params);
    }

    /** Paramètre entier nommé, ou {@code fallback} s'il est absent ou non numérique. */
    public int getInt(String name, int fallback) {
        Object value = params.get(name);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    /** Paramètre décimal nommé, ou {@code fallback}. */
    public double getDouble(String name, double fallback) {
        Object value = params.get(name);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    /** Paramètre booléen nommé, ou {@code fallback}. */
    public boolean getBoolean(String name, boolean fallback) {
        Object value = params.get(name);
        return value instanceof Boolean b ? b : fallback;
    }
}
