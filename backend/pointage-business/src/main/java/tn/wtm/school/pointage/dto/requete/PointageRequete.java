package tn.wtm.school.pointage.dto.requete;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointageRequete {

    @NotNull
    private Long membrePersonnelId;

    @NotNull
    private TypePersonnel typePersonnel;

    @NotNull
    private LocalDate datePointage;

    @NotNull
    private Periode periode;

    @NotNull
    private StatutPresencePersonnel statut;

    private LocalTime heureArrivee;
    private LocalTime heureDepart;
    private Integer minutesRetard;
    private String note;
}
