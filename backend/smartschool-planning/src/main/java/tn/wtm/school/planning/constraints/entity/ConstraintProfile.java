package tn.wtm.school.planning.constraints.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.TenantEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "constraint_profile",
        indexes = {
                @Index(name = "IDX_CONSTRAINT_PROFILE_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_CONSTRAINT_PROFILE_YEAR", columnList = "tenant_id,academic_year_id")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ConstraintProfile extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_seq")
    @SequenceGenerator(name = "planning_seq", sequenceName = "SEQ_PLANNING", allocationSize = 1)
    @Column(name = "id_constraint_profile")
    private Long idConstraintProfile;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "academic_year_id")
    private Long academicYearId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ConstraintSetting> settings = new ArrayList<>();
}
