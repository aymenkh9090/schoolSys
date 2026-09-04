package tn.wtm.school.planning.solver.builder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.planning.constraints.dsl.CompiledConstraint;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslCompiler;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslParser;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslValidator;
import tn.wtm.school.planning.constraints.dsl.DslValidationResult;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.entity.CustomConstraint;
import tn.wtm.school.planning.constraints.repository.CustomConstraintRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Charge les contraintes personnalisées d'un profil et les compile en faits du
 * problème, une fois par génération.
 *
 * <p>Compiler ici, et pas dans les flux de contraintes, est délibéré : la lecture
 * en base et la conversion des valeurs (heures, jours) se font une seule fois,
 * puis le solveur ne manipule plus que des prédicats en mémoire. Faire l'inverse
 * — relire le JSON à chaque évaluation — coûterait des ordres de grandeur, le
 * solveur évaluant les contraintes des millions de fois par génération.</p>
 *
 * <p>Une règle invalide est <b>ignorée avec un avertissement</b> plutôt que de
 * faire échouer la génération. Une règle a pu être écrite avant qu'un champ ne
 * change de nom ; refuser de générer l'emploi du temps de tout l'établissement
 * pour cette raison serait une réaction disproportionnée. La règle reste visible
 * et en erreur dans l'interface, où l'administrateur peut la corriger.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CustomConstraintLoader {

    private final CustomConstraintRepository repository;
    private final ConstraintDslParser        parser;
    private final ConstraintDslValidator     validator;
    private final ConstraintDslCompiler      compiler;

    public List<CompiledConstraint> load(String tenantId, Long profileId) {
        List<CustomConstraint> rules = repository.findActiveByProfileAndTenantId(profileId, tenantId);
        List<CompiledConstraint> compiled = new ArrayList<>(rules.size());

        for (CustomConstraint rule : rules) {
            compile(rule).ifPresent(compiled::add);
        }

        if (!compiled.isEmpty()) {
            log.info("Tenant {} : {} contrainte(s) personnalisée(s) active(s) sur {} enregistrée(s)",
                    tenantId, compiled.size(), rules.size());
        }
        return compiled;
    }

    private java.util.Optional<CompiledConstraint> compile(CustomConstraint rule) {
        try {
            ConstraintDsl dsl = parser.parse(rule.getDslJson());
            DslValidationResult validation = validator.validate(dsl);
            if (!validation.isValid()) {
                log.warn("Contrainte personnalisée « {} » ignorée (invalide) : {}",
                        rule.getCode(), validation.errorMessage());
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(compiler.compile(
                    dsl,
                    rule.getIdCustomConstraint(),
                    rule.getCode(),
                    rule.getName()));
        } catch (RuntimeException e) {
            log.warn("Contrainte personnalisée « {} » ignorée : {}", rule.getCode(), e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
