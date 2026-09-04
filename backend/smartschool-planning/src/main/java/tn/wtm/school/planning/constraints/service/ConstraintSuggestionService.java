package tn.wtm.school.planning.constraints.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.planning.constraints.dsl.enums.DslAction;
import tn.wtm.school.planning.constraints.dsl.enums.DslAggregateMetric;
import tn.wtm.school.planning.constraints.dsl.enums.DslOperator;
import tn.wtm.school.planning.constraints.dsl.enums.DslScope;
import tn.wtm.school.planning.constraints.dsl.enums.DslSeverity;
import tn.wtm.school.planning.constraints.dsl.model.ConstraintDsl;
import tn.wtm.school.planning.constraints.dsl.model.DslAggregate;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSuggestionResponse;
import tn.wtm.school.planning.solver.domain.TimetableSession;
import tn.wtm.school.planning.solver.entity.TimetableJob;
import tn.wtm.school.planning.solver.repository.TimetableJobRepository;
import tn.wtm.school.planning.solver.repository.TimetableSessionRepository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Détecte, dans un emploi du temps déjà généré, les situations qui mériteraient
 * une règle — et rédige la règle correspondante.
 *
 * <p>C'est ici qu'est produite la matière de la fonctionnalité « proposer
 * automatiquement une contrainte ». Le repérage est entièrement statistique et
 * fait en Java : on compte des heures consécutives, des journées chargées, des
 * semaines sans repos. L'assistant IA ne découvre rien — il met en forme, pose
 * la question à l'utilisateur, et n'enregistre qu'après confirmation.</p>
 *
 * <p>Le partage des rôles est délibéré. Un modèle de langage à qui l'on demande
 * « que remarques-tu dans cet emploi du temps ? » produira toujours une réponse
 * plausible, y compris sur des données parfaitement saines. Les seuils ci-dessous
 * sont franchis ou ne le sont pas, et le nombre de cas est vérifiable.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConstraintSuggestionService extends TenantService {

    /** En deçà, le phénomène est anecdotique et ne justifie pas une règle. */
    private static final int MIN_OCCURRENCES = 3;

    /** Seuil d'alerte sur les heures consécutives d'un enseignant. */
    private static final int CONSECUTIVE_HOURS_ALERT = 4;

    /** Seuil d'alerte sur la charge journalière d'un enseignant. */
    private static final int DAILY_HOURS_ALERT = 7;

    /** Nombre de cas concrets cités par proposition. */
    private static final int MAX_EVIDENCE = 5;

    private final TimetableJobRepository     jobRepository;
    private final TimetableSessionRepository sessionRepository;

    /**
     * Analyse le dernier emploi du temps exploitable de l'année, ou celui du job
     * demandé. Renvoie une liste vide quand rien ne dépasse les seuils — ne rien
     * proposer est une réponse valide, et préférable à une suggestion de complaisance.
     */
    public ConstraintSuggestionResponse suggest(Long schoolYearId, Long jobId) {
        String tenantId = currentTenant();
        TimetableJob job = resolveJob(tenantId, schoolYearId, jobId);

        List<TimetableSession> sessions =
                sessionRepository.findByJobIdAndTenantId(job.getIdTimetableJob(), tenantId);

        List<ConstraintSuggestionResponse.Suggestion> suggestions = new ArrayList<>();
        addConsecutiveHoursSuggestion(sessions, suggestions);
        addDailyLoadSuggestion(sessions, suggestions);
        addWeeklyRestSuggestion(sessions, suggestions);

        suggestions.sort(Comparator.comparingInt(
                ConstraintSuggestionResponse.Suggestion::getOccurrences).reversed());

        return ConstraintSuggestionResponse.builder()
                .jobId(job.getIdTimetableJob())
                .suggestions(suggestions)
                .build();
    }

    // ── 1. séries d'heures consécutives ───────────────────────────────────────

    private void addConsecutiveHoursSuggestion(List<TimetableSession> sessions,
                                               List<ConstraintSuggestionResponse.Suggestion> out) {
        Map<String, List<TimetableSession>> byTeacherDay = groupByTeacherAndDay(sessions);

        List<String> evidence = new ArrayList<>();
        int occurrences = 0;
        int longestRun  = 0;

        for (Map.Entry<String, List<TimetableSession>> entry : byTeacherDay.entrySet()) {
            double run = longestConsecutiveHours(entry.getValue());
            if (run >= CONSECUTIVE_HOURS_ALERT) {
                occurrences++;
                longestRun = Math.max(longestRun, (int) Math.ceil(run));
                if (evidence.size() < MAX_EVIDENCE) {
                    evidence.add(label(entry.getValue()) + " : " + trim(run) + " h d'affilée");
                }
            }
        }

        if (occurrences < MIN_OCCURRENCES) {
            return;
        }

        // Le seuil proposé est celui immédiatement inférieur à ce qui est observé :
        // proposer 3 h quand la pratique tourne autour de 5 h ferait rejeter la
        // règle par le solveur autant que par le directeur.
        int proposedLimit = Math.max(2, CONSECUTIVE_HOURS_ALERT - 1);

        out.add(ConstraintSuggestionResponse.Suggestion.builder()
                .code("MAX_HEURES_CONSECUTIVES_ENSEIGNANT")
                .title("Limiter les heures consécutives des enseignants")
                .observation(occurrences + " situation(s) où un enseignant enchaîne "
                        + CONSECUTIVE_HOURS_ALERT + " heures ou plus sans interruption "
                        + "(record observé : " + longestRun + " h).")
                .rationale("Des séries trop longues dégradent la qualité d'enseignement et sont "
                        + "une source fréquente de réclamations. Limiter la charge journalière "
                        + "répartit mécaniquement les séances sur la semaine.")
                .evidence(evidence)
                .occurrences(occurrences)
                .dsl(aggregateRule(DslScope.TEACHER_DAY, proposedLimit, DslSeverity.MEDIUM, 50))
                .build());
    }

    // ── 2. journées trop chargées ─────────────────────────────────────────────

    private void addDailyLoadSuggestion(List<TimetableSession> sessions,
                                        List<ConstraintSuggestionResponse.Suggestion> out) {
        Map<String, List<TimetableSession>> byTeacherDay = groupByTeacherAndDay(sessions);

        List<String> evidence = new ArrayList<>();
        int occurrences = 0;

        for (List<TimetableSession> daySessions : byTeacherDay.values()) {
            double hours = totalHours(daySessions);
            if (hours >= DAILY_HOURS_ALERT) {
                occurrences++;
                if (evidence.size() < MAX_EVIDENCE) {
                    evidence.add(label(daySessions) + " : " + trim(hours) + " h dans la journée");
                }
            }
        }

        if (occurrences < MIN_OCCURRENCES) {
            return;
        }

        out.add(ConstraintSuggestionResponse.Suggestion.builder()
                .code("PLAFOND_JOURNALIER_ENSEIGNANT")
                .title("Plafonner la journée d'un enseignant à 6 heures")
                .observation(occurrences + " journée(s) d'enseignant à " + DAILY_HOURS_ALERT
                        + " heures ou plus.")
                .rationale("Un plafond journalier explicite empêche le solveur de concentrer "
                        + "la charge d'un enseignant sur un ou deux jours pour satisfaire "
                        + "d'autres préférences.")
                .evidence(evidence)
                .occurrences(occurrences)
                .dsl(aggregateRule(DslScope.TEACHER_DAY, 6, DslSeverity.HARD, 100))
                .build());
    }

    // ── 3. semaines sans jour de repos ────────────────────────────────────────

    private void addWeeklyRestSuggestion(List<TimetableSession> sessions,
                                         List<ConstraintSuggestionResponse.Suggestion> out) {
        Map<String, Set<DayOfWeek>> daysByTeacher = new LinkedHashMap<>();
        Map<String, String>         nameByTeacher = new LinkedHashMap<>();

        for (TimetableSession s : sessions) {
            if (s.getTeacherCode() == null || s.getDay() == null) {
                continue;
            }
            daysByTeacher.computeIfAbsent(s.getTeacherCode(), k -> new TreeSet<>()).add(s.getDay());
            nameByTeacher.putIfAbsent(s.getTeacherCode(),
                    s.getTeacherName() != null ? s.getTeacherName() : s.getTeacherCode());
        }

        int workingDays = (int) sessions.stream()
                .map(TimetableSession::getDay)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        if (workingDays < 2) {
            return;
        }

        List<String> evidence = daysByTeacher.entrySet().stream()
                .filter(e -> e.getValue().size() >= workingDays)
                .limit(MAX_EVIDENCE)
                .map(e -> nameByTeacher.get(e.getKey()) + " : cours tous les " + workingDays + " jours ouvrés")
                .toList();

        long occurrences = daysByTeacher.values().stream()
                .filter(days -> days.size() >= workingDays)
                .count();

        if (occurrences < MIN_OCCURRENCES) {
            return;
        }

        // La règle proposée passe par un plafond hebdomadaire de jours travaillés,
        // exprimable dans le DSL. Le catalogue prédéfini offre déjà
        // TEACHER_WEEKLY_REST_DAY : on renvoie donc l'utilisateur vers ce réglage,
        // plutôt que de dupliquer la même intention sous deux formes concurrentes.
        out.add(ConstraintSuggestionResponse.Suggestion.builder()
                .code("TEACHER_WEEKLY_REST_DAY")
                .title("Activer le jour de repos hebdomadaire")
                .observation(occurrences + " enseignant(s) travaillent les " + workingDays
                        + " jours ouvrés de la semaine.")
                .rationale("Cette règle existe déjà au catalogue des contraintes configurables : "
                        + "il suffit de l'activer dans le profil, sans créer de règle personnalisée.")
                .evidence(evidence)
                .occurrences((int) occurrences)
                .dsl(null)
                .build());
    }

    // ── helpers d'analyse ─────────────────────────────────────────────────────

    private Map<String, List<TimetableSession>> groupByTeacherAndDay(List<TimetableSession> sessions) {
        return sessions.stream()
                .filter(s -> s.getTeacherCode() != null && s.getDay() != null
                        && s.getStartTime() != null && s.getEndTime() != null)
                // groupIndex 2 exclu : les deux moitiés d'un demi-groupe partagent
                // le même créneau, les compter deux fois doublerait la charge.
                .filter(s -> s.getGroupIndex() != 2)
                .collect(Collectors.groupingBy(
                        s -> s.getTeacherCode() + "|" + s.getDay(),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    /** Plus longue série de séances qui s'enchaînent sans interruption, en heures. */
    private double longestConsecutiveHours(List<TimetableSession> daySessions) {
        List<TimetableSession> sorted = daySessions.stream()
                .sorted(Comparator.comparing(TimetableSession::getStartTime))
                .toList();

        double longest = 0;
        double current = 0;
        java.time.LocalTime previousEnd = null;

        for (TimetableSession s : sorted) {
            double hours = Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0;
            // « Consécutif » = la séance commence exactement quand la précédente
            // finit. Une pause, même de 30 minutes, casse la série.
            current = (previousEnd != null && previousEnd.equals(s.getStartTime()))
                    ? current + hours
                    : hours;
            longest = Math.max(longest, current);
            previousEnd = s.getEndTime();
        }
        return longest;
    }

    private double totalHours(List<TimetableSession> daySessions) {
        return daySessions.stream()
                .mapToDouble(s -> Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0)
                .sum();
    }

    private static String label(List<TimetableSession> daySessions) {
        TimetableSession first = daySessions.get(0);
        String teacher = first.getTeacherName() != null ? first.getTeacherName() : first.getTeacherCode();
        return teacher + " — " + dayLabel(first.getDay());
    }

    private ConstraintDsl aggregateRule(DslScope scope, int maxHours, DslSeverity severity, int weight) {
        return ConstraintDsl.builder()
                .scope(scope)
                .conditions(List.of())
                .aggregate(DslAggregate.builder()
                        .metric(DslAggregateMetric.TOTAL_HOURS)
                        .operator(DslOperator.GREATER_THAN)
                        .value((double) maxHours)
                        .build())
                .action(DslAction.PENALIZE)
                .severity(severity)
                .weight(weight)
                .build();
    }

    private TimetableJob resolveJob(String tenantId, Long schoolYearId, Long jobId) {
        if (jobId != null) {
            return jobRepository.findByIdTimetableJobAndTenantId(jobId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Job de generation introuvable avec l'ID : " + jobId));
        }
        List<TimetableJob> jobs = schoolYearId == null
                ? jobRepository.findByTenantIdOrderByIdTimetableJobDesc(tenantId)
                : jobRepository.findByTenantIdAndAcademicYearIdOrderByIdTimetableJobDesc(tenantId, schoolYearId);

        return jobs.stream()
                .filter(j -> j.getStatus() != null && j.getStatus().hasTimetable())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun emploi du temps généré à analyser pour cette année scolaire."));
    }

    private static String dayLabel(DayOfWeek day) {
        if (day == null) {
            return "?";
        }
        return switch (day) {
            case MONDAY    -> "lundi";
            case TUESDAY   -> "mardi";
            case WEDNESDAY -> "mercredi";
            case THURSDAY  -> "jeudi";
            case FRIDAY    -> "vendredi";
            case SATURDAY  -> "samedi";
            case SUNDAY    -> "dimanche";
        };
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
