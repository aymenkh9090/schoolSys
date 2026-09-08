package tn.wtm.school.pointage.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.pointage.enums.Periode;
import tn.wtm.school.pointage.enums.StatutPresencePersonnel;
import tn.wtm.school.pointage.enums.TypePersonnel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
    name = "presence_personnel",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_presence_personnel_tenant_membre_date_periode",
            columnNames = {"tenant_id", "membre_personnel_id", "date_pointage", "periode"}
        )
    },
    indexes = {
        @Index(name = "idx_presence_personnel_tenant", columnList = "tenant_id"),
        @Index(name = "idx_presence_personnel_date", columnList = "tenant_id,date_pointage"),
        @Index(name = "idx_presence_personnel_membre", columnList = "tenant_id,membre_personnel_id"),
        @Index(name = "idx_presence_personnel_statut", columnList = "tenant_id,statut")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PresencePersonnel extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_presence_personnel")
    @SequenceGenerator(name = "seq_presence_personnel", sequenceName = "seq_presence_personnel_id", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "membre_personnel_id", nullable = false)
    private Long membrePersonnelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_personnel", nullable = false, length = 50)
    private TypePersonnel typePersonnel;

    @Column(name = "date_pointage", nullable = false)
    private LocalDate datePointage;

    @Enumerated(EnumType.STRING)
    @Column(name = "periode", nullable = false, length = 20)
    private Periode periode;

    @Column(name = "heure_arrivee")
    private LocalTime heureArrivee;

    @Column(name = "heure_depart")
    private LocalTime heureDepart;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 50)
    private StatutPresencePersonnel statut;

    @Column(name = "minutes_retard")
    private Integer minutesRetard;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "saisi_par", nullable = false)
    private String saisiPar;

    @Column(name = "saisi_a", nullable = false)
    private LocalDateTime saisiA;
}
