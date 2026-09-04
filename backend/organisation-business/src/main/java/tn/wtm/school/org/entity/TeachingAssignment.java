package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;



@Entity
@Table(
        name = "affectations_enseignants",
        indexes = {
                @Index(name = "IDX_AFFECTATION_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_AFFECTATION_ANNEE", columnList = "school_year_id"),
                @Index(name = "IDX_AFFECTATION_ENSEIGNANT", columnList = "teacher_id"),
                @Index(name = "IDX_AFFECTATION_CLASSE", columnList = "class_group_id"),
                @Index(name = "IDX_AFFECTATION_MATIERE_NIVEAU", columnList = "subject_level_id"),
                @Index(name = "IDX_AFFECTATION_TYPE_SEANCE", columnList = "subject_session_type_id"),
                @Index(name = "UK_AFFECTATION_UNIQUE", columnList = "tenant_id,school_year_id,teacher_id,class_group_id,subject_level_id,subject_session_type_id", unique = true)
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Table des affectations enseignants")
public class TeachingAssignment extends TenantEntity {


    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_affectation")
    @Comment("Clé primaire de l'affectation enseignant")
    private Long idTeachingAssignment;


    @Column(name = "priority")
    @Comment("Priorité de l'affectation pour la génération")
    private Integer priority;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    @Comment("Indique si l'affectation est active")
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_year_id",nullable = false)
    private SchoolYear schoolYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id",nullable = false)
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="class_group_id",nullable = false)
    private ClassGroup classGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_level_id",nullable = false)
    private SubjectLevel subjectLevel;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_session_type_id",nullable = false)
    private SubjectSessionType subjectSessionType;





















}
