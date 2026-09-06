package tn.wtm.school.planning.solver.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.planning.solver.ref.ExpectedCourse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le programme attendu — la seule source qui sache ce qu'une classe <em>devrait</em>
 * recevoir.
 *
 * <p>Elle n'est pas celle des séances, et c'est tout son intérêt :
 * {@code LessonGenerator} part des affectations d'enseignement, ce chargeur part
 * des matières du niveau. Une matière que personne n'enseigne existe ici et
 * n'existe pas là — c'est le seul endroit d'où l'on peut le voir.
 */
@ExtendWith(MockitoExtension.class)
class CurriculumLoaderTest {

    @Mock private ClassGroupRepository   classGroupRepository;
    @Mock private SubjectLevelRepository subjectLevelRepository;

    @InjectMocks private CurriculumLoader chargeur;

    private static final Long SEPTIEME = 7L;

    @Test
    @DisplayName("Chaque classe reçoit le programme de son niveau")
    void programmeDuNiveau() {
        when(classGroupRepository.findByTenantIdAndSchoolYear_IdAnnee("ecole-1", 2026L))
                .thenReturn(List.of(classe("7A", SEPTIEME), classe("7B", SEPTIEME)));
        when(subjectLevelRepository.findByLevel_IdNiveau(SEPTIEME))
                .thenReturn(List.of(matiere("MATH", "Mathématiques", 4.0),
                        matiere("MUS", "Éducation musicale", 1.0)));

        List<ExpectedCourse> attendu = chargeur.load("ecole-1", 2026L);

        assertThat(attendu).extracting(ExpectedCourse::couple)
                .containsExactlyInAnyOrder("7A / MATH", "7A / MUS", "7B / MATH", "7B / MUS");
        assertThat(attendu).allSatisfy(c ->
                assertThat(c.weeklySlots()).isPositive());
    }

    @Test
    @DisplayName("Le programme d'un niveau n'est lu qu'une fois pour toutes ses classes")
    void uneLectureParNiveau() {
        when(classGroupRepository.findByTenantIdAndSchoolYear_IdAnnee("ecole-1", 2026L))
                .thenReturn(List.of(classe("7A", SEPTIEME), classe("7B", SEPTIEME),
                        classe("7C", SEPTIEME)));
        when(subjectLevelRepository.findByLevel_IdNiveau(SEPTIEME))
                .thenReturn(List.of(matiere("MATH", "Mathématiques", 4.0)));

        chargeur.load("ecole-1", 2026L);

        verify(subjectLevelRepository, times(1)).findByLevel_IdNiveau(SEPTIEME);
    }

    @Test
    @DisplayName("Un volume à zéro dit « cette matière n'est pas enseignée »")
    void volumeNulExclu() {
        // C'est la façon de déclarer qu'on n'assure pas une matière. Désactiver
        // son pattern ne suffirait pas : LessonGenerator retombe alors sur le
        // type de séance et engendre quand même.
        when(classGroupRepository.findByTenantIdAndSchoolYear_IdAnnee("ecole-1", 2026L))
                .thenReturn(List.of(classe("7A", SEPTIEME)));
        when(subjectLevelRepository.findByLevel_IdNiveau(SEPTIEME))
                .thenReturn(List.of(matiere("MATH", "Mathématiques", 4.0),
                        matiere("THEATRE", "Éducation théâtrale", 0.0),
                        matiere("LATIN", "Latin", null)));

        assertThat(chargeur.load("ecole-1", 2026L))
                .extracting(ExpectedCourse::subjectCode)
                .containsExactly("MATH");
    }

    @Test
    @DisplayName("Une demi-heure n'est pas arrondie")
    void demiHeureConservee() {
        // Une matière à 1 h 30 vaut trois créneaux. Arrondir ferait échouer le
        // rapprochement de volume sur une donnée pourtant juste.
        when(classGroupRepository.findByTenantIdAndSchoolYear_IdAnnee("ecole-1", 2026L))
                .thenReturn(List.of(classe("7A", SEPTIEME)));
        when(subjectLevelRepository.findByLevel_IdNiveau(SEPTIEME))
                .thenReturn(List.of(matiere("TECH", "Éducation technologique", 1.5)));

        assertThat(chargeur.load("ecole-1", 2026L))
                .singleElement()
                .satisfies(c -> assertThat(c.weeklySlots()).isEqualTo(3));
    }

    @Test
    @DisplayName("Une classe désactivée n'attend plus rien")
    void classeInactiveIgnoree() {
        ClassGroup fermee = classe("7Z", SEPTIEME);
        fermee.setEstActif(false);

        when(classGroupRepository.findByTenantIdAndSchoolYear_IdAnnee("ecole-1", 2026L))
                .thenReturn(List.of(fermee));

        assertThat(chargeur.load("ecole-1", 2026L)).isEmpty();
    }

    // ── décor ─────────────────────────────────────────────────────────────────

    private static ClassGroup classe(String code, Long niveau) {
        return ClassGroup.builder()
                .idClasse((long) code.hashCode())
                .code(code)
                .estActif(true)
                .level(Level.builder().idNiveau(niveau).code("7EME").nom("7ème").build())
                .build();
    }

    private static SubjectLevel matiere(String code, String libelle, Double heures) {
        return SubjectLevel.builder()
                .heuresSemaine(heures)
                .subject(Subject.builder().codeMatiere(code).libMatiere(libelle).build())
                .build();
    }
}
