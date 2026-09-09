import apiClient from './client'
import type { Teacher } from './organisation.api'

// ── Enums ─────────────────────────────────────────────────────────────────────
export type StatutPresencePersonnel =
  | 'PRESENT'
  | 'ABSENT'
  | 'EN_RETARD'
  | 'EN_CONGE'
  | 'ABSENCE_JUSTIFIEE'
  | 'ABSENCE_NON_JUSTIFIEE'

export type TypePersonnel = 'ENSEIGNANT' | 'SURVEILLANT' | 'ADMINISTRATIF'
export type Periode = 'MATIN' | 'APRES_MIDI'

// ── Présence ──────────────────────────────────────────────────────────────────
export interface PresencePersonnel {
  id: number
  membrePersonnelId: number
  /** Nom complet résolu côté serveur ; null si le membre n'existe plus. */
  nomMembre: string | null
  typePersonnel: TypePersonnel
  datePointage: string
  periode: Periode
  heureArrivee: string
  heureDepart: string
  statut: StatutPresencePersonnel
  minutesRetard: number
  note: string
  saisiPar: string
  saisiA: string
  createdAt: string
}

export interface PointageRequete {
  membrePersonnelId: number
  typePersonnel: TypePersonnel
  datePointage: string
  periode: Periode
  statut: StatutPresencePersonnel
  heureArrivee?: string
  heureDepart?: string
  minutesRetard?: number
  note?: string
}

export interface ResultatPointageMasse {
  total: number
  reussis: number
  echoues: number
  resultats: PresencePersonnel[]
  echecs: Array<{ membrePersonnelId: number; raison: string }>
}

// ── Rapport journalier ────────────────────────────────────────────────────────
export interface RapportJournalier {
  date: string
  totalPresents: number
  totalAbsents: number
  totalEnRetard: number
  totalEnConge: number
  totalAbsencesJustifiees: number
  totalAbsencesNonJustifiees: number
  enregistrements: PresencePersonnel[]
}

// ── Suivi heures enseignant ───────────────────────────────────────────────────
export interface SuiviHeuresEnseignant {
  id: number
  enseignantId: number
  numeroSemaine: number
  anneeAcademique: string
  heuresPrevues: number
  heuresRealisees: number
  heuresManquees: number
  tauxPresence: number
  notes: string
  createdAt: string
}

export interface MiseAJourHeuresRequete {
  enseignantId: number
  numeroSemaine: number
  anneeAcademique: string
  heuresPrevues: number
  heuresRealisees: number
  notes?: string
}

// ── Justificatifs de pointage (personnel) ─────────────────────────────────────
export type TypeJustificatifPointage = 'CERTIFICAT_MEDICAL' | 'DOCUMENT_ADMINISTRATIF' | 'AUTRE'
export type StatutJustificatifPointage = 'EN_ATTENTE' | 'APPROUVE' | 'REJETE'

export interface JustificatifPointage {
  id: number
  membrePersonnelId: number
  presencePersonnelId: number
  /** Contexte du pointage justifié, résolu par le serveur. */
  nomMembre: string | null
  typePersonnel: TypePersonnel | null
  datePointage: string | null
  periode: Periode | null
  typeDocument: TypeJustificatifPointage
  description: string
  cheminDocument: string | null
  commentaireAdmin: string | null
  statut: StatutJustificatifPointage
  soumisA: string
  traitePar: string | null
  traiteA: string | null
  createdAt: string
}

export interface SoumissionJustificatifPointageRequete {
  presencePersonnelId: number
  typeDocument: TypeJustificatifPointage
  description: string
  cheminDocument?: string
  commentaireAdmin?: string
}

// ─────────────────────────────────────────────────────────────────────────────

export const pointageApi = {
  pointer: (dto: PointageRequete) =>
    apiClient.post<PresencePersonnel>('/api/v1/pointage', dto).then((r) => r.data),

  pointerEnMasse: (pointages: PointageRequete[]) =>
    apiClient.post<ResultatPointageMasse>('/api/v1/pointage/masse', { pointages }).then((r) => r.data),

  modifier: (id: number, dto: PointageRequete) =>
    apiClient.patch<PresencePersonnel>(`/api/v1/pointage/${id}`, dto).then((r) => r.data),

  rapport: (date: string) =>
    apiClient.get<RapportJournalier>('/api/v1/pointage/rapport', { params: { date } }).then((r) => r.data),

  enseignantsDisponibles: (date: string, periode?: Periode) =>
    apiClient.get<Teacher[]>('/api/v1/pointage/enseignants-disponibles', { params: { date, periode } }).then((r) => r.data),

  historique: (membreId: number, debut: string, fin: string) =>
    apiClient.get<PresencePersonnel[]>(`/api/v1/pointage/personnel/${membreId}/historique`, { params: { debut, fin } }).then((r) => r.data),

  justificatifs: {
    soumettre: (dto: SoumissionJustificatifPointageRequete) =>
      apiClient.post<JustificatifPointage>('/api/v1/pointage/justificatifs', dto).then((r) => r.data),
    enAttente: () =>
      apiClient.get<JustificatifPointage[]>('/api/v1/pointage/justificatifs/en-attente').then((r) => r.data),
    /** 404 si la présence n'a encore aucun justificatif — à traiter côté appelant. */
    parPresence: (presenceId: number) =>
      apiClient.get<JustificatifPointage>(`/api/v1/pointage/justificatifs/presence/${presenceId}`).then((r) => r.data),
    /** L'auteur de la décision est déduit du compte connecté côté serveur. */
    traiter: (id: number, dto: { approuve: boolean; motifRejet?: string }) =>
      apiClient.patch<JustificatifPointage>(`/api/v1/pointage/justificatifs/${id}/traiter`, dto).then((r) => r.data),
  },

  suiviHeures: {
    annuel: (enseignantId: number, anneeAcademique: string) =>
      apiClient.get<SuiviHeuresEnseignant[]>(`/api/v1/pointage/suivi-heures/enseignant/${enseignantId}/annuel`, { params: { anneeAcademique } }).then((r) => r.data),
    semaine: (enseignantId: number, semaine: number, anneeAcademique: string) =>
      apiClient.get<SuiviHeuresEnseignant>(`/api/v1/pointage/suivi-heures/enseignant/${enseignantId}/semaine/${semaine}`, { params: { anneeAcademique } }).then((r) => r.data),
    mettreAJour: (dto: MiseAJourHeuresRequete) =>
      apiClient.post<SuiviHeuresEnseignant>('/api/v1/pointage/suivi-heures', dto).then((r) => r.data),
  },
}
