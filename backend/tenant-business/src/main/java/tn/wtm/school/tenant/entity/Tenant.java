package tn.wtm.school.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.BaseEntity;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "tenants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenant_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_tenant_name", columnNames = "name")
        }
)
@SequenceGenerator(
        name = "tenant_seq_generator",
        sequenceName = "tenant_seq",
        allocationSize = 1
)
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tenant_seq_generator")
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private EtablissementType etablismentType;

    /** Dérivé de {@link #status} — voir {@link #setStatus(TenantStatus)}. Ne jamais setter directement. */
    @Setter(AccessLevel.NONE)
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    /**
     * Unique point d'entrée pour changer le statut : {@code active} est recalculé
     * systématiquement pour ne jamais pouvoir diverger de {@code status}
     * (auparavant les deux champs étaient mis à jour manuellement à 5 endroits
     * différents du service, avec un risque réel de désynchronisation).
     */
    public void setStatus(TenantStatus status) {
        this.status = status;
        this.active = status == TenantStatus.ACTIVE;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    @Builder.Default
    private TenantPlan plan = TenantPlan.FREE;

    @Column(name = "address", nullable = false,length = 255)
    private String address;

    @Column(name = "phone",nullable = false,length = 20)
    private String phone;

    /** Data URL base64 du logo importe depuis le frontend (pas une simple URL — TEXT car VARCHAR(255) est trop etroit). */
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    /** UUID du groupe Keycloak représentant ce tenant. Null avant la création Keycloak. */
    @Column(name = "keycloak_group_id")
    private String keycloakGroupId;

    /** UUID Keycloak de l'admin de l'établissement. Null avant la création Keycloak. */
    @Column(name = "admin_keycloak_id")
    private String adminKeycloakId;

    /** Email de l'administrateur principal de l'établissement. */
    @Column(name = "admin_email", length = 150)
    private String adminEmail;






}
