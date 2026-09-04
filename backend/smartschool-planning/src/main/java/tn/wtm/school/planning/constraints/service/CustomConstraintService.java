package tn.wtm.school.planning.constraints.service;

import tn.wtm.school.planning.constraints.dto.request.CustomConstraintRequest;
import tn.wtm.school.planning.constraints.dto.request.DslAnalysisRequest;
import tn.wtm.school.planning.constraints.dto.response.CustomConstraintResponse;
import tn.wtm.school.planning.constraints.dto.response.DslAnalysisResponse;

import java.util.List;

/**
 * Gestion des contraintes réellement personnalisées (niveau C du catalogue).
 *
 * <p>Toute écriture passe par {@link #create} ou {@link #update}, qui valident le
 * DSL avant d'enregistrer. Il n'existe volontairement aucun chemin permettant de
 * stocker une règle non validée — pas même pour l'assistant IA, qui utilise
 * exactement les mêmes méthodes que l'interface.</p>
 */
public interface CustomConstraintService {

    /** Analyse une règle candidate sans rien enregistrer : validation, impact, conflits. */
    DslAnalysisResponse analyze(DslAnalysisRequest request);

    CustomConstraintResponse create(CustomConstraintRequest request);

    CustomConstraintResponse update(Long id, CustomConstraintRequest request);

    List<CustomConstraintResponse> findAll(Long profileId);

    CustomConstraintResponse findById(Long id);

    /** Active ou désactive la règle sans la supprimer — le cas d'usage courant. */
    CustomConstraintResponse setEnabled(Long id, boolean enabled);

    void delete(Long id);
}
