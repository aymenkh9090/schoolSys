/**
 * Miroir des DTO backend réellement consommés par le mobile — pas de l'API
 * entière. Chaque champ absent ici est un champ que les trois écrans n'ouvrent
 * pas ; les ajouter « au cas où » donnerait une fausse impression de couverture.
 */

export type StatutPresence = 'PRESENT' | 'ABSENT' | 'RETARD' | 'EXCLU'

export interface LigneAppelReponse {
  id: number
  eleveId: number
  statut: StatutPresence
  arriveeAt: string
  minutesRetard: number
  raisonExclusion: string
  estJustifie: boolean
}

/**
 * Une absence ou une exclusion qu'aucun justificatif validé n'a soldée.
 *
 * Rattachée à la CLASSE et non à l'enseignant qui l'a saisie : c'est ce qui la
 * fait suivre l'élève de séance en séance, y compris chez un autre professeur,
 * tant que la vie scolaire ne l'a pas justifiée.
 */
export interface SignalementEleve {
  eleveId: number
  ligneAppelId: number
  seanceAppelId: number
  statut: 'ABSENT' | 'EXCLU'
  dateSeance: string
  /** Heure de début du créneau — absente si la séance n'est pas au planning. */
  heureDebut?: string
  matiereId?: number
  matiere?: string
  enseignantId?: number
  enseignant?: string
  raisonExclusion?: string
  justificatifId?: number
  /** EN_ATTENTE : la famille a répondu, la vie scolaire ne s'est pas prononcée. */
  statutJustificatif?: 'EN_ATTENTE' | 'VALIDE' | 'REFUSE'
}

export interface AppelReponse {
  id: number
  seancePlanningId: number
  enseignantId: number
  groupeClasseId: number
  matiereId: number
  anneeAcademique: string
  dateSeance: string
  ouvertureAt: string
  estVerrouille: boolean
  lignesAppel: LigneAppelReponse[]
}

export interface OuvertureAppelRequete {
  seancePlanningId: number
  enseignantId: number
  groupeClasseId: number
  anneeAcademique: string
  matiereId?: number
  dateSeance?: string
}

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
}

export interface EnregistrementCahierRequete {
  sujet?: string
  chapitre?: string
  activites?: string
  remarques?: string
  travailDemande?: string
  dateEcheance?: string
}

/**
 * Affectation d'enseignement : quelle matière, dans quelle classe.
 *
 * `/teachers/me` les renvoie déjà avec la classe et son effectif — l'écran
 * « Mes classes » n'a donc aucun appel supplémentaire à faire.
 */
export interface TeacherAssignment {
  idTeachingAssignment: number
  classGroupId: number
  classGroupCode: string
  classGroupNbEleve: number
  subjectCode: string
  subjectLib: string
  levelNom: string
  schoolYearNom: string
  isActive: boolean
}

export interface Teacher {
  idEnseignant: number
  codeEnseignant: string
  nom: string
  prenom: string
  nomComplet: string
  specialite?: string
  totalHeures?: number
  affectations?: TeacherAssignment[]
}

export interface SchoolClass {
  idClasse: number
  code: string
  levelNom: string
}

export interface Subject {
  idMatiere: number
  codeMatiere: string
  libMatiere: string
}

export interface SchoolYear {
  idAnnee: number
  nom: string
  estCourante: boolean
}

export interface Eleve {
  idEleve: number
  nom: string
  prenom: string
  classeId: number
  estActif: boolean
}

export interface SessionView {
  id: number
  startTime: string
  endTime: string
  subjectCode: string
  subjectName: string
  classCode?: string
  roomCode?: string
  sessionType: string
  groupLabel: string
}

export interface DaySchedule {
  day: string
  dayLabel: string
  sessions: SessionView[]
}

export interface TeacherTimetableView {
  jobId: number
  teacherCode: string
  teacherName: string
  schedule: DaySchedule[]
}

export interface GeneratedTimetable {
  jobId: number
  status: string
  publishedAt?: string
}

/** Réponse des assistants Python — snake_case, sérialisé tel quel par Pydantic. */
export interface ChatResponse {
  answer: string
  tools_used: string[]
  duration_ms: number
}

/** Accusé de dépôt : ce que le service a réellement lu du document. */
export interface DocumentDepose {
  document_id: string
  nom_fichier: string
  pages: number
  /** Caractères réellement retenus — avec `tronque`, dit ce qui entrera dans les réponses. */
  caracteres_lus: number
  tronque: boolean
}

export interface CoursChatMessage {
  role: 'user' | 'assistant'
  content: string
}
