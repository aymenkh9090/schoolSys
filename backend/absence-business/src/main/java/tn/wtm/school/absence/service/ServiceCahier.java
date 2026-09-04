package tn.wtm.school.absence.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.absence.dto.requete.EnregistrementCahierRequete;
import tn.wtm.school.absence.dto.reponse.CahierCorpusReponse;
import tn.wtm.school.absence.dto.reponse.CahierSeanceReponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ServiceCahier {

    CahierSeanceReponse enregistrer(Long seanceAppelId, EnregistrementCahierRequete requete);

    void verrouiller(Long seanceAppelId);

    CahierSeanceReponse recuperer(Long seanceAppelId);

    Page<CahierSeanceReponse> listerParEnseignant(Long enseignantId, LocalDateTime debut,
                                                   LocalDateTime fin, Pageable pageable);

    /**
     * Corpus des séances renseignées, enrichi de ses libellés, pour indexation.
     *
     * <p>Le périmètre n'est PAS un paramètre : il est déduit du compte appelant.
     * Un enseignant obtient ses propres séances, un administrateur celles de son
     * établissement. C'est délibéré — la méthode voisine
     * {@link #listerParEnseignant} prend un identifiant, ce qui laisse à
     * l'appelant le soin de ne pas demander le cahier d'un collègue. Pour une
     * lecture en masse destinée à un assistant conversationnel, cette garantie
     * devait être structurelle : ici, il n'existe aucun paramètre par lequel
     * réclamer les séances de quelqu'un d'autre.</p>
     *
     * @param depuis borne basse sur la date de séance, ou null pour tout l'historique
     * @param limite nombre maximum de séances, borné par le service
     */
    List<CahierCorpusReponse> construireCorpus(LocalDate depuis, int limite);
}
