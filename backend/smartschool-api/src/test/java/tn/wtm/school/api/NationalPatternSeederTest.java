package tn.wtm.school.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.org.entity.NationalPattern;
import tn.wtm.school.org.entity.NationalPatternDetail;
import tn.wtm.school.org.entity.NationalPatternSession;
import tn.wtm.school.org.repository.NationalPatternRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le programme national semé doit être celui de la circulaire n°66, § T.1.
 *
 * <h2>L'erreur que ces tests empêchent de revenir</h2>
 *
 * Le § N.1 dit de la notation {@code (2)} et {@code (3)} : « le chiffre est la
 * durée en heures ». La séance dure donc deux ou trois heures et elle est
 * répétée une fois par groupe. Le seed d'origine y a lu un nombre de groupes et
 * a divisé la durée par deux — {@code (3)} devenait « 1h30 par groupe ». Le
 * volume élève de l'informatique, de la technologie, du théâtre et des TP de
 * physique et de sciences s'en trouvait réduit de moitié.
 *
 * <p>Ce qui a rendu l'erreur durable, c'est qu'elle était <b>invisible</b> : le
 * total annoncé ({@code totalHoursPerWeek}) restait correct, et rien ne
 * vérifiait que la somme des séances le rejoignait. D'où le premier test, qui
 * est le vrai garde-fou : <i>la somme des séances doit égaler le total</i>.
 * Toute future divergence entre les deux, quelle qu'en soit la cause, échoue.
 */
@ExtendWith(MockitoExtension.class)
class NationalPatternSeederTest {

    @Mock
    private NationalPatternRepository repository;

    private List<NationalPattern> semer() {
        when(repository.existsByCountryCode("TN")).thenReturn(false);
        new NationalPatternSeeder(repository).seed();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<NationalPattern>> capteur =
                ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(capteur.capture());

        List<NationalPattern> patterns = new java.util.ArrayList<>();
        capteur.getValue().forEach(patterns::add);
        return patterns;
    }

    private static double sommeDesSeances(NationalPatternDetail d) {
        return d.getSessions().stream()
                .mapToDouble(NationalPatternSession::getDuration)
                .sum();
    }

    @Test
    @DisplayName("La somme des séances égale le volume annoncé, pour chaque matière")
    void sommeDesSeancesEgaleLeTotal() {
        for (NationalPattern np : semer()) {
            for (NationalPatternDetail d : np.getDetails()) {
                assertThat(sommeDesSeances(d))
                        .as("""
                            %s / %s — la somme des durées de séances (%s h) ne rejoint pas le \
                            volume annoncé (%s h), répartition « %s ».

                            C'est le symptôme exact de l'erreur de lecture du § N.1 : dans (2) \
                            et (3), le chiffre est la DURÉE de la séance en heures, pas un \
                            nombre de groupes. La séance est répétée une fois par groupe, mais \
                            l'élève reçoit bien le volume annoncé.""",
                                np.getCode(), d.getSubjectCode(),
                                sommeDesSeances(d), d.getTotalHoursPerWeek(), d.getRepartition())
                        .isEqualTo(d.getTotalHoursPerWeek());
            }
        }
    }

    @Test
    @DisplayName("Une séance en système de groupes dure ce que la notation annonce")
    void notationDesSeancesEnGroupes() {
        Map<String, NationalPattern> parCode = semer().stream()
                .collect(Collectors.toMap(NationalPattern::getCode, np -> np));

        // § T.1 — informatique (2) : une séance de 2 h, donnée à chaque groupe.
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "INFO"))
                .as("INFO est noté (2) : 2 heures").isEqualTo(2.0);

        // § T.1 — théâtre (2).
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "THEATRE"))
                .as("THEATRE est noté (2) : 2 heures").isEqualTo(2.0);

        // § T.1 — technologie : (3) en 7ᵉ, (2) en 8ᵉ et 9ᵉ.
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "TECH"))
                .as("TECH en 7ᵉ est noté (3) : 3 heures").isEqualTo(3.0);
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_8EME_OFFICIEL"), "TECH"))
                .as("TECH en 8ᵉ est noté (2) : 2 heures").isEqualTo(2.0);
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_9EME_OFFICIEL"), "TECH"))
                .as("TECH en 9ᵉ est noté (2) : 2 heures").isEqualTo(2.0);

        // § T.1 — physique et SVT : ①+(2), le TP est la séance de 2 h.
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "PHY"))
                .as("le TP de PHY est noté (2) : 2 heures").isEqualTo(2.0);
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "SCI"))
                .as("le TP de SCI est noté (2) : 2 heures").isEqualTo(2.0);
    }

    @Test
    @DisplayName("La séance de quinzaine ① est bien marquée BIWEEKLY")
    void seancesDeQuinzaine() {
        NationalPattern septieme = semer().stream()
                .filter(np -> "COLLEGE_7EME_OFFICIEL".equals(np.getCode()))
                .findFirst().orElseThrow();

        for (String matiere : List.of("PHY", "SCI", "ISL", "CIV")) {
            assertThat(detail(septieme, matiere).getSessions())
                    .as("%s comporte une séance de quinzaine (notation ① du § N.1)", matiere)
                    .anyMatch(s -> "BIWEEKLY".equals(s.getWeekParity()));
        }
    }

    @Test
    @DisplayName("Les trois programmes du collège sont semés")
    void troisProgrammes() {
        assertThat(semer())
                .extracting(NationalPattern::getCode)
                .containsExactlyInAnyOrder(
                        "COLLEGE_7EME_OFFICIEL", "COLLEGE_8EME_OFFICIEL", "COLLEGE_9EME_OFFICIEL");
    }

    @Test
    @DisplayName("Rien n'est semé une seconde fois")
    void seedIdempotent() {
        when(repository.existsByCountryCode("TN")).thenReturn(true);
        new NationalPatternSeeder(repository).seed();
        verify(repository, org.mockito.Mockito.never()).saveAll(anyIterable());
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    private static NationalPatternDetail detail(NationalPattern np, String matiere) {
        return np.getDetails().stream()
                .filter(d -> matiere.equals(d.getSubjectCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        matiere + " absent du programme " + np.getCode()));
    }

    /** Durée de l'unique séance en système de groupes d'une matière. */
    private static double dureeDeLaSeanceEnGroupe(NationalPattern np, String matiere) {
        return detail(np, matiere).getSessions().stream()
                .filter(s -> "DEMI_GROUP".equals(s.getGroupingType()))
                .mapToDouble(NationalPatternSession::getDuration)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        matiere + " n'a aucune séance en système de groupes dans " + np.getCode()));
    }
}
