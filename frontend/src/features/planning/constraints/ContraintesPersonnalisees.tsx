import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Loader2, Pencil, Plus, ScrollText, Sparkles, Trash2 } from 'lucide-react'

import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { planningApi, type ConstraintDsl, type CustomConstraint } from '@/api/planning.api'
import type { ConstraintProposal } from '@/api/aiAssistant.api'
import { cn } from '@/lib/utils'
import { AssistantRuleModal } from './AssistantRuleModal'
import { CustomConstraintModal } from './CustomConstraintModal'
import { SEVERITY_LABELS, SEVERITY_VARIANTS, SOURCE_LABELS, slugifyCode } from './dslLabels'

interface Props {
  profileId: number
  schoolYearId?: number
}

/** Interrupteur accessible — même composant visuel que l'onglet Catalogue. */
function Switch({ checked, onChange, label }: { checked: boolean; onChange: () => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={onChange}
      className={cn(
        'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue focus-visible:ring-offset-2',
        'dark:focus-visible:ring-offset-slate-900',
        checked ? 'bg-brand-blue' : 'bg-slate-300 dark:bg-slate-700'
      )}
    >
      <span
        className={cn(
          'inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow-sm transition-transform',
          checked ? 'translate-x-[1.15rem]' : 'translate-x-1'
        )}
      />
    </button>
  )
}

/**
 * Règles propres à l'établissement, celles qui n'existent pas au catalogue.
 *
 * Deux façons d'en créer une, qui aboutissent au même endroit : le formulaire
 * visuel, ou une phrase confiée à l'assistant. Dans les deux cas la règle est
 * vérifiée par le serveur avant d'être enregistrée — l'IA ne dispose d'aucun
 * raccourci que l'interface n'aurait pas.
 */
export function ContraintesPersonnalisees({ profileId, schoolYearId }: Props) {
  const qc = useQueryClient()

  const [editorOpen, setEditorOpen] = useState(false)
  const [assistantOpen, setAssistantOpen] = useState(false)
  const [editing, setEditing] = useState<CustomConstraint | null>(null)
  const [seedRule, setSeedRule] = useState<ConstraintDsl | null>(null)
  const [seedName, setSeedName] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<CustomConstraint | null>(null)

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ['custom-constraints', profileId],
    queryFn: () => planningApi.constraints.custom.list(profileId),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      planningApi.constraints.custom.setEnabled(id, enabled),
    onSuccess: () => {
      toast.success('Règle mise à jour')
      qc.invalidateQueries({ queryKey: ['custom-constraints'] })
    },
    onError: () => toast.error('Mise à jour impossible'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => planningApi.constraints.custom.delete(id),
    onSuccess: () => {
      toast.success('Règle supprimée')
      qc.invalidateQueries({ queryKey: ['custom-constraints'] })
      setDeleteTarget(null)
    },
    onError: () => toast.error('Suppression impossible'),
  })

  const openCreate = () => {
    setEditing(null)
    setSeedRule(null)
    setSeedName('')
    setEditorOpen(true)
  }

  const openEdit = (rule: CustomConstraint) => {
    setEditing(rule)
    setSeedRule(null)
    setEditorOpen(true)
  }

  /** Passage de l'assistant à l'éditeur manuel, en conservant la règle proposée. */
  const editProposal = (proposal: ConstraintProposal, name: string) => {
    setAssistantOpen(false)
    setEditing(null)
    setSeedRule(proposal.dsl)
    setSeedName(name)
    setEditorOpen(true)
  }

  const enabledCount = rules.filter((r) => r.enabled).length

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-brand-text dark:text-slate-100">
            Règles propres à votre établissement
          </p>
          <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
            {rules.length === 0
              ? 'Aucune règle personnalisée pour l’instant.'
              : `${enabledCount} active${enabledCount > 1 ? 's' : ''} sur ${rules.length}.`}
          </p>
          {/* Deux portes d'entrée pour la même chose : dire laquelle choisir
              évite d'avoir à essayer les deux pour comprendre. */}
          <p className="mt-1 max-w-xl text-xs leading-relaxed text-brand-textMuted dark:text-slate-500">
            Décrivez la contrainte en une phrase, ou construisez-la pas à pas en répondant à
            quelques questions. Dans les deux cas, vous la relisez et l’essayez avant qu’elle
            ne compte.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setAssistantOpen(true)}>
            <Sparkles size={16} /> Décrire la contrainte
          </Button>
          <Button onClick={openCreate}>
            <Plus size={16} /> Construire pas à pas
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center gap-2 rounded-xl border border-brand-border bg-white py-10 text-sm text-brand-textMuted dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
          <Loader2 size={16} className="animate-spin" /> Chargement des règles…
        </div>
      ) : rules.length === 0 ? (
        <div className="rounded-xl border border-dashed border-brand-border bg-white p-10 text-center dark:border-slate-700 dark:bg-slate-900">
          <ScrollText size={30} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-500" />
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            Une règle que le catalogue ne couvre pas ?
          </p>
          <p className="mx-auto mt-1 max-w-md text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
            Écrivez-la en une phrase — « les classes de terminale ne doivent pas avoir de
            mathématiques après 15h le vendredi » — ou construisez-la pas à pas en répondant à
            quelques questions.
          </p>
          <div className="mt-4 flex justify-center gap-2">
            <Button variant="outline" onClick={() => setAssistantOpen(true)}>
              <Sparkles size={16} /> Décrire la contrainte
            </Button>
            <Button onClick={openCreate}>
              <Plus size={16} /> Construire pas à pas
            </Button>
          </div>
        </div>
      ) : (
        <ul className="divide-y divide-brand-border overflow-hidden rounded-xl border border-brand-border bg-white dark:divide-slate-700 dark:border-slate-700 dark:bg-slate-900">
          {rules.map((rule) => (
            <li
              key={rule.idCustomConstraint}
              className={cn(
                'flex items-start gap-3 px-4 py-3 transition-colors',
                'hover:bg-brand-bgSecondary/60 dark:hover:bg-slate-800/40',
                !rule.enabled && 'opacity-55'
              )}
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium text-brand-text dark:text-slate-100">
                    {rule.name}
                  </span>
                  <Badge variant={SEVERITY_VARIANTS[rule.severity]}>
                    {SEVERITY_LABELS[rule.severity]}
                  </Badge>
                  {rule.source !== 'MANUAL' && (
                    <span className="inline-flex items-center gap-1 text-[11px] text-brand-textMuted dark:text-slate-400">
                      <Sparkles size={10} /> {SOURCE_LABELS[rule.source]}
                    </span>
                  )}
                </div>

                {/* Le résumé vient du compilateur : c'est ce que le moteur
                    applique, pas ce que l'utilisateur croit avoir écrit. */}
                {rule.summary && (
                  <p className="mt-1 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
                    {rule.summary}
                  </p>
                )}

                {rule.naturalLanguageRequest && (
                  <p className="mt-1 text-xs italic text-brand-textMuted dark:text-slate-500">
                    Demande d’origine : « {rule.naturalLanguageRequest} »
                  </p>
                )}

                <p className="mt-1 font-mono text-[10px] text-brand-textMuted dark:text-slate-500">
                  {rule.code}
                </p>
              </div>

              <div className="flex shrink-0 items-center gap-2">
                <div className="flex flex-col items-center gap-1">
                  <span className="text-[10px] uppercase tracking-wide text-brand-textMuted dark:text-slate-500">
                    {rule.enabled ? 'Active' : 'Inactive'}
                  </span>
                  <Switch
                    checked={rule.enabled}
                    onChange={() =>
                      toggleMutation.mutate({ id: rule.idCustomConstraint, enabled: !rule.enabled })
                    }
                    label={`${rule.enabled ? 'Désactiver' : 'Activer'} la règle ${rule.name}`}
                  />
                </div>

                <button
                  type="button"
                  onClick={() => openEdit(rule)}
                  aria-label={`Modifier ${rule.name}`}
                  className="rounded-lg p-2 text-brand-textMuted transition-colors hover:bg-brand-bgSecondary hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-800"
                >
                  <Pencil size={15} />
                </button>
                <button
                  type="button"
                  onClick={() => setDeleteTarget(rule)}
                  aria-label={`Supprimer ${rule.name}`}
                  className="rounded-lg p-2 text-brand-textMuted transition-colors hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-950/30"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <CustomConstraintModal
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        profileId={profileId}
        schoolYearId={schoolYearId}
        editing={editing}
        initialRule={seedRule}
        initialName={seedName}
        initialCode={seedName ? slugifyCode(seedName) : undefined}
      />

      <AssistantRuleModal
        open={assistantOpen}
        onClose={() => setAssistantOpen(false)}
        profileId={profileId}
        schoolYearId={schoolYearId}
        onEditManually={editProposal}
      />

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idCustomConstraint)}
        loading={deleteMutation.isPending}
        title="Supprimer la règle"
        message={`Supprimer définitivement « ${deleteTarget?.name} » ? Les prochaines générations n’en tiendront plus compte.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
