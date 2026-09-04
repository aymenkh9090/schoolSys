package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "niveaux",
        indexes = {
                @Index(name = "UK_NIVEAU_TENANT_CODE", columnList = "tenant_id,code", unique = true),
                @Index(name = "IDX_NIVEAU_TENANT", columnList = "tenant_id")
        }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false,onlyExplicitlyIncluded = true)
@Comment("Table des niveaux scolaires (7éme,8éme,1er...)")
public class Level extends TenantEntity {


    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_niveau")
    @Comment("Clé Primaire du niveau")
    private Long idNiveau;

    @Column(name = "level_nom", length = 100,nullable = false)
    @Comment("EX: 7éme Année,1ere secondaire")
    private String nom;

    @Column(name = "code", length = 100,nullable = false)
    @Comment("EX: 7A,1S")
    private String code;

    @Column(name = "description_level", length = 100,nullable = false)
    @Comment("Description du niveau")
    private String description;

    @Column(name = "est_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Comment("Niveau Actif ou nom")
    @Builder.Default
    private Boolean estActif = true;


    @OneToMany(mappedBy = "level", cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassGroup> classGroups = new ArrayList<>();

    @OneToMany(mappedBy = "level", cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubjectLevel> subjectLevels = new ArrayList<>() ;















}
