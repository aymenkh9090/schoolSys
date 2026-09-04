package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "niveaux_matieres",
        indexes = {
                @Index(name = "UK_NIVEAU_MATIERE", columnList = "level_id,subject_id", unique = true),
                @Index(name = "IDX_NIVEAU_MATIERE_NIVEAU", columnList = "level_id"),
                @Index(name = "IDX_NIVEAU_MATIERE_MATIERE", columnList = "subject_id")
        }
)
@EqualsAndHashCode(callSuper = false,onlyExplicitlyIncluded = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Comment("Table de liaison Niveau x Matiére")
public class SubjectLevel extends TenantEntity {



    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_niveau_matiere")
    @Comment("Cle Primaire")
    private Long idNiveauMatiere;

    @Column(name = "heures_semaine")
    @Comment("Nombre d'heures par semaines")
    private Double heuresSemaine;

    @Column(name = "description")
    @Comment("Description")
    private String description;

    @Column(name = "est_obligatoire",columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Comment("Matiere obligatoire ou optionnelle")
    @Builder.Default
    private Boolean estObligatoire=true;

    @Column(name = "coefficient")
    @Comment("coefficient matieres pour ce niveau")
    private Double coefficient;

    @Column(name = "max_heures_consecutives")
    @Comment("max heures consecutives")
    private Integer maxHeuresConsecutives;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private Level level;

    @OneToMany(mappedBy = "subjectLevel",cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeachingAssignment> teachingAssignments = new ArrayList<>();


    @OneToMany(mappedBy = "subjectLevel",cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubjectSessionType> subjectSessionTypes = new ArrayList<>();

    @OneToMany(mappedBy = "subjectLevel",cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Pattern> patterns = new LinkedHashSet<>();

















}
