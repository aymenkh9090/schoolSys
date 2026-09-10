package tn.wtm.school.api.pointage;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée des tranches web du domaine pointage.
 *
 * <p>Même rôle que {@code TenantWebSliceApp} : sans lui, {@code @WebMvcTest}
 * remonte jusqu'à {@code SmartSchoolApiApplication} dont l'{@code @EntityScan} /
 * {@code @EnableJpaRepositories} sur {@code tn.wtm.school} forcent la couche JPA
 * de tous les modules. Le scan est borné à {@code tn.wtm.school.api.pointage}
 * pour ne charger que les quatre controllers du domaine ; la sécurité réelle et
 * le gestionnaire d'exceptions sont {@code @Import}és par chaque test.
 */
@SpringBootApplication(scanBasePackages = "tn.wtm.school.api.pointage")
class PointageWebSliceApp {
}
