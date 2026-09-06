package tn.wtm.school.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.org.entity.NationalPattern;
import tn.wtm.school.org.entity.NationalPatternDetail;
import tn.wtm.school.org.entity.NationalPatternSession;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.repository.NationalPatternRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sème les programmes officiels tunisiens du collège (7ᵉ, 8ᵉ et 9ᵉ de base),
 * dans leurs deux versions : le collège ordinaire (§ T.1) et le collège pilote
 * (§ T.3).
 *
 * <p><b>Référence.</b> Circulaire n°66 du 04/09/2024, § T.1 et § T.3 — « Guide
 * de répartition des séances d'enseignement ». Transcription vérifiable dans
 * {@code ai-assistant/data/corpus-consigne-2024.md}.
 *
 * <p><b>Comment lire la notation (§ N.1), car c'est là que le seed s'est
 * trompé.</b> Dans {@code (2)} et {@code (3)}, « le chiffre est la durée en
 * heures » : la séance dure deux ou trois heures, et elle est <em>donnée deux
 * ou trois fois</em>, une par groupe. L'élève reçoit donc le volume annoncé, et
 * l'enseignant fait davantage d'heures que l'élève n'en reçoit — c'est
 * exactement ce que le § T.2 formalise en distinguant horaire élève et horaire
 * enseignant.
 *
 * <p>La version précédente lisait {@code (N)} comme un nombre de groupes et
 * divisait la durée par deux : {@code (3)} devenait « 1h30 par groupe », d'où
 * un volume élève réduit de moitié pour l'informatique, la technologie, le
 * théâtre et les TP de physique et de sciences. Le total annoncé
 * ({@code totalHoursPerWeek}) restait juste, si bien que la somme des séances
 * le contredisait sans que rien ne le signale.
 *
 * <h2>Ce que la séance de quinzaine pèse dans le total</h2>
 *
 * La circulaire écrit « 4 h + quinzaine » et laisse la séance {@code ①} hors du
 * total : c'est ce qu'un directeur inscrit à son emploi du temps. Ici, le total
 * est la <b>somme des durées de séances</b>, quinzaine comprise — français 7ᵉ
 * vaut donc 5 h et non « 4 h + quinzaine ». Ce n'est pas une divergence de
 * lecture mais la seule convention utilisable en aval : une séance de quinzaine
 * occupe un créneau entier de la grille, et
 * {@code RESPECT_OFFICIAL_SUBJECT_HOURS} compare des créneaux placés à ce total.
 * La compter pour une demi-heure ferait échouer la contrainte sur un emploi du
 * temps correct.
 *
 * <h2>Pourquoi le programme ordinaire porte une version supérieure</h2>
 *
 * {@code findActiveWithDetailsByCountryAndLevel} trie par version décroissante
 * et le service applique le premier : deux programmes partageant un niveau, la
 * version décide. Le § T.1 est le cas général — c'est lui que doit rendre une
 * demande qui ne dit rien d'autre que le niveau. D'où
 * {@link #VERSION_ORDINAIRE} défini comme {@link #VERSION_PILOTE} + 1, et non
 * comme un nombre choisi : la règle doit rester vraie après le prochain
 * changement de programme. Un collège pilote applique le § T.3 en désignant le
 * programme par son identifiant, que le catalogue expose.
 *
 * <h2>Rattrapage des bases déjà semées</h2>
 *
 * Le garde-fou d'origine — « des programmes TN existent, ne rien faire » — avait
 * un défaut de fond : une correction du programme officiel n'atteignait jamais
 * une base en service, c'est-à-dire précisément celles où elle comptait. Chaque
 * programme est donc comparé à sa version : absent, il est semé ; présent dans
 * une version antérieure, ses lignes sont remplacées. Un démarrage sur une base
 * à jour ne fait que six lectures par code.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NationalPatternSeeder {

    private static final String TN = "TN";
    private static final int YEAR = 2026;

    /** Programme du collège pilote — § T.3. */
    private static final int VERSION_PILOTE = 1;

    /**
     * Programme du collège ordinaire — § T.1. Strictement supérieure à celle du
     * pilote : voir « Pourquoi le programme ordinaire porte une version
     * supérieure ». La version 1 était le programme d'avant correction, qui
     * donnait 6 h de mathématiques et 5 h d'anglais sans séance de groupe.
     */
    private static final int VERSION_ORDINAIRE = VERSION_PILOTE + 1;

    private static final List<String> NIVEAUX = List.of("7EME", "8EME", "9EME");

    private static final Map<String, String> LIBELLES = Map.of(
            "7EME", "7ème", "8EME", "8ème", "9EME", "9ème");

    private final NationalPatternRepository nationalPatternRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        int semes = 0;
        int misAJour = 0;

        for (NationalPattern attendu : programmes()) {
            Optional<NationalPattern> existant =
                    nationalPatternRepository.findByCode(attendu.getCode());
            if (existant.isEmpty()) {
                nationalPatternRepository.save(attendu);
                semes++;
            } else if (perime(existant.get(), attendu)) {
                remplacer(existant.get(), attendu);
                misAJour++;
            }
        }

        if (semes + misAJour == 0) {
            log.debug("[seeder] Programmes nationaux TN à jour — rien à faire");
        } else {
            log.info("[seeder] Programmes nationaux TN : {} semé(s), {} mis à jour",
                    semes, misAJour);
        }
    }

    /** Les six programmes du collège : trois ordinaires (§ T.1), trois pilotes (§ T.3). */
    private List<NationalPattern> programmes() {
        List<NationalPattern> tous = new java.util.ArrayList<>();
        NIVEAUX.forEach(niveau -> tous.add(collegeOrdinaire(niveau)));
        NIVEAUX.forEach(niveau -> tous.add(collegePilote(niveau)));
        return tous;
    }

    private static boolean perime(NationalPattern existant, NationalPattern attendu) {
        return existant.getVersion() == null
                || existant.getVersion() < attendu.getVersion();
    }

    /**
     * Remplace les lignes d'un programme dépassé par celles de la version
     * attendue.
     *
     * <p>La suppression est vidée en base <em>avant</em> les insertions :
     * {@code national_pattern_details} porte un index unique sur
     * (programme, matière), et laisser Hibernate ordonner les deux à sa guise
     * exposerait à une violation de contrainte sur les matières conservées.
     */
    private void remplacer(NationalPattern existant, NationalPattern attendu) {
        log.info("[seeder] Programme {} : version {} → {}, lignes remplacées",
                existant.getCode(), existant.getVersion(), attendu.getVersion());

        existant.getDetails().clear();
        nationalPatternRepository.saveAndFlush(existant);

        attendu.getDetails().forEach(d -> d.setNationalPattern(existant));
        existant.getDetails().addAll(attendu.getDetails());
        existant.setName(attendu.getName());
        existant.setVersion(attendu.getVersion());
        existant.setLevelCode(attendu.getLevelCode());
        existant.setAcademicYear(attendu.getAcademicYear());
        existant.setActive(true);
        nationalPatternRepository.save(existant);
    }

    // ── § T.1 — collège ordinaire ─────────────────────────────────────────────

    private NationalPattern collegeOrdinaire(String niveau) {
        NationalPattern np = pattern(
                "COLLEGE_" + niveau + "_OFFICIEL",
                "Programme officiel " + LIBELLES.get(niveau) + " de Base",
                niveau, VERSION_ORDINAIRE);
        np.getDetails().addAll(lignesCommunes(np, niveau));
        np.getDetails().add(francaisOrdinaire(np, niveau));
        // Anglais — § T.1 : (2)+1+1. La séance de 2 h est en système de groupes ;
        // c'est elle qui avait disparu du seed, où l'anglais valait 2+1+1+1 en
        // classe entière.
        np.getDetails().add(detail(np, "EN", 4.0, "(2)+1+1", sessions(
                sess(1, SessionType.TD,     2.0, "DEMI_GROUP", "NORMALE", "ALL"),
                sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(3, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))));
        // Mathématiques — § T.1 : 1+1+1+1, quatre heures. Le seed en donnait six.
        np.getDetails().add(mathematiques(np, 4));
        return np;
    }

    /**
     * Français — § T.1 : {@code 2+1+1+①} en 7ᵉ et 8ᵉ, {@code 2+1+1+1} en 9ᵉ.
     *
     * <p>C'est la seule ligne du tableau dont le découpage change avec le
     * niveau : la quatrième séance devient hebdomadaire en 9ᵉ. Le volume annoncé
     * ne bouge pas — voir la convention de total sur la quinzaine.
     */
    private NationalPatternDetail francaisOrdinaire(NationalPattern np, String niveau) {
        if ("9EME".equals(niveau)) {
            return detail(np, "FR", 5.0, "2+1+1+1", quatreSeancesDeCours());
        }
        return detail(np, "FR", 5.0, "2+1+1+1(biweekly)", sessions(
                sess(1, SessionType.COURSE, 2.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(3, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(4, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "BIWEEKLY")));
    }

    // ── § T.3 — collège pilote ────────────────────────────────────────────────

    private NationalPattern collegePilote(String niveau) {
        NationalPattern np = pattern(
                "COLLEGE_" + niveau + "_PILOTE",
                "Programme officiel " + LIBELLES.get(niveau) + " de Base — collège pilote",
                niveau, VERSION_PILOTE);
        np.getDetails().addAll(lignesCommunes(np, niveau));
        // Français — § T.3 : 5 h pleines, sans la quinzaine du collège ordinaire.
        np.getDetails().add(detail(np, "FR", 5.0, "2+1+1+1", quatreSeancesDeCours()));
        // Anglais — § T.3 : (2)+1+1+1, une heure de plus qu'au § T.1.
        np.getDetails().add(detail(np, "EN", 5.0, "(2)+1+1+1", sessions(
                sess(1, SessionType.TD,     2.0, "DEMI_GROUP", "NORMALE", "ALL"),
                sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(3, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(4, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))));
        // Mathématiques — § T.3 : 1+1+1+1+1, cinq séances d'une heure, donc au
        // moins une par jour de la semaine.
        np.getDetails().add(mathematiques(np, 5));
        return np;
    }

    // ── lignes identiques aux deux tableaux ───────────────────────────────────

    /**
     * Tout ce que les § T.1 et § T.3 disent à l'identique — c'est-à-dire tout
     * sauf le français, l'anglais et les mathématiques, qui sont exactement les
     * trois écarts par lesquels un collège pilote se distingue.
     */
    private List<NationalPatternDetail> lignesCommunes(NationalPattern np, String niveau) {
        return List.of(
                detail(np, "AR", 5.0, "2+1+1+1", quatreSeancesDeCours()),
                detail(np, "HISTGEO", 2.0, "1+1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "ISL", 2.0, "1(biweekly)+1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "BIWEEKLY"),
                        sess(2, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "CIV", 2.0, "1(biweekly)+1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "BIWEEKLY"),
                        sess(2, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                // Sciences physiques — ①+(2). Le cours en classe entière est la
                // séance de quinzaine ; le TP est la séance de 2 h par groupe.
                detail(np, "PHY", 3.0, "1(biweekly)+(2)", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS",  "NORMALE",     "BIWEEKLY"),
                        sess(2, SessionType.TP,     2.0, "DEMI_GROUP",  "LABPHYSIQUE", "ALL"))),
                // SVT — ①+(2), même structure que les sciences physiques.
                detail(np, "SCI", 3.0, "1(biweekly)+(2)", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS",  "NORMALE",    "BIWEEKLY"),
                        sess(2, SessionType.TP,     2.0, "DEMI_GROUP",  "LABSCIENCE", "ALL"))),
                // Informatique — (2), une séance de 2 h par groupe.
                detail(np, "INFO", 2.0, "(2)", sessions(
                        sess(1, SessionType.TP, 2.0, "DEMI_GROUP", "LABINFORMATIQUE", "ALL"))),
                technologie(np, niveau),
                detail(np, "SPORT", 3.0, "2+1", sessions(
                        sess(1, SessionType.SPORT, 2.0, "FULL_CLASS", "SALLESPORT", "ALL"),
                        sess(2, SessionType.SPORT, 1.0, "FULL_CLASS", "SALLESPORT", "ALL"))),
                detail(np, "MUS", 1.0, "1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "DESSIN", 1.0, "1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                // Théâtre — (2), une séance de 2 h par groupe. Au § T.1 la ligne
                // ne vaut que pour les collèges qui assurent la matière, marque
                // (*) ; au § T.3 elle vaut sans restriction.
                detail(np, "THEATRE", 2.0, "(2)", sessions(
                        sess(1, SessionType.TP, 2.0, "DEMI_GROUP", "NORMALE", "ALL")))
        );
    }

    /** Éducation technologique — (3) en 7ᵉ, (2) en 8ᵉ et 9ᵉ, dans les deux tableaux. */
    private NationalPatternDetail technologie(NationalPattern np, String niveau) {
        double heures = "7EME".equals(niveau) ? 3.0 : 2.0;
        return detail(np, "TECH", heures, "(" + (int) heures + ")", sessions(
                sess(1, SessionType.TP, heures, "DEMI_GROUP", "LABTECHNIQUE", "ALL")));
    }

    /** Mathématiques : autant de séances d'une heure que le tableau en compte. */
    private NationalPatternDetail mathematiques(NationalPattern np, int heures) {
        List<NationalPatternSession> seances = new java.util.ArrayList<>();
        seances.add(sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"));
        for (int rang = 2; rang <= heures; rang++) {
            seances.add(sess(rang, SessionType.TD, 1.0, "FULL_CLASS", "NORMALE", "ALL"));
        }
        return detail(np, "MATH", (double) heures, "1" + "+1".repeat(heures - 1), seances);
    }

    /** Le découpage {@code 2+1+1+1} en classe entière, commun à plusieurs lignes. */
    private List<NationalPatternSession> quatreSeancesDeCours() {
        return sessions(
                sess(1, SessionType.COURSE, 2.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(3, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                sess(4, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"));
    }

    // ── factory helpers ───────────────────────────────────────────────────────

    private NationalPattern pattern(String code, String name, String levelCode, int version) {
        return NationalPattern.builder()
                .code(code)
                .name(name)
                .version(version)
                .academicYear(YEAR)
                .active(true)
                .countryCode(TN)
                .levelCode(levelCode)
                .build();
    }

    private NationalPatternDetail detail(NationalPattern np, String subjectCode,
                                          double totalHours, String repartition,
                                          List<NationalPatternSession> sessions) {
        NationalPatternDetail d = NationalPatternDetail.builder()
                .nationalPattern(np)
                .subjectCode(subjectCode)
                .totalHoursPerWeek(totalHours)
                .repartition(repartition)
                .build();
        sessions.forEach(s -> s.setNationalPatternDetail(d));
        d.getSessions().addAll(sessions);
        return d;
    }

    @SafeVarargs
    private List<NationalPatternSession> sessions(NationalPatternSession... s) {
        return List.of(s);
    }

    private NationalPatternSession sess(int order, SessionType type, double duration,
                                         String groupingType, String roomType, String weekParity) {
        return NationalPatternSession.builder()
                .sessionOrder(order)
                .sessionType(type)
                .duration(duration)
                .groupingType(groupingType)
                .requiredRoomType(roomType)
                .weekParity(weekParity)
                .build();
    }
}
