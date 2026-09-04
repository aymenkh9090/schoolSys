package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.WeekParity;


@Entity
@Table(
        name = "pattern_details",
        indexes = {
                @Index(name = "IDX_PATTERN_DETAIL_TENANT", columnList = "tenant_id"),
                @Index(name = "IDX_PATTERN_DETAIL_PATTERN", columnList = "pattern_id")
        }
)
@Getter
@Setter @Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Ordre et durée des séances")
public class PatternDetail extends TenantEntity {



    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_pattern_detail")
    private Long idPatternDetail;

    // ORDRE
    @Column(name = "session_order", nullable = false)
    private Integer sessionOrder; // 1,2,3

    // DURÉE
    @Column(name = "duration", nullable = false)
    private Double duration; // 2h, 2h, 1h

    // PARITÉ
    @Column(name = "week_parity", length = 10)
    @Enumerated(EnumType.STRING)
    private WeekParity weekParity; // ALL / ODD / EVEN

    // SPLIT
    @Column(name = "is_split",columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean isSplit = false;

    @Column(name = "split_group_index")
    private Integer splitGroupIndex;


    @Column(name = "required_room_type", length = 30)
    @Enumerated(EnumType.STRING)
    private RoomType requiredRoomType;

    @Column(name = "type",length = 50,nullable = false)
    @Comment("Type de seance EX: cours/TD/TP")
    @Enumerated(EnumType.STRING)
    private SessionType type;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id",nullable = false)
    private Pattern pattern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_session_type_id")
    private SubjectSessionType subjectSessionType;





















}
