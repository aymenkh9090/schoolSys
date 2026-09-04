import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ShieldCheck } from 'lucide-react'

import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import {
  planningApi,
  type ConstraintDsl,
  type CustomConstraint,
  type DslAnalysis,
} from '@/api/planning.api'
import { DslRuleBuilder } from './DslRuleBuilder'
import { RuleAnalysisPanel } from './RuleAnalysisPanel'
import { emptyRule, slugifyCode } from './dslLabels'

interface Props {
  open: boolean
  onClose: () => void
  profileId: number
  schoolYearId?: number
  /** Règle à modifier ; absent = création. */
  editing?: CustomConstraint | null
  /** Règle pré-remplie (venue d'une suggestion de l'assistant). */
  initialRule?: ConstraintDsl | null
  initialName?: string
  initialCode?: string
}

/**
 * Création et modification d'une règle personnalisée.
 *
 * Le parcours impose une vérification avant l'enregistrement : le bouton
 * « Enregistrer » ne s'active qu'après une analyse réussie côté backend. Ce
 * n'est pas une contrainte gratuite — c'est ce qui évite qu'une règle HARD mal
 * calibrée rende le planning de tout l'établissement infaisable, un symptôme
 * qui n'apparaîtrait sinon qu'à la prochaine génération.
 */
export function CustomConstraintModal({
  open,
  onClose,
  profileId,
  schoolYearId,
  editing,
  initialRule,
  initialName,
  initialCode,
}: Props) {
  const qc = useQueryClient()

  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [codeTouched, setCodeTouched] = useState(false)
  const [description, setDescription] = useState('')
  const [rule, setRule] = useState<ConstraintDsl>(emptyRule())
  const [analysis, setAnalysis] = useState<DslAnalysis | undefined>()

  const { data: schema, isLoading: loadingSchema } = useQuery({
    queryKey: ['dsl-schema'],
    queryFn: planningApi.constraints.custom.schema,
    // Le catalogue ne change qu'à un déploiement du backend : inutile de le
    // recharger à chaque ouverture de la fenêtre.
    staleTime: 10 * 60 * 1000,
    enabled: open,
  })

  useEffect(() => {
    if (!open) return
    setName(editing?.name ?? initialName ?? '')
    setCode(editing?.code ?? initialCode ?? '')
    setCodeTouched(Boolean(editing || initialCode))
    setDescription(editing?.description ?? '')
    setRule(editing?.dsl ?? initialRule ?? emptyRule())
    setAnalysis(undefined)
  }, [open, editing, initialRule, initialName, initialCode])

  // Toute modification de la règle invalide l'analyse précédente : afficher un
  // verdict qui ne correspond plus à ce qui est à l'écran serait pire que de
  // n'en afficher aucun.
  const changeRule = (next: ConstraintDsl) => {
    setRule(next)
    setAnalysis(undefined)
  }

  const analyzeMutation = useMutation({
    mutationFn: () =>
      planningApi.constraints.custom.analyze({
        dsl: rule,
        schoolYearId,
        constraintProfileId: profileId,
      }),
    onSuccess: setAnalysis,
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Vérification impossible'),
  })

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        constraintProfileId: profileId,
        code: slugifyCode(code),
        name,
        description: description || undefined,
        dsl: rule,
      }
      return editing
        ? planningApi.constraints.custom.update(editing.idCustomConstraint, payload)
        : planningApi.constraints.custom.create(payload)
    },
    onSuccess: () => {
      toast.success(editing ? 'Règle mise à jour' : 'Règle enregistrée')
      qc.invalidateQueries({ queryKey: ['custom-constraints'] })
      onClose()
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Enregistrement impossible'),
  })

  const canSave = Boolean(name.trim() && code.trim() && analysis?.valid)

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? `Modifier « ${editing.name} »` : 'Créer une règle'}
      size="xl"
    >
      <div className="space-y-5">
        <div className="grid gap-3 sm:grid-cols-2">
          <Input
            label="Nom de la règle *"
            value={name}
            placeholder="Pas de maths tard le vendredi"
            onChange={(e) => {
              const next = e.currentTarget.value
              setName(next)
              // Le code suit le nom tant que l'utilisateur ne l'a pas repris
              // en main : un identifiant technique n'a pas à être saisi deux fois.
              if (!codeTouched) setCode(slugifyCode(next))
            }}
          />
          <Input
            label="Motif (facultatif)"
            value={description}
            placeholder="Pourquoi cette règle existe"
            onChange={(e) => setDescription(e.currentTarget.value)}
          />
        </div>

        {/* Le code identifie la règle dans les journaux et les exports. Il est
            dérivé du nom : le proposer à la saisie ferait buter l'utilisateur
            sur une contrainte de format qui ne lui apprend rien. */}
        <details className="rounded-lg border border-brand-border px-3 py-2 dark:border-slate-700">
          <summary className="cursor-pointer text-xs font-medium text-brand-textMuted dark:text-slate-400">
            Identifiant de la règle : <span className="font-mono">{slugifyCode(code) || '—'}</span>
          </summary>
          <div className="mt-3 max-w-xs">
            <Input
              label="Identifiant"
              value={code}
              placeholder="PAS_DE_MATHS_TARD"
              onChange={(e) => {
                setCodeTouched(true)
                setCode(e.currentTarget.value.toUpperCase())
              }}
            />
            <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">
              Généré à partir du nom. Il sert à retrouver la règle dans les journaux et les
              exports — inutile d’y toucher.
            </p>
          </div>
        </details>

        {loadingSchema && (
          <p className="text-sm text-brand-textMuted dark:text-slate-400">Chargement du catalogue…</p>
        )}

        {schema && <DslRuleBuilder schema={schema} value={rule} onChange={changeRule} />}

        <section className="border-t border-brand-border pt-4 dark:border-slate-700">
          <div className="mb-2 flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0 flex-1">
              <p className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                <ShieldCheck size={15} className="text-brand-blue" />
                Dernière étape : l’essai
              </p>
              <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
                Obligatoire avant d’enregistrer. Rien n’est modifié : on regarde seulement
                combien de séances seraient touchées.
              </p>
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={() => analyzeMutation.mutate()}
              loading={analyzeMutation.isPending}
            >
              Essayer la règle
            </Button>
          </div>
          <RuleAnalysisPanel
            analysis={analysis}
            loading={analyzeMutation.isPending}
            idleMessage="Cliquez sur « Essayer la règle » : elle sera confrontée aux cours réels de l’année, sans rien enregistrer."
          />
        </section>

        <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
          <Button variant="outline" type="button" onClick={onClose}>
            Annuler
          </Button>
          <Button
            onClick={() => saveMutation.mutate()}
            loading={saveMutation.isPending}
            disabled={!canSave}
            title={canSave ? undefined : 'Essayez la règle avant de l’enregistrer'}
          >
            {editing ? 'Enregistrer les modifications' : 'Enregistrer la règle'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
