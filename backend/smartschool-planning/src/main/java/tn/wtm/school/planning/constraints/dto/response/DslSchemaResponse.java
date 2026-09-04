package tn.wtm.school.planning.constraints.dto.response;

import lombok.*;

import java.util.List;

/**
 * Catalogue DSL publié par l'API.
 *
 * <p>Une seule source pour trois consommateurs : les listes déroulantes de
 * l'interface, la documentation, et le prompt de l'assistant IA. Le modèle ne
 * peut pas inventer un champ qui n'existe pas s'il reçoit la liste exacte de
 * ceux qui existent — et si le catalogue évolue, le prompt évolue avec lui sans
 * qu'on ait à y penser.</p>
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DslSchemaResponse {

    private List<FieldDescriptor>    fields;
    private List<OperatorDescriptor> operators;
    private List<EnumDescriptor>     scopes;
    private List<EnumDescriptor>     severities;
    private List<EnumDescriptor>     actions;
    private List<EnumDescriptor>     aggregateMetrics;

    /** Exemple complet et valide, utile en documentation comme en few-shot prompt. */
    private Object example;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class FieldDescriptor {
        private String       name;
        private String       type;
        private String       label;
        private List<String> allowedValues;
        private String       example;
        /** Opérateurs réellement applicables à ce champ, compte tenu de son type. */
        private List<String> operators;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class OperatorDescriptor {
        private String       name;
        private String       label;
        private List<String> supportedTypes;
        /** Nombre de valeurs attendues ; -1 pour une liste de taille libre. */
        private int          arity;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class EnumDescriptor {
        private String name;
        private String label;
    }
}
