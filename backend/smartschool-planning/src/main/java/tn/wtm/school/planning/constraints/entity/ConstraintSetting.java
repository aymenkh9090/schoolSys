package tn.wtm.school.planning.constraints.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;

@Entity
@Table(
        name = "constraint_setting",
        indexes = {
                @Index(name = "IDX_CONSTRAINT_SETTING_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_CONSTRAINT_SETTING_PROFILE", columnList = "constraint_profile_id"),
                @Index(name = "UK_CONSTRAINT_SETTING_PROFILE_DEF",
                        columnList = "constraint_profile_id,constraint_definition_id", unique = true)
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ConstraintSetting extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_seq")
    @SequenceGenerator(name = "planning_seq", sequenceName = "SEQ_PLANNING", allocationSize = 1)
    @Column(name = "id_constraint_setting")
    private Long idConstraintSetting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "constraint_profile_id", nullable = false)
    private ConstraintProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "constraint_definition_id", nullable = false)
    private ConstraintDefinition definition;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportanceLevel importance;

    @Column(nullable = false)
    private Integer weight;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;
}
