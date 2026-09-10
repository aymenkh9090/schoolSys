package tn.wtm.school.api.tenant;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Le point d'entrée des tranches web du domaine tenant.
 *
 * <p>{@code @WebMvcTest} remonte l'arborescence de paquets jusqu'au premier
 * {@code @SpringBootConfiguration}. Sans cette classe, il tombe sur
 * {@code SmartSchoolApiApplication}, dont le {@code @EnableJpaRepositories} et
 * l'{@code @EntityScan} sur {@code tn.wtm.school} forcent la création des
 * repositories de tous les modules — donc d'un {@code EntityManagerFactory}, et
 * d'une base, dans un test qui n'en veut pas.
 *
 * <p>Le scan est borné à {@code tn.wtm.school.api.tenant} : la tranche voit les
 * trois controllers du domaine et rien d'autre. Ce que chaque test veut en plus
 * (la sécurité réelle, le gestionnaire d'exceptions) est {@code @Import}é
 * nommément.
 */
@SpringBootApplication(scanBasePackages = "tn.wtm.school.api.tenant")
class TenantWebSliceApp {
}
