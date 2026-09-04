package tn.wtm.school.absence.dto.reponse;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CahierSeanceReponse {

    private Long id;
    private Long seanceAppelId;
    private Long enseignantId;
    private String sujet;
    private String chapitre;
    private String activites;
    private String remarques;
    private String travailDemande;
    private LocalDate dateEcheance;
    private Boolean estVerrouille;
    private LocalDateTime verrouillageAt;
}
