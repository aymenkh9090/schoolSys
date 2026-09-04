/**
 * Vocabulaire français du DSL, côté interface.
 *
 * Les libellés des CHAMPS et des OPÉRATEURS ne sont pas ici : ils viennent du
 * catalogue publié par le backend (`/dsl/schema`). Les dupliquer donnerait deux
 * listes à maintenir, dont une finirait par mentir. Ne restent ici que les
 * libellés d'énumérations fermées et stables, et les couleurs — deux choses qui
 * relèvent de la présentation, pas du contrat.
 */

import type {
  ConstraintDsl,
  ConstraintSource,
  DslCondition,
  DslSchema,
  DslScope,
  DslSeverity,
} from '@/api/planning.api'

export const SEVERITY_LABELS: Record<DslSeverity, string> = {
  HARD: 'Obligatoire',
  MEDIUM: 'Fortement recommandée',
  SOFT: 'Préférence',
}

export const SEVERITY_HELP: Record<DslSeverity, string> = {
  HARD: 'Jamais enfreinte. Attention : trop de règles obligatoires peuvent rendre la génération impossible.',
  MEDIUM: "Le planning fait tout pour la respecter, mais peut s'en écarter s'il n'existe aucune autre solution.",
  SOFT: 'Simple préférence : le planning en tient compte quand il peut, sans jamais bloquer la génération.',
}

export const SEVERITY_VARIANTS: Record<DslSeverity, 'danger' | 'warning' | 'info'> = {
  HARD: 'danger',
  MEDIUM: 'warning',
  SOFT: 'info',
}

export const SCOPE_LABELS: Record<DslScope, string> = {
  LESSON: 'Chaque séance, prise une par une',
  TEACHER_DAY: 'Le total d’un enseignant sur une journée',
  CLASS_DAY: 'Le total d’une classe sur une journée',
  ROOM_DAY: 'L’occupation d’une salle sur une journée',
  TEACHER_WEEK: 'Le total d’un enseignant sur la semaine',
  CLASS_WEEK: 'Le total d’une classe sur la semaine',
}

export const SCOPE_HELP: Record<DslScope, string> = {
  LESSON: 'Exemple : « pas de maths le vendredi après 15h ». Choisissez ceci pour interdire ou privilégier un créneau.',
  TEACHER_DAY: 'Exemple : « pas plus de 5h de cours par jour pour un enseignant ». Choisissez ceci pour poser une limite d’heures.',
  CLASS_DAY: 'Exemple : « pas plus de 6h de cours dans la journée pour une classe ».',
  ROOM_DAY: 'Exemple : « le laboratoire n’est pas occupé plus de 4h par jour ».',
  TEACHER_WEEK: 'Exemple : « pas plus de 18h de cours par semaine pour un enseignant ».',
  CLASS_WEEK: 'Exemple : « pas plus de 30h de cours par semaine pour une classe ».',
}

export const SOURCE_LABELS: Record<ConstraintSource, string> = {
  MANUAL: 'Saisie manuelle',
  AI_TRANSLATED: 'Assistant IA',
  AI_SUGGESTED: 'Suggérée par l’IA',
}

export const METRIC_LABELS: Record<string, string> = {
  TOTAL_HOURS: 'heures cumulées',
  TOTAL_SLOTS: 'créneaux cumulés',
  LESSON_COUNT: 'nombre de séances',
}

/** True quand la portée impose un seuil (bloc `aggregate`). */
export function isAggregateScope(scope: DslScope): boolean {
  return scope !== 'LESSON'
}

/**
 * Règle vierge, prête à être remplie par le formulaire.
 *
 * SOFT par défaut, et non HARD : une règle mal calibrée en SOFT dégrade le
 * score, la même en HARD rend le planning infaisable pour tout l'établissement.
 * Le défaut le moins coûteux à corriger est le bon défaut.
 */
export function emptyRule(): ConstraintDsl {
  return {
    scope: 'LESSON',
    logic: 'AND',
    conditions: [],
    action: 'PENALIZE',
    severity: 'SOFT',
    weight: 10,
  }
}

/** Code technique proposé à partir du nom saisi : majuscules, sans accents. */
export function slugifyCode(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 100)
}

// ── Traduction d'une règle en français courant ──────────────────────────────
// Le compilateur du backend produit déjà un résumé, mais seulement APRÈS la
// vérification. Pendant la saisie, l'administrateur a besoin de relire ce qu'il
// est en train de construire — sinon il assemble des listes déroulantes sans
// jamais voir la phrase qui en résulte, et découvre son erreur à la génération.

/** Sujet de la phrase, selon ce que la règle observe. */
const SCOPE_SUBJECT: Record<DslScope, string> = {
  LESSON: 'une séance',
  TEACHER_DAY: 'la journée d’un enseignant',
  CLASS_DAY: 'la journée d’une classe',
  ROOM_DAY: 'la journée d’une salle',
  TEACHER_WEEK: 'la semaine d’un enseignant',
  CLASS_WEEK: 'la semaine d’une classe',
}

/** Verbe du seuil, à l'infinitif : « ne doit pas dépasser », « devrait atteindre ». */
const AGGREGATE_VERBS: Record<string, string> = {
  GREATER_THAN: 'dépasser',
  GREATER_THAN_OR_EQUAL: 'atteindre',
  LESS_THAN: 'rester sous',
}

/** Poids par défaut associé à chaque niveau d'exigence. */
export const DEFAULT_WEIGHTS: Record<DslSeverity, number> = {
  HARD: 100,
  MEDIUM: 50,
  SOFT: 10,
}

/** Une condition rendue en français : « Matière est Mathématiques ». */
function describeCondition(condition: DslCondition, schema?: DslSchema): string {
  const field = schema?.fields.find((f) => f.name === condition.field)
  const operator = schema?.operators.find((o) => o.name === condition.operator)
  const label = field?.label ?? condition.field
  const verb = (operator?.label ?? condition.operator).toLowerCase()

  const values = condition.values ?? (condition.value ? [condition.value] : [])
  const filled = values.filter((v) => v !== '' && v !== undefined)
  if (filled.length === 0) return `${label} … (valeur à compléter)`

  const value =
    condition.operator === 'BETWEEN' && filled.length === 2
      ? `${filled[0]} et ${filled[1]}`
      : filled.join(', ')

  return `${label} ${verb} ${value}`
}

/**
 * La règle telle qu'un administrateur la lirait, sans jargon.
 *
 * Ce n'est pas le verdict du moteur : c'est une relecture de la saisie en
 * cours. Le résumé qui fait foi reste celui renvoyé par la vérification.
 */
export function describeRule(rule: ConstraintDsl, schema?: DslSchema): string {
  const parts = rule.conditions.map((c) => describeCondition(c, schema))
  const joiner = (rule.logic ?? 'AND') === 'OR' ? ' ou ' : ' et '
  const where = parts.join(joiner)

  if (!isAggregateScope(rule.scope)) {
    if (parts.length === 0) {
      return 'Ajoutez au moins une condition pour désigner les séances concernées.'
    }
    return rule.action === 'PENALIZE'
      ? `Aucune séance ne doit avoir : ${where}.`
      : `Le planning placera en priorité les séances où : ${where}.`
  }

  const aggregate = rule.aggregate
  if (!aggregate) return 'Indiquez le seuil à ne pas dépasser.'

  const metric = METRIC_LABELS[aggregate.metric] ?? aggregate.metric
  const verb = AGGREGATE_VERBS[aggregate.operator] ?? 'dépasser'
  const filter = parts.length > 0 ? `, en ne comptant que les séances où ${where}` : ''
  const subject = `Sur ${SCOPE_SUBJECT[rule.scope]}, le total des ${metric}`

  return rule.action === 'PENALIZE'
    ? `${subject} ne doit pas ${verb} ${aggregate.value}${filter}.`
    : `${subject} devrait ${verb} ${aggregate.value}${filter}.`
}
