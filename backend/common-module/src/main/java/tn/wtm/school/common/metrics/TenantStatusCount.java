package tn.wtm.school.common.metrics;

/**
 * Comptage groupé par établissement ET par modalité (rôle, statut de job…).
 *
 * @param tenantId identifiant technique de l'établissement
 * @param key      modalité comptée, rendue en libellé de label Prometheus
 * @param value    valeur comptée
 */
public record TenantStatusCount(String tenantId, String key, long value) {
}
