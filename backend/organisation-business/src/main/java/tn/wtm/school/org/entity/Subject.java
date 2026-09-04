package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.RoomType;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matieres",
        indexes = {
                @Index(name = "UK_MATIERE_TENANT_CODE", columnList = "tenant_id,code_matiere", unique = true),
                @Index(name = "IDX_MATIERE_TENANT", columnList = "tenant_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Comment("Table des matières enseignées")
public class Subject extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_matiere")
    @Comment("Clé primaire de la matiere")
    private Long idMatiere;

    @Column(name = "code_matiere",length = 10,nullable = false)
    @Comment("Code:MATH,ARABE,PHYS...")
    private String codeMatiere;

    @Column(name = "lib_matiere", length = 64)
    @Comment("Libellé de la matiere")
    private String libMatiere;

    @Column(name = "description", length = 500)
    @Comment("Description de la matière")
    private String description;

    @Column(name = "necessite_lab", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Comment("Nécessite un laboratoire")
    @Builder.Default
    private Boolean necessiteLab=false;

    @Column(name = "necessite_sport", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Comment("Nécessite une salle de sport")
    @Builder.Default
    private Boolean necessiteSport=false;

    @Column(name = "type_salle_requise", length = 30)
    @Enumerated(EnumType.STRING)
    @Comment("Type de salle requise par défaut: NORMALE, LABPHYSIQUE, LABSCIENCE, LABINFORMATIQUE, SALLESPORT, SALLEDESSIN, SALLEMUSIQUE, AMPHI, BIBLIOTHEQUE")
    @Builder.Default
    private RoomType typeSalleRequise = RoomType.NORMALE;


    @Column(name = "couleur", length = 7)
    @Comment("Couleur EX: MATH avec coleur rouge")
    private String couleur;

    @Column(name = "abreviation")
    @Comment("Abreviation courte")
    private String abreviation;

    @Comment("est une matiere principale ou non ?")
    @Column(name = "est_principale", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean estPrincipale = false;

    @Comment("true = matiére enseignée dans l'établissement / false = non enseignée")
    @Column(name = "est_enseignee", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean estEnseignee  = true;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubjectLevel> subjectLevels = new ArrayList<>();







}
