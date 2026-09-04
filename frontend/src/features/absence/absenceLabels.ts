import { useQuery } from '@tanstack/react-query'

import { organisationApi, type Eleve } from '@/api/organisation.api'
import type { StatutJustificatif, StatutPresence, TypeJustificatif } from '@/api/absence.api'

/**
 * Libellés et référentiel partagés par les écrans d'absence (appel, absences du
 * jour, justificatifs). Les valeurs des Record sont celles des enums backend :
 * une clé en trop ou en moins casse la compilation, ce qui évite les libellés
 * fantômes quand un enum bouge côté serveur.
 */

export const STATUT_PRESENCE_LABELS: Record<StatutPresence, string> = {
  PRESENT: 'Présent',
  ABSENT: 'Absent',
  RETARD: 'En retard',
  EXCLU: 'Exclusion',
}

export const STATUT_PRESENCE_VARIANTS: Record<StatutPresence, 'success' | 'danger' | 'warning' | 'info'> = {
  PRESENT: 'success',
  ABSENT: 'danger',
  RETARD: 'warning',
  EXCLU: 'info',
}

export const STATUT_JUSTIFICATIF_LABELS: Record<StatutJustificatif, string> = {
  EN_ATTENTE: 'En attente',
  VALIDE: 'Validé',
  REFUSE: 'Refusé',
}

export const STATUT_JUSTIFICATIF_VARIANTS: Record<StatutJustificatif, 'warning' | 'success' | 'danger'> = {
  EN_ATTENTE: 'warning',
  VALIDE: 'success',
  REFUSE: 'danger',
}

export const TYPE_JUSTIFICATIF_LABELS: Record<TypeJustificatif, string> = {
  MEDICAL: 'Certificat médical',
  FAMILIAL: 'Motif familial',
  ADMINISTRATIF: 'Document administratif',
  AUTRE: 'Autre',
}

/** Date du jour au format ISO local (et non UTC, qui décalerait la journée). */
export function todayIso(): string {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

/** `jours` jours avant aujourd'hui, même format. */
export function isoIlYA(jours: number): string {
  const d = new Date()
  d.setDate(d.getDate() - jours)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export function formatJour(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('fr-FR', { weekday: 'short', day: '2-digit', month: '2-digit' })
}

export function libelleEleve(e: Eleve): string {
  return `${e.nom} ${e.prenom} — ${e.classeCode} (${e.codeEleve})`
}

/**
 * Référentiel scolaire résolu une fois pour toute la page : les appels ne
 * portent que des identifiants, l'écran affiche des noms. Les clés de cache
 * sont celles déjà utilisées ailleurs pour partager les requêtes.
 */
export function useReferentielScolaire() {
  const { data: eleves = [] } = useQuery({ queryKey: ['eleves-all'], queryFn: organisationApi.eleves.list })
  const { data: classes = [] } = useQuery({ queryKey: ['classes-all'], queryFn: organisationApi.classes.list })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects-all'], queryFn: organisationApi.subjects.list })
  const { data: teachers = [] } = useQuery({ queryKey: ['teachers-all'], queryFn: organisationApi.teachers.list })

  const eleveById = (id: number) => eleves.find((e) => e.idEleve === id)

  return {
    eleves,
    classes,
    subjects,
    teachers,
    eleveById,
    nomEleve: (id: number) => {
      const e = eleveById(id)
      return e ? `${e.nom} ${e.prenom}` : `Élève #${id}`
    },
    classeEleve: (id: number) => eleveById(id)?.classeCode ?? '—',
    nomClasse: (id: number | null | undefined) =>
      id == null ? '—' : (classes.find((c) => c.idClasse === id)?.code ?? `Classe #${id}`),
    nomMatiere: (id: number | null | undefined) =>
      id == null ? '—' : (subjects.find((s) => s.idMatiere === id)?.libMatiere ?? `Matière #${id}`),
    nomEnseignant: (id: number | null | undefined) =>
      id == null ? '—' : (teachers.find((t) => t.idEnseignant === id)?.nomComplet ?? `Enseignant #${id}`),
  }
}
