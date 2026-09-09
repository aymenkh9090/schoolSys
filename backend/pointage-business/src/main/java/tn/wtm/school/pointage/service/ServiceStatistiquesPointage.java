package tn.wtm.school.pointage.service;

import tn.wtm.school.pointage.dto.reponse.StatistiquesPresenceReponse;
import tn.wtm.school.pointage.enums.TypePersonnel;

import java.time.LocalDate;
import java.util.List;

public interface ServiceStatistiquesPointage {
    StatistiquesPresenceReponse obtenirStatistiquesPersonnel(Long membrePersonnelId, LocalDate debut, LocalDate fin);
    List<StatistiquesPresenceReponse> obtenirStatistiquesParTypePersonnel(TypePersonnel type, LocalDate debut, LocalDate fin);
}
