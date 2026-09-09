package tn.wtm.school.api.demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.config.TenantFilterConstants;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.security.keycloak.service.KeycloakAdminService;
import tn.wtm.school.org.dto.request.SchoolConfigRequestDTO;
import tn.wtm.school.org.dto.request.WorkingDayRequestDTO;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Eleve;
import tn.wtm.school.org.entity.Level;
import tn.wtm.school.org.entity.NationalPattern;
import tn.wtm.school.org.entity.Pattern;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Subject;
import tn.wtm.school.org.entity.SubjectLevel;
import tn.wtm.school.org.entity.SubjectSessionType;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TeachingAssignment;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.Specialite;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.EleveRepository;
import tn.wtm.school.org.repository.LevelRepository;
import tn.wtm.school.org.repository.NationalPatternRepository;
import tn.wtm.school.org.repository.PatternRepository;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.SubjectLevelRepository;
import tn.wtm.school.org.repository.SubjectRepository;
import tn.wtm.school.org.repository.SubjectSessionTypeRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TeachingAssignmentRepository;
import tn.wtm.school.org.repository.WorkingDayRepository;
import tn.wtm.school.org.service.NationalPatternService;
import tn.wtm.school.org.service.impl.SchoolConfigurationService;
import tn.wtm.school.api.NationalPatternSeeder;
import tn.wtm.school.planning.constraints.dto.request.CreateDefaultProfileRequest;
import tn.wtm.school.planning.constraints.dto.response.ConstraintProfileResponse;
import tn.wtm.school.planning.constraints.entity.ConstraintSetting;
import tn.wtm.school.planning.constraints.repository.ConstraintProfileRepository;
import tn.wtm.school.planning.constraints.repository.ConstraintSettingRepository;
import tn.wtm.school.planning.constraints.service.ConstraintProfileService;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;
import tn.wtm.school.tenant.repository.TenantRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Alimente la base avec deux établissements complets — le collège Ibn Khaldoun
 * et le collège Carthage — de l'année scolaire jusqu'aux affectations, de quoi
 * lancer une génération d'emploi du temps sur des effectifs réels.
 *
 * <h2>D'où viennent les matières</h2>
 *
 * Elles ne sont pas écrites ici. Chaque établissement <em>hérite</em> du
 * programme national semé par {@code NationalPatternSeeder} :
 * {@link NationalPatternService#applyToTenant} crée pour lui les niveaux, les
 * matières, les couples niveau × matière, les types de séance et les patterns
 * de répartition. Le seeder ne fait ensuite qu'habiller ce que le programme a
 * posé (couleurs, coefficients) et ajouter ce que le programme ne connaît pas :
 * les classes, les salles, les élèves, les enseignants et leurs affectations.
 *
 * <p>C'est la différence de fond avec la version précédente de ce runner, qui
 * recopiait à la main sa propre lecture du programme officiel. Les deux
 * transcriptions ont divergé — et c'est toujours la copie, jamais l'originale,
 * qu'on oublie de corriger. Ici, une correction de la circulaire dans
 * {@code NationalPatternSeeder} descend d'elle-même dans les deux collèges.
 *
 * <h2>Les deux établissements ne sont pas des jumeaux</h2>
 *
 * Ibn Khaldoun applique le § T.1 (collège ordinaire) sur 16 classes et
 * n'assure pas le théâtre, matière marquée (*) au tableau officiel. Carthage
 * applique le § T.3 (collège pilote) sur 10 classes, avec ses trois écarts
 * propres — français, anglais et mathématiques — et assure le théâtre. Une
 * démonstration qui bascule d'un établissement à l'autre montre donc à la fois
 * l'isolation multi-établissement et les deux programmes du secteur.
 *
 * <p>Le catalogue national est semé par ce runner lui-même, en première
 * instruction : {@code NationalPatternSeeder} est branché sur
 * {@code ApplicationReadyEvent}, publié après les {@code ApplicationRunner}, et
 * ce runner arriverait avant lui sur une base neuve.
 *
 * <h2>Reprise d'une base déjà semée</h2>
 *
 * Le runner est idempotent : sur une base déjà alimentée, il ne réécrit rien.
 * Il purge en revanche les données pédagogiques d'un établissement dans deux
 * cas — quand on le lui demande ({@code --reinitialiser-demo} ou
 * {@code smartschool.demo.reinitialiser=true}), et quand il trouve la structure
 * de l'ancien jeu de démo, dont les niveaux étaient codés {@code 7/8/9} là où le
 * programme national les désigne par {@code 7EME/8EME/9EME}. Les deux ne
 * peuvent pas cohabiter : un même collège s'y retrouverait avec deux jeux de
 * niveaux et deux jeux de matières, et la génération lirait l'un ou l'autre au
 * hasard des identifiants.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
public class DemoDataRunner implements ApplicationRunner {

    /** Année scolaire du jeu de données, et bornes de son calendrier. */
    private static final String     ANNEE       = "2026-2027";
    private static final LocalDate  RENTREE     = LocalDate.of(2026, 9, 15);
    private static final LocalDate  FIN_ANNEE   = LocalDate.of(2027, 6, 30);

    /** Suffixe des codes élèves : les deux fins d'années, « 2627 » pour 2026-2027. */
    private static final String MILLESIME = "2627";

    private static final List<String> NIVEAUX = List.of("7EME", "8EME", "9EME");

    /**
     * Service hebdomadaire visé par enseignant, en heures, pour dimensionner
     * l'effectif du corps professoral. Volontairement en dessous du plafond
     * réglementaire (18 h contre les 36 h que six jours à six heures
     * autoriseraient) : un service saturé ne laisse au solveur aucune marge de
     * placement, et le premier conflit de salle rend l'emploi du temps
     * impossible.
     */
    private static final int CIBLE_HEURES_ENSEIGNANT = 18;

    private static final int EFFECTIF_MIN = 25;
    private static final int EFFECTIF_MAX = 35;

    private static final String NOM_PROFIL_CONTRAINTES = "Profil officiel " + ANNEE;

    /**
     * Plafond journalier des élèves inscrit au profil de contraintes, en heures.
     *
     * <p>Le catalogue le fixe à 6 h par défaut, et cette contrainte est
     * CRITICAL, donc <b>dure</b> : six jours à six heures plafonnent la semaine
     * à 36 h alors que le programme officiel du collège en demande 40. Aucun
     * emploi du temps n'existerait. Huit heures rendent la semaine possible
     * (48 h de plafond pour 40 h à placer) tout en gardant la contrainte
     * mordante — sans elle, le solveur empilerait dix heures un jour et deux le
     * lendemain.
     */
    private static final int PLAFOND_HEURES_ELEVE_PAR_JOUR = 8;

    // ══════════════════════════════════════════════════════════════════════════
    // Dépendances
    // ══════════════════════════════════════════════════════════════════════════

    private final TenantRepository              tenantRepository;
    private final KeycloakAdminService          keycloakAdminService;
    private final SchoolYearRepository          schoolYearRepository;
    private final LevelRepository               levelRepository;
    private final ClassGroupRepository          classGroupRepository;
    private final SubjectRepository             subjectRepository;
    private final SubjectLevelRepository        subjectLevelRepository;
    private final SubjectSessionTypeRepository  subjectSessionTypeRepository;
    private final PatternRepository             patternRepository;
    private final TeacherRepository             teacherRepository;
    private final EleveRepository               eleveRepository;
    private final RoomRepository                roomRepository;
    private final TeachingAssignmentRepository  teachingAssignmentRepository;
    private final WorkingDayRepository          workingDayRepository;
    private final NationalPatternRepository     nationalPatternRepository;
    private final NationalPatternService        nationalPatternService;
    private final NationalPatternSeeder         nationalPatternSeeder;
    private final SchoolConfigurationService    schoolConfigurationService;

    private final ConstraintProfileService      constraintProfileService;
    private final ConstraintProfileRepository   constraintProfileRepository;
    private final ConstraintSettingRepository   constraintSettingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${smartschool.demo.reinitialiser:false}")
    private boolean reinitialiserParConfiguration;

    // ══════════════════════════════════════════════════════════════════════════
    // Entrée
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean reinitialiser = reinitialiserParConfiguration
                || args.containsOption("reinitialiser-demo");

        // Les programmes nationaux AVANT tout le reste, et semés ici plutôt
        // qu'attendus : NationalPatternSeeder est branché sur
        // ApplicationReadyEvent, publié APRÈS les ApplicationRunner. Sur une
        // base neuve, ce runner cherchait donc un catalogue que rien n'avait
        // encore écrit — il ne passait que sur une base déjà semée par un
        // démarrage précédent, c'est-à-dire jamais sur celle du jury. Le seed
        // est idempotent : sur une base à jour il ne fait que six lectures, et
        // le passage de l'événement, plus tard, n'aura rien à faire.
        nationalPatternSeeder.seed();

        for (Etablissement etablissement : etablissements()) {
            Tenant tenant = etablissement(etablissement);
            String tenantId = String.valueOf(tenant.getTenantId());

            ouvrirEtablissement(tenantId);
            try {
                if (reinitialiser || structureHeritee(tenantId)) {
                    purger(tenantId, reinitialiser);
                }
                alimenter(etablissement, tenantId);
            } finally {
                fermerEtablissement();
            }
        }
    }

    /**
     * Bascule le seeder sur un établissement, des DEUX côtés :
     * <ul>
     *   <li>écriture — {@link TenantContext}, lu par {@code TenantEntityListener}
     *       pour remplir {@code tenant_id} ;</li>
     *   <li>lecture — le filtre Hibernate {@code tenantFilter}, qui restreint
     *       tout SELECT sur une {@code TenantEntity}.</li>
     * </ul>
     *
     * <p>Sans le second, le filtre reste figé sur la valeur posée par
     * {@code TenantHibernateFilterAspect} au premier appel {@code @Transactional}
     * du run : {@code run()} est UNE seule transaction et l'aspect ne se
     * redéclenche pas sur les appels de repository, qui passent par un proxy
     * d'interface Spring Data non annoté. Le second établissement lirait alors
     * les données du premier, ne trouverait « rien » de ce qu'il cherche pour
     * lui-même, et réinsérerait des lignes déjà présentes.
     */
    private void ouvrirEtablissement(String tenantId) {
        TenantContext.setTenantId(tenantId);
        entityManager.unwrap(Session.class)
                .enableFilter(TenantFilterConstants.FILTER_NAME)
                .setParameter(TenantFilterConstants.PARAM_TENANT_ID, tenantId);
    }

    /** Symétrique de {@link #ouvrirEtablissement(String)} : ne laisse aucun établissement actif. */
    private void fermerEtablissement() {
        TenantContext.clear();
        entityManager.unwrap(Session.class)
                .disableFilter(TenantFilterConstants.FILTER_NAME);
    }

    private void alimenter(Etablissement etablissement, String tenantId) {
        SchoolYear annee = anneeScolaire();
        calendrier(etablissement, tenantId);

        Map<String, Level> niveaux = programmeNational(etablissement, annee, tenantId);
        habillerMatieres(etablissement, niveaux);

        Map<String, List<ClassGroup>> classes = classes(etablissement, niveaux, annee);
        salles(etablissement);
        eleves(etablissement, classes);
        affectations(etablissement, annee, niveaux, classes);
        profilDeContraintes(annee, tenantId);

        log.info("[jeu-de-donnees] {} : {} classes, {} élèves, {} enseignants, {} salles, {} affectations",
                etablissement.nom(),
                classes.values().stream().mapToInt(List::size).sum(),
                compter("eleves", tenantId),
                compter("enseignants", tenantId),
                compter("salles", tenantId),
                compter("affectations_enseignants", tenantId));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Le catalogue des deux établissements
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Un établissement du jeu de données : ce qui le distingue de l'autre, et
     * rien d'autre. Tout le reste — matières, volumes, découpage des séances —
     * vient du programme national désigné par {@link #pilote}.
     *
     * @param classesParNiveau nombre de classes à ouvrir par code de niveau
     * @param matieresNonAssurees codes des matières que l'établissement n'assure
     *        pas : elles restent au programme mais sont marquées non enseignées,
     *        leur pattern est désactivé et aucun enseignant ne leur est affecté
     */
    private record Etablissement(
            String code,
            String nom,
            String prefixe,
            TenantPlan plan,
            String adresse,
            String telephone,
            boolean pilote,
            Map<String, Integer> classesParNiveau,
            Set<String> matieresNonAssurees,
            List<SpecSalle> salles,
            SchoolConfigRequestDTO calendrier) {
    }

    /** Une série de salles identiques : {@code A01}, {@code A02}, … */
    private record SpecSalle(String prefixeCode, RoomType type, int nombre,
                             int capacite, String bloc, String etage) {
    }

    private List<Etablissement> etablissements() {
        return List.of(ibnKhaldoun(), carthage());
    }

    /**
     * Collège Ibn Khaldoun — programme ordinaire (§ T.1), 16 classes.
     *
     * <p>Le théâtre n'est pas assuré : le tableau officiel le marque (*), « pour
     * les collèges qui assurent la matière ». C'est le cas courant, et c'est
     * aussi ce qui donne au jeu de données un exemple de matière au programme
     * mais désactivée dans l'établissement.
     */
    private Etablissement ibnKhaldoun() {
        return new Etablissement(
                "IBN_KHALDOUN", "Collège Ibn Khaldoun", "IBN", TenantPlan.PREMIUM,
                "Avenue de la République, Ariana", "+216 71 700 100",
                false,
                Map.of("7EME", 7, "8EME", 5, "9EME", 4),
                Set.of("THEATRE"),
                List.of(
                        // 22 salles ordinaires pour 16 classes : le programme place
                        // environ 30 h de classe entière par classe et par semaine,
                        // soit 480 h à loger dans une semaine de 49 h. Onze salles
                        // suffiraient à l'arithmétique ; le solveur a besoin du
                        // reste pour ne pas buter sur un conflit à chaque créneau.
                        new SpecSalle("A", RoomType.NORMALE, 12, 36, "A", "0"),
                        new SpecSalle("B", RoomType.NORMALE, 10, 36, "B", "1"),
                        // Laboratoires dimensionnés pour un demi-groupe (18 places).
                        // Quatre par discipline et non deux : une séance de TP en
                        // demi-groupe occupe DEUX salles au même créneau, et la
                        // rotation apparie systématiquement un TP de physique avec
                        // un TP de SVT.
                        new SpecSalle("LSVT", RoomType.LABSCIENCE, 4, 18, "C", "0"),
                        new SpecSalle("LPHY", RoomType.LABPHYSIQUE, 4, 18, "C", "1"),
                        new SpecSalle("LINF", RoomType.LABINFORMATIQUE, 4, 18, "D", "0"),
                        new SpecSalle("LTEC", RoomType.LABTECHNIQUE, 3, 18, "D", "1"),
                        // Pas de bibliothèque ni de salle de dessin : le domaine
                        // du solveur n'est pas filtré par type, toute salle est
                        // une valeur possible pour toute séance. Une salle
                        // qu'aucun pattern n'exige n'ajoute donc que du bruit —
                        // la première génération y a placé une séance d'EPS.
                        new SpecSalle("GYM", RoomType.SALLESPORT, 3, 40, "E", "0")),
                calendrier(
                        journee(DayOfWeek.MONDAY,    "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.TUESDAY,   "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.WEDNESDAY, "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.THURSDAY,  "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.FRIDAY,    "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.SATURDAY,  "08:00", "12:00", null,    null)));
    }

    /**
     * Collège Carthage — programme pilote (§ T.3), 10 classes.
     *
     * <p>Le pilote demande une heure de plus en anglais et une séance de plus en
     * mathématiques : sa semaine élève est plus lourde, d'où le samedi
     * après-midi conservé là où Ibn Khaldoun s'arrête à midi.
     */
    private Etablissement carthage() {
        return new Etablissement(
                "CARTHAGE", "Collège pilote de Carthage", "CAR", TenantPlan.STANDARD,
                "Rue Hannibal, Carthage", "+216 71 730 200",
                true,
                Map.of("7EME", 4, "8EME", 3, "9EME", 3),
                Set.of(),
                List.of(
                        new SpecSalle("CA", RoomType.NORMALE, 8, 36, "A", "0"),
                        new SpecSalle("CB", RoomType.NORMALE, 7, 36, "B", "1"),
                        new SpecSalle("CSVT", RoomType.LABSCIENCE, 3, 18, "C", "0"),
                        new SpecSalle("CPHY", RoomType.LABPHYSIQUE, 3, 18, "C", "1"),
                        new SpecSalle("CINF", RoomType.LABINFORMATIQUE, 3, 18, "D", "0"),
                        new SpecSalle("CTEC", RoomType.LABTECHNIQUE, 2, 18, "D", "1"),
                        new SpecSalle("CGYM", RoomType.SALLESPORT, 2, 40, "E", "0")),
                calendrier(
                        journee(DayOfWeek.MONDAY,    "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.TUESDAY,   "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.WEDNESDAY, "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.THURSDAY,  "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.FRIDAY,    "08:00", "13:00", "14:00", "18:00"),
                        journee(DayOfWeek.SATURDAY,  "08:00", "13:00", null,    null)));
    }

    /**
     * Créneaux de trente minutes, et non d'une heure : {@code LessonGenerator}
     * convertit une durée en créneaux à raison de deux par heure, et une séance
     * d'une heure trente n'aurait aucune place à poser sur une grille horaire.
     */
    private SchoolConfigRequestDTO calendrier(WorkingDayRequestDTO... jours) {
        return new SchoolConfigRequestDTO(List.of(jours),
                SchoolConfigurationService.DEFAULT_SLOT_DURATION_MINUTES);
    }

    private WorkingDayRequestDTO journee(DayOfWeek jour, String debutMatin, String finMatin,
                                          String debutApresMidi, String finApresMidi) {
        return new WorkingDayRequestDTO(
                jour, true,
                LocalTime.parse(debutMatin),
                LocalTime.parse(finMatin),
                debutApresMidi != null ? LocalTime.parse(debutApresMidi) : null,
                finApresMidi   != null ? LocalTime.parse(finApresMidi)   : null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Établissement, année, calendrier
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * L'établissement au sens {@code Tenant}. Le discriminant multi-établissement
     * est l'identifiant du tenant rendu en texte — c'est ce que
     * {@code TenantServiceImpl.create()} écrit et ce que le JWT porte dans le
     * claim {@code tenant_id}.
     */
    private Tenant etablissement(Etablissement specification) {
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(specification.code())
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .code(specification.code())
                        .name(specification.nom())
                        .etablismentType(EtablissementType.COLLEGE)
                        .address(specification.adresse())
                        .phone(specification.telephone())
                        .active(true)
                        .status(TenantStatus.ACTIVE)
                        .plan(specification.plan())
                        .build()));
        return espaceAuthentification(tenant);
    }

    /**
     * Le groupe Keycloak de l'établissement, que le seeder doit créer lui-même.
     *
     * <p>Un tenant né ici n'est pas passé par {@code TenantServiceImpl.create()},
     * seul endroit qui crée ce groupe : sa colonne {@code keycloak_group_id}
     * restait vide. Or créer un compte pour cet établissement commence par
     * demander ce groupe — sur une base neuve, toute création de compte
     * échouait donc, alors que l'établissement, lui, était bien là.
     *
     * <p>L'échec Keycloak n'interrompt pas l'amorçage : le jeu de démonstration
     * doit rester consultable même si le realm n'est pas joignable.
     */
    private Tenant espaceAuthentification(Tenant tenant) {
        if (tenant.getKeycloakGroupId() != null && !tenant.getKeycloakGroupId().isBlank()) {
            return tenant;
        }
        try {
            String groupId = keycloakAdminService.ensureTenantGroup(
                    String.valueOf(tenant.getTenantId()), tenant.getName());
            tenant.setKeycloakGroupId(groupId);
            return tenantRepository.save(tenant);
        } catch (RuntimeException e) {
            log.warn("[Demo] Groupe Keycloak non provisionné pour '{}' ({}) : {}. "
                            + "La création de comptes restera impossible pour cet établissement.",
                    tenant.getName(), tenant.getTenantId(), e.getMessage());
            return tenant;
        }
    }

    private SchoolYear anneeScolaire() {
        String tenantId = TenantContext.getRequiredTenantId();
        return schoolYearRepository.findByTenantIdAndNom(tenantId, ANNEE)
                .orElseGet(() -> schoolYearRepository.save(SchoolYear.builder()
                        .nom(ANNEE)
                        .dateDebut(RENTREE)
                        .dateFin(FIN_ANNEE)
                        .estActive(true)
                        .estCourante(true)
                        .build()));
    }

    /**
     * Jours ouvrés et créneaux. {@code configure()} remet tout à plat à chaque
     * appel — on ne l'appelle donc que si l'établissement n'a pas encore de
     * calendrier, faute de quoi chaque démarrage détruirait et recréerait les
     * créneaux auxquels les emplois du temps déjà générés se réfèrent.
     */
    private void calendrier(Etablissement etablissement, String tenantId) {
        if (workingDayRepository.findByTenantIdOrderByDayOfWeekAsc(tenantId).isEmpty()) {
            schoolConfigurationService.configure(etablissement.calendrier());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Héritage du programme national
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Applique à l'établissement les trois programmes de son secteur — un par
     * niveau — et renvoie les niveaux ainsi créés.
     *
     * <p>Le programme est désigné par son code et non par
     * {@code applyByRequest}, qui retient toujours la version la plus élevée,
     * c'est-à-dire le § T.1. Un collège pilote doit pouvoir demander le § T.3.
     */
    private Map<String, Level> programmeNational(Etablissement etablissement,
                                                  SchoolYear annee,
                                                  String tenantId) {
        Map<String, Level> niveaux = new LinkedHashMap<>();
        String suffixe = etablissement.pilote() ? "_PILOTE" : "_OFFICIEL";

        for (String niveau : NIVEAUX) {
            String code = "COLLEGE_" + niveau + suffixe;
            NationalPattern programme = nationalPatternRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException(
                            "Le jeu de données demande le programme national " + code
                            + ", que NationalPatternSeeder vient pourtant de semer. Le code "
                            + "attendu a dû changer d'un côté sans l'autre."));

            nationalPatternService.applyToTenant(
                    programme.getIdNationalPattern(), annee.getIdAnnee());

            niveaux.put(niveau, levelRepository.findByTenantIdAndCode(tenantId, niveau)
                    .orElseThrow(() -> new IllegalStateException(
                            "Le niveau " + niveau + " n'a pas été créé par l'héritage du programme "
                            + code + " pour l'établissement " + tenantId)));
        }
        return niveaux;
    }

    /** Couleur d'affichage par matière — reprise telle quelle par la grille du planning. */
    private static final Map<String, String> COULEURS = Map.ofEntries(
            Map.entry("AR",      "#f97316"),
            Map.entry("FR",      "#3b82f6"),
            Map.entry("EN",      "#8b5cf6"),
            Map.entry("MATH",    "#ef4444"),
            Map.entry("SCI",     "#22c55e"),
            Map.entry("PHY",     "#06b6d4"),
            Map.entry("HISTGEO", "#a16207"),
            Map.entry("ISL",     "#10b981"),
            Map.entry("CIV",     "#f59e0b"),
            Map.entry("INFO",    "#0ea5e9"),
            Map.entry("TECH",    "#64748b"),
            Map.entry("SPORT",   "#ec4899"),
            Map.entry("MUS",     "#d946ef"),
            Map.entry("DESSIN",  "#14b8a6"),
            Map.entry("THEATRE", "#9333ea"));

    private static final Map<String, String> ABREVIATIONS = Map.ofEntries(
            Map.entry("AR", "Ar"),        Map.entry("FR", "Fr"),
            Map.entry("EN", "Ang"),       Map.entry("MATH", "Math"),
            Map.entry("SCI", "SVT"),      Map.entry("PHY", "Phys"),
            Map.entry("HISTGEO", "H-G"),  Map.entry("ISL", "Isl"),
            Map.entry("CIV", "Civ"),      Map.entry("INFO", "Info"),
            Map.entry("TECH", "Tech"),    Map.entry("SPORT", "EPS"),
            Map.entry("MUS", "Mus"),      Map.entry("DESSIN", "Des"),
            Map.entry("THEATRE", "Théâ"));

    /**
     * Coefficients du collège. Ils ne servent pas au solveur mais aux bulletins
     * et à l'affichage ; sans eux la colonne reste vide dans toute l'interface.
     */
    private static final Map<String, Double> COEFFICIENTS = Map.ofEntries(
            Map.entry("AR", 2.0),      Map.entry("FR", 2.0),
            Map.entry("MATH", 2.0),    Map.entry("EN", 1.5),
            Map.entry("SCI", 1.5),     Map.entry("PHY", 1.5),
            Map.entry("HISTGEO", 1.0), Map.entry("ISL", 1.0),
            Map.entry("CIV", 1.0),     Map.entry("INFO", 1.0),
            Map.entry("TECH", 1.0),    Map.entry("SPORT", 1.0),
            Map.entry("MUS", 0.5),     Map.entry("DESSIN", 0.5),
            Map.entry("THEATRE", 0.5));

    /**
     * Les matières fondamentales, au sens de {@code MAIN_SUBJECTS_MORNING_QUOTA}
     * — « trois quarts des matières fondamentales le matin ». Les quatre
     * matières à coefficient 2 et l'anglais en sont ; y ajouter les sciences
     * ferait passer le quota au-dessus de ce qu'une semaine de matinées peut
     * contenir, et la contrainte serait violée sur tout emploi du temps.
     */
    private static final Set<String> MATIERES_FONDAMENTALES = Set.of("AR", "FR", "MATH", "EN");

    /**
     * Complète ce que l'héritage du programme ne renseigne pas — couleur,
     * abréviation, coefficient, matière fondamentale — et éteint les matières
     * que l'établissement n'assure pas.
     */
    private void habillerMatieres(Etablissement etablissement, Map<String, Level> niveaux) {
        String tenantId = TenantContext.getRequiredTenantId();

        for (String code : COULEURS.keySet()) {
            Optional<Subject> trouvee = subjectRepository.findByTenantIdAndCodeMatiere(tenantId, code);
            if (trouvee.isEmpty()) {
                continue; // matière absente du programme de ce secteur
            }
            Subject matiere = trouvee.get();
            boolean assuree = !etablissement.matieresNonAssurees().contains(code);

            matiere.setCouleur(COULEURS.get(code));
            matiere.setAbreviation(ABREVIATIONS.get(code));
            matiere.setEstPrincipale(MATIERES_FONDAMENTALES.contains(code));
            matiere.setEstEnseignee(assuree);
            subjectRepository.save(matiere);

            for (Level niveau : niveaux.values()) {
                subjectLevelRepository
                        .findBySubject_IdMatiereAndLevel_IdNiveau(
                                matiere.getIdMatiere(), niveau.getIdNiveau())
                        .ifPresent(sl -> {
                            sl.setCoefficient(COEFFICIENTS.get(code));
                            // Deux heures consécutives au plus : c'est la règle
                            // du § III.1, et MAX_TWO_CONSECUTIVE_SESSIONS_SAME_SUBJECT
                            // s'y adosse.
                            sl.setMaxHeuresConsecutives(2);
                            subjectLevelRepository.save(sl);

                            // Une matière non assurée garde son pattern en base —
                            // le programme officiel ne change pas parce qu'un
                            // collège manque de professeur — mais son volume
                            // hebdomadaire passe à zéro.
                            //
                            // C'est le volume, et lui seul, qui déclare qu'une
                            // matière n'est pas enseignée : CurriculumLoader bâtit
                            // le programme attendu à partir de
                            // niveaux_matieres.heures_semaine, et un volume non nul
                            // sans affectation d'enseignant est refusé par le
                            // contrôle d'avant génération — MATIERE_SANS_ENSEIGNANT,
                            // une fois par classe. Désactiver le pattern ne suffit
                            // pas : le générateur de séances retombe alors sur le
                            // type de séance. On fait donc les deux, le pattern
                            // désactivé disant au lecteur humain ce que le zéro dit
                            // au solveur.
                            if (!assuree) {
                                sl.setHeuresSemaine(0.0);
                                subjectLevelRepository.save(sl);
                                patternRepository
                                        .findBySubjectLevel_IdNiveauMatiere(sl.getIdNiveauMatiere())
                                        .forEach(p -> {
                                            p.setActive(false);
                                            patternRepository.save(p);
                                        });
                            }
                        });
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Classes, salles, élèves
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Ouvre les classes de chaque niveau, codées {@code 7B1}, {@code 7B2}, … —
     * la notation en usage dans les collèges tunisiens, où le chiffre est le
     * niveau et le rang suit la lettre de section.
     */
    private Map<String, List<ClassGroup>> classes(Etablissement etablissement,
                                                   Map<String, Level> niveaux,
                                                   SchoolYear annee) {
        String tenantId = TenantContext.getRequiredTenantId();
        Map<String, List<ClassGroup>> parNiveau = new LinkedHashMap<>();

        for (String codeNiveau : NIVEAUX) {
            int nombre = etablissement.classesParNiveau().getOrDefault(codeNiveau, 0);
            Level niveau = niveaux.get(codeNiveau);
            String chiffre = codeNiveau.substring(0, 1);
            List<ClassGroup> classes = new ArrayList<>();

            for (int rang = 1; rang <= nombre; rang++) {
                String code = chiffre + "B" + rang;
                classes.add(classGroupRepository.findByTenantIdAndCode(tenantId, code)
                        .orElseGet(() -> classGroupRepository.save(ClassGroup.builder()
                                .code(code)
                                // Tronc commun : au collège, aucune classe n'est
                                // encore orientée vers une spécialité.
                                .codeSpecialite(Specialite.TCOM)
                                .estActif(true)
                                .level(niveau)
                                .schoolYear(annee)
                                .build())));
            }
            parNiveau.put(codeNiveau, classes);
        }
        return parNiveau;
    }

    private void salles(Etablissement etablissement) {
        String tenantId = TenantContext.getRequiredTenantId();

        for (SpecSalle spec : etablissement.salles()) {
            for (int rang = 1; rang <= spec.nombre(); rang++) {
                String code = spec.prefixeCode() + String.format("%02d", rang);
                if (roomRepository.existsByTenantIdAndCodeSalle(tenantId, code)) {
                    continue;
                }
                roomRepository.save(Room.builder()
                        .codeSalle(code)
                        .typeSalle(spec.type())
                        .capacite(spec.capacite())
                        .codeBloc(spec.bloc())
                        .numEtage(spec.etage())
                        .equipements(equipements(spec.type()))
                        .estDisponible(true)
                        .build());
            }
        }
    }

    private String equipements(RoomType type) {
        return switch (type) {
            case LABSCIENCE      -> "PAILLASSES,MICROSCOPES,HOTTE,TABLEAU_BLANC";
            case LABPHYSIQUE     -> "PAILLASSES,OSCILLOSCOPES,ALIMENTATIONS,TABLEAU_BLANC";
            case LABINFORMATIQUE -> "18_POSTES,VIDEOPROJECTEUR,RESEAU,CLIMATISATION";
            case LABTECHNIQUE    -> "ETABLIS,OUTILLAGE,VIDEOPROJECTEUR";
            case SALLESPORT      -> "VESTIAIRES,PANIERS_BASKET,TAPIS";
            case BIBLIOTHEQUE    -> "RAYONNAGES,POSTES_CONSULTATION";
            default              -> "TABLEAU_BLANC,VIDEOPROJECTEUR";
        };
    }

    /**
     * Inscrit entre 25 et 35 élèves par classe et reporte l'effectif obtenu sur
     * la classe.
     *
     * <p>Ce report n'est pas cosmétique : {@code Lesson.classStudentCount} le lit
     * et la contrainte dure de capacité de salle s'y adosse. Une classe laissée
     * à son effectif par défaut ferait tenir trente-cinq élèves dans un
     * laboratoire de dix-huit places sans que rien ne proteste.
     *
     * <p>L'effectif est tiré du code de la classe, donc stable d'un démarrage à
     * l'autre : {@code 7B1} aura toujours le même nombre d'élèves, et les mêmes.
     */
    private void eleves(Etablissement etablissement, Map<String, List<ClassGroup>> classes) {
        String tenantId = TenantContext.getRequiredTenantId();
        int numero = 1;

        for (List<ClassGroup> classesDuNiveau : classes.values()) {
            for (ClassGroup classe : classesDuNiveau) {
                int effectif = effectifDe(classe.getCode());

                for (int rang = 0; rang < effectif; rang++) {
                    String code = etablissement.prefixe() + "-" + MILLESIME + "-"
                            + String.format("%04d", numero++);
                    if (eleveRepository.existsByTenantIdAndCodeEleve(tenantId, code)) {
                        continue;
                    }
                    // Le décalage par le code de la classe évite que toutes les
                    // classes ouvrent sur le même patronyme.
                    int graine = Math.floorMod(classe.getCode().hashCode(), 97) + rang;
                    String nom    = NomsTunisiens.patronyme(graine * 3 + 1);
                    String prenom = NomsTunisiens.prenom(graine);

                    eleveRepository.save(Eleve.builder()
                            .codeEleve(code)
                            .nom(nom)
                            .prenom(prenom)
                            .numIdentite(String.format("%08d", 10_000_000 + numero))
                            .email(NomsTunisiens.identifiantMail(prenom, nom) + "."
                                    + numero + "@eleve."
                                    + etablissement.prefixe().toLowerCase(Locale.ROOT) + ".tn")
                            .telephone("+216 " + String.format("%02d", 20 + (numero % 10))
                                    + " " + String.format("%03d", 100 + (numero % 900))
                                    + " " + String.format("%03d", 100 + (numero % 887)))
                            .classeGroup(classe)
                            .estActif(true)
                            .build());
                }

                classe.setNbEleve(effectif);
                classGroupRepository.save(classe);
            }
        }
    }

    /** Effectif d'une classe : entre {@link #EFFECTIF_MIN} et {@link #EFFECTIF_MAX}, stable. */
    private int effectifDe(String codeClasse) {
        int amplitude = EFFECTIF_MAX - EFFECTIF_MIN + 1;
        return EFFECTIF_MIN + Math.floorMod(codeClasse.hashCode(), amplitude);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Enseignants et affectations
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Recrute le corps professoral au vu du service à assurer, puis affecte
     * chaque classe.
     *
     * <p>Le nombre d'enseignants d'une matière n'est pas écrit en dur : il se
     * déduit du programme hérité. On somme, pour chaque niveau, les heures que
     * la matière coûte à un enseignant — les séances en demi-groupe comptent
     * double, l'enseignant les donnant à chacune des deux moitiés — puis on
     * divise par le service visé. Corriger la circulaire ajuste donc le
     * recrutement au démarrage suivant, sans toucher à ce fichier.
     */
    private void affectations(Etablissement etablissement,
                               SchoolYear annee,
                               Map<String, Level> niveaux,
                               Map<String, List<ClassGroup>> classes) {

        for (String codeMatiere : COULEURS.keySet().stream().sorted().toList()) {
            if (etablissement.matieresNonAssurees().contains(codeMatiere)) {
                continue;
            }

            Map<String, SubjectLevel> parNiveau = new LinkedHashMap<>();
            Map<String, Double> heuresParNiveau = new LinkedHashMap<>();
            double service = 0;

            for (String codeNiveau : NIVEAUX) {
                SubjectLevel sl = coupleNiveauMatiere(niveaux.get(codeNiveau), codeMatiere);
                if (sl == null) {
                    continue;
                }
                double heures = heuresEnseignantParClasse(sl, annee);
                if (heures <= 0) {
                    continue;
                }
                parNiveau.put(codeNiveau, sl);
                heuresParNiveau.put(codeNiveau, heures);
                service += heures * classes.get(codeNiveau).size();
            }

            if (parNiveau.isEmpty()) {
                continue; // matière absente du programme de ce secteur
            }

            int effectif = Math.max(1, (int) Math.ceil(service / CIBLE_HEURES_ENSEIGNANT));
            List<Teacher> corps = enseignants(etablissement, codeMatiere, effectif);
            repartir(annee, corps, classes, parNiveau, heuresParNiveau);
        }
    }

    /**
     * Les heures qu'une classe coûte à son enseignant pour une matière donnée.
     *
     * <p>Lu sur le pattern hérité, et non sur {@code heuresSemaine} : les deux
     * diffèrent dès qu'il y a demi-groupe. Le § T.2 le dit en toutes lettres —
     * l'horaire de l'élève et celui de l'enseignant ne sont pas le même nombre.
     */
    private double heuresEnseignantParClasse(SubjectLevel sl, SchoolYear annee) {
        List<Pattern> patterns = patternRepository
                .findBySubjectLevelWithDetails(sl.getIdNiveauMatiere());

        Optional<Pattern> retenu = patterns.stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActive()))
                .filter(p -> p.getSchoolYear() != null
                        && p.getSchoolYear().getIdAnnee().equals(annee.getIdAnnee()))
                .max(Comparator.comparingLong(Pattern::getIdPattern));

        if (retenu.isEmpty()) {
            return 0;
        }
        double heures = 0;
        for (PatternDetail detail : retenu.get().getPatternDetails()) {
            heures += detail.getDuration() * (Boolean.TRUE.equals(detail.getIsSplit()) ? 2 : 1);
        }
        return heures;
    }

    /**
     * Répartit les classes entre les enseignants d'une matière : à chaque
     * classe, celui dont le service est le plus léger.
     *
     * <p>Les classes sont parcourues en alternant les niveaux — {@code 7B1},
     * {@code 8B1}, {@code 9B1}, {@code 7B2}… — et, à service égal, on préfère un
     * enseignant qui n'a pas encore ce niveau. Les deux ensemble servent
     * {@code TEACHER_MIN_TWO_LEVELS} : un professeur du secondaire tunisien
     * enseigne normalement sur plusieurs niveaux, et un tirage naïf lui donnait
     * les sept classes de septième.
     */
    private void repartir(SchoolYear annee,
                           List<Teacher> corps,
                           Map<String, List<ClassGroup>> classes,
                           Map<String, SubjectLevel> parNiveau,
                           Map<String, Double> heuresParNiveau) {

        double[] service = new double[corps.size()];
        List<Set<String>> niveauxCouverts = new ArrayList<>();
        corps.forEach(t -> niveauxCouverts.add(new HashSet<>()));

        for (ClassGroup classe : classesEntrelacees(classes)) {
            String codeNiveau = classe.getLevel().getCode();
            SubjectLevel sl = parNiveau.get(codeNiveau);
            if (sl == null) {
                continue;
            }

            int choisi = 0;
            for (int i = 1; i < corps.size(); i++) {
                boolean plusLeger = service[i] < service[choisi] - 1e-9;
                boolean aEgalitePlusVarie = Math.abs(service[i] - service[choisi]) < 1e-9
                        && !niveauxCouverts.get(i).contains(codeNiveau)
                        && niveauxCouverts.get(choisi).contains(codeNiveau);
                if (plusLeger || aEgalitePlusVarie) {
                    choisi = i;
                }
            }

            service[choisi] += heuresParNiveau.get(codeNiveau);
            niveauxCouverts.get(choisi).add(codeNiveau);
            affecter(annee, classe, sl, corps.get(choisi));
        }
    }

    /** {@code 7B1, 8B1, 9B1, 7B2, 8B2, …} — les niveaux alternés, puis le reste. */
    private List<ClassGroup> classesEntrelacees(Map<String, List<ClassGroup>> classes) {
        int plusLong = classes.values().stream().mapToInt(List::size).max().orElse(0);
        List<ClassGroup> entrelacees = new ArrayList<>();
        for (int rang = 0; rang < plusLong; rang++) {
            for (String codeNiveau : NIVEAUX) {
                List<ClassGroup> duNiveau = classes.getOrDefault(codeNiveau, List.of());
                if (rang < duNiveau.size()) {
                    entrelacees.add(duNiveau.get(rang));
                }
            }
        }
        return entrelacees;
    }

    /**
     * Une affectation par type de séance : le programme distingue le cours du TP
     * et de la séance de groupe, et {@code LessonGenerator} n'engendre les
     * séances d'un type que si une affectation le porte. Le même enseignant
     * prend tous les types de sa classe — {@code ONE_TEACHER_PER_SUBJECT_CLASS}
     * est une contrainte dure.
     */
    private void affecter(SchoolYear annee, ClassGroup classe, SubjectLevel sl, Teacher enseignant) {
        for (SubjectSessionType type :
                subjectSessionTypeRepository.findBySubjectLevel_IdNiveauMatiere(sl.getIdNiveauMatiere())) {

            boolean existe = teachingAssignmentRepository
                    .existsBySchoolYear_IdAnneeAndClassGroup_IdClasseAndSubjectLevel_IdNiveauMatiereAndSubjectSessionType_IdSubjectSessionType(
                            annee.getIdAnnee(),
                            classe.getIdClasse(),
                            sl.getIdNiveauMatiere(),
                            type.getIdSubjectSessionType());
            if (existe) {
                continue;
            }
            teachingAssignmentRepository.save(TeachingAssignment.builder()
                    .schoolYear(annee)
                    .teacher(enseignant)
                    .classGroup(classe)
                    .subjectLevel(sl)
                    .subjectSessionType(type)
                    .priority(1)
                    .isActive(true)
                    .build());
        }
    }

    /** Le corps enseignant d'une matière, créé à la demande. */
    private List<Teacher> enseignants(Etablissement etablissement, String codeMatiere, int effectif) {
        String tenantId = TenantContext.getRequiredTenantId();
        String libelle = ABREVIATIONS.getOrDefault(codeMatiere, codeMatiere);
        List<Teacher> corps = new ArrayList<>();

        for (int rang = 1; rang <= effectif; rang++) {
            String code = etablissement.prefixe() + "-" + codeMatiere + "-" + String.format("%02d", rang);
            Optional<Teacher> existant = teacherRepository.findByTenantIdAndCodeEnseignant(tenantId, code);
            if (existant.isPresent()) {
                corps.add(existant.get());
                continue;
            }

            int graine = Math.floorMod(code.hashCode(), 991);
            String nom    = NomsTunisiens.patronyme(graine);
            String prenom = NomsTunisiens.prenom(graine * 5 + 3);

            corps.add(teacherRepository.save(Teacher.builder()
                    .codeEnseignant(code)
                    .numIdentite(String.format("%08d", 20_000_000 + Math.floorMod(code.hashCode(), 9_000_000)))
                    .nom(nom)
                    .prenom(prenom)
                    .email(NomsTunisiens.identifiantMail(prenom, nom) + "."
                            + code.toLowerCase(Locale.ROOT) + "@"
                            + etablissement.prefixe().toLowerCase(Locale.ROOT) + ".edu.tn")
                    .telephone("+216 " + String.format("%02d", 90 + (rang % 9))
                            + " " + String.format("%03d", 200 + (graine % 700))
                            + " " + String.format("%03d", 100 + (graine % 800)))
                    // Le plafond hebdomadaire n'est pas lu par le solveur — il
                    // raisonne sur le plafond journalier, jours ouvrés compris —
                    // mais il documente le service et sert aux écrans RH.
                    .maxHeuresSemaine(24)
                    .maxHeuresJour(6)
                    .minHeuresJour(0)
                    .specialite(libelle)
                    .estEnPoste(true)
                    .build()));
        }
        return corps;
    }

    private SubjectLevel coupleNiveauMatiere(Level niveau, String codeMatiere) {
        String tenantId = TenantContext.getRequiredTenantId();
        return subjectRepository.findByTenantIdAndCodeMatiere(tenantId, codeMatiere)
                .flatMap(matiere -> subjectLevelRepository
                        .findBySubject_IdMatiereAndLevel_IdNiveau(
                                matiere.getIdMatiere(), niveau.getIdNiveau()))
                .orElse(null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Profil de contraintes
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Un profil peuplé du catalogue complet, avec les valeurs par défaut de
     * chaque contrainte — et non une poignée de réglages choisis à la main,
     * qui laissait quinze contraintes du catalogue absentes du profil sans que
     * l'écran de réglage le signale.
     *
     * <p>Seul le plafond journalier des élèves est relevé : voir
     * {@link #PLAFOND_HEURES_ELEVE_PAR_JOUR}.
     */
    private void profilDeContraintes(SchoolYear annee, String tenantId) {
        if (constraintProfileRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYearId(
                tenantId, NOM_PROFIL_CONTRAINTES, annee.getIdAnnee())) {
            return;
        }

        ConstraintProfileResponse profil = constraintProfileService.createDefault(
                CreateDefaultProfileRequest.builder()
                        .name(NOM_PROFIL_CONTRAINTES)
                        .schoolYearId(annee.getIdAnnee())
                        .build());

        for (ConstraintSetting reglage : constraintSettingRepository
                .findActiveByProfileAndTenantId(profil.getIdConstraintProfile(), tenantId)) {
            if ("MAX_STUDENT_HOURS_PER_DAY".equals(reglage.getDefinition().getCode())) {
                reglage.setParametersJson("{\"maxHours\":" + PLAFOND_HEURES_ELEVE_PAR_JOUR + "}");
                constraintSettingRepository.save(reglage);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Reprise : détection de l'ancienne structure et purge
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Vrai si l'établissement porte des niveaux qui ne viennent pas du programme
     * national — la signature de l'ancien jeu de démo, qui les codait
     * {@code 7}, {@code 8}, {@code 9}.
     */
    private boolean structureHeritee(String tenantId) {
        Long anciens = entityManager.createQuery("""
                        SELECT COUNT(l) FROM Level l
                        WHERE l.tenantId = :tenantId AND l.code NOT IN :codes
                        """, Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("codes", NIVEAUX)
                .getSingleResult();
        return anciens != null && anciens > 0;
    }

    /**
     * Les tables pédagogiques d'un établissement, dans l'ordre où les clés
     * étrangères permettent de les vider. Les {@code tenants} et les
     * {@code subscriptions} n'y figurent pas : l'établissement lui-même, son
     * abonnement et ses comptes Keycloak survivent à la purge.
     */
    private static final List<String> TABLES_A_PURGER = List.of(
            "ligne_appel", "historique_appel", "justificatif_absence",
            "seance_appel", "cahier_seance",
            "justificatif_pointage", "presence_personnel", "suivi_heures_enseignant",
            "planning_timetable_session", "planning_generated_timetable", "planning_timetable_job",
            "constraint_setting", "custom_constraint", "constraint_profile",
            "eleves", "affectations_enseignants",
            "pattern_details", "patterns", "types_seance_matiere",
            "niveaux_matieres", "matieres",
            "school_user", "enseignants",
            "classes", "niveaux", "salles",
            "creneaux_horaires", "school_working_day",
            "annees_scolaires");

    private void purger(String tenantId, boolean demandee) {
        log.warn("[jeu-de-donnees] Purge des données pédagogiques de l'établissement {} ({})",
                tenantId, demandee ? "demandée" : "ancienne structure détectée");

        int total = 0;
        for (String table : TABLES_A_PURGER) {
            total += entityManager
                    .createNativeQuery("DELETE FROM " + table + " WHERE tenant_id = :tenantId")
                    .setParameter("tenantId", tenantId)
                    .executeUpdate();
        }
        // Le contexte de persistance garde des entités désormais absentes de la
        // base ; les laisser exposerait le reste du run à des états fantômes.
        entityManager.clear();
        log.warn("[jeu-de-donnees] {} ligne(s) supprimée(s) pour l'établissement {}", total, tenantId);
    }

    private long compter(String table, String tenantId) {
        Number total = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return total.longValue();
    }
}
