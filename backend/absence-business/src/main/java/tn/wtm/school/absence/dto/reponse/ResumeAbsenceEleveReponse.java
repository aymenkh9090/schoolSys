package tn.wtm.school.absence.dto.reponse;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResumeAbsenceEleveReponse {

    private Long eleveId;
    private String anneeAcademique;
    private Long totalAbsences;
    private Long absencesJustifiees;
    private Long absencesNonJustifiees;
    private Long totalRetards;
    private Long totalExclusions;
    private String periode;
}
