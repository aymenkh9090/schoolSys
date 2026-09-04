package tn.wtm.school.absence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.common.base.TenantEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "ligne_appel",
        indexes = {
                @Index(name = "UK_LIGNE_APPEL_SEANCE_ELEVE", columnList = "tenant_id,seance_appel_id,eleve_id", unique = true),
                @Index(name = "IDX_LIGNE_APPEL_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_LIGNE_APPEL_SEANCE", columnList = "seance_appel_id"),
                @Index(name = "IDX_LIGNE_APPEL_ELEVE", columnList = "eleve_id"),
                @Index(name = "IDX_LIGNE_APPEL_STATUT", columnList = "statut")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Table des lignes d'appel par élève")
public class LigneAppel extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ligne_appel")
    @SequenceGenerator(name = "seq_ligne_appel", sequenceName = "seq_ligne_appel_id", allocationSize = 1)
    @Column(name = "id")
    @Comment("Clé primaire")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seance_appel_id", nullable = false)
    @Comment("Séance d'appel associée")
    private SeanceAppel seanceAppel;

    @Column(name = "eleve_id", nullable = false)
    @Comment("Référence à l'élève dans organisation-module")
    private Long eleveId;

    @Column(name = "statut", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Comment("Statut de présence de l'élève")
    private StatutPresence statut = StatutPresence.PRESENT;

    @Column(name = "arrivee_at")
    @Comment("Heure réelle d'arrivée si RETARD")
    private LocalDateTime arriveeAt;

    @Column(name = "minutes_retard")
    @Comment("Minutes de retard calculées automatiquement")
    private Integer minutesRetard;

    @Column(name = "exclusion_at")
    @Comment("Heure d'exclusion si EXCLU")
    private LocalDateTime exclusionAt;

    @Column(name = "raison_exclusion", length = 500)
    @Comment("Raison de l'exclusion (obligatoire si statut = EXCLU)")
    private String raisonExclusion;

    @Column(name = "exclu_par")
    @Comment("ID de l'enseignant ayant exclu")
    private Long excluPar;

    @Column(name = "est_justifie", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    @Comment("Indique si l'absence est justifiée")
    private Boolean estJustifie = false;

    @OneToMany(mappedBy = "ligneAppel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<JustificatifAbsence> justificatifs = new ArrayList<>();

    @OneToMany(mappedBy = "ligneAppel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<HistoriqueAppel> historiques = new ArrayList<>();
}
