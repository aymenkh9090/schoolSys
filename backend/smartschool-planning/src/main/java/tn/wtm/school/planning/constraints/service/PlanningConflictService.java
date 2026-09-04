package tn.wtm.school.planning.constraints.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.planning.constraints.dsl.CompiledConstraint;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslCompiler;
import tn.wtm.school.planning.constraints.dsl.ConstraintDslValidator;
import tn.wtm.school.planning.constraints.dsl.DslValidationResult;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dto.response.DslAnalysisResponse;
import tn.wtm.school.planning.solver.builder.TimetableProblemBuilder;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Confronte une règle candidate aux données réelles de l'établissement, <b>avant</b>
 * de lancer l'optimisation.
 *
 * <p>Le besoin est concret : une règle peut être parfaitement valide et pourtant
 * irréalisable. « M. Ahmed n'est disponible que de 8h à 12h » est syntaxiquement
 * impeccable, mais si ses affectations exigent 7 heures de cours par jour, aucun
 * solveur au monde ne trouvera de solution. Sans cette analyse, l'utilisateur
 * attend la fin d'une génération de trente secondes pour découvrir un score dur
 * négatif qu'il ne sait pas interpréter.</p>
 *
 * <p>L'analyse est <b>déterministe et faite en Java</b> : elle compte des créneaux
 * et des heures. L'assistant IA n'y participe pas — il se contente de la déclencher
 * et d'en restituer le résultat en français. Un diagnostic d'infaisabilité produit
 * par un modèle de langage n'aurait aucune valeur ; celui-ci est démontrable.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PlanningConflictService {

    /** Nombre d'exemples de séances renvoyés — assez pour convaincre, pas pour noyer. */
    private static final int MAX_EXAMPLES = 8;

    /** Au-delà, la liste de conflits devient illisible ; le verdict suffit. */
    private static final int MAX_CONFLICTS = 15;

    private final TimetableProblemBuilder  problemBuilder;
    private final ConstraintDslValidator   validator;
    private final ConstraintDslCompiler    compiler;

    /**
     * Valide la règle, estime son impact et cherche les blocages.
     *
     * <p>Quand {@code schoolYearId} est absent, seule la validation est faite :
     * il n'y a pas de données auxquelles confronter la règle, et inventer un
     * verdict d'impact serait pire que de n'en donner aucun.</p>
     */
    public DslAnalysisResponse analyze(ConstraintDsl dsl,
                                       String tenantId,
                                       Long schoolYearId,
                                       Long constraintProfileId) {

        DslValidationResult validation = validator.validate(dsl);

        DslAnalysisResponse.DslAnalysisResponseBuilder response = DslAnalysisResponse.builder()
                .valid(validation.isValid())
                .errors(validation.getErrors())
                .warnings(validation.getWarnings())
                .summary(validation.getSummary());

        if (!validation.isValid()) {
            return response
                    .feasible(false)
                    .conflicts(List.of())
                    .verdict("La règle n'est pas valide : " + validation.errorMessage())
                    .build();
        }

        if (schoolYearId == null) {
            return response
                    .feasible(true)
                    .conflicts(List.of())
                    .verdict("Règle valide. Précisez une année scolaire pour mesurer son impact "
                            + "sur les affectations existantes.")
                    .build();
        }

        TimetableSolution problem;
        try {
            problem = problemBuilder.build(tenantId, schoolYearId, constraintProfileId);
        } catch (RuntimeException e) {
            // Pas de créneaux, pas de salles, pas d'affectations : la règle reste
            // valide, on le dit, et on n'invente pas d'analyse sur des données absentes.
            log.info("Analyse d'impact impossible pour le tenant {} : {}", tenantId, e.getMessage());
            return response
                    .feasible(true)
                    .conflicts(List.of())
                    .verdict("Règle valide. Impact non mesurable : " + e.getMessage() + ".")
                    .build();
        }

        CompiledConstraint rule = compiler.compile(dsl, null, "CANDIDATE", "Règle candidate");
        List<Lesson>      lessons   = problem.getLessons();
        List<TimeSlotRef> timeSlots = problem.getTimeSlots();

        List<Lesson> matched = matchingLessons(rule, lessons, timeSlots);

        List<DslAnalysisResponse.Conflict> conflicts = new ArrayList<>();
        if (rule.getSeverity() == DslSeverity.HARD && rule.getAction() == DslAction.PENALIZE) {
            if (rule.isAggregate()) {
                conflicts.addAll(detectWorkloadOverloads(rule, lessons, timeSlots));
            } else {
                conflicts.addAll(detectUnplaceableLessons(rule, lessons, timeSlots));
            }
        }

        boolean feasible = conflicts.isEmpty();
        return response
                .matchedLessons(matched.size())
                .totalLessons(lessons.size())
                .examples(matched.stream()
                        .limit(MAX_EXAMPLES)
                        .map(PlanningConflictService::describe)
                        .toList())
                .feasible(feasible)
                .conflicts(conflicts)
                .verdict(buildVerdict(rule, matched.size(), lessons.size(), conflicts))
                .build();
    }

    // ── impact ────────────────────────────────────────────────────────────────

    /**
     * Séances susceptibles d'être concernées par la règle.
     *
     * <p>Les séances ne sont pas encore placées : une règle qui parle d'horaire ne
     * peut donc pas être testée telle quelle. On considère qu'une séance est
     * concernée s'il <b>existe au moins un créneau</b> qui la ferait correspondre.
     * C'est une majoration honnête de l'impact — « voici ce que la règle pourrait
     * toucher » — et non une prédiction du planning final, que personne ne connaît
     * avant de l'avoir optimisé.</p>
     */
    private List<Lesson> matchingLessons(CompiledConstraint rule,
                                         List<Lesson> lessons,
                                         List<TimeSlotRef> timeSlots) {
        List<Lesson> matched = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (anySlotMatches(rule, lesson, timeSlots)) {
                matched.add(lesson);
            }
        }
        return matched;
    }

    private boolean anySlotMatches(CompiledConstraint rule, Lesson lesson, List<TimeSlotRef> timeSlots) {
        TimeSlotRef original = lesson.getTimeSlot();
        try {
            for (TimeSlotRef slot : timeSlots) {
                if (!fits(lesson, slot)) {
                    continue;
                }
                lesson.setTimeSlot(slot);
                if (rule.matches(lesson)) {
                    return true;
                }
            }
            return false;
        } finally {
            // Le problème est reconstruit à chaque appel, mais on restaure quand
            // même : une analyse ne doit jamais laisser d'effet de bord derrière elle.
            lesson.setTimeSlot(original);
        }
    }

    // ── conflits : séance sans créneau admissible ─────────────────────────────

    /**
     * Cherche les séances que la règle interdit partout.
     *
     * <p>Une règle HARD qui exclut <i>tous</i> les créneaux d'une séance rend
     * l'emploi du temps infaisable de manière certaine — ce n'est pas une
     * heuristique, c'est une preuve par énumération.</p>
     */
    private List<DslAnalysisResponse.Conflict> detectUnplaceableLessons(CompiledConstraint rule,
                                                                        List<Lesson> lessons,
                                                                        List<TimeSlotRef> timeSlots) {
        List<DslAnalysisResponse.Conflict> conflicts = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (conflicts.size() >= MAX_CONFLICTS) {
                break;
            }
            int admissible = 0;
            int structurallyPossible = 0;

            TimeSlotRef original = lesson.getTimeSlot();
            try {
                for (TimeSlotRef slot : timeSlots) {
                    if (!fits(lesson, slot)) {
                        continue;
                    }
                    structurallyPossible++;
                    lesson.setTimeSlot(slot);
                    if (!rule.matches(lesson)) {
                        admissible++;
                    }
                }
            } finally {
                lesson.setTimeSlot(original);
            }

            // structurallyPossible == 0 : la séance est déjà impossible à placer
            // pour une raison indépendante de la règle (durée trop longue pour
            // tous les blocs). Ce n'est pas ce conflit-ci qu'on signale.
            if (structurallyPossible > 0 && admissible == 0) {
                conflicts.add(DslAnalysisResponse.Conflict.builder()
                        .type("NO_ADMISSIBLE_SLOT")
                        .subject(describe(lesson))
                        .detail("Cette séance n'a plus aucun créneau autorisé : la règle exclut "
                                + "les " + structurallyPossible + " créneaux possibles.")
                        .build());
            }
        }
        return conflicts;
    }

    // ── conflits : charge obligatoire supérieure au plafond ───────────────────

    /**
     * Compare la charge <b>incompressible</b> de chaque enseignant / classe / salle
     * au plafond demandé par la règle.
     *
     * <p>Les séances sont imposées par les affectations : leur volume ne dépend pas
     * du solveur. Si un enseignant doit assurer 24 heures et que la règle plafonne
     * à 3 heures par jour sur 5 jours ouvrés, la contrainte est mathématiquement
     * insatisfiable — et il vaut mieux le dire tout de suite.</p>
     */
    private List<DslAnalysisResponse.Conflict> detectWorkloadOverloads(CompiledConstraint rule,
                                                                       List<Lesson> lessons,
                                                                       List<TimeSlotRef> timeSlots) {
        DslOperator operator = rule.getAggregateOperator();
        // Seul un plafond peut rendre le problème infaisable. Un plancher
        // (« au moins 2 heures ») se traduit par un score dégradé, pas par un blocage.
        if (operator != DslOperator.GREATER_THAN && operator != DslOperator.GREATER_THAN_OR_EQUAL) {
            return List.of();
        }

        int workingDays = countWorkingDays(timeSlots);
        int capacityPerGroup = rule.getScope() == DslScope.TEACHER_WEEK || rule.getScope() == DslScope.CLASS_WEEK
                ? rule.getLimitUnits()
                : rule.getLimitUnits() * Math.max(1, workingDays);

        // GREATER_THAN_OR_EQUAL interdit d'atteindre le seuil : la capacité réelle
        // est donc d'une unité de moins.
        if (operator == DslOperator.GREATER_THAN_OR_EQUAL) {
            capacityPerGroup = Math.max(0, capacityPerGroup - 1);
        }

        Map<String, Integer> requiredByGroup = new LinkedHashMap<>();
        Map<String, String>  labelByGroup    = new LinkedHashMap<>();

        for (Lesson lesson : lessons) {
            // Les deux moitiés d'un demi-groupe occupent le même créneau : les
            // compter toutes les deux gonflerait la charge sans raison.
            // anySlotMatches, et non rule.matches : la séance n'est pas encore
            // placée, on veut savoir si elle entre dans le PÉRIMÈTRE de la règle,
            // pas si elle la viole à un horaire qu'aucun solveur n'a encore choisi.
            if (lesson.getGroupIndex() == 2 || !anySlotMatches(rule, lesson, timeSlots)) {
                continue;
            }
            String key = entityKey(rule.getScope(), lesson);
            if (key == null) {
                continue;
            }
            requiredByGroup.merge(key, rule.metricUnits(lesson), Integer::sum);
            labelByGroup.putIfAbsent(key, entityLabel(rule.getScope(), lesson));
        }

        List<DslAnalysisResponse.Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requiredByGroup.entrySet()) {
            if (conflicts.size() >= MAX_CONFLICTS) {
                break;
            }
            int required = entry.getValue();
            if (required > capacityPerGroup) {
                conflicts.add(DslAnalysisResponse.Conflict.builder()
                        .type("WORKLOAD_EXCEEDS_LIMIT")
                        .subject(labelByGroup.get(entry.getKey()))
                        .detail(formatWorkload(rule, required, capacityPerGroup, workingDays))
                        .build());
            }
        }
        return conflicts;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Un créneau convient si la séance tient entièrement dans son bloc de travail. */
    private static boolean fits(Lesson lesson, TimeSlotRef slot) {
        return Math.max(1, lesson.getDurationSlots()) <= slot.getMaxDurationSlots();
    }

    private static int countWorkingDays(List<TimeSlotRef> timeSlots) {
        Set<DayOfWeek> days = timeSlots.stream()
                .map(TimeSlotRef::getDay)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return days.size();
    }

    private static String entityKey(DslScope scope, Lesson lesson) {
        return switch (scope) {
            case TEACHER_DAY, TEACHER_WEEK ->
                    lesson.getTeacher() == null ? null : String.valueOf(lesson.getTeacher().getId());
            case CLASS_DAY, CLASS_WEEK -> lesson.getStudentClassName();
            // La salle n'est pas encore attribuée : aucune charge par salle n'est
            // connue avant l'optimisation, donc rien à vérifier ici.
            case ROOM_DAY, LESSON -> null;
        };
    }

    private static String entityLabel(DslScope scope, Lesson lesson) {
        return switch (scope) {
            case TEACHER_DAY, TEACHER_WEEK -> lesson.getTeacher() == null
                    ? "Enseignant inconnu"
                    : "Enseignant " + lesson.getTeacher().getName();
            case CLASS_DAY, CLASS_WEEK -> "Classe " + lesson.getStudentClassName();
            case ROOM_DAY, LESSON -> "—";
        };
    }

    private String formatWorkload(CompiledConstraint rule, int required, int capacity, int workingDays) {
        String unit = rule.getMetric() == null ? "unités" : rule.getMetric().getUnitLabel();
        double divisor = rule.getMetric() == null ? 1 : rule.getMetric().unitsPerValue();
        String scopeLabel = rule.getScope() == DslScope.TEACHER_WEEK || rule.getScope() == DslScope.CLASS_WEEK
                ? "sur la semaine"
                : "sur " + workingDays + " jour(s) ouvré(s)";

        return "Charge obligatoire de " + trim(required / divisor) + " " + unit
                + " " + scopeLabel + ", pour un maximum autorisé de "
                + trim(capacity / divisor) + " " + unit
                + ". La règle est incompatible avec les affectations actuelles.";
    }

    private String buildVerdict(CompiledConstraint rule,
                                int matched,
                                int total,
                                List<DslAnalysisResponse.Conflict> conflicts) {
        if (!conflicts.isEmpty()) {
            return "Conflit détecté : " + conflicts.size() + " blocage(s). "
                    + conflicts.get(0).getSubject() + " — " + conflicts.get(0).getDetail()
                    + " Assouplissez la règle (sévérité SOFT, ou seuil plus élevé) "
                    + "ou ajustez les affectations avant de l'enregistrer.";
        }
        if (matched == 0) {
            return "Règle valide, mais aucune séance de l'année ne correspond à ses conditions : "
                    + "vérifiez les valeurs saisies (code matière, niveau de classe…).";
        }
        return "Règle valide et applicable : " + matched + " séance(s) concernée(s) sur "
                + total + ". Aucun blocage détecté avec les affectations actuelles.";
    }

    private static String describe(Lesson lesson) {
        StringBuilder sb = new StringBuilder();
        sb.append(lesson.getSubjectName() != null ? lesson.getSubjectName() : lesson.getSubjectCode());
        if (lesson.getStudentClassName() != null) {
            sb.append(" · ").append(lesson.getStudentClassName());
        }
        if (lesson.getTeacher() != null && lesson.getTeacher().getName() != null) {
            sb.append(" · ").append(lesson.getTeacher().getName());
        }
        if (lesson.getGroupIndex() > 0) {
            sb.append(" (groupe ").append(lesson.getGroupIndex() == 1 ? "A" : "B").append(')');
        }
        return sb.toString();
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
