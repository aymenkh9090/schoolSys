package tn.wtm.school.absence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "cahier_seance",
        indexes = {
                @Index(name = "UK_CAHIER_SEANCE_APPEL", columnList = "tenant_id,seance_appel_id", unique = true),
                @Index(name = "IDX_CAHIER_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_CAHIER_SEANCE", columnList = "seance_appel_id"),
                @Index(name = "IDX_CAHIER_ENSEIGNANT", columnList = "enseignant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Cahier de séance (relation 1-1 avec SeanceAppel)")
public class CahierSeance extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_cahier_seance")
    @SequenceGenerator(name = "seq_cahier_seance", sequenceName = "seq_cahier_seance_id", allocationSize = 1)
    @Column(name = "id")
    @Comment("Clé primaire")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seance_appel_id", nullable = false, unique = true)
    @Comment("Séance d'appel associée")
    private SeanceAppel seanceAppel;

    @Column(name = "enseignant_id", nullable = false)
    @Comment("ID de l'enseignant")
    private Long enseignantId;

    @Column(name = "sujet", length = 500)
    @Comment("Sujet de la séance")
    private String sujet;

    @Column(name = "chapitre", length = 200)
    @Comment("Chapitre traité")
    private String chapitre;

    @Column(name = "activites", columnDefinition = "TEXT")
    @Comment("Activités réalisées pendant la séance")
    private String activites;

    @Column(name = "remarques", columnDefinition = "TEXT")
    @Comment("Remarques de l'enseignant")
    private String remarques;

    @Column(name = "travail_demande", columnDefinition = "TEXT")
    @Comment("Travail demandé aux élèves")
    private String travailDemande;

    @Column(name = "date_echeance")
    @Comment("Date limite du travail demandé")
    private LocalDate dateEcheance;

    @Column(name = "est_verrouille", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    @Comment("Indique si le cahier est verrouillé")
    private Boolean estVerrouille = false;

    @Column(name = "verrouillage_at")
    @Comment("Date/heure du verrouillage")
    private LocalDateTime verrouillageAt;
}
