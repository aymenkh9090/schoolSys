import apiClient from './client'

// ── Enums ──────────────────────────────────────────────────────────────────
export type SolverStatus = 'PENDING' | 'RUNNING' | 'SOLVED' | 'INFEASIBLE' | 'FAILED' | 'CANCELLED'
export type ConstraintCategory = 'STUDENT' | 'TEACHER' | 'SUBJECT' | 'ROOM' | 'PEDAGOGICAL'
export type ConstraintType = 'HARD' | 'MEDIUM' | 'SOFT'
export type ImportanceLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type GeneratedTimetableStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

// ── Constraint ──────────────────────────────────────────────────────────────
export interface ConstraintDefinition {
  idConstraintDefinition: number
  code: string
  name: string
  description: string
  category: ConstraintCategory
  type: ConstraintType
  defaultImportance: ImportanceLevel
  defaultEnabled: boolean
  parameterSchema?: string
}

export interface ConstraintSetting {
  idConstraintSetting: number
  profileId: number
  definitionId: number
  constraintCode: string
  constraintName: string
  description?: string
  category: ConstraintCategory
  type: ConstraintType
  enabled: boolean
  importance: ImportanceLevel
  weight?: number
  parametersJson?: string
}

export interface ConstraintProfile {
  idConstraintProfile: number
  name: string
  academicYearId?: number
  active: boolean
  settings: ConstraintSetting[]
}

// ── DSL des contraintes personnalisées ──────────────────────────────────────
// Miroir exact de `constraints/dsl/model/ConstraintDsl.java`. Le contrat est
// partagé mot pour mot avec le backend et avec l'assistant IA : c'est ce qui
// permet d'afficher une règle proposée par le modèle, de l'éditer à la main,
// puis de l'enregistrer, sans jamais la retraduire d'une forme à l'autre.
export type DslScope = 'LESSON' | 'TEACHER_DAY' | 'CLASS_DAY' | 'ROOM_DAY' | 'TEACHER_WEEK' | 'CLASS_WEEK'
export type DslSeverity = 'HARD' | 'MEDIUM' | 'SOFT'
export type DslAction = 'PENALIZE' | 'REWARD'
export type DslLogic = 'AND' | 'OR'
export type DslAggregateMetric = 'TOTAL_HOURS' | 'TOTAL_SLOTS' | 'LESSON_COUNT'
export type ConstraintSource = 'MANUAL' | 'AI_TRANSLATED' | 'AI_SUGGESTED'

export interface DslCondition {
  field: string
  operator: string
  /** Opérateurs d'arité 1 (EQUALS, GREATER_THAN…). */
  value?: string
  /** Opérateurs multi-valeurs : IN, NOT_IN, BETWEEN. */
  values?: string[]
}

export interface DslAggregate {
  metric: DslAggregateMetric
  operator: string
  value: number
}

export interface ConstraintDsl {
  version?: number
  scope: DslScope
  logic?: DslLogic
  conditions: DslCondition[]
  /** Obligatoire sur une portée agrégée, interdit sur LESSON. */
  aggregate?: DslAggregate
  action: DslAction
  severity: DslSeverity
  weight: number
}

// ── Catalogue publié par le backend ─────────────────────────────────────────
// Source unique des listes déroulantes : si un champ disparaît côté serveur,
// il disparaît du formulaire au rechargement, sans modification du frontend.
export interface DslFieldDescriptor {
  name: string
  type: 'STRING' | 'NUMBER' | 'TIME' | 'DAY' | 'ENUM' | 'BOOLEAN'
  label: string
  allowedValues: string[]
  example?: string
  /** Opérateurs déjà filtrés par le backend selon le type du champ. */
  operators: string[]
}

export interface DslOperatorDescriptor {
  name: string
  label: string
  supportedTypes: string[]
  /** Nombre de valeurs attendues ; -1 pour une liste de taille libre. */
  arity: number
}

export interface DslEnumDescriptor {
  name: string
  label: string
}

export interface DslSchema {
  fields: DslFieldDescriptor[]
  operators: DslOperatorDescriptor[]
  scopes: DslEnumDescriptor[]
  severities: DslEnumDescriptor[]
  actions: DslEnumDescriptor[]
  aggregateMetrics: DslEnumDescriptor[]
  example: ConstraintDsl
}

// ── Règle personnalisée ─────────────────────────────────────────────────────
export interface CustomConstraint {
  idCustomConstraint: number
  constraintProfileId: number
  code: string
  name: string
  description?: string
  dsl: ConstraintDsl
  scope: DslScope
  severity: DslSeverity
  weight: number
  enabled: boolean
  source: ConstraintSource
  naturalLanguageRequest?: string
  /** Résumé français produit par le compilateur Java — ce que le moteur applique. */
  summary?: string
}

export interface CustomConstraintRequest {
  constraintProfileId: number
  code: string
  name: string
  description?: string
  dsl: ConstraintDsl
  enabled?: boolean
  source?: ConstraintSource
  naturalLanguageRequest?: string
}

// ── Analyse d'une règle candidate ───────────────────────────────────────────
export interface DslConflict {
  type: 'NO_ADMISSIBLE_SLOT' | 'WORKLOAD_EXCEEDS_LIMIT' | string
  subject: string
  detail: string
}

export interface DslAnalysis {
  valid: boolean
  errors: string[]
  warnings: string[]
  summary?: string
  matchedLessons?: number
  totalLessons?: number
  examples?: string[]
  /** False quand la règle rend le placement impossible avec les données actuelles. */
  feasible: boolean
  conflicts: DslConflict[]
  verdict?: string
}

// ── Suggestions issues du dernier planning ──────────────────────────────────
export interface ConstraintSuggestion {
  code: string
  title: string
  observation: string
  rationale: string
  evidence: string[]
  occurrences: number
  /** Null quand la suggestion pointe vers une contrainte du catalogue à activer. */
  dsl?: ConstraintDsl | null
}

export interface ConstraintSuggestions {
  jobId: number
  suggestions: ConstraintSuggestion[]
}

// ── Job / Solver ────────────────────────────────────────────────────────────
export interface TimetableJob {
  jobId: number
  schoolYearId: number
  constraintProfileId?: number
  status: SolverStatus
  scoreAchieved?: string
  startedAt?: string
  finishedAt?: string
  errorMessage?: string
  /**
   * Ce que la validation métier reproche à cet emploi du temps, en clair.
   * Distinct de `errorMessage`, qui signale un plantage du solveur : ici le
   * planning existe et se consulte, c'est sa conformité qui est en cause.
   */
  validationReport?: string | null
}

// ── Score explanation (panneau "pourquoi ce planning ?") ───────────────────
export interface ConstraintViolation {
  constraintName: string
  label: string
  score: string
  count: number
  examples: string[]
  suggestion?: string
}

/**
 * Un constat de la validation métier — le contrôle indépendant du solveur.
 *
 * Les `ConstraintViolation` disent ce que *les contraintes activées* reprochent
 * au planning ; ceci dit ce qu'il a de faux quoi qu'on ait activé : une séance
 * perdue, un volume horaire amputé, deux classes dans la même salle.
 */
export interface BusinessFinding {
  severity: 'BLOQUANT' | 'AVERTISSEMENT'
  code: string
  scope?: string
  message: string
}

/**
 * Verdict du contrôle qui précède la génération.
 *
 * Il répond en une seconde à « les données de cette année permettent-elles
 * d'engendrer un emploi du temps ? ». Rien n'est placé pour y répondre : chaque
 * constat est un dénombrement — une matière sans enseignant, un service qui ne
 * tient pas dans une semaine — et non le résultat d'une recherche. Passer le
 * contrôle n'est donc pas une promesse de faisabilité.
 */
export interface PreflightReport {
  ready: boolean
  blockingCount: number
  warningCount: number
  findings: BusinessFinding[]
}

export interface ScoreExplanation {
  jobId: number
  score: string
  feasible: boolean
  hardViolations: ConstraintViolation[]
  mediumViolations: ConstraintViolation[]
  softViolations: ConstraintViolation[]
  /** Absent des réponses produites avant l'ajout de la validation métier. */
  businessValidation?: BusinessFinding[]
  businessValid?: boolean
}

export interface GeneratedTimetable {
  id: number
  jobId: number
  academicYearId: number
  constraintProfileId?: number
  scoreAchieved?: string
  feasible: boolean
  totalSessions: number
  hardViolations: number
  mediumViolations: number
  status: GeneratedTimetableStatus
  publishedAt?: string
}

// ── Timetable views ─────────────────────────────────────────────────────────
// Forme commune aux 3 vues (classe/enseignant/salle) : chacune omet le champ
// correspondant à sa propre dimension (ex: la vue classe n'a pas classCode).
export interface SessionView {
  id: number
  startTime: string
  endTime: string
  duration: string
  subjectCode: string
  subjectName: string
  classCode?: string
  teacherCode?: string
  teacherName?: string
  roomCode?: string
  roomType?: string
  sessionType: string
  groupIndex: number
  groupLabel: string
}

export interface MoveSessionRequest {
  day: string
  startTime: string
  roomCode?: string
  roomType?: string
}

export interface DaySchedule {
  day: string
  dayLabel: string
  sessions: SessionView[]
}

export interface ClassTimetableView {
  jobId: number
  classCode: string
  status: SolverStatus
  scoreAchieved?: string
  schedule: DaySchedule[]
}

export interface TeacherTimetableView {
  jobId: number
  teacherCode: string
  teacherName: string
  status: SolverStatus
  scoreAchieved?: string
  schedule: DaySchedule[]
}

export interface RoomTimetableView {
  jobId: number
  roomCode: string
  roomType?: string
  status: SolverStatus
  scoreAchieved?: string
  schedule: DaySchedule[]
}

// ── API ─────────────────────────────────────────────────────────────────────
export const planningApi = {
  // Constraints
  constraints: {
    definitions: () =>
      apiClient.get<ConstraintDefinition[]>('/api/planning/constraints/definitions').then((r) => r.data),

    profiles: {
      list: () =>
        apiClient.get<ConstraintProfile[]>('/api/planning/constraints/profiles').then((r) => r.data),
      get: (id: number) =>
        apiClient.get<ConstraintProfile>(`/api/planning/constraints/profiles/${id}`).then((r) => r.data),
      create: (dto: { name: string; academicYearId?: number; active?: boolean }) =>
        apiClient.post<ConstraintProfile>('/api/planning/constraints/profiles', dto).then((r) => r.data),
      /** `schoolYearId` : c'est le nom attendu par CreateDefaultProfileRequest — un `academicYearId` est ignoré et le profil naît sans année. */
      createDefault: (dto: { name: string; schoolYearId?: number }) =>
        apiClient.post<ConstraintProfile>('/api/planning/constraints/profiles/create-default', dto).then((r) => r.data),
      /** Rend ce profil le seul actif de son année scolaire (le solveur s'en sert par défaut). */
      activate: (id: number) =>
        apiClient.put<ConstraintProfile>(`/api/planning/constraints/profiles/${id}/activate`).then((r) => r.data),
      delete: (id: number) =>
        apiClient.delete(`/api/planning/constraints/profiles/${id}`),
      addSetting: (profileId: number, dto: { constraintDefinitionId: number; enabled: boolean; importance: ImportanceLevel; weight?: number; parametersJson?: string }) =>
        apiClient.post<ConstraintSetting>(`/api/planning/constraints/profiles/${profileId}/settings`, dto).then((r) => r.data),
      updateSetting: (settingId: number, dto: { enabled?: boolean; importance?: ImportanceLevel; weight?: number; parametersJson?: string }) =>
        apiClient.put<ConstraintSetting>(`/api/planning/constraints/settings/${settingId}`, dto).then((r) => r.data),
      deleteSetting: (settingId: number) =>
        apiClient.delete(`/api/planning/constraints/settings/${settingId}`),
    },

    /** Contraintes réellement personnalisées, exprimées en DSL. */
    custom: {
      /** Catalogue des champs/opérateurs — alimente les listes du formulaire. */
      schema: () =>
        apiClient.get<DslSchema>('/api/planning/constraints/dsl/schema').then((r) => r.data),

      list: (profileId?: number) =>
        apiClient.get<CustomConstraint[]>('/api/planning/constraints/custom', { params: { profileId } }).then((r) => r.data),

      get: (id: number) =>
        apiClient.get<CustomConstraint>(`/api/planning/constraints/custom/${id}`).then((r) => r.data),

      /**
       * Valide la règle, mesure son impact et cherche les conflits — SANS rien
       * enregistrer. Appelé avant chaque ouverture de l'écran de confirmation :
       * un utilisateur ne doit jamais découvrir qu'une règle est irréalisable
       * après trente secondes de génération.
       */
      analyze: (dto: { dsl: ConstraintDsl; schoolYearId?: number; constraintProfileId?: number }) =>
        apiClient.post<DslAnalysis>('/api/planning/constraints/custom/analyze', dto).then((r) => r.data),

      create: (dto: CustomConstraintRequest) =>
        apiClient.post<CustomConstraint>('/api/planning/constraints/custom', dto).then((r) => r.data),

      update: (id: number, dto: Partial<CustomConstraintRequest>) =>
        apiClient.put<CustomConstraint>(`/api/planning/constraints/custom/${id}`, dto).then((r) => r.data),

      setEnabled: (id: number, enabled: boolean) =>
        apiClient.patch<CustomConstraint>(`/api/planning/constraints/custom/${id}/enabled`, null, { params: { enabled } }).then((r) => r.data),

      delete: (id: number) =>
        apiClient.delete(`/api/planning/constraints/custom/${id}`).then(() => undefined),

      /** Propositions statistiques déduites du dernier emploi du temps généré. */
      suggestions: (params?: { schoolYearId?: number; jobId?: number }) =>
        apiClient.get<ConstraintSuggestions>('/api/planning/constraints/custom/suggestions', { params }).then((r) => r.data),
    },
  },

  // Solver
  timetable: {
    /** Ce que les données disent avant qu'on lance quoi que ce soit. */
    preflight: (schoolYearId: number, constraintProfileId?: number) =>
      apiClient.get<PreflightReport>('/api/planning/timetable/preflight', {
        params: { schoolYearId, constraintProfileId },
      }).then((r) => r.data),

    generate: (dto: { schoolYearId: number; constraintProfileId?: number }) =>
      apiClient.post<TimetableJob>('/api/planning/timetable/generate', dto).then((r) => r.data),

    jobs: {
      list: (academicYearId?: number) =>
        apiClient.get<TimetableJob[]>('/api/planning/timetable/jobs', { params: { academicYearId } }).then((r) => r.data),
      get: (jobId: number) =>
        apiClient.get<TimetableJob>(`/api/planning/timetable/jobs/${jobId}`).then((r) => r.data),
      cancel: (jobId: number) =>
        apiClient.post<TimetableJob>(`/api/planning/timetable/jobs/${jobId}/cancel`).then((r) => r.data),
      delete: (jobId: number) =>
        apiClient.delete(`/api/planning/timetable/jobs/${jobId}`).then(() => undefined),
      result: (jobId: number) =>
        apiClient.get<GeneratedTimetable>(`/api/planning/timetable/jobs/${jobId}/result`).then((r) => r.data),
      scoreExplanation: (jobId: number) =>
        apiClient.get<ScoreExplanation>(`/api/planning/timetable/jobs/${jobId}/score-explanation`).then((r) => r.data),
    },

    generated: {
      list: (academicYearId?: number) =>
        apiClient.get<GeneratedTimetable[]>('/api/planning/timetable/generated', { params: { academicYearId } }).then((r) => r.data),
      publish: (id: number) =>
        apiClient.post<GeneratedTimetable>(`/api/planning/timetable/generated/${id}/publish`).then((r) => r.data),
    },

    views: {
      byClass: (jobId: number, classCode: string) =>
        apiClient.get<ClassTimetableView>(`/api/planning/timetable/jobs/${jobId}/view/class/${classCode}`).then((r) => r.data),
      byTeacher: (jobId: number, teacherCode: string) =>
        apiClient.get<TeacherTimetableView>(`/api/planning/timetable/jobs/${jobId}/view/teacher/${teacherCode}`).then((r) => r.data),
      byRoom: (jobId: number, roomCode: string) =>
        apiClient.get<RoomTimetableView>(`/api/planning/timetable/jobs/${jobId}/view/room/${roomCode}`).then((r) => r.data),
      allClasses: (jobId: number) =>
        apiClient.get<ClassTimetableView[]>(`/api/planning/timetable/jobs/${jobId}/view/classes`).then((r) => r.data),
      allTeachers: (jobId: number) =>
        apiClient.get<TeacherTimetableView[]>(`/api/planning/timetable/jobs/${jobId}/view/teachers`).then((r) => r.data),
      allRooms: (jobId: number) =>
        apiClient.get<RoomTimetableView[]>(`/api/planning/timetable/jobs/${jobId}/view/rooms`).then((r) => r.data),
    },

    sessions: {
      move: (jobId: number, sessionId: number, dto: MoveSessionRequest) =>
        apiClient.patch(`/api/planning/timetable/jobs/${jobId}/sessions/${sessionId}`, dto).then(() => undefined),
    },
  },
}
