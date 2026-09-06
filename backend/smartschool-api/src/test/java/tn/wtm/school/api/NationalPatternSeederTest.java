package tn.wtm.school.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le programme national semé doit être celui de la circulaire n°66 : le § T.1
 * pour le collège ordinaire, le § T.3 pour le collège pilote.
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
 *
 * <h2>La seconde erreur, corrigée à l'étape G</h2>
 *
 * Le programme se disait « officiel » et « § T.1 » tout en donnant 6 h de
 * mathématiques — le § T.1 en donne 4, le § T.3 en donne 5 — et 5 h d'anglais en
 * classe entière, là où les deux tableaux prévoient une séance de groupe. Les
 * volumes sont désormais vérifiés matière par matière, dans les deux tableaux.
 */
@ExtendWith(MockitoExtension.class)
class NationalPatternSeederTest {

    @Mock
    private NationalPatternRepository repository;

    // ══════════════════════════════════════════════════════════════════════════
    // Invariants de notation — § N.1
    // ══════════════════════════════════════════════════════════════════════════

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
        Map<String, NationalPattern> parCode = parCode();

        // Informatique (2) : une séance de 2 h, donnée à chaque groupe.
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "INFO"))
                .as("INFO est noté (2) : 2 heures").isEqualTo(2.0);

        // Théâtre (2).
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "THEATRE"))
                .as("THEATRE est noté (2) : 2 heures").isEqualTo(2.0);

        // Technologie : (3) en 7ᵉ, (2) en 8ᵉ et 9ᵉ.
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "TECH"))
                .as("TECH en 7ᵉ est noté (3) : 3 heures").isEqualTo(3.0);
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_8EME_OFFICIEL"), "TECH"))
                .as("TECH en 8ᵉ est noté (2) : 2 heures").isEqualTo(2.0);
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_9EME_OFFICIEL"), "TECH"))
                .as("TECH en 9ᵉ est noté (2) : 2 heures").isEqualTo(2.0);

        // Physique et SVT : ①+(2), le TP est la séance de 2 h.
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "PHY"))
                .as("le TP de PHY est noté (2) : 2 heures").isEqualTo(2.0);
        assertThat(dureeDeLaSeanceEnGroupe(parCode.get("COLLEGE_7EME_OFFICIEL"), "SCI"))
                .as("le TP de SCI est noté (2) : 2 heures").isEqualTo(2.0);
    }

    @Test
    @DisplayName("La séance de quinzaine ① est bien marquée BIWEEKLY")
    void seancesDeQuinzaine() {
        NationalPattern septieme = parCode().get("COLLEGE_7EME_OFFICIEL");

        for (String matiere : List.of("PHY", "SCI", "ISL", "CIV")) {
            assertThat(detail(septieme, matiere).getSessions())
                    .as("%s comporte une séance de quinzaine (notation ① du § N.1)", matiere)
                    .anyMatch(s -> "BIWEEKLY".equals(s.getWeekParity()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Volumes — § T.1 et § T.3
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Collège ordinaire — § T.1")
    class CollegeOrdinaire {

        @Test
        @DisplayName("Quatre heures de mathématiques, et non six")
        void mathematiquesQuatreHeures() {
            for (String niveau : List.of("7EME", "8EME", "9EME")) {
                assertThat(detail(parCode().get("COLLEGE_" + niveau + "_OFFICIEL"), "MATH")
                        .getTotalHoursPerWeek())
                        .as("§ T.1 donne 1+1+1+1 en %s ; le seed en donnait six", niveau)
                        .isEqualTo(4.0);
            }
        }

        @Test
        @DisplayName("Anglais : quatre heures, dont la séance de groupe (2)")
        void anglaisAvecSeanceDeGroupe() {
            NationalPatternDetail en = detail(parCode().get("COLLEGE_7EME_OFFICIEL"), "EN");
            assertThat(en.getTotalHoursPerWeek()).isEqualTo(4.0);
            assertThat(dureeDeLaSeanceEnGroupe(parCode().get("COLLEGE_7EME_OFFICIEL"), "EN"))
                    .as("§ T.1 note l'anglais (2)+1+1 ; la séance de groupe avait disparu")
                    .isEqualTo(2.0);
        }

        @Test
        @DisplayName("Français : une quinzaine en 7ᵉ et 8ᵉ, quatre séances pleines en 9ᵉ")
        void francaisSelonLeNiveau() {
            for (String niveau : List.of("7EME", "8EME")) {
                assertThat(detail(parCode().get("COLLEGE_" + niveau + "_OFFICIEL"), "FR")
                        .getSessions())
                        .as("§ T.1 note le français 2+1+1+① en %s", niveau)
                        .anyMatch(s -> "BIWEEKLY".equals(s.getWeekParity()));
            }
            assertThat(detail(parCode().get("COLLEGE_9EME_OFFICIEL"), "FR").getSessions())
                    .as("§ T.1 note le français 2+1+1+1 en 9ᵉ — cinq heures pleines")
                    .noneMatch(s -> "BIWEEKLY".equals(s.getWeekParity()));
        }
    }

    @Nested
    @DisplayName("Collège pilote — § T.3")
    class CollegePilote {

        @Test
        @DisplayName("Les trois écarts du § T.3 sont exactement les trois différences")
        void lesTroisEcarts() {
            NationalPattern pilote   = parCode().get("COLLEGE_7EME_PILOTE");
            NationalPattern ordinaire = parCode().get("COLLEGE_7EME_OFFICIEL");

            assertThat(detail(pilote, "MATH").getTotalHoursPerWeek())
                    .as("§ T.3 : mathématiques 5 h au lieu de 4").isEqualTo(5.0);
            assertThat(detail(pilote, "EN").getTotalHoursPerWeek())
                    .as("§ T.3 : anglais (2)+1+1+1, 5 h").isEqualTo(5.0);
            assertThat(detail(pilote, "FR").getSessions())
                    .as("§ T.3 : français 5 h pleines, sans la quinzaine du collège ordinaire")
                    .noneMatch(s -> "BIWEEKLY".equals(s.getWeekParity()));

            // Et rien d'autre ne bouge : les deux tableaux disent la même chose
            // partout ailleurs.
            for (String matiere : List.of("AR", "HISTGEO", "ISL", "CIV", "PHY", "SCI",
                    "INFO", "TECH", "SPORT", "MUS", "DESSIN", "THEATRE")) {
                assertThat(detail(pilote, matiere).getTotalHoursPerWeek())
                        .as("%s est identique aux § T.1 et § T.3", matiere)
                        .isEqualTo(detail(ordinaire, matiere).getTotalHoursPerWeek());
            }
        }

        @Test
        @DisplayName("Le programme ordinaire prime dans la sélection par niveau")
        void ordinairePrime() {
            Map<String, NationalPattern> parCode = parCode();
            for (String niveau : List.of("7EME", "8EME", "9EME")) {
                assertThat(parCode.get("COLLEGE_" + niveau + "_OFFICIEL").getVersion())
                        .as("""
                            findActiveWithDetailsByCountryAndLevel trie par version décroissante \
                            et le service applique le premier : les deux programmes partageant le \
                            niveau %s, c'est la version qui décide lequel une demande muette \
                            obtient. Le § T.1 est le cas général — il doit gagner.""", niveau)
                        .isGreaterThan(parCode.get("COLLEGE_" + niveau + "_PILOTE").getVersion());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Semis et rattrapage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Les six programmes du collège sont semés")
    void sixProgrammes() {
        assertThat(semer())
                .extracting(NationalPattern::getCode)
                .containsExactlyInAnyOrder(
                        "COLLEGE_7EME_OFFICIEL", "COLLEGE_8EME_OFFICIEL", "COLLEGE_9EME_OFFICIEL",
                        "COLLEGE_7EME_PILOTE", "COLLEGE_8EME_PILOTE", "COLLEGE_9EME_PILOTE");
    }

    @Test
    @DisplayName("Un programme déjà à jour n'est pas réécrit")
    void programmeAJourInchange() {
        when(repository.findByCode(anyString())).thenAnswer(invocation ->
                Optional.of(NationalPattern.builder()
                        .code(invocation.getArgument(0)).name("déjà là").version(99)
                        .countryCode("TN").levelCode("7EME").academicYear(2026).build()));

        new NationalPatternSeeder(repository).seed();

        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un programme dépassé est rattrapé, lignes comprises")
    void programmeDepasseEstRattrape() {
        // Le vrai cas : une base en service porte le programme d'avant
        // correction — 6 h de mathématiques — et le garde-fou d'origine
        // (« des programmes TN existent, ne rien faire ») ne l'aurait jamais
        // corrigée. C'est-à-dire précisément là où la correction compte.
        NationalPattern ancien = NationalPattern.builder()
                .code("COLLEGE_7EME_OFFICIEL").name("Programme officiel 7ème de Base")
                .version(1).countryCode("TN").levelCode("7EME").academicYear(2026).build();
        ancien.getDetails().add(NationalPatternDetail.builder()
                .nationalPattern(ancien).subjectCode("MATH")
                .totalHoursPerWeek(6.0).repartition("1+1+1+1+1+1").build());

        when(repository.findByCode(anyString())).thenAnswer(invocation ->
                "COLLEGE_7EME_OFFICIEL".equals(invocation.getArgument(0))
                        ? Optional.of(ancien)
                        : Optional.empty());

        new NationalPatternSeeder(repository).seed();

        assertThat(ancien.getVersion())
                .as("la version doit suivre le programme rattrapé").isEqualTo(2);
        assertThat(detail(ancien, "MATH").getTotalHoursPerWeek())
                .as("§ T.1 : quatre heures de mathématiques").isEqualTo(4.0);
        verify(repository).saveAndFlush(ancien);
    }

    @Test
    @DisplayName("Les suppressions sont vidées en base avant les insertions")
    void suppressionsAvantInsertions() {
        // national_pattern_details porte un index unique (programme, matière) :
        // insérer les nouvelles lignes avant que les anciennes soient parties
        // viole la contrainte sur toutes les matières conservées.
        NationalPattern ancien = NationalPattern.builder()
                .code("COLLEGE_8EME_PILOTE").name("vieux pilote").version(0)
                .countryCode("TN").levelCode("8EME").academicYear(2026).build();
        ancien.getDetails().add(NationalPatternDetail.builder()
                .nationalPattern(ancien).subjectCode("AR").totalHoursPerWeek(5.0).build());

        when(repository.findByCode(anyString())).thenAnswer(invocation ->
                "COLLEGE_8EME_PILOTE".equals(invocation.getArgument(0))
                        ? Optional.of(ancien)
                        : Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            assertThat(((NationalPattern) invocation.getArgument(0)).getDetails())
                    .as("au flush des suppressions, aucune nouvelle ligne ne doit être là")
                    .isEmpty();
            return invocation.getArgument(0);
        });

        new NationalPatternSeeder(repository).seed();

        verify(repository).saveAndFlush(ancien);
        assertThat(ancien.getDetails()).isNotEmpty();
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    /** Sème sur une base vide et rend les programmes passés au dépôt. */
    private List<NationalPattern> semer() {
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());
        new NationalPatternSeeder(repository).seed();

        ArgumentCaptor<NationalPattern> capteur = ArgumentCaptor.forClass(NationalPattern.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(capteur.capture());
        return capteur.getAllValues();
    }

    /**
     * Les programmes semés, indexés par code — mémorisés, car semer deux fois
     * dans le même test rejouerait le semis et dédoublerait les prises du
     * capteur.
     */
    private Map<String, NationalPattern> parCode;

    private Map<String, NationalPattern> parCode() {
        if (parCode == null) {
            parCode = semer().stream()
                    .collect(Collectors.toMap(NationalPattern::getCode, np -> np));
        }
        return parCode;
    }

    private static double sommeDesSeances(NationalPatternDetail d) {
        return d.getSessions().stream()
                .mapToDouble(NationalPatternSession::getDuration)
                .sum();
    }

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
