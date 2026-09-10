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

/** Une séance que l'assistant désigne — de quoi la retrouver dans la grille. */
export interface SeanceDesignee {
  session_id: number | null
  lesson_id: number | null
  subject_name: string | null
  class_name: string | null
  teacher_name: string | null
  room_code: string | null
  /** Nom du jour côté Java, ex. "MONDAY". */
  day: string | null
  start_time: string | null
  /** 0 = classe entière, 1 = demi-groupe A, 2 = demi-groupe B. */
  group_index: number
}

/**
 * Un déplacement possible, calculé par le solveur.
 *
 * Le créneau d'arrivée a été vérifié libre pour l'enseignant, pour la classe, et
 * une salle du bon type y est disponible. `text` porte la phrase construite côté
 * Java : l'afficher telle quelle est délibéré — la reformuler ici reviendrait à
 * réécrire une garantie que le front n'est pas en mesure de donner.
 */
export interface DeplacementPropose {
  session_id: number | null
  to_day: string | null
  to_start_time: string | null
  to_slot_id: number | null
  to_room_code: string | null
  text: string
}

/**
 * Un conflit désigné : la règle enfreinte, les séances en cause, le remède.
 *
 * **Rempli par le code, jamais par le modèle.** Le service Python le construit à
 * partir de la réponse du backend, en marge de la boucle d'outils : le modèle ne
 * voit que du texte déjà interprété. C'est ce qui permet d'afficher ces
 * identifiants sans craindre qu'ils aient été inventés.
 */
export interface ConflitDesigne {
  constraint: string
  severity: string
  label: string
  sessions: SeanceDesignee[]
  /** Absent quand aucun créneau ne convient — fréquent sur un planning saturé. */
  relocation: DeplacementPropose | null
}

export interface PlanningChatResponse extends ChatResponse {
  conflicts: ConflitDesigne[]
}

export type TimeWindow = '5m' | '1h' | '24h'

/** Les clés que `trends` peut porter — celles de HISTORY_METRICS côté Python. */
export type CleTendance = 'memory' | 'cpu' | 'latency' | 'errors' | 'throughput'

/**
 * La forme récente des mesures, pour la vignette de tendance des tuiles.
 *
 * Les points sont dans l'unité d'affichage de la tuile — pourcentage,
 * millisecondes — et non dans celle de Prometheus : la courbe et le chiffre
 * posé au-dessus d'elle doivent parler de la même chose.
 *
 * Une mesure sans donnée est **absente** de `trends`, jamais présente avec un
 * tableau vide : l'écran n'affiche alors aucune courbe, au lieu d'une ligne
 * plate qui se lirait comme une mesure stable.
 */
export interface MetricTrends {
  window: TimeWindow
  trends: Partial<Record<CleTendance, number[]>>
}

/**
 * Le verdict d'une prévision, calculé côté Python — l'écran ne fait que le lire.
 *
 * - `hausse`      : seuil d'alerte atteint avant l'horizon, durées renseignées ;
 * - `stable`      : aucun seuil atteint avant l'horizon ;
 * - `baisse`      : la ressource se libère ;
 * - `incertain`   : la droite n'explique pas la série — aucune échéance ;
 * - `insuffisant` : trop peu d'historique pour tracer une droite.
 */
export type VerdictPrevision = 'insuffisant' | 'incertain' | 'hausse' | 'stable' | 'baisse'

/**
 * Où va une ressource, par régression linéaire sur l'heure écoulée.
 *
 * Les durées ne sont JAMAIS renseignées hors `hausse`. `0` = seuil déjà franchi ;
 * `null` = pas atteint avant l'horizon.
 */
export interface ResourceForecast {
  verdict: VerdictPrevision
  /** Ce qui a été régressé — pour la mémoire, le plancher de la heap, pas la heap brute. */
  series: string
  unit: string
  warning_threshold: number
  critical_threshold: number
  points: number
  r2: number | null
  slope_per_hour: number | null
  /** Valeur de la droite maintenant. */
  current: number | null
  /**
   * Valeur de la droite au bout de l'horizon. Absente quand la droite n'explique
   * pas la série (R² < 0,5) : sa présence est ce qui autorise le pointillé.
   */
  at_horizon: number | null
  /** La durée observée — on ne projette jamais plus loin qu'on a regardé. */
  horizon_minutes: number | null
  minutes_to_warning: number | null
  minutes_to_critical: number | null
}

/** Les ressources qui se consomment, sous les clés de leurs tuiles. */
export interface ResourceForecasts {
  window: TimeWindow
  forecasts: Partial<Record<'memory' | 'cpu', ResourceForecast>>
}

export const aiAssistantApi = {
  getHealth: (window: TimeWindow = '5m') =>
    aiClient
      .get<HealthSnapshot>('/api/monitoring/health', { params: { window } })
      .then((r) => r.data),

  getTrends: (window: TimeWindow = '1h') =>
    aiClient
      .get<MetricTrends>('/api/monitoring/trends', { params: { window } })
      .then((r) => r.data),

  getForecast: (window: TimeWindow = '1h') =>
    aiClient
      .get<ResourceForecasts>('/api/monitoring/forecast', { params: { window } })
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

/**
 * Un article de la circulaire cité au bas d'une proposition de règle.
 *
 * `concordance` est la seule information qui autorise à écrire « cette règle
 * correspond à l'article X » : elle est vraie quand la portée pré-annotée de
 * l'article est bien celle que la règle produite utilise. Les autres articles
 * ont été remontés par la recherche sans que la règle les applique — les
 * présenter comme correspondants serait une citation décorative, c'est-à-dire
 * une apparence de preuve.
 *
 * Nommée `ConsigneSource` et non `ConstraintSource` : ce dernier existe déjà
 * dans planning.api et désigne l'ORIGINE d'une règle (manuelle, IA), pas sa
 * source réglementaire.
 */
export interface ConsigneSource {
  id: string
  page: number
  citation: string
  extrait: string
  extrait_ar: string
  portee: string | null
  severite: string | null
  concordance: boolean
}

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
  /**
   * Articles de la circulaire retrouvés pour cette demande. Liste vide quand le
   * ministère ne couvre pas le sujet — le cas normal d'une règle propre à
   * l'établissement, que l'écran a intérêt à dire plutôt qu'à taire.
   */
  sources: ConsigneSource[]
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
      .post<PlanningChatResponse>(
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

// ── Circulaire ministérielle ────────────────────────────────────────────────
//
// Lecture seule d'un texte réglementaire public : pas d'en-tête de tenant, pas
// de paramètre d'établissement. La circulaire est le même texte pour tous les
// collèges du pays — il n'y a rien à cloisonner, et prétendre le contraire
// ferait croire à un périmètre qui n'existe pas.

export interface ConsigneArticle {
  id: string
  page: number
  section: string
  /** Traduction de l'article. C'est la parole du ministère. */
  texte: string
  /** Texte arabe d'origine, cité tel quel. */
  texte_ar: string
  /** NOTRE lecture du rattachement au solveur — à ne jamais présenter comme officielle. */
  commentaire: string
  /** Non nul seulement pour les articles qui se traduisent en contrainte. */
  portee: string | null
  severite: string | null
  citation: string
}

export interface ConsigneArticlesResponse {
  reference: string
  articles: ConsigneArticle[]
}

export interface ConsigneChatResponse {
  answer: string
  sources: ConsigneArticle[]
  duration_ms: number
}

export const consigneApi = {
  /** Les 22 articles, dans l'ordre du document. Corpus statique : mis en cache long. */
  listArticles: () =>
    aiClient.get<ConsigneArticlesResponse>('/api/consigne/articles').then((r) => r.data),

  /**
   * Question libre sur la circulaire.
   *
   * Les articles cités remontent ENTIERS jusqu'à l'écran, et non résumés en
   * étiquettes « § III.2.a · p. 4 » comme ils l'étaient d'abord. C'était sous-
   * vendre la seule chose qui distingue cet assistant d'un modèle répondant de
   * mémoire : la réponse est vérifiable, texte à l'appui, arabe d'origine
   * compris. Le composant les affiche dépliables sous la réponse.
   *
   * La borne de temps est plus courte que celle des autres : ce service n'a pas
   * de boucle d'outils, donc au plus une génération. Mesuré entre 1 et 7 s avec
   * articles, et environ 100 ms quand la circulaire ne couvre pas la question —
   * le modèle n'est alors pas appelé du tout.
   */
  ask: (message: string) =>
    aiClient
      .post<ConsigneChatResponse>('/api/consigne/chat', { message }, { timeout: 90_000 })
      .then((r) => ({
        answer: r.data.answer,
        // Vide, et volontairement : les sources ont désormais leur propre
        // rendu. Les répéter en pastilles ferait dire deux fois la même chose,
        // dont une fois mal.
        tools_used: [],
        duration_ms: r.data.duration_ms,
        sources: r.data.sources,
      })),
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
