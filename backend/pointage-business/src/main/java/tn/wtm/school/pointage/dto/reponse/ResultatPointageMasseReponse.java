package tn.wtm.school.pointage.dto.reponse;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResultatPointageMasseReponse {
    private int total;
    private int reussis;
    private int echoues;
    private List<PresencePersonnelReponse> resultats;
    private List<EchecPointageItem> echecs;

    public record EchecPointageItem(Long membrePersonnelId, String raison) {}
}
