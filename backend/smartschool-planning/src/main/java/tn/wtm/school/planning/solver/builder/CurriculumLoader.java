package tn.wtm.school.planning.solver.builder;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.planning.solver.ref.ExpectedCourse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le programme attendu d'une année scolaire : pour chaque classe active, les
 * matières de son niveau et le volume qu'elles doivent recevoir.
 *
 * <p>Sa source n'est <b>pas</b> celle des séances. {@code LessonGenerator} part
 * des affectations d'enseignement ; ce chargeur part des classes et des matières
 * de leur niveau. C'est exactement ce décalage qui rend le contrôle utile : une
 * matière déclarée au niveau mais que personne n'enseigne existe ici et n'existe
 * pas là, et c'est le seul endroit d'où l'on peut le voir.
 *
 * <p><b>Comment déclarer qu'une matière n'est pas enseignée</b> : mettre son
 * volume à zéro dans {@code niveaux_matieres.heures_semaine}. Un volume nul
 * n'entre pas dans le programme attendu, et rien ne sera reproché. Désactiver le
 * pattern ne suffit pas — {@code LessonGenerator} retombe alors sur le type de
 * séance et engendre quand même.
 */
@Component
@RequiredArgsConstructor
public class CurriculumLoader {

    /** Une heure de cours occupe deux créneaux de trente minutes. */
    private static final int CRENEAUX_PAR_HEURE = 2;

    private final ClassGroupRepository   classGroupRepository;
    private final SubjectLevelRepository subjectLevelRepository;

    public List<ExpectedCourse> load(String tenantId, Long academicYearId) {
        List<ClassGroup> classes = classGroupRepository
                .findByTenantIdAndSchoolYear_IdAnnee(tenantId, academicYearId).stream()
                .filter(c -> !Boolean.FALSE.equals(c.getEstActif()))
                .filter(c -> c.getLevel() != null && c.getCode() != null)
                .toList();

        // Les classes d'un même niveau suivent le même programme : une lecture
        // par niveau, pas une par classe.
        Map<Long, List<SubjectLevel>> parNiveau = new HashMap<>();
        List<ExpectedCourse> attendu = new ArrayList<>();

        for (ClassGroup classe : classes) {
            Long niveau = classe.getLevel().getIdNiveau();
            List<SubjectLevel> matieres = parNiveau.computeIfAbsent(
                    niveau, subjectLevelRepository::findByLevel_IdNiveau);

            for (SubjectLevel matiere : matieres) {
                int creneaux = enCreneaux(matiere.getHeuresSemaine());
                Subject sujet = matiere.getSubject();
                if (creneaux <= 0 || sujet == null || sujet.getCodeMatiere() == null) {
                    continue;
                }
                attendu.add(new ExpectedCourse(classe.getCode(), sujet.getCodeMatiere(),
                        sujet.getLibMatiere(), creneaux));
            }
        }
        return attendu;
    }

    /**
     * Les demi-heures sont conservées : une matière à 1 h 30 vaut trois
     * créneaux. Arrondir ici ferait échouer le rapprochement de volume sur une
     * donnée pourtant juste.
     */
    private static int enCreneaux(Double heuresSemaine) {
        if (heuresSemaine == null || heuresSemaine <= 0) {
            return 0;
        }
        return (int) Math.round(heuresSemaine * CRENEAUX_PAR_HEURE);
    }
}
