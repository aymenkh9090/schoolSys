package tn.wtm.school.security.keycloak.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.keycloak.admin.client.Keycloak;
import org.springframework.test.util.ReflectionTestUtils;
import tn.wtm.school.security.utils.PasswordGeneratorUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le premier test du module de sécurité — il n'en avait aucun.
 *
 * <p>Ce qui est éprouvé ici est la fabrication du nom d'utilisateur Keycloak à
 * partir d'un email et d'un nom complet. C'est du calcul pur : aucun appel au
 * serveur, donc rien qui empêche de le vérifier en intégration continue. Et
 * c'est un calcul qui compte — Keycloak refuse un username de moins de trois
 * caractères, et un enseignant tunisien s'appelle « Aïcha Ben Salah », avec des
 * diacritiques et des espaces qu'un identifiant ne peut pas porter.
 *
 * <p>Les deux méthodes visées sont privées : elles n'ont pas à être exposées
 * pour être testées, la réflexion suffit et laisse l'API du service intacte.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakUsernameTest {

    @Mock private Keycloak keycloak;
    @Mock private PasswordGeneratorUtil passwordGeneratorUtil;

    private KeycloakAdminServiceImpl service;

    private String username(String email, String nomComplet) {
        service = new KeycloakAdminServiceImpl(keycloak, passwordGeneratorUtil);
        return ReflectionTestUtils.invokeMethod(service, "buildUsername", email, nomComplet);
    }

    private String normalise(String valeur) {
        service = new KeycloakAdminServiceImpl(keycloak, passwordGeneratorUtil);
        return ReflectionTestUtils.invokeMethod(service, "normalizeForUsername", valeur);
    }

    @ParameterizedTest
    @DisplayName("Les diacritiques et les séparateurs tombent, l'identité reste")
    @CsvSource({
            "'Aïcha',             aicha",
            "'Béchir Trabelsi',   bechir.trabelsi",
            "'JEAN-PIERRE',       jean.pierre",
            "'nom  espacé',       nom.espace",
    })
    void normaliseLesAccentsEtLesSeparateurs(String entree, String attendu) {
        assertThat(normalise(entree)).isEqualTo(attendu);
    }

    /**
     * Le point est le séparateur produit par la normalisation. En tête ou en
     * queue il ne sépare rien : `.ahmed.` n'est pas un identifiant lisible, et
     * c'est ce que la dernière substitution retire. Deux alternatives ancrées,
     * une de chaque côté — la règle Sonar S5850 demandait qu'on le dise
     * explicitement, ce test le vérifie.
     */
    @ParameterizedTest
    @DisplayName("Un point en tête ou en queue est retiré, jamais ceux du milieu")
    @CsvSource({
            "'.ahmed',        ahmed",
            "'ahmed.',        ahmed",
            "'.ahmed.',       ahmed",
            "'@ahmed@',       ahmed",
            "'ben.ali',       ben.ali",
            "'a.b.c',         a.b.c",
    })
    void retireLesPointsDeBordSansToucherAuxAutres(String entree, String attendu) {
        assertThat(normalise(entree)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("Une valeur vide ou nulle donne une chaîne vide, pas une exception")
    void toleUneEntreeVide() {
        assertThat(normalise(null)).isEmpty();
        assertThat(normalise("   ")).isEmpty();
    }

    @Test
    @DisplayName("Le cas courant : la partie locale de l'email suffit")
    void prendLaPartieLocaleDeLEmail() {
        assertThat(username("bechir.trabelsi@ecole.tn", "Béchir Trabelsi"))
                .isEqualTo("bechir.trabelsi");
    }

    /**
     * Keycloak exige trois caractères. Une adresse d'initiales — fréquente sur
     * les comptes de direction — n'en fournit que deux : le nom complet vient
     * compléter.
     */
    @Test
    @DisplayName("Une adresse trop courte est complétée par le nom complet")
    void completeAvecLeNomQuandLEmailEstTropCourt() {
        assertThat(username("bt@ecole.tn", "Béchir Trabelsi"))
                .isEqualTo("bt.bechir.trabelsi");
    }

    @Test
    @DisplayName("Sans email exploitable ni nom, le username est complété par des zéros")
    void garantitLaLongueurMinimaleEnDernierRecours() {
        assertThat(username("a@ecole.tn", null))
                .hasSize(3)
                .startsWith("a");
    }
}
