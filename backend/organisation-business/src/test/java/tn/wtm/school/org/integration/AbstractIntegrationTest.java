package tn.wtm.school.org.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.repository.SchoolYearRepository;

import java.util.Optional;

@DataJpaTest
@ActiveProfiles("test")
@Transactional
@EnabledIfSystemProperty(
        named = "testcontainers.enabled",
        matches = "true",
        disabledReason = "PostgreSQL/Testcontainers tests require Docker; run with -Dtestcontainers.enabled=true"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AbstractIntegrationTest.TestApplication.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    @EntityScan(basePackageClasses = SchoolYear.class)
    @EnableJpaRepositories(basePackageClasses = SchoolYearRepository.class)
    static class TestApplication {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("test-user");
        }
    }
}
