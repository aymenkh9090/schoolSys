import { useMemo, useState, type CSSProperties } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { DndContext, useDraggable, useDroppable, type DragEndEvent } from '@dnd-kit/core'
import { toast } from 'sonner'
import { Pencil } from 'lucide-react'

import { planningApi, type DaySchedule, type SessionView } from '@/api/planning.api'
import { organisationApi } from '@/api/organisation.api'
import { cn } from '@/lib/utils'
import { SessionEditModal } from './SessionEditModal'

const DAYS_FR: Record<string, string> = {
  MONDAY: 'Lundi', TUESDAY: 'Mardi', WEDNESDAY: 'Mercredi',
  THURSDAY: 'Jeudi', FRIDAY: 'Vendredi', SATURDAY: 'Samedi',
}
const WEEK_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

const SESSION_TYPE_LABELS: Record<string, string> = {
  COURSE: 'Cours', TD: 'TD', TP: 'TP', LAB: 'Labo', SPORT: 'Sport', EXAM: 'Examen',
}

const FALLBACK_PALETTE = [
  '#3b82f6', '#ef4444', '#22c55e', '#f97316', '#8b5cf6', '#06b6d4',
  '#ec4899', '#f59e0b', '#10b981', '#64748b', '#6366f1', '#84cc16',
]

function colorForSubject(code: string, colorMap: Record<string, string>): string {
  const known = colorMap[code]
  if (known) return known
  let hash = 0
  for (let i = 0; i < code.length; i++) hash = (hash * 31 + code.charCodeAt(i)) >>> 0
  return FALLBACK_PALETTE[hash % FALLBACK_PALETTE.length]
}

function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number)
  return h * 60 + m
}

export interface FlatSession extends SessionView {
  day: string
}

interface Props {
  jobId: number
  /** Les 3 vues (classe/enseignant/salle) partagent cette forme minimale. */
  view: { schedule: DaySchedule[] }
  canEdit: boolean
  onChanged: () => void
}

export function TimetableGrid({ jobId, view, canEdit, onChanged }: Props) {
  const [editingSession, setEditingSession] = useState<FlatSession | null>(null)
  const [pendingId, setPendingId] = useState<number | null>(null)

  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: organisationApi.subjects.list })
  const { data: config } = useQuery({ queryKey: ['school-config'], queryFn: organisationApi.config.get })

  const colorMap = useMemo(() => {
    const map: Record<string, string> = {}
    for (const s of subjects) if (s.couleur) map[s.codeMatiere] = s.couleur
    return map
  }, [subjects])

  const activeDays = useMemo(
    () => WEEK_ORDER.filter((d) => view.schedule.some((ds) => ds.day === d && ds.sessions.length > 0)),
    [view.schedule]
  )

  // Créneaux valides par jour, dérivés de la config école (jours fermés / pause déjeuner = trous naturels)
  const slotsByDay = useMemo(() => {
    const map: Record<string, Set<string>> = {}
    for (const day of activeDays) map[day] = new Set()
    for (const slot of config?.timeSlots ?? []) {
      const day = slot.dayOfWeek
      const start = slot.startTime.slice(0, 5)
      if (map[day]) map[day].add(start)
    }
    return map
  }, [config, activeDays])

  const rowTimes = useMemo(() => {
    const all = new Set<string>()
    Object.values(slotsByDay).forEach((set) => set.forEach((t) => all.add(t)))
    if (all.size === 0) {
      // Config école indisponible — repli sur les horaires des séances elles-mêmes
      for (const ds of view.schedule) for (const s of ds.sessions) all.add(s.startTime)
    }
    return Array.from(all).sort((a, b) => toMinutes(a) - toMinutes(b))
  }, [slotsByDay, view.schedule])

  const flatSessions: FlatSession[] = useMemo(
    () => view.schedule.flatMap((ds) => ds.sessions.map((s) => ({ ...s, day: ds.day }))),
    [view.schedule]
  )

  // Regroupe les séances qui partagent exactement le même jour/heure (ex: demi-groupes
  // A/B en salles différentes) pour les placer côte à côte plutôt que superposées.
  const cellGroups = useMemo(() => {
    const groups = new Map<string, FlatSession[]>()
    for (const s of flatSessions) {
      const key = `${s.day}|${s.startTime}`
      const arr = groups.get(key)
      if (arr) arr.push(s)
      else groups.set(key, [s])
    }
    return Array.from(groups.values())
  }, [flatSessions])

  const subjectLegend = useMemo(() => {
    const seen = new Map<string, string>()
    for (const s of flatSessions) if (!seen.has(s.subjectCode)) seen.set(s.subjectCode, s.subjectName)
    return Array.from(seen.entries())
  }, [flatSessions])

  const moveMutation = useMutation({
    mutationFn: ({ sessionId, day, startTime }: { sessionId: number; day: string; startTime: string }) =>
      planningApi.timetable.sessions.move(jobId, sessionId, { day, startTime }),
    onSuccess: () => {
      toast.success('Séance déplacée')
      onChanged()
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Impossible de déplacer cette séance'),
    onSettled: () => setPendingId(null),
  })

  function rowSpanFor(session: SessionView): number {
    return Math.max(1, Math.round((toMinutes(session.endTime) - toMinutes(session.startTime)) / 30))
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over) return
    const session = active.data.current?.session as FlatSession | undefined
    const target = over.data.current as { day: string; startTime: string } | undefined
    if (!session || !target) return
    if (session.day === target.day && session.startTime === target.startTime) return

    const durationSlots = rowSpanFor(session)
    const daySlots = slotsByDay[target.day]
    const targetIndex = rowTimes.indexOf(target.startTime)
    for (let i = 0; i < durationSlots; i++) {
      const slot = rowTimes[targetIndex + i]
      if (!slot || !daySlots?.has(slot)) {
        toast.error("Cette séance ne rentre pas dans les horaires de la journée")
        return
      }
    }

    setPendingId(session.id)
    moveMutation.mutate({ sessionId: session.id, day: target.day, startTime: target.startTime })
  }

  if (rowTimes.length === 0 || activeDays.length === 0) {
    return <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-8">Aucune séance planifiée</p>
  }

  return (
    <div>
      {subjectLegend.length > 0 && (
        <div className="flex flex-wrap gap-x-4 gap-y-1.5 mb-4 print:mb-2">
          {subjectLegend.map(([code, name]) => (
            <div key={code} className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
              <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: colorForSubject(code, colorMap) }} />
              {name}
            </div>
          ))}
        </div>
      )}

      <DndContext onDragEnd={handleDragEnd}>
        <div className="overflow-x-auto print:overflow-visible">
          <div
            className="grid rounded-xl overflow-hidden border border-brand-border dark:border-slate-700 print:border print:rounded-none"
            style={{
              gridTemplateColumns: `56px repeat(${activeDays.length}, minmax(140px, 1fr))`,
              gridTemplateRows: `auto repeat(${rowTimes.length}, 30px)`,
            }}
          >
            <div
              className="bg-brand-bgSecondary dark:bg-slate-800 border-b border-r border-brand-border dark:border-slate-700"
              style={{ gridColumn: 1, gridRow: 1 }}
            />
            {activeDays.map((day, di) => (
              <div
                key={day}
                className="bg-brand-bgSecondary dark:bg-slate-800 border-b border-r last:border-r-0 border-brand-border dark:border-slate-700 px-2 py-2 text-center text-xs font-semibold text-brand-text dark:text-slate-100"
                style={{ gridColumn: di + 2, gridRow: 1 }}
              >
                {DAYS_FR[day] ?? day}
              </div>
            ))}

            {rowTimes.map((t, ri) => (
              <div
                key={t}
                className="bg-brand-bgSecondary dark:bg-slate-800 border-b border-r border-brand-border dark:border-slate-700 px-1.5 flex items-start justify-end text-[10px] font-mono text-brand-textMuted dark:text-slate-500 pt-0.5"
                style={{ gridColumn: 1, gridRow: ri + 2 }}
              >
                {t}
              </div>
            ))}

            {activeDays.flatMap((day, di) =>
              rowTimes.map((t, ri) => {
                const isOpen = slotsByDay[day]?.has(t)
                return isOpen ? (
                  <DroppableCell
                    key={`${day}-${t}`}
                    day={day}
                    startTime={t}
                    canEdit={canEdit}
                    style={{ gridColumn: di + 2, gridRow: ri + 2 }}
                  />
                ) : (
                  <div
                    key={`${day}-${t}`}
                    className="bg-slate-50 dark:bg-slate-900/40 border-b border-r border-brand-border/50 dark:border-slate-800"
                    style={{ gridColumn: di + 2, gridRow: ri + 2 }}
                  />
                )
              })
            )}

            {cellGroups.map((group) => {
              const first = group[0]
              const di = activeDays.indexOf(first.day)
              const ri = rowTimes.indexOf(first.startTime)
              if (di === -1 || ri === -1) return null
              const span = Math.max(...group.map(rowSpanFor))
              return (
                <div
                  key={`${first.day}-${first.startTime}`}
                  className="flex gap-0.5 items-stretch"
                  style={{ gridColumn: di + 2, gridRow: `${ri + 2} / span ${span}` }}
                >
                  {group.map((s) => (
                    <SessionCard
                      key={s.id}
                      session={s}
                      color={colorForSubject(s.subjectCode, colorMap)}
                      canEdit={canEdit}
                      pending={pendingId === s.id}
                      className="flex-1 min-w-0"
                      onEdit={() => setEditingSession(s)}
                    />
                  ))}
                </div>
              )
            })}
          </div>
        </div>
      </DndContext>

      {editingSession && (
        <SessionEditModal
          jobId={jobId}
          session={editingSession}
          onClose={() => setEditingSession(null)}
          onSaved={() => { setEditingSession(null); onChanged() }}
        />
      )}
    </div>
  )
}

function DroppableCell({ day, startTime, canEdit, style }: { day: string; startTime: string; canEdit: boolean; style: CSSProperties }) {
  const { setNodeRef, isOver } = useDroppable({ id: `cell-${day}-${startTime}`, data: { day, startTime }, disabled: !canEdit })
  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        'border-b border-r border-brand-border/60 dark:border-slate-800 transition-colors',
        isOver && canEdit ? 'bg-brand-blue/10 dark:bg-blue-500/15' : 'bg-white dark:bg-slate-900'
      )}
    />
  )
}

function SessionCard({
  session, color, canEdit, pending, className, onEdit,
}: {
  session: FlatSession
  color: string
  canEdit: boolean
  pending: boolean
  className?: string
  onEdit: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: `session-${session.id}`,
    data: { session },
    disabled: !canEdit,
  })

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: transform ? `translate3d(${transform.x}px, ${transform.y}px, 0)` : undefined,
        zIndex: isDragging ? 30 : undefined,
        background: `${color}1f`,
        borderLeft: `3px solid ${color}`,
      }}
      className={cn(
        'group relative m-0.5 rounded-lg p-1.5 shadow-sm hover:shadow-md transition-shadow overflow-hidden select-none print:shadow-none print:m-0 print:rounded-none',
        canEdit && 'cursor-grab active:cursor-grabbing touch-none',
        pending && 'opacity-50 pointer-events-none',
        isDragging && 'opacity-70 shadow-lg',
        className
      )}
      {...(canEdit ? { ...listeners, ...attributes } : {})}
    >
      <div className="flex items-start justify-between gap-1">
        <p className="font-semibold text-[11px] leading-tight text-brand-text dark:text-slate-100 truncate">{session.subjectName}</p>
        {canEdit && (
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); onEdit() }}
            onPointerDown={(e) => e.stopPropagation()}
            className="opacity-0 group-hover:opacity-100 transition-opacity shrink-0 p-0.5 rounded hover:bg-white/70 dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 print:hidden"
          >
            <Pencil size={11} />
          </button>
        )}
      </div>
      {/* Chaque vue (classe/enseignant/salle) omet son propre champ côté backend —
          on affiche donc simplement les champs présents parmi les deux autres dimensions. */}
      <p className="text-[10px] text-brand-textMuted dark:text-slate-400 truncate">
        {[session.classCode, session.teacherName].filter(Boolean).join(' · ')}
      </p>
      <p className="text-[10px] text-brand-textMuted dark:text-slate-400 truncate">
        {[session.roomCode, SESSION_TYPE_LABELS[session.sessionType] ?? session.sessionType].filter(Boolean).join(' · ')}
        {session.groupLabel && (
          <span className="ml-1 px-1 rounded bg-white/70 dark:bg-slate-800 text-[9px]">{session.groupLabel}</span>
        )}
      </p>
    </div>
  )
}
