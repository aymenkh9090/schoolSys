package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "national_patterns",
        indexes = {
                @Index(name = "IDX_NAT_PATTERN_COUNTRY_LEVEL", columnList = "country_code,level_code"),
                @Index(name = "UK_NAT_PATTERN_CODE_YEAR", columnList = "code,academic_year", unique = true)
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class NationalPattern extends BaseEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nat_pattern_seq")
    @SequenceGenerator(name = "nat_pattern_seq", sequenceName = "SEQ_NATIONAL_PATTERN", allocationSize = 1)
    @Column(name = "id_national_pattern")
    private Long idNationalPattern;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "academic_year")
    private Integer academicYear;

    @Column(name = "active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "level_code", nullable = false, length = 50)
    private String levelCode;

    @OneToMany(mappedBy = "nationalPattern", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NationalPatternDetail> details = new ArrayList<>();
}
