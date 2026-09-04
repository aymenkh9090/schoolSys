package tn.wtm.school.absence.port;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Accès en lecture au créneau d'une séance de l'emploi du temps, sans coupler
 * le module absence au module planning.
 */
public interface PortSeancePlanning {

    /**
     * Créneau hebdomadaire de la séance : jour de la semaine et bornes horaires.
     * Vide si la séance n'existe pas (appel ouvert manuellement en dehors d'un
     * emploi du temps généré, par exemple).
     */
    Optional<CreneauSeance> trouverCreneau(String tenantId, Long seancePlanningId);

    record CreneauSeance(DayOfWeek jour, LocalTime heureDebut, LocalTime heureFin) {}
}
