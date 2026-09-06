/**
 * Une règle proposée par l'assistant, dite en français ordinaire.
 *
 * Le backend renvoie deux choses exactes et deux choses illisibles : un résumé
 * produit par le compilateur (exact, mais rédigé pour décrire un moteur) et la
 * règle elle-même, avec ses noms de champs (`teacher.name`), ses opérateurs
 * (`GREATER_THAN`) et ses valeurs d'énumération (`FRIDAY`). Un directeur
 * d'établissement n'a pas à lire ça pour décider.
 *
 * Ce module fait la traduction, en trois temps qui sont ceux d'une explication
 * orale : ce que j'ai compris de votre phrase, ce que la règle dit exactement,
 * ce que ça change concrètement. Rien n'y est inventé — tout est dérivé de la
 * règle que le serveur a validée, et l'impact chiffré vient de sa mesure sur
 * les cours réels de l'année.
 */

import type { ConstraintProposal } from '@/api/aiAssistant.api'
import type {
  ConstraintDsl,
  DslCondition,
  DslSchema,
  DslScope,
  DslSeverity,
} from '@/api/planning.api'

// ── Vocabulaire ─────────────────────────────────────────────────────────────

/**
 * Les valeurs d'énumération, en français.
 *
 * Le catalogue du backend publie les libellés des CHAMPS mais pas ceux des
 * valeurs : « FRIDAY » arrive tel quel. Une valeur inconnue est rendue telle
 * quelle plutôt que masquée — un nom de matière ou de classe passe par là.
 */
const VALEURS: Record<string, string> = {
  MONDAY: 'lundi',
  TUESDAY: 'mardi',
  WEDNESDAY: 'mercredi',
  THURSDAY: 'jeudi',
  FRIDAY: 'vendredi',
  SATURDAY: 'samedi',
  SUNDAY: 'dimanche',
  MORNING: 'le matin',
  AFTERNOON: 'l’après-midi',
  COURS: 'cours',
  COURSE: 'cours',
  TD: 'travaux dirigés',
  TP: 'travaux pratiques',
  LAB: 'laboratoire',
  SPORT: 'sport',
  ATELIER: 'atelier',
  EXAM: 'examen',
  NORMALE: 'salle ordinaire',
  LABSCIENCE: 'laboratoire de sciences',
  LABPHYSIQUE: 'laboratoire de physique',
  LABINFORMATIQUE: 'salle informatique',
  LABTECHNIQUE: 'laboratoire technique',
  SALLESPORT: 'salle de sport',
  SALLEDESSIN: 'salle de dessin',
  SALLEMUSIQUE: 'salle de musique',
  AMPHI: 'amphithéâtre',
  BIBLIOTHEQUE: 'bibliothèque',
  TECH: 'salle technique',
  WORKSHOP: 'atelier',
  true: 'oui',
  false: 'non',
}

/** Nom court d'un champ, et sa forme dans une phrase. */
const CHAMPS: Record<string, { nom: string; prose: string }> = {
  'subject.code': { nom: 'Matière', prose: 'la matière' },
  'subject.name': { nom: 'Matière', prose: 'la matière' },
  'subject.main': { nom: 'Matière principale', prose: 'il s’agit d’une matière principale' },
  'class.name': { nom: 'Classe', prose: 'la classe' },
  'class.level': { nom: 'Niveau', prose: 'le niveau' },
  'class.speciality': { nom: 'Spécialité', prose: 'la spécialité' },
  'class.size': { nom: 'Effectif', prose: 'l’effectif' },
  'teacher.code': { nom: 'Enseignant', prose: 'l’enseignant' },
  'teacher.name': { nom: 'Enseignant', prose: 'l’enseignant' },
  'teacher.id': { nom: 'Enseignant', prose: 'l’enseignant' },
  'room.code': { nom: 'Salle', prose: 'la salle' },
  'room.type': { nom: 'Type de salle', prose: 'le type de salle' },
  day: { nom: 'Jour', prose: 'le jour' },
  period: { nom: 'Moment de la journée', prose: 'le moment de la journée' },
  startTime: { nom: 'Heure de début', prose: 'l’heure de début' },
  endTime: { nom: 'Heure de fin', prose: 'l’heure de fin' },
  slotOrder: { nom: 'Position dans la journée', prose: 'la position dans la journée' },
  sessionType: { nom: 'Type de séance', prose: 'le type de séance' },
  durationHours: { nom: 'Durée', prose: 'la durée' },
  groupIndex: { nom: 'Groupe', prose: 'le groupe' },
}

/** Ce que la règle observe, nommé comme dans l'établissement. */
const CIBLES: Record<DslScope, string> = {
  LESSON: 'Les cours',
  TEACHER_DAY: 'Les enseignants',
  TEACHER_WEEK: 'Les enseignants',
  CLASS_DAY: 'Les classes',
  CLASS_WEEK: 'Les classes',
  ROOM_DAY: 'Les salles',
}

const PERIODES: Record<DslScope, string> = {
  LESSON: 'À chaque séance',
  TEACHER_DAY: 'Par jour',
  TEACHER_WEEK: 'Par semaine',
  CLASS_DAY: 'Par jour',
  CLASS_WEEK: 'Par semaine',
  ROOM_DAY: 'Par jour',
}

/** Le sujet de la phrase, pour la reformulation. */
const SUJETS: Record<DslScope, string> = {
  LESSON: '',
  TEACHER_DAY: 'pour un même enseignant',
  TEACHER_WEEK: 'pour un même enseignant',
  CLASS_DAY: 'pour une même classe',
  CLASS_WEEK: 'pour une même classe',
  ROOM_DAY: 'pour une même salle',
}

const QUAND: Record<DslScope, string> = {
  LESSON: '',
  TEACHER_DAY: 'sur une même journée',
  TEACHER_WEEK: 'sur une même semaine',
  CLASS_DAY: 'sur une même journée',
  CLASS_WEEK: 'sur une même semaine',
  ROOM_DAY: 'sur une même journée',
}

const MESURES: Record<string, string> = {
  TOTAL_HOURS: 'heures de cours',
  TOTAL_SLOTS: 'créneaux',
  LESSON_COUNT: 'séances',
}

export const NIVEAUX: Record<DslSeverity, { label: string; couleur: string }> = {
  HARD: { label: 'Obligatoire', couleur: 'bg-red-500' },
  MEDIUM: { label: 'Fortement recommandée', couleur: 'bg-amber-500' },
  SOFT: { label: 'Souhaitable', couleur: 'bg-blue-500' },
}

const EFFET_NIVEAU: Record<DslSeverity, string> = {
  HARD: 'Le planning ne pourra jamais enfreindre cette règle : elle sera respectée dans tous les cas.',
  MEDIUM:
    'Le planning fera tout pour la respecter, et ne s’en écartera que s’il n’existe aucune autre solution.',
  SOFT: 'Le planning en tiendra compte quand c’est possible, sans jamais bloquer la génération.',
}

// ── Rendu d'une condition ───────────────────────────────────────────────────

function valeur(brut: string): string {
  return VALEURS[brut] ?? brut
}

function champ(nom: string, schema?: DslSchema): { nom: string; prose: string } {
  const connu = CHAMPS[nom]
  if (connu) return connu
  const descripteur = schema?.fields.find((f) => f.name === nom)
  const libelle = descripteur?.label ?? nom
  return { nom: libelle, prose: libelle.toLowerCase() }
}

function estHoraire(nomChamp: string): boolean {
  return nomChamp === 'startTime' || nomChamp === 'endTime'
}

function valeurs(condition: DslCondition): string[] {
  const brutes = condition.values ?? (condition.value !== undefined ? [condition.value] : [])
  return brutes.filter((v) => v !== '' && v !== undefined).map(valeur)
}

/** « Matière : sport », « Jour : vendredi » — la forme courte, pour une liste. */
function conditionCourte(condition: DslCondition, schema?: DslSchema): string {
  const c = champ(condition.field, schema)
  const v = valeurs(condition)
  if (v.length === 0) return c.nom

  switch (condition.operator) {
    case 'NOT_EQUALS':
    case 'NOT_IN':
      return `${c.nom} : tout sauf ${v.join(', ')}`
    case 'GREATER_THAN':
    case 'GREATER_THAN_OR_EQUAL':
      return `${c.nom} : ${estHoraire(condition.field) ? 'après' : 'plus de'} ${v[0]}`
    case 'LESS_THAN':
    case 'LESS_THAN_OR_EQUAL':
      return `${c.nom} : ${estHoraire(condition.field) ? 'avant' : 'moins de'} ${v[0]}`
    case 'BETWEEN':
      return `${c.nom} : entre ${v[0]} et ${v[1] ?? '…'}`
    case 'CONTAINS':
      return `${c.nom} : contient « ${v[0]} »`
    case 'STARTS_WITH':
      return `${c.nom} : commence par « ${v[0]} »`
    default:
      return `${c.nom} : ${v.join(', ')}`
  }
}

/** « la matière est sport » — la forme longue, pour une phrase. */
function conditionProse(condition: DslCondition, schema?: DslSchema): string {
  const c = champ(condition.field, schema)
  const v = valeurs(condition)
  if (v.length === 0) return c.prose

  switch (condition.operator) {
    case 'NOT_EQUALS':
      return `${c.prose} n’est pas ${v[0]}`
    case 'NOT_IN':
      return `${c.prose} n’est ni ${v.join(' ni ')}`
    case 'IN':
      return `${c.prose} est ${v.join(' ou ')}`
    case 'GREATER_THAN':
      return estHoraire(condition.field)
        ? `${c.prose} est après ${v[0]}`
        : `${c.prose} dépasse ${v[0]}`
    case 'GREATER_THAN_OR_EQUAL':
      return `${c.prose} est d’au moins ${v[0]}`
    case 'LESS_THAN':
      return estHoraire(condition.field)
        ? `${c.prose} est avant ${v[0]}`
        : `${c.prose} est inférieur à ${v[0]}`
    case 'LESS_THAN_OR_EQUAL':
      return `${c.prose} est d’au plus ${v[0]}`
    case 'BETWEEN':
      return `${c.prose} est entre ${v[0]} et ${v[1] ?? '…'}`
    case 'CONTAINS':
      return `${c.prose} contient « ${v[0]} »`
    case 'STARTS_WITH':
      return `${c.prose} commence par « ${v[0]} »`
    default:
      return `${c.prose} est ${v.join(', ')}`
  }
}

function joindre(parties: string[], logique: string | undefined): string {
  const lien = logique === 'OR' ? ' ou ' : ' et '
  return parties.join(lien)
}

// ── Les trois blocs ─────────────────────────────────────────────────────────

/**
 * « Vous souhaitez… » — la demande, redite dans les mots de l'utilisateur.
 *
 * C'est le premier contrôle que l'on offre : si la reformulation est fausse,
 * il n'y a rien à lire plus bas. Le résumé du compilateur sert de filet quand
 * la règle a une forme dont on ne sait pas faire une phrase.
 */
export function reformuler(dsl: ConstraintDsl, resume?: string | null, schema?: DslSchema): string {
  const conditions = dsl.conditions.map((c) => conditionProse(c, schema))
  const liste = joindre(conditions, dsl.logic)

  if (dsl.scope === 'LESSON') {
    if (conditions.length === 0) return resume ?? 'Une règle sur les cours de l’année.'
    return dsl.action === 'PENALIZE'
      ? `Vous souhaitez qu’aucun cours ne soit programmé lorsque ${liste}.`
      : `Vous souhaitez que le planning place en priorité les cours pour lesquels ${liste}.`
  }

  const agregat = dsl.aggregate
  if (!agregat) return resume ?? 'Une règle sur les cours de l’année.'

  const mesure = MESURES[agregat.metric] ?? agregat.metric.toLowerCase()
  const sujet = SUJETS[dsl.scope]
  const quand = QUAND[dsl.scope]
  const filtre = conditions.length > 0 ? `, en ne comptant que les cours où ${liste}` : ''

  const plafond = dsl.action === 'PENALIZE'
  const seuil =
    agregat.operator === 'LESS_THAN' || agregat.operator === 'LESS_THAN_OR_EQUAL'
      ? `garantir au moins ${agregat.value} ${mesure}`
      : plafond
      ? `limiter à ${agregat.value} ${mesure}`
      : `atteindre au moins ${agregat.value} ${mesure}`

  return `Vous souhaitez ${seuil} ${sujet} ${quand}${filtre}.`.replace(/\s+/g, ' ')
}

export interface LigneResume {
  etiquette: string
  valeur: string
}

/** Le contenu de la règle, en quatre lignes qu'on lit d'un coup d'œil. */
export function resumerContrainte(dsl: ConstraintDsl, schema?: DslSchema): LigneResume[] {
  const lignes: LigneResume[] = [{ etiquette: 'Cible', valeur: CIBLES[dsl.scope] }]

  const conditions = dsl.conditions.map((c) => conditionCourte(c, schema))
  const agregat = dsl.aggregate

  if (agregat) {
    const mesure = MESURES[agregat.metric] ?? agregat.metric.toLowerCase()
    const minimum =
      agregat.operator === 'LESS_THAN' || agregat.operator === 'LESS_THAN_OR_EQUAL'
    lignes.push({
      etiquette: 'Règle',
      valeur: `${minimum ? 'Minimum' : 'Maximum'} ${agregat.value} ${mesure}`,
    })
    if (conditions.length > 0) {
      lignes.push({ etiquette: 'Ne compte que', valeur: conditions.join(' · ') })
    }
  } else {
    lignes.push({
      etiquette: 'Règle',
      valeur:
        conditions.length > 0
          ? `${dsl.action === 'PENALIZE' ? 'À éviter' : 'À privilégier'} — ${conditions.join(' · ')}`
          : 'Aucune condition précisée',
    })
  }

  lignes.push({ etiquette: 'Période', valeur: PERIODES[dsl.scope] })
  return lignes
}

export interface Effet {
  texte: string
  ton: 'ok' | 'alerte'
}

/**
 * Ce que la règle change vraiment, une fois enregistrée.
 *
 * L'impact est mesuré par le serveur sur les cours réels de l'année, pas
 * estimé : « 0 séance concernée » est donc une information, et la plus utile
 * de toutes — presque toujours le signe d'un critère trop étroit (une matière
 * mal orthographiée, un niveau absent cette année) plutôt que d'une année sans
 * cas. Affiché en chiffre brut, il se lirait comme un simple nombre bas ;
 * formulé, il devient l'avertissement qu'il est.
 */
export function effetsConcrets(proposal: ConstraintProposal, dsl: ConstraintDsl): Effet[] {
  const effets: Effet[] = [{ ton: 'ok', texte: EFFET_NIVEAU[dsl.severity] }]

  const touchees = proposal.matched_lessons
  const total = proposal.total_lessons

  if (touchees === 0) {
    effets.push({
      ton: 'alerte',
      texte: total
        ? `Aucune des ${total} séances de l’année n’est concernée : la règle n’aurait aucun effet. Vérifiez la matière, le niveau ou le jour visés.`
        : 'Aucune séance de l’année n’est concernée : la règle n’aurait aucun effet.',
    })
  } else if (touchees != null) {
    effets.push({
      ton: 'ok',
      texte:
        total && total > 0
          ? `${touchees} séance${touchees > 1 ? 's' : ''} de l’année ${touchees > 1 ? 'sont concernées' : 'est concernée'}, sur ${total} au total.`
          : `${touchees} séance${touchees > 1 ? 's' : ''} de l’année ${touchees > 1 ? 'sont concernées' : 'est concernée'}.`,
    })
  }

  // Le verdict du serveur quand il en produit un : il porte parfois une
  // information que le seul décompte ne dit pas.
  if (proposal.verdict) effets.push({ ton: 'ok', texte: proposal.verdict })

  if (!proposal.feasible) {
    effets.push({
      ton: 'alerte',
      texte:
        'Avec les affectations actuelles, cette règle ne peut pas être tenue : la génération risque d’échouer tant que rien ne change.',
    })
  }

  for (const conflit of proposal.conflicts.slice(0, 3)) {
    effets.push({ ton: 'alerte', texte: `${conflit.subject} — ${conflit.detail}` })
  }

  for (const avertissement of proposal.warnings.slice(0, 2)) {
    effets.push({ ton: 'alerte', texte: avertissement })
  }

  effets.push({
    ton: 'ok',
    texte: 'Elle sera appliquée dès la prochaine génération de l’emploi du temps.',
  })

  return effets
}
