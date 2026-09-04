package tn.wtm.school.absence.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.absence.dto.requete.SoumissionJustificatifRequete;
import tn.wtm.school.absence.dto.requete.TraitementJustificatifRequete;
import tn.wtm.school.absence.dto.reponse.JustificatifReponse;
import tn.wtm.school.absence.enums.StatutJustificatif;

import java.time.LocalDateTime;

public interface ServiceJustificatif {

    JustificatifReponse soumettre(SoumissionJustificatifRequete requete);

    JustificatifReponse approuver(Long justificatifId, TraitementJustificatifRequete requete);

    JustificatifReponse refuser(Long justificatifId, TraitementJustificatifRequete requete);

    /**
     * Liste les justificatifs du tenant. {@code eleveId} nul = tous les élèves,
     * ce qui donne la file d'attente de traitement de la vie scolaire.
     */
    Page<JustificatifReponse> lister(Long eleveId, StatutJustificatif statut,
                                      LocalDateTime debut, LocalDateTime fin, Pageable pageable);
}
