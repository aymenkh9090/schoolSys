package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.Specialite;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(
        name = "classes",
        indexes = {
                @Index(name = "UK_CLASSE_TENANT_CODE", columnList = "tenant_id,code", unique = true),
                @Index(name = "IDX_CLASSE_NIVEAU", columnList = "school_year_id"),
                @Index(name = "IDX_CLASSE_PARENT", columnList = "level_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false,onlyExplicitlyIncluded = true)
@Comment("Table des classes/groups d'élèves")
public class ClassGroup extends TenantEntity {


    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_classe")
    @Comment("Clé primaire de la classe")
    private Long idClasse;




    @Comment("le code de la classe est saisissable exemple: 7B1(7eme B un), 7B2(7eme B deux ), 9B10( 9eme B dix)")
    @Column (name ="code" ,  length = 15 , nullable = false)
    private String code;


    @Comment("Spécialité de la classe: TCOM, SCIE, MATH, LETR, TECH, ECON, INFO")
    @Column(name = "code_specialite", length = 10, nullable = false)
    @Enumerated(EnumType.STRING)
    private Specialite codeSpecialite;

    @Column(name = "nbr_eleve")
    @Comment("Nombre d'eleves")
    private Integer nbEleve;

    @Column(name = "est_actif",columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Comment("Classe actif")
    @Builder.Default
    private Boolean estActif=true;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_year_id")
    private SchoolYear schoolYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private Level level;

    @OneToMany(mappedBy = "classGroup",cascade =  CascadeType.ALL , fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeachingAssignment> teachingAssignments = new ArrayList<>();






















}
