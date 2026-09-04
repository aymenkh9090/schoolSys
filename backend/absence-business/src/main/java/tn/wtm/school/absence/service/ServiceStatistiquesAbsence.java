package tn.wtm.school.absence.service;

import tn.wtm.school.absence.dto.reponse.ResumeAbsenceEleveReponse;
import tn.wtm.school.absence.dto.reponse.StatistiquesAbsenceReponse;

import java.time.LocalDate;

public interface ServiceStatistiquesAbsence {

    ResumeAbsenceEleveReponse resumeParEleve(Long eleveId, String anneeAcademique);

    StatistiquesAbsenceReponse resumeParClasse(Long groupeClasseId, LocalDate debut, LocalDate fin);

    StatistiquesAbsenceReponse dashboardAdmin(String tenantId, String periode);
}
