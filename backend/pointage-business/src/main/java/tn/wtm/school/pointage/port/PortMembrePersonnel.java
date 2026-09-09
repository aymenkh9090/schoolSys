package tn.wtm.school.pointage.port;

import tn.wtm.school.pointage.enums.TypePersonnel;

import java.util.Collection;
import java.util.Map;

/**
 * Résout le nom affichable d'un membre du personnel sans coupler le module
 * pointage au module organisation : un enseignant vit dans {@code Teacher},
 * un surveillant / administratif dans {@code SchoolUser}.
 */
public interface PortMembrePersonnel {

    /**
     * Noms complets indexés par identifiant. Les identifiants introuvables sont
     * simplement absents de la map — l'appelant retombe alors sur l'ID.
     */
    Map<Long, String> nomsParIds(String tenantId, TypePersonnel type, Collection<Long> ids);
}
