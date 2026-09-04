package tn.wtm.school.planning.constraints.dsl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;

/**
 * Sérialisation / désérialisation du contrat DSL.
 *
 * <p>Le mapper est <b>strict</b> et volontairement distinct de celui de
 * l'application : une propriété inconnue fait échouer la lecture au lieu d'être
 * ignorée silencieusement. C'est essentiel face à un LLM, qui invente volontiers
 * des clés plausibles (« unit », « target », « description ») : mieux vaut un
 * refus explicite, que le modèle peut corriger au tour suivant, qu'une règle
 * enregistrée en ignorant la moitié de ce que l'utilisateur croyait avoir dit.</p>
 */
@Component
public class ConstraintDslParser {

    private final ObjectMapper strictMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /** Lit une règle depuis son JSON. Lève une 400 lisible en cas de JSON invalide. */
    public ConstraintDsl parse(String json) {
        if (json == null || json.isBlank()) {
            throw new BadRequestException("La définition DSL est vide.");
        }
        try {
            ConstraintDsl dsl = strictMapper.readValue(json, ConstraintDsl.class);
            if (dsl == null) {
                throw new BadRequestException("La définition DSL est vide.");
            }
            return dsl;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Définition DSL illisible : " + rootCause(e));
        }
    }

    public String write(ConstraintDsl dsl) {
        try {
            return strictMapper.writeValueAsString(dsl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation DSL impossible", e);
        }
    }

    /**
     * Message d'erreur Jackson réduit à sa première ligne : la trace complète
     * contient le chemin de classe interne, inutile — voire trompeur — pour
     * l'utilisateur comme pour le modèle qui doit se corriger.
     */
    private static String rootCause(JsonProcessingException e) {
        String message = e.getOriginalMessage();
        if (message == null) {
            return "format JSON invalide";
        }
        int cut = message.indexOf('\n');
        return cut > 0 ? message.substring(0, cut) : message;
    }
}
