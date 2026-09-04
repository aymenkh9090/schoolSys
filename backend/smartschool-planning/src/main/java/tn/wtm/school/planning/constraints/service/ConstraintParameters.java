package tn.wtm.school.planning.constraints.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lecture des paramètres d'une contrainte configurable.
 *
 * <p>Deux formes de JSON coexistent dans {@code constraint_setting.parameters_json},
 * et il faut savoir les distinguer :</p>
 * <ul>
 *   <li>le <b>schéma</b>, copié depuis {@code constraint_definition.parameter_schema} :
 *       {@code {"maxHours": {"type": "number", "default": 6}}} ;</li>
 *   <li>les <b>valeurs</b>, telles que configurées par l'établissement :
 *       {@code {"maxHours": 3}}.</li>
 * </ul>
 *
 * <p>Les profils créés avant l'ajout de {@link #materializeDefaults} contiennent
 * la première forme. Or un schéma lu comme des valeurs ne donne aucun nombre : le
 * seuil retombait à 0 et la contrainte, bien qu'« activée » dans l'interface, ne
 * pénalisait rien. C'est pourquoi la lecture ci-dessous accepte les deux formes
 * et déplie {@code default} quand elle rencontre un schéma — la donnée existante
 * redevient correcte sans migration ni réécriture des profils.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConstraintParameters {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    /** Clé du schéma portant la valeur par défaut d'un paramètre. */
    private static final String DEFAULT_KEY = "default";

    private final ObjectMapper objectMapper;

    /**
     * Valeurs effectives des paramètres, quelle que soit la forme du JSON.
     * Renvoie une map vide (jamais null) quand le JSON est absent ou illisible.
     */
    public Map<String, Object> readValues(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(json, JSON_MAP);
        } catch (Exception e) {
            log.warn("Paramètres de contrainte illisibles ({}) : {}", json, e.getMessage());
            return Map.of();
        }

        Map<String, Object> values = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (value instanceof Map<?, ?> descriptor) {
                // Forme « schéma » : on retient la valeur par défaut déclarée.
                Object fallback = descriptor.get(DEFAULT_KEY);
                if (fallback != null) {
                    values.put(key, fallback);
                }
            } else if (value != null) {
                values.put(key, value);
            }
        });
        return values;
    }

    /**
     * Convertit un schéma de définition en JSON de valeurs, pour initialiser un
     * profil avec des paramètres réellement exploitables par le solveur.
     */
    public String materializeDefaults(String parameterSchema) {
        Map<String, Object> values = readValues(parameterSchema);
        if (values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            log.warn("Sérialisation des paramètres par défaut impossible : {}", e.getMessage());
            return null;
        }
    }

    /**
     * Premier paramètre numérique, dans l'ordre de déclaration.
     * Conservé pour les flux de contraintes à seuil unique déjà écrits ainsi.
     */
    public int firstInt(Map<String, Object> values) {
        return values.values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .findFirst()
                .orElse(0);
    }
}
