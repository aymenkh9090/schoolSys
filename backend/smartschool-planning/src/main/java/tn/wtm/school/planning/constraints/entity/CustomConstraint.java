package tn.wtm.school.planning.constraints.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.enums.ConstraintSource;

/**
 * Contrainte réellement personnalisée : une règle qui n'existe pas au catalogue,
 * exprimée en DSL et rattachée à un profil de contraintes d'un établissement.
 *
 * <p>Le contenu métier tient dans {@link #dslJson} : une <b>description</b>
 * déclarative, jamais du code. Rien de ce qui est stocké ici n'est compilé,
 * chargé ni exécuté en tant que programme — le moteur ne fait qu'y lire des noms
 * de champs et d'opérateurs, tous deux vérifiés contre un catalogue figé. C'est
 * la raison pour laquelle stocker du Java (ou n'importe quel script) en base
 * serait un choix radicalement différent : il faudrait alors faire confiance au
 * contenu de la base pour la sécurité de la JVM, alors qu'ici la base ne contient
 * que des données inertes.</p>
 *
 * <p>{@link #severity} et {@link #weight} sont dupliqués depuis le DSL. C'est une
 * dénormalisation assumée : elle permet de lister, filtrer et trier les règles
 * sans désérialiser le JSON de chacune, et elle est réécrite à chaque
 * enregistrement à partir du DSL, qui reste la source de vérité.</p>
 */
@Entity
@Table(
        name = "custom_constraint",
        indexes = {
                @Index(name = "IDX_CUSTOM_CONSTRAINT_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_CUSTOM_CONSTRAINT_PROFILE", columnList = "constraint_profile_id"),
                @Index(name = "UK_CUSTOM_CONSTRAINT_TENANT_CODE",
                        columnList = "tenant_id,code", unique = true)
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class CustomConstraint extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_seq")
    @SequenceGenerator(name = "planning_seq", sequenceName = "SEQ_PLANNING", allocationSize = 1)
    @Column(name = "id_custom_constraint")
    private Long idCustomConstraint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "constraint_profile_id", nullable = false)
    private ConstraintProfile profile;

    /** Code technique, unique dans l'établissement. Sert d'identifiant dans le score. */
    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** La règle elle-même, au format {@code ConstraintDsl}. */
    @Column(name = "dsl_json", nullable = false, columnDefinition = "TEXT")
    private String dslJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DslScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DslSeverity severity;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConstraintSource source = ConstraintSource.MANUAL;

    /**
     * Phrase d'origine quand la règle vient de l'assistant.
     * Conservée pour l'audit : elle permet de comparer ce que l'utilisateur a
     * demandé et ce que le moteur applique réellement.
     */
    @Column(name = "natural_language_request", columnDefinition = "TEXT")
    private String naturalLanguageRequest;

    /** Résumé français produit par le compilateur, figé au moment de l'enregistrement. */
    @Column(name = "summary", length = 500)
    private String summary;
}
