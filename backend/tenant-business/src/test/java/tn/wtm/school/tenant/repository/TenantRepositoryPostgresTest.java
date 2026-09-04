package tn.wtm.school.tenant.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import tn.wtm.school.tenant.entity.Tenant;
import tn.wtm.school.tenant.enums.EtablissementType;
import tn.wtm.school.tenant.enums.TenantPlan;
import tn.wtm.school.tenant.enums.TenantStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@EnabledIfSystemProperty(
        named = "testcontainers.enabled",
        matches = "true",
        disabledReason = "PostgreSQL/Testcontainers tests require Docker; run with -Dtestcontainers.enabled=true"
)
@ContextConfiguration(classes = TenantRepositoryPostgresTest.TestApplication.class)
class TenantRepositoryPostgresTest {

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

    private final TenantRepository tenantRepository;

    TenantRepositoryPostgresTest(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Test
    void savesAndFindsTenantByIdCodeAndName() {
        Tenant saved = tenantRepository.saveAndFlush(tenant("IBN", "College Ibn Khaldoun"));

        assertThat(tenantRepository.findById(saved.getTenantId())).contains(saved);
        assertThat(tenantRepository.findByCodeIgnoreCase("ibn")).contains(saved);
        assertThat(tenantRepository.findByNameIgnoreCase("college ibn khaldoun")).contains(saved);
    }

    @Test
    void persistsActivationAndSuspension() {
        Tenant saved = tenantRepository.saveAndFlush(tenant("CAR", "College Carthage"));
        saved.setStatus(TenantStatus.SUSPENDED); // active=false dérivé automatiquement
        tenantRepository.saveAndFlush(saved);

        Optional<Tenant> reloaded = tenantRepository.findById(saved.getTenantId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getActive()).isFalse();
        assertThat(reloaded.get().getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    void enforcesUniqueCode() {
        tenantRepository.saveAndFlush(tenant("IBN", "College Ibn Khaldoun"));

        assertThatThrownBy(() -> tenantRepository.saveAndFlush(tenant("IBN", "Autre college")))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void enforcesUniqueName() {
        tenantRepository.saveAndFlush(tenant("IBN", "College Ibn Khaldoun"));

        assertThatThrownBy(() -> tenantRepository.saveAndFlush(tenant("CAR", "College Ibn Khaldoun")))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    private Tenant tenant(String code, String name) {
        return Tenant.builder()
                .code(code)
                .name(name)
                .etablismentType(EtablissementType.COLLEGE)
                .address("Adresse " + code)
                .phone("+216 71 000 000")
                .active(true)
                .status(TenantStatus.ACTIVE)
                .plan(TenantPlan.STANDARD)
                .build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    @EntityScan(basePackageClasses = Tenant.class)
    @EnableJpaRepositories(basePackageClasses = TenantRepository.class)
    static class TestApplication {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("test");
        }
    }
}
