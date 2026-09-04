package tn.wtm.school.absence.repository.projection;

import java.time.LocalDate;

/**
 * Ligne brute du corpus, telle qu'elle sort de la base.
 *
 * <p>Projection et non entité : la requête ne ramène que les colonnes réellement
 * indexées, sans charger la séance d'appel ni ses lignes d'élèves. Sur un
 * établissement d'un an d'ancienneté, le corpus se compte en milliers de
 * séances — charger les graphes JPA correspondants coûterait plusieurs secondes
 * pour des données dont on ne lit que six champs de texte.</p>
 */
public record CahierCorpusRow(
        Long id,
        Long seanceAppelId,
        Long enseignantId,
        Long groupeClasseId,
        Long matiereId,
        LocalDate dateSeance,
        String anneeAcademique,
        String sujet,
        String chapitre,
        String activites,
        String remarques,
        String travailDemande,
        LocalDate dateEcheance
) {}
