import { useEffect, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'

import type { SolverStatus, TimetableJob } from '@/api/planning.api'
import * as authStore from '@/auth/authStore'

/** Instantané d'avancement poussé par le serveur (cf. SolverProgressPublisher.SolverProgress). */
export interface SolverProgress {
  jobId: number
  status: SolverStatus
  score?: string | null
  feasible?: boolean | null
  placedSessions?: number | null
  totalSessions?: number | null
  errorMessage?: string | null
  at: string
}

const TERMINAL_STATUSES: SolverStatus[] = ['SOLVED', 'INFEASIBLE', 'FAILED', 'CANCELLED']

/** ws(s)://…/ws, dérivé de l'URL d'API pour ne pas dupliquer la configuration. */
function brokerUrl(): string {
  const base = import.meta.env.VITE_API_URL || window.location.origin
  return `${base.replace(/^http/, 'ws')}/ws`
}

/**
 * Abonnement à la progression du solveur pour l'établissement courant.
 *
 * Le serveur pousse un instantané au maximum une fois par seconde et par job,
 * plus un message à chaque changement d'état. Tant que la connexion est active,
 * l'appelant peut se passer de polling ; `connected` permet de le réactiver en
 * repli si le socket tombe (voir GenerationPlanning).
 */
export function useSolverProgress() {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)
  const [progress, setProgress] = useState<Record<number, SolverProgress>>({})

  useEffect(() => {
    const tenantId = authStore.getState().tokenParsed?.tenant_id
    if (!tenantId) return

    const client = new Client()

    const handleMessage = (message: IMessage) => {
      const update = JSON.parse(message.body) as SolverProgress
      setProgress((previous) => ({ ...previous, [update.jobId]: update }))

      // Le score et le statut sont appliqués directement au cache : la ligne du
      // tableau se met à jour sans requête. Les champs que le message ne porte
      // pas (finishedAt…) sont rechargés à la fin du job.
      let known = false
      queryClient.setQueryData<TimetableJob[]>(['timetable-jobs'], (jobs) => {
        if (!jobs) return jobs
        known = jobs.some((job) => job.jobId === update.jobId)
        return jobs.map((job) =>
          job.jobId === update.jobId
            ? {
                ...job,
                status: update.status,
                scoreAchieved: update.score ?? job.scoreAchieved,
                errorMessage: update.errorMessage ?? job.errorMessage,
              }
            : job
        )
      })

      if (!known || TERMINAL_STATUSES.includes(update.status)) {
        void queryClient.invalidateQueries({ queryKey: ['timetable-jobs'] })
      }
    }

    client.configure({
      brokerURL: brokerUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // Le handshake ne peut pas porter le jeton : il part dans la frame CONNECT,
      // rafraîchi juste avant chaque (re)connexion.
      beforeConnect: async () => {
        try {
          await authStore.updateToken(30)
        } catch {
          void client.deactivate()
          return
        }
        client.connectHeaders = { Authorization: `Bearer ${authStore.getState().accessToken}` }
      },
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/${tenantId}/planning/jobs`, handleMessage)
      },
      onWebSocketClose: () => setConnected(false),
      // Jeton refusé ou abonnement interdit : inutile de boucler sur la reconnexion,
      // on rend la main au polling.
      onStompError: (frame) => {
        console.warn('[ws] erreur STOMP:', frame.headers.message)
        setConnected(false)
        void client.deactivate()
      },
    })

    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [queryClient])

  return { connected, progress }
}
