package tn.wtm.school.pointage.service;

import tn.wtm.school.pointage.dto.reponse.JustificatifPointageReponse;
import tn.wtm.school.pointage.dto.requete.SoumissionJustificatifPointageRequete;
import tn.wtm.school.pointage.dto.requete.TraitementJustificatifPointageRequete;

import java.util.List;

public interface ServiceJustificatifPointage {
    JustificatifPointageReponse soumettreJustificatif(SoumissionJustificatifPointageRequete requete);
    JustificatifPointageReponse traiterJustificatif(Long justificatifId, TraitementJustificatifPointageRequete requete);
    List<JustificatifPointageReponse> listerJustificatifsEnAttente();
    JustificatifPointageReponse obtenirJustificatifParPresence(Long presenceId);
}
