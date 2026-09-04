package tn.wtm.school.absence.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Ce que le module absence a besoin de savoir du module organisation, sans en
 * dépendre : l'identité de l'enseignant connecté et les libellés lisibles des
 * identifiants qu'il stocke (enseignant, classe, matière).
 *
 * <p>Ces libellés n'ont d'intérêt que pour le corpus d'indexation. Un cahier de
 * séance ne porte que des identifiants numériques ; un moteur de recherche
 * sémantique a besoin des mots que l'utilisateur emploie — « 7ème B », « SVT »,
 * « Mme Trabelsi » — sinon la question « où en est la 7ème B en SVT ? » ne
 * ressemble à aucun document indexé.</p>
 */
public interface PortContexteScolaire {

    /**
     * Fiche enseignant liée au compte appelant, si l'appelant est un enseignant.
     *
     * <p>Vide pour un administrateur ou un surveillant : c'est cette absence qui
     * fait basculer le corpus du périmètre « mes séances » au périmètre
     * « tout l'établissement ».</p>
     */
    Optional<Long> idEnseignantCourant();

    /** Nom complet des enseignants, par identifiant. Les inconnus sont absents. */
    Map<Long, String> nomsEnseignants(String tenantId, Collection<Long> ids);

    /** Code des classes (« 7B »), par identifiant. */
    Map<Long, String> codesClasses(String tenantId, Collection<Long> ids);

    /** Libellé des matières (« Sciences de la vie et de la Terre »), par identifiant. */
    Map<Long, String> libellesMatieres(String tenantId, Collection<Long> ids);
}
