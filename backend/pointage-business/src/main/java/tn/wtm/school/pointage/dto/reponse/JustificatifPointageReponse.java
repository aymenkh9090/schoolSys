package tn.wtm.school.pointage.dto.reponse;

import lombok.*;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutJustificatifPointage;
import tn.wtm.school.pointage.enums.TypeJustificatifPointage;
import tn.wtm.school.pointage.enums.TypePersonnel;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JustificatifPointageReponse {
    private Long id;
    private Long membrePersonnelId;
    private Long presencePersonnelId;
    /** Contexte du pointage justifié — évite au client de recharger la présence. */
    private String nomMembre;
    private TypePersonnel typePersonnel;
    private LocalDate datePointage;
    private Periode periode;
    private TypeJustificatifPointage typeDocument;
    private String description;
    private String cheminDocument;
    private String commentaireAdmin;
    private StatutJustificatifPointage statut;
    private LocalDateTime soumisA;
    private String traitePar;
    private LocalDateTime traiteA;
    private LocalDateTime createdAt;
}
