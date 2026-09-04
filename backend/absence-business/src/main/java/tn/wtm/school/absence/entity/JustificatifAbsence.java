package tn.wtm.school.absence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.absence.enums.StatutJustificatif;
import tn.wtm.school.absence.enums.TypeJustificatif;
import tn.wtm.school.common.base.TenantEntity;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "justificatif_absence",
        indexes = {
                @Index(name = "IDX_JUSTIFICATIF_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_JUSTIFICATIF_ELEVE", columnList = "eleve_id"),
                @Index(name = "IDX_JUSTIFICATIF_STATUT", columnList = "statut"),
                @Index(name = "IDX_JUSTIFICATIF_LIGNE", columnList = "ligne_appel_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Table des justificatifs d'absence")
public class JustificatifAbsence extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_justificatif_absence")
    @SequenceGenerator(name = "seq_justificatif_absence", sequenceName = "seq_justificatif_absence_id", allocationSize = 1)
    @Column(name = "id")
    @Comment("Clé primaire")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ligne_appel_id", nullable = false)
    @Comment("Ligne d'appel associée")
    private LigneAppel ligneAppel;

    @Column(name = "eleve_id", nullable = false)
    @Comment("ID de l'élève (dénormalisé)")
    private Long eleveId;

    @Column(name = "soumis_at", nullable = false)
    @Comment("Date/heure de soumission du justificatif")
    private LocalDateTime soumisAt;

    @Column(name = "soumis_par_id", nullable = false)
    @Comment("ID de l'utilisateur ayant soumis")
    private Long soumisParId;

    @Column(name = "type_document", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Comment("Type de justificatif")
    private TypeJustificatif typeDocument;

    @Column(name = "reference_document", length = 200)
    @Comment("Référence ou numéro du document")
    private String referenceDocument;

    @Column(name = "statut", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Comment("Statut du justificatif")
    private StatutJustificatif statut = StatutJustificatif.EN_ATTENTE;

    @Column(name = "traite_at")
    @Comment("Date/heure de traitement")
    private LocalDateTime traiteAt;

    @Column(name = "traite_par_id")
    @Comment("ID de l'admin ayant traité")
    private Long traiteParId;

    @Column(name = "notes_admin", length = 1000)
    @Comment("Notes de l'administrateur")
    private String notesAdmin;
}
