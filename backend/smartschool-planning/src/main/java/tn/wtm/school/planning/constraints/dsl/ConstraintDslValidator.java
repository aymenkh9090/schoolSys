package tn.wtm.school.planning.constraints.dsl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.planning.constraints.dsl.catalog.DslField;
import tn.wtm.school.planning.constraints.dsl.catalog.DslFieldCatalog;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dsl.model.DslAggregate;
import tn.wtm.school.planning.constraints.dsl.model.DslCondition;

import java.util.List;
import java.util.Locale;

/**
 * Validation sémantique d'une règle DSL, avant compilation et avant tout accès
 * au solveur.
 *
 * <p>C'est le point de contrôle unique : que la règle vienne d'un formulaire
 * React, d'un appel API direct ou d'une génération par le LLM, elle passe ici.
 * L'assistant IA n'a donc aucun privilège particulier — il propose du JSON comme
 * n'importe quel client, et ce JSON est jugé sur les mêmes critères.</p>
 *
 * <p>Les messages sont rédigés pour être lisibles par un administrateur <i>et</i>
 * réinjectables dans le prompt du modèle : quand la validation échoue, on renvoie
 * l'erreur au LLM, qui corrige généralement sa proposition au tour suivant.</p>
 */
@Component
@RequiredArgsConstructor
public class ConstraintDslValidator {

    /** Au-delà, la règle devient illisible pour un humain et coûteuse à évaluer. */
    private static final int MAX_CONDITIONS = 10;

    /** Bornes du poids : un poids démesuré écraserait toutes les autres contraintes. */
    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 1000;

    private final DslFieldCatalog        catalog;
    private final ConstraintDslCompiler  compiler;

    public DslValidationResult validate(ConstraintDsl dsl) {
        DslValidationResult result = DslValidationResult.ok();

        if (dsl == null) {
            return result.addError("Définition DSL absente.");
        }
        validateHeader(dsl, result);
        validateConditions(dsl, result);
        validateAggregate(dsl, result);

        if (result.isValid()) {
            result.withSummary(compiler.summarize(dsl));
        }
        return result;
    }

    // ── en-tête : portée, action, sévérité, poids ─────────────────────────────

    private void validateHeader(ConstraintDsl dsl, DslValidationResult result) {
        if (dsl.getScope() == null) {
            result.addError("La portée (scope) est obligatoire : "
                    + "LESSON, TEACHER_DAY, CLASS_DAY, ROOM_DAY, TEACHER_WEEK ou CLASS_WEEK.");
        }
        if (dsl.getSeverity() == null) {
            result.addError("La sévérité (severity) est obligatoire : HARD, MEDIUM ou SOFT.");
        }
        if (dsl.getAction() == null) {
            result.addError("L'action est obligatoire : PENALIZE ou REWARD.");
        }

        Integer weight = dsl.getWeight();
        if (weight == null) {
            result.addError("Le poids (weight) est obligatoire.");
        } else if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            result.addError("Le poids doit être compris entre " + MIN_WEIGHT
                    + " et " + MAX_WEIGHT + " (reçu : " + weight + ").");
        }

        // Récompenser au niveau HARD rendrait « faisable » une solution qui ne
        // l'est pas : le score dur ne doit mesurer que des violations.
        if (dsl.getAction() == DslAction.REWARD && dsl.getSeverity() == DslSeverity.HARD) {
            result.addError("Une règle de type REWARD ne peut pas être HARD : "
                    + "utilisez MEDIUM ou SOFT pour exprimer une préférence.");
        }
    }

    // ── conditions ────────────────────────────────────────────────────────────

    private void validateConditions(ConstraintDsl dsl, DslValidationResult result) {
        List<DslCondition> conditions = dsl.safeConditions();

        if (conditions.size() > MAX_CONDITIONS) {
            result.addError("Une règle ne peut pas dépasser " + MAX_CONDITIONS
                    + " conditions (reçu : " + conditions.size() + ").");
            return;
        }

        // Sans condition ET sans seuil, la règle pénaliserait toutes les séances :
        // le score s'effondrerait uniformément sans jamais guider le solveur.
        if (conditions.isEmpty() && dsl.getScope() != null && !dsl.getScope().isAggregate()) {
            result.addError("Une règle de portée LESSON doit comporter au moins une condition.");
        }

        for (int i = 0; i < conditions.size(); i++) {
            validateCondition(conditions.get(i), i + 1, result);
        }
    }

    private void validateCondition(DslCondition condition, int position, DslValidationResult result) {
        String prefix = "Condition " + position + " : ";

        if (condition.getOperator() == null) {
            result.addError(prefix + "l'opérateur est obligatoire.");
            return;
        }

        DslField field = catalog.find(condition.getField()).orElse(null);
        if (field == null) {
            result.addError(prefix + "champ inconnu « " + condition.getField()
                    + " ». Champs disponibles : " + String.join(", ", catalog.names()) + ".");
            return;
        }

        DslOperator operator = condition.getOperator();
        if (!operator.supports(field.getType())) {
            result.addError(prefix + "l'opérateur " + operator
                    + " ne s'applique pas à un champ de type " + field.getType()
                    + " (« " + field.getName() + " »).");
            return;
        }

        List<String> values = condition.effectiveValues();
        if (values.isEmpty()) {
            result.addError(prefix + "aucune valeur fournie pour « " + field.getName() + " ».");
            return;
        }
        if (operator.arity() > 0 && values.size() != operator.arity()) {
            result.addError(prefix + "l'opérateur " + operator + " attend "
                    + operator.arity() + " valeur(s), " + values.size() + " fournie(s).");
            return;
        }

        for (String raw : values) {
            validateValue(field, raw, prefix, result);
        }
    }

    private void validateValue(DslField field, String raw, String prefix, DslValidationResult result) {
        try {
            DslValues.coerce(field.getType(), raw);
        } catch (DslValues.CoercionException e) {
            result.addError(prefix + "valeur invalide pour « " + field.getName()
                    + " » : " + e.getMessage() + ".");
            return;
        }

        // Champ à valeurs fermées (type de salle, type de séance…) : une valeur
        // hors liste ne déclencherait jamais la règle. C'est le cas le plus
        // fréquent d'hallucination du LLM, et le plus silencieux — donc bloquant.
        if (field.isClosed()) {
            String upper = raw.trim().toUpperCase(Locale.ROOT);
            boolean known = field.getAllowedValues().stream().anyMatch(v -> v.equalsIgnoreCase(upper));
            if (!known) {
                result.addError(prefix + "« " + raw + " » n'est pas une valeur possible pour « "
                        + field.getName() + " ». Valeurs admises : "
                        + String.join(", ", field.getAllowedValues()) + ".");
            }
        }
    }

    // ── seuil agrégé ──────────────────────────────────────────────────────────

    private void validateAggregate(ConstraintDsl dsl, DslValidationResult result) {
        if (dsl.getScope() == null) {
            return;
        }
        DslAggregate aggregate = dsl.getAggregate();

        if (!dsl.getScope().isAggregate()) {
            if (aggregate != null) {
                result.addError("La portée LESSON n'accepte pas de seuil « aggregate » : "
                        + "utilisez une portée agrégée (TEACHER_DAY, CLASS_DAY…) pour exprimer un cumul.");
            }
            return;
        }

        if (aggregate == null) {
            result.addError("La portée " + dsl.getScope()
                    + " exige un seuil « aggregate » (metric, operator, value).");
            return;
        }
        if (aggregate.getMetric() == null) {
            result.addError("La métrique du seuil est obligatoire : TOTAL_HOURS, TOTAL_SLOTS ou LESSON_COUNT.");
        }
        if (aggregate.getOperator() == null) {
            result.addError("L'opérateur du seuil est obligatoire.");
        } else if (aggregate.getOperator().isMultiValue()
                || aggregate.getOperator() == DslOperator.CONTAINS
                || aggregate.getOperator() == DslOperator.STARTS_WITH) {
            result.addError("L'opérateur " + aggregate.getOperator()
                    + " n'a pas de sens sur un cumul : utilisez une comparaison numérique.");
        }
        if (aggregate.getValue() == null) {
            result.addError("La valeur du seuil est obligatoire.");
        } else if (aggregate.getValue() < 0) {
            result.addError("Le seuil ne peut pas être négatif.");
        } else if (aggregate.getValue() == 0
                && aggregate.getOperator() == DslOperator.GREATER_THAN) {
            result.addWarning("Un seuil de 0 heure interdit purement et simplement toute séance "
                    + "correspondant aux conditions : vérifiez que c'est bien l'intention.");
        }
    }
}
