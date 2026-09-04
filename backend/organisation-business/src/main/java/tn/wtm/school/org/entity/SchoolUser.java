package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.UserRole;

@Entity
@Table(
        name = "school_user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_school_user_tenant_email",
                        columnNames = {"tenant_id", "email"}
                )
        },
        indexes = {
                @Index(name = "idx_school_user_tenant", columnList = "tenant_id"),
                @Index(name = "idx_school_user_role",   columnList = "tenant_id,role"),
                @Index(name = "idx_school_user_kc",     columnList = "keycloak_user_id", unique = true)
        }
)
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class SchoolUser extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "keycloak_user_id", nullable = false, unique = true)
    private String keycloakUserId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "nom_complet", nullable = false, length = 100)
    private String nomComplet;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "matiere", length = 100)
    private String matiere;

    /** Fiche enseignant liée (obligatoire pour role=TEACHER) — source unique des données métier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enseignant_id")
    private Teacher teacher;

    @Column(name = "telephone", length = 20)
    private String telephone;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private boolean actif = true;
}
