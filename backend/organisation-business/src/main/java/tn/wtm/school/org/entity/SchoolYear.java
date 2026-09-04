package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(
        name = "annees_scolaires",
        indexes = {
                @Index(name = "IDX_ANNEE_TENANT",columnList = "tenant_id"),
                @Index(name = "IDX_ANNEE_ACTIVE",columnList = "tenant_id,est_active"),
                @Index(name = "UK_ANNEE_TENANT_NOM", columnList = "tenant_id,nom", unique = true)
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper=false,onlyExplicitlyIncluded=true)
@Comment("Table des années scolaires")
public class SchoolYear extends TenantEntity {


    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_annee")
    @Comment("Clé primaire de l'année scolaire")
    private Long idAnnee;

    @Column(name = "nom",length = 50,nullable = false)
    @Comment("EX: 2025-2026")
    private String nom;

    @Column(name = "date_debut",nullable = false)
    @Comment("Date de debut de l'année")
    private LocalDate dateDebut;

    @Column(name = "date_fin",nullable = false)
    @Comment("Date de fin de l'année")
    private LocalDate dateFin;

    @Column(name = "est_active",columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Comment("Année active par defaut")
    @Builder.Default
    private Boolean estActive = true;

    @Column(name = "est_courante", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Comment("Est l'année en cours")
    @Builder.Default
    private Boolean estCourante = false;

    // ========== RELATIONS ==========

    @OneToMany(mappedBy = "schoolYear", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassGroup> classGroups = new ArrayList<>();

    @OneToMany(mappedBy = "schoolYear", fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeachingAssignment> teachingAssignments = new ArrayList<>();











}
