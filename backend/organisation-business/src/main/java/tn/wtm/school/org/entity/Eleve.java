package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;

@Entity
@Table(
        name = "eleves",
        indexes = {
                @Index(name = "UK_ELEVE_TENANT_CODE", columnList = "tenant_id,code_eleve", unique = true),
                @Index(name = "IDX_ELEVE_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_ELEVE_CLASSE", columnList = "classe_id"),
                @Index(name = "IDX_ELEVE_ACTIF", columnList = "tenant_id,est_actif")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Table des élèves")
public class Eleve extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_eleve")
    @Comment("Clé primaire de l'élève")
    private Long idEleve;

    @Comment("Code unique de l'élève dans l'établissement")
    @Column(name = "code_eleve", length = 20, nullable = false)
    private String codeEleve;

    @Comment("Nom de famille")
    @Column(name = "nom", length = 64, nullable = false)
    private String nom;

    @Comment("Prénom")
    @Column(name = "prenom", length = 64, nullable = false)
    private String prenom;

    @Comment("Numéro de pièce d'identité (CIN ou carte d'élève)")
    @Column(name = "num_identite", length = 20)
    private String numIdentite;

    @Comment("Email de l'élève ou du responsable")
    @Column(name = "email", length = 100)
    private String email;

    @Comment("Téléphone du responsable")
    @Column(name = "telephone", length = 20)
    private String telephone;

    @Comment("Classe affectée à l'élève")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_id")
    private ClassGroup classeGroup;

    @Comment("Élève actif dans l'établissement")
    @Column(name = "est_actif", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean estActif = true;
}
