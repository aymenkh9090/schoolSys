import apiClient from './client'
import type { UserRole } from '@/lib/roles'

// ── Enums ─────────────────────────────────────────────────────────────────────
export type RoomType =
  | 'NORMALE'
  | 'LABSCIENCE'
  | 'LABPHYSIQUE'
  | 'LABINFORMATIQUE'
  | 'LABTECHNIQUE'
  | 'SALLESPORT'
  | 'SALLEDESSIN'
  | 'SALLEMUSIQUE'
  | 'AMPHI'
  | 'BIBLIOTHEQUE'
export type { UserRole } from '@/lib/roles'
export type Specialite =
  | 'TCOM' | 'SCIE' | 'MATH' | 'LETR' | 'TECH' | 'ECON' | 'INFO'

// ── School Years ──────────────────────────────────────────────────────────────
export interface SchoolYear {
  idAnnee: number
  nom: string
  dateDebut: string
  dateFin: string
  estActive: boolean
  estCourante: boolean
  nombreClasses: number
  nombreAffectations: number
}

export interface SchoolYearRequest {
  nom: string
  dateDebut: string
  dateFin: string
  estActive?: boolean
  estCourante?: boolean
}

// ── Levels ────────────────────────────────────────────────────────────────────
export interface Level {
  idNiveau: number
  nom: string
  code: string
  description: string
  estActif: boolean
  nombreClasses: number
  nombreMatieres: number
}

export interface LevelRequest {
  nom: string
  code: string
  description: string
  estActif?: boolean
}

// ── Subjects ──────────────────────────────────────────────────────────────────
export interface Subject {
  idMatiere: number
  codeMatiere: string
  libMatiere: string
  description: string
  necessiteLab: boolean
  necessiteSport: boolean
  couleur: string
  abreviation: string
  estPrincipale: boolean
  estEnseignee: boolean
  nombreNiveaux: number
}

export interface SubjectRequest {
  codeMatiere: string
  libMatiere: string
  description?: string
  necessiteLab?: boolean
  necessiteSport?: boolean
  couleur?: string
  abreviation?: string
  estPrincipale?: boolean
  estEnseignee?: boolean
}

export interface SubjectLevel {
  idNiveauMatiere: number
  subjectId: number
  subjectCode: string
  subjectLib: string
  subjectCouleur: string
  levelId: number
  levelNom: string
  levelCode: string
  heuresSemaine: number
  description: string
  estObligatoire: boolean
  coefficient: number
  maxHeuresConsecutives: number
  sessionTypes?: SubjectSessionType[]
  patterns?: SchoolPattern[]
}

export interface SubjectLevelRequest {
  subjectId: number
  levelId: number
  heuresSemaine: number
  description?: string
  estObligatoire?: boolean
  coefficient?: number
  maxHeuresConsecutives?: number
}

// ── School Patterns (programme de l'école, copié + adapté depuis le national) ──
export type SessionType = 'COURSE' | 'TD' | 'TP' | 'LAB' | 'SPORT' | 'EXAM'
export type WeekParity = 'ALL' | 'ODD' | 'EVEN' | 'BIWEEKLY'
export type PatternType = 'WEEKLY_IDENTICAL' | 'ALTERNATING' | 'BIWEEKLY'
/** Sous-ensemble de RoomType accepté par le backend pour PatternDetail.requiredRoomType */
export const PATTERN_ROOM_TYPES = ['NORMALE', 'LABSCIENCE', 'LABPHYSIQUE', 'LABINFORMATIQUE', 'LABTECHNIQUE', 'SALLESPORT'] as const
export type PatternRoomType = (typeof PATTERN_ROOM_TYPES)[number]

export interface SubjectSessionType {
  idSubjectSessionType: number
  subjectLevelId: number
  subjectCode: string
  levelCode: string
  type: SessionType
  duration: number
  requiresSplit: boolean
  groupCount?: number
  estActif?: boolean
}

export interface SchoolPatternDetail {
  idPatternDetail: number
  sessionOrder: number
  duration: number
  weekParity: WeekParity | null
  isSplit: boolean | null
  splitGroupIndex: number | null
  type: SessionType
  requiredRoomType: PatternRoomType | null
  subjectSessionTypeId: number | null
  sessionTypeDuration?: number
  sessionTypeRequiresSplit?: boolean
}

export interface SchoolPattern {
  idPattern: number
  name: string
  totalHours: number
  sessionCount: number
  repartition: string | null
  patternType: PatternType | null
  active?: boolean
  schoolYearId: number | null
  schoolYearNom?: string | null
  subjectLevelId: number
  subjectCode: string
  subjectLib: string
  levelCode: string
  levelNom: string
  details: SchoolPatternDetail[]
}

export interface SchoolPatternDetailRequest {
  sessionOrder: number
  duration: number
  weekParity?: WeekParity
  isSplit?: boolean
  splitGroupIndex?: number
  type: SessionType
  requiredRoomType?: PatternRoomType
  subjectSessionTypeId?: number
}

export interface SchoolPatternRequest {
  name: string
  totalHours: number
  sessionCount: number
  repartition?: string
  patternType?: PatternType
  subjectLevelId: number
  schoolYearId?: number
  details: SchoolPatternDetailRequest[]
}

// ── Rooms ─────────────────────────────────────────────────────────────────────
export interface Room {
  idSalle: number
  codeSalle: string
  typeSalle: RoomType
  capacite: number
  codeBloc: string
  numEtage: string
  equipements: string | null
  estDisponible: boolean | null
}

export interface RoomRequest {
  codeSalle: string
  typeSalle: RoomType
  capacite?: number
  codeBloc?: string
  numEtage?: string
  equipements?: string
  estDisponible?: boolean
}

export interface RoomImportResult {
  totalLignes: number
  importes: number
  ignores: number
  erreurs: Array<{ ligne: number; codeSalle: string; message: string }>
}

// ── Classes ───────────────────────────────────────────────────────────────────
export interface SchoolClass {
  idClasse: number
  code: string
  codeSpecialite: Specialite
  nbEleve: number
  estActif: boolean
  schoolYearId: number
  schoolYearNom: string
  levelId: number
  levelNom: string
  levelCode: string
  nombreAffectations: number
}

export interface ClassRequest {
  code: string
  codeSpecialite: Specialite
  nbEleve?: number
  estActif?: boolean
  schoolYearId: number
  levelId: number
}

export interface BulkClassResult {
  created: number
  skipped: number
  classes: SchoolClass[]
}

// ── Teachers ──────────────────────────────────────────────────────────────────
export interface Teacher {
  idEnseignant: number
  codeEnseignant: string
  numIdentite: string
  nom: string
  prenom: string
  nomComplet: string
  email: string
  telephone: string
  maxHeuresSemaine: number
  maxHeuresJour: number
  minHeuresJour: number
  estEnPoste: boolean
  photo?: string
  specialite: string
  nombreAffectations?: number
  totalHeures?: number
  affectations?: TeacherAssignment[]
}

export interface TeacherAssignment {
  idTeachingAssignment: number
  nomClasse: string
  nomMatiere: string
  typeSeance: string
  isActive: boolean
}

export interface TeacherRequest {
  codeEnseignant: string
  numIdentite: string
  nom: string
  prenom: string
  email?: string
  telephone?: string
  maxHeuresSemaine?: number
  maxHeuresJour?: number
  minHeuresJour?: number
  estEnPoste?: boolean
  specialite?: string
}

export interface TeacherImportResult {
  totalLignes: number
  importes: number
  ignores: number
  erreurs: Array<{ ligne: number; message: string }>
}

// ── Students ──────────────────────────────────────────────────────────────────
export interface Eleve {
  idEleve: number
  codeEleve: string
  nom: string
  prenom: string
  numIdentite: string
  email: string
  telephone: string
  classeId: number
  classeCode: string
  estActif: boolean
}

export interface EleveRequest {
  codeEleve: string
  nom: string
  prenom: string
  numIdentite?: string
  email?: string
  telephone?: string
  classeId: number
}

export interface EleveImportResult {
  totalLignes: number
  importes: number
  ignores: number
  erreurs: Array<{ ligne: number; message: string }>
}

// ── Teaching Assignments ──────────────────────────────────────────────────────
export interface TeachingAssignmentFull {
  idTeachingAssignment: number
  schoolYearId: number
  schoolYearNom: string
  teacherId: number
  teacherCode: string
  teacherNomComplet: string
  teacherEmail: string
  classGroupId: number
  classGroupCode: string
  classGroupSpecialite: Specialite
  classGroupNbEleve: number
  subjectLevelId: number
  subjectCode: string
  subjectLib: string
  subjectCouleur: string
  levelCode: string
  levelNom: string
  subjectSessionTypeId: number
  sessionDuration: number
  sessionRequiresSplit: boolean
  sessionGroupCount: number
  priority: number
  isActive: boolean
}

export interface TeachingAssignmentRequest {
  schoolYearId: number
  teacherId: number
  classGroupId: number
  subjectLevelId: number
  subjectSessionTypeId: number
  priority?: number
  isActive?: boolean
}

export interface MissingAssignmentEntry {
  classGroupCode: string
  subjectCode: string
  levelCode: string
}

// ── School Users ──────────────────────────────────────────────────────────────
export interface SchoolUser {
  id: number
  email: string
  nomComplet: string
  role: UserRole
  matiere: string
  telephone: string
  actif: boolean
  enseignantId?: number
  createdAt: string
  username?: string
  tempPassword?: string
}

export interface CreateSchoolUserRequest {
  role: UserRole
  /** role=TEACHER : fiche enseignant à lier (email/nom/matière dérivés de la fiche) */
  teacherId?: number
  /** rôles non-enseignants uniquement */
  email?: string
  nomComplet?: string
  telephone?: string
}

// ── National Patterns ─────────────────────────────────────────────────────────
export interface NationalPatternSession {
  idNationalPatternSession: number
  sessionOrder: number
  sessionType: string
  duration: number
  groupingType?: string
  requiredRoomType?: string
  weekParity?: string
}

export interface NationalPatternDetail {
  idNationalPatternDetail: number
  subjectCode: string
  totalHoursPerWeek: number
  repartition?: string
  sessions?: NationalPatternSession[]
}

export interface NationalPattern {
  idNationalPattern: number
  code: string
  name: string
  version?: number
  academicYear?: number
  active: boolean
  countryCode: string
  levelCode: string
  details?: NationalPatternDetail[]
}

export interface NationalPatternApplyResult {
  created: number
  skipped: number
  message?: string
}

// ── School Config ─────────────────────────────────────────────────────────────
export interface WorkingDayRequest {
  dayOfWeek: string
  active?: boolean
  morningStart: string
  morningEnd: string
  afternoonStart?: string
  afternoonEnd?: string
}

export interface SchoolConfigRequest {
  workingDays: WorkingDayRequest[]
  slotDurationMinutes: number
}

export interface SchoolConfigResponse {
  workingDays?: Array<{ dayOfWeek: string; active?: boolean; morningStart?: string; morningEnd?: string; afternoonStart?: string; afternoonEnd?: string }>
  timeSlots?: Array<{ id?: number; dayOfWeek: string; startTime: string; endTime: string; orderIndex?: number; dayPeriod?: 'MORNING' | 'AFTERNOON' }>
  totalSlotsPerWeek?: number
  isReadyForGeneration?: boolean
  slotDurationMinutes?: number
}

// Keep legacy alias
export type SchoolConfig = SchoolConfigResponse

// ─────────────────────────────────────────────────────────────────────────────

export const organisationApi = {
  // School years
  schoolYears: {
    list: () =>
      apiClient.get<SchoolYear[]>('/api/organisation/school-years/list').then((r) => r.data),
    create: (dto: SchoolYearRequest) =>
      apiClient.post<SchoolYear>('/api/organisation/school-years', dto).then((r) => r.data),
    update: (id: number, dto: SchoolYearRequest) =>
      apiClient.put<SchoolYear>(`/api/organisation/school-years/${id}`, dto).then((r) => r.data),
    toggleStatus: (id: number, active: boolean) =>
      apiClient.patch<SchoolYear>(`/api/organisation/school-years/${id}/status`, null, { params: { active } }).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/school-years/${id}`),
  },

  // Levels
  levels: {
    list: () =>
      apiClient.get<Level[]>('/api/organisation/levels/actifs').then((r) => r.data),
    create: (dto: LevelRequest) =>
      apiClient.post<Level>('/api/organisation/levels', dto).then((r) => r.data),
    update: (id: number, dto: LevelRequest) =>
      apiClient.put<Level>(`/api/organisation/levels/${id}`, dto).then((r) => r.data),
    toggleStatus: (id: number, estActif: boolean) =>
      apiClient.patch<Level>(`/api/organisation/levels/${id}/status`, null, { params: { estActif } }).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/levels/${id}`),
  },

  // Subjects
  subjects: {
    list: () =>
      apiClient.get<Subject[]>('/api/organisation/subjects/enseignees').then((r) => r.data),
    create: (dto: SubjectRequest) =>
      apiClient.post<Subject>('/api/organisation/subjects', dto).then((r) => r.data),
    update: (id: number, dto: SubjectRequest) =>
      apiClient.put<Subject>(`/api/organisation/subjects/${id}`, dto).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/subjects/${id}`),
    subjectLevels: {
      byLevel: (levelId: number) =>
        apiClient.get<SubjectLevel[]>(`/api/organisation/subject-levels/by-level/${levelId}`).then((r) => r.data),
      create: (dto: SubjectLevelRequest) =>
        apiClient.post<SubjectLevel>('/api/organisation/subject-levels', dto).then((r) => r.data),
      update: (id: number, dto: SubjectLevelRequest) =>
        apiClient.put<SubjectLevel>(`/api/organisation/subject-levels/${id}`, dto).then((r) => r.data),
      delete: (id: number) => apiClient.delete(`/api/organisation/subject-levels/${id}`),
    },
  },

  // School Patterns (programme de l'école — copié + adapté depuis le national)
  patterns: {
    getById: (id: number) =>
      apiClient.get<SchoolPattern>(`/api/organisation/patterns/${id}`).then((r) => r.data),
    getWithDetails: (id: number) =>
      apiClient.get<SchoolPattern>(`/api/organisation/patterns/${id}/with-details`).then((r) => r.data),
    bySubjectLevel: (subjectLevelId: number) =>
      apiClient.get<SchoolPattern[]>(`/api/organisation/patterns/by-subject-level/${subjectLevelId}`).then((r) => r.data),
    create: (dto: SchoolPatternRequest) =>
      apiClient.post<SchoolPattern>('/api/organisation/patterns', dto).then((r) => r.data),
    update: (id: number, dto: SchoolPatternRequest) =>
      apiClient.put<SchoolPattern>(`/api/organisation/patterns/${id}`, dto).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/patterns/${id}`),
    toggleStatus: (id: number, active: boolean) =>
      apiClient.patch<SchoolPattern>(`/api/organisation/patterns/${id}/status`, null, { params: { active } }).then((r) => r.data),
    inconsistants: () =>
      apiClient.get<SchoolPattern[]>('/api/organisation/patterns/inconsistants').then((r) => r.data),
  },

  // Rooms
  rooms: {
    list: () =>
      apiClient.get<Room[]>('/api/organisation/rooms/list').then((r) => r.data),
    create: (dto: RoomRequest) =>
      apiClient.post<Room>('/api/organisation/rooms', dto).then((r) => r.data),
    update: (id: number, dto: RoomRequest) =>
      apiClient.put<Room>(`/api/organisation/rooms/${id}`, dto).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/rooms/${id}`),
    importFile: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return apiClient.post<RoomImportResult>('/api/organisation/rooms/import', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }).then((r) => r.data)
    },
    downloadTemplateCsv: () =>
      apiClient.get('/api/organisation/rooms/import/template', { responseType: 'blob' }).then((r) => r.data as Blob),
    downloadTemplateExcel: () =>
      apiClient.get('/api/organisation/rooms/import/template/excel', { responseType: 'blob' }).then((r) => r.data as Blob),
  },

  // Classes
  classes: {
    list: () =>
      apiClient.get<SchoolClass[]>('/api/organisation/classes').then((r) => r.data),
    byYear: (schoolYearId: number) =>
      apiClient.get<SchoolClass[]>(`/api/organisation/classes/by-year/${schoolYearId}`).then((r) => r.data),
    byLevel: (levelId: number) =>
      apiClient.get<SchoolClass[]>(`/api/organisation/classes/by-level/${levelId}`).then((r) => r.data),
    byLevelAndYear: (levelId: number, schoolYearId: number) =>
      apiClient.get<SchoolClass[]>(`/api/organisation/classes/by-level/${levelId}/year/${schoolYearId}`).then((r) => r.data),
    create: (dto: ClassRequest) =>
      apiClient.post<SchoolClass>('/api/organisation/classes', dto).then((r) => r.data),
    update: (id: number, dto: ClassRequest) =>
      apiClient.put<SchoolClass>(`/api/organisation/classes/${id}`, dto).then((r) => r.data),
    toggleStatus: (id: number, estActif: boolean) =>
      apiClient.patch<SchoolClass>(`/api/organisation/classes/${id}/status`, null, { params: { estActif } }).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/classes/${id}`),
    bulkCreate: (dto: {
      schoolYearId: number
      defaultSize?: number
      levels: Array<{ levelCode: string; count: number; prefix: string; specialite?: string }>
    }) =>
      apiClient.post<BulkClassResult>('/api/organisation/classes/bulk-create', dto).then((r) => r.data),
  },

  // Teachers
  teachers: {
    /** Fiche (avec affectations) de l'enseignant connecté — espace enseignant. */
    me: () =>
      apiClient.get<Teacher>('/api/organisation/teachers/me').then((r) => r.data),
    list: () =>
      apiClient.get<Teacher[]>('/api/organisation/teachers/list').then((r) => r.data),
    getActifs: () =>
      apiClient.get<Teacher[]>('/api/organisation/teachers/actifs').then((r) => r.data),
    getWorkload: () =>
      apiClient.get<Teacher[]>('/api/organisation/teachers/workload').then((r) => r.data),
    get: (id: number) =>
      apiClient.get<Teacher>(`/api/organisation/teachers/${id}`).then((r) => r.data),
    getWithAssignments: (id: number) =>
      apiClient.get<Teacher>(`/api/organisation/teachers/${id}/assignments`).then((r) => r.data),
    create: (dto: TeacherRequest) =>
      apiClient.post<Teacher>('/api/organisation/teachers', dto).then((r) => r.data),
    update: (id: number, dto: TeacherRequest) =>
      apiClient.put<Teacher>(`/api/organisation/teachers/${id}`, dto).then((r) => r.data),
    deactivate: (id: number) =>
      apiClient.patch<Teacher>(`/api/organisation/teachers/${id}/deactivate`).then((r) => r.data),
    reactivate: (id: number) =>
      apiClient.patch<Teacher>(`/api/organisation/teachers/${id}/reactivate`).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/teachers/${id}`),
    importFile: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return apiClient.post<TeacherImportResult>('/api/organisation/teachers/import', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }).then((r) => r.data)
    },
    downloadTemplateCsv: () =>
      apiClient.get('/api/organisation/teachers/import/template', { responseType: 'blob' }).then((r) => r.data as Blob),
    downloadTemplateExcel: () =>
      apiClient.get('/api/organisation/teachers/import/template/excel', { responseType: 'blob' }).then((r) => r.data as Blob),
  },

  // Students (Eleves)
  eleves: {
    list: () =>
      apiClient.get<{ content: Eleve[] }>('/api/organisation/eleves', { params: { size: 1000 } }).then((r) => r.data.content),
    byClass: (classeId: number) =>
      apiClient.get<Eleve[]>(`/api/organisation/eleves/classe/${classeId}/actifs`).then((r) => r.data),
    create: (dto: EleveRequest) =>
      apiClient.post<Eleve>('/api/organisation/eleves', dto).then((r) => r.data),
    update: (id: number, dto: EleveRequest) =>
      apiClient.put<Eleve>(`/api/organisation/eleves/${id}`, dto).then((r) => r.data),
    toggleStatut: (id: number, estActif: boolean) =>
      apiClient.patch<Eleve>(`/api/organisation/eleves/${id}/statut`, null, { params: { estActif } }).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/eleves/${id}`),
    importFile: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return apiClient.post<EleveImportResult>('/api/organisation/eleves/import', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }).then((r) => r.data)
    },
    downloadTemplateCsv: () =>
      apiClient.get('/api/organisation/eleves/import/template', { responseType: 'blob' }).then((r) => r.data as Blob),
    downloadTemplateExcel: () =>
      apiClient.get('/api/organisation/eleves/import/template/excel', { responseType: 'blob' }).then((r) => r.data as Blob),
  },

  // Teaching Assignments
  teachingAssignments: {
    list: () =>
      apiClient.get<{ content: TeachingAssignmentFull[] }>('/api/organisation/teaching-assignments', { params: { size: 1000 } }).then((r) => r.data.content),
    byYear: (schoolYearId: number) =>
      apiClient.get<TeachingAssignmentFull[]>(`/api/organisation/teaching-assignments/by-year/${schoolYearId}`).then((r) => r.data),
    byClass: (classId: number) =>
      apiClient.get<TeachingAssignmentFull[]>(`/api/organisation/teaching-assignments/by-class/${classId}`).then((r) => r.data),
    byTeacher: (teacherId: number) =>
      apiClient.get<TeachingAssignmentFull[]>(`/api/organisation/teaching-assignments/by-teacher/${teacherId}`).then((r) => r.data),
    missing: (schoolYearId: number) =>
      apiClient.get<MissingAssignmentEntry[]>('/api/organisation/teaching-assignments/missing', { params: { schoolYearId } }).then((r) => r.data),
    create: (dto: TeachingAssignmentRequest) =>
      apiClient.post<TeachingAssignmentFull>('/api/organisation/teaching-assignments', dto).then((r) => r.data),
    update: (id: number, dto: TeachingAssignmentRequest) =>
      apiClient.put<TeachingAssignmentFull>(`/api/organisation/teaching-assignments/${id}`, dto).then((r) => r.data),
    toggleStatus: (id: number, isActive: boolean) =>
      apiClient.patch<TeachingAssignmentFull>(`/api/organisation/teaching-assignments/${id}/status`, null, { params: { isActive } }).then((r) => r.data),
    delete: (id: number) => apiClient.delete(`/api/organisation/teaching-assignments/${id}`),
  },

  // School Users
  users: {
    list: (role?: UserRole) =>
      apiClient.get<SchoolUser[]>('/api/admin/users', { params: role ? { role } : undefined }).then((r) => r.data),
    create: (dto: CreateSchoolUserRequest) =>
      apiClient.post<SchoolUser>('/api/admin/users', dto).then((r) => r.data),
    update: (id: number, dto: Partial<CreateSchoolUserRequest>) =>
      apiClient.put<SchoolUser>(`/api/admin/users/${id}`, dto).then((r) => r.data),
    deactivate: (id: number) =>
      apiClient.put<SchoolUser>(`/api/admin/users/${id}/deactivate`).then((r) => r.data),
    reactivate: (id: number) =>
      apiClient.put<SchoolUser>(`/api/admin/users/${id}/reactivate`).then((r) => r.data),
  },

  // National patterns
  nationalPatterns: {
    list: (countryCode = 'TN') =>
      apiClient.get<NationalPattern[]>('/api/v1/national-patterns', { params: { countryCode } }).then((r) => r.data),
    apply: (id: number, schoolYearId?: number) =>
      apiClient.post<NationalPatternApplyResult>(
        `/api/v1/national-patterns/${id}/apply`,
        null,
        { params: schoolYearId ? { schoolYearId } : undefined }
      ).then((r) => r.data),
    applyByRequest: (dto: { country: string; levels: string[]; schoolYearId?: number }) =>
      apiClient.post<{ applied: number; skipped: number }>('/api/organisation/school-config/apply-national', dto).then((r) => r.data),
  },

  // School config
  config: {
    get: () =>
      apiClient.get<SchoolConfigResponse>('/api/organisation/school-config').then((r) => r.data),
    save: (dto: SchoolConfigRequest) =>
      apiClient.post<SchoolConfigResponse>('/api/organisation/school-config', dto).then((r) => r.data),
    toggleDay: (day: string, active: boolean) =>
      apiClient.patch<SchoolConfigResponse>(`/api/organisation/school-config/days/${day}/toggle`, null, { params: { active } }).then((r) => r.data),
  },
}
