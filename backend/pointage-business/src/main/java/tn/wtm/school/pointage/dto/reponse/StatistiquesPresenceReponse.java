package tn.wtm.school.pointage.dto.reponse;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StatistiquesPresenceReponse {
    private Long membrePersonnelId;
    private String periode;
    private int joursPresent;
    private int joursAbsent;
    private int joursEnRetard;
    private int joursEnConge;
    private double tauxPresence;
}
