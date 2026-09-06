package tn.wtm.school.planning.solver.validation;

import org.springframework.stereotype.Component;
import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.RoomType;
import tn.wtm.school.planning.solver.ref.ExpectedCourse;
import tn.wtm.school.planning.solver.ref.RoomRef;
import tn.wtm.school.planning.solver.ref.TeacherRef;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ce que les données disent <em>avant</em> qu'on lance le solveur.
 *
 * <h2>Le problème qu'il résout</h2>
 *
 * {@code startGeneration} construisait le problème et lançait le solveur sans
 * rien vérifier. Un défaut de données ne se découvrait donc qu'à la fin — un
 * volume horaire qui ne correspond plus au pattern refusé par
 * {@code VOLUME_HORAIRE} après trois minutes de calcul — ou <b>jamais</b> :
 * une matière sans affectation d'enseignant n'engendre aucune séance, et une
 * validation qui lit les séances ne peut rien dire de celles qui n'existent pas.
 *
 * <p>Tout ce qui est contrôlé ici est <b>décidable par le calcul</b>, sans rien
 * placer. Ce n'est pas un pré-solveur ni une heuristique : chaque constat est
 * une preuve. « Cette matière n'a aucun enseignant » ne devient pas faux parce
 * que le solveur cherche mieux, et « il faut 69 séances d'EPS pour 3 enseignants
 * » est une soustraction, pas une exploration.
 *
 * <h2>Ce qu'il ne fait pas</h2>
 *
 * Il ne prédit pas la faisabilité. Un problème qui passe ces six contrôles peut
 * parfaitement rester infaisable : les impossibilités croisées — deux matières
 * qui se disputent la seule salle informatique aux seules heures où leur
 * enseignant est là — ne se voient qu'en cherchant. Ce validateur écarte les
 * impossibilités <em>évidentes</em>, celles qu'il est absurde de faire découvrir
 * par un calcul de trois minutes. Passer le contrôle n'est pas une promesse.
 *
 * <p>Il partage son vocabulaire avec {@link TimetableBusinessValidator} —
 * {@link ValidationFinding}, {@link ValidationReport} — pour que l'interface
 * n'ait qu'une façon d'afficher un constat, qu'il vienne d'avant ou d'après.
 */
@Component
public class PreGenerationValidator {

    static final String MATIERE_SANS_ENSEIGNANT = "MATIERE_SANS_ENSEIGNANT";
    static final String VOLUME_INCOHERENT       = "VOLUME_INCOHERENT";
    static final String SEANCE_HORS_PROGRAMME   = "SEANCE_HORS_PROGRAMME";
    static final String TYPE_DE_SALLE_ABSENT    = "TYPE_DE_SALLE_ABSENT";
    static final String AUCUNE_SALLE_ASSEZ_GRANDE = "AUCUNE_SALLE_ASSEZ_GRANDE";
    static final String SERVICE_IMPOSSIBLE      = "SERVICE_IMPOSSIBLE";
    static final String SEMAINE_TROP_COURTE     = "SEMAINE_TROP_COURTE";
    static final String PROGRAMME_INCONNU       = "PROGRAMME_INCONNU";

    /** Une heure de cours occupe deux créneaux de trente minutes. */
    private static final int CRENEAUX_PAR_HEURE = 2;

    /** Nombre de couples cités dans un constat qui en regroupe plusieurs. */
    private static final int EXEMPLES = 5;

    public ValidationReport valider(TimetableSolution probleme) {
        if (probleme == null) {
            return ValidationReport.conforme();
        }
        List<ValidationFinding> constats = new ArrayList<>();

        programmeEtSeances(probleme, constats);
        sallesInexistantes(probleme, constats);
        servicesImpossibles(probleme, constats);
        semainesTropCourtes(probleme, constats);

        return new ValidationReport(constats);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Le programme attendu face aux séances engendrées
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Trois constats d'un même rapprochement, et ils disent trois choses
     * différentes :
     *
     * <ul>
     *   <li><b>Matière sans enseignant</b> — le programme la prévoit, aucune
     *       séance n'a été engendrée. C'est le trou que la validation d'après
     *       coup ne pouvait pas voir.</li>
     *   <li><b>Volume incohérent</b> — les séances existent mais leur somme
     *       s'écarte du volume déclaré. Les deux viennent de sources
     *       différentes : les séances du pattern, le volume de
     *       {@code niveaux_matieres.heures_semaine}. Modifier l'un sans l'autre
     *       produit exactement ce constat, et le job serait de toute façon
     *       refusé à la fin — autant le dire tout de suite.</li>
     *   <li><b>Séance hors programme</b> — une affectation d'enseignement porte
     *       sur une matière que le niveau ne déclare pas. La séance sera placée
     *       et personne ne pourra dire si son volume est le bon.</li>
     * </ul>
     */
    private void programmeEtSeances(TimetableSolution probleme, List<ValidationFinding> constats) {
        List<ExpectedCourse> attendu = probleme.getExpectedCurriculum();
        if (attendu == null || attendu.isEmpty()) {
            constats.add(ValidationFinding.avertissement(PROGRAMME_INCONNU, "",
                    "aucune matière n'est déclarée pour les classes de cette année : "
                            + "la conformité au programme ne pourra pas être vérifiée"));
            return;
        }

        Map<String, Integer> placeParCouple = probleme.getLessons().stream()
                .filter(l -> l.getGroupIndex() != 2)
                .collect(Collectors.groupingBy(
                        l -> l.getStudentClassName() + " / " + l.getSubjectCode(),
                        LinkedHashMap::new,
                        Collectors.summingInt(Lesson::getDurationSlots)));

        Set<String> couplesAttendus = new HashSet<>();

        for (ExpectedCourse cours : attendu) {
            couplesAttendus.add(cours.couple());
            Integer engendre = placeParCouple.get(cours.couple());

            if (engendre == null) {
                constats.add(ValidationFinding.bloquant(MATIERE_SANS_ENSEIGNANT, cours.designation(),
                        "le programme prévoit " + enHeures(cours.weeklySlots())
                                + " mais aucune séance n'est engendrée : il manque une affectation "
                                + "d'enseignant. Si la matière n'est pas enseignée, mettre son "
                                + "volume hebdomadaire à zéro."));
            } else if (engendre != cours.weeklySlots()) {
                constats.add(ValidationFinding.bloquant(VOLUME_INCOHERENT, cours.designation(),
                        "le programme déclare " + enHeures(cours.weeklySlots())
                                + " et le découpage des séances en produit " + enHeures(engendre)
                                + " : le pattern et le volume hebdomadaire ne disent pas la même chose."));
            }
        }

        List<String> horsProgramme = placeParCouple.keySet().stream()
                .filter(couple -> !couplesAttendus.contains(couple))
                .sorted()
                .toList();
        if (!horsProgramme.isEmpty()) {
            constats.add(ValidationFinding.avertissement(SEANCE_HORS_PROGRAMME, extrait(horsProgramme),
                    horsProgramme.size() + " couple(s) classe / matière ont des séances sans figurer "
                            + "au programme de leur niveau : leur volume ne pourra pas être vérifié."));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Les salles qui n'existent pas
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Deux impossibilités que le solveur ne peut que subir : une séance qui
     * exige un type de salle dont l'établissement ne possède aucun exemplaire,
     * et une classe qu'aucune salle ne peut contenir.
     *
     * <p>{@code TimetableBusinessValidator} contrôle déjà la salle
     * <em>attribuée</em>. La différence est décisive : là, on constate qu'aucune
     * attribution correcte n'était possible, et on le dit avant de chercher.
     */
    private void sallesInexistantes(TimetableSolution probleme, List<ValidationFinding> constats) {
        List<RoomRef> salles = probleme.getRooms();
        if (salles == null || salles.isEmpty()) {
            return; // le constructeur du problème refuse déjà ce cas
        }

        Set<RoomType> typesManquants = new LinkedHashSet<>();
        Map<RoomType, List<String>> seancesParType = new LinkedHashMap<>();
        List<String> tropGrandes = new ArrayList<>();
        int capaciteMax = salles.stream().mapToInt(RoomRef::getCapacity).max().orElse(0);

        for (Lesson l : probleme.getLessons()) {
            if (l.isRequiresSpecialRoom() && l.getRequiredRoomType() != null
                    && salles.stream().noneMatch(s -> s.canAccommodate(l.getRequiredRoomType()))) {
                typesManquants.add(l.getRequiredRoomType());
                seancesParType.computeIfAbsent(l.getRequiredRoomType(), t -> new ArrayList<>())
                        .add(designation(l));
            }
            if (effectif(l) > capaciteMax) {
                tropGrandes.add(designation(l) + " (" + effectif(l) + " élèves)");
            }
        }

        for (RoomType type : typesManquants) {
            List<String> seances = seancesParType.get(type);
            constats.add(ValidationFinding.bloquant(TYPE_DE_SALLE_ABSENT, "salles de type " + type,
                    seances.size() + " séance(s) exigent ce type de salle et l'établissement n'en "
                            + "déclare aucune : " + extrait(seances)));
        }

        if (!tropGrandes.isEmpty()) {
            constats.add(ValidationFinding.bloquant(AUCUNE_SALLE_ASSEZ_GRANDE, extrait(tropGrandes),
                    "la plus grande salle déclarée accueille " + capaciteMax + " élèves : "
                            + tropGrandes.size() + " séance(s) n'y tiennent pas."));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Les services qui ne tiennent pas dans une semaine
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Le service demandé à un enseignant comparé à ce qu'une semaine lui
     * permet — jours où il est disponible, multipliés par son plafond
     * journalier.
     *
     * <p>C'est une borne supérieure généreuse : elle ignore les salles, les
     * classes et les créneaux réellement libres. Un dépassement est donc une
     * <b>preuve</b> d'impossibilité, jamais une conjecture. C'est le contrôle qui
     * répond en une seconde au cas emblématique — trois professeurs d'EPS pour
     * vingt-trois classes à trois séances — que le système ne savait dire
     * qu'après avoir cherché.
     */
    private void servicesImpossibles(TimetableSolution probleme, List<ValidationFinding> constats) {
        Set<DayOfWeek> joursOuvres = probleme.getTimeSlots().stream()
                .map(TimeSlotRef::getDay)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (joursOuvres.isEmpty()) {
            return;
        }

        Map<TeacherRef, Integer> serviceDemande = new LinkedHashMap<>();
        for (Lesson l : probleme.getLessons()) {
            if (l.getTeacher() == null) {
                continue; // ENSEIGNANT_MANQUANT est le constat de la validation d'après coup
            }
            serviceDemande.merge(l.getTeacher(), Math.max(1, l.getDurationSlots()), Integer::sum);
        }

        serviceDemande.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getName(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(entree -> {
                    TeacherRef prof = entree.getKey();
                    int demande = entree.getValue();
                    int capacite = capaciteHebdomadaire(prof, joursOuvres);
                    if (demande > capacite) {
                        constats.add(ValidationFinding.bloquant(SERVICE_IMPOSSIBLE, nomDe(prof),
                                "on lui demande " + enHeures(demande) + " par semaine alors que ses "
                                        + "jours de présence et son plafond journalier en autorisent "
                                        + enHeures(capacite) + " au plus."));
                    }
                });
    }

    /**
     * Le plafond hebdomadaire d'un enseignant, en créneaux. Un plafond
     * journalier absent ou nul est lu comme six heures — la valeur du § II.2, et
     * celle que porte {@code Teacher.maxHeuresJour} par défaut.
     */
    private static int capaciteHebdomadaire(TeacherRef prof, Set<DayOfWeek> joursOuvres) {
        long joursPresent = joursOuvres.stream().filter(j -> !prof.isUnavailableOn(j)).count();
        int plafondJour = prof.getMaxHoursPerDay() > 0 ? prof.getMaxHoursPerDay() : 6;
        return (int) joursPresent * plafondJour * CRENEAUX_PAR_HEURE;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Les classes qui ne tiennent pas dans une semaine
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Le volume d'une classe comparé au nombre de créneaux que la semaine
     * contient. Même nature de preuve que pour l'enseignant, et même cause
     * fréquente : un vendredi et un samedi sans après-midi réduisent la semaine
     * plus qu'on ne le croit en la remplissant.
     *
     * <p>Les demi-groupes sont comptés une fois : les deux moitiés d'une classe
     * dédoublée occupent le même créneau.
     */
    private void semainesTropCourtes(TimetableSolution probleme, List<ValidationFinding> constats) {
        int creneauxDeLaSemaine = probleme.getTimeSlots().size();
        if (creneauxDeLaSemaine == 0) {
            return;
        }

        Map<String, Integer> volumeParClasse = probleme.getLessons().stream()
                .filter(l -> l.getGroupIndex() != 2)
                .collect(Collectors.groupingBy(
                        Lesson::getStudentClassName,
                        LinkedHashMap::new,
                        Collectors.summingInt(l -> Math.max(1, l.getDurationSlots()))));

        volumeParClasse.forEach((classe, volume) -> {
            if (volume > creneauxDeLaSemaine) {
                constats.add(ValidationFinding.bloquant(SEMAINE_TROP_COURTE, classe,
                        "son programme demande " + enHeures(volume) + " alors que la semaine ne "
                                + "compte que " + enHeures(creneauxDeLaSemaine) + " de créneaux ouverts."));
            }
        });
    }

    // ── mise en mots ──────────────────────────────────────────────────────────

    private static int effectif(Lesson l) {
        return l.isDemiGroup() ? (l.getClassStudentCount() + 1) / 2 : l.getClassStudentCount();
    }

    private static String designation(Lesson l) {
        String matiere = l.getSubjectName() != null ? l.getSubjectName() : l.getSubjectCode();
        return l.getStudentClassName() + " / " + matiere;
    }

    private static String nomDe(TeacherRef prof) {
        if (prof.getName() != null && !prof.getName().isBlank()) {
            return prof.getName();
        }
        return prof.getCode() != null ? prof.getCode() : "enseignant " + prof.getId();
    }

    private static String extrait(List<String> elements) {
        String debut = elements.stream().distinct().limit(EXEMPLES).collect(Collectors.joining(", "));
        int reste = (int) elements.stream().distinct().count() - EXEMPLES;
        return reste > 0 ? debut + ", … et " + reste + " autre" + (reste > 1 ? "s" : "") : debut;
    }

    /** « 4 h », « 1 h 30 » — l'utilisateur ne compte pas en créneaux de trente minutes. */
    private static String enHeures(int creneaux) {
        int heures = creneaux / CRENEAUX_PAR_HEURE;
        return creneaux % CRENEAUX_PAR_HEURE == 0 ? heures + " h" : heures + " h 30";
    }
}
