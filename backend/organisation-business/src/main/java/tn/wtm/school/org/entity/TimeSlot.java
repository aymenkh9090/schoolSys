package tn.wtm.school.org.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.org.enums.DayPeriod;

import java.time.DayOfWeek;
import java.time.LocalTime;

// TimeSlot will be used in PatternDetail and Lesson planning

@Entity
@Table(
        name = "creneaux_horaires",
        indexes = {
                @Index(name = "IDX_CRENEAU_TENANT", columnList = "tenant_id"),
                @Index(name = "UK_CRENEAU_TENANT_DAY_TIME", columnList = "tenant_id,day_of_week,start_time,end_time", unique = true)
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@Comment("Table des créneaux horaires")
public class TimeSlot extends TenantEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generateur_admscol")
    @SequenceGenerator(name = "generateur_admscol", sequenceName = "SEQADMSCOL", allocationSize = 1)
    @Column(name = "id_creneau_horaire")
    @Comment("Clé primaire du créneau horaire")
    private Long idTimeSlot;


    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20, nullable = false)
    @Comment("Jour de la semaine: MONDAY, TUESDAY, ...")
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    @Comment("Heure de début du créneau")
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    @Comment("Heure de fin du créneau")
    private LocalTime endTime;

    @Column(name = "order_index", nullable = false)
    @Comment("Ordre d'affichage du créneau dans la journée")
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_period", nullable = false, length = 20)
    private DayPeriod dayPeriod;























}
