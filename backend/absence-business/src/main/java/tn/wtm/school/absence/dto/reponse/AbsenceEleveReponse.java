package tn.wtm.school.absence.dto.reponse;

import lombok.*;
import tn.wtm.school.absence.enums.StatutJustificatif;
import tn.wtm.school.absence.enums.StatutPresence;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Une absence (ou retard / exclusion) d'un élève, replacée dans sa séance.
 * <p>
 * C'est la vue dont la vie scolaire a besoin pour justifier une absence : elle
 * porte l'identifiant de ligne d'appel attendu par le circuit de justificatif,
 * mais aussi le jour, la classe et la matière — de quoi choisir l'absence dans
 * une liste au lieu d'en saisir l'identifiant à la main.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AbsenceEleveReponse {

    private Long ligneAppelId;
    private Long seanceAppelId;
    private Long eleveId;
    private LocalDate dateSeance;
    private Long groupeClasseId;
    private Long matiereId;
    private Long enseignantId;
    private String anneeAcademique;
    private LocalDateTime ouvertureAt;
    private StatutPresence statut;
    private Boolean estJustifie;
    private Integer minutesRetard;
    private String raisonExclusion;
    private Boolean seanceVerrouillee;

    /** Dernier justificatif déposé sur cette ligne — null si aucun. */
    private Long justificatifId;
    private StatutJustificatif statutJustificatif;
}
