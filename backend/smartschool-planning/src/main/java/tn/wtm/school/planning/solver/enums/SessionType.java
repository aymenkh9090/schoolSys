package tn.wtm.school.planning.solver.enums;

/**
 * Solver-level session types, distinct from the org-module's pedagogical session types.
 * Determines which kind of room the lesson requires.
 */
public enum SessionType {
    COURS,   // cours magistral — normal classroom, full class
    TP,      // travaux pratiques — specialized lab, demi-group
    TD,      // travaux dirigés — normal classroom, can be demi-group
    SPORT,   // EPS — sports hall, demi-group or full
    ATELIER  // atelier STI/technique — workshop, demi-group
}
