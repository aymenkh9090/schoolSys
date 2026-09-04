package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "school_working_day",
        indexes = {
                @Index(name = "idx_swd_tenant", columnList = "tenant_id"),
                @Index(name = "idx_swd_day", columnList = "day_of_week"),
                @Index(name = "uk_swd_tenant_day", columnList = "tenant_id,day_of_week", unique = true)}
)
@Comment("Les jours des travails pour une ecole")
public class SchoolWorkingDay extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_school_working_day")
    private Long schoolWorkingDayId;

    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @Column(name = "morning_start")
    private LocalTime morningStart;

    @Column(name = "morning_end")
    private LocalTime morningEnd;

    @Column(name = "afternoon_start")
    private LocalTime afternoonStart;

    @Column(name = "afternoon_end")
    private LocalTime afternoonEnd;

    @Column(name = "est_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean active = true;











}
