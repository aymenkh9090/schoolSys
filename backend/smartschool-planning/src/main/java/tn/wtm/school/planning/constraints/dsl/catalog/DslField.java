package tn.wtm.school.planning.constraints.dsl.catalog;

import lombok.Getter;
import tn.wtm.school.planning.constraints.dsl.enums.DslFieldType;
import tn.wtm.school.planning.solver.domain.Lesson;

import java.util.List;
import java.util.function.Function;

/**
 * Description d'un champ interrogeable par le DSL.
 *
 * <p>Un champ associe un nom public (celui écrit dans le JSON) à trois choses :
 * son type, la manière d'en extraire la valeur depuis une {@link Lesson}, et le
 * texte qui sera montré à l'utilisateur — et au LLM — pour l'expliquer.</p>
 *
 * <p>L'extracteur est une méthode Java écrite ici, pas une expression fournie de
 * l'extérieur. C'est ce qui rend le DSL sûr : le nom du champ n'est qu'une clé de
 * recherche dans une table figée à la compilation.</p>
 */
@Getter
public class DslField {

    private final String                 name;
    private final DslFieldType           type;
    private final String                 label;
    private final Function<Lesson, Object> extractor;
    private final List<String>           allowedValues;
    private final String                 example;

    DslField(String name,
             DslFieldType type,
             String label,
             Function<Lesson, Object> extractor,
             List<String> allowedValues,
             String example) {
        this.name          = name;
        this.type          = type;
        this.label         = label;
        this.extractor     = extractor;
        this.allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        this.example       = example;
    }

    /**
     * Valeur du champ pour cette séance, ou {@code null} si elle n'est pas encore
     * connue (séance non placée : ni jour ni heure). Une condition portant sur une
     * valeur nulle est toujours fausse — une séance non placée ne viole rien.
     */
    public Object valueOf(Lesson lesson) {
        try {
            return extractor.apply(lesson);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True quand le champ n'accepte qu'une liste fermée de valeurs. */
    public boolean isClosed() {
        return !allowedValues.isEmpty();
    }
}
