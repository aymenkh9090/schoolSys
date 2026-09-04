package tn.wtm.school.planning.constraints.dsl.catalog;

import org.springframework.stereotype.Component;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.planning.constraints.dsl.enums.DslFieldType;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.enums.SessionType;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Catalogue fermé des champs utilisables dans une contrainte personnalisée.
 *
 * <p>C'est la frontière de sécurité du DSL. Tout ce qu'une école — ou l'assistant
 * IA — peut interroger est ici, et rien d'autre : un nom de champ absent de cette
 * table fait échouer la validation avant toute exécution. Il n'y a donc aucun
 * chemin par lequel une règle stockée en base atteindrait du code arbitraire.</p>
 *
 * <p>Le catalogue est aussi exposé en lecture par l'API ({@code GET
 * /api/planning/constraints/dsl/schema}) : l'interface React s'en sert pour
 * construire ses listes déroulantes, et le prompt du LLM en est dérivé — le
 * modèle voit exactement les champs qui existent, ce qui l'empêche d'en inventer.</p>
 */
@Component
public class DslFieldCatalog {

    private final Map<String, DslField> fields = new LinkedHashMap<>();

    public DslFieldCatalog() {
        // ── matière ───────────────────────────────────────────────────────────
        register("subject.code", DslFieldType.STRING, "Code de la matière",
                Lesson::getSubjectCode, List.of(), "MATH");
        register("subject.name", DslFieldType.STRING, "Nom de la matière",
                Lesson::getSubjectName, List.of(), "Mathématiques");
        register("subject.main", DslFieldType.BOOLEAN, "Matière principale",
                Lesson::isMainSubject, List.of("true", "false"), "true");

        // ── classe ────────────────────────────────────────────────────────────
        register("class.name", DslFieldType.STRING, "Code de la classe",
                Lesson::getStudentClassName, List.of(), "4A");
        register("class.level", DslFieldType.STRING, "Niveau de la classe",
                Lesson::getStudentClassLevel, List.of(), "TERMINALE");
        register("class.speciality", DslFieldType.STRING, "Spécialité de la classe",
                Lesson::getStudentClassSpeciality, List.of(), "SCIENCES");
        register("class.size", DslFieldType.NUMBER, "Effectif de la classe",
                Lesson::getClassStudentCount, List.of(), "30");

        // ── enseignant ────────────────────────────────────────────────────────
        register("teacher.code", DslFieldType.STRING, "Code de l'enseignant",
                l -> l.getTeacher() == null ? null : l.getTeacher().getCode(), List.of(), "ENS08");
        register("teacher.name", DslFieldType.STRING, "Nom de l'enseignant",
                l -> l.getTeacher() == null ? null : l.getTeacher().getName(), List.of(), "Ahmed Ben Ali");
        register("teacher.id", DslFieldType.NUMBER, "Identifiant de l'enseignant",
                l -> l.getTeacher() == null ? null : l.getTeacher().getId(), List.of(), "12");

        // ── salle ─────────────────────────────────────────────────────────────
        register("room.code", DslFieldType.STRING, "Code de la salle",
                l -> l.getRoom() == null ? null : l.getRoom().getCode(), List.of(), "B12");
        register("room.type", DslFieldType.ENUM, "Type de salle",
                l -> l.getRoom() == null ? null : l.getRoom().getType(),
                names(RoomType.values()), "LABPHYSIQUE");

        // ── position dans la semaine ──────────────────────────────────────────
        register("day", DslFieldType.DAY, "Jour de la semaine",
                l -> l.getTimeSlot() == null ? null : l.getTimeSlot().getDay(),
                names(DayOfWeek.values()), "FRIDAY");
        register("period", DslFieldType.ENUM, "Demi-journée",
                l -> l.getTimeSlot() == null ? null : l.getTimeSlot().getPeriod(),
                names(DayPeriod.values()), "AFTERNOON");
        register("startTime", DslFieldType.TIME, "Heure de début de la séance",
                Lesson::getStartTime, List.of(), "15:00");
        register("endTime", DslFieldType.TIME, "Heure de fin de la séance",
                Lesson::getEndTime, List.of(), "17:00");
        register("slotOrder", DslFieldType.NUMBER, "Rang du créneau dans la journée",
                l -> l.getTimeSlot() == null ? null : l.getTimeSlot().getOrderIndex(), List.of(), "5");

        // ── nature de la séance ───────────────────────────────────────────────
        register("sessionType", DslFieldType.ENUM, "Type de séance",
                Lesson::getSessionType, names(SessionType.values()), "TP");
        register("durationHours", DslFieldType.NUMBER, "Durée de la séance en heures",
                l -> l.getDurationSlots() / 2.0d, List.of(), "1.5");
        register("groupIndex", DslFieldType.NUMBER, "Demi-groupe (0 = classe entière)",
                Lesson::getGroupIndex, List.of("0", "1", "2"), "0");
    }

    private void register(String name,
                          DslFieldType type,
                          String label,
                          Function<Lesson, Object> extractor,
                          List<String> allowedValues,
                          String example) {
        fields.put(name, new DslField(name, type, label, extractor, allowedValues, example));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    /** Le champ demandé, vide si le nom n'existe pas. Aucune tolérance : la casse compte. */
    public Optional<DslField> find(String name) {
        return Optional.ofNullable(name).map(String::trim).map(fields::get);
    }

    public boolean exists(String name) {
        return find(name).isPresent();
    }

    public Collection<DslField> all() {
        return fields.values();
    }

    /** Noms de champs connus, dans l'ordre du catalogue — utilisé dans les messages d'erreur. */
    public List<String> names() {
        return List.copyOf(fields.keySet());
    }
}
