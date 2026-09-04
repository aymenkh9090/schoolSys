import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Copy, ImageUp, X } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { tenantApi, type CreateTenantDto, type Tenant } from '@/api/tenant.api'

const LOGO_MAX_BYTES = 1024 * 1024 // 1 Mo

// Aligné sur le backend CreateTenantRequest
const schema = z.object({
  code: z
    .string()
    .min(2, 'Code requis')
    .max(50, '50 caractères max')
    .regex(/^[a-z0-9-]+$/, 'Lettres minuscules, chiffres et tirets uniquement'),
  name: z.string().min(2, 'Nom requis').max(150, '150 caractères max'),
  type: z.enum(['PRIMAIRE', 'COLLEGE', 'SECONDAIRE'], {
    message: "Type d'établissement requis",
  }),
  plan: z.enum(['FREE', 'STANDARD', 'PREMIUM']),
  address: z.string().min(2, 'Adresse requise').max(255, '255 caractères max'),
  phone: z.string().min(6, 'Téléphone requis').max(20, '20 caractères max'),
  logo: z.string().optional(),
  emailAdmin: z.string().email('Email invalide').max(150),
  nomCompletAdmin: z.string().min(3, 'Nom complet requis').max(100),
})

interface Props {
  open: boolean
  onClose: () => void
}

export function ModalCreationEtablissement({ open, onClose }: Props) {
  const qc = useQueryClient()
  // Credentials admin retournés uniquement à la création — affichés une seule fois
  const [created, setCreated] = useState<Tenant | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<CreateTenantDto>({
    resolver: zodResolver(schema),
    defaultValues: { plan: 'FREE' },
  })

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
    mutationFn: (dto: CreateTenantDto) =>
      tenantApi.create({ ...dto, logo: dto.logo || undefined }),
    onSuccess: (tenant) => {
      toast.success('Établissement créé avec succès')
      qc.invalidateQueries({ queryKey: ['tenants'] })
      reset()
      setCreated(tenant)
    },
    onError: (err: unknown) => {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? 'Erreur lors de la création'
      toast.error(message)
    },
  })

  function handleClose() {
    setCreated(null)
    onClose()
  }

  function copyCredentials() {
    if (!created) return
    navigator.clipboard.writeText(
      `Établissement: ${created.name}\nCode: ${created.code}\nLogin: ${created.adminUsername ?? created.adminEmail}\nMot de passe temporaire: ${created.adminTempPassword}`
    )
    toast.success('Credentials copiés')
  }

  if (created) {
    return (
      <Modal open={open} onClose={handleClose} title="Établissement créé" size="md">
        <div className="space-y-4">
          <p className="text-sm text-brand-textMuted">
            Compte administrateur créé et email envoyé à {created.adminEmail}. Ces
            informations ne seront <strong>plus jamais affichées</strong> — en cas de
            non-réception de l'email, transmettez-les vous-même à l'admin de l'établissement.
          </p>
          <div className="rounded-lg bg-brand-bgSecondary p-4 text-sm space-y-1.5 font-mono">
            <p>Code établissement : <strong>{created.code}</strong></p>
            <p>Login : <strong>{created.adminUsername ?? created.adminEmail}</strong></p>
            <p>Mot de passe temporaire : <strong>{created.adminTempPassword}</strong></p>
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={copyCredentials}>
              <Copy size={14} /> Copier
            </Button>
            <Button onClick={handleClose}>Fermer</Button>
          </div>
        </div>
      </Modal>
    )
  }

  return (
    <Modal open={open} onClose={handleClose} title="Créer un établissement" size="lg">
      <form onSubmit={handleSubmit((d) => mutate(d))} className="space-y-4">
        <p className="text-xs font-semibold uppercase text-brand-textMuted">
          Informations générales
        </p>
        <Input label="Nom de l'établissement" error={errors.name?.message} {...register('name')} />
        <div className="grid grid-cols-2 gap-4">
          <Input
            label="Code unique"
            placeholder="lycee-alfarabi"
            error={errors.code?.message}
            {...register('code')}
          />
          <Select
            label="Type d'établissement"
            placeholder="Choisir..."
            defaultValue=""
            error={errors.type?.message}
            options={[
              { value: 'PRIMAIRE', label: 'Primaire' },
              { value: 'COLLEGE', label: 'Collège' },
              { value: 'SECONDAIRE', label: 'Lycée / Secondaire' },
            ]}
            {...register('type')}
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

        <p className="text-xs font-semibold uppercase text-brand-textMuted pt-2">
          Abonnement
        </p>
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

        <p className="text-xs font-semibold uppercase text-brand-textMuted pt-2">
          Compte administrateur initial
        </p>
        <div className="grid grid-cols-2 gap-4">
          <Input
            label="Nom complet admin"
            placeholder="Mohamed Ben Ali"
            error={errors.nomCompletAdmin?.message}
            {...register('nomCompletAdmin')}
          />
          <Input
            label="Email admin (sera son login)"
            type="email"
            error={errors.emailAdmin?.message}
            {...register('emailAdmin')}
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" type="button" onClick={handleClose}>
            Annuler
          </Button>
          <Button type="submit" loading={isPending}>
            Créer
          </Button>
        </div>
      </form>
    </Modal>
  )
}
