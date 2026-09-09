package tn.wtm.school.planning.solver.service;

import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Où cette séance pourrait-elle aller ?
 *
 * <p>Répond par un créneau <em>réellement</em> libre — libre pour l'enseignant,
 * pour la classe, et avec une salle du bon type disponible — ou par rien du
 * tout. Il n'y a pas de troisième réponse : une suggestion de déplacement qui
 * se révèle impossible coûte plus cher au directeur que l'absence de
 * suggestion, puisqu'il l'aura essayée.
 *
 * <h4>Pourquoi ce n'est pas le modèle qui répond</h4>
 *
 * Un LLM sait rédiger « déplacez ce cours au jeudi 10 h » sans avoir aucun
 * moyen de savoir si le jeudi 10 h est libre. Le calcul, lui, le sait : il lit
 * la solution telle qu'elle est et rejoue les contraintes dures que le solveur
 * applique. Le modèle raconte ; ce code désigne.
 *
 * <h4>Les contraintes rejouées</h4>
 *
 * Les six règles dures qu'un déplacement dans le temps peut enfreindre, telles
 * qu'elles sont écrites dans {@code TimetableConstraintProvider} :
 * {@code TEACHER_CONFLICT}, {@code CLASS_CONFLICT}, {@code ROOM_CONFLICT},
 * {@code ROOM_CAPACITY}, {@code TEACHER_AVAILABILITY},
 * {@code LESSON_EXCEEDS_WORKING_BLOCK}, plus le couple
 * {@code SPECIAL_ROOM_REQUIRED} / {@code NORMAL_COURSE_NOT_IN_SPECIAL_ROOM}
 * pour le choix de la salle. Les exceptions y sont recopiées à l'identique —
 * demi-groupes distincts qui cohabitent, notamment : les oublier ferait écarter
 * des créneaux parfaitement valides.
 *
 * <h4>Ce que la réponse ne promet pas</h4>
 *
 * Que le créneau est libre, rien de plus. Le déplacement peut dégrader une
 * contrainte souple — concentration d'une matière sur la journée, quota du
 * matin, heure creuse chez l'élève. C'est assumé : le score souple est un
 * arbitrage, et l'arbitrage revient au directeur, qui voit la grille. La phrase
 * rendue à l'écran dit donc ce qui a été vérifié, jamais que le planning s'en
 * trouvera meilleur.
 */
public final class AlternativeSlotFinder {

    /** Un déplacement possible : la séance, son créneau d'arrivée, la salle qui l'accueillerait. */
    public record Relocation(Lesson lesson, TimeSlotRef slot, RoomRef room) {}

    /** Créneaux candidats, ordonnés — la proposition doit être la même à chaque appel. */
    private final List<TimeSlotRef> slots;

    /** Salles candidates, ordonnées pour la même raison. */
    private final List<RoomRef> rooms;

    /**
     * Les séances déjà posées, groupées par jour.
     *
     * <p>Un créneau candidat porte son jour : n'examiner que les séances de ce
     * jour-là évite de balayer tout l'emploi du temps pour chacun des soixante
     * créneaux de la semaine.
     */
    private final Map<DayOfWeek, List<Lesson>> placedByDay;

    public AlternativeSlotFinder(TimetableSolution solution) {
        this.slots = solution.getTimeSlots().stream()
                .filter(TimeSlotRef::isActive)   // NO_LESSON_IN_BREAK_SLOT : la pause n'est pas un créneau
                .filter(slot -> slot.getDay() != null && slot.getStartTime() != null)
                .sorted(Comparator
                        .comparing(TimeSlotRef::getDay)
                        .thenComparing(TimeSlotRef::getStartTime))
                .toList();

        this.rooms = solution.getRooms().stream()
                .sorted(Comparator.comparing(RoomRef::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        this.placedByDay = new EnumMap<>(DayOfWeek.class);
        for (Lesson lesson : solution.getLessons()) {
            if (lesson.getTimeSlot() == null || lesson.getTimeSlot().getDay() == null) {
                continue;
            }
            placedByDay.computeIfAbsent(lesson.getTimeSlot().getDay(), d -> new ArrayList<>()).add(lesson);
        }
    }

    /**
     * Le premier créneau, dans l'ordre de la semaine, où cette séance tiendrait.
     *
     * <p>Un <em>déplacement dans le temps</em>, et pas un changement de salle à
     * l'heure actuelle : le créneau d'origine est écarté. Réattribuer une salle
     * sans bouger l'horaire est le remède d'un conflit de salle, pas d'un
     * conflit d'enseignant ou de classe — et ce chercheur ignore quelle
     * contrainte l'a fait appeler. Proposer un remède qui ne corrige rien serait
     * pire que se taire.
     *
     * <p>Vide pour une séance de demi-groupe appariée : déplacer le groupe A
     * sans le groupe B enfreindrait {@code PAIRED_DEMI_GROUP_SAME_SLOT}, et
     * déplacer les deux d'un coup demande une proposition à deux séances, que le
     * contrat de sortie ne porte pas encore. Se taire est ici la seule réponse
     * honnête.
     */
    public Optional<Relocation> findFor(Lesson lesson) {
        if (lesson == null || lesson.isPaired()) {
            return Optional.empty();
        }
        for (TimeSlotRef slot : slots) {
            if (!accepts(lesson, slot)) {
                continue;
            }
            Optional<RoomRef> room = freeRoomAt(lesson, slot);
            if (room.isPresent()) {
                return Optional.of(new Relocation(lesson, slot, room.get()));
            }
        }
        return Optional.empty();
    }

    /** Le créneau lui-même convient-il, salle mise à part ? */
    private boolean accepts(Lesson lesson, TimeSlotRef slot) {
        if (slot.equals(lesson.getTimeSlot())) {
            return false;   // là où elle est déjà : ce n'est pas un déplacement
        }
        // LESSON_EXCEEDS_WORKING_BLOCK — une séance de 2 h ne tient pas dans la
        // dernière demi-heure avant la pause.
        if (lesson.getDurationSlots() > slot.getMaxDurationSlots()) {
            return false;
        }
        // TEACHER_AVAILABILITY
        if (lesson.getTeacher() != null && lesson.getTeacher().isUnavailableOn(slot.getDay())) {
            return false;
        }
        return !teacherBusy(lesson, slot) && !classBusy(lesson, slot);
    }

    /** TEACHER_CONFLICT — l'enseignant est-il déjà ailleurs sur ce créneau ? */
    private boolean teacherBusy(Lesson lesson, TimeSlotRef slot) {
        if (lesson.getTeacher() == null) {
            return false;
        }
        Long teacherId = lesson.getTeacher().getId();
        return placedOn(slot.getDay()).stream()
                .filter(other -> other != lesson)
                .filter(other -> other.getTeacher() != null
                        && Objects.equals(other.getTeacher().getId(), teacherId))
                .anyMatch(other -> lesson.wouldOverlapAt(slot, other));
        // L'exception « un prof encadre A et B en parallèle » n'a pas à être
        // rejouée ici : findFor écarte d'emblée les séances appariées.
    }

    /** CLASS_CONFLICT — la classe suit-elle déjà un cours sur ce créneau ? */
    private boolean classBusy(Lesson lesson, TimeSlotRef slot) {
        return placedOn(slot.getDay()).stream()
                .filter(other -> other != lesson)
                // Comparaison sur le nom, comme le Joiners.equal de la contrainte —
                // deux noms nuls s'y apparient aussi. Rester aussi permissif
                // qu'elle ferait proposer un créneau qu'elle pénalise.
                .filter(other -> Objects.equals(other.getStudentClassName(), lesson.getStudentClassName()))
                // Exception de la contrainte : deux demi-groupes distincts de la
                // même classe cohabitent — A en TP pendant que B est en cours.
                .filter(other -> !(lesson.getGroupIndex() > 0 && other.getGroupIndex() > 0
                        && lesson.getGroupIndex() != other.getGroupIndex()))
                .anyMatch(other -> lesson.wouldOverlapAt(slot, other));
    }

    /**
     * Une salle libre et du bon type sur ce créneau.
     *
     * <p>La salle actuelle est essayée en premier : quand elle est libre à
     * l'arrivée, la proposition ne change qu'une chose — l'horaire. Un
     * déplacement qui bouge aussi la salle sans raison est plus dur à relire, et
     * l'écart avec la grille d'origine est ce que le directeur vérifie.
     */
    private Optional<RoomRef> freeRoomAt(Lesson lesson, TimeSlotRef slot) {
        RoomRef current = lesson.getRoom();
        // Les candidates sortent toutes de `rooms`, y compris la salle actuelle :
        // celle-ci n'est qu'un ordre de préférence, jamais un laissez-passer. Une
        // salle que l'établissement a retirée reste posée sur la solution en
        // mémoire, et la proposer reviendrait à renvoyer le directeur vers une
        // salle qui n'existe plus.
        return Stream.concat(
                        rooms.stream().filter(room -> room.equals(current)),
                        rooms.stream().filter(room -> !room.equals(current)))
                .filter(room -> suits(lesson, room))
                .filter(room -> !roomBusy(room, lesson, slot))
                .findFirst();
    }

    /** ROOM_CAPACITY, SPECIAL_ROOM_REQUIRED et NORMAL_COURSE_NOT_IN_SPECIAL_ROOM. */
    private static boolean suits(Lesson lesson, RoomRef room) {
        int needed = lesson.isDemiGroup()
                ? (lesson.getClassStudentCount() + 1) / 2
                : lesson.getClassStudentCount();
        if (room.getCapacity() < needed) {
            return false;
        }
        return lesson.isRequiresSpecialRoom()
                ? room.canAccommodate(lesson.getRequiredRoomType())
                : !room.isSpecialRoom();
    }

    /** ROOM_CONFLICT — sans exception, y compris entre demi-groupes appariés. */
    private boolean roomBusy(RoomRef room, Lesson lesson, TimeSlotRef slot) {
        return placedOn(slot.getDay()).stream()
                .filter(other -> other != lesson)
                .filter(other -> other.getRoom() != null
                        && Objects.equals(other.getRoom().getId(), room.getId()))
                .anyMatch(other -> lesson.wouldOverlapAt(slot, other));
    }

    private List<Lesson> placedOn(DayOfWeek day) {
        return placedByDay.getOrDefault(day, List.of());
    }
}
