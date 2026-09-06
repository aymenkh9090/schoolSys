package tn.wtm.school.planning.solver.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.constraints.dsl.CompiledConstraint;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.enums.WeekParity;
import tn.wtm.school.planning.solver.ref.TeacherRef;

import java.time.DayOfWeek;
import java.util.Objects;

import static tn.wtm.school.planning.solver.constraint.ConstraintCodes.*;

public class TimetableConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[]{
                // ── Bloc 1 : contraintes structurelles hard-codées ────────────
                teacherConflict(f),
                roomConflict(f),
                classConflict(f),
                roomCapacity(f),
                teacherAvailability(f),
                specialRoomRequired(f),
                normalCourseNotInSpecialRoom(f),
                pairedDemiGroupSameSlot(f),
                noLessonInBreakSlot(f),
                lessonExceedsWorkingBlock(f),
                noStudentIdleGaps(f),

                // ── Bloc 2 : contraintes dynamiques HARD (activées en DB) ─────
                maxTeacherHoursPerDay(f),
                maxStudentHoursPerDay(f),
                maxTeacherHoursFridaySaturday(f),
                oneTeacherPerSubjectClass(f),
                maxConsecutiveSameSessions(f),
                respectOfficialSubjectHours(f),
                physicalEducationSessionShape(f),
                minStudentHoursPerHalfDay(f),

                // ── Bloc 3 : contraintes dynamiques MEDIUM / SOFT ─────────────
                theoryPracticeSeparation(f),
                balancedMorningAfternoon(f),
                teacherWeeklyRestDay(f),
                avoidSubjectConcentrationSameDay(f),
                subjectTwoHoursNotConsecutiveDays(f),
                physicalEducationSessionSpacing(f),
                mainSubjectsMorningQuota(f),
                classRoomStabilityPerHalfDay(f),
                teacherMinTwoLevels(f),
                balancedTeacherWorkload(f),
                mainSubjectBalancedDistribution(f),

                // ── Bloc 4 : contraintes personnalisées (DSL, par établissement) ──
                customLessonPenalty(f, DslSeverity.HARD,   CUSTOM_RULE_HARD),
                customLessonPenalty(f, DslSeverity.MEDIUM, CUSTOM_RULE_MEDIUM),
                customLessonPenalty(f, DslSeverity.SOFT,   CUSTOM_RULE_SOFT),
                customLessonReward(f,  DslSeverity.MEDIUM, CUSTOM_REWARD_MEDIUM),
                customLessonReward(f,  DslSeverity.SOFT,   CUSTOM_REWARD_SOFT),
                customAggregateLimit(f, DslSeverity.HARD,   CUSTOM_LIMIT_HARD),
                customAggregateLimit(f, DslSeverity.MEDIUM, CUSTOM_LIMIT_MEDIUM),
                customAggregateLimit(f, DslSeverity.SOFT,   CUSTOM_LIMIT_SOFT),
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bloc 1 — Contraintes structurelles hard-codées
    // ══════════════════════════════════════════════════════════════════════════

    // Un prof ne peut pas être dans 2 classes en même temps (chevauchement d'intervalles
    // multi-slots). EXCEPTION : un prof peut encadrer G1 et G2 en parallèle (salles
    // différentes, même créneau) — paire demi-groupe partageant le pairedLessonId.
    private Constraint teacherConflict(ConstraintFactory f) {
        return f.forEachUniquePair(Lesson.class,
                Joiners.equal(l -> l.getTimeSlot() == null ? null : l.getTimeSlot().getDay()),
                Joiners.equal(l -> l.getTeacher() == null ? null : l.getTeacher().getId()))
                .filter((a, b) -> a.getTimeSlot() != null && a.getTeacher() != null)
                .filter(Lesson::overlapsInTime)
                .filter((a, b) -> a.getPairedLessonId() == null
                        || !Objects.equals(a.getPairedLessonId(), b.getPairedLessonId()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(TEACHER_CONFLICT);
    }

    // Une salle ne peut accueillir qu'un seul cours à la fois — sans exception.
    // G1 et G2 d'une même paire de demi-groupes DOIVENT être dans des salles différentes
    // (ex: G1→sc1, G2→sc2). Le prof circule entre les deux salles.
    private Constraint roomConflict(ConstraintFactory f) {
        return f.forEachUniquePair(Lesson.class,
                Joiners.equal(l -> l.getTimeSlot() == null ? null : l.getTimeSlot().getDay()),
                Joiners.equal(l -> l.getRoom() == null ? null : l.getRoom().getId()))
                .filter((a, b) -> a.getTimeSlot() != null && a.getRoom() != null)
                .filter(Lesson::overlapsInTime)
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(ROOM_CONFLICT);
    }

    // Une classe ne peut pas avoir 2 cours en même temps (intervalles qui se chevauchent)
    // EXCEPTION : demi-groupes de groupIndex différents sont autorisés en parallèle
    private Constraint classConflict(ConstraintFactory f) {
        return f.forEachUniquePair(Lesson.class,
                Joiners.equal(l -> l.getTimeSlot() == null ? null : l.getTimeSlot().getDay()),
                Joiners.equal(Lesson::getStudentClassName))
                .filter((a, b) -> a.getTimeSlot() != null)
                .filter(Lesson::overlapsInTime)
                .filter((a, b) ->
                        !(a.getGroupIndex() > 0 && b.getGroupIndex() > 0
                                && a.getGroupIndex() != b.getGroupIndex()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(CLASS_CONFLICT);
    }

    // La salle doit avoir une capacité suffisante : class entière = nbEleve,
    // demi-groupe = ceil(nbEleve / 2).
    private Constraint roomCapacity(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getRoom() != null)
                .filter(l -> {
                    int needed = l.isDemiGroup()
                            ? (l.getClassStudentCount() + 1) / 2
                            : l.getClassStudentCount();
                    return l.getRoom().getCapacity() < needed;
                })
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(ROOM_CAPACITY);
    }

    // Un enseignant ne peut pas enseigner sur un jour où il est indisponible.
    private Constraint teacherAvailability(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTeacher() != null && l.getTimeSlot() != null)
                .filter(l -> l.getTeacher().isUnavailableOn(l.getTimeSlot().getDay()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(TEACHER_AVAILABILITY);
    }

    /**
     * Aucune heure creuse chez l'élève, au sein d'une même demi-journée — § I.5.
     *
     * <p>Les séances sont regroupées par (classe, jour, demi-journée) et la
     * suite triée doit être contiguë : une séance de N créneaux couvre
     * {@code [orderIndex, orderIndex + N - 1]}, la suivante doit commencer au
     * créneau d'après.
     *
     * <p><b>La parité de semaine est évaluée à part</b>, semaine impaire puis
     * semaine paire. Une séance de quinzaine n'occupe son créneau qu'une semaine
     * sur deux : la compter toutes les semaines bouche un trou que l'élève subit
     * réellement l'autre semaine, et le trou disparaît du score sans avoir
     * disparu de l'emploi du temps. La réciproque compte tout autant : deux
     * quinzaines opposées de part et d'autre d'un créneau libre ne créent aucun
     * trou, puisque aucune semaine ne voit les deux.
     */
    private Constraint noStudentIdleGaps(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() != 2)
                .groupBy(
                        Lesson::getStudentClassName,
                        l -> l.getTimeSlot().getDay() + "|" + l.getTimeSlot().getPeriod(),
                        ConstraintCollectors.toList())
                .filter((cls, demiJournee, seances) -> trouDansLaDemiJournee(seances))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NO_STUDENT_IDLE_GAPS);
    }

    /**
     * Un trou dans l'une des deux semaines suffit à condamner la demi-journée :
     * elle n'est régulière que si elle l'est les deux.
     */
    private static boolean trouDansLaDemiJournee(java.util.List<Lesson> seances) {
        return hasGapInSortedLessons(seancesDeLaSemaine(seances, WeekParity.ODD))
                || hasGapInSortedLessons(seancesDeLaSemaine(seances, WeekParity.EVEN));
    }

    private static boolean hasGapInSortedLessons(java.util.List<Lesson> lessons) {
        if (lessons.size() < 2) return false;
        java.util.List<Lesson> sorted = lessons.stream()
                .filter(l -> l.getTimeSlot() != null && l.getTimeSlot().getOrderIndex() != null)
                .sorted(java.util.Comparator.comparingInt(l -> l.getTimeSlot().getOrderIndex()))
                .toList();
        for (int i = 0; i < sorted.size() - 1; i++) {
            int currLast  = sorted.get(i).getTimeSlot().getOrderIndex()
                            + sorted.get(i).getDurationSlots() - 1;
            int nextStart = sorted.get(i + 1).getTimeSlot().getOrderIndex();
            if ((currLast + 1) < nextStart) return true;
        }
        return false;
    }

    // Une séance multi-slots ne doit pas dépasser son bloc de travail (déborder sur la
    // pause 12h-14h ou la fin de journée). maxDurationSlots = nb de slots contigus
    // disponibles depuis le créneau de départ jusqu'à la fin du bloc (matin/après-midi).
    private Constraint lessonExceedsWorkingBlock(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null)
                .filter(l -> l.getDurationSlots() > l.getTimeSlot().getMaxDurationSlots())
                .penalize(HardMediumSoftScore.ONE_HARD,
                        l -> l.getDurationSlots() - l.getTimeSlot().getMaxDurationSlots())
                .asConstraint(LESSON_EXCEEDS_WORKING_BLOCK);
    }

    // TP/SPORT doit être dans la bonne salle spécialisée
    private Constraint specialRoomRequired(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(Lesson::isRequiresSpecialRoom)
                .filter(l -> l.getRoom() != null)
                .filter(l -> !l.getRoom().canAccommodate(l.getRequiredRoomType()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(SPECIAL_ROOM_REQUIRED);
    }

    // Cours normal interdit en salle spécialisée (LAB/SPORT/COMPUTER)
    private Constraint normalCourseNotInSpecialRoom(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> !l.isRequiresSpecialRoom())
                .filter(l -> l.getRoom() != null)
                .filter(l -> l.getRoom().isSpecialRoom())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NORMAL_COURSE_NOT_IN_SPECIAL_ROOM);
    }

    // Groupe A et groupe B du même TP doivent être au même créneau
    private Constraint pairedDemiGroupSameSlot(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(a -> a.getGroupIndex() == 1 && a.getPairedLessonId() != null)
                .join(Lesson.class,
                        Joiners.equal(Lesson::getPairedLessonId),
                        Joiners.filtering((a, b) -> b.getGroupIndex() == 2))
                .filter((a, b) -> a.getTimeSlot() != null && b.getTimeSlot() != null)
                .filter((a, b) -> !Objects.equals(a.getTimeSlot(), b.getTimeSlot()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(PAIRED_DEMI_GROUP_SAME_SLOT);
    }

    // Aucun cours pendant la pause 12h-14h
    private Constraint noLessonInBreakSlot(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getTimeSlot().isBreakSlot())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NO_LESSON_IN_BREAK_SLOT);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bloc 2 — Contraintes dynamiques HARD (pilotées par ActiveConstraintParam)
    // Pattern : JOIN avec ActiveConstraintParam sur le code de la contrainte.
    //   • Param absent  → JOIN vide → 0 pénalité (contrainte désactivée)
    //   • Param présent → contrainte active
    // ══════════════════════════════════════════════════════════════════════════

    // Max 6h/jour pour un enseignant (Ministry §II-3)
    // groupIndex=2 exclu : les demi-groupes A et B partagent le même créneau pour le même prof
    private Constraint maxTeacherHoursPerDay(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getTeacher() != null
                        && l.getGroupIndex() != 2)
                .groupBy(l -> l.getTeacher().getId(),
                         l -> l.getTimeSlot().getDay(),
                         ConstraintCollectors.sum(Lesson::getDurationSlots))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((t, d, slots) -> MAX_TEACHER_HOURS_PER_DAY,
                                ActiveConstraintParam::getCode))
                // intParam is in hours → convert to 30-min slots (×2)
                .filter((t, d, slots, p) -> p.getIntParam() > 0 && slots > p.getIntParam() * 2)
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (t, d, slots, p) -> slots - p.getIntParam() * 2)
                .asConstraint(MAX_TEACHER_HOURS_PER_DAY);
    }

    // Max 6h/jour pour les élèves — groupIndex 0 (full-class) et 1 (demi-groupe A)
    // groupIndex=2 exclu pour éviter double-comptage des paires
    private Constraint maxStudentHoursPerDay(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() != 2)
                .groupBy(Lesson::getStudentClassName,
                         l -> l.getTimeSlot().getDay(),
                         ConstraintCollectors.sum(Lesson::getDurationSlots))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cls, d, slots) -> MAX_STUDENT_HOURS_PER_DAY,
                                ActiveConstraintParam::getCode))
                // intParam is in hours → convert to 30-min slots (×2)
                .filter((cls, d, slots, p) -> p.getIntParam() > 0 && slots > p.getIntParam() * 2)
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (cls, d, slots, p) -> slots - p.getIntParam() * 2)
                .asConstraint(MAX_STUDENT_HOURS_PER_DAY);
    }

    // Max 5h les vendredi et samedi (Ministry §II-4) — groupIndex=2 exclu (même logique)
    private Constraint maxTeacherHoursFridaySaturday(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getTeacher() != null
                        && l.getGroupIndex() != 2
                        && isFridayOrSaturday(l.getTimeSlot().getDay()))
                .groupBy(l -> l.getTeacher().getId(),
                         l -> l.getTimeSlot().getDay(),
                         ConstraintCollectors.sum(Lesson::getDurationSlots))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((t, d, slots) -> MAX_TEACHER_HOURS_FRIDAY_SATURDAY,
                                ActiveConstraintParam::getCode))
                // intParam is in hours → convert to 30-min slots (×2)
                .filter((t, d, slots, p) -> p.getIntParam() > 0 && slots > p.getIntParam() * 2)
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (t, d, slots, p) -> slots - p.getIntParam() * 2)
                .asConstraint(MAX_TEACHER_HOURS_FRIDAY_SATURDAY);
    }

    // Un seul enseignant par matière/classe — pas deux profs différents pour la même matière
    private Constraint oneTeacherPerSubjectClass(ConstraintFactory f) {
        return f.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getStudentClassName),
                Joiners.equal(Lesson::getSubjectCode))
                .filter((a, b) -> a.getTeacher() != null && b.getTeacher() != null)
                .filter((a, b) -> !a.getTeacher().getId().equals(b.getTeacher().getId()))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((a, b) -> ONE_TEACHER_PER_SUBJECT_CLASS,
                                ActiveConstraintParam::getCode))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(ONE_TEACHER_PER_SUBJECT_PER_CLASS);
    }

    /**
     * Pas plus de N séances d'affilée dans la même matière — la consécutivité
     * mesurée pour ce qu'elle est.
     *
     * <p><b>Ce qu'elle comptait avant.</b> Le nombre de séances de la matière
     * <em>dans la journée</em>, sans regarder où elles tombaient. Deux heures de
     * mathématiques à 8 h et à 16 h étaient donc comptées « consécutives », et
     * trois heures d'affilée séparées par une récréation d'anglais ne l'étaient
     * pas. Elle mesurait la concentration, pas la consécutivité — et faisait
     * doublon avec {@code AVOID_SUBJECT_CONCENTRATION_SAME_DAY}, qui la mesure
     * déjà, avec le même {@code groupBy}, en SOFT.
     *
     * <p>Les deux contraintes ne se recouvrent plus : celle-ci regarde la plus
     * longue suite de séances contiguës, l'autre le total de la journée. Une
     * matière donnée deux fois le matin et deux fois l'après-midi concentre sans
     * enchaîner ; quatre heures d'affilée enchaînent sans plus concentrer.
     *
     * <p><b>Le groupement va jusqu'à la demi-journée</b>, et pas seulement au
     * jour : la dernière heure de la matinée et la première de l'après-midi ne
     * s'enchaînent pas, la pause du § I.3 les sépare. Les rattacher au même
     * groupe reviendrait à compter une suite là où l'élève a déjeuné.
     *
     * <p><b>La parité de semaine est évaluée à part</b>, semaine impaire puis
     * semaine paire : une quinzaine ne prolonge pas une suite les semaines où
     * elle n'a pas lieu. {@code noStudentIdleGaps} et
     * {@code minStudentHoursPerHalfDay} lisent désormais la parité de la même
     * façon, par {@link #seancesDeLaSemaine}.
     */
    private Constraint maxConsecutiveSameSessions(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() != 2
                        && l.getTimeSlot().getOrderIndex() != null)
                .groupBy(
                        l -> l.getStudentClassName() + "|" + l.getSubjectCode()
                                + "|" + l.getTimeSlot().getDay()
                                + "|" + l.getTimeSlot().getPeriod(),
                        ConstraintCollectors.toList())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cle, seances) -> MAX_TWO_CONSECUTIVE_SESSIONS,
                                ActiveConstraintParam::getCode))
                .filter((cle, seances, p) -> p.getIntParam() > 0
                        && plusLongueSuite(seances) > p.getIntParam())
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (cle, seances, p) -> plusLongueSuite(seances) - p.getIntParam())
                .asConstraint(MAX_TWO_CONSECUTIVE_SESSIONS);
    }

    /**
     * Le volume hebdomadaire placé pour un couple classe / matière doit être
     * exactement celui du programme officiel — § T.1 de la circulaire n°66.
     *
     * <p><b>Ce que cette contrainte attrape réellement.</b> Toutes les séances
     * étant engendrées à partir du pattern, la somme devrait coïncider
     * d'elle-même : quand elle ne coïncide pas, c'est que la <em>génération</em>
     * est fautive — séance perdue, séance en double, durée mal transcrite. La
     * contrainte ne corrige rien, elle rend visible en violation dure ce qui
     * passait jusqu'ici pour un emploi du temps valide mais incomplet. C'est
     * précisément le défaut qu'avaient les séances en système de groupes, dont
     * la durée était divisée par deux : le planning était « faisable » et
     * l'élève perdait la moitié de son informatique.
     *
     * <p><b>{@code groupIndex != 2} :</b> les deux moitiés d'une classe suivent
     * la même séance au même moment. Compter les deux doublerait le volume reçu
     * par l'élève, qui n'assiste qu'à l'une d'elles.
     *
     * <p><b>La parité de semaine n'est pas déduite ici.</b> Une séance de
     * quinzaine compte pour sa durée pleine, parce que c'est la convention des
     * données : {@code heures_semaine} vaut 3 pour la physique, soit 1 h de
     * quinzaine plus 2 h de TP. Comparer une somme pondérée à un total qui ne
     * l'est pas ferait échouer toutes les matières à quinzaine.
     *
     * <p>Le volume officiel voyage dans la clé de regroupement plutôt que dans
     * un collecteur : les flux de contraintes s'arrêtent au quadruplet, et la
     * jointure avec {@link ActiveConstraintParam} en consomme déjà un terme.
     */
    private Constraint respectOfficialSubjectHours(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getGroupIndex() != 2 && l.getOfficialWeeklySlots() > 0)
                .groupBy(Lesson::getStudentClassName,
                         l -> l.getSubjectCode() + '|' + l.getOfficialWeeklySlots(),
                         ConstraintCollectors.sum(Lesson::getDurationSlots))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cls, cle, places) -> RESPECT_OFFICIAL_SUBJECT_HOURS,
                                ActiveConstraintParam::getCode))
                .filter((cls, cle, places, p) -> places != officielDepuisCle(cle))
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (cls, cle, places, p) -> Math.abs(places - officielDepuisCle(cle)))
                .asConstraint(RESPECT_OFFICIAL_SUBJECT_HOURS);
    }

    /** Volume officiel encodé en fin de clé « MATIERE|slots ». */
    private static int officielDepuisCle(String cle) {
        int sep = cle.lastIndexOf('|');
        return sep < 0 ? 0 : Integer.parseInt(cle.substring(sep + 1));
    }

    /**
     * L'éducation physique suit l'un des deux découpages autorisés par le
     * § III.2.b : « soit en trois séances espacées, soit en deux séances dont
     * l'une de deux heures et l'autre d'une heure ».
     *
     * <p>La circulaire n'en propose pas un troisième. Quatre séances d'une
     * demi-heure, ou une seule de trois heures, totalisent le bon volume et ne
     * sont pourtant pas conformes — d'où une contrainte sur la <em>forme</em>,
     * distincte de {@code RESPECT_OFFICIAL_SUBJECT_HOURS} qui, lui, ne regarde
     * que le total.
     *
     * <p><b>L'espacement n'est pas vérifié ici.</b> La règle des 24 heures
     * entre deux séances relève du § III.2.b également, mais elle porte sur le
     * placement et non sur le découpage ; elle a sa propre contrainte. Les
     * mélanger rendrait le diagnostic illisible : « votre EPS n'est pas
     * conforme » sans dire si c'est le nombre de séances ou leur écart.
     *
     * <p>Le paramètre du profil donne le nombre de séances de la première
     * forme (3 par défaut) ; la seconde forme, 2 h + 1 h, est celle que la
     * circulaire énonce littéralement et n'est pas paramétrable.
     */
    private Constraint physicalEducationSessionShape(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getSessionType() == SessionType.SPORT && l.getGroupIndex() != 2)
                .groupBy(Lesson::getStudentClassName, ConstraintCollectors.toList())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cls, seances) -> PHYSICAL_EDUCATION_THREE_SESSIONS,
                                ActiveConstraintParam::getCode))
                .filter((cls, seances, p) -> !decoupageEpsAutorise(seances, p.getIntParam()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(PHYSICAL_EDUCATION_THREE_SESSIONS);
    }

    /**
     * Les deux découpages du § III.2.b, et eux seuls.
     *
     * @param seancesAttendues nombre de séances de la première forme, réglé par
     *                         le profil ; 3 dans la circulaire
     */
    private static boolean decoupageEpsAutorise(java.util.List<Lesson> seances, int seancesAttendues) {
        if (seances.isEmpty()) {
            return true; // matière non enseignée dans cette classe — rien à dire
        }
        int attendues = seancesAttendues > 0 ? seancesAttendues : 3;
        if (seances.size() == attendues) {
            return true;
        }
        // Seconde forme : deux séances, l'une de 2 h (4 créneaux), l'autre d'1 h.
        if (seances.size() == 2) {
            int a = seances.get(0).getDurationSlots();
            int b = seances.get(1).getDurationSlots();
            return (a == 4 && b == 2) || (a == 2 && b == 4);
        }
        return false;
    }

    /**
     * Toute demi-journée où la classe vient doit lui offrir au moins deux
     * heures — § I.2 de la circulaire n°66.
     *
     * <p>Le plafond de six heures par jour existait déjà
     * ({@code MAX_STUDENT_HOURS_PER_DAY}) ; c'est le plancher qui manquait. Les
     * deux bornes viennent pourtant de la même phrase, et c'est le plancher qui
     * décrit la nuisance la plus concrète : faire venir une classe entière au
     * collège pour une heure unique.
     *
     * <p><b>L'EPS est nommément exclu par le texte.</b> Une séance de sport
     * d'une heure, seule dans sa demi-journée, est régulière — c'est même le
     * découpage que le § III.2.b recommande. L'exemption porte sur la
     * demi-journée entièrement sportive : dès qu'une autre matière l'accompagne,
     * le volume de la demi-journée se compte en entier, EPS compris, et doit
     * atteindre le plancher.
     */
    private Constraint minStudentHoursPerHalfDay(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() != 2)
                .groupBy(
                        Lesson::getStudentClassName,
                        l -> l.getTimeSlot().getDay() + "|" + l.getTimeSlot().getPeriod(),
                        ConstraintCollectors.toList())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cls, demiJournee, seances) -> MIN_STUDENT_HOURS_PER_HALF_DAY,
                                ActiveConstraintParam::getCode))
                .filter((cls, demiJournee, seances, p) ->
                        manqueDeLaDemiJournee(seances, plancherDemiJournee(p)) > 0)
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (cls, demiJournee, seances, p) ->
                                manqueDeLaDemiJournee(seances, plancherDemiJournee(p)))
                .asConstraint(MIN_STUDENT_HOURS_PER_HALF_DAY);
    }

    /** Plancher du § I.2 en créneaux de 30 min — 2 h, sauf réglage contraire. */
    private static int plancherDemiJournee(ActiveConstraintParam p) {
        int heures = p.getInt("minHours", p.getIntParam() > 0 ? p.getIntParam() : 2);
        return heures * 2;
    }

    private static int volumeEnCreneaux(java.util.List<Lesson> seances) {
        return seances.stream().mapToInt(Lesson::getDurationSlots).sum();
    }

    /**
     * Ce qui manque à la demi-journée pour atteindre le plancher, en créneaux —
     * zéro quand elle le respecte.
     *
     * <p><b>Les deux semaines sont mesurées séparément, et on retient la pire.</b>
     * Une demi-journée dont l'unique séance est de quinzaine paraissait occupée
     * les deux semaines. Or la semaine où la séance n'a pas lieu, la classe ne se
     * déplace pas — il n'y a rien à lui reprocher —, et la semaine où elle a
     * lieu, la classe vient au collège pour une heure : c'est exactement la
     * nuisance que le § I.2 interdit.
     */
    private static int manqueDeLaDemiJournee(java.util.List<Lesson> seances, int plancher) {
        return Math.max(manqueDeLaSemaine(seances, WeekParity.ODD, plancher),
                manqueDeLaSemaine(seances, WeekParity.EVEN, plancher));
    }

    private static int manqueDeLaSemaine(java.util.List<Lesson> seances,
                                         WeekParity semaine, int plancher) {
        java.util.List<Lesson> deLaSemaine = seancesDeLaSemaine(seances, semaine);
        return demiJourneeSousLePlancher(deLaSemaine, plancher)
                ? plancher - volumeEnCreneaux(deLaSemaine)
                : 0;
    }

    /**
     * Vrai quand la demi-journée est occupée, trop courte, et pas uniquement
     * sportive — les trois conditions du § I.2 réunies.
     */
    private static boolean demiJourneeSousLePlancher(java.util.List<Lesson> seances, int plancher) {
        if (seances.isEmpty()) {
            return false; // demi-journée libre : la règle ne vise que les demi-journées où la classe vient
        }
        if (seances.stream().allMatch(l -> l.getSessionType() == SessionType.SPORT)) {
            return false; // exclusion nommée par le texte
        }
        return volumeEnCreneaux(seances) < plancher;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bloc 3 — Contraintes dynamiques MEDIUM / SOFT
    // ══════════════════════════════════════════════════════════════════════════

    // Cours théorique (COURS/TD) et TP de la même matière ne doivent pas être le même jour
    private Constraint theoryPracticeSeparation(ConstraintFactory f) {
        return f.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getStudentClassName),
                Joiners.equal(Lesson::getSubjectCode))
                .filter((a, b) -> a.getTimeSlot() != null && b.getTimeSlot() != null)
                .filter((a, b) -> a.getTimeSlot().getDay() == b.getTimeSlot().getDay())
                .filter((a, b) -> isTheoryPracticePair(a, b))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((a, b) -> THEORY_PRACTICE_SEPARATION,
                                ActiveConstraintParam::getCode))
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(THEORY_PRACTICE_SEPARATION);
    }

    private boolean isTheoryPracticePair(Lesson a, Lesson b) {
        boolean aTheory   = a.getSessionType() == SessionType.COURS || a.getSessionType() == SessionType.TD;
        boolean bTheory   = b.getSessionType() == SessionType.COURS || b.getSessionType() == SessionType.TD;
        boolean aPractice = a.getSessionType() == SessionType.TP;
        boolean bPractice = b.getSessionType() == SessionType.TP;
        return (aTheory && bPractice) || (aPractice && bTheory);
    }

    /**
     * Alternance matin / après-midi de l'enseignant sur les quatre premiers
     * jours de la semaine — § II.4.
     *
     * <p><b>Correction.</b> Cette contrainte équilibrait jusqu'ici la semaine
     * entière, vendredi et samedi compris. Or ces deux jours-là n'ont pas
     * d'après-midi dans un grand nombre d'établissements — c'est le cas de
     * l'établissement 28 — et chaque heure du vendredi matin creusait donc un
     * déséquilibre que rien ne pouvait combler. La contrainte pénalisait un
     * emploi du temps parfaitement régulier, et le solveur dépensait son budget
     * à courir après un équilibre impossible. La circulaire, elle, borne
     * explicitement l'alternance aux quatre premiers jours.
     *
     * <p>{@code groupIndex != 2} pour la raison habituelle : les deux moitiés
     * d'une classe dédoublée occupent le même créneau chez le même professeur ;
     * les compter toutes les deux gonflerait artificiellement le côté où elles
     * tombent.
     */
    private Constraint balancedMorningAfternoon(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTeacher() != null && l.getTimeSlot() != null
                        && l.getGroupIndex() != 2
                        && estDansLesQuatrePremiersJours(l.getTimeSlot().getDay()))
                .groupBy(
                        l -> l.getTeacher().getId(),
                        ConstraintCollectors.sum(
                                l -> l.getTimeSlot().getPeriod() == DayPeriod.MORNING ? 1 : 0),
                        ConstraintCollectors.sum(
                                l -> l.getTimeSlot().getPeriod() == DayPeriod.AFTERNOON ? 1 : 0))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((t, morn, aft) -> BALANCED_MORNING_AFTERNOON,
                                ActiveConstraintParam::getCode))
                .filter((t, morn, aft, p) -> morn + aft > 0)
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (t, morn, aft, p) -> Math.abs(morn - aft))
                .asConstraint(BALANCED_MORNING_AFTERNOON);
    }

    // Un enseignant devrait avoir au moins un jour de repos par semaine
    private Constraint teacherWeeklyRestDay(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTeacher() != null && l.getTimeSlot() != null)
                .groupBy(l -> l.getTeacher().getId(),
                         ConstraintCollectors.toSet(l -> l.getTimeSlot().getDay()))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((t, days) -> TEACHER_WEEKLY_REST_DAY,
                                ActiveConstraintParam::getCode))
                .filter((t, days, p) -> days.size() >= 6)
                .penalize(HardMediumSoftScore.ofSoft(1),
                        (t, days, p) -> p.getSoftWeight())
                .asConstraint(TEACHER_WEEKLY_REST_DAY);
    }

    // Éviter de concentrer la même matière plusieurs fois le même jour
    private Constraint avoidSubjectConcentrationSameDay(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() == 0)
                .groupBy(
                        l -> l.getStudentClassName() + "|" + l.getSubjectCode()
                                + "|" + l.getTimeSlot().getDay(),
                        ConstraintCollectors.count())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((key, cnt) -> AVOID_SUBJECT_CONCENTRATION_SAME_DAY,
                                ActiveConstraintParam::getCode))
                .filter((key, cnt, p) -> cnt > 1)
                .penalize(HardMediumSoftScore.ofSoft(1),
                        (key, cnt, p) -> (cnt - 1) * p.getSoftWeight())
                .asConstraint(AVOID_SUBJECT_CONCENTRATION_SAME_DAY);
    }

    /**
     * Une matière à deux heures hebdomadaires ne tombe jamais sur deux jours qui
     * se suivent — § III.2.c.
     *
     * <p>Le texte vise l'histoire-géographie et l'éducation islamique et civique,
     * mais il les désigne par leur volume, pas par leur nom : la règle est écrite
     * ici sur {@code officialWeeklySlots}, donc elle suivra le programme si celui-ci
     * change, et elle s'appliquera d'elle-même à toute matière ramenée à 2 h.
     *
     * <p>Une matière de 2 h donnée en une seule séance de deux heures ne forme
     * aucune paire : elle ne peut pas violer la règle, et le flux ne la voit
     * même pas. Ce sont les deux séances d'une heure que la règle écarte.
     *
     * <p>Samedi et lundi ne sont pas consécutifs : la semaine scolaire s'arrête
     * au samedi, et l'écart réel est d'un dimanche entier. Comparer les numéros
     * de jour de {@link DayOfWeek} le dit sans qu'on ait à l'énoncer.
     */
    private Constraint subjectTwoHoursNotConsecutiveDays(ConstraintFactory f) {
        return f.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getStudentClassName),
                Joiners.equal(Lesson::getSubjectCode))
                .filter((a, b) -> a.getGroupIndex() != 2 && b.getGroupIndex() != 2)
                .filter((a, b) -> a.getOfficialWeeklySlots() == DEUX_HEURES_EN_CRENEAUX)
                .filter((a, b) -> a.getTimeSlot() != null && b.getTimeSlot() != null)
                .filter((a, b) -> joursConsecutifs(a.getTimeSlot().getDay(), b.getTimeSlot().getDay()))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((a, b) -> SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS,
                                ActiveConstraintParam::getCode))
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS);
    }

    /**
     * Deux séances d'éducation physique sont séparées d'au moins vingt-quatre
     * heures — § III.2.b, seconde moitié de la phrase.
     *
     * <p>{@code PHYSICAL_EDUCATION_THREE_SESSIONS} vérifie le découpage — trois
     * séances, ou une de 2 h et une d'1 h. Elle ne regarde pas où elles tombent :
     * trois séances le même mardi la satisfont. C'est cette contrainte-ci qui
     * les écarte, et les deux restent distinctes pour que le diagnostic dise
     * laquelle des deux moitiés du § III.2.b est en défaut.
     *
     * <p><b>Mesure de début à début</b> — hypothèse assumée : la circulaire ne
     * dit pas si les vingt-quatre heures se comptent de la fin d'une séance au
     * début de la suivante ou de début à début (voir le § 1.6 du plan de mise en
     * conformité). De début à début est la lecture stricte : elle interdit
     * lundi 10 h puis mardi 8 h, que l'autre lecture accepterait.
     */
    private Constraint physicalEducationSessionSpacing(ConstraintFactory f) {
        return f.forEachUniquePair(Lesson.class,
                Joiners.equal(Lesson::getStudentClassName))
                .filter((a, b) -> a.getSessionType() == SessionType.SPORT
                        && b.getSessionType() == SessionType.SPORT)
                .filter((a, b) -> a.getGroupIndex() != 2 && b.getGroupIndex() != 2)
                .filter((a, b) -> estDatee(a) && estDatee(b))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((a, b) -> PHYSICAL_EDUCATION_SESSION_SPACING,
                                ActiveConstraintParam::getCode))
                .filter((a, b, p) -> minutesEntre(a, b) < separationExigeeEnMinutes(p))
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (a, b, p) -> heuresManquantes(minutesEntre(a, b), separationExigeeEnMinutes(p)))
                .asConstraint(PHYSICAL_EDUCATION_SESSION_SPACING);
    }

    /**
     * Les trois quarts des matières fondamentales — arabe, français,
     * mathématiques — se donnent le matin, § III.2.a.
     *
     * <p><b>Pourquoi une récompense et non une pénalité.</b> Le texte ne dit pas
     * « pas de fondamentale l'après-midi » : il en réserve explicitement un quart
     * à l'après-midi. Une pénalité sur les heures d'après-midi combattrait donc
     * la circulaire elle-même. On récompense les heures matinales jusqu'au quota,
     * et pas au-delà : tout matin ne rapporte pas plus qu'un trois-quarts, si
     * bien que le solveur n'a aucun intérêt à vider l'après-midi.
     *
     * <p>Le quota se calcule par couple classe / matière, à partir du volume
     * réellement placé. {@code Lesson.mainSubject} vient de
     * {@code Subject.estPrincipale} : c'est l'établissement qui désigne ses
     * matières fondamentales, et non une liste de codes figée ici.
     */
    private Constraint mainSubjectsMorningQuota(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.isMainSubject() && l.getTimeSlot() != null && l.getGroupIndex() != 2)
                .groupBy(Lesson::getStudentClassName,
                         Lesson::getSubjectCode,
                         ConstraintCollectors.toList())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cls, matiere, seances) -> MAIN_SUBJECTS_MORNING_QUOTA,
                                ActiveConstraintParam::getCode))
                // Une récompense nulle n'est pas une récompense : la filtrer évite
                // d'inscrire au score des correspondances qui ne pèsent rien.
                .filter((cls, matiere, seances, p) ->
                        creditMatinal(seances, quotaMatinalEnPourcent(p)) > 0)
                .reward(HardMediumSoftScore.ONE_MEDIUM,
                        (cls, matiere, seances, p) -> creditMatinal(seances, quotaMatinalEnPourcent(p)))
                .asConstraint(MAIN_SUBJECTS_MORNING_QUOTA);
    }

    /**
     * Une classe ne change pas de salle au sein d'une demi-journée — § I.4.
     *
     * <p><b>L'exception des salles spécialisées est la règle même du § III.4</b> :
     * les travaux pratiques se font au laboratoire, l'informatique en salle
     * machine, le sport au gymnase. Pénaliser ces déplacements-là rendrait les
     * deux articles contradictoires ; le § I.4 ne parle que des allers-retours
     * gratuits entre salles ordinaires.
     *
     * <p>Les séances dédoublées sont hors du compte : {@code roomConflict} exige
     * déjà que les deux moitiés d'une classe occupent deux salles différentes.
     * Le déplacement y est structurel, pas subi.
     */
    private Constraint classRoomStabilityPerHalfDay(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getRoom() != null
                        && l.getGroupIndex() == 0 && !l.isRequiresSpecialRoom())
                .groupBy(
                        Lesson::getStudentClassName,
                        l -> l.getTimeSlot().getDay() + "|" + l.getTimeSlot().getPeriod(),
                        ConstraintCollectors.toSet(l -> l.getRoom().getId()))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cls, demiJournee, salles) -> CLASS_ROOM_STABILITY_PER_HALF_DAY,
                                ActiveConstraintParam::getCode))
                .filter((cls, demiJournee, salles, p) -> salles.size() > 1)
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (cls, demiJournee, salles, p) -> salles.size() - 1)
                .asConstraint(CLASS_ROOM_STABILITY_PER_HALF_DAY);
    }

    /**
     * Un enseignant intervient sur au moins deux niveaux différents — § II.5.
     *
     * <p>Le code figurait au catalogue depuis l'origine, activable dans
     * l'interface, sans qu'aucun flux ne le consomme : l'établissement pouvait le
     * cocher, le solveur ne le voyait pas. C'est le dernier des codes « jamais
     * implémentés » que la circulaire réclame nommément.
     *
     * <p>Les séances sans niveau renseigné sont écartées en amont plutôt que
     * comptées comme un niveau nul : un niveau manquant est une donnée absente,
     * pas un second niveau.
     */
    private Constraint teacherMinTwoLevels(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTeacher() != null && l.getStudentClassLevel() != null)
                .groupBy(l -> l.getTeacher().getId(),
                         ConstraintCollectors.toSet(Lesson::getStudentClassLevel))
                .join(ActiveConstraintParam.class,
                        Joiners.equal((t, niveaux) -> TEACHER_MIN_TWO_LEVELS,
                                ActiveConstraintParam::getCode))
                .filter((t, niveaux, p) -> niveaux.size() < niveauxExiges(p))
                .penalize(HardMediumSoftScore.ofSoft(1),
                        (t, niveaux, p) -> (niveauxExiges(p) - niveaux.size()) * p.getSoftWeight())
                .asConstraint(TEACHER_MIN_TWO_LEVELS);
    }

    /**
     * Le service d'un enseignant se répartit sur ses jours de travail — § II.2.
     *
     * <p>« L'horaire hebdomadaire dû par l'enseignant est réparti de manière
     * équilibrée sur les jours de travail, sans compter la journée consacrée à
     * la formation. » La circulaire ne chiffre pas cet équilibre, et il ne faut
     * pas lui prêter un chiffre qu'elle n'écrit pas. Ce qui est mesuré ici est
     * donc la seule chose que la phrase interdit sans ambiguïté : <b>concentrer
     * le service sur quelques jours</b>.
     *
     * <p><b>Les deux nombres viennent de la même phrase.</b> Le plafond
     * au-delà duquel une journée est dite concentrée est le service divisé par
     * les jours travaillés — la part équitable — et jamais moins de deux heures,
     * le plancher que le § II.2 énonce dans la phrase suivante. Sans ce plancher,
     * la contrainte pousserait un enseignant à mi-temps vers une heure par jour
     * six jours par semaine, c'est-à-dire exactement ce que le même article
     * interdit. La pénalité est la somme des créneaux qui dépassent ce plafond :
     * un service de douze heures tenu en deux jours coûte plus qu'en trois.
     *
     * <p><b>Le jour de formation</b> du § II.1 est retiré du diviseur par le
     * biais des indisponibilités de l'enseignant — c'est là qu'il devrait être
     * inscrit. Rien ne peuple encore cette source (voir § P5), le diviseur vaut
     * donc aujourd'hui les jours ouvrés de l'établissement.
     *
     * <p>La parité de semaine est évaluée à part, comme partout ailleurs : une
     * semaine où la quinzaine tombe est plus chargée que l'autre, et c'est la
     * plus chargée qui décide.
     */
    private Constraint balancedTeacherWorkload(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getTeacher() != null
                        && l.getTimeSlot().getDay() != null && l.getGroupIndex() != 2)
                .groupBy(Lesson::getTeacher, ConstraintCollectors.toList())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((prof, seances) -> BALANCED_TEACHER_WORKLOAD,
                                ActiveConstraintParam::getCode))
                .filter((prof, seances, p) -> concentrationDuService(prof, seances, p) > 0)
                .penalize(HardMediumSoftScore.ofSoft(1),
                        (prof, seances, p) ->
                                concentrationDuService(prof, seances, p) * p.getSoftWeight())
                .asConstraint(BALANCED_TEACHER_WORKLOAD);
    }

    /**
     * Les heures d'une matière se répartissent entre le matin et
     * l'après-midi — § III.1.
     *
     * <p>« Les heures hebdomadaires prévues pour une même matière sont réparties
     * sur les périodes du matin et de l'après-midi, quelle que soit cette
     * matière. » C'est le cadre général dont le § III.2.a — trois quarts des
     * fondamentales le matin — est le cas particulier chiffré. Le texte ne
     * demande pas une moitié de chaque côté : il interdit qu'une matière soit
     * <em>entièrement</em> massée d'un seul côté. La contrainte dit donc oui ou
     * non, et pénalise d'un point : une matière est massée ou elle ne l'est pas,
     * un degré de massement n'aurait pas de sens.
     *
     * <p><b>Deux exclusions, parce qu'on ne reproche pas l'impossible.</b> Une
     * matière d'une seule séance ne se partage pas ; une matière dont le volume
     * reste sous le seuil configuré non plus. Le seuil est en heures, deux par
     * défaut.
     *
     * <p><b>La parité de semaine n'est pas séparée ici</b>, contrairement aux
     * contraintes de continuité. L'article parle des heures « prévues pour la
     * matière » — le programme de la semaine type, pas la semaine telle qu'un
     * élève la vit. Une matière dont la séance du matin est de quinzaine reste
     * une matière répartie.
     */
    private Constraint mainSubjectBalancedDistribution(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() != 2
                        && l.getTimeSlot().getPeriod() != null)
                .groupBy(l -> l.getStudentClassName() + "|" + l.getSubjectCode(),
                        ConstraintCollectors.toList())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((cle, seances) -> MAIN_SUBJECT_BALANCED_DISTRIBUTION,
                                ActiveConstraintParam::getCode))
                .filter((cle, seances, p) -> matiereMasseeSurUnePeriode(seances, p))
                .penalize(HardMediumSoftScore.ofSoft(1),
                        (cle, seances, p) -> p.getSoftWeight())
                .asConstraint(MAIN_SUBJECT_BALANCED_DISTRIBUTION);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bloc 4 — Contraintes personnalisées définies en DSL par l'établissement
    //
    // Même mécanisme que le Bloc 2, mais le fait joint porte un prédicat compilé
    // plutôt qu'un simple entier : la condition métier est décrite en base, pas
    // écrite ici. Les trois flux ci-dessous suffisent donc à couvrir n'importe
    // quelle règle exprimable dans le catalogue DSL, sans jamais recompiler ni
    // redéployer le provider.
    //
    // Un tenant ne voit que ses propres règles : les CompiledConstraint injectés
    // dans la solution proviennent de CustomConstraintLoader, filtré par tenantId.
    // ══════════════════════════════════════════════════════════════════════════

    /** Règles de portée LESSON qui interdisent : chaque séance correspondante est pénalisée. */
    private Constraint customLessonPenalty(ConstraintFactory f, DslSeverity severity, String name) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null)
                .join(CompiledConstraint.class,
                        Joiners.filtering((l, c) -> !c.isAggregate()
                                && c.getSeverity() == severity
                                && c.getAction() == DslAction.PENALIZE
                                && c.matches(l)))
                .penalize(severity.unitScore(), (l, c) -> c.penaltyFor(1))
                .asConstraint(name);
    }

    /**
     * Règles de portée LESSON qui favorisent. Récompenser sert à exprimer une
     * préférence positive (« de préférence le sport en fin de journée ») sans
     * pénaliser tout le reste, ce qui produirait un score constamment dégradé et
     * illisible pour l'utilisateur.
     */
    private Constraint customLessonReward(ConstraintFactory f, DslSeverity severity, String name) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null)
                .join(CompiledConstraint.class,
                        Joiners.filtering((l, c) -> !c.isAggregate()
                                && c.getSeverity() == severity
                                && c.getAction() == DslAction.REWARD
                                && c.matches(l)))
                .reward(severity.unitScore(), (l, c) -> c.penaltyFor(1))
                .asConstraint(name);
    }

    /**
     * Règles à seuil (portées TEACHER_DAY, CLASS_DAY, ROOM_DAY, *_WEEK).
     *
     * Le groupBy est porté par Timefold, donc incrémental : ajouter une règle de
     * plafond horaire à une école ne coûte pas un parcours complet des séances à
     * chaque mouvement du solveur.
     *
     * groupIndex == 2 est exclu comme dans le Bloc 2 : les deux moitiés d'un
     * demi-groupe occupent le même créneau, les compter toutes les deux
     * doublerait artificiellement le cumul horaire.
     */
    private Constraint customAggregateLimit(ConstraintFactory f, DslSeverity severity, String name) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() != 2)
                .join(CompiledConstraint.class,
                        Joiners.filtering((l, c) -> c.isAggregate()
                                && c.getSeverity() == severity
                                && c.getAction() == DslAction.PENALIZE
                                && c.matches(l)))
                .groupBy((l, c) -> c,
                         (l, c) -> c.groupKey(l),
                         ConstraintCollectors.sum((l, c) -> c.metricUnits(l)))
                .filter((c, key, total) -> c.breaches(total))
                .penalize(severity.unitScore(),
                        (c, key, total) -> c.penaltyFor(c.overshoot(total)))
                .asConstraint(name);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean isFridayOrSaturday(DayOfWeek day) {
        return day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
    }

    /** Deux heures, exprimées dans l'unité du solveur : des créneaux de 30 min. */
    private static final int DEUX_HEURES_EN_CRENEAUX = 4;

    /** Lundi à jeudi — les « quatre premiers jours » du § II.4. */
    private static boolean estDansLesQuatrePremiersJours(DayOfWeek jour) {
        return jour != null && jour.getValue() <= DayOfWeek.THURSDAY.getValue();
    }

    /** Deux jours qui se suivent dans la semaine scolaire, § III.2.c. */
    private static boolean joursConsecutifs(DayOfWeek a, DayOfWeek b) {
        return Math.abs(a.getValue() - b.getValue()) == 1;
    }

    /** Séance placée sur un créneau dont on connaît le jour et l'heure de début. */
    private static boolean estDatee(Lesson l) {
        return l.getTimeSlot() != null
                && l.getTimeSlot().getDay() != null
                && l.getTimeSlot().getStartTime() != null;
    }

    /**
     * Écart en minutes entre les débuts de deux séances de la même semaine.
     *
     * <p>Le repère est la minute de la semaine — numéro de jour puis heure de
     * début — plutôt qu'une différence de jours corrigée par une différence
     * d'heures : la seconde forme se trompe de signe dès que la séance du jour
     * suivant commence plus tôt, ce qui est exactement le cas que le § III.2.b
     * cherche à interdire.
     */
    private static int minutesEntre(Lesson a, Lesson b) {
        return Math.abs(minuteDeLaSemaine(a) - minuteDeLaSemaine(b));
    }

    private static int minuteDeLaSemaine(Lesson l) {
        return l.getTimeSlot().getDay().getValue() * 24 * 60
                + l.getTimeSlot().getStartTime().toSecondOfDay() / 60;
    }

    /** Séparation exigée par le § III.2.b, en minutes ; 24 h sauf réglage contraire. */
    private static int separationExigeeEnMinutes(ActiveConstraintParam p) {
        int heures = p.getInt("minHoursBetweenSessions",
                p.getIntParam() > 0 ? p.getIntParam() : 24);
        return heures * 60;
    }

    /**
     * Heures manquantes, arrondies à l'heure supérieure et jamais nulles : une
     * séparation trop courte d'une demi-heure reste une violation, et une
     * pénalité de zéro la rendrait invisible.
     */
    private static int heuresManquantes(int reelles, int exigees) {
        return Math.max(1, (exigees - reelles + 59) / 60);
    }

    /** Part du volume à placer le matin, § III.2.a — trois quarts par défaut. */
    private static int quotaMatinalEnPourcent(ActiveConstraintParam p) {
        int pourcent = p.getInt("morningPercent",
                p.getIntParam() > 0 ? p.getIntParam() : 75);
        return Math.min(100, Math.max(0, pourcent));
    }

    /**
     * Créneaux matinaux récompensés : ceux qui sont placés le matin, plafonnés
     * au quota. Le plafond est arrondi à l'entier inférieur — récompenser
     * au-delà de ce que le quota autorise reviendrait à préférer une matière
     * entièrement matinale, que le § III.2.a ne demande pas.
     */
    private static int creditMatinal(java.util.List<Lesson> seances, int pourcent) {
        int total = volumeEnCreneaux(seances);
        int matin = seances.stream()
                .filter(l -> l.getTimeSlot().getPeriod() == DayPeriod.MORNING)
                .mapToInt(Lesson::getDurationSlots)
                .sum();
        return Math.min(matin, total * pourcent / 100);
    }

    /** Nombre de niveaux exigé par le § II.5 — deux sauf réglage contraire. */
    private static int niveauxExiges(ActiveConstraintParam p) {
        return p.getInt("minLevels", p.getIntParam() > 0 ? p.getIntParam() : 2);
    }

    /**
     * La plus longue suite de séances qui s'enchaînent réellement, toutes
     * semaines confondues.
     *
     * <p>Évaluée deux fois — pour la semaine impaire, pour la semaine paire — et
     * on retient la pire. Une séance de quinzaine ne prolonge une suite que les
     * semaines où elle a lieu ; la compter toujours inventerait un enchaînement
     * que l'élève ne vit jamais, et ne jamais la compter en masquerait un vrai.
     */
    private static int plusLongueSuite(java.util.List<Lesson> seances) {
        return Math.max(
                suiteDeLaSemaine(seances, WeekParity.ODD),
                suiteDeLaSemaine(seances, WeekParity.EVEN));
    }

    /**
     * La plus longue suite parmi les seules séances qui ont lieu cette
     * semaine-là.
     *
     * <p>Deux séances s'enchaînent quand la seconde commence là où la première
     * s'arrête. Le {@code <=} plutôt qu'un {@code ==} traite un chevauchement
     * comme une suite : deux séances qui se superposent sont déjà une violation
     * dure ({@code classConflict}), et rompre la suite à cet endroit ferait
     * disparaître la seconde violation derrière la première.
     */
    private static int suiteDeLaSemaine(java.util.List<Lesson> seances, WeekParity semaine) {
        java.util.List<Lesson> deLaSemaine = seancesDeLaSemaine(seances, semaine).stream()
                .sorted(java.util.Comparator.comparingInt(l -> l.getTimeSlot().getOrderIndex()))
                .toList();
        if (deLaSemaine.isEmpty()) {
            return 0;
        }
        int record = 1;
        int courante = 1;
        for (int i = 1; i < deLaSemaine.size(); i++) {
            Lesson precedente = deLaSemaine.get(i - 1);
            Lesson suivante   = deLaSemaine.get(i);
            int finPrecedente = precedente.getTimeSlot().getOrderIndex()
                    + Math.max(1, precedente.getDurationSlots());
            courante = suivante.getTimeSlot().getOrderIndex() <= finPrecedente ? courante + 1 : 1;
            record = Math.max(record, courante);
        }
        return record;
    }

    /**
     * Les créneaux de service qui dépassent la part équitable d'une journée,
     * toutes journées cumulées — zéro quand le service est réparti.
     */
    private static int concentrationDuService(TeacherRef prof, java.util.List<Lesson> seances,
                                              ActiveConstraintParam p) {
        return Math.max(concentrationDeLaSemaine(prof, seances, p, WeekParity.ODD),
                concentrationDeLaSemaine(prof, seances, p, WeekParity.EVEN));
    }

    private static int concentrationDeLaSemaine(TeacherRef prof, java.util.List<Lesson> seances,
                                                ActiveConstraintParam p, WeekParity semaine) {
        java.util.Map<DayOfWeek, Integer> parJour = new java.util.EnumMap<>(DayOfWeek.class);
        for (Lesson l : seancesDeLaSemaine(seances, semaine)) {
            parJour.merge(l.getTimeSlot().getDay(), Math.max(1, l.getDurationSlots()), Integer::sum);
        }
        if (parJour.isEmpty()) {
            return 0;
        }
        int service = parJour.values().stream().mapToInt(Integer::intValue).sum();
        int plafond = plafondEquitable(prof, p, service);
        return parJour.values().stream()
                .mapToInt(charge -> Math.max(0, charge - plafond))
                .sum();
    }

    /**
     * Ce qu'une journée peut porter sans qu'on parle de concentration : la part
     * équitable du service, arrondie au créneau supérieur, et jamais sous le
     * plancher de deux heures du § II.2.
     */
    private static int plafondEquitable(TeacherRef prof, ActiveConstraintParam p, int service) {
        int jours = Math.max(1, joursTravailles(p) - joursIndisponibles(prof));
        int partEquitable = (service + jours - 1) / jours;
        return Math.max(PLANCHER_JOURNALIER, partEquitable);
    }

    /** Deux heures en créneaux de 30 min — le plancher journalier du § II.2. */
    private static final int PLANCHER_JOURNALIER = 4;

    /** Jours ouvrés de l'établissement — six sauf réglage contraire. */
    private static int joursTravailles(ActiveConstraintParam p) {
        return Math.max(1, p.getInt("workingDays", p.getIntParam() > 0 ? p.getIntParam() : 6));
    }

    private static int joursIndisponibles(TeacherRef prof) {
        return prof.getUnavailableDays() == null ? 0 : prof.getUnavailableDays().size();
    }

    /**
     * Vrai quand toutes les heures d'une matière tombent du même côté de la
     * journée — § III.1 — alors que son découpage permettrait de les répartir.
     */
    private static boolean matiereMasseeSurUnePeriode(java.util.List<Lesson> seances,
                                                      ActiveConstraintParam p) {
        if (seances.size() < 2 || volumeEnCreneaux(seances) < volumeRepartissable(p)) {
            return false;
        }
        boolean matin = seances.stream()
                .anyMatch(l -> l.getTimeSlot().getPeriod() == DayPeriod.MORNING);
        boolean apresMidi = seances.stream()
                .anyMatch(l -> l.getTimeSlot().getPeriod() == DayPeriod.AFTERNOON);
        return !(matin && apresMidi);
    }

    /** Volume à partir duquel une matière doit se répartir, en créneaux — 2 h par défaut. */
    private static int volumeRepartissable(ActiveConstraintParam p) {
        int heures = p.getInt("weeklyHours", p.getIntParam() > 0 ? p.getIntParam() : 2);
        return Math.max(2, heures * 2);
    }

    /**
     * Les séances qui ont effectivement lieu la semaine donnée.
     *
     * <p>Une séance sans parité, ou marquée {@code ALL}, a lieu toutes les
     * semaines ; une quinzaine ne compte que dans la sienne. C'est le seul point
     * où trois contraintes — consécutivité, heures creuses, plancher de
     * demi-journée — lisent la parité, et il vaut mieux qu'elles la lisent au
     * même endroit : elles ont chacune eu leur version du même angle mort.
     */
    private static java.util.List<Lesson> seancesDeLaSemaine(
            java.util.List<Lesson> seances, WeekParity semaine) {
        return seances.stream()
                .filter(l -> l.getWeekParity() == null
                        || l.getWeekParity() == WeekParity.ALL
                        || l.getWeekParity() == semaine)
                .toList();
    }
}
