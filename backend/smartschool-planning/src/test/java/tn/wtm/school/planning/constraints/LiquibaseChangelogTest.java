package tn.wtm.school.planning.constraints;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibaseChangelogTest {

    @Test
    void appliesChangelogAndVerifiesSchemaAndSeedData() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:planning_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "")) {

            Database db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibase = new Liquibase(
                    "db/changelog.planing/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), db);
            liquibase.update(new Contexts(), new LabelExpression());

            // Tables exist
            assertThat(tableExists(conn, "constraint_definition")).isTrue();
            assertThat(tableExists(conn, "constraint_profile")).isTrue();
            assertThat(tableExists(conn, "constraint_setting")).isTrue();
            // Longtemps absente du master applicatif : elle n'existait que par
            // `ddl-auto`, donc pas du tout sur une installation en `validate`.
            assertThat(tableExists(conn, "custom_constraint")).isTrue();

            // La colonne de rapprochement ajoutée par la migration 017 : sans
            // elle, l'explication de score ne peut désigner aucune séance et
            // l'interface n'a rien à surligner.
            assertThat(columns(conn, "planning_timetable_session")).contains("lesson_id");

            // Tenant isolation: definition has no tenant_id, profile and setting do
            assertThat(columns(conn, "constraint_definition")).doesNotContain("tenant_id");
            assertThat(columns(conn, "constraint_profile")).contains("tenant_id");
            assertThat(columns(conn, "constraint_setting")).contains("tenant_id");

            // 18 contraintes au seed d'origine, plus les 5 règles de la
            // circulaire n°66 semées à l'étape D (migration 014), moins les 4
            // codes retirés à l'étape I (migration 016) : trois règles dures
            // appliquées en dur, donc jamais désactivables, et une préférence
            // que la circulaire ne demande nulle part.
            assertThat(countRows(conn, "constraint_definition")).isEqualTo(19);
            assertThat(countRows(conn, "constraint_profile")).isZero();
            assertThat(countRows(conn, "constraint_setting")).isZero();

            // Spot-check specific codes from PLANNING_MODULE.md §11
            assertThat(countByCode(conn, "MAX_STUDENT_HOURS_PER_DAY")).isEqualTo(1);
            assertThat(countByCode(conn, "MAX_TEACHER_HOURS_FRIDAY_SATURDAY")).isEqualTo(1);
            assertThat(countByCode(conn, "TEACHER_MIN_TWO_LEVELS")).isEqualTo(1);

            // Retirées du catalogue à l'étape I. Les deux premières restent
            // appliquées en dur par le provider — c'est la case à cocher qui
            // était mensongère, pas la règle ; la troisième ne disait rien de
            // plus que roomConflict.
            assertThat(countByCode(conn, "NO_STUDENT_IDLE_GAPS")).isZero();
            assertThat(countByCode(conn, "SPECIAL_ROOM_REQUIRED")).isZero();
            assertThat(countByCode(conn, "SPECIAL_ROOM_NO_OVERLAP")).isZero();
            assertThat(countByCode(conn, "BALANCED_CLASS_DIFFICULTY_FOR_TEACHERS")).isZero();

            // Règles de la circulaire n°66 ajoutées par la migration 014
            assertThat(countByCode(conn, "SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS")).isEqualTo(1);
            assertThat(countByCode(conn, "PHYSICAL_EDUCATION_SESSION_SPACING")).isEqualTo(1);
            assertThat(countByCode(conn, "MIN_STUDENT_HOURS_PER_HALF_DAY")).isEqualTo(1);
            assertThat(countByCode(conn, "MAIN_SUBJECTS_MORNING_QUOTA")).isEqualTo(1);
            assertThat(countByCode(conn, "CLASS_ROOM_STABILITY_PER_HALF_DAY")).isEqualTo(1);
        }
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) AS n FROM information_schema.tables WHERE LOWER(table_name)='" + table + "'")) {
            rs.next();
            return rs.getInt("n") == 1;
        }
    }

    private Set<String> columns(Connection conn, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT LOWER(column_name) AS c FROM information_schema.columns WHERE LOWER(table_name)='" + table + "'")) {
            while (rs.next()) cols.add(rs.getString("c"));
        }
        return cols;
    }

    private int countRows(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS n FROM " + table)) {
            rs.next();
            return rs.getInt("n");
        }
    }

    private int countByCode(Connection conn, String code) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) AS n FROM constraint_definition WHERE code='" + code + "'")) {
            rs.next();
            return rs.getInt("n");
        }
    }
}
