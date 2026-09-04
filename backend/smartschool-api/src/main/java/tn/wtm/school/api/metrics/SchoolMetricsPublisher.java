package tn.wtm.school.api.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
import tn.wtm.school.tenant.repository.TenantRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Publie les métriques métier par établissement sur l'endpoint Prometheus.
 *
 * <h2>Pourquoi un relevé périodique et non un {@code Gauge} classique</h2>
 * Un {@code Gauge.builder(..., repo::count)} exécute son fournisseur À CHAQUE
 * scrape de Prometheus, soit toutes les 15 s. Avec six métriques et deux cents
 * établissements, cela ferait 1 200 requêtes SQL par scrape, déclenchées par un
 * système de supervision — la supervision deviendrait la première cause de
 * charge de la base. Ici, un unique relevé périodique exécute SIX requêtes
 * groupées (une par famille de métrique, {@code GROUP BY tenant_id}) et publie
 * le résultat ; les scrapes suivants ne font que lire des valeurs en mémoire.
 *
 * <h2>Pourquoi ces requêtes traversent les établissements</h2>
 * Le filtre Hibernate multi-tenant n'est activé par
 * {@code TenantHibernateFilterAspect} que si un tenant est présent dans le
 * {@link TenantContext}. Ce composant s'exécute sur un thread de l'ordonnanceur,
 * hors de toute requête HTTP : aucun tenant en contexte, donc aucun filtre — le
 * balayage de tous les établissements est volontaire et se fait naturellement.
 * Le {@link TenantContext#clear()} en tête de méthode rend cette intention
 * explicite et protège du cas où le thread aurait été réutilisé.
 *
 * <h2>Cardinalité</h2>
 * Chaque série porte un label {@code tenant}. Le nombre de séries croît donc
 * linéairement avec le nombre d'établissements : c'est acceptable ici (quelques
 * dizaines de séries par établissement), mais c'est exactement pour cette raison
 * qu'un label {@code tenant} ne doit JAMAIS être posé sur les métriques HTTP —
 * celles-ci comptent déjà des centaines de séries par instance, et les
 * multiplier par le nombre d'écoles ferait exploser la mémoire de Prometheus.
 * Un garde-fou refuse de publier au-delà de {@code max-tenants}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smartschool.metrics.school.enabled", havingValue = "true", matchIfMissing = true)
public class SchoolMetricsPublisher {

    private final TenantRepository tenantRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final EleveRepository eleveRepository;
    private final ClassGroupRepository classGroupRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableJobRepository timetableJobRepository;

    /**
     * Au-delà de ce nombre d'établissements, le relevé est abandonné plutôt que
     * de saturer Prometheus. Mieux vaut perdre les métriques métier qu'emporter
     * la supervision entière — un incident sur l'une ne doit pas en créer un
     * sur l'autre.
     */
    private final int maxTenants;

    private final MultiGauge users;
    private final MultiGauge students;
    private final MultiGauge classes;
    private final MultiGauge teachers;
    private final MultiGauge planningJobs;
    private final MultiGauge lastGenerationSeconds;
    private final MultiGauge schoolInfo;

    public SchoolMetricsPublisher(
            MeterRegistry registry,
            TenantRepository tenantRepository,
            SchoolUserRepository schoolUserRepository,
            EleveRepository eleveRepository,
            ClassGroupRepository classGroupRepository,
            TeacherRepository teacherRepository,
            TimetableJobRepository timetableJobRepository,
            @Value("${smartschool.metrics.school.max-tenants:500}") int maxTenants) {

        this.tenantRepository = tenantRepository;
        this.schoolUserRepository = schoolUserRepository;
        this.eleveRepository = eleveRepository;
        this.classGroupRepository = classGroupRepository;
        this.teacherRepository = teacherRepository;
        this.timetableJobRepository = timetableJobRepository;
        this.maxTenants = maxTenants;

        // MultiGauge, et non Gauge : l'ensemble des labels change dans le temps
        // (un établissement est créé, un autre supprimé). Un Gauge par tenant
        // enregistré une fois pour toutes laisserait des séries fantômes que
        // Prometheus continuerait de collecter indéfiniment.
        // Une seule métrique pour les utilisateurs, ventilée par rôle : le total
        // par établissement s'obtient avec `sum by (tenant, school)`. Publier en
        // plus un total pré-calculé donnerait deux séries pour le même chiffre,
        // donc deux occasions de se contredire.
        this.users = MultiGauge.builder("smartschool.school.users")
                .description("Utilisateurs actifs de l'établissement, par rôle")
                .baseUnit("users")
                .register(registry);

        this.students = MultiGauge.builder("smartschool.school.students")
                .description("Élèves actifs de l'établissement")
                .baseUnit("students")
                .register(registry);

        this.classes = MultiGauge.builder("smartschool.school.classes")
                .description("Classes actives de l'établissement")
                .baseUnit("classes")
                .register(registry);

        this.teachers = MultiGauge.builder("smartschool.school.teachers")
                .description("Enseignants en poste dans l'établissement")
                .baseUnit("teachers")
                .register(registry);

        this.planningJobs = MultiGauge.builder("smartschool.planning.jobs")
                .description("Générations d'emploi du temps de l'établissement, par statut")
                .baseUnit("jobs")
                .register(registry);

        this.lastGenerationSeconds = MultiGauge.builder("smartschool.planning.last.generation.seconds")
                .description("Durée de la dernière génération d'emploi du temps terminée")
                .baseUnit("seconds")
                .register(registry);

        // Métrique « info » : valeur toujours à 1, l'information est dans les
        // labels. C'est la façon usuelle d'attacher des attributs qui changent
        // rarement (offre, statut) sans les coller sur toutes les autres
        // métriques — dans Grafana on joint dessus avec `* on(tenant)`.
        this.schoolInfo = MultiGauge.builder("smartschool.school.info")
                .description("Établissement présent sur la plateforme (valeur toujours 1)")
                .register(registry);
    }

    /** Premier relevé au démarrage : sans lui, les métriques restent vides jusqu'au premier cycle. */
    @PostConstruct
    void publishOnStartup() {
        try {
            publish();
        } catch (RuntimeException e) {
            // Un échec de relevé ne doit jamais empêcher l'application de démarrer.
            log.warn("[SchoolMetrics] Relevé initial impossible : {}", e.getMessage());
        }
    }

    @Scheduled(
            initialDelayString = "${smartschool.metrics.school.initial-delay-ms:60000}",
            fixedDelayString = "${smartschool.metrics.school.interval-ms:60000}")
    @Transactional(readOnly = true)
    public void publish() {
        // Intention explicite : ce relevé traverse tous les établissements.
        TenantContext.clear();

        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.size() > maxTenants) {
            log.warn("[SchoolMetrics] {} établissements pour une limite de {} : relevé abandonné "
                            + "pour ne pas saturer Prometheus. Relever `smartschool.metrics.school.max-tenants` "
                            + "après avoir vérifié la mémoire disponible.",
                    tenants.size(), maxTenants);
            return;
        }

        // tenant_id des entités métier = identifiant numérique du Tenant, sous
        // forme de chaîne (voir TenantServiceImpl.createTenantGroup).
        Map<String, Tenant> byId = tenants.stream()
                .collect(Collectors.toMap(t -> String.valueOf(t.getTenantId()), Function.identity(), (a, b) -> a));

        long start = System.nanoTime();

        schoolInfo.register(tenants.stream()
                .<MultiGauge.Row<?>>map(t -> MultiGauge.Row.of(
                        Tags.of(
                                "tenant", String.valueOf(t.getTenantId()),
                                "school", nullSafe(t.getCode()),
                                "plan", t.getPlan() == null ? "UNKNOWN" : t.getPlan().name(),
                                "status", t.getStatus() == null ? "UNKNOWN" : t.getStatus().name()),
                        1))
                .toList(), true);

        registerCounts(students, byId, eleveRepository.countActiveGroupedByTenant());
        registerCounts(classes, byId, classGroupRepository.countActiveGroupedByTenant());
        registerCounts(teachers, byId, teacherRepository.countActiveGroupedByTenant());
        registerDurations(byId, timetableJobRepository.findLastGenerationIntervalByTenant());

        registerKeyedCounts(users, byId, "role",
                schoolUserRepository.countActiveGroupedByTenantAndRole());
        registerKeyedCounts(planningJobs, byId, "status",
                timetableJobRepository.countGroupedByTenantAndStatus());

        log.debug("[SchoolMetrics] {} établissements relevés en {} ms",
                tenants.size(), (System.nanoTime() - start) / 1_000_000);
    }

    private void registerCounts(MultiGauge gauge, Map<String, Tenant> byId, List<TenantCount> counts) {
        warnOrphans(counts.stream().map(TenantCount::tenantId).toList(), byId);
        gauge.register(counts.stream()
                // Un tenant_id orphelin (établissement supprimé, données
                // résiduelles) produirait une série sans nom lisible : on
                // l'ignore plutôt que de publier un label vide.
                .filter(c -> byId.containsKey(c.tenantId()))
                .<MultiGauge.Row<?>>map(c -> MultiGauge.Row.of(tagsFor(byId, c.tenantId()), c.value()))
                .toList(), true);
    }

    /** Durée calculée côté Java : voir {@link TenantInterval} pour la raison. */
    private void registerDurations(Map<String, Tenant> byId, List<TenantInterval> intervals) {
        lastGenerationSeconds.register(intervals.stream()
                .filter(i -> byId.containsKey(i.tenantId()))
                .<MultiGauge.Row<?>>map(i -> MultiGauge.Row.of(
                        tagsFor(byId, i.tenantId()),
                        Duration.between(i.startedAt(), i.finishedAt()).toMillis() / 1000d))
                .toList(), true);
    }

    private void registerKeyedCounts(MultiGauge gauge, Map<String, Tenant> byId,
                                     String keyLabel, List<TenantStatusCount> counts) {
        warnOrphans(counts.stream().map(TenantStatusCount::tenantId).toList(), byId);
        gauge.register(counts.stream()
                .filter(c -> byId.containsKey(c.tenantId()))
                .<MultiGauge.Row<?>>map(c -> MultiGauge.Row.of(
                        tagsFor(byId, c.tenantId()).and(keyLabel, nullSafe(c.key())),
                        c.value()))
                .toList(), true);
    }

    /**
     * Deux labels, et deux seulement : {@code tenant} est l'identifiant stable
     * sur lequel s'appuient les requêtes et les jointures, {@code school} est le
     * code lisible affiché dans Grafana. Le NOM de l'établissement n'est
     * volontairement pas un label — il est modifiable, et changer un label
     * casse la continuité de la série dans Prometheus.
     */
    private Tags tagsFor(Map<String, Tenant> byId, String tenantId) {
        Tenant tenant = byId.get(tenantId);
        return Tags.of("tenant", tenantId, "school", nullSafe(tenant.getCode()));
    }

    /**
     * Signale les {@code tenant_id} présents dans les données métier mais absents
     * de la table {@code tenants}.
     *
     * Ces lignes sont exclues des métriques faute d'établissement à nommer. Les
     * écarter en silence serait le pire des deux mondes : un tableau de bord qui
     * sous-compte sans que personne ne sache pourquoi. Le message est en WARN et
     * nomme les identifiants, pour que l'écart se corrige au lieu de s'installer.
     */
    private void warnOrphans(List<String> tenantIds, Map<String, Tenant> byId) {
        List<String> orphans = tenantIds.stream()
                .filter(id -> !byId.containsKey(id))
                .distinct()
                .sorted()
                .toList();
        if (!orphans.isEmpty()) {
            log.warn("[SchoolMetrics] {} tenant_id sans établissement correspondant, exclus des "
                            + "métriques : {}. Données résiduelles d'un établissement supprimé, ou "
                            + "jeu de démo écrit avec un tenant_id qui n'est pas l'identifiant du Tenant.",
                    orphans.size(), orphans);
        }
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
