import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { subscriptionApi, type SubscriptionOverview } from '@/api/subscription.api'

const renewSchema = z.object({
  billingCycle: z.enum(['MONTHLY', 'YEARLY']),
  price: z.coerce.number().min(0, 'Le prix ne peut pas être négatif').optional(),
})
type RenewValues = z.infer<typeof renewSchema>

const planSchema = z.object({
  plan: z.enum(['FREE', 'STANDARD', 'PREMIUM']),
})
type PlanValues = z.infer<typeof planSchema>

interface Props {
  subscription: SubscriptionOverview | null
  onClose: () => void
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['subscriptions'] })
  qc.invalidateQueries({ queryKey: ['tenants'] })
}

export function ModalGestionAbonnement({ subscription, onClose }: Props) {
  const qc = useQueryClient()
  const [cancelConfirmOpen, setCancelConfirmOpen] = useState(false)
  const tenantId = subscription?.tenantId ?? null

  const renewForm = useForm<z.input<typeof renewSchema>, unknown, RenewValues>({
    resolver: zodResolver(renewSchema),
    values: { billingCycle: subscription?.billingCycle ?? 'MONTHLY', price: undefined },
  })

  const planForm = useForm<z.input<typeof planSchema>, unknown, PlanValues>({
    resolver: zodResolver(planSchema),
    values: { plan: subscription?.plan ?? 'FREE' },
  })

  const renewMutation = useMutation({
    mutationFn: (values: RenewValues) => subscriptionApi.renew(tenantId!, values),
    onSuccess: () => {
      toast.success('Abonnement renouvelé')
      invalidate(qc)
      onClose()
    },
    onError: () => toast.error('Erreur lors du renouvellement'),
  })

  const planMutation = useMutation({
    mutationFn: (values: PlanValues) => subscriptionApi.changePlan(tenantId!, values),
    onSuccess: () => {
      toast.success('Plan mis à jour')
      invalidate(qc)
      onClose()
    },
    onError: () => toast.error('Erreur lors du changement de plan'),
  })

  const cancelMutation = useMutation({
    mutationFn: () => subscriptionApi.cancel(tenantId!),
    onSuccess: () => {
      toast.success('Abonnement annulé, établissement suspendu')
      invalidate(qc)
      setCancelConfirmOpen(false)
      onClose()
    },
    onError: () => toast.error("Erreur lors de l'annulation"),
  })

  return (
    <>
      <Modal
        open={subscription !== null}
        onClose={onClose}
        title={`Abonnement — ${subscription?.tenantName ?? ''}`}
        size="lg"
      >
        <div className="space-y-6">
          <div className="rounded-lg bg-brand-bgSecondary dark:bg-slate-800 p-3 text-sm text-brand-textMuted dark:text-slate-400">
            Statut actuel : <span className="font-medium text-brand-text dark:text-slate-200">{subscription?.status ?? '—'}</span>
            {' · '}
            Échéance : <span className="font-medium text-brand-text dark:text-slate-200">{subscription?.endDate ?? '—'}</span>
          </div>

          <form
            onSubmit={renewForm.handleSubmit((d) => renewMutation.mutate(d))}
            className="space-y-3 border-t border-brand-border dark:border-slate-700 pt-4"
          >
            <p className="text-xs font-semibold uppercase text-brand-textMuted">Renouveler</p>
            <div className="grid grid-cols-2 gap-4">
              <Select
                label="Cycle de facturation"
                error={renewForm.formState.errors.billingCycle?.message}
                options={[
                  { value: 'MONTHLY', label: 'Mensuel' },
                  { value: 'YEARLY', label: 'Annuel' },
                ]}
                {...renewForm.register('billingCycle')}
              />
              <Input
                label="Prix (optionnel)"
                type="number"
                min={0}
                step="0.01"
                error={renewForm.formState.errors.price?.message}
                {...renewForm.register('price')}
              />
            </div>
            <div className="flex justify-end">
              <Button type="submit" size="sm" loading={renewMutation.isPending}>
                Renouveler
              </Button>
            </div>
          </form>

          <form
            onSubmit={planForm.handleSubmit((d) => planMutation.mutate(d))}
            className="space-y-3 border-t border-brand-border dark:border-slate-700 pt-4"
          >
            <p className="text-xs font-semibold uppercase text-brand-textMuted">Changer de plan</p>
            <Select
              label="Plan"
              error={planForm.formState.errors.plan?.message}
              options={[
                { value: 'FREE', label: 'Gratuit' },
                { value: 'STANDARD', label: 'Standard' },
                { value: 'PREMIUM', label: 'Premium' },
              ]}
              {...planForm.register('plan')}
            />
            <div className="flex justify-end">
              <Button type="submit" size="sm" variant="secondary" loading={planMutation.isPending}>
                Changer le plan
              </Button>
            </div>
          </form>

          <div className="border-t border-brand-border dark:border-slate-700 pt-4">
            <p className="text-xs font-semibold uppercase text-brand-textMuted mb-3">Zone dangereuse</p>
            <div className="flex justify-between items-center">
              <p className="text-sm text-brand-textMuted">
                Annule l'abonnement et suspend immédiatement l'établissement.
              </p>
              <Button
                type="button"
                variant="danger"
                size="sm"
                onClick={() => setCancelConfirmOpen(true)}
                disabled={subscription?.status === 'CANCELLED'}
              >
                Annuler l'abonnement
              </Button>
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <Button variant="outline" type="button" onClick={onClose}>
              Fermer
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={cancelConfirmOpen}
        onClose={() => setCancelConfirmOpen(false)}
        onConfirm={() => cancelMutation.mutate()}
        loading={cancelMutation.isPending}
        title="Annuler l'abonnement"
        message={`Êtes-vous sûr de vouloir annuler l'abonnement de "${subscription?.tenantName}" ? L'établissement sera immédiatement suspendu.`}
      />
    </>
  )
}
