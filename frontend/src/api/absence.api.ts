import apiClient from './client'

// ── Enums ─────────────────────────────────────────────────────────────────────
export type StatutPresence = 'PRESENT' | 'ABSENT' | 'RETARD' | 'EXCLU'
// Miroir exact des enums backend (StatutJustificatif / TypeJustificatif) : toute
// autre valeur est rejetée à la désérialisation côté serveur.
export type StatutJustificatif = 'EN_ATTENTE' | 'VALIDE' | 'REFUSE'
export type TypeJustificatif = 'MEDICAL' | 'FAMILIAL' | 'ADMINISTRATIF' | 'AUTRE'

// ── Appel ─────────────────────────────────────────────────────────────────────
export interface AppelReponse {
  id: number
  seancePlanningId: number
  enseignantId: number
  groupeClasseId: number
  matiereId: number
  anneeAcademique: string
  /** Jour de cours concerné (YYYY-MM-DD) — une séance du planning revient chaque semaine. */
  dateSeance: string
  ouvertureAt: string
  fermetureAt: string
  estVerrouille: boolean
  verrouillageAt: string
  lignesAppel: LigneAppelReponse[]
}

export interface LigneAppelReponse {
  id: number
  eleveId: number
  statut: StatutPresence
  arriveeAt: string
  minutesRetard: number
  raisonExclusion: string
  estJustifie: boolean
}

export interface OuvertureAppelRequete {
  seancePlanningId: number
  enseignantId: number
  groupeClasseId: number
  anneeAcademique: string
  matiereId?: number
  /** Jour de cours. Absent = aujourd'hui ; renseigné pour régulariser une séance passée. */
  dateSeance?: string
}

export interface ModificationStatutRequete {
  statut: StatutPresence
  raisonExclusion?: string
  arriveeAt?: string
  modifiePar?: number
}

// ── Justificatifs ─────────────────────────────────────────────────────────────
export interface JustificatifReponse {
  id: number
  ligneAppelId: number
  eleveId: number
  /** Séance concernée, renvoyée par l'API pour situer l'absence sans recharger l'appel. */
  dateSeance: string | null
  groupeClasseId: number | null
  matiereId: number | null
  soumisAt: string
  soumisParId: number
  typeDocument: TypeJustificatif
  referenceDocument: string
  statut: StatutJustificatif
  traiteAt: string
  traiteParId: number
  notesAdmin: string
}

// L'auteur (soumisParId / traiteParId) est déduit du compte connecté côté
// serveur : le client ne l'envoie jamais.
export interface SoumissionJustificatifRequete {
  ligneAppelId: number
  typeDocument: TypeJustificatif
  referenceDocument?: string
}

export interface TraitementJustificatifRequete {
  decision: StatutJustificatif
  notesAdmin?: string
}

/** Une absence/retard/exclusion d'un élève, replacée dans sa séance. */
export interface AbsenceEleveReponse {
  ligneAppelId: number
  seanceAppelId: number
  eleveId: number
  dateSeance: string
  groupeClasseId: number
  matiereId: number | null
  enseignantId: number
  anneeAcademique: string
  ouvertureAt: string
  statut: StatutPresence
  estJustifie: boolean
  minutesRetard: number | null
  raisonExclusion: string | null
  seanceVerrouillee: boolean
  justificatifId: number | null
  statutJustificatif: StatutJustificatif | null
}

// ── Cahier de séance ─────────────────────────────────────────────────────────
export interface CahierSeanceReponse {
  id: number
  seanceAppelId: number
  enseignantId: number
  sujet: string
  chapitre: string
  activites: string
  remarques: string
  travailDemande: string
  dateEcheance: string
  estVerrouille: boolean
  verrouillageAt: string
}

export interface EnregistrementCahierRequete {
  sujet?: string
  chapitre?: string
  activites?: string
  remarques?: string
  travailDemande?: string
  dateEcheance?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── Statistiques ──────────────────────────────────────────────────────────────
export interface StatistiquesAbsenceReponse {
  groupeClasseId: number
  tenantId: string
  periode: string
  debut: string
  fin: string
  totalSeances: number
  totalAbsences: number
  totalJustifiees: number
  totalNonJustifiees: number
  totalRetards: number
  totalExclusions: number
  tauxAbsenteisme: number
}

// ─────────────────────────────────────────────────────────────────────────────

export const absenceApi = {
  appel: {
    ouvrir: (dto: OuvertureAppelRequete) =>
      apiClient.post<AppelReponse>('/api/v1/appel/seances/ouvrir', dto).then((r) => r.data),
    get: (id: number) =>
      apiClient.get<AppelReponse>(`/api/v1/appel/seances/${id}`).then((r) => r.data),
    lister: (params?: { date?: string; groupeClasseId?: number; enseignantId?: number; estVerrouille?: boolean }) =>
      apiClient.get<AppelReponse[]>('/api/v1/appel/seances', { params }).then((r) => r.data),
    modifierStatut: (ligneId: number, dto: ModificationStatutRequete) =>
      apiClient.patch<LigneAppelReponse>(`/api/v1/appel/lignes/${ligneId}/statut`, dto).then((r) => r.data),
    // L'auteur de la clôture est déduit du compte connecté côté serveur.
    verrouiller: (seanceId: number) =>
      apiClient.post(`/api/v1/appel/seances/${seanceId}/verrouiller`),
    historique: (seanceId: number) =>
      apiClient.get(`/api/v1/appel/seances/${seanceId}/historique`).then((r) => r.data),
    /** Dossier d'absences d'un élève sur une période — base du circuit de justification. */
    absencesEleve: (eleveId: number, params?: { debut?: string; fin?: string; inclureRetards?: boolean }) =>
      apiClient
        .get<AbsenceEleveReponse[]>(`/api/v1/appel/eleves/${eleveId}/absences`, { params })
        .then((r) => r.data),
  },

  cahier: {
    recuperer: (seanceAppelId: number) =>
      apiClient.get<CahierSeanceReponse>(`/api/v1/cahier/seances/${seanceAppelId}`).then((r) => r.data),
    enregistrer: (seanceAppelId: number, dto: EnregistrementCahierRequete) =>
      apiClient.post<CahierSeanceReponse>(`/api/v1/cahier/seances/${seanceAppelId}`, dto).then((r) => r.data),
    verrouiller: (seanceAppelId: number) =>
      apiClient.post(`/api/v1/cahier/seances/${seanceAppelId}/verrouiller`),
    lister: (params: { enseignantId: number; debut?: string; fin?: string; page?: number; size?: number }) =>
      apiClient.get<Page<CahierSeanceReponse>>('/api/v1/cahier', { params }).then((r) => r.data),
  },

  justificatifs: {
    /** `eleveId` omis = tous les élèves (file de traitement de la vie scolaire). */
    list: (params?: { eleveId?: number; statut?: StatutJustificatif; size?: number }) =>
      apiClient
        .get<Page<JustificatifReponse>>('/api/v1/justificatifs', { params: { size: 200, ...params } })
        .then((r) => r.data.content),
    soumettre: (dto: SoumissionJustificatifRequete) =>
      apiClient.post<JustificatifReponse>('/api/v1/justificatifs', dto).then((r) => r.data),
    approuver: (id: number, notesAdmin?: string) =>
      apiClient
        .patch<JustificatifReponse>(`/api/v1/justificatifs/${id}/approuver`, { decision: 'VALIDE', notesAdmin })
        .then((r) => r.data),
    refuser: (id: number, notesAdmin?: string) =>
      apiClient
        .patch<JustificatifReponse>(`/api/v1/justificatifs/${id}/refuser`, { decision: 'REFUSE', notesAdmin })
        .then((r) => r.data),
  },

  statistiques: {
    dashboard: (periode?: string) =>
      apiClient.get<StatistiquesAbsenceReponse>('/api/v1/statistiques/absences/dashboard', { params: periode ? { periode } : undefined }).then((r) => r.data),
    byClass: (groupeClasseId: number, params?: { debut?: string; fin?: string }) =>
      apiClient.get<StatistiquesAbsenceReponse>(`/api/v1/statistiques/absences/classes/${groupeClasseId}`, { params }).then((r) => r.data),
    byEleve: (eleveId: number, anneeAcademique: string) =>
      apiClient.get(`/api/v1/statistiques/absences/eleves/${eleveId}`, { params: { anneeAcademique } }).then((r) => r.data),
  },
}
