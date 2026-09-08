package tn.wtm.school.pointage.service;

import tn.wtm.school.pointage.dto.reponse.SuiviHeuresEnseignantReponse;
import tn.wtm.school.pointage.dto.requete.MiseAJourHeuresRequete;

import java.util.List;

public interface ServiceSuiviHeures {
    SuiviHeuresEnseignantReponse mettreAJourHeures(MiseAJourHeuresRequete requete);
    SuiviHeuresEnseignantReponse obtenirResumeSemaine(Long enseignantId, int numeroSemaine, String anneeAcademique);
    List<SuiviHeuresEnseignantReponse> obtenirResumeAnnuel(Long enseignantId, String anneeAcademique);
    void synchroniserDepuisPlanning(Long enseignantId, int numeroSemaine, String anneeAcademique, double heuresPrevues);
}
