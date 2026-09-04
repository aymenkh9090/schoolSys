package tn.wtm.school.planning.solver.constraint;

/**
 * Constraint identifiers used in two places:
 *   - asConstraint() calls in TimetableConstraintProvider
 *   - DB code lookups in ConstraintWeightMapper (dynamic constraints)
 *
 * Hard-coded constraint names (Section 6.2) use descriptive English labels.
 * Dynamic constraint codes (Section 6.3) match the constraint_definition.code column exactly.
 */
public final class ConstraintCodes {

    private ConstraintCodes() {}

    // ── hard-coded structural constraints (always active, no DB lookup) ───────
    public static final String TEACHER_CONFLICT                 = "Teacher conflict";
    public static final String ROOM_CONFLICT                    = "Room conflict";
    public static final String CLASS_CONFLICT                   = "Class conflict";
    public static final String ROOM_CAPACITY                    = "Room capacity exceeded";
    public static final String TEACHER_AVAILABILITY             = "Teacher availability";
    public static final String SPECIAL_ROOM_REQUIRED            = "Special room required";
    public static final String NORMAL_COURSE_NOT_IN_SPECIAL_ROOM= "Normal course not in special room";
    public static final String ONE_TEACHER_PER_SUBJECT_PER_CLASS= "One teacher per subject per class";
    public static final String PAIRED_DEMI_GROUP_SAME_SLOT      = "Paired demi-group must be simultaneous";
    public static final String NO_LESSON_IN_BREAK_SLOT          = "No lesson in break slot";
    public static final String LESSON_EXCEEDS_WORKING_BLOCK     = "Lesson exceeds working block";
    public static final String NO_STUDENT_IDLE_GAPS             = "No student idle gaps";

    // ── dynamic configurable constraints (DB-driven, added in Step 5) ─────────
    public static final String ONE_TEACHER_PER_SUBJECT_CLASS         = "ONE_TEACHER_PER_SUBJECT_CLASS";
    public static final String MAX_STUDENT_HOURS_PER_DAY             = "MAX_STUDENT_HOURS_PER_DAY";
    public static final String MAX_TEACHER_HOURS_PER_DAY             = "MAX_TEACHER_HOURS_PER_DAY";
    public static final String MAX_TEACHER_HOURS_FRIDAY_SATURDAY     = "MAX_TEACHER_HOURS_FRIDAY_SATURDAY";
    public static final String MAX_TWO_CONSECUTIVE_SESSIONS           = "MAX_TWO_CONSECUTIVE_SESSIONS_SAME_SUBJECT";
    public static final String BALANCED_MORNING_AFTERNOON            = "BALANCED_MORNING_AFTERNOON";
    public static final String TEACHER_MIN_TWO_LEVELS                = "TEACHER_MIN_TWO_LEVELS";
    public static final String BALANCED_TEACHER_WORKLOAD             = "BALANCED_TEACHER_WORKLOAD";
    public static final String TEACHER_WEEKLY_REST_DAY               = "TEACHER_WEEKLY_REST_DAY";
    public static final String AVOID_SUBJECT_CONCENTRATION_SAME_DAY  = "AVOID_SUBJECT_CONCENTRATION_SAME_DAY";
    public static final String BALANCED_CLASS_DIFFICULTY             = "BALANCED_CLASS_DIFFICULTY_FOR_TEACHERS";
    public static final String MAIN_SUBJECT_BALANCED_DISTRIBUTION    = "MAIN_SUBJECT_BALANCED_DISTRIBUTION";
    public static final String THEORY_PRACTICE_SEPARATION            = "THEORY_PRACTICE_SEPARATION";

    // ── contraintes personnalisées (DSL, Section 6.4) ─────────────────────────
    //
    // Timefold agrège les violations par NOM de contrainte, et ce nom est figé à
    // la compilation. Toutes les règles DSL d'un même niveau partagent donc un
    // flux — et un nom. Ce n'est pas une perte de traçabilité : la règle fautive
    // est le CompiledConstraint joint dans le tuple, donc présente dans les
    // objets incriminés de chaque ConstraintMatch, d'où TimetableSolverService
    // l'extrait pour l'afficher nommément.
    public static final String CUSTOM_RULE_HARD     = "Règle personnalisée (dure)";
    public static final String CUSTOM_RULE_MEDIUM   = "Règle personnalisée (moyenne)";
    public static final String CUSTOM_RULE_SOFT     = "Règle personnalisée (souple)";
    public static final String CUSTOM_REWARD_MEDIUM = "Préférence personnalisée (moyenne)";
    public static final String CUSTOM_REWARD_SOFT   = "Préférence personnalisée (souple)";
    public static final String CUSTOM_LIMIT_HARD    = "Seuil personnalisé (dur)";
    public static final String CUSTOM_LIMIT_MEDIUM  = "Seuil personnalisé (moyen)";
    public static final String CUSTOM_LIMIT_SOFT    = "Seuil personnalisé (souple)";
}
