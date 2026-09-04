/**
 * Vocabulaire de l'emploi du temps, repris de `useMonPlanning.ts` côté web.
 * Les deux clients lisent la même vue enseignant : les règles de tri et de
 * découpage de la journée doivent donner exactement le même résultat.
 */

import type { SessionView, TeacherTimetableView } from './api/types'

/** Jours renvoyés par le planning, indexés comme Date#getDay(). */
const DAY_CODES = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

export const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Lundi',
  TUESDAY: 'Mardi',
  WEDNESDAY: 'Mercredi',
  THURSDAY: 'Jeudi',
  FRIDAY: 'Vendredi',
  SATURDAY: 'Samedi',
  SUNDAY: 'Dimanche',
}

export const SESSION_TYPE_LABELS: Record<string, string> = {
  COURSE: 'Cours',
  TD: 'TD',
  TP: 'TP',
  LAB: 'Labo',
  SPORT: 'Sport',
  EXAM: 'Examen',
}

export function dayCodeOf(date: Date = new Date()): string {
  return DAY_CODES[date.getDay()]
}

/** Date du jour en ISO **local** : l'UTC décalerait la journée d'une séance du soir. */
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

export function sessionsOfDay(view: TeacherTimetableView | undefined, day: string): SessionView[] {
  const sessions = view?.schedule.find((d) => d.day === day)?.sessions ?? []
  return [...sessions].sort((a, b) => toMinutes(a.startTime) - toMinutes(b.startTime))
}

/** `true` si l'heure courante tombe dans le créneau de la séance. */
export function isOngoing(session: SessionView): boolean {
  const now = nowMinutes()
  return now >= toMinutes(session.startTime) && now < toMinutes(session.endTime)
}
