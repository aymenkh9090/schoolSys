package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.SessionType;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "types_seance_matiere",
        indexes = {
                @Index(name = "IDX_TYPE_SEANCE_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_TYPE_SEANCE_MATIERE_NIVEAU", columnList = "subject_level_id"),
                @Index(name = "UK_TYPE_SEANCE_TENANT_SL_TYPE", columnList = "tenant_id,subject_level_id,type", unique = true)
        }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
@EqualsAndHashCode(callSuper = true,onlyExplicitlyIncluded = true)
@Comment("Table des types de seance par matiere-niveau")
public class SubjectSessionType extends TenantEntity {


    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name= "id_type_seance_matiere")
    @Comment("Clé primaire du type seance matiére")
    private  Long idSubjectSessionType;



    @Column(name = "type", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    @Comment("Type de séance: COURS, TD, TP, EXAM, ATELIER, PROJET")
    private SessionType type;

    @Column(name = "duration", length = 50, nullable = false)
    @Comment("Durée de la seance en heures EX:1.0,1.5,2.0")
    private Double duration;

    @Column(name = "require_split",columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean requiresSplit = false;

    @Column(name = "groupe_count")
    @Comment("Nombre de groupe si la seance est divisé")
    private Integer groupCount;

    @Column(name = "est_active",columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean estActif = true ;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_level_id")
    private  SubjectLevel subjectLevel;

    @OneToMany(mappedBy = "subjectSessionType",cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private List<PatternDetail> patternDetails = new ArrayList<>();


    @OneToMany(mappedBy = "subjectSessionType",cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeachingAssignment> teachingAssignments = new ArrayList<>();














}
