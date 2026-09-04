package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.PatternType;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(
        name = "patterns",
        indexes = {
                @Index(name = "IDX_PATTERN_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_PATTERN_SUBJECT_LEVEL", columnList = "subject_level_id"),
                @Index(name = "IDX_PATTERN_SCHOOL_YEAR", columnList = "school_year_id"),
                @Index(name = "UK_PATTERN_TENANT_SL_YEAR_NAME",
                        columnList = "tenant_id,subject_level_id,school_year_id,name", unique = true)
        }

)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Repartition des heures par matiere (ex: 5h = 2+2+1) ")
public class Pattern extends TenantEntity {


    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_pattern")
    @Comment("Clé primaire du pattern pédagogique")
    private Long idPattern;

    @Column(name = "name", nullable = false, length = 1000)
    @Comment("Nom patter EX : Pattern Mathematique 7eme")
    private String name;

    @Column(name = "total_hours", nullable = false)
    @Comment("total des heures pour matiere ex:5.0")
    private Double totalHours;

    @Column(name = "session_count", nullable = false)
    @Comment("nombre se seance ex :3")
    private Integer sessionCount;

    @Column(name = "repartition", length = 50)
    @Comment("EX : 2+2+1")
    private String repartition;

    @Column(name = "pattern_type", length = 30)
    @Enumerated(EnumType.STRING)
    @Comment("Stratégie de répétition hebdomadaire: WEEKLY_IDENTICAL, ALTERNATING, BIWEEKLY")
    private PatternType patternType;

    @Column(name = "active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Comment("Pattern actif = pris en compte par le solveur. Désactivé = matière non enseignée dans cet établissement")
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_year_id")
    @Comment("Null = pattern par défaut (toutes années). Non-null = pattern spécifique à une année scolaire.")
    private SchoolYear schoolYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_level_id")
    private SubjectLevel subjectLevel;

    @OneToMany(mappedBy = "pattern", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PatternDetail> patternDetails = new ArrayList<>();


}
