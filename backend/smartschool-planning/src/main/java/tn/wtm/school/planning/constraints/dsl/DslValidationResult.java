package tn.wtm.school.planning.constraints.dsl;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Résultat de la validation d'une règle DSL.
 *
 * <p>On distingue erreurs et avertissements parce que les deux ne s'adressent pas
 * au même moment : une erreur bloque l'enregistrement, un avertissement laisse
 * passer la règle mais mérite d'être affiché à l'écran de confirmation
 * (« cette règle ne concerne aucune séance existante »).</p>
 */
@Getter
public class DslValidationResult {

    private final List<String> errors   = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** Résumé français de la règle, renseigné seulement si elle est valide. */
    private String summary;

    public static DslValidationResult ok() {
        return new DslValidationResult();
    }

    public DslValidationResult addError(String message) {
        errors.add(message);
        return this;
    }

    public DslValidationResult addWarning(String message) {
        warnings.add(message);
        return this;
    }

    public DslValidationResult withSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** Message d'erreur unique, pour lever une exception lisible. */
    public String errorMessage() {
        return String.join(" ; ", errors);
    }
}
