package tn.wtm.school.common.metrics;

import java.time.Instant;

/**
 * Bornes temporelles d'un traitement, par établissement.
 *
 * La durée est calculée en Java plutôt qu'en SQL : {@code EXTRACT(EPOCH FROM …)}
 * n'existe pas dans tous les dialectes, et une requête qui ne compile qu'en
 * PostgreSQL échouerait au démarrage sur la base de test H2 sans que rien ne
 * l'ait signalé avant.
 *
 * @param tenantId   identifiant technique de l'établissement
 * @param startedAt  début du traitement
 * @param finishedAt fin du traitement
 */
public record TenantInterval(String tenantId, Instant startedAt, Instant finishedAt) {
}
