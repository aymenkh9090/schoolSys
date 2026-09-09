package tn.wtm.school.api.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.metrics.TenantCount;
import tn.wtm.school.common.metrics.TenantInterval;
import tn.wtm.school.common.metrics.TenantStatusCount;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.EleveRepository;
import tn.wtm.school.org.repository.SchoolUserRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.planning.solver.repository.TimetableJobRepository;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.repository.TenantRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le relevé des métriques métier publiées sur l'endpoint Prometheus.
 *
 * <p>Les tests s'appuient sur un {@link SimpleMeterRegistry} réel plutôt que
 * sur un double : ce qui est vérifié ici n'est pas qu'une méthode a été
 * appelée, mais <b>ce que Prometheus lirait</b> — le nom des séries, leurs
 * étiquettes et leurs valeurs. Un mock de {@code MeterRegistry} n'aurait rien
 * dit de la cardinalité, qui est le vrai risque de ce composant.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolMetricsPublisherTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private SchoolUserRepository schoolUserRepository;
    @Mock private EleveRepository eleveRepository;
    @Mock private ClassGroupRepository classGroupRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private TimetableJobRepository timetableJobRepository;

    private MeterRegistry registre;

    @BeforeEach
    void setUp() {
        registre = new SimpleMeterRegistry();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant(28L, "LYC-ARIANA")));
        when(schoolUserRepository.countActiveGroupedByTenantAndRole()).thenReturn(List.of());
        when(eleveRepository.countActiveGroupedByTenant()).thenReturn(List.of());
        when(classGroupRepository.countActiveGroupedByTenant()).thenReturn(List.of());
        when(teacherRepository.countActiveGroupedByTenant()).thenReturn(List.of());
        when(timetableJobRepository.countGroupedByTenantAndStatus()).thenReturn(List.of());
        when(timetableJobRepository.findLastGenerationIntervalByTenant()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Ce que Prometheus lit ────────────────────────────────────────────────────

    @Test
    void publieUneSerieParEtablissementEtParFamilleDeMetrique() {
        when(eleveRepository.countActiveGroupedByTenant()).thenReturn(List.of(new TenantCount("28", 412)));
        when(classGroupRepository.countActiveGroupedByTenant()).thenReturn(List.of(new TenantCount("28", 16)));
        when(teacherRepository.countActiveGroupedByTenant()).thenReturn(List.of(new TenantCount("28", 37)));

        publier();

        assertThat(valeur("smartschool.school.students")).isEqualTo(412);
        assertThat(valeur("smartschool.school.classes")).isEqualTo(16);
        assertThat(valeur("smartschool.school.teachers")).isEqualTo(37);
    }

    /**
     * Deux étiquettes, et deux seulement : {@code tenant} porte l'identifiant
     * stable, {@code school} le code lisible. Le nom de l'établissement est
     * modifiable, et changer une étiquette casse la continuité de la série.
     */
    @Test
    void netiquetteQueLidentifiantEtLeCodeDeLetablissement() {
        when(eleveRepository.countActiveGroupedByTenant()).thenReturn(List.of(new TenantCount("28", 412)));

        publier();

        Gauge serie = registre.find("smartschool.school.students").gauge();
        assertThat(serie).isNotNull();
        assertThat(serie.getId().getTags()).extracting("key").containsExactlyInAnyOrder("tenant", "school");
        assertThat(serie.getId().getTag("tenant")).isEqualTo("28");
        assertThat(serie.getId().getTag("school")).isEqualTo("LYC-ARIANA");
    }

    /** La métrique « info » vaut toujours 1 : l'information est dans ses étiquettes. */
    @Test
    void portelesAttributsDeLetablissementDansUneMetriqueInfoAUn() {
        publier();

        Gauge info = registre.find("smartschool.school.info").gauge();
        assertThat(info).isNotNull();
        assertThat(info.value()).isEqualTo(1);
        assertThat(info.getId().getTag("plan")).isEqualTo("STANDARD");
        assertThat(info.getId().getTag("status")).isEqualTo("ACTIVE");
    }

    /**
     * Un établissement sans code, sans offre ni statut reste publiable, sous une
     * étiquette explicite plutôt qu'une série sans valeur.
     *
     * <p>L'entité porte pourtant des valeurs par défaut : elles ne s'appliquent
     * qu'à la construction, jamais à une ligne lue en base — d'où les {@code null}
     * posés ici après coup, qui reproduisent une ligne antérieure à ces colonnes.</p>
     */
    @Test
    void remplaceLesAttributsAbsentsParUneValeurLisible() {
        Tenant incomplet = Tenant.builder().tenantId(31L).build();
        incomplet.setPlan(null);
        incomplet.setStatus(null);
        when(tenantRepository.findAll()).thenReturn(List.of(incomplet));

        publier();

        Gauge info = registre.find("smartschool.school.info").gauge();
        assertThat(info.getId().getTag("school")).isEqualTo("unknown");
        assertThat(info.getId().getTag("plan")).isEqualTo("UNKNOWN");
        assertThat(info.getId().getTag("status")).isEqualTo("UNKNOWN");
    }

    @Test
    void ventileLesUtilisateursParRoleEtLesGenerationsParStatut() {
        when(schoolUserRepository.countActiveGroupedByTenantAndRole()).thenReturn(List.of(
                new TenantStatusCount("28", "ENSEIGNANT", 37),
                new TenantStatusCount("28", "ADMIN", 3)));
        when(timetableJobRepository.countGroupedByTenantAndStatus()).thenReturn(List.of(
                new TenantStatusCount("28", "COMPLETED", 12)));

        publier();

        assertThat(registre.find("smartschool.school.users").gauges()).hasSize(2);
        assertThat(registre.find("smartschool.school.users").tag("role", "ENSEIGNANT").gauge().value())
                .isEqualTo(37);
        assertThat(registre.find("smartschool.planning.jobs").tag("status", "COMPLETED").gauge().value())
                .isEqualTo(12);
    }

    /** La durée est calculée en Java, en secondes, à partir des deux bornes. */
    @Test
    void convertitLesBornesDeLaDerniereGenerationEnSecondes() {
        Instant debut = Instant.parse("2026-09-08T10:00:00Z");
        when(timetableJobRepository.findLastGenerationIntervalByTenant())
                .thenReturn(List.of(new TenantInterval("28", debut, debut.plusMillis(93_500))));

        publier();

        assertThat(valeur("smartschool.planning.last.generation.seconds")).isEqualTo(93.5);
    }

    // ── Les deux garde-fous ──────────────────────────────────────────────────────

    /**
     * Le garde-fou de cardinalité : au-delà du plafond, mieux vaut perdre les
     * métriques métier qu'emporter la supervision entière. Aucune requête de
     * comptage ne doit même être lancée.
     */
    @Test
    void abandonneLeReleveAuDelaDuPlafondDetablissements() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant(28L, "A"), tenant(29L, "B")));

        publier(1);

        assertThat(registre.find("smartschool.school.info").gauges()).isEmpty();
        verify(eleveRepository, never()).countActiveGroupedByTenant();
    }

    /**
     * Un {@code tenant_id} présent dans les données métier mais absent de la
     * table des établissements ne peut pas être nommé : la série est écartée
     * plutôt que publiée sous une étiquette vide.
     */
    @Test
    void ecarteLesLignesDontLetablissementNexistePlus() {
        when(eleveRepository.countActiveGroupedByTenant()).thenReturn(List.of(
                new TenantCount("28", 412),
                new TenantCount("999", 7)));

        publier();

        assertThat(registre.find("smartschool.school.students").gauges()).hasSize(1);
        assertThat(registre.find("smartschool.school.students").tag("tenant", "999").gauge()).isNull();
    }

    // ── Contexte d'exécution ─────────────────────────────────────────────────────

    /**
     * Le relevé traverse volontairement tous les établissements : le filtre
     * multi-tenant d'Hibernate ne s'active que si un tenant est en contexte. Le
     * nettoyage protège du cas où le thread de l'ordonnanceur aurait été
     * réutilisé après une requête HTTP.
     */
    @Test
    void videLeContexteDetablissementAvantDeBalayerLaBase() {
        TenantContext.setTenantId("28");

        publier();

        assertThat(TenantContext.getTenantId()).isNull();
    }

    /** Un relevé initial qui échoue ne doit pas empêcher l'application de démarrer. */
    @Test
    void laisseLapplicationDemarrerMalgreUnReleveInitialEnEchec() {
        when(tenantRepository.findAll()).thenThrow(new IllegalStateException("base indisponible"));

        publisher(500).publishOnStartup();

        assertThat(registre.find("smartschool.school.info").gauges()).isEmpty();
    }

    // ── Doubles et fabriques ─────────────────────────────────────────────────────

    private Tenant tenant(Long id, String code) {
        return Tenant.builder()
                .tenantId(id)
                .code(code)
                .name("Lycée d'Ariana")
                .plan(TenantPlan.STANDARD)
                .status(TenantStatus.ACTIVE)
                .build();
    }

    private SchoolMetricsPublisher publisher(int maxTenants) {
        return new SchoolMetricsPublisher(registre, tenantRepository, schoolUserRepository, eleveRepository,
                classGroupRepository, teacherRepository, timetableJobRepository, maxTenants);
    }

    private void publier() {
        publier(500);
    }

    private void publier(int maxTenants) {
        publisher(maxTenants).publish();
    }

    private double valeur(String metrique) {
        Gauge serie = registre.find(metrique).gauge();
        assertThat(serie).as("série %s absente du registre", metrique).isNotNull();
        return serie.value();
    }
}
