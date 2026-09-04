package tn.wtm.school.absence.dto.reponse;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatistiquesAbsenceReponse {

    private Long groupeClasseId;
    private String tenantId;
    private String periode;
    private LocalDate debut;
    private LocalDate fin;
    private Long totalSeances;
    private Long totalAbsences;
    private Long totalJustifiees;
    private Long totalNonJustifiees;
    private Long totalRetards;
    private Long totalExclusions;
    private Double tauxAbsenteisme;
}
