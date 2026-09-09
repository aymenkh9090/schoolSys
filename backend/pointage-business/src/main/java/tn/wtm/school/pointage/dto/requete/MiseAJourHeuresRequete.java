package tn.wtm.school.pointage.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MiseAJourHeuresRequete {

    @NotNull
    private Long enseignantId;

    @NotNull
    private Integer numeroSemaine;

    @NotBlank
    private String anneeAcademique;

    @NotNull
    private Double heuresPrevues;

    @NotNull
    private Double heuresRealisees;

    private String notes;
}
