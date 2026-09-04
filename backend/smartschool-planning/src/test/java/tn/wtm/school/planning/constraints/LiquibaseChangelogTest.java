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

            // Tenant isolation: definition has no tenant_id, profile and setting do
            assertThat(columns(conn, "constraint_definition")).doesNotContain("tenant_id");
            assertThat(columns(conn, "constraint_profile")).contains("tenant_id");
            assertThat(columns(conn, "constraint_setting")).contains("tenant_id");

            // Exactly 18 constraints seeded, profiles and settings empty
            assertThat(countRows(conn, "constraint_definition")).isEqualTo(18);
            assertThat(countRows(conn, "constraint_profile")).isZero();
            assertThat(countRows(conn, "constraint_setting")).isZero();

            // Spot-check specific codes from PLANNING_MODULE.md §11
            assertThat(countByCode(conn, "MAX_STUDENT_HOURS_PER_DAY")).isEqualTo(1);
            assertThat(countByCode(conn, "NO_STUDENT_IDLE_GAPS")).isEqualTo(1);
            assertThat(countByCode(conn, "MAX_TEACHER_HOURS_FRIDAY_SATURDAY")).isEqualTo(1);
            assertThat(countByCode(conn, "TEACHER_MIN_TWO_LEVELS")).isEqualTo(1);
            assertThat(countByCode(conn, "SPECIAL_ROOM_NO_OVERLAP")).isEqualTo(1);
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
