package tn.wtm.school.pointage.dto.requete;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointageMasseRequete {

    @NotEmpty
    private List<PointageRequete> pointages;
}
