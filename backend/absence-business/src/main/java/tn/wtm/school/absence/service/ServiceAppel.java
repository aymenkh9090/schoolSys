package tn.wtm.school.absence.service;

import tn.wtm.school.absence.dto.requete.ModificationStatutRequete;
import tn.wtm.school.absence.dto.requete.OuvertureAppelRequete;
import tn.wtm.school.absence.dto.reponse.AbsenceEleveReponse;
import tn.wtm.school.absence.dto.reponse.AppelReponse;
import tn.wtm.school.absence.dto.reponse.HistoriqueAppelReponse;
import tn.wtm.school.absence.dto.reponse.LigneAppelReponse;

import java.time.LocalDate;
import java.util.List;

public interface ServiceAppel {

    AppelReponse ouvrirOuRecupererAppel(OuvertureAppelRequete requete);

    AppelReponse recupererAppel(Long seanceAppelId);

    /**
     * Liste les séances d'appel du tenant pour une journée donnée (par défaut
     * aujourd'hui), avec filtres optionnels — pour parcourir les séances sans
     * connaître leur ID.
     */
    List<AppelReponse> listerSeances(LocalDate date, Long groupeClasseId, Long enseignantId, Boolean estVerrouille);

    /**
     * Dossier d'absences d'un élève : ses lignes d'appel non « présent » sur la
     * période, replacées dans leur séance et accompagnées de l'état du dernier
     * justificatif déposé.
     *
     * @param retards inclure les retards et exclusions en plus des absences
     */
    List<AbsenceEleveReponse> listerAbsencesEleve(Long eleveId, LocalDate debut, LocalDate fin, boolean retards);

    LigneAppelReponse modifierStatutEleve(Long ligneAppelId, ModificationStatutRequete requete);

    void verrouillerSeance(Long seanceAppelId, Long adminId);

    List<HistoriqueAppelReponse> recupererHistorique(Long seanceAppelId);
}
