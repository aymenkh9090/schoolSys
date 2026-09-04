package tn.wtm.school.planning.constraints.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.BaseEntity;
import tn.wtm.school.planning.constraints.enums.ConstraintCategory;
import tn.wtm.school.planning.constraints.enums.ConstraintType;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;

@Entity
@Table(
        name = "constraint_definition",
        indexes = {
                @Index(name = "IDX_CONSTRAINT_DEF_CODE", columnList = "code", unique = true),
                @Index(name = "IDX_CONSTRAINT_DEF_CATEGORY", columnList = "category")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ConstraintDefinition extends BaseEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_seq")
    @SequenceGenerator(name = "planning_seq", sequenceName = "SEQ_PLANNING", allocationSize = 1)
    @Column(name = "id_constraint_definition")
    private Long idConstraintDefinition;

    @Column(nullable = false, length = 100, unique = true)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConstraintCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConstraintType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_importance", nullable = false, length = 20)
    private ImportanceLevel defaultImportance;

    @Column(name = "default_enabled", nullable = false)
    @Builder.Default
    private Boolean defaultEnabled = Boolean.TRUE;

    @Column(name = "parameter_schema", columnDefinition = "TEXT")
    private String parameterSchema;
}
