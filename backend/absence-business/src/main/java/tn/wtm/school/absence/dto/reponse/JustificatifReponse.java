package tn.wtm.school.absence.dto.reponse;

import lombok.*;
import tn.wtm.school.absence.enums.StatutJustificatif;
import tn.wtm.school.absence.enums.TypeJustificatif;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JustificatifReponse {

    private Long id;
    private Long ligneAppelId;
    private Long eleveId;
    /** Séance concernée — évite au client de recharger l'appel pour situer l'absence. */
    private LocalDate dateSeance;
    private Long groupeClasseId;
    private Long matiereId;
    private LocalDateTime soumisAt;
    private Long soumisParId;
    private TypeJustificatif typeDocument;
    private String referenceDocument;
    private StatutJustificatif statut;
    private LocalDateTime traiteAt;
    private Long traiteParId;
    private String notesAdmin;
}
