package tn.wtm.school.pointage.dto.requete;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TraitementJustificatifPointageRequete {

    @NotNull
    private Boolean approuve;

    private String motifRejet;
    private String traitePar;
}
