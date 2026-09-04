package tn.wtm.school.planning.solver.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TimetableGenerationRequest {

    @NotNull(message = "L'identifiant de l'annee scolaire est obligatoire")
    private Long schoolYearId;

    private Long constraintProfileId;
}
