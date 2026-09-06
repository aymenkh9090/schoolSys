package tn.wtm.school.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le changelog que l'application exécute doit être celui que les tests
 * vérifient.
 *
 * <h2>Le défaut que ce test empêche de revenir</h2>
 *
 * Il y avait deux listes de migrations. Celle du module planning — exercée par
 * {@code LiquibaseChangelogTest} et {@code CatalogueProviderCoverageTest} — et
 * celle-ci, recopiée à la main dans le module applicatif, la seule que Spring
 * charge au démarrage. Elles ont dérivé <b>dans les deux sens</b> : la liste
 * applicative ignorait les migrations 014, 015 et 016 — les règles de la
 * circulaire, le rapport de validation archivé, le ménage du catalogue — et la
 * liste du module planning ignorait la 009.
 *
 * <p>Rien ne le signalait. Toute la suite de tests passait au vert sur un
 * catalogue à dix-neuf règles pendant que la base de développement en portait
 * dix-huit, dont quatre que le solveur n'évalue plus et sans les cinq que la
 * circulaire impose. C'est le défaut P1 du plan de conformité — une liste
 * affichée, une autre appliquée — transposé aux migrations.
 *
 * <p>La correction tient en un {@code include} : le master applicatif délègue
 * au master planning au lieu d'en recopier le contenu. Ce test verrouille
 * l'arrangement, parce qu'une liste recopiée est toujours plus tentante que la
 * délégation le jour où l'on ajoute une migration.
 */
class ChangelogMasterTest {

    private static final String MASTER_APPLICATIF = "db.changelog/db.changelog-master.yaml";
    private static final String MASTER_PLANNING   = "db/changelog.planing/db.changelog-master.yaml";

    @Test
    @DisplayName("Le master applicatif délègue au master planning")
    void delegueAuMasterPlanning() {
        assertThat(lignes(MASTER_APPLICATIF))
                .as("""
                    Le changelog chargé par Spring doit inclure le master du module planning,
                    et non recopier ses migrations une par une : c'est la seule façon qu'il ait
                    d'être à jour le jour où le module en ajoute une.""")
                .anyMatch(l -> l.contains(MASTER_PLANNING));
    }

    @Test
    @DisplayName("Aucune migration planning n'est recopiée à côté")
    void aucuneMigrationRecopiee() {
        List<String> recopiees = lignes(MASTER_APPLICATIF).stream()
                .filter(l -> l.contains("db/changelog.planing/"))
                .filter(l -> !l.contains(MASTER_PLANNING))
                .toList();

        assertThat(recopiees)
                .as("""
                    Ces migrations sont citées à la main dans le master applicatif alors que le
                    master planning les inclut déjà. Une liste doublée est une liste qui
                    divergera : retirez-les et laissez l'include faire son travail.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Le master planning est bien sur le classpath de l'application")
    void masterPlanningPresentSurLeClasspath() {
        // L'include ne vaut que si le fichier voyage avec l'application : il vit
        // dans le jar du module planning, pas dans celui-ci.
        assertThat(ChangelogMasterTest.class.getClassLoader().getResource(MASTER_PLANNING))
                .as("le master planning doit être livré dans le classpath applicatif")
                .isNotNull();
    }

    private static List<String> lignes(String ressource) {
        try (InputStream in = ChangelogMasterTest.class.getClassLoader()
                .getResourceAsStream(ressource)) {
            assertThat(in).as("%s doit être sur le classpath de test", ressource).isNotNull();
            String contenu = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(contenu.split("\n"))
                    .map(String::strip)
                    .filter(l -> !l.startsWith("#"))
                    .toList();
        } catch (Exception e) {
            throw new AssertionError("lecture de " + ressource + " impossible", e);
        }
    }
}
