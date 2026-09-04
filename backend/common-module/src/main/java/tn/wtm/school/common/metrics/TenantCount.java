package tn.wtm.school.common.metrics;

/**
 * Résultat d'un comptage groupé par établissement.
 *
 * Une seule requête « GROUP BY tenant_id » remplace N requêtes « COUNT WHERE
 * tenant_id = ? ». La différence n'est pas cosmétique : les métriques sont
 * relevées en boucle, et une plateforme à 200 établissements paierait sinon
 * 200 allers-retours SQL à chaque relevé.
 *
 * @param tenantId identifiant technique de l'établissement, tel que stocké dans
 *                 la colonne {@code tenant_id} des entités métier
 * @param value    valeur comptée
 */
public record TenantCount(String tenantId, long value) {
}
