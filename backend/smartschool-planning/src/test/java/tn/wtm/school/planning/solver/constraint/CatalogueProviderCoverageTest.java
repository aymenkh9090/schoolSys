package tn.wtm.school.planning.solver.constraint;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrou entre le catalogue de contraintes (table {@code constraint_definition},
 * alimentée par Liquibase) et ce que le solveur évalue réellement.
 *
 * <h2>Pourquoi ce test existe</h2>
 *
 * Le catalogue et {@link TimetableConstraintProvider} ont dérivé l'un de l'autre
 * sans que rien ne le signale. Conséquence observée en production : le profil de
 * contraintes d'un établissement activait {@code RESPECT_OFFICIAL_SUBJECT_HOURS}
 * et {@code PHYSICAL_EDUCATION_THREE_SESSIONS} en CRITICAL, poids 1000, et le
 * solveur ne les évaluait pas — aucune violation n'était comptée, l'interface
 * affichait des règles actives que rien ne faisait respecter.
 *
 * Le mécanisme de la dérive est simple : {@code ConstraintWeightMapper.load()}
 * charge en {@link ActiveConstraintParam} <em>tous</em> les réglages actifs, y
 * compris ceux qu'aucun flux ne joint. Un code absent du provider ne provoque
 * donc ni erreur ni avertissement : il est chargé, puis ignoré.
 *
 * <h2>Comment il fonctionne</h2>
 *
 * Les constantes {@code static final String} de {@link ConstraintCodes} sont
 * inlinées par le compilateur dans la classe qui les référence. Chercher le
 * littéral dans le <i>constant pool</i> de {@code TimetableConstraintProvider}
 * revient donc à vérifier que le provider mentionne bien ce code — c'est-à-dire
 * qu'il ouvre un flux dessus. Aucune dépendance, aucune introspection Timefold.
 *
 * <h2>Ce qu'il autorise, et pourquoi</h2>
 *
 * {@link #NON_CABLEES} est la liste — assumée et datée — des codes du catalogue
 * qu'aucun flux ne consomme aujourd'hui. Elle doit correspondre <em>exactement</em>
 * à la réalité : ajouter un code au catalogue sans le câbler fait échouer le
 * test, et câbler un code sans retirer sa ligne d'ici le fait échouer aussi. La
 * liste ne peut donc pas pourrir en silence, et elle a vocation à se vider.
 *
 * <p>Voir {@code docs/plan-conformite-circulaire.md} § P1.
 */
class CatalogueProviderCoverageTest {

    /**
     * Codes présents au catalogue mais qu'aucun flux du provider ne joint.
     *
     * <p>Deux natures très différentes, et il faut les distinguer :
     *
     * <ul>
     *   <li><b>Jamais implémentées</b> — les trois SOFT restantes :
     *       l'établissement peut les activer, elles ne font rien. Aucune ne
     *       figure dans la circulaire, ce qui explique qu'elles survivent à
     *       l'étape D. {@code RESPECT_OFFICIAL_SUBJECT_HOURS} et
     *       {@code PHYSICAL_EDUCATION_THREE_SESSIONS} figuraient ici jusqu'à
     *       l'étape C, {@code TEACHER_MIN_TWO_LEVELS} jusqu'à l'étape D.</li>
     *   <li><b>Implémentées mais non pilotables</b> —
     *       {@code NO_STUDENT_IDLE_GAPS}, {@code SPECIAL_ROOM_REQUIRED} et
     *       {@code SPECIAL_ROOM_NO_OVERLAP} : le provider les applique en dur,
     *       toujours, sans joindre {@link ActiveConstraintParam}. Elles sont
     *       respectées, mais les décocher dans l'interface ne les désactive pas.
     *       C'est l'inverse du problème précédent, et c'est tout aussi trompeur.</li>
     * </ul>
     */
    private static final Set<String> NON_CABLEES = new LinkedHashSet<>(Set.of(
            // — jamais implémentées —
            //
            // Ces trois-là ne viennent pas de la circulaire : elle ne demande ni
            // charge égale entre enseignants, ni répartition hebdomadaire des
            // matières principales, ni équité des classes difficiles. Ce sont
            // des préférences de confort, ajoutées au catalogue par anticipation.
            // Elles restent donc en attente, sans échéance, là où l'étape D a
            // câblé tout ce que le texte réclame nommément.
            "BALANCED_TEACHER_WORKLOAD",
            "MAIN_SUBJECT_BALANCED_DISTRIBUTION",
            "BALANCED_CLASS_DIFFICULTY_FOR_TEACHERS",
            // — appliquées en dur, non pilotables depuis le profil —
            "NO_STUDENT_IDLE_GAPS",
            "SPECIAL_ROOM_REQUIRED",
            "SPECIAL_ROOM_NO_OVERLAP"));

    @Test
    @DisplayName("Tout code du catalogue est soit consommé par le provider, soit déclaré non câblé")
    void catalogueEtProviderRestentAlignes() throws Exception {
        Set<String> catalogue = codesDuCatalogue();
        assertThat(catalogue)
                .as("le changelog doit alimenter le catalogue")
                .isNotEmpty();

        String bytecode = constantPoolDuProvider();

        Set<String> cablees = new TreeSet<>();
        Set<String> absentes = new TreeSet<>();
        for (String code : catalogue) {
            if (bytecode.contains(code)) {
                cablees.add(code);
            } else {
                absentes.add(code);
            }
        }

        assertThat(absentes)
                .as("""
                    Codes du catalogue qu'aucun flux de TimetableConstraintProvider ne joint.

                    Si vous venez d'AJOUTER un code au catalogue : ouvrez-lui un flux dans
                    le provider, ou inscrivez-le dans NON_CABLEES en disant pourquoi.

                    Si vous venez de CÂBLER un code : retirez-le de NON_CABLEES.

                    Un code activé en base et absent du provider est chargé puis ignoré :
                    l'interface le montre actif, le solveur ne le fait pas respecter.""")
                .containsExactlyInAnyOrderElementsOf(NON_CABLEES);

        assertThat(cablees)
                .as("les codes câblés sont exactement le catalogue moins NON_CABLEES")
                .containsExactlyInAnyOrderElementsOf(
                        catalogue.stream().filter(c -> !NON_CABLEES.contains(c)).toList());
    }

    @Test
    @DisplayName("NON_CABLEES ne contient que des codes réellement présents au catalogue")
    void aucuneLignePerimeeDansLaListe() throws Exception {
        assertThat(codesDuCatalogue())
                .as("une entrée de NON_CABLEES qui ne correspond à aucun code du catalogue "
                        + "est une ligne morte : le code a dû être renommé ou supprimé")
                .containsAll(NON_CABLEES);
    }

    // ── outillage ─────────────────────────────────────────────────────────────

    /**
     * Résultat mémorisé : la base H2 en mémoire survit à la classe de test
     * ({@code DB_CLOSE_DELAY=-1}), donc rejouer le changelog une seconde fois
     * échoue sur {@code DATABASECHANGELOG already exists}. Le catalogue étant
     * figé pour toute la JVM, on ne l'applique qu'une fois.
     */
    private static Set<String> catalogueMemorise;

    /** Applique le changelog sur une base H2 en mémoire et lit les codes semés. */
    private static synchronized Set<String> codesDuCatalogue() throws Exception {
        if (catalogueMemorise != null) {
            return catalogueMemorise;
        }
        Set<String> codes = new TreeSet<>();
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:catalogue_coverage;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "")) {
            Database db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog.planing/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), db)
                    .update(new Contexts(), new LabelExpression());

            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT code FROM constraint_definition")) {
                while (rs.next()) {
                    codes.add(rs.getString(1));
                }
            }
        }
        catalogueMemorise = codes;
        return codes;
    }

    /**
     * Le fichier .class du provider, lu en ISO-8859-1 pour que chaque octet
     * devienne un caractère : les littéraux UTF-8 du constant pool restent alors
     * cherchables tels quels pour des codes en ASCII, ce que sont tous les nôtres.
     */
    private static String constantPoolDuProvider() throws Exception {
        try (InputStream in = TimetableConstraintProvider.class
                .getResourceAsStream("TimetableConstraintProvider.class")) {
            assertThat(in).as("le .class du provider doit être sur le classpath de test").isNotNull();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
