import { useState, useEffect, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  Save, Clock, CheckCircle, XCircle, Loader2, Sparkles, Sun, Sunset,
  CalendarDays, LayoutGrid, Timer,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { TimeInput24 } from '@/components/ui/TimeInput24'
import { organisationApi, type WorkingDayRequest, type SchoolConfigResponse } from '@/api/organisation.api'

const DAYS_FR: Record<string, string> = {
  MONDAY: 'Lundi',
  TUESDAY: 'Mardi',
  WEDNESDAY: 'Mercredi',
  THURSDAY: 'Jeudi',
  FRIDAY: 'Vendredi',
  SATURDAY: 'Samedi',
  SUNDAY: 'Dimanche',
}

const DEFAULT_DAYS: WorkingDayRequest[] = [
  { dayOfWeek: 'MONDAY', active: true, morningStart: '08:00', morningEnd: '12:00', afternoonStart: '14:00', afternoonEnd: '17:00' },
  { dayOfWeek: 'TUESDAY', active: true, morningStart: '08:00', morningEnd: '12:00', afternoonStart: '14:00', afternoonEnd: '17:00' },
  { dayOfWeek: 'WEDNESDAY', active: true, morningStart: '08:00', morningEnd: '12:00', afternoonStart: undefined, afternoonEnd: undefined },
  { dayOfWeek: 'THURSDAY', active: true, morningStart: '08:00', morningEnd: '12:00', afternoonStart: '14:00', afternoonEnd: '17:00' },
  { dayOfWeek: 'FRIDAY', active: true, morningStart: '08:00', morningEnd: '12:00', afternoonStart: '14:00', afternoonEnd: '17:00' },
  { dayOfWeek: 'SATURDAY', active: false, morningStart: '08:00', morningEnd: '12:00', afternoonStart: undefined, afternoonEnd: undefined },
]

/** Un créneau numéroté (1, 2, 3...) au sein d'une période (matin/après-midi) d'un jour. */
interface SlotPreview {
  index: number
  start: string
  end: string
}

function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number)
  return h * 60 + m
}

function toHHMM(totalMinutes: number): string {
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** Calcule côté client la numérotation des créneaux d'une période, pour aperçu instantané. */
function previewSlots(start: string | undefined, end: string | undefined, durationMinutes: number): SlotPreview[] {
  if (!start || !end) return []
  const startMin = toMinutes(start)
  const endMin = toMinutes(end)
  const slots: SlotPreview[] = []
  let cur = startMin
  let index = 1
  while (cur + durationMinutes <= endMin) {
    slots.push({ index, start: toHHMM(cur), end: toHHMM(cur + durationMinutes) })
    cur += durationMinutes
    index++
  }
  return slots
}

function SlotChips({ slots, tone }: { slots: SlotPreview[]; tone: 'morning' | 'afternoon' }) {
  if (slots.length === 0) return null
  const toneClass =
    tone === 'morning'
      ? 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-500/10 dark:text-amber-400 dark:border-amber-500/20'
      : 'bg-indigo-50 text-indigo-700 border-indigo-200 dark:bg-indigo-500/10 dark:text-indigo-400 dark:border-indigo-500/20'
  return (
    <div className="flex flex-wrap items-center gap-1">
      {slots.map((s) => (
        <div
          key={s.index}
          title={`${s.start} → ${s.end}`}
          className={`flex flex-col items-center justify-center w-9 h-9 rounded-lg border text-[10px] font-mono leading-none cursor-default ${toneClass}`}
        >
          <span className="text-xs font-bold font-sans">{s.index}</span>
          <span className="opacity-70 mt-0.5">{s.start.slice(0, 2)}h</span>
        </div>
      ))}
    </div>
  )
}

export default function ConfigurationHoraires() {
  const [days, setDays] = useState<WorkingDayRequest[]>(DEFAULT_DAYS)
  const [slotDuration, setSlotDuration] = useState(60)
  const [showResult, setShowResult] = useState(false)
  const queryClient = useQueryClient()

  const { data: config, isLoading } = useQuery({
    queryKey: ['school-config'],
    queryFn: organisationApi.config.get,
  })

  useEffect(() => {
    if (config) {
      if (config.slotDurationMinutes) setSlotDuration(config.slotDurationMinutes)
      if (config.workingDays && config.workingDays.length > 0) {
        setDays(config.workingDays.map((d) => ({
          dayOfWeek: d.dayOfWeek,
          active: d.active ?? true,
          morningStart: d.morningStart ?? '08:00',
          morningEnd: d.morningEnd ?? '12:00',
          afternoonStart: d.afternoonStart,
          afternoonEnd: d.afternoonEnd,
        })))
      }
      if (config.timeSlots && config.timeSlots.length > 0) setShowResult(true)
    }
  }, [config])

  const saveMutation = useMutation({
    mutationFn: () => organisationApi.config.save({ workingDays: days, slotDurationMinutes: slotDuration }),
    onSuccess: (data) => {
      queryClient.setQueryData(['school-config'], data)
      setShowResult(true)
      toast.success(`${data.totalSlotsPerWeek ?? ''} créneaux générés avec succès`)
    },
    onError: () => toast.error('Erreur lors de la sauvegarde'),
  })

  function updateDay(index: number, patch: Partial<WorkingDayRequest>) {
    setDays((prev) => prev.map((d, i) => i === index ? { ...d, ...patch } : d))
  }

  const activeDays = days.filter((d) => d.active).length
  const totalSlots = config?.totalSlotsPerWeek ?? '—'
  const isGenerating = saveMutation.isPending

  return (
    <div className="space-y-6">
      <PageHero
        title="Configuration des horaires"
        subtitle="Les jours ouvrés et les créneaux dans lesquels l'emploi du temps sera calculé"
        icon={Clock}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={() => saveMutation.mutate()}
            loading={isGenerating}
          >
            {isGenerating ? 'Génération…' : <><Save size={16} /> Enregistrer</>}
          </Button>
        }
      />

      {/* Indicateur de génération en cours */}
      {isGenerating && (
        <div className="relative overflow-hidden rounded-xl border border-brand-blue/30 bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-500/10 dark:to-indigo-500/10 p-4 flex items-center gap-3">
          <Loader2 size={18} className="text-brand-blue animate-spin shrink-0" />
          <div className="flex-1">
            <p className="text-sm font-semibold text-brand-text dark:text-slate-100">Génération des créneaux horaires en cours…</p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400">Calcul des créneaux pour chaque jour actif</p>
          </div>
          <div className="absolute bottom-0 left-0 h-0.5 w-full bg-brand-blue/20 overflow-hidden">
            <div className="h-full w-1/3 bg-brand-blue animate-[progress_1.2s_ease-in-out_infinite]" />
          </div>
          <style>{`@keyframes progress { 0% { transform: translateX(-100%);} 100% { transform: translateX(300%);} }`}</style>
        </div>
      )}

      {/* Les trois chiffres passent sur le composant partagé, comme partout
          ailleurs dans la section ; l'état « prêt » gagne sa propre ligne au
          lieu d'être serré dans un coin de tuile. */}
      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard title="Jours ouvrés" value={activeDays} icon={CalendarDays} color="blue" />
        <StatCard title="Durée d'un créneau" value={`${slotDuration} min`} icon={Timer} color="purple" />
        <StatCard title="Créneaux par semaine" value={totalSlots} icon={LayoutGrid} color="green" />
      </div>

      <div className={`flex items-start gap-2 rounded-xl p-3 text-sm ${
        config?.isReadyForGeneration
          ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400'
          : 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400'
      }`}>
        {config?.isReadyForGeneration
          ? <CheckCircle size={16} className="mt-0.5 shrink-0" />
          : <XCircle size={16} className="mt-0.5 shrink-0" />}
        <span>
          {config?.isReadyForGeneration
            ? "Ces horaires sont complets : la génération de l'emploi du temps peut s'appuyer dessus."
            : "Configuration incomplète — la génération de l'emploi du temps restera indisponible tant qu'un jour ouvré n'aura pas ses créneaux."}
        </span>
      </div>

      {/* Durée créneau */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
        <div className="flex items-center gap-3 mb-2">
          <Clock size={16} className="text-brand-textMuted dark:text-slate-400" />
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Durée d'un créneau</h3>
        </div>
        <div className="flex gap-2 flex-wrap">
          {[30, 45, 60, 90, 120].map((d) => (
            <button
              key={d}
              onClick={() => setSlotDuration(d)}
              aria-pressed={slotDuration === d}
              className={`rounded-lg border px-4 py-2 text-sm font-medium transition-colors ${
                slotDuration === d
                  ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                  : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
              }`}
            >
              {d} min
            </button>
          ))}
        </div>
      </div>

      {/* Tableau des jours */}
      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-16 bg-brand-bgSecondary dark:bg-slate-800 rounded-xl animate-pulse" />)}
        </div>
      ) : (
        <div className={`space-y-3 transition-opacity ${isGenerating ? 'opacity-60 pointer-events-none' : ''}`}>
          {days.map((day, i) => {
            const morningSlots = previewSlots(day.morningStart, day.morningEnd, slotDuration)
            const afternoonSlots = previewSlots(day.afternoonStart, day.afternoonEnd, slotDuration)
            return (
              <div key={day.dayOfWeek} className={`bg-white dark:bg-slate-900 rounded-xl border p-4 transition-colors ${day.active ? 'border-brand-border dark:border-slate-700' : 'border-brand-border dark:border-slate-700 opacity-60'}`}>
                <div className="flex items-start gap-4 flex-wrap">
                  {/* Toggle */}
                  <button
                    onClick={() => updateDay(i, { active: !day.active })}
                    className={`w-10 h-6 rounded-full transition-colors relative mt-1 shrink-0 ${day.active ? 'bg-brand-blue' : 'bg-brand-bgSecondary dark:bg-slate-700'}`}
                  >
                    <span className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${day.active ? 'translate-x-4' : 'translate-x-0.5'}`} />
                  </button>

                  {/* Jour label */}
                  <span className="w-28 font-semibold text-brand-text dark:text-slate-100 text-sm mt-1 shrink-0">{DAYS_FR[day.dayOfWeek]}</span>

                  {day.active && (
                    <div className="flex-1 min-w-0 space-y-3">
                      <div className="flex flex-wrap items-center gap-6">
                        {/* Matin */}
                        <div className="flex items-center gap-2 text-sm">
                          <Sun size={13} className="text-amber-500" />
                          <span className="text-brand-textMuted dark:text-slate-400 w-12">Matin</span>
                          <TimeInput24 value={day.morningStart} onChange={(v) => updateDay(i, { morningStart: v })} className="w-16 border border-brand-border dark:border-slate-700 rounded px-2 py-1 text-sm bg-white dark:bg-slate-800 text-brand-text dark:text-slate-200" />
                          <span className="text-brand-textMuted dark:text-slate-400">→</span>
                          <TimeInput24 value={day.morningEnd} onChange={(v) => updateDay(i, { morningEnd: v })} className="w-16 border border-brand-border dark:border-slate-700 rounded px-2 py-1 text-sm bg-white dark:bg-slate-800 text-brand-text dark:text-slate-200" />
                        </div>

                        {/* Après-midi */}
                        <div className="flex items-center gap-2 text-sm">
                          <Sunset size={13} className="text-indigo-500" />
                          <span className="text-brand-textMuted dark:text-slate-400 w-20">Après-midi</span>
                          <TimeInput24 value={day.afternoonStart} onChange={(v) => updateDay(i, { afternoonStart: v })} className="w-16 border border-brand-border dark:border-slate-700 rounded px-2 py-1 text-sm bg-white dark:bg-slate-800 text-brand-text dark:text-slate-200" />
                          <span className="text-brand-textMuted dark:text-slate-400">→</span>
                          <TimeInput24 value={day.afternoonEnd} onChange={(v) => updateDay(i, { afternoonEnd: v })} className="w-16 border border-brand-border dark:border-slate-700 rounded px-2 py-1 text-sm bg-white dark:bg-slate-800 text-brand-text dark:text-slate-200" />
                          <button onClick={() => updateDay(i, { afternoonStart: undefined, afternoonEnd: undefined })} className="text-xs text-brand-textMuted dark:text-slate-400 hover:text-danger">Effacer</button>
                        </div>
                      </div>

                      {/* Aperçu numéroté des créneaux (ex: après-midi 13,14,...,18 → 1,2,...,6) */}
                      {(morningSlots.length > 0 || afternoonSlots.length > 0) && (
                        <div className="flex flex-wrap items-center gap-4 pt-1">
                          <SlotChips slots={morningSlots} tone="morning" />
                          <SlotChips slots={afternoonSlots} tone="afternoon" />
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Résultat de la génération — vue moderne des créneaux persistés */}
      {showResult && config?.timeSlots && config.timeSlots.length > 0 && !isGenerating && (
        <GeneratedSlotsShowcase config={config} />
      )}
    </div>
  )
}

function GeneratedSlotsShowcase({ config }: { config: SchoolConfigResponse }) {
  const groupedByDay = useMemo(() => {
    const map: Record<string, { morning: SlotPreview[]; afternoon: SlotPreview[] }> = {}
    const order = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
    for (const slot of config.timeSlots ?? []) {
      if (!map[slot.dayOfWeek]) map[slot.dayOfWeek] = { morning: [], afternoon: [] }
      const bucket = slot.dayPeriod === 'AFTERNOON' ? map[slot.dayOfWeek].afternoon : map[slot.dayOfWeek].morning
      bucket.push({ index: bucket.length + 1, start: slot.startTime.slice(0, 5), end: slot.endTime.slice(0, 5) })
    }
    return order.filter((d) => map[d]).map((d) => ({ day: d, ...map[d] }))
  }, [config.timeSlots])

  return (
    <div className="rounded-xl border border-emerald-200 dark:border-emerald-500/20 bg-gradient-to-br from-emerald-50/60 via-white to-white dark:from-emerald-500/5 dark:via-slate-900 dark:to-slate-900 p-5 animate-[fadeIn_0.4s_ease-out]">
      <style>{`@keyframes fadeIn { from { opacity: 0; transform: translateY(4px);} to { opacity: 1; transform: translateY(0);} }`}</style>
      <div className="flex items-center gap-2 mb-4">
        <div className="w-8 h-8 rounded-lg bg-emerald-500/15 flex items-center justify-center">
          <Sparkles size={16} className="text-emerald-600 dark:text-emerald-400" />
        </div>
        <div>
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Créneaux générés</h3>
          <p className="text-xs text-brand-textMuted dark:text-slate-400">{config.totalSlotsPerWeek} créneaux répartis sur {groupedByDay.length} jours</p>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {groupedByDay.map(({ day, morning, afternoon }) => (
          <div key={day} className="bg-white dark:bg-slate-900 rounded-lg border border-brand-border dark:border-slate-700 p-3">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-semibold text-brand-text dark:text-slate-100">{DAYS_FR[day]}</span>
              <Badge variant="success">{morning.length + afternoon.length} créneaux</Badge>
            </div>
            {morning.length > 0 && (
              <div className="mb-2">
                <div className="flex items-center gap-1 text-[10px] uppercase tracking-wide text-amber-600 dark:text-amber-400 font-semibold mb-1">
                  <Sun size={10} /> Matin
                </div>
                <SlotChips slots={morning} tone="morning" />
              </div>
            )}
            {afternoon.length > 0 && (
              <div>
                <div className="flex items-center gap-1 text-[10px] uppercase tracking-wide text-indigo-600 dark:text-indigo-400 font-semibold mb-1">
                  <Sunset size={10} /> Après-midi
                </div>
                <SlotChips slots={afternoon} tone="afternoon" />
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
