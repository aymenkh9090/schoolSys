package tn.wtm.school.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "tn.wtm.school")
@EntityScan(basePackages = "tn.wtm.school")
@EnableJpaRepositories(basePackages = "tn.wtm.school")
@EnableScheduling
public class SmartSchoolApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartSchoolApiApplication.class, args);
    }
}
