package tn.wtm.school.planning.solver.dto.response;

import lombok.*;

import java.util.List;

/**
 * Explication déterministe du score d'une solution : liste des contraintes
 * violées par niveau (hard/medium/soft), avec pour chacune un libellé lisible,
 * le nombre d'occurrences et quelques exemples concrets (quel enseignant,
 * quelle classe, quel créneau) — construits à partir des
 * {@code ConstraintMatch} de Timefold, pas d'une IA.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScoreExplanationResponse {

    private Long jobId;
    private String score;
    private boolean feasible;
    private List<ConstraintViolation> hardViolations;
    private List<ConstraintViolation> mediumViolations;
    private List<ConstraintViolation> softViolations;

    /**
     * Verdict de la validation métier — indépendant du solveur et du profil.
     *
     * <p>Les trois listes ci-dessus disent ce que <em>les contraintes activées</em>
     * reprochent au planning. Celle-ci dit ce que le planning a de faux quoi qu'on
     * ait activé : une séance perdue, un volume horaire amputé, deux classes dans
     * la même salle. Les deux se lisent ensemble, et c'est bien pour cela qu'elles
     * voyagent dans la même réponse.
     */
    private List<BusinessFinding> businessValidation;

    /** True quand la validation métier ne formule aucun constat bloquant. */
    private boolean businessValid;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ConstraintViolation {
        /** Nom technique de la contrainte (identifiant Timefold, ex. "Teacher conflict"). */
        private String constraintName;
        /** Libellé français destiné à l'affichage. */
        private String label;
        /** Score total infligé par cette contrainte, ex. "-3hard/0medium/0soft". */
        private String score;
        /** Nombre d'occurrences de cette violation dans la solution. */
        private int count;
        /** Exemples concrets (enseignant/classe/créneau concernés), limités en nombre. */
        private List<String> examples;
        /**
         * Les mêmes violations que {@link #examples}, mais désignées au lieu
         * d'être décrites : chaque entrée porte les identifiants des séances en
         * cause. C'est ce qui permet à l'interface de surligner la case fautive
         * et à une suggestion de viser un déplacement précis.
         *
         * <p>{@code examples} n'est pas remplacé pour autant : une contrainte à
         * seuil (« pas plus de 6 h par jour ») n'incrimine aucune séance en
         * particulier — son tuple porte la clé de groupe et le cumul — et n'a
         * donc rien à mettre ici. La phrase reste alors le seul rendu possible.
         */
        private List<Occurrence> occurrences;
        /** Suggestion concrète et actionnable pour résoudre ce type de conflit. */
        private String suggestion;
    }

    /**
     * Une violation, une. Là où {@code ConstraintViolation} agrège tout ce
     * qu'une contrainte reproche, ceci est un {@code ConstraintMatch} de
     * Timefold : le fait élémentaire dont le score est la somme.
     */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Occurrence {
        /** La phrase déjà lisible, identique à l'entrée correspondante d'{@code examples}. */
        private String label;
        /** Score infligé par cette occurrence seule, ex. "-1hard/0medium/0soft". */
        private String score;
        /**
         * Les séances mises en cause — deux pour un conflit (l'enseignant est
         * <em>ici</em> et <em>là</em>), une seule pour une salle trop petite.
         *
         * <p>C'est la liste entière qui répond à « où exactement » : surligner
         * une seule des deux séances d'un conflit ne montrerait pas le conflit.
         */
        private List<SessionRef> sessions;
        /**
         * Un déplacement qui lèverait cette occurrence, calculé sur la solution.
         *
         * <p>{@code null} quand aucun créneau ne convient — c'est fréquent sur
         * un emploi du temps saturé, et c'est une information en soi : le
         * conflit ne se règle pas en bougeant une case. L'interface retombe
         * alors sur la phrase générique de {@code suggestion}.
         */
        private Relocation relocation;
    }

    /**
     * Un déplacement possible, <b>vérifié</b> sur la solution en mémoire.
     *
     * <p>Le créneau d'arrivée est libre pour l'enseignant, pour la classe, et
     * une salle du bon type y est disponible — les contraintes dures du solveur
     * ont été rejouées une à une ({@code AlternativeSlotFinder}). Ce n'est pas
     * une phrase produite par un modèle : un LLM sait écrire « déplacez-le au
     * jeudi 10 h » sans aucun moyen de savoir si le jeudi 10 h est libre.
     *
     * <p><b>Ce n'est pas une promesse d'amélioration du score.</b> Le
     * déplacement peut dégrader une contrainte souple — quota du matin, heure
     * creuse chez l'élève. L'arbitrage revient au directeur, et rien ici n'est
     * appliqué sans son geste : {@code sessionId} n'est qu'une cible offerte au
     * {@code PATCH}, jamais un ordre exécuté.
     */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Relocation {
        /** Cible du {@code PATCH /jobs/{id}/sessions/{sessionId}}, {@code null} si la séance n'a pas de ligne. */
        private Long sessionId;
        /** Identifiant Timefold de la séance à déplacer, unique dans le job. */
        private Long lessonId;
        /** Ce qu'on déplace, en clair : « Mathématiques · 7B ». */
        private String subjectName;
        private String className;
        /** D'où — les coordonnées actuelles, ex. "MONDAY" et "08:00". */
        private String fromDay;
        private String fromStartTime;
        /** Vers où. {@code toSlotId} est l'identifiant de créneau à envoyer au PATCH. */
        private String toDay;
        private String toStartTime;
        private Long toSlotId;
        /** La salle qui accueillerait la séance à l'arrivée — souvent la même qu'avant. */
        private String toRoomCode;
        /**
         * La proposition en une phrase française, prête à afficher.
         *
         * <p>Construite ici plutôt que côté client parce que trois consommateurs
         * la rendraient différemment — l'écran, l'assistant, et un futur export —
         * et qu'une proposition de déplacement formulée de trois façons devient
         * trois propositions aux yeux de qui la lit.
         */
        private String text;
    }

    /**
     * Une séance désignée : de quoi la retrouver dans la grille, et de quoi la
     * déplacer.
     *
     * <h4>Les coordonnées sont celles du solveur</h4>
     *
     * {@code day} et {@code startTime} disent où la séance était posée
     * <em>quand la violation a été constatée</em>. Si un utilisateur a déplacé
     * la séance depuis (via {@code PATCH /jobs/{id}/sessions/{sessionId}}),
     * elles ne décrivent plus la grille — mais {@code sessionId}, lui, reste
     * juste. C'est l'identifiant qui sert d'ancre, jamais les coordonnées.
     */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SessionRef {
        /** Identifiant Timefold, unique à l'intérieur du job seulement. */
        private Long lessonId;
        /**
         * Identifiant de la ligne persistée, cible du PATCH de déplacement.
         * {@code null} quand la séance n'a pas été retrouvée : job antérieur à
         * la colonne {@code lesson_id}, ou séance qu'aucune solution n'a placée.
         */
        private Long sessionId;
        private String subjectCode;
        private String subjectName;
        private String className;
        private String teacherCode;
        private String teacherName;
        private String roomCode;
        /** Nom du jour tel que {@link java.time.DayOfWeek}, ex. "MONDAY". */
        private String day;
        /** Heure de début au format ISO, ex. "08:00". */
        private String startTime;
        /** 0 = classe entière, 1 = demi-groupe A, 2 = demi-groupe B. */
        private int groupIndex;
    }

    /** Un constat de la validation métier, mis à plat pour l'interface. */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class BusinessFinding {
        /** {@code BLOQUANT} ou {@code AVERTISSEMENT} — seul le premier refuse le planning. */
        private String severity;
        /** Identifiant stable du contrôle, ex. {@code CONFLIT_SALLE}. */
        private String code;
        /** Ce qui est concerné : « 7A / MATH », « salle A1, lundi 08:00 ». */
        private String scope;
        /** Ce qui ne va pas, en une phrase. */
        private String message;
    }
}
