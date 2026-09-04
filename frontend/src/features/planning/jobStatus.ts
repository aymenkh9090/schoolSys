import type { SolverStatus } from '@/api/planning.api'

export const STATUS_LABELS: Record<SolverStatus, string> = {
  PENDING: 'En attente',
  RUNNING: 'En cours',
  SOLVED: 'Résolu',
  INFEASIBLE: 'Généré avec conflits',
  FAILED: 'Échoué',
  CANCELLED: 'Annulé',
}

export const STATUS_VARIANTS: Record<SolverStatus, 'default' | 'info' | 'success' | 'warning' | 'danger'> = {
  PENDING: 'default',
  RUNNING: 'info',
  SOLVED: 'success',
  INFEASIBLE: 'warning',
  FAILED: 'danger',
  CANCELLED: 'default',
}

/**
 * Le job a produit un emploi du temps exploitable : toutes les séances sont
 * placées. INFEASIBLE signifie qu'il reste des contraintes violées à corriger
 * à la main, pas qu'il n'y a rien à montrer — seul FAILED (plantage du
 * solveur) est dépourvu de résultat.
 */
export function hasTimetable(status: SolverStatus): boolean {
  return status === 'SOLVED' || status === 'INFEASIBLE'
}

/**
 * Traduit le score technique Timefold (ex. "0hard/-2medium/-5soft") en résumé
 * compréhensible pour un admin non technique, sans jargon de solveur.
 */
export function describeScore(scoreAchieved: string | null | undefined): string {
  if (!scoreAchieved) return 'Score indisponible'
  const match = scoreAchieved.match(/(-?\d+)hard\/(-?\d+)medium\/(-?\d+)soft/)
  if (!match) return scoreAchieved

  const hard = Math.abs(Number(match[1]))
  const medium = Math.abs(Number(match[2]))
  if (hard === 0 && medium === 0) return 'Aucun conflit'

  const parts: string[] = []
  if (hard > 0) parts.push(`${hard} conflit${hard > 1 ? 's' : ''} important${hard > 1 ? 's' : ''}`)
  if (medium > 0) parts.push(`${medium} conflit${medium > 1 ? 's' : ''} moyen${medium > 1 ? 's' : ''}`)
  return parts.join(', ')
}
