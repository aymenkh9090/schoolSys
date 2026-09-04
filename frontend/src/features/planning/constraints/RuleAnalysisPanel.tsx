import { AlertTriangle, CheckCircle2, Info, Loader2, XCircle } from 'lucide-react'

import type { DslAnalysis } from '@/api/planning.api'
import { cn } from '@/lib/utils'

interface Props {
  analysis?: DslAnalysis
  loading?: boolean
  /** Affiché tant qu'aucune analyse n'a été demandée. */
  idleMessage?: string
}

/**
 * Verdict du backend sur une règle candidate : validité, impact chiffré,
 * conflits avec les données réelles.
 *
 * Ce panneau est le même pour une règle saisie à la main et pour une règle
 * proposée par l'assistant. C'est délibéré : une règle venue de l'IA ne
 * bénéficie d'aucun raccourci, elle passe par le même contrôle et le même écran
 * que les autres.
 *
 * Le résumé affiché est produit par le compilateur Java — c'est la règle telle
 * que le moteur l'appliquera, pas une reformulation.
 */
export function RuleAnalysisPanel({ analysis, loading, idleMessage }: Props) {
  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 rounded-lg border border-brand-border py-6 text-sm text-brand-textMuted dark:border-slate-700 dark:text-slate-400">
        <Loader2 size={16} className="animate-spin" />
        Vérification de la règle…
      </div>
    )
  }

  if (!analysis) {
    return (
      <p className="rounded-lg border border-dashed border-brand-border px-3 py-5 text-center text-xs text-brand-textMuted dark:border-slate-700 dark:text-slate-400">
        {idleMessage ?? 'La règle sera vérifiée avant l’enregistrement.'}
      </p>
    )
  }

  // Trois états distincts, et non deux : une règle peut être parfaitement
  // valide ET irréalisable avec les affectations actuelles. Les confondre
  // enverrait l'utilisateur corriger une syntaxe qui n'a rien de faux.
  const blocked = analysis.valid && !analysis.feasible

  return (
    <div className="space-y-3">
      <div
        className={cn(
          'flex items-start gap-2.5 rounded-lg border p-3',
          !analysis.valid && 'border-red-200 bg-red-50/60 dark:border-red-500/20 dark:bg-red-500/5',
          blocked && 'border-amber-200 bg-amber-50/60 dark:border-amber-500/20 dark:bg-amber-500/5',
          analysis.valid &&
            analysis.feasible &&
            'border-emerald-200 bg-emerald-50/60 dark:border-emerald-500/20 dark:bg-emerald-500/5'
        )}
      >
        {!analysis.valid ? (
          <XCircle size={16} className="mt-0.5 shrink-0 text-danger" />
        ) : blocked ? (
          <AlertTriangle size={16} className="mt-0.5 shrink-0 text-amber-500" />
        ) : (
          <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" />
        )}

        <div className="min-w-0 flex-1">
          {analysis.summary && (
            <p className="text-sm font-medium leading-relaxed text-brand-text dark:text-slate-100">
              {analysis.summary}
            </p>
          )}
          {analysis.verdict && (
            <p className="mt-1 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
              {analysis.verdict}
            </p>
          )}
        </div>
      </div>

      {/* Erreurs de validation — la règle ne peut pas être enregistrée. */}
      {analysis.errors.length > 0 && (
        <ul className="space-y-1">
          {analysis.errors.map((error, i) => (
            <li key={i} className="flex items-start gap-2 text-xs text-danger">
              <XCircle size={12} className="mt-0.5 shrink-0" />
              {error}
            </li>
          ))}
        </ul>
      )}

      {analysis.warnings.length > 0 && (
        <ul className="space-y-1">
          {analysis.warnings.map((warning, i) => (
            <li key={i} className="flex items-start gap-2 text-xs text-amber-600 dark:text-amber-400">
              <AlertTriangle size={12} className="mt-0.5 shrink-0" />
              {warning}
            </li>
          ))}
        </ul>
      )}

      {/* Conflits : la règle est correcte mais irréalisable. */}
      {analysis.conflicts.length > 0 && (
        <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3 dark:border-amber-500/20 dark:bg-amber-500/5">
          <p className="mb-2 text-xs font-semibold text-brand-text dark:text-slate-100">
            Conflit avec les affectations actuelles ({analysis.conflicts.length})
          </p>
          <ul className="space-y-2">
            {analysis.conflicts.map((conflict, i) => (
              <li key={i} className="text-xs leading-relaxed">
                <span className="font-medium text-brand-text dark:text-slate-200">{conflict.subject}</span>
                <span className="text-brand-textMuted dark:text-slate-400"> — {conflict.detail}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Impact mesuré, en français. Une règle est une décision : on ne la
          prend pas sur un ratio brut, on la prend sur une phrase qui dit
          combien de cours de l'année elle déplace. */}
      {analysis.valid && analysis.matchedLessons !== undefined && analysis.matchedLessons !== null && (
        <div
          className={cn(
            'rounded-lg border p-3',
            analysis.matchedLessons === 0
              ? 'border-amber-200 bg-amber-50/60 dark:border-amber-500/20 dark:bg-amber-500/5'
              : 'border-brand-border dark:border-slate-700'
          )}
        >
          <p className="flex items-start gap-2 text-xs leading-relaxed text-brand-text dark:text-slate-200">
            <Info size={13} className="mt-0.5 shrink-0 text-brand-textMuted dark:text-slate-400" />
            <span>{phraseImpact(analysis)}</span>
          </p>

          {analysis.examples && analysis.examples.length > 0 && (
            <>
              <p className="mt-2 text-xs text-brand-textMuted dark:text-slate-400">Par exemple :</p>
              <ul className="mt-1 space-y-1">
                {analysis.examples.map((example, i) => (
                  <li
                    key={i}
                    className="border-l-2 border-brand-border pl-2.5 text-xs text-brand-textMuted dark:border-slate-700 dark:text-slate-400"
                  >
                    {example}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * L'impact d'une règle, dit comme on le dirait à l'oral.
 *
 * Trois cas, et le premier est le plus utile : une règle qui ne touche aucune
 * séance est syntaxiquement valide et parfaitement inutile. Affichée en
 * « 0 sur 310 », l'information se lit comme un simple chiffre bas ; formulée,
 * elle devient un avertissement — presque toujours le signe d'un critère trop
 * étroit (une matière mal orthographiée, un niveau qui n'existe pas cette
 * année) plutôt que d'une année réellement sans cas.
 *
 * Le pourcentage ne remplace pas les effectifs, il les accompagne : « 42 sur
 * 310 » dit l'ampleur du travail, « 13 % » dit la part de l'année. Les deux
 * ensemble suffisent à décider, séparément aucun ne le fait.
 */
function phraseImpact(analysis: DslAnalysis): string {
  const touchees = analysis.matchedLessons ?? 0
  const total = analysis.totalLessons ?? 0

  if (touchees === 0) {
    return total > 0
      ? `Aucune des ${total} séances de l’année n’est concernée : la règle serait sans effet. Vérifiez la matière, le niveau ou le jour visés.`
      : 'Aucune séance de l’année n’est concernée : la règle serait sans effet.'
  }

  const seances = `${touchees} séance${touchees > 1 ? 's' : ''}`
  if (total <= 0) return `Cette règle concerne ${seances}.`

  return `Cette règle concerne ${seances} sur ${total} (${pourcentage(touchees, total)}).`
}

/**
 * Un pourcentage arrondi, sauf sous le point de bascule : « 0 % » pour une
 * règle qui touche bel et bien des séances serait faux au sens où l'utilisateur
 * le lirait — il en conclurait qu'elle ne sert à rien.
 */
function pourcentage(part: number, total: number): string {
  const ratio = (part / total) * 100
  if (ratio > 0 && ratio < 1) return '<\u20091\u2009%'
  return `${Math.round(ratio)}\u2009%`
}
