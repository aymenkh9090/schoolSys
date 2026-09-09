package tn.wtm.school.pointage.service;

import tn.wtm.school.pointage.dto.reponse.PresencePersonnelReponse;
import tn.wtm.school.pointage.dto.reponse.RapportJournalierReponse;
import tn.wtm.school.pointage.dto.reponse.ResultatPointageMasseReponse;
import tn.wtm.school.pointage.dto.requete.PointageMasseRequete;
import tn.wtm.school.pointage.dto.requete.PointageRequete;

import java.time.LocalDate;
import java.util.List;

public interface ServicePointage {
    PresencePersonnelReponse pointer(PointageRequete requete);
    ResultatPointageMasseReponse pointerEnMasse(PointageMasseRequete requete);
    PresencePersonnelReponse modifierPointage(Long presenceId, PointageRequete requete);
    RapportJournalierReponse obtenirRapportJournalier(LocalDate date);
    List<PresencePersonnelReponse> obtenirHistoriquePersonnel(Long membrePersonnelId, LocalDate debut, LocalDate fin);
}
