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

/**
 * Sème les programmes officiels tunisiens du collège (7ᵉ, 8ᵉ et 9ᵉ de base).
 *
 * <p><b>Référence.</b> Circulaire n°66 du 04/09/2024, § T.1 — « Guide de
 * répartition des séances d'enseignement, collèges ». Transcription vérifiable
 * dans {@code ai-assistant/data/corpus-consigne-2024.md}.
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
 * <p>Exécuté une fois au démarrage ; ignoré si les programmes TN existent déjà.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NationalPatternSeeder {

    private static final String TN = "TN";
    private static final int YEAR = 2026;

    private final NationalPatternRepository nationalPatternRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (nationalPatternRepository.existsByCountryCode(TN)) {
            log.debug("[seeder] National patterns TN already seeded — skipping");
            return;
        }

        log.info("[seeder] Seeding TN national curriculum patterns...");
        nationalPatternRepository.saveAll(List.of(
                build7eme(),
                build8eme(),
                build9eme()
        ));
        log.info("[seeder] TN national curriculum patterns seeded successfully");
    }

    // ── 7ème de Base ─────────────────────────────────────────────────────────

    private NationalPattern build7eme() {
        NationalPattern np = pattern("COLLEGE_7EME_OFFICIEL", "Programme officiel 7ème de Base", "7EME");
        np.getDetails().addAll(commonDetails(np));
        // Technique 7ᵉ — § T.1 : (3), soit UNE séance de 3 h donnée à chaque groupe.
        np.getDetails().add(detail(np, "TECH", 3.0, "(3)", sessions(
                sess(1, SessionType.TP, 3.0, "DEMI_GROUP", "LABTECHNIQUE", "ALL"))));
        return np;
    }

    // ── 8ème de Base ─────────────────────────────────────────────────────────

    private NationalPattern build8eme() {
        NationalPattern np = pattern("COLLEGE_8EME_OFFICIEL", "Programme officiel 8ème de Base", "8EME");
        np.getDetails().addAll(commonDetails(np));
        np.getDetails().add(techniqueDeuxHeures(np));
        return np;
    }

    // ── 9ème de Base ─────────────────────────────────────────────────────────

    private NationalPattern build9eme() {
        NationalPattern np = pattern("COLLEGE_9EME_OFFICIEL", "Programme officiel 9ème de Base", "9EME");
        np.getDetails().addAll(commonDetails(np));
        np.getDetails().add(techniqueDeuxHeures(np));
        return np;
    }

    // ── répartition commune 7ème/8ème/9ème (tout sauf Technique) ───────────────

    private List<NationalPatternDetail> commonDetails(NationalPattern np) {
        return List.of(
                detail(np, "AR", 5.0, "2+1+1+1", sessions(
                        sess(1, SessionType.COURSE, 2.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(3, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(4, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "FR", 5.0, "2+1+1+1", sessions(
                        sess(1, SessionType.COURSE, 2.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(3, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(4, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "EN", 5.0, "2+1+1+1", sessions(
                        sess(1, SessionType.COURSE, 2.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(3, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(4, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "HISTGEO", 2.0, "1+1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(2, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "ISL", 2.0, "1(biweekly)+1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "BIWEEKLY"),
                        sess(2, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "CIV", 2.0, "1(biweekly)+1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "BIWEEKLY"),
                        sess(2, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "MATH", 6.0, "1+1+1+1+1+1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(2, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(3, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(4, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(5, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"),
                        sess(6, SessionType.TD,     1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                // Sciences physiques — § T.1 : ①+(2). Le cours en classe entière est
                // la séance de quinzaine ; le TP est la séance de 2 h par groupe.
                detail(np, "PHY", 3.0, "1(biweekly)+(2)", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS",  "NORMALE",     "BIWEEKLY"),
                        sess(2, SessionType.TP,     2.0, "DEMI_GROUP",  "LABPHYSIQUE", "ALL"))),
                // SVT — § T.1 : ①+(2), même structure que les sciences physiques.
                detail(np, "SCI", 3.0, "1(biweekly)+(2)", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS",  "NORMALE",    "BIWEEKLY"),
                        sess(2, SessionType.TP,     2.0, "DEMI_GROUP",  "LABSCIENCE", "ALL"))),
                // Informatique — § T.1 : (2), une séance de 2 h par groupe.
                detail(np, "INFO", 2.0, "(2)", sessions(
                        sess(1, SessionType.TP, 2.0, "DEMI_GROUP", "LABINFORMATIQUE", "ALL"))),
                detail(np, "SPORT", 3.0, "2+1", sessions(
                        sess(1, SessionType.SPORT, 2.0, "FULL_CLASS", "SALLESPORT", "ALL"),
                        sess(2, SessionType.SPORT, 1.0, "FULL_CLASS", "SALLESPORT", "ALL"))),
                detail(np, "MUS", 1.0, "1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "DESSIN", 1.0, "1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                // Théâtre — § T.1 : (2), une séance de 2 h par groupe. La ligne
                // ne vaut que pour les collèges qui assurent la matière, marque (*).
                detail(np, "THEATRE", 2.0, "(2)", sessions(
                        sess(1, SessionType.TP, 2.0, "DEMI_GROUP", "NORMALE", "ALL")))
        );
    }

    // Technique 8ᵉ et 9ᵉ — § T.1 : (2), soit UNE séance de 2 h par groupe.
    private NationalPatternDetail techniqueDeuxHeures(NationalPattern np) {
        return detail(np, "TECH", 2.0, "(2)", sessions(
                sess(1, SessionType.TP, 2.0, "DEMI_GROUP", "LABTECHNIQUE", "ALL")));
    }

    // ── factory helpers ───────────────────────────────────────────────────────

    private NationalPattern pattern(String code, String name, String levelCode) {
        return NationalPattern.builder()
                .code(code)
                .name(name)
                .version(1)
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
