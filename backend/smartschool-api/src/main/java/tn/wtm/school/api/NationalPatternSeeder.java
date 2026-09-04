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
 * Seeds the official Tunisian college curriculum patterns (7ème, 8ème, 9ème de base)
 * from the national reference data defined in docs/exemple.md.
 *
 * Runs once on startup; skipped if TN patterns already exist.
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
        // Technique 7ème : (3) → par groupe, 1h30/groupe, volume total 3h
        np.getDetails().add(detail(np, "TECH", 3.0, "(3)", sessions(
                sess(1, SessionType.TP, 1.5, "DEMI_GROUP", "LABTECHNIQUE", "ALL"))));
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
                detail(np, "PHY", 3.0, "1(biweekly)+(2)", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS",  "NORMALE",     "BIWEEKLY"),
                        sess(2, SessionType.TP,     1.0, "DEMI_GROUP",  "LABPHYSIQUE", "ALL"))),
                detail(np, "SCI", 3.0, "1(biweekly)+(2)", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS",  "NORMALE",    "BIWEEKLY"),
                        sess(2, SessionType.TP,     1.0, "DEMI_GROUP",  "LABSCIENCE", "ALL"))),
                detail(np, "INFO", 2.0, "(2)", sessions(
                        sess(1, SessionType.TP, 1.0, "DEMI_GROUP", "LABINFORMATIQUE", "ALL"))),
                detail(np, "SPORT", 3.0, "2+1", sessions(
                        sess(1, SessionType.SPORT, 2.0, "FULL_CLASS", "SALLESPORT", "ALL"),
                        sess(2, SessionType.SPORT, 1.0, "FULL_CLASS", "SALLESPORT", "ALL"))),
                detail(np, "MUS", 1.0, "1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "DESSIN", 1.0, "1", sessions(
                        sess(1, SessionType.COURSE, 1.0, "FULL_CLASS", "NORMALE", "ALL"))),
                detail(np, "THEATRE", 2.0, "(2)", sessions(
                        sess(1, SessionType.TP, 1.0, "DEMI_GROUP", "NORMALE", "ALL")))
        );
    }

    // Technique 8ème/9ème : (2) → par groupe, 1h/groupe, volume total 2h
    private NationalPatternDetail techniqueDeuxHeures(NationalPattern np) {
        return detail(np, "TECH", 2.0, "(2)", sessions(
                sess(1, SessionType.TP, 1.0, "DEMI_GROUP", "LABTECHNIQUE", "ALL")));
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
