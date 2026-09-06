package tn.wtm.school.planning.solver.validation;

/**
 * Un constat de la validation métier, destiné à être lu par un humain.
 *
 * @param severity gravité — seul {@link ValidationSeverity#BLOQUANT} refuse l'emploi du temps
 * @param code     identifiant stable du contrôle, pour regrouper et compter
 *                 (ex. {@code CONFLIT_SALLE}) ; jamais affiché tel quel
 * @param scope    ce qui est concerné, dit en français : « 7A / MATH »,
 *                 « Mme Ben Salah, lundi », « salle A1 »
 * @param message  ce qui ne va pas, en une phrase complète
 */
public record ValidationFinding(
        ValidationSeverity severity,
        String code,
        String scope,
        String message) {

    public static ValidationFinding bloquant(String code, String scope, String message) {
        return new ValidationFinding(ValidationSeverity.BLOQUANT, code, scope, message);
    }

    public static ValidationFinding avertissement(String code, String scope, String message) {
        return new ValidationFinding(ValidationSeverity.AVERTISSEMENT, code, scope, message);
    }

    /** « 7A / MATH — le volume placé est de 5 h contre 4 h au programme. » */
    public String ligne() {
        return scope == null || scope.isBlank() ? message : scope + " — " + message;
    }
}
