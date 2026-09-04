package tn.wtm.school.planning.solver.builder;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.org.entity.Pattern;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.enums.WeekParity;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.TeacherRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Translates TeachingAssignment + Pattern data from the organisation-module
 * into Lesson objects for Timefold (PLANNING_MODULE.md §7).
 *
 * Rules:
 *   • Each PatternDetail (or SubjectSessionType) whose isSplit=true generates
 *     two paired Lessons (group A groupIndex=1, group B groupIndex=2).
 *   • Each PatternDetail with isSplit=false generates ceil(duration) full-class Lessons.
 *   • Paired lessons share the same pairedLessonId so the solver can enforce simultaneity.
 */
@Component
@RequiredArgsConstructor
public class LessonGenerator {

    private final TeachingAssignmentRepository assignmentRepository;

    /** Global counters — reset per generate() call for deterministic IDs. */
    private final AtomicLong lessonIdCounter = new AtomicLong(0);
    private final AtomicLong pairIdCounter   = new AtomicLong(0);

    public List<Lesson> generate(String tenantId, Long academicYearId) {
        lessonIdCounter.set(0);
        pairIdCounter.set(0);

        List<TeachingAssignment> assignments =
                assignmentRepository.findAllForGeneration(academicYearId);

        List<Lesson> lessons = new ArrayList<>();
        for (TeachingAssignment ta : assignments) {
            if (!tenantId.equals(ta.getTenantId())) {
                continue; // tenant isolation — never expose another tenant's data
            }
            lessons.addAll(generateFor(ta));
        }
        applyDemiGroupRotation(lessons);
        return lessons;
    }

    // ── Rotation des demi-groupes (Contraintes curriculum tunisien §2.3) ────────
    //
    // Règle : quand une classe étudie une matière en demi-groupe, les deux moitiés
    // ne doivent pas être inoccupées — le Groupe A fait la matière X pendant que le
    // Groupe B fait une AUTRE matière Y au même créneau, puis inversion au créneau
    // suivant. Cas emblématique : TP Physique ↔ TP SVT (priorisé), mais la règle
    // s'applique à toute séance demi-groupe (SVT, Physique, Techno, Info…).
    //
    // Implémentation : après génération, par classe, on ré-apparie en croisé les
    // paires demi-groupe de matières DIFFÉRENTES et de MÊME durée :
    //   créneau 1 → X-A (idx 1) ↔ Y-B (idx 2)   — même pairedLessonId
    //   créneau 2 → Y-A (idx 1) ↔ X-B (idx 2)   — autre pairedLessonId
    // pairedDemiGroupSameSlot force le partage du créneau ; roomConflict force deux
    // salles distinctes (ex. labo physique + labo sciences) simultanément.
    // Les paires sans partenaire de matière différente restent auto-appariées
    // (les deux moitiés font la même matière dans deux salles).

    private static final Set<String> PHYSICS_CODES = Set.of("PHY", "PHYSIQUE", "PH");
    private static final Set<String> SCIENCE_CODES = Set.of("SCI", "SVT", "SC");

    private void applyDemiGroupRotation(List<Lesson> lessons) {
        // Regroupe toutes les paires demi-groupe par classe
        Map<String, List<Lesson>> demiByClass = new LinkedHashMap<>();
        for (Lesson l : lessons) {
            if (l.getGroupIndex() == 0 || l.getPairedLessonId() == null) {
                continue;
            }
            demiByClass.computeIfAbsent(l.getStudentClassName(), k -> new ArrayList<>()).add(l);
        }

        for (List<Lesson> classLessons : demiByClass.values()) {
            List<Lesson[]> pairs = new ArrayList<>(toDemiGroupPairs(classLessons));
            boolean[] used = new boolean[pairs.size()];

            // Passe 1 : prioriser l'appariement Physique ↔ SVT (mandat du doc §2.3)
            crossPairPass(pairs, used, true);
            // Passe 2 : généraliser à toute autre paire de matières différentes
            crossPairPass(pairs, used, false);
            // Les paires restantes gardent leur auto-appariement initial (X-A + X-B)
        }
    }

    /**
     * Greedy cross-pairing of demi-group pairs of DIFFERENT subjects and SAME duration.
     * When {@code physicsScienceOnly} is true, only Physics↔Science pairs are matched.
     */
    private void crossPairPass(List<Lesson[]> pairs, boolean[] used, boolean physicsScienceOnly) {
        for (int i = 0; i < pairs.size(); i++) {
            if (used[i]) continue;
            Lesson[] x = pairs.get(i);
            for (int j = i + 1; j < pairs.size(); j++) {
                if (used[j]) continue;
                Lesson[] y = pairs.get(j);
                if (!canCrossPair(x, y, physicsScienceOnly)) continue;
                used[i] = used[j] = true;
                crossPair(x, y);
                break;
            }
        }
    }

    private boolean canCrossPair(Lesson[] x, Lesson[] y, boolean physicsScienceOnly) {
        String xc = subjectOf(x), yc = subjectOf(y);
        if (xc.equals(yc)) return false;                                  // matières différentes
        if (x[0].getDurationSlots() != y[0].getDurationSlots()) return false; // même durée
        if (physicsScienceOnly) {
            return (PHYSICS_CODES.contains(xc) && SCIENCE_CODES.contains(yc))
                    || (SCIENCE_CODES.contains(xc) && PHYSICS_CODES.contains(yc));
        }
        return true;
    }

    private static String subjectOf(Lesson[] pair) {
        String c = pair[0].getSubjectCode();
        return c == null ? "" : c.toUpperCase();
    }

    /** Re-pairs two demi-group pairs cross-wise: X-A↔Y-B on one slot, Y-A↔X-B on another. */
    private void crossPair(Lesson[] x, Lesson[] y) {
        long slot1 = pairIdCounter.incrementAndGet();
        x[0].setGroupIndex(1); x[0].setPairedLessonId(slot1); // X-A
        y[1].setGroupIndex(2); y[1].setPairedLessonId(slot1); // Y-B

        long slot2 = pairIdCounter.incrementAndGet();
        y[0].setGroupIndex(1); y[0].setPairedLessonId(slot2); // Y-A
        x[1].setGroupIndex(2); x[1].setPairedLessonId(slot2); // X-B
    }

    /** Reconstructs [groupA, groupB] pairs from already-generated demi-group lessons. */
    private List<Lesson[]> toDemiGroupPairs(List<Lesson> lessons) {
        Map<Long, Lesson[]> byPair = new LinkedHashMap<>();
        for (Lesson l : lessons) {
            if (l.getPairedLessonId() == null) {
                continue;
            }
            Lesson[] pair = byPair.computeIfAbsent(l.getPairedLessonId(), k -> new Lesson[2]);
            if (l.getGroupIndex() == 1) {
                pair[0] = l;
            } else {
                pair[1] = l;
            }
        }
        return byPair.values().stream()
                .filter(p -> p[0] != null && p[1] != null)
                .toList();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private List<Lesson> generateFor(TeachingAssignment ta) {
        List<PatternDetail> details = findRelevantDetails(ta);
        if (details.isEmpty()) {
            return generateFromSessionType(ta);
        }
        List<Lesson> lessons = new ArrayList<>();
        for (PatternDetail detail : details) {
            lessons.addAll(generateFromDetail(ta, detail));
        }
        return lessons;
    }

    /**
     * Selects the PatternDetails that apply to this TeachingAssignment.
     * Strategy: find the most specific pattern (matching schoolYear), then filter
     * details by the TA's session type. Fall back to the default (schoolYear=null) pattern.
     */
    private List<PatternDetail> findRelevantDetails(TeachingAssignment ta) {
        // Un pattern désactivé (matière non enseignée dans cet établissement) est ignoré —
        // la liste vide retournée plus bas fait retomber generateFor() sur
        // generateFromSessionType(), le même filet de sécurité que "aucun pattern".
        List<Pattern> patterns = ta.getSubjectLevel().getPatterns().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActive()))
                .toList();
        if (patterns.isEmpty()) {
            return List.of();
        }
        Long schoolYearId = ta.getSchoolYear().getIdAnnee();
        tn.wtm.school.org.enums.SessionType orgType = ta.getSubjectSessionType().getType();

        // Prefer year-specific pattern; fall back to default (schoolYear=null)
        Pattern chosen = patterns.stream()
                .filter(p -> p.getSchoolYear() != null
                        && p.getSchoolYear().getIdAnnee().equals(schoolYearId))
                .max(Comparator.comparingLong(p -> p.getIdPattern()))
                .orElseGet(() -> patterns.stream()
                        .filter(p -> p.getSchoolYear() == null)
                        .max(Comparator.comparingLong(p -> p.getIdPattern()))
                        .orElse(null));

        if (chosen == null || chosen.getPatternDetails() == null) {
            return List.of();
        }

        return chosen.getPatternDetails().stream()
                .filter(d -> d.getType() == orgType)
                .filter(d -> d.getWeekParity() == null
                        || d.getWeekParity() == WeekParity.ALL
                        || d.getWeekParity() == WeekParity.BIWEEKLY)
                .sorted(Comparator.comparingInt(PatternDetail::getSessionOrder))
                .toList();
    }

    /** Generate lessons from a single PatternDetail. */
    private List<Lesson> generateFromDetail(TeachingAssignment ta, PatternDetail detail) {
        boolean isSplit = Boolean.TRUE.equals(detail.getIsSplit());
        int durationSlots = hoursToSlots(detail.getDuration());

        if (isSplit) {
            return generateDemiGroup(ta, detail, durationSlots);
        } else {
            return generateFullClass(ta, detail, durationSlots);
        }
    }

    /** Fallback when no pattern details are found: use SubjectSessionType directly. */
    private List<Lesson> generateFromSessionType(TeachingAssignment ta) {
        SubjectSessionType sst = ta.getSubjectSessionType();
        boolean isSplit = Boolean.TRUE.equals(sst.getRequiresSplit());
        int durationSlots = hoursToSlots(sst.getDuration());

        if (isSplit) {
            return generateDemiGroup(ta, null, durationSlots);
        } else {
            return generateFullClass(ta, null, durationSlots);
        }
    }

    /**
     * One full-class session = ONE Lesson spanning {@code durationSlots} consecutive
     * 30-min slots (1h → 2 slots, 1h30 → 3, 2h → 4).
     */
    private List<Lesson> generateFullClass(TeachingAssignment ta,
                                            PatternDetail detail, int durationSlots) {
        return List.of(build(ta, detail, 0, null, durationSlots));
    }

    /**
     * One demi-group session = ONE pair (group A + group B), each a Lesson spanning
     * {@code durationSlots} slots and sharing a pairedLessonId so they land on the
     * same time slot. Cross-subject re-pairing happens later in applyDemiGroupRotation.
     */
    private List<Lesson> generateDemiGroup(TeachingAssignment ta,
                                            PatternDetail detail, int durationSlots) {
        long pairId = pairIdCounter.incrementAndGet();
        return List.of(
                build(ta, detail, 1, pairId, durationSlots),  // group A
                build(ta, detail, 2, pairId, durationSlots));  // group B
    }

    private Lesson build(TeachingAssignment ta,
                          PatternDetail detail,
                          int groupIndex,
                          Long pairedLessonId,
                          int durationSlots) {
        var subject = ta.getSubjectLevel().getSubject();
        var classGroup = ta.getClassGroup();
        var sst = ta.getSubjectSessionType();

        tn.wtm.school.org.enums.SessionType orgSessionType =
                detail != null ? detail.getType() : sst.getType();
        tn.wtm.school.org.enums.RoomType orgRoomType =
                detail != null ? detail.getRequiredRoomType() : null;

        SessionType sessionType    = mapSessionType(orgSessionType);
        RoomType    requiredRoom   = mapRoomType(orgRoomType, subject);
        boolean     requiresSpecial = requiredRoom != RoomType.NORMALE;

        return Lesson.builder()
                .id(lessonIdCounter.incrementAndGet())
                .teachingAssignmentId(ta.getIdTeachingAssignment())
                .subjectCode(subject.getCodeMatiere())
                .subjectName(subject.getLibMatiere())
                .mainSubject(Boolean.TRUE.equals(subject.getEstPrincipale()))
                .studentClassName(classGroup.getCode())
                .studentClassLevel(levelCodeOf(classGroup))
                .studentClassSpeciality(specialityOf(classGroup))
                .classStudentCount(classGroup.getNbEleve() != null ? classGroup.getNbEleve() : 0)
                .sessionType(sessionType)
                .requiredRoomType(requiredRoom)
                .requiresSpecialRoom(requiresSpecial)
                .groupIndex(groupIndex)
                .pairedLessonId(pairedLessonId)
                .durationSlots(durationSlots)
                .teacher(toTeacherRef(ta.getTeacher()))
                .build();
    }

    // ── attributs exposés au DSL de contraintes personnalisees ───────────────

    /**
     * Code du niveau de la classe, exposé au DSL sous {@code class.level}.
     * On préfère le code au libellé : il est stable et c'est ce que le catalogue
     * DSL propose comme valeurs d'exemple.
     */
    private static String levelCodeOf(tn.wtm.school.org.entity.ClassGroup classGroup) {
        var level = classGroup.getLevel();
        if (level == null) {
            return null;
        }
        return level.getCode() != null ? level.getCode() : level.getNom();
    }

    private static String specialityOf(tn.wtm.school.org.entity.ClassGroup classGroup) {
        return classGroup.getCodeSpecialite() == null
                ? null
                : classGroup.getCodeSpecialite().name();
    }

    // ── ref builder ──────────────────────────────────────────────────────────

    private static TeacherRef toTeacherRef(Teacher t) {
        if (t == null) return null;
        String name = ((t.getPrenom() != null ? t.getPrenom() : "") + " "
                + (t.getNom() != null ? t.getNom() : "")).trim();
        int maxHours = t.getMaxHeuresJour() != null ? t.getMaxHeuresJour() : 6;
        return TeacherRef.builder()
                .id(t.getIdEnseignant())
                .code(t.getCodeEnseignant())
                .name(name)
                .maxHoursPerDay(maxHours)
                .build();
    }

    // ── enum mappers ──────────────────────────────────────────────────────────

    static SessionType mapSessionType(tn.wtm.school.org.enums.SessionType orgType) {
        if (orgType == null) return SessionType.COURS;
        return switch (orgType) {
            case COURSE -> SessionType.COURS;
            case TD     -> SessionType.TD;
            case TP, LAB -> SessionType.TP;
            case SPORT  -> SessionType.SPORT;
            case EXAM   -> SessionType.COURS;
        };
    }

    static RoomType mapRoomType(tn.wtm.school.org.enums.RoomType orgType,
                                 tn.wtm.school.org.entity.Subject subject) {
        // 1. Le pattern detail est prioritaire (séance spécifique, ex: TP en labo)
        if (orgType != null) {
            return toSolverRoomType(orgType);
        }
        // 2. Type de salle par défaut déclaré sur la matière
        if (subject.getTypeSalleRequise() != null
                && subject.getTypeSalleRequise() != tn.wtm.school.org.enums.RoomType.NORMALE) {
            return toSolverRoomType(subject.getTypeSalleRequise());
        }
        // 3. Fallback legacy : anciens booléens de la matière
        if (Boolean.TRUE.equals(subject.getNecessiteLab())) {
            return isScienceSubject(subject) ? RoomType.LABSCIENCE : RoomType.LABPHYSIQUE;
        }
        if (Boolean.TRUE.equals(subject.getNecessiteSport())) return RoomType.SALLESPORT;
        return RoomType.NORMALE;
    }

    private static RoomType toSolverRoomType(tn.wtm.school.org.enums.RoomType orgType) {
        return switch (orgType) {
            case LABSCIENCE      -> RoomType.LABSCIENCE;
            case LABPHYSIQUE     -> RoomType.LABPHYSIQUE;
            case LABINFORMATIQUE -> RoomType.LABINFORMATIQUE;
            case SALLESPORT      -> RoomType.SALLESPORT;
            case SALLEDESSIN     -> RoomType.SALLEDESSIN;
            case SALLEMUSIQUE    -> RoomType.SALLEMUSIQUE;
            case AMPHI           -> RoomType.AMPHI;
            case BIBLIOTHEQUE    -> RoomType.BIBLIOTHEQUE;
            default              -> RoomType.NORMALE;
        };
    }

    private static boolean isScienceSubject(tn.wtm.school.org.entity.Subject subject) {
        String code = subject == null || subject.getCodeMatiere() == null
                ? "" : subject.getCodeMatiere().toUpperCase();
        return SCIENCE_CODES.contains(code);
    }

    /** 30-min slots covered by a session of {@code hours} hours (1h→2, 1h30→3, 2h→4). */
    private static int hoursToSlots(Double hours) {
        if (hours == null || hours <= 0) return 1;
        return Math.max(1, (int) Math.round(hours / 0.5));
    }
}
