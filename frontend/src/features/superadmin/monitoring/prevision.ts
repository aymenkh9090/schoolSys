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

function majuscule(texte: string): string {
  return texte.charAt(0).toUpperCase() + texte.slice(1)
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

/**
 * Le prochain seuil que la droite franchira, et dans combien de temps.
 *
 * Seulement pour `hausse`, et seulement un seuil encore devant nous : l'alerte
 * si elle n'est pas atteinte, l'incident sinon. `undefined` dans tous les autres
 * cas — le panneau écrit alors « aucun » ou « non estimable », jamais une durée.
 */
export function prochainSeuil(
  p: ResourceForecast
): { seuil: number; minutes: number; ton: Exclude<TonPrevision, 'neutre'> } | undefined {
  if (p.verdict !== 'hausse') return undefined
  if (p.minutes_to_warning !== null && p.minutes_to_warning > 0) {
    return { seuil: p.warning_threshold, minutes: p.minutes_to_warning, ton: 'alerte' }
  }
  if (p.minutes_to_critical !== null && p.minutes_to_critical > 0) {
    return { seuil: p.critical_threshold, minutes: p.minutes_to_critical, ton: 'critique' }
  }
  return undefined
}

export interface Recommandation {
  ton: TonPrevision | 'inconnu'
  titre: string
  texte: string
}

/** Ce qu'on fait quand la ressource monte — propre à chacune. */
const CONSEIL: Record<string, string> = {
  cpu:
    'Repérer le traitement qui charge le processeur (une génération ' +
    "d'emploi du temps en cours, par exemple) avant d'en lancer un autre.",
  memory:
    "Le plancher de la heap monte d'un ramasse-miettes à l'autre, signe " +
    "possible d'une fuite : prévoir un redémarrage hors des heures de cours.",
  system_cpu:
    "La charge vient de toute la machine, pas forcément de l'API : Ollama " +
    "pendant une réponse de l'assistant, PostgreSQL, ou un autre processus.",
  disk:
    'Libérer de la place avant que la base ne puisse plus écrire : anciennes ' +
    'sauvegardes du dossier sauvegardes/, journaux, images Docker inutilisées.',
  db_pool:
    'Des requêtes gardent leurs connexions trop longtemps : chercher les ' +
    "transactions lentes avant d'agrandir le pool, qui ne ferait que retarder l'attente.",
}

/**
 * La conduite à tenir, déduite des verdicts — jamais rédigée par le modèle.
 *
 * La ressource la plus urgente décide : un incident annoncé l'emporte sur une
 * alerte, une alerte sur une incertitude, une incertitude sur le calme. Un
 * verdict `incertain` ou `insuffisant` ne rassure pas : il dit qu'on ne sait
 * pas, et le texte le dit aussi.
 */
export function recommandation(
  previsions: Partial<Record<string, ResourceForecast>>,
  noms: Record<string, string>
): Recommandation {
  const entrees = Object.entries(previsions).filter(
    (e): e is [string, ResourceForecast] => e[1] !== undefined
  )
  const nom = (cle: string) => noms[cle] ?? cle
  const horizonMin = entrees.find(([, p]) => p.horizon_minutes)?.[1].horizon_minutes ?? null
  const surHorizon = horizonMin ? `sur ${formatDuree(horizonMin)}` : "dans l'heure qui vient"

  // L'échéance la plus pressante : un seuil d'incident déjà dépassé, puis
  // l'incident le plus proche, puis l'alerte la plus proche.
  const rang = (p: ResourceForecast) => {
    const s = prochainSeuil(p)
    if (!s) return p.verdict === 'hausse' && p.minutes_to_critical === 0 ? -1 : Infinity
    return s.ton === 'critique' ? s.minutes : 100_000 + s.minutes
  }
  const [urgente] = entrees.filter(([, p]) => rang(p) !== Infinity).sort(([, a], [, b]) => rang(a) - rang(b))

  if (urgente) {
    const [cle, p] = urgente
    const conseil = CONSEIL[cle] ?? ''
    const s = prochainSeuil(p)
    if (!s) {
      return {
        ton: 'critique',
        titre: 'Intervenir',
        texte: `${majuscule(nom(cle))} a franchi son seuil d'incident et continue de monter. ${conseil}`,
      }
    }
    return s.ton === 'critique'
      ? {
          ton: 'critique',
          titre: 'Intervenir',
          texte: `${majuscule(nom(cle))} est déjà en alerte ; à ce rythme, le seuil d'incident (${s.seuil} %) sera atteint ${dans(s.minutes)}. ${conseil}`,
        }
      : {
          ton: 'alerte',
          titre: 'Anticiper',
          texte: `À ce rythme, ${nom(cle)} atteindra son seuil d'alerte (${s.seuil} %) ${dans(s.minutes)}. ${conseil}`,
        }
  }

  const insuffisant = entrees.find(([, p]) => p.verdict === 'insuffisant')
  if (insuffisant) {
    return {
      ton: 'inconnu',
      titre: 'Prévision impossible',
      texte:
        `Pas assez d'historique pour anticiper ${nom(insuffisant[0])} : il faut au moins ` +
        "30 min de mesures — l'application a sans doute redémarré. Cela ne veut pas " +
        "dire qu'il n'y a aucun risque.",
    }
  }

  const incertain = entrees.find(([, p]) => p.verdict === 'incertain')
  if (incertain) {
    return {
      ton: 'inconnu',
      titre: 'Aucune échéance fiable',
      texte:
        `${majuscule(nom(incertain[0]))} varie sans tendance nette : aucune droite ne ` +
        "l'explique assez pour annoncer une échéance. Surveiller son niveau sur les tuiles.",
    }
  }

  const enHausse = entrees.find(([, p]) => p.verdict === 'hausse')
  if (enHausse) {
    return {
      ton: 'alerte',
      titre: 'Surveiller',
      texte: `${majuscule(nom(enHausse[0]))} monte, sans atteindre son seuil d'incident ${surHorizon}.`,
    }
  }

  return {
    ton: 'neutre',
    titre: 'Aucune action immédiate',
    texte: `À ce rythme, aucun seuil ne sera atteint ${surHorizon}.`,
  }
}
