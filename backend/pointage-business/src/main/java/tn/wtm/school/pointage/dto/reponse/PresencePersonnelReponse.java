package tn.wtm.school.pointage.dto.reponse;

import lombok.*;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PresencePersonnelReponse {
    private Long id;
    private Long membrePersonnelId;
    /** Nom complet du membre, résolu via {@code PortMembrePersonnel}. Null si introuvable. */
    private String nomMembre;
    private TypePersonnel typePersonnel;
    private LocalDate datePointage;
    private Periode periode;
    private LocalTime heureArrivee;
    private LocalTime heureDepart;
    private StatutPresencePersonnel statut;
    private Integer minutesRetard;
    private String note;
    private String saisiPar;
    private LocalDateTime saisiA;
    private LocalDateTime createdAt;
    private JustificatifPointageReponse justificatif;
}
