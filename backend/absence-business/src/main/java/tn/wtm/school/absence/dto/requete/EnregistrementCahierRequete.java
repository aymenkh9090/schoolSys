package tn.wtm.school.absence.dto.requete;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnregistrementCahierRequete {

    private String sujet;

    private String chapitre;

    private String activites;

    private String remarques;

    private String travailDemande;

    private LocalDate dateEcheance;
}
