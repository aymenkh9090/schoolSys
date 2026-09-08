package tn.wtm.school.pointage.dto.requete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.pointage.enums.TypeJustificatifPointage;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SoumissionJustificatifPointageRequete {

    @NotNull
    private Long presencePersonnelId;

    @NotNull
    private TypeJustificatifPointage typeDocument;

    @NotBlank
    private String description;

    private String cheminDocument;
    private String commentaireAdmin;
}
