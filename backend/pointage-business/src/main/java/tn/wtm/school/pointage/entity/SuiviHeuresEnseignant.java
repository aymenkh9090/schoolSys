package tn.wtm.school.pointage.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;

@Entity
@Table(
    name = "suivi_heures_enseignant",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_suivi_heures_tenant_enseignant_semaine_annee",
            columnNames = {"tenant_id", "enseignant_id", "numero_semaine", "annee_academique"}
        )
    },
    indexes = {
        @Index(name = "idx_suivi_heures_tenant", columnList = "tenant_id"),
        @Index(name = "idx_suivi_heures_enseignant", columnList = "tenant_id,enseignant_id"),
        @Index(name = "idx_suivi_heures_annee", columnList = "tenant_id,annee_academique")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SuiviHeuresEnseignant extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_suivi_heures_enseignant")
    @SequenceGenerator(name = "seq_suivi_heures_enseignant", sequenceName = "seq_suivi_heures_enseignant_id", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "enseignant_id", nullable = false)
    private Long enseignantId;

    @Column(name = "numero_semaine", nullable = false)
    private Integer numeroSemaine;

    @Column(name = "annee_academique", nullable = false, length = 20)
    private String anneeAcademique;

    @Column(name = "heures_prevues", nullable = false)
    private Double heuresPrevues;

    @Column(name = "heures_realisees", nullable = false)
    private Double heuresRealisees;

    @Column(name = "heures_manquees")
    private Double heuresManquees;

    @Column(name = "taux_presence")
    private Double tauxPresence;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
