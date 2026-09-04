package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Collate;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(
        name = "enseignants",
        indexes = {
                @Index(name = "IDX_ENSEIGNANT_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_ENSEIGNANT_IDENTITE" , columnList = "num_identite"),
                @Index(name = "UK_ENSEIGNANT_TENANT_CODE", columnList = "tenant_id,code_enseignant", unique = true),
                @Index(name = "UK_ENSEIGNANT_TENANT_IDENTITE", columnList = "tenant_id,num_identite", unique = true),
                @Index(name = "UK_ENSEIGNANT_TENANT_EMAIL", columnList = "tenant_id,email", unique = true),
                @Index(name = "IDX_ENSEIGNANT_ACTIF", columnList = "tenant_id,est_en_poste")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false,onlyExplicitlyIncluded = true)
@Comment("Table des enseignants")
public class Teacher extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_enseignant")
    @Comment("Clé primaire de l'enseignant")
    private Long idEnseignant;

    @Comment("Le code de l'enseignant")
    @Column(name = "code_enseignant",length = 15,nullable = false)
    private String codeEnseignant;

    @Comment("Le NUM CIN de l'enseignant")
    @Column(name = "num_identite",length = 15,nullable = false)
    private String numIdentite;

    @Comment("Le nom de l'enseignant")
    @Column(name = "nom",length = 64,nullable = false)
    private String nom;

    @Comment("Le prenom de l'enseignant")
    @Column(name = "prenom",length = 64,nullable = false)
    private String prenom;

    @Column(name = "email", length = 100)
    @Comment("Email professionnel")
    private String email;

    @Column(name = "telephone", length = 20)
    @Comment("Téléphone")
    private String telephone;

    @Comment("le nombre maximale des heures a travailler par semaine par l'enseignant")
    @Column(name = "max_heures_semaine")
    private Integer maxHeuresSemaine;

    @Comment("le nombre maximale des heures a travailler par jour par l'enseignant")
    @Column(name = "max_heures_jour")
    private Integer maxHeuresJour;

    @Comment("le nombre minimale des heures a travailler par jour par l'enseignant")
    @Column(name = "min_heures_jour")
    private Integer minHeuresJour;

    @Comment("true = enseignant en poste dans l'etablissement / false = muté ailleurs")
    @Column(name = "est_en_poste" , columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean estEnPoste = true;

    @Column(name = "photo", length = 500)
    @Comment(" photo de l'enseignant")
    private String photo;

    @Column(name = "specialite", length = 50)
    @Comment("Spécialité / discipline du diplôme de l'enseignant (ex: MATHEMATIQUES, SCIENCES_PHYSIQUES)")
    private String specialite;


    @OneToMany(mappedBy = "teacher",cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeachingAssignment> teachingAssignments = new ArrayList<>();


















}
