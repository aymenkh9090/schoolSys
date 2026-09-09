package tn.wtm.school.planning.solver.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.*;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.enums.WeekParity;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

/**
 * Core Timefold planning entity — one unit of instruction to be placed on the timetable.
 *
 * Fixed facts are loaded from the organisation-module (TeachingAssignment + PatternDetail)
 * and never changed by the solver.
 *
 * Planning variables (timeSlot, teacher, room) are decided by Timefold.
 *
 * Demi-group pairs: when groupIndex > 0, two lessons (A=1, B=2) share the same
 * pairedLessonId so the solver can enforce they are placed in the same TimeSlot.
 */
@PlanningEntity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Lesson {

    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    // ── fixed facts ───────────────────────────────────────────────────────────

    private String subjectCode;
    private String subjectName;

    /** True quand la matière est déclarée « principale » (Subject.estPrincipale). */
    private boolean mainSubject;

    private String studentClassName;

    /**
     * Code du niveau de la classe (ex. « 4EME », « TERMINALE »), repris de
     * ClassGroup.level. Exposé au DSL sous le champ {@code class.level} : c'est
     * ce qui permet à une école d'écrire une règle qui ne vise qu'un niveau.
     */
    private String studentClassLevel;

    /** Spécialité de la classe (ClassGroup.codeSpecialite), ou null. */
    private String studentClassSpeciality;

    private Long teachingAssignmentId;

    private SessionType sessionType;

    /** Non-null only when this lesson requires a specific lab/sport room. */
    private RoomType requiredRoomType;

    private boolean requiresSpecialRoom;

    /**
     * 0 = full class, 1 = demi-group A, 2 = demi-group B.
     * Pairs of lessons with groupIndex 1 and 2 share the same pairedLessonId.
     */
    private int groupIndex;

    /**
     * Number of consecutive 30-min slots this session occupies.
     * 1h = 2 slots, 1h30 = 3 slots, 2h = 4 slots. The assigned {@link #timeSlot}
     * is the START slot; the session spans durationSlots × 30 min from there.
     */
    @Builder.Default
    private int durationSlots = 1;

    /**
     * Semaines où la séance a effectivement lieu — notation {@code ①} du § N.1.
     *
     * <p>Jamais {@code null} après génération : {@link WeekParity#ALL} est le
     * cas ordinaire. Une séance de quinzaine porte {@code ODD} ou {@code EVEN},
     * et deux séances de parités opposées ne se disputent ni le créneau, ni la
     * salle, ni l'enseignant — voir {@link #sharesWeeksWith(Lesson)}.
     */
    @Builder.Default
    private WeekParity weekParity = WeekParity.ALL;

    /** Links the two demi-group lessons that must share the same TimeSlot. */
    private Long pairedLessonId;

    /**
     * Volume hebdomadaire officiel de la matière pour ce niveau, en créneaux de
     * 30 min — {@code niveaux_matieres.heures_semaine} × 2.
     *
     * <p>Porté par chaque séance parce que c'est la seule façon d'en disposer
     * dans un flux de contraintes : le solveur ne voit que des {@code Lesson}.
     * Toutes les séances d'un même couple classe / matière portent la même
     * valeur ; la contrainte {@code RESPECT_OFFICIAL_SUBJECT_HOURS} compare la
     * somme des durées placées à ce volume (§ T.1 de la circulaire).
     *
     * <p>Zéro quand le volume officiel est inconnu — la contrainte se tait
     * alors plutôt que de pénaliser une donnée manquante.
     */
    private int officialWeeklySlots;

    /** Number of students in the class (needed for room capacity constraint). */
    private int classStudentCount;

    // ── planning variables ────────────────────────────────────────────────────

    @PlanningVariable(valueRangeProviderRefs = "timeSlotRange")
    private TimeSlotRef timeSlot;

    // Champ fixe — chargé depuis TeachingAssignment, Timefold ne le modifie pas
    private TeacherRef teacher;

    @PlanningVariable(valueRangeProviderRefs = "roomRange")
    private RoomRef room;

    // ── derived helpers ───────────────────────────────────────────────────────

    /** True for group A (1) or group B (2) — i.e. not a full-class session. */
    public boolean isDemiGroup() {
        return groupIndex > 0;
    }

    /** True when this lesson has a demi-group partner that must share its time slot. */
    public boolean isPaired() {
        return pairedLessonId != null;
    }

    /**
     * Les deux séances peuvent-elles tomber la même semaine ?
     *
     * <p>Fausse uniquement pour une quinzaine impaire face à une quinzaine
     * paire. Toute contrainte de conflit — enseignant, salle, classe — doit en
     * tenir compte : deux séances qui n'ont jamais lieu la même semaine ne se
     * heurtent pas, même sur le même créneau.
     */
    public boolean sharesWeeksWith(Lesson other) {
        WeekParity mine  = weekParity == null ? WeekParity.ALL : weekParity;
        WeekParity yours = other.weekParity == null ? WeekParity.ALL : other.weekParity;
        return mine.overlapsWith(yours);
    }

    /** True when both time slot and room are assigned (fully placed). */
    public boolean isAssigned() {
        return timeSlot != null && room != null;
    }

    /** Minutes covered by one 30-min slot. */
    private static final int SLOT_MINUTES = 30;

    /** Start time of the session (its start slot's start), or null if unplaced. */
    public java.time.LocalTime getStartTime() {
        return timeSlot == null ? null : timeSlot.getStartTime();
    }

    /** Exclusive end time = start + durationSlots × 30 min, or null if unplaced. */
    public java.time.LocalTime getEndTime() {
        return endTimeIfPlacedAt(timeSlot);
    }

    /**
     * Fin qu'aurait cette séance si elle était posée sur {@code slot} —
     * <em>sans</em> l'y poser.
     *
     * <p>Existe pour la recherche de créneau de remplacement, qui doit évaluer
     * des positions hypothétiques sur la meilleure solution en mémoire. Muter
     * la leçon pour tester, puis la remettre en place, exposerait la solution
     * partagée à un état incohérent le temps du test.
     */
    public java.time.LocalTime endTimeIfPlacedAt(TimeSlotRef slot) {
        if (slot == null || slot.getStartTime() == null) {
            return null;
        }
        return slot.getStartTime().plusMinutes((long) SLOT_MINUTES * Math.max(1, durationSlots));
    }

    /**
     * True when this lesson's time interval overlaps {@code other}'s on the same day.
     * Used by teacher/room/class conflict constraints for multi-slot sessions.
     *
     * <p>Deux séances de quinzaine opposées ne se chevauchent jamais, même à
     * cheval sur le même créneau : elles n'ont pas lieu la même semaine. La
     * parité est donc évaluée ici plutôt que répétée dans chaque contrainte de
     * conflit — l'oublier dans une seule d'entre elles suffirait à réintroduire
     * un faux conflit.
     */
    public boolean overlapsInTime(Lesson other) {
        return wouldOverlapAt(timeSlot, other);
    }

    /**
     * Même question, posée d'un créneau où la séance n'est pas encore : si on la
     * posait sur {@code slot}, heurterait-elle {@code other} ?
     *
     * <p>{@link #overlapsInTime} en est le cas particulier — le créneau actuel.
     * Les deux passent par ici pour que la règle de chevauchement, parité de
     * quinzaine comprise, n'existe qu'à un seul endroit : une recherche de
     * remplacement qui recopierait le calcul finirait par proposer un créneau
     * que les contraintes refusent, ou par en écarter un qui convenait.
     */
    public boolean wouldOverlapAt(TimeSlotRef slot, Lesson other) {
        if (slot == null || other.timeSlot == null) {
            return false;
        }
        if (slot.getDay() != other.timeSlot.getDay()) {
            return false;
        }
        if (!sharesWeeksWith(other)) {
            return false;
        }
        java.time.LocalTime aStart = slot.getStartTime(), aEnd = endTimeIfPlacedAt(slot);
        java.time.LocalTime bStart = other.getStartTime(), bEnd = other.getEndTime();
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
