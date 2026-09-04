package tn.wtm.school.planning.solver.enums;

public enum SolverStatus {
    PENDING,     // job created, not yet started
    RUNNING,     // solver is actively computing
    SOLVED,      // finished, 0 hard violations
    INFEASIBLE,  // finished with a usable timetable, but hard violations remain
    FAILED,      // solver crashed — no result to show
    CANCELLED;   // cancelled by the user

    /**
     * Le solveur est allé au bout : une liste de séances vide est une réponse
     * légitime, pas le signe d'un job encore en cours.
     */
    public boolean isFinished() {
        return this == SOLVED || this == INFEASIBLE || this == FAILED;
    }

    /** Un emploi du temps exploitable a été produit (consultable, éditable, publiable). */
    public boolean hasTimetable() {
        return this == SOLVED || this == INFEASIBLE;
    }
}
