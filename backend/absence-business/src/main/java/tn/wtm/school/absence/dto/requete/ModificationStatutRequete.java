package tn.wtm.school.absence.dto.requete;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.absence.enums.StatutPresence;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModificationStatutRequete {

    @NotNull(message = "Le statut est obligatoire")
    private StatutPresence statut;

    private String raisonExclusion;

    private LocalDateTime arriveeAt;

    private Long modifiePar;

    private String adresseIp;
}
