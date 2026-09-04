import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, AlertCircle, Info, Loader2, Lightbulb } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { planningApi, type ConstraintViolation } from '@/api/planning.api'
import { describeScore } from './jobStatus'
import { cn } from '@/lib/utils'

interface Props {
  jobId: number | null
  onClose: () => void
}

const LEVELS = [
  { key: 'hardViolations' as const, title: 'Conflits importants (bloquants)', icon: AlertTriangle, tone: 'danger' as const },
  { key: 'mediumViolations' as const, title: 'Conflits moyens', icon: AlertCircle, tone: 'warning' as const },
  { key: 'softViolations' as const, title: 'Préférences non respectées', icon: Info, tone: 'info' as const },
]

const TONE_CLASSES: Record<'danger' | 'warning' | 'info', string> = {
  danger: 'border-red-200 bg-red-50/60 dark:border-red-500/20 dark:bg-red-500/5',
  warning: 'border-amber-200 bg-amber-50/60 dark:border-amber-500/20 dark:bg-amber-500/5',
  info: 'border-blue-200 bg-blue-50/60 dark:border-blue-500/20 dark:bg-blue-500/5',
}

function ViolationCard({ v, tone }: { v: ConstraintViolation; tone: 'danger' | 'warning' | 'info' }) {
  return (
    <div className={cn('rounded-lg border p-3', TONE_CLASSES[tone])} title={v.score}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-brand-text dark:text-slate-100">{v.label}</p>
        <Badge variant={tone} className="shrink-0">{v.count} fois</Badge>
      </div>
      {v.examples.length > 0 && (
        <ul className="mt-2 space-y-1">
          {v.examples.map((ex, i) => (
            <li key={i} className="text-xs text-brand-textMuted dark:text-slate-400 pl-3 border-l-2 border-brand-border dark:border-slate-700">
              {ex}
            </li>
          ))}
        </ul>
      )}
      {v.suggestion && (
        <div className="mt-3 flex items-start gap-2 text-xs text-brand-text dark:text-slate-200 bg-white/70 dark:bg-slate-900/40 rounded-md px-2.5 py-2">
          <Lightbulb size={13} className="shrink-0 mt-0.5 text-amber-500" />
          <span><strong>Comment corriger : </strong>{v.suggestion}</span>
        </div>
      )}
    </div>
  )
}

/** Panneau d'explication déterministe du score Timefold — pas d'IA, juste les ConstraintMatch. */
export function ScoreExplanationPanel({ jobId, onClose }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ['score-explanation', jobId],
    queryFn: () => planningApi.timetable.jobs.scoreExplanation(jobId!),
    enabled: jobId !== null,
  })

  const totalViolations = data
    ? data.hardViolations.length + data.mediumViolations.length + data.softViolations.length
    : 0

  return (
    <Modal open={jobId !== null} onClose={onClose} title={jobId ? `Pourquoi le job #${jobId} ?` : ''} size="lg">
      {isLoading ? (
        <div className="flex items-center justify-center py-12 text-brand-textMuted dark:text-slate-400">
          <Loader2 size={20} className="animate-spin me-2" /> Analyse du score...
        </div>
      ) : !data ? (
        <p className="text-sm text-brand-textMuted dark:text-slate-400 py-6 text-center">Aucune donnée disponible.</p>
      ) : (
        <div className="space-y-5 max-h-[70vh] overflow-y-auto pr-1">
          <div className="flex items-center justify-between p-3 rounded-lg bg-brand-bgSecondary dark:bg-slate-800" title={data.score}>
            <span className="text-sm font-medium text-brand-text dark:text-slate-200">{describeScore(data.score)}</span>
            <Badge variant={data.feasible ? 'success' : 'danger'}>
              {data.feasible ? 'Planning exploitable tel quel' : 'Conflits bloquants à corriger'}
            </Badge>
          </div>

          {totalViolations === 0 && (
            <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-4">
              Aucun conflit détecté — le planning respecte toutes les contraintes.
            </p>
          )}

          {LEVELS.map(({ key, title, icon: Icon, tone }) => {
            const violations = data[key]
            if (violations.length === 0) return null
            return (
              <div key={key}>
                <div className="flex items-center gap-2 mb-2">
                  <Icon size={15} className={cn(
                    tone === 'danger' && 'text-danger',
                    tone === 'warning' && 'text-amber-500',
                    tone === 'info' && 'text-brand-blue'
                  )} />
                  <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">{title}</h3>
                  <span className="text-xs text-brand-textMuted dark:text-slate-400">({violations.length})</span>
                </div>
                <div className="space-y-2">
                  {violations.map((v) => (
                    <ViolationCard key={v.constraintName} v={v} tone={tone} />
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </Modal>
  )
}
