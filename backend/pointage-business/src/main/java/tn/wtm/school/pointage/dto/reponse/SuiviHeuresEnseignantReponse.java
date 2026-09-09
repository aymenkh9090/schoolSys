package tn.wtm.school.pointage.dto.reponse;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SuiviHeuresEnseignantReponse {
    private Long id;
    private Long enseignantId;
    private Integer numeroSemaine;
    private String anneeAcademique;
    private Double heuresPrevues;
    private Double heuresRealisees;
    private Double heuresManquees;
    private Double tauxPresence;
    private String notes;
    private LocalDateTime createdAt;
}
