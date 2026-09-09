package tn.wtm.school.pointage.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.pointage.enums.StatutJustificatifPointage;
import tn.wtm.school.pointage.enums.TypeJustificatifPointage;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "justificatif_pointage",
    indexes = {
        @Index(name = "idx_justificatif_pointage_tenant", columnList = "tenant_id"),
        @Index(name = "idx_justificatif_pointage_presence", columnList = "tenant_id,presence_personnel_id"),
        @Index(name = "idx_justificatif_pointage_statut", columnList = "tenant_id,statut")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JustificatifPointage extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_justificatif_pointage")
    @SequenceGenerator(name = "seq_justificatif_pointage", sequenceName = "seq_justificatif_pointage_id", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "membre_personnel_id", nullable = false)
    private Long membrePersonnelId;

    @Column(name = "presence_personnel_id", nullable = false)
    private Long presencePersonnelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, length = 50)
    private TypeJustificatifPointage typeDocument;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "chemin_document")
    private String cheminDocument;

    @Column(name = "commentaire_admin", columnDefinition = "TEXT")
    private String commentaireAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 50)
    @Builder.Default
    private StatutJustificatifPointage statut = StatutJustificatifPointage.EN_ATTENTE;

    @Column(name = "soumis_a", nullable = false)
    private LocalDateTime soumisA;

    @Column(name = "traite_par")
    private String traitePar;

    @Column(name = "traite_a")
    private LocalDateTime traiteA;
}
