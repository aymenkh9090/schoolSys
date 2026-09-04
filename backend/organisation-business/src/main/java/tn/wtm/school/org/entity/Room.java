package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.RoomType;


@Entity
@Table(name = "salles",
        indexes = {
                @Index(name = "UK_SALLE_TENANT_CODE", columnList = "tenant_id,code_salle", unique = true),
                @Index(name = "IDX_SALLE_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_SALLE_TYPE", columnList = "type_salle")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Comment("Table des salles")
public class Room extends TenantEntity {


    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_salle")
    @Comment("Clé primaire")
    private Long idSalle;

    @Comment("le code de la salle")
    @Column(name = "code_salle",length = 10, nullable = false)
    private String codeSalle;

    @Column(name = "type_salle", length = 50)
    @Enumerated(EnumType.STRING)
    @Comment("Type: NORMALE, LABSCIENCE, LABPHYSIQUE, LABINFORMATIQUE, SALLESPORT")
    private RoomType typeSalle;

    @Comment("capacite en nombre d'eleves")
    @Column(name = "capacite")
    private Integer capacite;

    @Comment("le bloc de la salle")
    @Column(name = "code_bloc",length = 10)
    private String codeBloc;

    @Comment("l'etage de la salle")
    @Column(name = "num_etage",length = 10)
    private String numEtage;

    @Comment("Equipements de la salle, séparés par des virgules (ex: VIDEOPROJECTEUR,TABLEAU_BLANC,CLIMATISATION)")
    @Column(name = "equipements", length = 255)
    private String equipements;

    @Comment("Salle disponible pour la planification ou non")
    @Column(name = "est_disponible", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean estDisponible = true;











}
