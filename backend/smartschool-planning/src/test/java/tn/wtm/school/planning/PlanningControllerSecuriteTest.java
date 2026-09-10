package tn.wtm.school.planning;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import tn.wtm.school.planning.constraints.web.ConstraintController;
import tn.wtm.school.planning.constraints.web.CustomConstraintController;
import tn.wtm.school.planning.solver.web.TimetableController;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les règles de rôle des controllers planning, lues sur les annotations.
 *
 * <p>Ces controllers vivent dans une bibliothèque sans {@code @EnableMethodSecurity} :
 * leurs {@code @PreAuthorize} ne sont exécutés que dans {@code smartschool-api}.
 * Un test d'intégration les y couvrirait ; ici, on garde une prise directe et
 * bon marché contre la dérive — la suppression ou l'affaiblissement d'un
 * {@code @PreAuthorize} sur une méthode d'écriture. Le seuil de rôle est une
 * décision, pas un détail : il mérite d'être figé par un test qui échoue si on y
 * touche sans le vouloir.
 *
 * <p>Le partage lecture / écriture voulu :
 * <ul>
 *   <li>consultation ouverte largement — l'enseignant lit les contraintes, le
 *       surveillant consulte les emplois du temps ;</li>
 *   <li>toute écriture (profil, règle, génération, publication, déplacement de
 *       séance) réservée au {@code SCHOOL_ADMIN}.</li>
 * </ul>
 */
class PlanningControllerSecuriteTest {

    private static final String ADMIN_SEUL = "hasRole('SCHOOL_ADMIN')";

    // ── Contraintes (catalogue + profils) ────────────────────────────────────

    @Nested
    class Contraintes {

        @Test
        void laLectureEstOuverteAuxEnseignants() {
            assertThat(classeExige(ConstraintController.class))
                    .isEqualTo("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')");
        }

        @Test
        void seulLAdminEcritSurLesProfilsEtParametres() {
            for (String methode : new String[]{
                    "createProfile", "createDefaultProfile", "activateProfile", "deleteProfile",
                    "addSetting", "updateSetting", "deleteSetting"}) {
                assertThat(methodeExige(ConstraintController.class, methode))
                        .as("%s doit rester réservée au SCHOOL_ADMIN", methode)
                        .isEqualTo(ADMIN_SEUL);
            }
        }

        @Test
        void lesLecturesNAjoutentPasDeVerrouDeMethode() {
            for (String methode : new String[]{"getDefinitions", "getProfiles", "getProfile"}) {
                assertThat(methodeAnnotation(ConstraintController.class, methode))
                        .as("%s : la règle de classe suffit", methode)
                        .isNull();
            }
        }
    }

    // ── Contraintes personnalisées (DSL) ─────────────────────────────────────

    @Nested
    class ContraintesPersonnalisees {

        @Test
        void laLectureEstOuverteAuxEnseignants() {
            assertThat(classeExige(CustomConstraintController.class))
                    .isEqualTo("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')");
        }

        @Test
        void seulLAdminCreeModifieOuSupprimeUneRegle() {
            for (String methode : new String[]{"create", "update", "setEnabled", "delete"}) {
                assertThat(methodeExige(CustomConstraintController.class, methode))
                        .as("%s doit rester réservée au SCHOOL_ADMIN", methode)
                        .isEqualTo(ADMIN_SEUL);
            }
        }

        @Test
        void analyserUneRegleCandidateResteOuvertAUnEnseignant() {
            // POST, mais sans effet de bord : l'écran de confirmation l'appelle,
            // et un enseignant a le droit de simuler avant de demander à l'admin.
            assertThat(methodeAnnotation(CustomConstraintController.class, "analyze"))
                    .isNull();
        }
    }

    // ── Emplois du temps ────────────────────────────────────────────────────

    @Nested
    class EmploisDuTemps {

        @Test
        void laConsultationEstOuverteJusquAuSurveillant() {
            assertThat(classeExige(TimetableController.class))
                    .isEqualTo("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT')");
        }

        @Test
        void generationEditionEtPublicationSontReserveesALAdmin() {
            for (String methode : new String[]{
                    "preflight", "generate", "cancel", "deleteJob", "moveSession", "publish"}) {
                assertThat(methodeExige(TimetableController.class, methode))
                        .as("%s doit rester réservée au SCHOOL_ADMIN", methode)
                        .isEqualTo(ADMIN_SEUL);
            }
        }

        @Test
        void lesGrillesEtLeSuiviDeJobRestentEnLectureLarge() {
            for (String methode : new String[]{
                    "listJobs", "getJobStatus", "getSolution", "getScoreExplanation", "getResult",
                    "getClassTimetable", "getAllClassTimetables", "getTeacherTimetable",
                    "getAllTeacherTimetables", "getRoomTimetable", "getAllRoomTimetables",
                    "listGenerated"}) {
                assertThat(methodeAnnotation(TimetableController.class, methode))
                        .as("%s : consultation, pas de verrou de méthode", methode)
                        .isNull();
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String classeExige(Class<?> controller) {
        PreAuthorize pre = controller.getAnnotation(PreAuthorize.class);
        assertThat(pre).as("%s doit porter un @PreAuthorize de classe", controller.getSimpleName()).isNotNull();
        return pre.value();
    }

    private static String methodeExige(Class<?> controller, String nom) {
        PreAuthorize pre = methodeAnnotation(controller, nom);
        assertThat(pre).as("%s.%s doit porter un @PreAuthorize", controller.getSimpleName(), nom).isNotNull();
        return pre.value();
    }

    private static PreAuthorize methodeAnnotation(Class<?> controller, String nom) {
        Method methode = Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.getName().equals(nom))
                .findFirst()
                .orElseThrow(() -> new AssertionError(controller.getSimpleName() + " n'a pas de méthode " + nom));
        return methode.getAnnotation(PreAuthorize.class);
    }
}
