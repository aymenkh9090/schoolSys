package tn.wtm.school.absence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.absence.enums.StatutPresence;
import tn.wtm.school.common.base.TenantEntity;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "historique_appel",
        indexes = {
                @Index(name = "IDX_HISTORIQUE_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_HISTORIQUE_LIGNE", columnList = "ligne_appel_id"),
                @Index(name = "IDX_HISTORIQUE_MODIFIE_PAR", columnList = "modifie_par")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Audit immuable des modifications de statut d'appel")
public class HistoriqueAppel extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_historique_appel")
    @SequenceGenerator(name = "seq_historique_appel", sequenceName = "seq_historique_appel_id", allocationSize = 1)
    @Column(name = "id")
    @Comment("Clé primaire")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ligne_appel_id", nullable = false)
    @Comment("Ligne d'appel concernée")
    private LigneAppel ligneAppel;

    @Column(name = "modifie_par", nullable = false)
    @Comment("ID de l'utilisateur ayant fait la modification")
    private Long modifiePar;

    @Column(name = "modifie_at", nullable = false)
    @Comment("Date/heure de la modification")
    private LocalDateTime modifieAt;

    @Column(name = "statut_precedent", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Comment("Statut avant modification")
    private StatutPresence statutPrecedent;

    @Column(name = "nouveau_statut", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Comment("Nouveau statut après modification")
    private StatutPresence nouveauStatut;

    @Column(name = "raison_modification", length = 500)
    @Comment("Raison de la modification")
    private String raisonModification;

    @Column(name = "adresse_ip", length = 45)
    @Comment("Adresse IP de l'acteur")
    private String adresseIp;
}
