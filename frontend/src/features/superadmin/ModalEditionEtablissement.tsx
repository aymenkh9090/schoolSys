import { useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ImageUp, X } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { tenantApi, type Tenant, type UpdateTenantDto } from '@/api/tenant.api'

const LOGO_MAX_BYTES = 1024 * 1024 // 1 Mo

// Aligné sur le backend UpdateTenantRequest (le code établissement n'est pas modifiable)
const schema = z.object({
  name: z.string().min(2, 'Nom requis').max(150, '150 caractères max'),
  type: z.enum(['PRIMAIRE', 'COLLEGE', 'SECONDAIRE'], {
    message: "Type d'établissement requis",
  }),
  plan: z.enum(['FREE', 'STANDARD', 'PREMIUM']),
  address: z.string().min(2, 'Adresse requise').max(255, '255 caractères max'),
  phone: z.string().min(6, 'Téléphone requis').max(20, '20 caractères max'),
  logo: z.string().optional(),
})

interface Props {
  tenant: Tenant | null
  onClose: () => void
}

export function ModalEditionEtablissement({ tenant, onClose }: Props) {
  const qc = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<UpdateTenantDto>({
    resolver: zodResolver(schema),
  })

  useEffect(() => {
    if (tenant) {
      reset({
        name: tenant.name,
        type: tenant.type,
        plan: tenant.plan,
        address: tenant.address,
        phone: tenant.phone,
        logo: tenant.logo ?? undefined,
      })
    }
  }, [tenant, reset])

  const logoPreview = watch('logo')

  function handleLogoFile(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      toast.error('Le logo doit être une image (PNG, JPG, SVG…)')
      return
    }
    if (file.size > LOGO_MAX_BYTES) {
      toast.error('Image trop volumineuse (1 Mo max)')
      return
    }
    const reader = new FileReader()
    reader.onload = () => setValue('logo', reader.result as string, { shouldValidate: true })
    reader.readAsDataURL(file)
  }

  function removeLogo() {
    setValue('logo', undefined)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const { mutate, isPending } = useMutation({
    mutationFn: (dto: UpdateTenantDto) => tenantApi.update(tenant!.id, { ...dto, logo: dto.logo || undefined }),
    onSuccess: () => {
      toast.success('Établissement mis à jour')
      qc.invalidateQueries({ queryKey: ['tenants'] })
      onClose()
    },
    onError: (err: unknown) => {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? 'Erreur lors de la mise à jour'
      toast.error(message)
    },
  })

  return (
    <Modal open={!!tenant} onClose={onClose} title="Modifier l'établissement" size="lg">
      <form onSubmit={handleSubmit((d) => mutate(d))} className="space-y-4">
        <p className="text-xs font-semibold uppercase text-brand-textMuted">
          Informations générales
        </p>
        <div className="grid grid-cols-2 gap-4">
          <Input label="Nom de l'établissement" error={errors.name?.message} {...register('name')} />
          <Input label="Code unique" value={tenant?.code ?? ''} disabled readOnly />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Select
            label="Type d'établissement"
            placeholder="Choisir..."
            error={errors.type?.message}
            options={[
              { value: 'PRIMAIRE', label: 'Primaire' },
              { value: 'COLLEGE', label: 'Collège' },
              { value: 'SECONDAIRE', label: 'Lycée / Secondaire' },
            ]}
            {...register('type')}
          />
          <Select
            label="Plan d'abonnement"
            error={errors.plan?.message}
            options={[
              { value: 'FREE', label: 'Gratuit' },
              { value: 'STANDARD', label: 'Standard' },
              { value: 'PREMIUM', label: 'Premium' },
            ]}
            {...register('plan')}
          />
        </div>
        <Input label="Adresse complète" error={errors.address?.message} {...register('address')} />
        <div className="grid grid-cols-2 gap-4">
          <Input label="Téléphone" error={errors.phone?.message} {...register('phone')} />
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">
              Logo (optionnel)
            </label>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => handleLogoFile(e.target.files?.[0])}
            />
            <div className="flex items-center gap-3">
              {logoPreview ? (
                <img
                  src={logoPreview}
                  alt="Aperçu du logo"
                  className="w-10 h-10 rounded-lg object-cover border border-brand-border dark:border-slate-700"
                />
              ) : (
                <div className="w-10 h-10 rounded-lg border border-dashed border-brand-border dark:border-slate-700 flex items-center justify-center text-brand-textMuted dark:text-slate-500">
                  <ImageUp size={16} />
                </div>
              )}
              <Button type="button" variant="outline" size="sm" onClick={() => fileInputRef.current?.click()}>
                {logoPreview ? 'Changer' : 'Importer'}
              </Button>
              {logoPreview && (
                <button type="button" onClick={removeLogo} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted hover:text-danger">
                  <X size={14} />
                </button>
              )}
            </div>
            {errors.logo?.message && <p className="text-xs text-danger mt-1">{errors.logo.message}</p>}
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" type="button" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={isPending}>
            Enregistrer
          </Button>
        </div>
      </form>
    </Modal>
  )
}
