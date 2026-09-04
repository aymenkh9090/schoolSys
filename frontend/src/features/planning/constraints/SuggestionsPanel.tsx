import { useQuery } from '@tanstack/react-query'
import { Lightbulb, Loader2, Plus, ThumbsUp } from 'lucide-react'

import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { planningApi, type ConstraintDsl, type ConstraintSuggestion } from '@/api/planning.api'

interface Props {
  schoolYearId?: number
  /** Ouvre l'éditeur pré-rempli avec la règle proposée, pour relecture. */
  onAdopt: (suggestion: ConstraintSuggestion, dsl: ConstraintDsl) => void
}

/**
 * Propositions déduites du dernier emploi du temps généré.
 *
 * L'analyse est statistique et faite côté serveur : séries d'heures
 * consécutives, journées surchargées, semaines sans repos. Chaque proposition
 * arrive avec les cas observés qui la motivent — c'est ce qui permet de trancher
 * en connaissance de cause plutôt que d'accepter un conseil sur parole.
 *
 * Rien n'est créé ici : adopter une suggestion ouvre l'éditeur, où la règle est
 * relue puis vérifiée comme n'importe quelle autre.
 */
export function SuggestionsPanel({ schoolYearId, onAdopt }: Props) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['constraint-suggestions', schoolYearId],
    queryFn: () => planningApi.constraints.custom.suggestions({ schoolYearId }),
    retry: false,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-2 rounded-xl border border-brand-border bg-white py-10 text-sm text-brand-textMuted dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
        <Loader2 size={16} className="animate-spin" />
        Analyse du dernier emploi du temps…
      </div>
    )
  }

  // 404 attendu tant qu'aucun planning n'a été généré : ce n'est pas une panne,
  // et le dire évite un message d'erreur rouge sur un état parfaitement normal.
  if (isError) {
    const message =
      (error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
      'Analyse indisponible.'
    return (
      <div className="rounded-xl border border-brand-border bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-900">
        <Lightbulb size={28} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-500" />
        <p className="text-sm text-brand-textMuted dark:text-slate-400">{message}</p>
        <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-500">
          Générez un emploi du temps : les suggestions s’appuient sur un planning réel.
        </p>
      </div>
    )
  }

  const suggestions = data?.suggestions ?? []

  // Ne rien proposer est une réponse valide : sous le seuil d'occurrences, le
  // serveur se tait plutôt que d'inventer un conseil de complaisance.
  if (suggestions.length === 0) {
    return (
      <div className="rounded-xl border border-brand-border bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-900">
        <ThumbsUp size={28} className="mx-auto mb-3 text-emerald-500" />
        <p className="text-sm font-medium text-brand-text dark:text-slate-200">
          Aucun problème récurrent détecté
        </p>
        <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">
          L’emploi du temps #{data?.jobId} ne présente pas d’anomalie justifiant une nouvelle règle.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-brand-textMuted dark:text-slate-400">
        Analyse de l’emploi du temps #{data?.jobId} — {suggestions.length} proposition
        {suggestions.length > 1 ? 's' : ''}. Rien n’est créé sans votre accord.
      </p>

      {suggestions.map((suggestion) => (
        <article
          key={suggestion.code}
          className="rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900"
        >
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-start gap-2.5">
              <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-amber-50 dark:bg-amber-500/10">
                <Lightbulb size={16} className="text-amber-600 dark:text-amber-400" />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-brand-text dark:text-slate-100">
                  {suggestion.title}
                </p>
                <p className="mt-0.5 text-xs leading-relaxed text-brand-text dark:text-slate-300">
                  {suggestion.observation}
                </p>
              </div>
            </div>
            <Badge variant="warning" className="shrink-0">
              {suggestion.occurrences} cas
            </Badge>
          </div>

          <p className="mt-2.5 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
            {suggestion.rationale}
          </p>

          {suggestion.evidence.length > 0 && (
            <ul className="mt-2.5 space-y-1">
              {suggestion.evidence.map((item, i) => (
                <li
                  key={i}
                  className="border-l-2 border-brand-border pl-2.5 text-xs text-brand-textMuted dark:border-slate-700 dark:text-slate-400"
                >
                  {item}
                </li>
              ))}
            </ul>
          )}

          <div className="mt-3 flex justify-end">
            {suggestion.dsl ? (
              <Button size="sm" variant="outline" onClick={() => onAdopt(suggestion, suggestion.dsl!)}>
                <Plus size={13} /> Créer cette règle
              </Button>
            ) : (
              // Certaines suggestions pointent vers une contrainte du catalogue :
              // dupliquer la même intention en règle personnalisée créerait deux
              // réglages concurrents pour un seul comportement attendu.
              <span className="text-xs italic text-brand-textMuted dark:text-slate-500">
                À activer dans l’onglet Catalogue
              </span>
            )}
          </div>
        </article>
      ))}
    </div>
  )
}
