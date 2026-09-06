import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, UserX, UserCheck, KeyRound, Users, ShieldCheck, Mail, Phone,
  BookOpen, CalendarDays, Info, Copy,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
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

  const initiales = (nom: string) =>
    nom.split(/\s+/).filter(Boolean).slice(0, 2).map((m) => m[0]).join('').toUpperCase() || '?'

  return (
    <div className="space-y-6">
      <PageHero
        title="Utilisateurs"
        subtitle="Les comptes d'accès à l'application, et ce que chacun a le droit de voir"
        icon={ShieldCheck}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={() => setOpen(true)}
          >
            <Plus size={16} /> Nouvel utilisateur
          </Button>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Total comptes" value={users.length} icon={Users} color="blue" />
        <StatCard title="Actifs" value={actifs} icon={UserCheck} color="green" />
        <StatCard title="Désactivés" value={users.length - actifs} icon={UserX} color="red" />
        <StatCard title="Administrateurs" value={admins} icon={ShieldCheck} color="purple" />
      </div>

      {/* Trois rôles seulement : des pastilles valent mieux qu'un menu qu'il
          faut ouvrir pour savoir ce qu'il contient. */}
      <div className="rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">Rôle</p>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setRoleFilter('')}
            aria-pressed={roleFilter === ''}
            className={`rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
              roleFilter === ''
                ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
            }`}
          >
            Tous
          </button>
          {ASSIGNABLE_ROLES.map((r) => {
            const actif = roleFilter === r
            return (
              <button
                key={r}
                onClick={() => setRoleFilter(actif ? '' : r)}
                aria-pressed={actif}
                className={`rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                  actif
                    ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                    : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                }`}
              >
                {ROLE_LABELS[r]}
              </button>
            )
          })}
        </div>
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-36 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && users.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <ShieldCheck size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            {roleFilter ? `Aucun compte pour le rôle « ${ROLE_LABELS[roleFilter]} »` : 'Aucun utilisateur'}
          </p>
          <Button className="mt-4" variant="outline" onClick={() => setOpen(true)}>
            <Plus size={16} /> Nouvel utilisateur
          </Button>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {users.map((u) => (
          <div
            key={u.id}
            className={`flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
              u.actif ? '' : 'opacity-70'
            }`}
          >
            <div className="flex flex-1 items-start gap-3 p-4">
              <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                u.actif
                  ? 'bg-teal-50 text-brand-teal dark:bg-teal-500/15 dark:text-teal-300'
                  : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
              }`}>
                {initiales(u.nomComplet)}
              </span>

              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">{u.nomComplet}</h3>
                  {!u.actif && <Badge variant="danger">Désactivé</Badge>}
                </div>

                <div className="mt-1.5"><Badge variant={ROLE_VARIANTS[u.role]}>{ROLE_LABELS[u.role]}</Badge></div>

                <div className="mt-2 space-y-1">
                  {u.email && (
                    <p className="flex items-center gap-1.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                      <Mail size={12} className="shrink-0" /> {u.email}
                    </p>
                  )}
                  {u.telephone && (
                    <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                      <Phone size={12} className="shrink-0" /> {u.telephone}
                    </p>
                  )}
                  {u.matiere && (
                    <p className="flex items-center gap-1.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                      <BookOpen size={12} className="shrink-0" /> {u.matiere}
                    </p>
                  )}
                </div>
              </div>
            </div>

            <div className="flex items-center justify-between gap-2 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
              <span className="inline-flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                <CalendarDays size={12} /> {formatDate(u.createdAt)}
              </span>
              {u.actif ? (
                <button
                  title="Désactiver ce compte" onClick={() => setDeactivateTarget(u)}
                  className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-500/10"
                >
                  <UserX size={15} />
                </button>
              ) : (
                <button
                  title="Réactiver ce compte" onClick={() => reactivateMutation.mutate(u.id)}
                  className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                >
                  <UserCheck size={15} />
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Modal créer */}
      <Modal open={open} onClose={() => { setOpen(false); reset() }} title="Nouvel utilisateur" size="xl">
        <form onSubmit={handleSubmit((d) => createMutation.mutate(d))} className="space-y-5">
          <div>
            {/* Le rôle décide de la suite du formulaire : il mérite mieux
                qu'un menu déroulant qu'on ouvre sans savoir ce qu'il contient. */}
            <label className="mb-2 block text-sm font-medium text-brand-text dark:text-slate-200">Rôle *</label>
            <div className="grid gap-2 sm:grid-cols-3">
              {ASSIGNABLE_ROLES.map((r) => (
                <label
                  key={r}
                  className="flex cursor-pointer items-center gap-2.5 rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10"
                >
                  <input
                    type="radio" value={r} {...register('role')}
                    className="h-4 w-4 shrink-0 border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
                  />
                  <span className="text-sm font-medium text-brand-text dark:text-slate-200">{ROLE_LABELS[r]}</span>
                </label>
              ))}
            </div>
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
              <p className="flex items-start gap-1.5 rounded-lg bg-blue-50 p-3 text-xs text-blue-700 dark:bg-blue-500/10 dark:text-blue-400">
                <Info size={13} className="mt-px shrink-0" />
                <span>
                  Le compte est lié à la fiche du module Enseignants : email, nom, matière et téléphone
                  en sont repris automatiquement. Si l'enseignant n'apparaît pas, créez d'abord sa fiche
                  — avec un email — dans la gestion des enseignants.
                </span>
              </p>
            </>
          ) : (
            <>
              <Input label="Nom complet *" placeholder="Nom Prénom" {...register('nomComplet')} error={errors.nomComplet?.message} />
              <Input label="Email *" type="email" {...register('email')} error={errors.email?.message} />
              <Input label="Téléphone" {...register('telephone')} />
            </>
          )}

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => { setOpen(false); reset() }}>Annuler</Button>
            <Button type="submit" loading={createMutation.isPending}>Créer le compte</Button>
          </div>
        </form>
      </Modal>

      {/* Modal mot de passe temporaire */}
      <Modal open={!!createdUser} onClose={() => setCreatedUser(null)} title="Compte créé" size="md">
        {createdUser && (
          <div className="space-y-4">
            <div className="rounded-lg border border-green-200 bg-green-50 p-4 dark:border-emerald-500/20 dark:bg-emerald-500/10">
              <p className="mb-1 text-sm font-semibold text-green-800 dark:text-emerald-300">{createdUser.nomComplet}</p>
              <p className="truncate text-xs text-green-700 dark:text-emerald-400">{createdUser.email}</p>
              <p className="mt-1 text-xs text-green-700 dark:text-emerald-400">
                Un email avec ces informations vient de lui être envoyé.
              </p>
            </div>

            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-500/20 dark:bg-amber-500/10">
              <div className="mb-2 flex items-center gap-2">
                <KeyRound size={14} className="text-amber-700 dark:text-amber-400" />
                <p className="text-sm font-semibold text-amber-800 dark:text-amber-300">Mot de passe temporaire</p>
              </div>
              {/* Il se recopie à la main dans un message : le bouton évite la
                  faute de frappe sur une chaîne qu'on ne peut pas relire. */}
              <div className="flex items-center gap-2">
                <p className="flex-1 select-all break-all rounded-md bg-white px-3 py-2 font-mono text-lg font-bold text-amber-900 dark:bg-slate-900 dark:text-amber-300">
                  {createdUser.tempPassword}
                </p>
                <button
                  type="button"
                  title="Copier le mot de passe"
                  onClick={() => {
                    navigator.clipboard.writeText(createdUser.tempPassword ?? '')
                      .then(() => toast.success('Mot de passe copié'))
                      .catch(() => toast.error('Copie impossible — sélectionnez le texte'))
                  }}
                  className="shrink-0 rounded-md p-2 text-amber-700 hover:bg-amber-100 dark:text-amber-400 dark:hover:bg-amber-500/20"
                >
                  <Copy size={16} />
                </button>
              </div>
              <p className="mt-2 text-xs text-amber-700 dark:text-amber-400">
                En cas de non-réception de l'email, communiquez-lui ce mot de passe.
                Il devra le changer à la première connexion.
              </p>
            </div>

            <div className="flex justify-end border-t border-brand-border pt-4 dark:border-slate-700">
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
