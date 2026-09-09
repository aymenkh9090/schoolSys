import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, AlertCircle, Info, Loader2, Lightbulb, ShieldAlert, Crosshair, ArrowRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { planningApi, type ConstraintViolation, type ViolationOccurrence } from '@/api/planning.api'
import { describeScore } from './jobStatus'
import { BusinessCard, FINDING_TONE_CLASSES, groupFindings } from './businessFindings'
import { cn } from '@/lib/utils'
import { cibleDeLOccurrence, lienDeDesignation } from './designation'

interface Props {
  jobId: number | null
  onClose: () => void
}

const LEVELS = [
  { key: 'hardViolations' as const, title: 'Conflits importants (bloquants)', icon: AlertTriangle, tone: 'danger' as const },
  { key: 'mediumViolations' as const, title: 'Conflits moyens', icon: AlertCircle, tone: 'warning' as const },
  { key: 'softViolations' as const, title: 'Préférences non respectées', icon: Info, tone: 'info' as const },
]

/**
 * Une occurrence : la phrase, le bouton qui la montre, et le remède s'il existe.
 *
 * C'est ici que l'explication cesse de raconter. « L'enseignant Ahmed a deux
 * cours en même temps » ne se clique pas : il restait à retrouver les deux
 * séances à la main dans une grille de vingt-trois classes. Le bouton, lui,
 * ouvre la grille qui les porte et les surligne toutes les deux — en montrer
 * une seule ne montrerait pas le conflit.
 */
function OccurrenceRow({
  occurrence, jobId, onNavigate,
}: {
  occurrence: ViolationOccurrence
  jobId: number
  onNavigate: (lien: string) => void
}) {
  const cible = cibleDeLOccurrence(occurrence)

  return (
    <li className="border-l-2 border-brand-border dark:border-slate-700 pl-3 space-y-1">
      <div className="flex items-start justify-between gap-2">
        <span className="text-xs text-brand-textMuted dark:text-slate-400">{occurrence.label}</span>
        {/* Pas de bouton quand rien n'est surlignable — job antérieur à la
            colonne lesson_id. Un geste sans effet coûte plus cher que son
            absence : on l'essaie, et on doute du reste de l'écran. */}
        {cible && (
          <button
            type="button"
            onClick={() => onNavigate(lienDeDesignation(jobId, cible))}
            className="shrink-0 inline-flex items-center gap-1 rounded-md border border-brand-border dark:border-slate-600 bg-white/70 dark:bg-slate-900/40 px-1.5 py-0.5 text-[11px] text-brand-text dark:text-slate-200 transition-colors hover:bg-white dark:hover:bg-slate-800"
          >
            <Crosshair size={11} /> Voir dans la grille
          </button>
        )}
      </div>
      {occurrence.relocation && (
        <p className="flex items-start gap-1.5 rounded-md bg-white/70 dark:bg-slate-900/40 px-2 py-1.5 text-[11px] leading-relaxed text-brand-text dark:text-slate-200">
          <ArrowRight size={12} className="mt-0.5 shrink-0 text-amber-500" />
          {occurrence.relocation.text}
        </p>
      )}
    </li>
  )
}

function ViolationCard({
  v, tone, jobId, onNavigate,
}: {
  v: ConstraintViolation
  tone: 'danger' | 'warning' | 'info'
  jobId: number
  onNavigate: (lien: string) => void
}) {
  const occurrences = v.occurrences ?? []
  // Une contrainte à seuil — « pas plus de 6 h par jour » — n'incrimine aucune
  // séance en particulier : son tuple porte une clé de groupe et un cumul. Elle
  // n'a donc rien à désigner, et la phrase reste le seul rendu honnête.
  const aDesigner = occurrences.length > 0
  // La suggestion générale répète la proposition de la première occurrence
  // lorsqu'il y en a une : ne la montrer qu'à défaut évite de dire deux fois la
  // même chose à deux endroits de la même carte.
  const propositionAffichee = occurrences.some((o) => o.relocation)

  return (
    <div className={cn('rounded-lg border p-3', FINDING_TONE_CLASSES[tone])} title={v.score}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-brand-text dark:text-slate-100">{v.label}</p>
        <Badge variant={tone} className="shrink-0">{v.count} fois</Badge>
      </div>

      {aDesigner ? (
        <ul className="mt-2 space-y-2">
          {occurrences.map((o, i) => (
            <OccurrenceRow key={i} occurrence={o} jobId={jobId} onNavigate={onNavigate} />
          ))}
        </ul>
      ) : (
        v.examples.length > 0 && (
          <ul className="mt-2 space-y-1">
            {v.examples.map((ex, i) => (
              <li key={i} className="text-xs text-brand-textMuted dark:text-slate-400 pl-3 border-l-2 border-brand-border dark:border-slate-700">
                {ex}
              </li>
            ))}
          </ul>
        )
      )}

      {v.suggestion && !propositionAffichee && (
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
  const navigate = useNavigate()

  // Fermer avant de naviguer : la modale couvre la grille, et laisser
  // l'utilisateur découvrir qu'il doit la fermer lui-même après avoir cliqué
  // « voir » transforme le geste en énigme.
  const montrer = (lien: string) => {
    onClose()
    navigate(lien)
  }

  const { data, isLoading } = useQuery({
    queryKey: ['score-explanation', jobId],
    queryFn: () => planningApi.timetable.jobs.scoreExplanation(jobId!),
    enabled: jobId !== null,
  })

  const totalViolations = data
    ? data.hardViolations.length + data.mediumViolations.length + data.softViolations.length
    : 0

  // Optionnel côté API : une réponse produite avant l'ajout de la validation
  // métier n'a pas ce champ, et le panneau doit rester lisible pour ces jobs-là.
  const businessFindings = data?.businessValidation ?? []

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

          {totalViolations === 0 && businessFindings.length === 0 && (
            <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-4">
              Aucun conflit détecté — le planning respecte toutes les contraintes.
            </p>
          )}

          {/* La vérification métier passe avant les contraintes du profil : ce
              qu'elle reproche est vrai quelles que soient les règles activées. */}
          {businessFindings.length > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-2">
                <ShieldAlert size={15} className="text-danger" />
                <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">
                  Vérification métier
                </h3>
                <span className="text-xs text-brand-textMuted dark:text-slate-400">
                  (indépendante des règles activées)
                </span>
              </div>
              <div className="space-y-2">
                {groupFindings(businessFindings).map(([code, findings]) => (
                  <BusinessCard key={code} code={code} findings={findings} />
                ))}
              </div>
            </div>
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
                    <ViolationCard
                      key={v.constraintName}
                      v={v}
                      tone={tone}
                      jobId={data.jobId}
                      onNavigate={montrer}
                    />
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
