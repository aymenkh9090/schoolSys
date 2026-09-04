package tn.wtm.school.planning.constraints.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.wtm.school.planning.constraints.dsl.catalog.DslField;
import tn.wtm.school.planning.constraints.dsl.catalog.DslFieldCatalog;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslAggregateMetric;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dsl.model.DslCondition;
import tn.wtm.school.planning.constraints.dto.response.DslSchemaResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Publie le catalogue DSL sous une forme consommable par un client.
 *
 * <p>Le schéma est construit à partir des mêmes objets que ceux utilisés par le
 * validateur et le compilateur. Il ne peut donc pas mentir : si un champ
 * disparaît du catalogue, il disparaît du schéma, du formulaire et du prompt du
 * LLM au même instant. Une liste de champs maintenue à la main quelque part dans
 * un prompt aurait, elle, dérivé dès la première évolution.</p>
 */
@Service
@RequiredArgsConstructor
public class DslSchemaService {

    private final DslFieldCatalog catalog;

    public DslSchemaResponse describe() {
        return DslSchemaResponse.builder()
                .fields(catalog.all().stream().map(this::toFieldDescriptor).toList())
                .operators(Arrays.stream(DslOperator.values()).map(this::toOperatorDescriptor).toList())
                .scopes(Arrays.stream(DslScope.values())
                        .map(s -> descriptor(s.name(), s.getLabel()))
                        .toList())
                .severities(Arrays.stream(DslSeverity.values())
                        .map(s -> descriptor(s.name(), s.getLabel()))
                        .toList())
                .actions(List.of(
                        descriptor(DslAction.PENALIZE.name(), "Interdire / pénaliser"),
                        descriptor(DslAction.REWARD.name(), "Favoriser / récompenser")))
                .aggregateMetrics(Arrays.stream(DslAggregateMetric.values())
                        .map(m -> descriptor(m.name(), "Total en " + m.getUnitLabel()))
                        .toList())
                .example(example())
                .build();
    }

    private DslSchemaResponse.FieldDescriptor toFieldDescriptor(DslField field) {
        return DslSchemaResponse.FieldDescriptor.builder()
                .name(field.getName())
                .type(field.getType().name())
                .label(field.getLabel())
                .allowedValues(field.getAllowedValues())
                .example(field.getExample())
                // Les opérateurs sont filtrés par type : un client n'a jamais à
                // deviner que CONTAINS ne s'applique pas à une heure.
                .operators(Arrays.stream(DslOperator.values())
                        .filter(op -> op.supports(field.getType()))
                        .map(Enum::name)
                        .toList())
                .build();
    }

    private DslSchemaResponse.OperatorDescriptor toOperatorDescriptor(DslOperator operator) {
        return DslSchemaResponse.OperatorDescriptor.builder()
                .name(operator.name())
                .label(operator.getLabel())
                .supportedTypes(operator.getSupportedTypes().stream().map(Enum::name).sorted().toList())
                .arity(operator.arity())
                .build();
    }

    private static DslSchemaResponse.EnumDescriptor descriptor(String name, String label) {
        return DslSchemaResponse.EnumDescriptor.builder().name(name).label(label).build();
    }

    /**
     * Exemple canonique — celui du cahier des charges : « les classes de terminale
     * ne doivent jamais avoir de mathématiques après 15h le vendredi ».
     */
    private ConstraintDsl example() {
        return ConstraintDsl.builder()
                .scope(DslScope.LESSON)
                .conditions(List.of(
                        condition("class.level", DslOperator.EQUALS, "TERMINALE"),
                        condition("subject.code", DslOperator.EQUALS, "MATH"),
                        condition("day", DslOperator.EQUALS, "FRIDAY"),
                        condition("startTime", DslOperator.GREATER_THAN_OR_EQUAL, "15:00")))
                .action(DslAction.PENALIZE)
                .severity(DslSeverity.HARD)
                .weight(100)
                .build();
    }

    private static DslCondition condition(String field, DslOperator operator, String value) {
        return DslCondition.builder().field(field).operator(operator).value(value).build();
    }
}
