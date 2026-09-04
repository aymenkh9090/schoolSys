package tn.wtm.school.planning.constraints.dsl;

import tn.wtm.school.planning.constraints.dsl.enums.DslFieldType;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * Conversion des valeurs du DSL vers des objets Java comparables.
 *
 * <p>Deux directions, et une règle : la conversion est pilotée par le <b>type
 * déclaré du champ</b> dans le catalogue, jamais par la forme du littéral JSON.
 * « 15:00 » n'est une heure que parce que {@code startTime} est de type TIME ;
 * sur un champ STRING, ce serait une chaîne. Le LLM ne peut donc pas changer le
 * sens d'une comparaison en changeant l'écriture d'une valeur.</p>
 */
public final class DslValues {

    private DslValues() {}

    /** Jours acceptés en français, en plus des noms {@link DayOfWeek}. */
    private static final Map<String, DayOfWeek> FRENCH_DAYS = Map.of(
            "lundi",    DayOfWeek.MONDAY,
            "mardi",    DayOfWeek.TUESDAY,
            "mercredi", DayOfWeek.WEDNESDAY,
            "jeudi",    DayOfWeek.THURSDAY,
            "vendredi", DayOfWeek.FRIDAY,
            "samedi",   DayOfWeek.SATURDAY,
            "dimanche", DayOfWeek.SUNDAY);

    /** Levée quand une valeur du DSL n'est pas convertible dans le type du champ. */
    public static class CoercionException extends RuntimeException {
        public CoercionException(String message) {
            super(message);
        }
    }

    // ── valeur écrite dans le DSL → objet comparable ──────────────────────────

    public static Comparable<?> coerce(DslFieldType type, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CoercionException("valeur vide");
        }
        String text = raw.trim();
        return switch (type) {
            case STRING  -> normalize(text);
            case ENUM    -> text.toUpperCase(Locale.ROOT);
            case NUMBER  -> parseNumber(text);
            case TIME    -> parseTime(text);
            case DAY     -> parseDay(text);
            case BOOLEAN -> parseBoolean(text);
        };
    }

    // ── valeur lue sur la séance → même forme comparable ──────────────────────

    public static Comparable<?> fromLesson(DslFieldType type, Object value) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case STRING  -> normalize(String.valueOf(value));
            case ENUM    -> value instanceof Enum<?> e
                                ? e.name()
                                : String.valueOf(value).toUpperCase(Locale.ROOT);
            case NUMBER  -> value instanceof Number n ? n.doubleValue() : parseNumber(String.valueOf(value));
            case TIME    -> value instanceof LocalTime t ? t : parseTime(String.valueOf(value));
            case DAY     -> value instanceof DayOfWeek d ? d : parseDay(String.valueOf(value));
            case BOOLEAN -> value instanceof Boolean b ? b : parseBoolean(String.valueOf(value));
        };
    }

    // ── comparaison ───────────────────────────────────────────────────────────

    /**
     * Compare deux valeurs déjà converties dans le même type.
     * Renvoie {@code null} quand la comparaison n'a pas de sens — ce qui rend la
     * condition fausse plutôt que de faire échouer le solveur en plein calcul.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Integer compare(Comparable<?> a, Comparable<?> b) {
        if (a == null || b == null || !a.getClass().equals(b.getClass())) {
            return null;
        }
        try {
            return ((Comparable) a).compareTo(b);
        } catch (ClassCastException e) {
            return null;
        }
    }

    // ── parsers ───────────────────────────────────────────────────────────────

    private static Double parseNumber(String text) {
        try {
            return Double.valueOf(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new CoercionException("« " + text + " » n'est pas un nombre");
        }
    }

    private static LocalTime parseTime(String text) {
        String candidate = text.replace('h', ':');
        if (candidate.endsWith(":")) {
            candidate = candidate + "00";
        }
        try {
            return LocalTime.parse(candidate.length() == 4 ? "0" + candidate : candidate);
        } catch (DateTimeParseException e) {
            throw new CoercionException("« " + text + " » n'est pas une heure au format HH:mm");
        }
    }

    private static DayOfWeek parseDay(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        try {
            return DayOfWeek.valueOf(upper);
        } catch (IllegalArgumentException ignored) {
            DayOfWeek french = FRENCH_DAYS.get(normalize(text));
            if (french != null) {
                return french;
            }
            throw new CoercionException("« " + text + " » n'est pas un jour de la semaine");
        }
    }

    private static Boolean parseBoolean(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("oui") || lower.equals("1")) {
            return Boolean.TRUE;
        }
        if (lower.equals("false") || lower.equals("non") || lower.equals("0")) {
            return Boolean.FALSE;
        }
        throw new CoercionException("« " + text + " » n'est ni vrai ni faux");
    }

    /**
     * Minuscules, sans accents, sans espaces superflus.
     *
     * <p>Indispensable pour les comparaisons de chaînes : une école qui saisit
     * « Mathématiques » et une base qui stocke « MATHEMATIQUES » doivent donner
     * le même résultat, sinon la règle ne se déclenche jamais et l'utilisateur
     * conclut — à tort — que le moteur est cassé.</p>
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.toLowerCase(Locale.ROOT);
    }
}
