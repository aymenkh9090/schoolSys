import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { subscriptionApi, type CreateSubscriptionDto } from '@/api/subscription.api'

const schema = z.object({
  plan: z.enum(['FREE', 'STANDARD', 'PREMIUM']),
  billingCycle: z.enum(['MONTHLY', 'YEARLY']),
  price: z.coerce.number().min(0, 'Le prix ne peut pas être négatif').optional(),
})

type FormValues = z.infer<typeof schema>

interface Props {
  tenantId: number | null
  tenantName?: string
  onClose: () => void
}

export function ModalCreationAbonnement({ tenantId, tenantName, onClose }: Props) {
  const qc = useQueryClient()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<z.input<typeof schema>, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { plan: 'FREE', billingCycle: 'MONTHLY' },
  })

  const { mutate, isPending } = useMutation({
    mutationFn: (values: FormValues) => {
      const dto: CreateSubscriptionDto = { tenantId: tenantId!, ...values }
      return subscriptionApi.create(dto)
    },
    onSuccess: () => {
      toast.success('Abonnement créé')
      qc.invalidateQueries({ queryKey: ['subscriptions'] })
      qc.invalidateQueries({ queryKey: ['tenants'] })
      reset()
      onClose()
    },
    onError: (err: unknown) => {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? "Erreur lors de la création de l'abonnement"
      toast.error(message)
    },
  })

  return (
    <Modal
      open={tenantId !== null}
      onClose={onClose}
      title={`Créer un abonnement${tenantName ? ` — ${tenantName}` : ''}`}
    >
      <form onSubmit={handleSubmit((d) => mutate(d))} className="space-y-4">
        <Select
          label="Plan"
          error={errors.plan?.message}
          options={[
            { value: 'FREE', label: 'Gratuit' },
            { value: 'STANDARD', label: 'Standard' },
            { value: 'PREMIUM', label: 'Premium' },
          ]}
          {...register('plan')}
        />
        <Select
          label="Cycle de facturation"
          error={errors.billingCycle?.message}
          options={[
            { value: 'MONTHLY', label: 'Mensuel' },
            { value: 'YEARLY', label: 'Annuel' },
          ]}
          {...register('billingCycle')}
        />
        <Input
          label="Prix (optionnel)"
          type="number"
          min={0}
          step="0.01"
          error={errors.price?.message}
          {...register('price')}
        />

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" type="button" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={isPending}>
            Créer l'abonnement
          </Button>
        </div>
      </form>
    </Modal>
  )
}
