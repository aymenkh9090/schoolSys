import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, AlertCircle, Info, Loader2, Lightbulb, ShieldAlert } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { planningApi, type BusinessFinding, type ConstraintViolation } from '@/api/planning.api'
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

/**
 * Libellés des contrôles métier. Le backend renvoie un code stable ; on ne
 * l'affiche pas tel quel — « SEANCE_NON_PLACEE » ne veut rien dire pour un
 * directeur d'établissement. Un code inconnu retombe sur lui-même plutôt que de
 * disparaître : un contrôle ajouté côté serveur doit rester visible ici avant
 * même qu'on lui ait écrit un libellé.
 */
const BUSINESS_LABELS: Record<string, string> = {
  SEANCE_NON_PLACEE: 'Séances jamais placées',
  SEANCE_DUPLIQUEE: 'Séances comptées deux fois',
  ENSEIGNANT_MANQUANT: 'Séances sans enseignant',
  DEMI_GROUPE_DESAPPARIE: 'Demi-groupes désappariés',
  VOLUME_HORAIRE: 'Volume horaire hors programme officiel',
  VOLUME_NON_VERIFIABLE: 'Volume officiel absent des données',
  CONFLIT_ENSEIGNANT: 'Enseignant attendu à deux endroits',
  CONFLIT_CLASSE: 'Classe à deux cours en même temps',
  CONFLIT_SALLE: 'Salle occupée deux fois',
  ENSEIGNANT_INDISPONIBLE: 'Cours un jour d\'indisponibilité',
  CAPACITE_SALLE: 'Salle trop petite',
  SALLE_INADAPTEE: 'Salle spécialisée manquante',
  COURS_PENDANT_LA_PAUSE: 'Cours sur la pause méridienne',
  SEANCE_DEBORDANTE: 'Séance débordant de la demi-journée',
  RAPPORT_ARCHIVE: 'Rapport archivé de la génération',
}

/** Nombre d'exemples cités par famille — au-delà, on annonce le reste. */
const BUSINESS_EXAMPLES = 3

/**
 * Regroupe les constats par code, en conservant l'ordre d'arrivée : le backend
 * les émet contrôle par contrôle, et cet ordre est celui du diagnostic.
 */
function groupFindings(findings: BusinessFinding[]) {
  const groups = new Map<string, BusinessFinding[]>()
  for (const f of findings) {
    const existing = groups.get(f.code)
    if (existing) existing.push(f)
    else groups.set(f.code, [f])
  }
  return [...groups.entries()]
}

function BusinessCard({ code, findings }: { code: string; findings: BusinessFinding[] }) {
  const blocking = findings[0].severity === 'BLOQUANT'
  const tone = blocking ? 'danger' : 'warning'
  const rest = findings.length - BUSINESS_EXAMPLES
  return (
    <div className={cn('rounded-lg border p-3', TONE_CLASSES[tone])}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-brand-text dark:text-slate-100">
          {BUSINESS_LABELS[code] ?? code}
        </p>
        <Badge variant={tone} className="shrink-0">
          {findings.length} {blocking ? 'bloquant(s)' : 'à vérifier'}
        </Badge>
      </div>
      <ul className="mt-2 space-y-1">
        {findings.slice(0, BUSINESS_EXAMPLES).map((f, i) => (
          <li key={i} className="text-xs text-brand-textMuted dark:text-slate-400 pl-3 border-l-2 border-brand-border dark:border-slate-700">
            {f.scope ? <strong className="font-medium">{f.scope}</strong> : null}
            {f.scope ? ' — ' : ''}{f.message}
          </li>
        ))}
      </ul>
      {rest > 0 && (
        <p className="mt-1.5 text-xs text-brand-textMuted dark:text-slate-500">
          … et {rest} autre{rest > 1 ? 's' : ''}
        </p>
      )}
    </div>
  )
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
