package tn.wtm.school.pointage.dto.reponse;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RapportJournalierReponse {
    private LocalDate date;
    private int totalPresents;
    private int totalAbsents;
    private int totalEnRetard;
    private int totalEnConge;
    private int totalAbsencesJustifiees;
    private int totalAbsencesNonJustifiees;
    private List<PresencePersonnelReponse> enregistrements;
}
