package tn.wtm.school.org.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.metrics.TenantCount;
import tn.wtm.school.common.metrics.TenantStatusCount;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Eleve;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.SchoolUser;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.enums.Specialite;
import tn.wtm.school.org.enums.UserRole;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.EleveRepository;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.TeacherRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requêtes groupées alimentant les métriques par établissement.
 *
 * Ces requêtes sont d'une nature différente de toutes les autres du projet :
 * elles TRAVERSENT volontairement les établissements, là où le reste du code
 * s'interdit de le faire. Deux choses doivent donc être vérifiées, et aucune ne
 * l'est par la compilation :
 *
 *   1. que le JPQL s'exécute réellement — une expression de construction et un
 *      {@code CAST(... AS string)} sur une énumération ne sont validés qu'au
 *      démarrage du contexte, pas par javac ;
 *   2. qu'elles retournent bien TOUS les établissements — une requête qui ne
 *      renverrait que le tenant courant produirait des métriques plausibles
 *      mais fausses, le pire mode de défaillance pour de la supervision.
 */
class SchoolMetricsQueriesIT extends AbstractIntegrationTest {

    @Autowired SchoolUserRepository userRepo;
    @Autowired EleveRepository eleveRepo;
    @Autowired ClassGroupRepository classRepo;
    @Autowired TeacherRepository teacherRepo;
    @Autowired SchoolYearRepository schoolYearRepo;
    @Autowired LevelRepository levelRepo;

    private static final String ECOLE_A = "metrics-a";
    private static final String ECOLE_B = "metrics-b";

    @Test
    void utilisateurs_actifs_comptes_par_etablissement_et_par_role() {
        savedUser(ECOLE_A, "a1", UserRole.TEACHER, true);
        savedUser(ECOLE_A, "a2", UserRole.TEACHER, true);
        savedUser(ECOLE_A, "a3", UserRole.SURVEILLANT, true);
        // Inactif : ne doit jamais entrer dans le compte, sinon une école qui
        // archive ses comptes paraîtrait grossir indéfiniment.
        savedUser(ECOLE_A, "a4", UserRole.TEACHER, false);
        savedUser(ECOLE_B, "b1", UserRole.TEACHER, true);

        TenantContext.clear();
        List<TenantStatusCount> counts = userRepo.countActiveGroupedByTenantAndRole();

        assertThat(valueOf(counts, ECOLE_A, "TEACHER")).isEqualTo(2);
        assertThat(valueOf(counts, ECOLE_A, "SURVEILLANT")).isEqualTo(1);
        assertThat(valueOf(counts, ECOLE_B, "TEACHER")).isEqualTo(1);
    }

    @Test
    void le_total_par_etablissement_est_la_somme_des_roles() {
        savedUser(ECOLE_A, "s1", UserRole.TEACHER, true);
        savedUser(ECOLE_A, "s2", UserRole.SURVEILLANT, true);
        savedUser(ECOLE_B, "s3", UserRole.TEACHER, true);

        TenantContext.clear();
        Map<String, Long> totals = userRepo.countActiveGroupedByTenantAndRole().stream()
                .collect(Collectors.groupingBy(TenantStatusCount::tenantId,
                        Collectors.summingLong(TenantStatusCount::value)));

        // C'est exactement ce que fait `sum by (tenant)` dans Prometheus : si
        // cette somme est juste ici, le tableau de bord l'est aussi.
        assertThat(totals.get(ECOLE_A)).isEqualTo(2);
        assertThat(totals.get(ECOLE_B)).isEqualTo(1);
    }

    @Test
    void la_requete_traverse_bien_les_etablissements_malgre_un_tenant_courant() {
        savedUser(ECOLE_A, "x1", UserRole.TEACHER, true);
        savedUser(ECOLE_B, "x2", UserRole.TEACHER, true);

        // Un tenant est positionné : la requête doit malgré tout voir les deux
        // établissements. C'est la garantie sur laquelle repose le collecteur.
        TenantContext.setTenantId(ECOLE_A);
        List<TenantStatusCount> counts = userRepo.countActiveGroupedByTenantAndRole();

        assertThat(counts).extracting(TenantStatusCount::tenantId)
                .contains(ECOLE_A, ECOLE_B);
    }

    @Test
    void eleves_classes_et_enseignants_actifs_comptes_par_etablissement() {
        SchoolYear yearA = savedYear(ECOLE_A);
        Level levelA = savedLevel(ECOLE_A);
        ClassGroup classeA = savedClass(ECOLE_A, "7A1", yearA, levelA);
        savedClass(ECOLE_A, "7A2", yearA, levelA);
        savedEleve(ECOLE_A, "E1", classeA, true);
        savedEleve(ECOLE_A, "E2", classeA, true);
        savedEleve(ECOLE_A, "E3", classeA, false);
        savedTeacher(ECOLE_A, "T1", true);
        savedTeacher(ECOLE_A, "T2", false);

        SchoolYear yearB = savedYear(ECOLE_B);
        Level levelB = savedLevel(ECOLE_B);
        savedClass(ECOLE_B, "8B1", yearB, levelB);
        savedTeacher(ECOLE_B, "T3", true);

        TenantContext.clear();

        assertThat(valueOf(eleveRepo.countActiveGroupedByTenant(), ECOLE_A)).isEqualTo(2);
        assertThat(valueOf(classRepo.countActiveGroupedByTenant(), ECOLE_A)).isEqualTo(2);
        assertThat(valueOf(classRepo.countActiveGroupedByTenant(), ECOLE_B)).isEqualTo(1);
        assertThat(valueOf(teacherRepo.countActiveGroupedByTenant(), ECOLE_A)).isEqualTo(1);
        assertThat(valueOf(teacherRepo.countActiveGroupedByTenant(), ECOLE_B)).isEqualTo(1);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private static long valueOf(List<TenantCount> counts, String tenantId) {
        return counts.stream()
                .filter(c -> tenantId.equals(c.tenantId()))
                .mapToLong(TenantCount::value)
                .findFirst()
                .orElse(0L);
    }

    private static long valueOf(List<TenantStatusCount> counts, String tenantId, String key) {
        return counts.stream()
                .filter(c -> tenantId.equals(c.tenantId()) && key.equals(c.key()))
                .mapToLong(TenantStatusCount::value)
                .findFirst()
                .orElse(0L);
    }

    private SchoolUser savedUser(String tenantId, String suffix, UserRole role, boolean actif) {
        TenantContext.setTenantId(tenantId);
        return userRepo.saveAndFlush(SchoolUser.builder()
                .keycloakUserId(tenantId + "-" + suffix)
                .email(suffix + "@" + tenantId + ".test")
                .nomComplet("Utilisateur " + suffix)
                .role(role)
                .actif(actif)
                .build());
    }

    private SchoolYear savedYear(String tenantId) {
        TenantContext.setTenantId(tenantId);
        return schoolYearRepo.saveAndFlush(SchoolYear.builder()
                .nom(tenantId + "-AY").dateDebut(LocalDate.of(2024, 9, 1))
                .dateFin(LocalDate.of(2025, 6, 30)).estActive(true).estCourante(true).build());
    }

    private Level savedLevel(String tenantId) {
        TenantContext.setTenantId(tenantId);
        return levelRepo.saveAndFlush(Level.builder()
                .code("L7").nom("L7 nom").description("L7 desc").estActif(true).build());
    }

    private ClassGroup savedClass(String tenantId, String code, SchoolYear year, Level level) {
        TenantContext.setTenantId(tenantId);
        return classRepo.saveAndFlush(ClassGroup.builder()
                .code(code).codeSpecialite(Specialite.TCOM).nbEleve(28)
                .estActif(true).schoolYear(year).level(level).build());
    }

    private Eleve savedEleve(String tenantId, String code, ClassGroup classe, boolean actif) {
        TenantContext.setTenantId(tenantId);
        return eleveRepo.saveAndFlush(Eleve.builder()
                .codeEleve(code).nom("Nom" + code).prenom("Prenom" + code)
                .classeGroup(classe).estActif(actif).build());
    }

    private Teacher savedTeacher(String tenantId, String code, boolean enPoste) {
        TenantContext.setTenantId(tenantId);
        return teacherRepo.saveAndFlush(Teacher.builder()
                .codeEnseignant(code).numIdentite("ID" + code)
                .nom("Nom" + code).prenom("Prenom" + code)
                .estEnPoste(enPoste).build());
    }
}
