package tn.wtm.school.absence.dto.reponse;

import lombok.*;
import tn.wtm.school.absence.enums.StatutPresence;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LigneAppelReponse {

    private Long id;
    private Long eleveId;
    private StatutPresence statut;
    private LocalDateTime arriveeAt;
    private Integer minutesRetard;
    private LocalDateTime exclusionAt;
    private String raisonExclusion;
    private Long excluPar;
    private Boolean estJustifie;
}
