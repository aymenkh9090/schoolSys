package tn.wtm.school.planning.solver.ref;

/**
 * Une ligne du programme attendu : ce qu'une classe doit recevoir dans une
 * matière, indépendamment de ce qui a été engendré.
 *
 * <h2>Pourquoi cette liste existe</h2>
 *
 * {@code TimetableBusinessValidator} est une fonction pure des séances de la
 * solution, et c'était voulu. Mais une fonction des séances ne peut rien dire
 * d'une séance qui n'existe pas : une matière déclarée au niveau et dépourvue
 * d'affectation d'enseignant n'engendre aucune leçon, n'apparaît dans aucun
 * groupe, et sortait donc de la validation sans un mot. L'emploi du temps était
 * déclaré conforme, la matière simplement absente.
 *
 * <p>Le programme attendu est la seule chose qui manquait pour fermer ce trou :
 * il vient des données d'organisation — les classes de l'année et les matières
 * de leur niveau — et non des séances. Il voyage avec la solution pour que la
 * validation reste une fonction de son seul argument.
 *
 * @param studentClassName code de la classe, tel que porté par {@code Lesson}
 * @param subjectCode      code matière, tel que porté par {@code Lesson}
 * @param subjectName      libellé destiné à l'affichage
 * @param weeklySlots      volume attendu, en créneaux de 30 min
 */
public record ExpectedCourse(
        String studentClassName,
        String subjectCode,
        String subjectName,
        int weeklySlots) {

    /**
     * La clé sous laquelle les séances de ce couple sont regroupées côté
     * validation. Les deux doivent s'écrire de la même façon, sans quoi le
     * rapprochement ne se fait pas — d'où cette méthode plutôt qu'une
     * concaténation recopiée.
     */
    public String couple() {
        return studentClassName + " / " + subjectCode;
    }

    /** « 7A / Mathématiques » — pour l'humain, pas pour le rapprochement. */
    public String designation() {
        return studentClassName + " / "
                + (subjectName != null && !subjectName.isBlank() ? subjectName : subjectCode);
    }
}
