package tn.wtm.school.absence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.absence.enums.RaisonVerrouillage;
import tn.wtm.school.common.base.TenantEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "seance_appel",
        indexes = {
                // Une séance du planning est hebdomadaire : l'unicité porte sur le
                // couple séance + jour de cours, sinon un créneau ne pourrait porter
                // qu'un seul appel pour toute l'année scolaire.
                @Index(name = "UK_SEANCE_APPEL_TENANT_PLANNING_DATE", columnList = "tenant_id,seance_planning_id,date_seance", unique = true),
                @Index(name = "IDX_SEANCE_APPEL_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_SEANCE_APPEL_PLANNING", columnList = "seance_planning_id"),
                @Index(name = "IDX_SEANCE_APPEL_DATE", columnList = "tenant_id,date_seance"),
                @Index(name = "IDX_SEANCE_APPEL_GROUPE", columnList = "groupe_classe_id"),
                @Index(name = "IDX_SEANCE_APPEL_ENSEIGNANT", columnList = "enseignant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Table des séances d'appel")
public class SeanceAppel extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_seance_appel")
    @SequenceGenerator(name = "seq_seance_appel", sequenceName = "seq_seance_appel_id", allocationSize = 1)
    @Column(name = "id")
    @Comment("Clé primaire")
    private Long id;

    @Column(name = "seance_planning_id", nullable = false)
    @Comment("Référence à la séance dans le planning (pas de FK JPA)")
    private Long seancePlanningId;

    @Column(name = "enseignant_id", nullable = false)
    @Comment("Référence à l'enseignant dans organisation-module")
    private Long enseignantId;

    @Column(name = "groupe_classe_id", nullable = false)
    @Comment("Référence au groupe classe dans organisation-module")
    private Long groupeClasseId;

    @Column(name = "matiere_id")
    @Comment("Référence à la matière dans planning-module")
    private Long matiereId;

    @Column(name = "annee_academique", nullable = false, length = 20)
    @Comment("Ex: 2025-2026")
    private String anneeAcademique;

    @Column(name = "date_seance", nullable = false)
    @Comment("Jour de cours concerné — clé métier avec seance_planning_id (séance hebdomadaire)")
    private LocalDate dateSeance;

    @Column(name = "ouverture_at", nullable = false)
    @Comment("Date/heure réelle d'ouverture de l'appel par l'enseignant")
    private LocalDateTime ouvertureAt;

    @Column(name = "fermeture_at")
    @Comment("Date/heure de fermeture (null = séance en cours)")
    private LocalDateTime fermetureAt;

    @Column(name = "est_verrouille", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    @Comment("Indique si la séance est verrouillée")
    private Boolean estVerrouille = false;

    @Column(name = "verrouillage_at")
    @Comment("Date/heure du verrouillage")
    private LocalDateTime verrouillageAt;

    @Column(name = "verrouillage_par")
    @Comment("ID de l'utilisateur ayant verrouillé")
    private Long verrouillagePar;

    @Column(name = "raison_verrouillage", length = 40)
    @Enumerated(EnumType.STRING)
    @Comment("Raison du verrouillage")
    private RaisonVerrouillage raisonVerrouillage;

    @OneToMany(mappedBy = "seanceAppel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LigneAppel> lignesAppel = new ArrayList<>();
}
