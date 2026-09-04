import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, UserX, UserCheck, KeyRound, Users } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { organisationApi, type SchoolUser, type UserRole } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'
import { ASSIGNABLE_ROLES, ROLE_LABELS, ROLE_VARIANTS } from '@/lib/roles'

const OBLIGATOIRE = 'Ce champ est obligatoire'

const schema = z
  .object({
    role: z.enum(['SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT']),
    teacherId: z.coerce.number().optional(),
    nomComplet: z.string().optional(),
    email: z.string().optional(),
    telephone: z.string().optional(),
  })
  .superRefine((d, ctx) => {
    if (d.role === 'TEACHER') {
      if (!d.teacherId || d.teacherId < 1) {
        ctx.addIssue({ code: 'custom', path: ['teacherId'], message: OBLIGATOIRE })
      }
    } else {
      if (!d.nomComplet?.trim()) {
        ctx.addIssue({ code: 'custom', path: ['nomComplet'], message: OBLIGATOIRE })
      }
      if (!d.email?.trim()) {
        ctx.addIssue({ code: 'custom', path: ['email'], message: OBLIGATOIRE })
      } else if (!z.email().safeParse(d.email).success) {
        ctx.addIssue({ code: 'custom', path: ['email'], message: 'Email invalide' })
      }
    }
  })

type FormData = z.infer<typeof schema>

export default function GestionUsers() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [createdUser, setCreatedUser] = useState<SchoolUser | null>(null)
  const [deactivateTarget, setDeactivateTarget] = useState<SchoolUser | null>(null)
  const [roleFilter, setRoleFilter] = useState<UserRole | ''>('')

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['school-users', roleFilter],
    queryFn: () => organisationApi.users.list(roleFilter || undefined),
  })

  const { data: teachers = [] } = useQuery({
    queryKey: ['teachers-actifs'],
    queryFn: organisationApi.teachers.getActifs,
  })

  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: { role: 'TEACHER' },
  })
  const selectedRole = watch('role')

  // Un compte enseignant se greffe sur une fiche existante ; il faut un email sur la fiche
  const linkableTeachers = teachers.filter((t) => t.email)
  const alreadyLinked = new Set(users.map((u) => u.enseignantId).filter(Boolean))

  const createMutation = useMutation({
    mutationFn: (d: FormData) =>
      organisationApi.users.create(
        d.role === 'TEACHER'
          ? { role: d.role, teacherId: d.teacherId }
          : { role: d.role, nomComplet: d.nomComplet, email: d.email, telephone: d.telephone || undefined }
      ),
    onSuccess: (data) => {
      toast.success('Utilisateur créé')
      qc.invalidateQueries({ queryKey: ['school-users'] })
      setOpen(false)
      reset()
      if (data.tempPassword) setCreatedUser(data)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id: number) => organisationApi.users.deactivate(id),
    onSuccess: () => {
      toast.success('Utilisateur désactivé')
      qc.invalidateQueries({ queryKey: ['school-users'] })
      setDeactivateTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const reactivateMutation = useMutation({
    mutationFn: (id: number) => organisationApi.users.reactivate(id),
    onSuccess: () => {
      toast.success('Utilisateur réactivé')
      qc.invalidateQueries({ queryKey: ['school-users'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const actifs = users.filter((u) => u.actif).length
  const admins = users.filter((u) => u.role === 'SCHOOL_ADMIN').length

  const columns: Column<SchoolUser>[] = [
    { key: 'nomComplet', header: 'Nom', render: (u) => <span className="font-medium">{u.nomComplet}</span> },
    { key: 'email', header: 'Email', render: (u) => <span className="text-sm text-brand-textMuted">{u.email}</span> },
    { key: 'role', header: 'Rôle', render: (u) => <Badge variant={ROLE_VARIANTS[u.role]}>{ROLE_LABELS[u.role]}</Badge> },
    { key: 'matiere', header: 'Matière', render: (u) => <span className="text-sm text-brand-textMuted">{u.matiere || '—'}</span> },
    { key: 'telephone', header: 'Téléphone', render: (u) => <span className="text-sm text-brand-textMuted">{u.telephone || '—'}</span> },
    { key: 'createdAt', header: 'Créé le', render: (u) => <span className="text-xs text-brand-textMuted">{formatDate(u.createdAt)}</span> },
    { key: 'actif', header: 'Statut', render: (u) => <Badge variant={u.actif ? 'success' : 'danger'}>{u.actif ? 'Actif' : 'Inactif'}</Badge> },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (u) => (
        <div className="flex items-center gap-1 justify-end">
          {u.actif ? (
            <button title="Désactiver" onClick={() => setDeactivateTarget(u)} className="p-1.5 rounded-md hover:bg-red-50 text-brand-textMuted hover:text-danger">
              <UserX size={15} />
            </button>
          ) : (
            <button title="Réactiver" onClick={() => reactivateMutation.mutate(u.id)} className="p-1.5 rounded-md hover:bg-brand-bgSecondary text-brand-textMuted hover:text-brand-text">
              <UserCheck size={15} />
            </button>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Utilisateurs"
        subtitle="Comptes d'accès à l'application"
        actions={<Button onClick={() => setOpen(true)}><Plus size={16} /> Nouvel utilisateur</Button>}
      />

      {/* Filtre rôle */}
      <div className="flex items-center gap-2">
        <label className="text-sm font-medium text-brand-text">Filtrer par rôle :</label>
        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value as UserRole | '')}
          className="border border-brand-border rounded-lg px-3 py-1.5 text-sm bg-white"
        >
          <option value="">Tous</option>
          {ASSIGNABLE_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatCard title="Total utilisateurs" value={users.length} icon={Users} color="blue" />
        <StatCard title="Actifs" value={actifs} icon={Users} color="green" />
        <StatCard title="Admins" value={admins} icon={Users} color="red" />
      </div>

      <DataTable columns={columns} data={users} keyField="id" loading={isLoading} emptyMessage="Aucun utilisateur trouvé" />

      {/* Modal créer */}
      <Modal open={open} onClose={() => { setOpen(false); reset() }} title="Nouvel utilisateur" size="md">
        <form onSubmit={handleSubmit((d) => createMutation.mutate(d))} className="space-y-4">
          <div>
            <label className="text-sm font-medium text-brand-text block mb-1">Rôle *</label>
            <select {...register('role')} className="w-full border border-brand-border rounded-lg px-3 py-2 text-sm bg-white">
              {ASSIGNABLE_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
            </select>
          </div>

          {selectedRole === 'TEACHER' ? (
            <>
              <Select
                label="Enseignant *"
                placeholder={linkableTeachers.length === 0 ? 'Aucun enseignant avec email disponible' : 'Sélectionner un enseignant'}
                disabled={linkableTeachers.length === 0}
                options={linkableTeachers.map((t) => ({
                  value: t.idEnseignant,
                  label: `${t.nomComplet} — ${t.email}${alreadyLinked.has(t.idEnseignant) ? ' (compte existant)' : ''}`,
                }))}
                error={errors.teacherId?.message}
                {...register('teacherId')}
              />
              <p className="text-xs text-brand-textMuted">
                Le compte est lié à la fiche du module Enseignants : email, nom, matière et téléphone
                en sont repris automatiquement. Si l'enseignant n'apparaît pas, créez d'abord sa fiche
                (avec un email) dans Gestion des enseignants.
              </p>
            </>
          ) : (
            <>
              <Input label="Nom complet *" placeholder="Nom Prénom" {...register('nomComplet')} error={errors.nomComplet?.message} />
              <Input label="Email *" type="email" {...register('email')} error={errors.email?.message} />
              <Input label="Téléphone" {...register('telephone')} />
            </>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => { setOpen(false); reset() }}>Annuler</Button>
            <Button type="submit" loading={createMutation.isPending}>Créer</Button>
          </div>
        </form>
      </Modal>

      {/* Modal mot de passe temporaire */}
      <Modal open={!!createdUser} onClose={() => setCreatedUser(null)} title="Compte créé" size="sm">
        {createdUser && (
          <div className="space-y-4">
            <div className="p-4 rounded-lg bg-green-50 border border-green-200">
              <p className="text-sm font-semibold text-green-800 mb-1">{createdUser.nomComplet}</p>
              <p className="text-xs text-green-700">{createdUser.email}</p>
              <p className="text-xs text-green-700 mt-1">Un email avec ces informations vient de lui être envoyé.</p>
            </div>
            <div className="p-4 rounded-lg bg-yellow-50 border border-yellow-200">
              <div className="flex items-center gap-2 mb-1">
                <KeyRound size={14} className="text-yellow-700" />
                <p className="text-sm font-semibold text-yellow-800">Mot de passe temporaire</p>
              </div>
              <p className="font-mono font-bold text-yellow-900 text-lg">{createdUser.tempPassword}</p>
              <p className="text-xs text-yellow-700 mt-1">En cas de non-réception de l'email, communiquez-lui ce mot de passe. Il devra le changer à la première connexion.</p>
            </div>
            <div className="flex justify-end">
              <Button onClick={() => setCreatedUser(null)}>Fermer</Button>
            </div>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={!!deactivateTarget}
        onClose={() => setDeactivateTarget(null)}
        onConfirm={() => deactivateTarget && deactivateMutation.mutate(deactivateTarget.id)}
        loading={deactivateMutation.isPending}
        title="Désactiver l'utilisateur"
        message={`Désactiver le compte de ${deactivateTarget?.nomComplet} ? Il ne pourra plus se connecter.`}
        variant="danger"
        confirmLabel="Désactiver"
      />
    </div>
  )
}
