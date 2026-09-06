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

                // ── Bloc 3 : contraintes dynamiques MEDIUM / SOFT ─────────────
                theoryPracticeSeparation(f),
                balancedMorningAfternoon(f),
                teacherWeeklyRestDay(f),
                avoidSubjectConcentrationSameDay(f),

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

    // Un élève ne doit pas avoir de trou dans son emploi du temps au sein d'une même
    // demi-journée (matin OU après-midi). On regroupe toutes les leçons par (classe, jour, période)
    // et on vérifie sur la liste triée qu'il n'y a pas de créneau libre entre deux leçons
    // successives. Multi-slots : une leçon de N slots couvre [startOrder, startOrder+N-1].
    private Constraint noStudentIdleGaps(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() != 2)
                .groupBy(
                        Lesson::getStudentClassName,
                        l -> l.getTimeSlot().getDay() + "|" + l.getTimeSlot().getPeriod(),
                        ConstraintCollectors.toList())
                .filter((cls, dayPeriod, lessons) -> hasGapInSortedLessons(lessons))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NO_STUDENT_IDLE_GAPS);
    }

    private boolean hasGapInSortedLessons(java.util.List<Lesson> lessons) {
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

    // Maximum N séances consécutives de la même matière dans la même journée pour une classe
    private Constraint maxConsecutiveSameSessions(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTimeSlot() != null && l.getGroupIndex() == 0)
                .groupBy(
                        l -> l.getStudentClassName() + "|" + l.getSubjectCode()
                                + "|" + l.getTimeSlot().getDay(),
                        ConstraintCollectors.count())
                .join(ActiveConstraintParam.class,
                        Joiners.equal((key, cnt) -> MAX_TWO_CONSECUTIVE_SESSIONS,
                                ActiveConstraintParam::getCode))
                .filter((key, cnt, p) -> p.getIntParam() > 0 && cnt > p.getIntParam())
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (key, cnt, p) -> cnt - p.getIntParam())
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

    // Équilibrage matin/après-midi pour les enseignants (Ministry §II-5)
    private Constraint balancedMorningAfternoon(ConstraintFactory f) {
        return f.forEach(Lesson.class)
                .filter(l -> l.getTeacher() != null && l.getTimeSlot() != null)
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
}
