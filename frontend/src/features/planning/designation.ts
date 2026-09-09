import type { ViolationOccurrence } from '@/api/planning.api'

/**
 * Faire *montrer* une séance, plutôt que la décrire.
 *
 * Le solveur incrimine des séances ; l'explication de score les désigne depuis
 * l'étape 1 ; ce module fait le pont jusqu'à la grille, qui vit sur un autre
 * écran. Le lien passe par l'URL et non par un état partagé : il se colle dans
 * un message, se rouvre le lendemain, et survit à un rechargement — trois choses
 * qu'un état en mémoire ne fait pas.
 */

export type ViewMode = 'class' | 'teacher' | 'room'

export const VIEW_MODES: { value: ViewMode; label: string }[] = [
  { value: 'class', label: 'Classe' },
  { value: 'teacher', label: 'Enseignant' },
  { value: 'room', label: 'Salle' },
]

/** Code interne de la requête « tout afficher » — jamais un code réel. */
export const ALL = '*'

/** Où regarder, et quoi y surligner. */
export interface CibleDesignation {
  mode: ViewMode
  code: string
  /** Identifiants de lignes persistées — les mêmes que `SessionView.id`. */
  ids: number[]
}

/**
 * Quelle grille montre le mieux ce conflit-là ?
 *
 * L'ordre n'est pas arbitraire. Un conflit d'enseignant oppose **deux classes** :
 * une grille de classe n'en montrerait qu'une, et n'en montrer qu'une ne montre
 * pas le conflit. La grille de l'enseignant, elle, porte les deux séances côte à
 * côte — c'est exactement l'image qu'il faut. On ne retombe sur la classe que
 * lorsque l'enseignant ne les réunit pas, et sur « toutes les classes » quand
 * rien ne les réunit.
 *
 * `null` quand aucune séance n'a de ligne persistée : job antérieur à la colonne
 * `lesson_id`. Mieux vaut pas de bouton qu'un bouton qui ne surligne rien.
 */
export function cibleDeLOccurrence(occurrence: ViolationOccurrence): CibleDesignation | null {
  const ids = occurrence.sessions
    .map((s) => s.sessionId)
    .filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) return null

  /** Le code que TOUTES les séances partagent sur ce champ, sinon `null`. */
  const partage = (champ: 'teacherCode' | 'className'): string | null => {
    const premier = occurrence.sessions[0]?.[champ]
    if (!premier) return null
    return occurrence.sessions.every((s) => s[champ] === premier) ? premier : null
  }

  const enseignant = partage('teacherCode')
  if (enseignant) return { mode: 'teacher', code: enseignant, ids }

  const classe = partage('className')
  if (classe) return { mode: 'class', code: classe, ids }

  // Rien ne les réunit : on ouvre toutes les classes, et le bandeau dira
  // combien de séances la page montre réellement.
  return { mode: 'class', code: ALL, ids }
}

/** L'adresse de la grille, désignation comprise. */
export function lienDeDesignation(jobId: number, cible: CibleDesignation): string {
  const params = new URLSearchParams({
    jobId: String(jobId),
    mode: cible.mode,
    code: cible.code,
    highlight: cible.ids.join(','),
  })
  return `/planning/consultation?${params}`
}

/** Ce que porte l'URL de la consultation, ou `null` si elle ne porte rien. */
export interface Designation {
  query: { jobId: number; mode: ViewMode; code: string }
  ids: number[]
}

export function lireDesignation(params: URLSearchParams): Designation | null {
  const jobId = Number(params.get('jobId'))
  const mode = params.get('mode') as ViewMode | null
  const code = params.get('code')
  if (!jobId || !mode || !code) return null
  if (!VIEW_MODES.some((m) => m.value === mode)) return null

  // Un identifiant illisible est écarté plutôt que rendu en NaN : surligner la
  // mauvaise case est pire que n'en surligner aucune.
  const ids = (params.get('highlight') ?? '')
    .split(',')
    .map(Number)
    .filter((n) => Number.isInteger(n) && n > 0)

  return { query: { jobId, mode, code }, ids }
}
