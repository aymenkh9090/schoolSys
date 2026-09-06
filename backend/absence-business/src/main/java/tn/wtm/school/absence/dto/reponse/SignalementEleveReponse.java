package tn.wtm.school.absence.dto.reponse;

import lombok.*;
import tn.wtm.school.absence.enums.StatutJustificatif;
import tn.wtm.school.absence.enums.StatutPresence;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Une absence ou une exclusion qu'aucun justificatif validé n'a encore soldée.
 * <p>
 * C'est l'information qui manquait entre deux séances. Un élève marqué absent
 * en première heure par un enseignant restait invisible pour celui de la
 * deuxième : chacun ouvrait sa feuille d'appel sur une classe réputée entière,
 * et un élève parti de l'établissement pouvait n'être signalé qu'une fois. La
 * vue est donc rattachée à la CLASSE, et non à l'enseignant qui l'a saisie —
 * elle suit l'élève de séance en séance, quel que soit le professeur devant lui.
 * <p>
 * Elle disparaît quand la vie scolaire tranche : approuver un justificatif
 * marque la ligne d'appel justifiée, et le signalement sort de la liste. C'est
 * la seule sortie, et c'est voulu — un enseignant ne peut pas faire taire
 * l'alerte d'un collègue, il peut seulement la constater.
 * <p>
 * Les libellés (matière, enseignant) sont résolus ici plutôt que laissés aux
 * clients : c'est un écran de téléphone consulté en trois secondes au moment
 * d'entrer en classe, il ne va pas recroiser des identifiants.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SignalementEleveReponse {

    private Long eleveId;
    private Long ligneAppelId;
    private Long seanceAppelId;

    /** ABSENT ou EXCLU — les deux états qu'un collègue doit connaître. */
    private StatutPresence statut;

    private LocalDate dateSeance;

    /** Heure de début du créneau, quand la séance vient d'un emploi du temps. */
    private LocalTime heureDebut;

    private Long matiereId;
    private String matiere;
    private Long enseignantId;
    private String enseignant;

    private String raisonExclusion;

    /**
     * Dernier justificatif déposé sur cette absence, s'il existe. Un dépôt en
     * attente ne solde rien : il dit seulement que la famille a répondu et que
     * la vie scolaire ne s'est pas encore prononcée.
     */
    private Long justificatifId;
    private StatutJustificatif statutJustificatif;
}
