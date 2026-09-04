package tn.wtm.school.absence.dto.reponse;

import lombok.*;
import tn.wtm.school.absence.enums.StatutPresence;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HistoriqueAppelReponse {

    private Long id;
    private Long ligneAppelId;
    private Long modifiePar;
    private LocalDateTime modifieAt;
    private StatutPresence statutPrecedent;
    private StatutPresence nouveauStatut;
    private String raisonModification;
    private String adresseIp;
}
