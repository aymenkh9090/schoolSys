package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.wtm.school.common.base.BaseEntity;
import tn.wtm.school.org.enums.SessionType;

@Entity
@Table(
        name = "national_pattern_sessions",
        indexes = {
                @Index(name = "IDX_NAT_SESSION_DETAIL", columnList = "national_pattern_detail_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class NationalPatternSession extends BaseEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nat_pattern_seq")
    @SequenceGenerator(name = "nat_pattern_seq", sequenceName = "SEQ_NATIONAL_PATTERN", allocationSize = 1)
    @Column(name = "id_national_pattern_session")
    private Long idNationalPatternSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "national_pattern_detail_id", nullable = false)
    private NationalPatternDetail nationalPatternDetail;

    @Column(name = "session_order", nullable = false)
    private Integer sessionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", length = 20, nullable = false)
    private SessionType sessionType;

    @Column(name = "duration", nullable = false)
    private Double duration;

    /** FULL_CLASS or DEMI_GROUP */
    @Column(name = "grouping_type", length = 20)
    private String groupingType;

    /** NORMAL, LAB_PHYSICS, LAB_SCIENCE, SALLE_SPORT, COMPUTER */
    @Column(name = "required_room_type", length = 30)
    private String requiredRoomType;

    /** ALL, BIWEEKLY */
    @Column(name = "week_parity", length = 20)
    private String weekParity;
}
