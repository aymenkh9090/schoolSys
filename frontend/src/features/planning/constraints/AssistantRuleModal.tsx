import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Loader2, Sparkles, Wand2 } from 'lucide-react'

import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { planningAssistantApi, type ConstraintProposal } from '@/api/aiAssistant.api'
import type { DslAnalysis } from '@/api/planning.api'
import { RuleAnalysisPanel } from './RuleAnalysisPanel'
import { slugifyCode } from './dslLabels'

interface Props {
  open: boolean
  onClose: () => void
  profileId: number
  schoolYearId?: number
  /** Ouvre le formulaire sur la règle proposée, pour la modifier avant d'enregistrer. */
  onEditManually: (proposal: ConstraintProposal, name: string) => void
}

// Exemples cliquables. Avec un modèle de 7B tournant sur CPU, montrer la forme
// attendue d'une demande change radicalement le taux de réussite — et évite à
// l'utilisateur de conclure que la fonction ne marche pas après un essai flou.
const EXAMPLES = [
  'Les classes de terminale ne doivent pas avoir de mathématiques après 15h le vendredi.',
  'Aucun professeur ne doit avoir plus de 3 heures consécutives.',
  'Pas de sport le lundi matin.',
  'De préférence, les TP de physique le matin.',
]

/**
 * Écrire une règle en français, la faire traduire, puis la confirmer.
 *
 * Le parcours en deux temps est le cœur du contrat avec l'utilisateur :
 * l'assistant PROPOSE (rien n'est écrit), l'utilisateur CONFIRME (le backend
 * revalide et enregistre). Aucun enchaînement automatique entre les deux — une
 * règle ne peut pas entrer en base sans avoir été affichée puis acceptée.
 *
 * Ce qui est montré est le résumé produit par le compilateur Java, pas une
 * phrase du modèle : l'utilisateur relit donc ce que le moteur appliquera
 * réellement, et non ce que l'IA croit avoir compris.
 */
export function AssistantRuleModal({ open, onClose, profileId, schoolYearId, onEditManually }: Props) {
  const qc = useQueryClient()

  const [request, setRequest] = useState('')
  const [proposal, setProposal] = useState<ConstraintProposal | null>(null)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')

  useEffect(() => {
    if (open) return
    setRequest('')
    setProposal(null)
    setName('')
    setCode('')
  }, [open])

  const proposeMutation = useMutation({
    mutationFn: (text: string) =>
      planningAssistantApi.propose(text, { schoolYearId, profileId }),
    onSuccess: (result) => {
      setProposal(result)
      if (result.valid) {
        // Nom par défaut dérivé de la demande : l'utilisateur le corrige s'il veut,
        // mais il n'a pas à inventer un intitulé pour une règle qu'il vient d'écrire.
        const suggested = defaultName(request)
        setName(suggested)
        setCode(slugifyCode(suggested))
      }
    },
    onError: () =>
      toast.error("L'assistant est injoignable. Vérifiez que le service IA est démarré."),
  })

  const confirmMutation = useMutation({
    mutationFn: () =>
      planningAssistantApi.confirm({
        constraint_profile_id: profileId,
        code: slugifyCode(code),
        name,
        dsl: proposal!.dsl!,
        natural_language_request: request,
      }),
    onSuccess: () => {
      toast.success('Règle enregistrée')
      qc.invalidateQueries({ queryKey: ['custom-constraints'] })
      onClose()
    },
    onError: (e: { response?: { data?: { detail?: string } } }) =>
      toast.error(e.response?.data?.detail ?? 'Enregistrement impossible'),
  })

  // La proposition porte déjà le verdict complet du backend : on le réaffiche
  // avec le même composant que le formulaire manuel, sans nouvel appel.
  const analysis: DslAnalysis | undefined = proposal
    ? {
        valid: proposal.valid,
        errors: proposal.errors,
        warnings: proposal.warnings,
        summary: proposal.summary ?? undefined,
        verdict: proposal.verdict ?? undefined,
        matchedLessons: proposal.matched_lessons ?? undefined,
        totalLessons: proposal.total_lessons ?? undefined,
        examples: proposal.examples,
        feasible: proposal.feasible,
        conflicts: proposal.conflicts,
      }
    : undefined

  const canConfirm = Boolean(proposal?.valid && proposal.dsl && name.trim() && code.trim())

  return (
    <Modal open={open} onClose={onClose} title="Décrire la contrainte" size="xl">
      <div className="space-y-5">
        <div className="flex items-start gap-2.5 rounded-lg border border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40">
          <Sparkles size={16} className="mt-0.5 shrink-0 text-brand-blue" />
          <p className="text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
            Écrivez la contrainte comme vous la diriez à un collègue. Elle est ensuite mise en
            forme, vérifiée, et son effet sur les cours de l’année est mesuré.{' '}
            <strong className="text-brand-text dark:text-slate-200">
              Rien n’est enregistré tant que vous n’avez pas confirmé.
            </strong>
          </p>
        </div>

        <div>
          <label
            htmlFor="assistant-request"
            className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200"
          >
            Votre contrainte
          </label>
          <textarea
            id="assistant-request"
            value={request}
            onChange={(e) => setRequest(e.target.value)}
            rows={3}
            maxLength={500}
            placeholder="Les classes de terminale ne doivent pas avoir de mathématiques après 15h le vendredi."
            className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text placeholder:text-brand-textMuted focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:placeholder:text-slate-500"
          />

          <div className="mt-2 flex flex-wrap gap-1.5">
            {EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => setRequest(example)}
                disabled={proposeMutation.isPending}
                className="rounded-full border border-brand-border px-2.5 py-1 text-left text-xs text-brand-textMuted transition-colors hover:bg-brand-bgSecondary disabled:opacity-50 dark:border-slate-700 dark:text-slate-400 dark:hover:bg-slate-800"
              >
                {example}
              </button>
            ))}
          </div>
        </div>

        <Button
          onClick={() => proposeMutation.mutate(request.trim())}
          loading={proposeMutation.isPending}
          disabled={!request.trim() || proposeMutation.isPending}
          className="w-full"
        >
          <Wand2 size={16} /> Voir ce que ça donne
        </Button>

        {/* Le modèle tourne sur CPU : sans indicateur explicite, l'attente
            de 5 à 30 s passe pour une panne. */}
        {proposeMutation.isPending && (
          <div className="flex items-center justify-center gap-2 py-2 text-sm text-brand-textMuted dark:text-slate-400">
            <Loader2 size={15} className="animate-spin" />
            Mise en forme de la contrainte, puis vérification…
          </div>
        )}

        {proposal && (
          <section className="space-y-4 border-t border-brand-border pt-4 dark:border-slate-700">
            <RuleAnalysisPanel analysis={analysis} />

            {!proposal.valid && proposal.message && (
              <p className="text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
                {proposal.message}
              </p>
            )}

            {proposal.valid && proposal.dsl && (
              <>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Input
                    label="Nom de la règle *"
                    value={name}
                    onChange={(e) => setName(e.currentTarget.value)}
                  />
                  <div>
                    <Input
                      label="Identifiant"
                      value={code}
                      onChange={(e) => setCode(e.currentTarget.value.toUpperCase())}
                    />
                    <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">
                      Généré à partir du nom — inutile d’y toucher.
                    </p>
                  </div>
                </div>

                {/* Traçabilité : la règle exacte reste consultable avant l'accord.
                    Repliée par défaut — c'est une pièce justificative pour qui la
                    demande, pas une étape du parcours. */}
                <details className="rounded-lg border border-brand-border p-2.5 dark:border-slate-700">
                  <summary className="cursor-pointer text-xs font-medium text-brand-textMuted dark:text-slate-400">
                    Détail informatique (réservé au support)
                  </summary>
                  <pre className="mt-2 overflow-x-auto rounded bg-brand-bgSecondary p-2 text-[11px] leading-relaxed text-brand-text dark:bg-slate-800 dark:text-slate-300">
                    {JSON.stringify(proposal.dsl, null, 2)}
                  </pre>
                </details>
              </>
            )}
          </section>
        )}

        <div className="flex flex-wrap justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
          <Button variant="outline" type="button" onClick={onClose}>
            Annuler
          </Button>
          {proposal?.valid && proposal.dsl && (
            <Button variant="outline" type="button" onClick={() => onEditManually(proposal, name)}>
              Modifier avant d’enregistrer
            </Button>
          )}
          <Button
            onClick={() => confirmMutation.mutate()}
            loading={confirmMutation.isPending}
            disabled={!canConfirm}
          >
            Confirmer et enregistrer
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/** Intitulé court dérivé de la demande, tronqué sur un mot entier. */
function defaultName(request: string): string {
  const trimmed = request.trim().replace(/[.!?]+$/, '')
  if (trimmed.length <= 60) return trimmed
  const cut = trimmed.slice(0, 60)
  return cut.slice(0, cut.lastIndexOf(' ')) || cut
}
