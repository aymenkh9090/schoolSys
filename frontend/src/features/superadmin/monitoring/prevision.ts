/**
 * Ce que l'écran dit d'une prévision — une phrase, et un prolongement de courbe.
 *
 * <h4>Le verdict vient de Python, jamais d'ici</h4>
 *
 * Aucun calcul de tendance dans ce fichier : la régression, le R² et les
 * garde-fous vivent dans `ai-assistant/app/services/prevision.py`, qui est testé
 * sur des séries construites à la main. L'écran ne fait que mettre des mots sur
 * un verdict déjà rendu. Refaire un seuil ici finirait par produire une tuile qui
 * annonce « alerte dans 40 min » quand le service dit « incertain ».
 *
 * <h4>Jamais une durée que les données ne soutiennent pas</h4>
 *
 * Une échéance ne s'écrit que pour le verdict `hausse`. `incertain` se dit
 * « incertain », `insuffisant` se dit « insuffisant » — ni ligne plate, ni durée
 * par défaut. Une prévision fausse fait intervenir pour rien, puis ignorer la
 * suivante.
 */

import type { ResourceForecast } from '@/api/aiAssistant.api'

export type TonPrevision = 'neutre' | 'alerte' | 'critique'
export type SensPrevision = 'hausse' | 'stable' | 'baisse' | 'inconnu'

export interface LignePrevision {
  texte: string
  ton: TonPrevision
  sens: SensPrevision
}

/**
 * Une durée à l'échelle de l'intervention : « 45 min », « 2 h 10 ».
 *
 * Arrondie à cinq minutes au-delà d'une heure. Une pente calculée sur soixante
 * points ne vaut pas la minute près à deux heures de distance, et « 2 h 07 »
 * afficherait une précision que le calcul n'a pas.
 *
 * Espaces insécables : sur une tuile étroite, la ligne passe à la ligne, et
 * « 47 » d'un côté, « min » de l'autre ne se lit plus comme une durée.
 */
export function formatDuree(minutes: number): string {
  const arrondi = Math.round(minutes)
  if (arrondi < 60) return `${arrondi} min`
  const parCinq = Math.round(minutes / 5) * 5
  const heures = Math.floor(parCinq / 60)
  const reste = parCinq % 60
  return reste === 0 ? `${heures} h` : `${heures} h ${String(reste).padStart(2, '0')}`
}

/** « dans ~45 min » — le tilde dit que c'est une projection, pas un compte à rebours. */
function dans(minutes: number): string {
  return minutes < 1 ? "dans moins d'une minute" : `dans ~${formatDuree(minutes)}`
}

export function lignePrevision(p: ResourceForecast): LignePrevision {
  const horizon = p.horizon_minutes !== null ? formatDuree(p.horizon_minutes) : null
  const sur = horizon ? ` sur ${horizon}` : ''

  switch (p.verdict) {
    case 'insuffisant':
      return { texte: 'Historique insuffisant pour prévoir', ton: 'neutre', sens: 'inconnu' }
    case 'incertain':
      return { texte: 'Tendance incertaine', ton: 'neutre', sens: 'inconnu' }
    case 'stable':
      return { texte: `Stable${sur}`, ton: 'neutre', sens: 'stable' }
    case 'baisse':
      return { texte: `En baisse${sur}`, ton: 'neutre', sens: 'baisse' }
    case 'hausse': {
      const alerte = p.minutes_to_warning
      const incident = p.minutes_to_critical

      // Le seuil d'alerte est encore devant : c'est lui qu'on annonce, et
      // l'incident à la suite s'il tombe aussi dans l'horizon.
      if (alerte !== null && alerte > 0) {
        const suite = incident !== null ? ` · incident ~${formatDuree(incident)}` : ''
        return { texte: `Alerte ${dans(alerte)}${suite}`, ton: 'alerte', sens: 'hausse' }
      }
      // L'alerte est déjà là — la tuile le dit par sa couleur. Ce qui reste à
      // anticiper, c'est l'incident.
      if (incident !== null && incident > 0) {
        return { texte: `Incident ${dans(incident)}`, ton: 'critique', sens: 'hausse' }
      }
      if (incident === 0) {
        return { texte: "En hausse, au-delà du seuil d'incident", ton: 'critique', sens: 'hausse' }
      }
      return { texte: `En hausse — pas d'incident${sur}`, ton: 'alerte', sens: 'hausse' }
    }
  }
}

/**
 * Le prolongement en pointillé : la variation prévue, et sur combien de temps.
 *
 * Absent dès que le service n'a pas rendu `at_horizon` — ce qu'il fait quand la
 * droite n'explique pas la série. Le pointillé n'est donc jamais le tracé d'une
 * pente que le verdict a refusée.
 *
 * <h4>Une variation, pas une valeur d'arrivée</h4>
 *
 * Pour la mémoire, la droite décrit le **plancher** de la heap après passage du
 * ramasse-miettes, pas la heap brute que la courbe trace : son point de départ
 * tombe sous le dernier point de la courbe. Partir de sa valeur ferait un saut
 * au raccord. On reporte donc la **variation** prévue à partir du dernier point
 * tracé — la pente, qui est ce qu'on prédit, reste exacte.
 */
export function variationProjetee(p: ResourceForecast): { variation: number; minutes: number } | undefined {
  if (p.at_horizon === null || p.current === null || !p.horizon_minutes) return undefined
  return { variation: p.at_horizon - p.current, minutes: p.horizon_minutes }
}
