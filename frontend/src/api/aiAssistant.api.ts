/**
 * Client du microservice Python d'observabilité (AI Assistant).
 *
 * URL de base différente du backend Java, mais MÊME jeton Keycloak :
 * le service Python valide exactement le même JWT.
 */

import axios from 'axios'
import * as authStore from '@/auth/authStore'
import type { ConstraintDsl, DslConflict } from './planning.api'

const AI_BASE_URL = import.meta.env.VITE_AI_ASSISTANT_URL ?? 'http://localhost:8000'

const aiClient = axios.create({ baseURL: AI_BASE_URL })

// Même interceptor que le client principal.
// Pas d'en-tête X-Tenant-ID ici : l'observabilité est cross-tenant par nature.
aiClient.interceptors.request.use(async (config) => {
  const { accessToken } = authStore.getState()
  if (accessToken) {
    try {
      await authStore.updateToken(30)
    } catch {
      window.location.href = '/login'
      return config
    }
    config.headers.Authorization = `Bearer ${authStore.getState().accessToken}`
  }
  return config
})

/**
 * ⚠️ Champs en snake_case : FastAPI/Pydantic sérialise tel quel, sans
 * conversion camelCase. Les renommer ici donnerait des `undefined` silencieux.
 */
export interface HealthSnapshot {
  status: 'HEALTHY' | 'WARNING' | 'CRITICAL' | 'UNKNOWN'
  app_status: string
  db_status: string
  heap_used_mb: number | null
  heap_max_mb: number | null
  heap_percent: number | null
  cpu_percent: number | null
  latency_p95_ms: number | null
  error_rate_percent: number | null
  requests_per_second: number | null
  db_connections_active: number | null
  db_connections_pending: number | null
  issues: string[]
}

export interface ChatResponse {
  answer: string
  tools_used: string[]
  duration_ms: number
}

export type TimeWindow = '5m' | '1h' | '24h'

export const aiAssistantApi = {
  getHealth: (window: TimeWindow = '5m') =>
    aiClient
      .get<HealthSnapshot>('/api/monitoring/health', { params: { window } })
      .then((r) => r.data),

  getSlowestEndpoints: (window: TimeWindow = '1h') =>
    aiClient
      .get<Record<string, number>>('/api/monitoring/slowest-endpoints', {
        params: { window },
      })
      .then((r) => r.data),

  // Le LLM tourne sur CPU : compter 3 à 30 s par réponse. On borne à 2 min
  // pour qu'une génération partie en vrille finisse par rendre la main.
  ask: (message: string) =>
    aiClient
      .post<ChatResponse>('/api/assistant/chat', { message }, { timeout: 120_000 })
      .then((r) => r.data),
}

// ── Assistant Planning ──────────────────────────────────────────────────────
//
// Deux appels, volontairement séparés : `propose` traduit une phrase en règle
// et ne fait qu'AFFICHER le résultat ; `confirm` enregistre, et n'est déclenché
// que par un clic explicite sur la règle telle qu'elle a été montrée. Aucun
// écran ne les enchaîne automatiquement — c'est ce qui garantit qu'une règle
// n'entre jamais en base sans avoir été relue.

export interface ConstraintProposal {
  valid: boolean
  dsl: ConstraintDsl | null
  /** Résumé produit par le compilateur Java, pas par le modèle. */
  summary: string | null
  verdict: string | null
  message: string
  errors: string[]
  warnings: string[]
  feasible: boolean
  conflicts: DslConflict[]
  matched_lessons: number | null
  total_lessons: number | null
  examples: string[]
  attempts: number
  duration_ms: number
  requires_confirmation: boolean
}

export interface ConstraintConfirmPayload {
  constraint_profile_id: number
  code: string
  name: string
  description?: string
  dsl: ConstraintDsl
  natural_language_request?: string
}

export const planningAssistantApi = {
  /** Question en langage naturel sur l'emploi du temps. Lecture seule. */
  ask: (message: string, params?: { schoolYearId?: number; profileId?: number }) =>
    aiClient
      .post<ChatResponse>(
        '/api/planning/assistant/chat',
        {
          message,
          school_year_id: params?.schoolYearId,
          profile_id: params?.profileId,
        },
        { timeout: 120_000 }
      )
      .then((r) => r.data),

  /**
   * Traduit une demande en règle DSL validée par le backend. N'enregistre rien.
   *
   * Le modèle peut avoir besoin d'une passe de correction : compter jusqu'à
   * deux générations, donc un délai plus long qu'une simple réponse de chat.
   */
  propose: (message: string, params?: { schoolYearId?: number; profileId?: number }) =>
    aiClient
      .post<ConstraintProposal>(
        '/api/planning/assistant/constraints/propose',
        {
          message,
          school_year_id: params?.schoolYearId,
          profile_id: params?.profileId,
        },
        { timeout: 180_000 }
      )
      .then((r) => r.data),

  /** Enregistre la règle, après confirmation explicite de l'utilisateur. */
  confirm: (payload: ConstraintConfirmPayload) =>
    aiClient
      .post('/api/planning/assistant/constraints/confirm', payload)
      .then((r) => r.data),
}

// ── Assistant Cahier de séance ──────────────────────────────────────────────
//
// Une seule route pour deux usages : un enseignant y interroge SES séances, un
// directeur celles de son établissement. Aucun paramètre d'identité n'est
// envoyé, et ce n'est pas un oubli — le périmètre est décidé par le backend à
// partir du compte porté par le jeton. Ajouter ici un `enseignantId` rouvrirait
// exactement la question que cette conception ferme.

export const cahierAssistantApi = {
  /** Question en langage naturel sur les cahiers de séance. Lecture seule. */
  ask: (message: string) =>
    aiClient
      .post<ChatResponse>('/api/cahier/assistant/chat', { message }, { timeout: 120_000 })
      .then((r) => r.data),
}
