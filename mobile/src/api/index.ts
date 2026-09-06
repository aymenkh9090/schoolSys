import { ai, api, query, upload, type FichierAEnvoyer } from './client'
import type {
  AppelReponse,
  CahierSeanceReponse,
  ChatResponse,
  CoursChatMessage,
  DocumentDepose,
  Eleve,
  EnregistrementCahierRequete,
  GeneratedTimetable,
  OuvertureAppelRequete,
  SchoolClass,
  SchoolYear,
  SignalementEleve,
  StatutPresence,
  Subject,
  Teacher,
  TeacherTimetableView,
} from './types'

export const appelApi = {
  ouvrir: (dto: OuvertureAppelRequete) =>
    api<AppelReponse>('/api/v1/appel/seances/ouvrir', { method: 'POST', body: dto }),
  get: (id: number) => api<AppelReponse>(`/api/v1/appel/seances/${id}`),
  lister: (params: { date?: string; enseignantId?: number }) =>
    api<AppelReponse[]>(`/api/v1/appel/seances${query(params)}`),
  // L'auteur de la modification comme celui de la clôture sont déduits du
  // compte connecté côté serveur : le mobile n'envoie aucun identifiant.
  modifierStatut: (
    ligneId: number,
    body: { statut: StatutPresence; arriveeAt?: string; raisonExclusion?: string }
  ) => api(`/api/v1/appel/lignes/${ligneId}/statut`, { method: 'PATCH', body }),
  /**
   * Absences et exclusions non justifiées de la classe, sur les jours récents.
   *
   * Interrogé par classe et non par enseignant : l'élève signalé absent en
   * première heure doit apparaître au professeur de la deuxième, et à tous les
   * suivants tant que la vie scolaire n'a rien tranché.
   */
  signalements: (groupeClasseId: number, params?: { depuis?: string; jusqua?: string }) =>
    api<SignalementEleve[]>(
      `/api/v1/appel/classes/${groupeClasseId}/signalements${query(params ?? {})}`
    ),
  verrouiller: (seanceId: number) =>
    api(`/api/v1/appel/seances/${seanceId}/verrouiller`, { method: 'POST' }),
}

export const cahierApi = {
  recuperer: (seanceAppelId: number) =>
    api<CahierSeanceReponse>(`/api/v1/cahier/seances/${seanceAppelId}`),
  enregistrer: (seanceAppelId: number, dto: EnregistrementCahierRequete) =>
    api<CahierSeanceReponse>(`/api/v1/cahier/seances/${seanceAppelId}`, {
      method: 'POST',
      body: dto,
    }),
}

export const organisationApi = {
  moi: () => api<Teacher>('/api/organisation/teachers/me'),
  classes: () => api<SchoolClass[]>('/api/organisation/classes'),
  matieres: () => api<Subject[]>('/api/organisation/subjects/enseignees'),
  annees: () => api<SchoolYear[]>('/api/organisation/school-years/list'),
  /**
   * Élèves d'une seule classe, et non les 1000 de l'établissement comme le
   * fait le web : sur mobile, la charge utile compte.
   */
  elevesDeClasse: (classeId: number) =>
    api<Eleve[]>(`/api/organisation/eleves/classe/${classeId}/actifs`),
}

export const planningApi = {
  generes: () => api<GeneratedTimetable[]>('/api/planning/timetable/generated'),
  vueEnseignant: (jobId: number, codeEnseignant: string) =>
    api<TeacherTimetableView>(
      `/api/planning/timetable/jobs/${jobId}/view/teacher/${codeEnseignant}`
    ),
}

export const assistantApi = {
  /** Question libre sur ses propres cahiers. Lecture seule, aucun identifiant envoyé. */
  ask: (message: string) =>
    ai<ChatResponse>('/api/cahier/assistant/chat', {
      method: 'POST',
      body: { message },
      timeoutMs: 120_000,
    }),
}

export const coursApi = {
  /**
   * Dépose un cours PDF et reçoit l'identifiant qui servira à en parler.
   *
   * Le document n'est ni stocké sur disque ni indexé : il est gardé en mémoire
   * le temps d'une préparation, rattaché au compte qui l'a envoyé.
   */
  deposer: (fichier: FichierAEnvoyer) =>
    upload<DocumentDepose>('/api/cours/document', 'fichier', fichier, 120_000),

  /** Demande libre à propos du cours déposé. Le format de la réponse suit la demande. */
  demander: (documentId: string, message: string, historique: CoursChatMessage[]) =>
    ai<ChatResponse>('/api/cours/chat', {
      method: 'POST',
      body: { document_id: documentId, message, historique },
      timeoutMs: 240_000,
    }),
}
