package tn.wtm.school.planning.solver.validation;

import tn.wtm.school.planning.solver.domain.Lesson;
import tn.wtm.school.planning.solver.domain.TimetableSolution;
import tn.wtm.school.planning.solver.enums.SessionType;
import tn.wtm.school.planning.solver.ref.TimeSlotRef;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vérification métier d'un emploi du temps produit — étape E du plan de mise en
 * conformité à la circulaire n°66.
 *
 * <h2>Pourquoi une seconde vérification</h2>
 *
 * {@code TimetableSolverService} ne connaissait qu'un critère : {@code score.isFeasible()}.
 * Or ce score ne dit qu'une chose — <em>aucune des contraintes activées par cet
 * établissement n'est violée</em>. Il ne dit rien de celles qui ne l'ont pas été.
 * Décocher {@code RESPECT_OFFICIAL_SUBJECT_HOURS} dans l'interface suffisait donc à
 * obtenir un emploi du temps déclaré conforme où chaque classe perdait des heures
 * de cours. C'était le défaut P7, et il rendait tout le reste du plan révocable
 * d'un clic.
 *
 * <p>Ce validateur ne lit ni le profil de contraintes, ni le score : il regarde
 * l'emploi du temps et dit ce qui est vrai. Un établissement peut choisir ce que
 * le solveur optimise ; il ne choisit pas ce qui est physiquement possible ni ce
 * que le programme officiel impose.
 *
 * <h2>Ce qu'il refuse, et ce qu'il ne refuse pas</h2>
 *
 * Un constat {@link ValidationSeverity#BLOQUANT} appartient à l'une de trois
 * familles, et à aucune autre :
 *
 * <ol>
 *   <li><b>Impossibilité physique</b> — deux cours dans la même salle au même
 *       moment, un enseignant dédoublé, une classe en deux endroits, une salle
 *       trop petite, un cours pendant la pause méridienne.</li>
 *   <li><b>Défaut de génération</b> — une séance jamais placée, une séance en
 *       double, un demi-groupe dont la moitié manque. Le solveur est
 *       structurellement aveugle à ces cas : une séance qui n'a pas été
 *       engendrée n'est dans aucun flux de contraintes, donc ne coûte rien.</li>
 *   <li><b>Volume horaire officiel</b> — les tableaux T.1 à T.3 de la
 *       circulaire. Ce n'est pas une préférence : c'est le nombre d'heures
 *       d'enseignement qu'un élève doit recevoir.</li>
 * </ol>
 *
 * <p><b>Aucune règle pédagogique n'y figure.</b> Le quota matinal du § III.2.a,
 * l'espacement des séances d'EPS du § III.2.b, la stabilité de salle du § I.4
 * sont des contraintes MEDIUM : elles orientent le solveur, elles ne justifient
 * pas de refuser un emploi du temps à un établissement. Les réimplémenter ici
 * dupliquerait le provider sans rien apporter — et ferait diverger deux
 * définitions de la même règle, ce qui est exactement le défaut P1 que le plan
 * combat.
 *
 * <h2>Coût</h2>
 *
 * Les contrôles de conflit regroupent avant de comparer — par (enseignant, jour),
 * (classe, jour), (salle, jour). Les groupes valent quelques séances, là où une
 * comparaison deux à deux sur l'emploi du temps entier coûterait le carré du
 * nombre de séances. Le validateur ne tourne qu'une fois par job, mais il tourne
 * dans la transaction de persistance : il n'a pas le droit d'être lent.
 *
 * <p>Sans état, sans collaborateur, sans configuration : c'est une fonction pure
 * de la solution. Elle n'est donc pas injectée — il n'y aurait rien à y injecter.
 */
public class TimetableBusinessValidator {

    // ── codes de constat ──────────────────────────────────────────────────────

    static final String SEANCE_NON_PLACEE      = "SEANCE_NON_PLACEE";
    static final String SEANCE_DUPLIQUEE       = "SEANCE_DUPLIQUEE";
    static final String ENSEIGNANT_MANQUANT    = "ENSEIGNANT_MANQUANT";
    static final String DEMI_GROUPE_DESAPPARIE = "DEMI_GROUPE_DESAPPARIE";
    static final String VOLUME_HORAIRE         = "VOLUME_HORAIRE";
    static final String VOLUME_NON_VERIFIABLE  = "VOLUME_NON_VERIFIABLE";
    static final String CONFLIT_ENSEIGNANT     = "CONFLIT_ENSEIGNANT";
    static final String CONFLIT_CLASSE         = "CONFLIT_CLASSE";
    static final String CONFLIT_SALLE          = "CONFLIT_SALLE";
    static final String ENSEIGNANT_INDISPONIBLE = "ENSEIGNANT_INDISPONIBLE";
    static final String CAPACITE_SALLE         = "CAPACITE_SALLE";
    static final String SALLE_INADAPTEE        = "SALLE_INADAPTEE";
    static final String COURS_PENDANT_LA_PAUSE = "COURS_PENDANT_LA_PAUSE";
    static final String SEANCE_DEBORDANTE      = "SEANCE_DEBORDANTE";

    /** Deux créneaux de 30 min font une heure — l'unité dans laquelle on parle à l'utilisateur. */
    private static final int CRENEAUX_PAR_HEURE = 2;

    public ValidationReport valider(TimetableSolution solution) {
        if (solution == null || solution.getLessons() == null || solution.getLessons().isEmpty()) {
            return ValidationReport.conforme();
        }
        List<Lesson> seances = solution.getLessons();
        List<ValidationFinding> constats = new ArrayList<>();

        // 1. Intégrité de la génération — avant tout le reste : un emploi du
        //    temps incomplet ne mérite pas qu'on en vérifie les volumes.
        seancesNonPlacees(seances, constats);
        seancesDupliquees(seances, constats);
        enseignantsManquants(seances, constats);
        demiGroupesDesapparies(seances, constats);

        // 2. Impossibilités physiques
        conflits(seances, constats);
        indisponibilites(seances, constats);
        sallesInadaptees(seances, constats);
        creneauxInterdits(seances, constats);

        // 3. Programme officiel — § T.1 à T.3
        volumesOfficiels(seances, constats);

        return new ValidationReport(constats);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Intégrité de la génération
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Une séance sans créneau ou sans salle n'apparaîtra dans aucun emploi du
     * temps remis : elle est perdue, silencieusement, à la persistance
     * ({@code saveSessions} ne garde que les séances complètes).
     */
    private void seancesNonPlacees(List<Lesson> seances, List<ValidationFinding> constats) {
        for (Lesson l : seances) {
            if (l.getTimeSlot() == null || l.getRoom() == null) {
                constats.add(ValidationFinding.bloquant(SEANCE_NON_PLACEE, designation(l),
                        l.getTimeSlot() == null
                                ? "aucun créneau ne lui a été affecté"
                                : "aucune salle ne lui a été affectée"));
            }
        }
    }

    /**
     * Deux séances portant le même identifiant sont la même séance comptée deux
     * fois. Le solveur ne peut pas s'en apercevoir : elles se présentent à lui
     * comme deux entités distinctes, et il les place toutes les deux.
     */
    private void seancesDupliquees(List<Lesson> seances, List<ValidationFinding> constats) {
        seances.stream()
                .filter(l -> l.getId() != null)
                .collect(Collectors.groupingBy(Lesson::getId, LinkedHashMap::new, Collectors.counting()))
                .forEach((id, nombre) -> {
                    if (nombre > 1) {
                        constats.add(ValidationFinding.bloquant(SEANCE_DUPLIQUEE, "séance " + id,
                                "engendrée " + nombre + " fois ; l'emploi du temps compte "
                                        + "des heures qui n'existent pas"));
                    }
                });
    }

    private void enseignantsManquants(List<Lesson> seances, List<ValidationFinding> constats) {
        for (Lesson l : seances) {
            if (l.getTeacher() == null) {
                constats.add(ValidationFinding.bloquant(ENSEIGNANT_MANQUANT, designation(l),
                        "aucun enseignant n'est affecté à cette séance"));
            }
        }
    }

    /**
     * Les deux moitiés d'une classe dédoublée suivent la même séance au même
     * moment. Une moitié orpheline, ou placée ailleurs, signifie qu'une partie
     * de la classe n'a pas cours — ou en a deux fois.
     */
    private void demiGroupesDesapparies(List<Lesson> seances, List<ValidationFinding> constats) {
        Map<Long, List<Lesson>> paires = seances.stream()
                .filter(Lesson::isPaired)
                .collect(Collectors.groupingBy(Lesson::getPairedLessonId, LinkedHashMap::new, Collectors.toList()));

        paires.forEach((idPaire, moities) -> {
            if (moities.size() != 2) {
                constats.add(ValidationFinding.bloquant(DEMI_GROUPE_DESAPPARIE,
                        designation(moities.get(0)),
                        "la séance dédoublée compte " + moities.size()
                                + " moitié(s) au lieu de deux"));
                return;
            }
            TimeSlotRef a = moities.get(0).getTimeSlot();
            TimeSlotRef b = moities.get(1).getTimeSlot();
            if (a != null && b != null && !Objects.equals(a.getId(), b.getId())) {
                constats.add(ValidationFinding.bloquant(DEMI_GROUPE_DESAPPARIE,
                        designation(moities.get(0)),
                        "les deux moitiés sont placées sur des créneaux différents ("
                                + creneau(a) + " et " + creneau(b) + ")"));
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Impossibilités physiques
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Les trois conflits de ressource, sur le même principe : regrouper par
     * (ressource, jour), puis comparer les séances du groupe deux à deux.
     *
     * <p>Le chevauchement se lit sur {@link Lesson#overlapsInTime}, qui tient
     * déjà compte de la durée réelle des séances et de la parité de semaine :
     * deux quinzaines opposées ne se heurtent pas, elles n'ont jamais lieu la
     * même semaine.
     */
    private void conflits(List<Lesson> seances, List<ValidationFinding> constats) {
        List<Lesson> placees = seances.stream().filter(l -> l.getTimeSlot() != null).toList();

        parGroupe(placees, l -> l.getTeacher() == null ? null : l.getTeacher().getId(),
                (a, b) -> {
                    // Un professeur encadre ses deux demi-groupes en parallèle :
                    // c'est le fonctionnement normal d'une séance dédoublée.
                    if (a.isPaired() && Objects.equals(a.getPairedLessonId(), b.getPairedLessonId())) {
                        return;
                    }
                    constats.add(ValidationFinding.bloquant(CONFLIT_ENSEIGNANT,
                            a.getTeacher().getName() + ", " + creneau(a.getTimeSlot()),
                            "attendu en même temps en " + a.getStudentClassName()
                                    + " et en " + b.getStudentClassName()));
                });

        parGroupe(placees, Lesson::getStudentClassName,
                (a, b) -> {
                    // Deux demi-groupes distincts de la même classe : chaque
                    // moitié n'a qu'un cours, c'est régulier.
                    if (a.getGroupIndex() > 0 && b.getGroupIndex() > 0
                            && a.getGroupIndex() != b.getGroupIndex()) {
                        return;
                    }
                    constats.add(ValidationFinding.bloquant(CONFLIT_CLASSE,
                            a.getStudentClassName() + ", " + creneau(a.getTimeSlot()),
                            "deux cours en même temps : " + a.getSubjectCode()
                                    + " et " + b.getSubjectCode()));
                });

        parGroupe(placees, l -> l.getRoom() == null ? null : l.getRoom().getId(),
                (a, b) -> constats.add(ValidationFinding.bloquant(CONFLIT_SALLE,
                        "salle " + a.getRoom().getCode() + ", " + creneau(a.getTimeSlot()),
                        "occupée en même temps par " + a.getStudentClassName()
                                + " et " + b.getStudentClassName())));
    }

    /**
     * Compare deux à deux, au sein de chaque groupe (ressource, jour).
     *
     * <p>Une clé nulle écarte la séance : une séance sans enseignant ne peut pas
     * entrer en conflit d'enseignant, et son absence a déjà été signalée.
     */
    private void parGroupe(List<Lesson> seances,
                           Function<Lesson, Object> ressource,
                           java.util.function.BiConsumer<Lesson, Lesson> auConflit) {
        Map<String, List<Lesson>> groupes = new LinkedHashMap<>();
        for (Lesson l : seances) {
            Object cle = ressource.apply(l);
            if (cle == null) {
                continue;
            }
            groupes.computeIfAbsent(cle + "|" + l.getTimeSlot().getDay(), k -> new ArrayList<>()).add(l);
        }
        for (List<Lesson> groupe : groupes.values()) {
            for (int i = 0; i < groupe.size(); i++) {
                for (int j = i + 1; j < groupe.size(); j++) {
                    if (groupe.get(i).overlapsInTime(groupe.get(j))) {
                        auConflit.accept(groupe.get(i), groupe.get(j));
                    }
                }
            }
        }
    }

    /** Jour de formation pédagogique, congé, temps partiel — § II.1. */
    private void indisponibilites(List<Lesson> seances, List<ValidationFinding> constats) {
        for (Lesson l : seances) {
            if (l.getTeacher() == null || l.getTimeSlot() == null) {
                continue;
            }
            DayOfWeek jour = l.getTimeSlot().getDay();
            if (l.getTeacher().isUnavailableOn(jour)) {
                constats.add(ValidationFinding.bloquant(ENSEIGNANT_INDISPONIBLE,
                        l.getTeacher().getName() + ", " + jourEnFrancais(jour),
                        "un cours lui est affecté un jour où il est déclaré indisponible"));
            }
        }
    }

    /** Capacité insuffisante, et salle spécialisée manquante ou détournée — § III.4. */
    private void sallesInadaptees(List<Lesson> seances, List<ValidationFinding> constats) {
        for (Lesson l : seances) {
            if (l.getRoom() == null) {
                continue;
            }
            int effectif = l.isDemiGroup()
                    ? (l.getClassStudentCount() + 1) / 2
                    : l.getClassStudentCount();
            if (l.getRoom().getCapacity() < effectif) {
                constats.add(ValidationFinding.bloquant(CAPACITE_SALLE, designation(l),
                        "salle " + l.getRoom().getCode() + " : " + l.getRoom().getCapacity()
                                + " places pour " + effectif + " élèves"));
            }
            if (l.isRequiresSpecialRoom() && !l.getRoom().canAccommodate(l.getRequiredRoomType())) {
                constats.add(ValidationFinding.bloquant(SALLE_INADAPTEE, designation(l),
                        "exige une salle de type " + l.getRequiredRoomType()
                                + ", placée en " + l.getRoom().getCode()));
            }
        }
    }

    /**
     * Créneaux où aucune séance ne peut se tenir : la pause méridienne du § I.3,
     * et le débordement d'une séance longue hors de son bloc de travail.
     */
    private void creneauxInterdits(List<Lesson> seances, List<ValidationFinding> constats) {
        for (Lesson l : seances) {
            TimeSlotRef creneau = l.getTimeSlot();
            if (creneau == null) {
                continue;
            }
            if (creneau.isBreakSlot()) {
                constats.add(ValidationFinding.bloquant(COURS_PENDANT_LA_PAUSE, designation(l),
                        "placée sur la pause méridienne (" + creneau(creneau) + ")"));
            }
            if (l.getDurationSlots() > creneau.getMaxDurationSlots()) {
                constats.add(ValidationFinding.bloquant(SEANCE_DEBORDANTE, designation(l),
                        "dure " + enHeures(l.getDurationSlots()) + " alors que "
                                + enHeures(creneau.getMaxDurationSlots())
                                + " restent avant la fin de la demi-journée"));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Programme officiel — § T.1 à T.3
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Le volume placé pour un couple classe / matière est exactement celui du
     * programme.
     *
     * <p>{@code groupIndex != 2} : les deux moitiés d'une classe dédoublée
     * suivent la même séance, l'élève n'assiste qu'à l'une d'elles. Les compter
     * toutes les deux doublerait le volume reçu.
     *
     * <p>Les couples dont le volume officiel est inconnu ne sont pas jugés — ils
     * sont <em>signalés</em>. Un contrôle qui se tait faute de donnée doit dire
     * qu'il s'est tu ; sinon l'absence de constat se lit comme une conformité.
     */
    private void volumesOfficiels(List<Lesson> seances, List<ValidationFinding> constats) {
        Map<String, List<Lesson>> parCouple = seances.stream()
                .filter(l -> l.getGroupIndex() != 2)
                .collect(Collectors.groupingBy(
                        l -> l.getStudentClassName() + " / " + l.getSubjectCode(),
                        LinkedHashMap::new, Collectors.toList()));

        List<String> nonVerifiables = new ArrayList<>();

        parCouple.forEach((couple, lecons) -> {
            int officiel = lecons.get(0).getOfficialWeeklySlots();
            if (officiel <= 0) {
                nonVerifiables.add(couple);
                return;
            }
            int place = lecons.stream().mapToInt(Lesson::getDurationSlots).sum();
            if (place != officiel) {
                constats.add(ValidationFinding.bloquant(VOLUME_HORAIRE, couple,
                        "le programme prévoit " + enHeures(officiel)
                                + ", l'emploi du temps en place " + enHeures(place)));
            }
        });

        if (!nonVerifiables.isEmpty()) {
            constats.add(ValidationFinding.avertissement(VOLUME_NON_VERIFIABLE,
                    nonVerifiables.stream().sorted().limit(5).collect(Collectors.joining(", ")),
                    nonVerifiables.size() + " couple(s) classe / matière sans volume officiel "
                            + "en base : leur conformité au programme n'a pas pu être vérifiée"));
        }
    }

    // ── mise en mots ──────────────────────────────────────────────────────────

    private static String designation(Lesson l) {
        String matiere = l.getSubjectName() != null ? l.getSubjectName() : l.getSubjectCode();
        String base = l.getStudentClassName() + " / " + matiere;
        if (l.getSessionType() == SessionType.SPORT) {
            return base;
        }
        return l.isDemiGroup() ? base + " (groupe " + l.getGroupIndex() + ")" : base;
    }

    private static String creneau(TimeSlotRef t) {
        return jourEnFrancais(t.getDay()) + " " + t.getStartTime();
    }

    /** « 4 h », « 1 h 30 » — l'utilisateur ne compte pas en créneaux de trente minutes. */
    private static String enHeures(int creneaux) {
        int heures = creneaux / CRENEAUX_PAR_HEURE;
        return creneaux % CRENEAUX_PAR_HEURE == 0 ? heures + " h" : heures + " h 30";
    }

    private static final List<String> JOURS = List.of(
            "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche");

    private static String jourEnFrancais(DayOfWeek jour) {
        return jour == null ? "jour inconnu" : JOURS.get(jour.getValue() - 1);
    }
}
