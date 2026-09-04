package tn.wtm.school.absence.dto.reponse;

import lombok.*;
import tn.wtm.school.absence.enums.RaisonVerrouillage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppelReponse {

    private Long id;
    private Long seancePlanningId;
    private Long enseignantId;
    private Long groupeClasseId;
    private Long matiereId;
    private String anneeAcademique;
    private LocalDate dateSeance;
    private LocalDateTime ouvertureAt;
    private LocalDateTime fermetureAt;
    private Boolean estVerrouille;
    private LocalDateTime verrouillageAt;
    private RaisonVerrouillage raisonVerrouillage;
    private List<LigneAppelReponse> lignesAppel;
}
