import { useQuery } from '@tanstack/react-query'

import { organisationApi } from '@/api/organisation.api'
import { useAuth } from '@/hooks/useAuth'
import { planningApi, type SessionView, type TeacherTimetableView } from '@/api/planning.api'

/** Jours renvoyés par le planning, indexés comme Date#getDay(). */
const DAY_CODES = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

export const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Lundi', TUESDAY: 'Mardi', WEDNESDAY: 'Mercredi',
  THURSDAY: 'Jeudi', FRIDAY: 'Vendredi', SATURDAY: 'Samedi', SUNDAY: 'Dimanche',
}

export const WEEK_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

export const SESSION_TYPE_LABELS: Record<string, string> = {
  COURSE: 'Cours', TD: 'TD', TP: 'TP', LAB: 'Labo', SPORT: 'Sport', EXAM: 'Examen',
}

export function dayCodeOf(date: Date = new Date()): string {
  return DAY_CODES[date.getDay()]
}

/** Date du jour au format ISO local (et non UTC, qui décalerait la journée). */
export function todayIso(): string {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export function hhmm(time: string): string {
  return time.slice(0, 5)
}

export function toMinutes(time: string): number {
  const [h, m] = hhmm(time).split(':').map(Number)
  return h * 60 + m
}

export function nowMinutes(): number {
  const d = new Date()
  return d.getHours() * 60 + d.getMinutes()
}

/** Séances d'un jour de la semaine, triées par heure de début. */
export function sessionsOfDay(view: TeacherTimetableView | undefined, day: string): SessionView[] {
  const sessions = view?.schedule.find((d) => d.day === day)?.sessions ?? []
  return [...sessions].sort((a, b) => toMinutes(a.startTime) - toMinutes(b.startTime))
}

/** `true` si l'heure courante tombe dans le créneau de la séance. */
export function isOngoing(session: SessionView): boolean {
  const now = nowMinutes()
  return now >= toMinutes(session.startTime) && now < toMinutes(session.endTime)
}

/**
 * Fiche de l'enseignant connecté + sa vue de l'emploi du temps publié le plus récent.
 * Base commune au tableau de bord enseignant, à « Mon planning » et à l'appel web.
 */
export function useMonPlanning() {
  // `/teachers/me` est réservé au rôle TEACHER : l'appeler depuis un compte de
  // vie scolaire ne renverrait qu'un 403. Les écrans partagés (appel) traitent
  // déjà l'absence de fiche enseignant.
  const { isTeacher } = useAuth()

  const meQuery = useQuery({
    queryKey: ['teacher-me'],
    queryFn: organisationApi.teachers.me,
    enabled: isTeacher,
    retry: false,
  })
  const me = meQuery.data

  const { data: generated = [] } = useQuery({
    queryKey: ['timetables-generated'],
    queryFn: () => planningApi.timetable.generated.list(),
    enabled: !!me,
  })

  const published = generated
    .filter((g) => g.status === 'PUBLISHED')
    .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))[0]

  const timetableQuery = useQuery({
    queryKey: ['timetable-me', published?.jobId, me?.codeEnseignant],
    queryFn: () => planningApi.timetable.views.byTeacher(published!.jobId, me!.codeEnseignant),
    enabled: !!published && !!me,
  })

  return {
    me,
    isTeacherFiche: !!me,
    meLoading: meQuery.isLoading,
    meError: meQuery.isError,
    meErrorMessage:
      (meQuery.error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message,
    published,
    timetable: timetableQuery.data,
    timetableLoading: timetableQuery.isLoading,
    todaySessions: sessionsOfDay(timetableQuery.data, dayCodeOf()),
  }
}
