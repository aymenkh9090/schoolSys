package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "national_pattern_details",
        indexes = {
                @Index(name = "IDX_NAT_DETAIL_PATTERN", columnList = "national_pattern_id"),
                @Index(name = "UK_NAT_DETAIL_PATTERN_SUBJECT",
                        columnList = "national_pattern_id,subject_code", unique = true)
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class NationalPatternDetail extends BaseEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nat_pattern_seq")
    @SequenceGenerator(name = "nat_pattern_seq", sequenceName = "SEQ_NATIONAL_PATTERN", allocationSize = 1)
    @Column(name = "id_national_pattern_detail")
    private Long idNationalPatternDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "national_pattern_id", nullable = false)
    private NationalPattern nationalPattern;

    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    @Column(name = "total_hours_per_week")
    private Double totalHoursPerWeek;

    @Column(name = "repartition", length = 150)
    private String repartition;

    @OneToMany(mappedBy = "nationalPatternDetail", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NationalPatternSession> sessions = new ArrayList<>();
}
