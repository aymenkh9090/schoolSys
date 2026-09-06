import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, PowerOff, Power, Upload, Users,
  Search, Eye, BarChart2, X, CheckCircle, UserPlus, UserCheck, KeyRound,
  Mail, Phone, Clock, Link2, ShieldOff,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ImportModal } from '@/components/ui/ImportModal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { cn } from '@/lib/utils'
import { organisationApi, type Teacher, type TeacherRequest, type SchoolUser } from '@/api/organisation.api'

// ─── Constants ────────────────────────────────────────────────────────────────

const SPECIALITES = [
  'MATHEMATIQUES', 'LANGUE_ARABE', 'LANGUE_FRANCAISE', 'LANGUE_ANGLAISE',
  'SCIENCES_PHYSIQUES', 'SCIENCES_NATURELLES', 'HISTOIRE_GEOGRAPHIE',
  'EDUCATION_PHYSIQUE', 'INFORMATIQUE', 'EDUCATION_ISLAMIQUE',
  'EDUCATION_CIVIQUE', 'PHILOSOPHIE', 'AUTRE',
]

// ─── Validation schema ────────────────────────────────────────────────────────

const teacherSchema = z.object({
  codeEnseignant: z.string().min(1, 'Obligatoire').max(15),
  numIdentite: z.string().min(1, 'Obligatoire').max(15),
  nom: z.string().min(1, 'Obligatoire').max(64),
  prenom: z.string().min(1, 'Obligatoire').max(64),
  email: z.string().email('Email invalide').max(100).optional().or(z.literal('')),
  telephone: z.string().max(20).optional().or(z.literal('')),
  specialite: z.string().optional().or(z.literal('')),
  maxHeuresSemaine: z.coerce.number().min(1).max(40).optional(),
  maxHeuresJour: z.coerce.number().min(1).max(10).optional(),
  minHeuresJour: z.coerce.number().min(0).max(10).optional(),
  estEnPoste: z.boolean().optional(),
}).refine(
  (d) => {
    if (d.minHeuresJour != null && d.maxHeuresJour != null) {
      return d.minHeuresJour <= d.maxHeuresJour
    }
    return true
  },
  { message: 'Min heures/jour ≤ Max heures/jour', path: ['minHeuresJour'] }
)

type FormData = z.infer<typeof teacherSchema>

// ─── Helpers ──────────────────────────────────────────────────────────────────

function StatusBadge({ actif }: { actif: boolean }) {
  return (
    <Badge variant={actif ? 'success' : 'danger'}>
      {actif ? 'En poste' : 'Muté'}
    </Badge>
  )
}

/** Initiales pour la pastille d'un enseignant, à défaut de photo. */
function initiales(prenom: string, nom: string): string {
  return `${prenom?.[0] ?? ''}${nom?.[0] ?? ''}`.toUpperCase() || '?'
}

// ─── Detail Modal ─────────────────────────────────────────────────────────────

function DetailModal({ teacher, open, onClose }: { teacher: Teacher | null; open: boolean; onClose: () => void }) {
  const { data: detail, isLoading } = useQuery({
    queryKey: ['teacher-detail', teacher?.idEnseignant],
    queryFn: () => organisationApi.teachers.getWithAssignments(teacher!.idEnseignant),
    enabled: !!teacher && open,
  })

  const t = detail ?? teacher

  return (
    <Modal open={open} onClose={onClose} title="Fiche enseignant" size="xl">
      {isLoading || !t ? (
        <div className="flex justify-center py-8">
          <div className="w-8 h-8 border-2 border-brand-blue border-t-transparent rounded-full animate-spin" />
        </div>
      ) : (
        <div className="space-y-6">
          {/* Header */}
          <div className="flex items-center gap-4 rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-4 dark:border-slate-700 dark:bg-slate-800/40">
            <span className={cn(
              'flex h-14 w-14 shrink-0 items-center justify-center rounded-full text-xl font-bold',
              t.estEnPoste
                ? 'bg-teal-50 text-brand-teal dark:bg-teal-500/15 dark:text-teal-300'
                : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
            )}>
              {initiales(t.prenom, t.nom)}
            </span>
            <div className="min-w-0">
              <h3 className="truncate text-lg font-bold text-brand-text dark:text-slate-100">{t.nomComplet || `${t.prenom} ${t.nom}`}</h3>
              <p className="truncate font-mono text-sm text-brand-textMuted dark:text-slate-400">
                {t.codeEnseignant}{t.specialite ? ` · ${t.specialite.replace(/_/g, ' ')}` : ''}
              </p>
              <div className="mt-1.5"><StatusBadge actif={t.estEnPoste} /></div>
            </div>
          </div>

          {/* Info grid */}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            {[
              ['N° Identité', t.numIdentite],
              ['Email', t.email || '—'],
              ['Téléphone', t.telephone || '—'],
              ['Max H/semaine', t.maxHeuresSemaine ? `${t.maxHeuresSemaine}h` : '—'],
              ['Max H/jour', t.maxHeuresJour ? `${t.maxHeuresJour}h` : '—'],
              ['Min H/jour', t.minHeuresJour != null ? `${t.minHeuresJour}h` : '—'],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg bg-brand-bgSecondary/60 p-3 dark:bg-slate-800/50">
                <p className="text-xs text-brand-textMuted dark:text-slate-400">{label}</p>
                <p className="mt-0.5 truncate text-sm font-medium text-brand-text dark:text-slate-200">{value}</p>
              </div>
            ))}
          </div>

          {/* Charge horaire */}
          {t.maxHeuresSemaine && t.totalHeures != null && (() => {
            // Même correction que dans la vue globale : la couleur se décide
            // sur le pourcentage réel, la barre seule est plafonnée.
            const pct = Math.round((t.totalHeures / t.maxHeuresSemaine) * 100)
            const surcharge = pct > 100
            const sousCharge = pct < 60
            return (
              <div>
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="text-sm font-medium text-brand-text dark:text-slate-200">Charge horaire</p>
                  {surcharge ? <Badge variant="danger">Surchargé</Badge>
                    : sousCharge ? <Badge variant="warning">Sous-chargé</Badge>
                    : <Badge variant="success">Équilibrée</Badge>}
                </div>
                <div className="flex items-center gap-3">
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-brand-bgSecondary dark:bg-slate-800">
                    <div
                      className={cn('h-full rounded-full transition-all',
                        surcharge ? 'bg-red-500' : sousCharge ? 'bg-amber-400' : 'bg-emerald-500')}
                      style={{ width: `${Math.min(100, pct)}%` }}
                    />
                  </div>
                  <span className="whitespace-nowrap text-sm font-medium tabular-nums text-brand-text dark:text-slate-200">
                    {t.totalHeures}h / {t.maxHeuresSemaine}h
                    <span className="ms-1.5 text-brand-textMuted dark:text-slate-400">({pct}%)</span>
                  </span>
                </div>
              </div>
            )
          })()}

          {/* Affectations */}
          <div>
            <p className="text-sm font-medium text-brand-text dark:text-slate-200 mb-2">
              Affectations ({t.affectations?.length ?? t.nombreAffectations ?? 0})
            </p>
            {!t.affectations || t.affectations.length === 0 ? (
              <p className="text-sm text-brand-textMuted dark:text-slate-400 italic">Aucune affectation</p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-brand-border dark:border-slate-700">
                <table className="w-full text-sm">
                  <thead className="bg-brand-bgSecondary dark:bg-slate-800">
                    <tr>
                      {['Classe', 'Matière', 'Type séance', 'Statut'].map(h => (
                        <th key={h} className="px-3 py-2 text-start text-xs font-medium text-brand-textMuted dark:text-slate-400">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-brand-border dark:divide-slate-700 text-brand-text dark:text-slate-200">
                    {t.affectations.map((a) => (
                      <tr key={a.idTeachingAssignment}>
                        <td className="px-3 py-2 font-medium">{a.nomClasse}</td>
                        <td className="px-3 py-2">{a.nomMatiere}</td>
                        <td className="px-3 py-2"><Badge variant="info">{a.typeSeance}</Badge></td>
                        <td className="px-3 py-2"><Badge variant={a.isActive ? 'success' : 'default'}>{a.isActive ? 'Actif' : 'Inactif'}</Badge></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}

// ─── Workload Modal ───────────────────────────────────────────────────────────

function WorkloadModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { data: workload = [], isLoading } = useQuery({
    queryKey: ['teachers-workload'],
    queryFn: organisationApi.teachers.getWorkload,
    enabled: open,
  })

  /** Charge réelle d'un enseignant, en pourcentage de son maximum hebdomadaire. */
  const charge = (t: Teacher) =>
    t.maxHeuresSemaine && t.totalHeures != null
      ? Math.round((t.totalHeures / t.maxHeuresSemaine) * 100)
      : null

  const surcharges = workload.filter((t: Teacher) => (charge(t) ?? 0) > 100).length
  const sousCharges = workload.filter((t: Teacher) => { const p = charge(t); return p != null && p < 60 }).length
  const equilibres = workload.filter((t: Teacher) => { const p = charge(t); return p != null && p >= 60 && p <= 100 }).length

  return (
    <Modal open={open} onClose={onClose} title="Charge horaire — vue globale" size="full">
      {/* Trois chiffres avant le tableau : on ouvre cette vue pour repérer les
          déséquilibres, pas pour lire soixante lignes une à une. */}
      {!isLoading && workload.length > 0 && (
        <div className="mb-4 grid grid-cols-3 gap-3">
          {([
            ['Surchargés', surcharges, 'text-red-600 dark:text-red-400', 'bg-red-50 dark:bg-red-500/10'],
            ['Sous-chargés', sousCharges, 'text-amber-600 dark:text-amber-400', 'bg-amber-50 dark:bg-amber-500/10'],
            ['Équilibrés', equilibres, 'text-emerald-600 dark:text-emerald-400', 'bg-emerald-50 dark:bg-emerald-500/10'],
          ] as const).map(([libelle, valeur, couleur, fond]) => (
            <div key={libelle} className={cn('rounded-lg p-3 text-center', valeur > 0 ? fond : 'bg-brand-bgSecondary dark:bg-slate-800')}>
              <p className={cn('text-2xl font-bold leading-none tabular-nums', valeur > 0 ? couleur : 'text-brand-textMuted dark:text-slate-400')}>
                {valeur}
              </p>
              <p className={cn('mt-1.5 text-xs font-medium', valeur > 0 ? couleur : 'text-brand-textMuted dark:text-slate-400')}>
                {libelle}
              </p>
            </div>
          ))}
        </div>
      )}

      <div className="overflow-x-auto rounded-xl border border-brand-border dark:border-slate-700">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-brand-bgSecondary dark:bg-slate-800">
              {['Enseignant', 'Spécialité', 'Affectations', 'Total H', 'Max H/sem', 'Charge', 'Statut'].map(h => (
                <th key={h} className="px-4 py-3 text-start text-xs font-medium text-brand-textMuted dark:text-slate-400">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-brand-border dark:divide-slate-700">
            {isLoading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <tr key={i}>
                  {Array.from({ length: 7 }).map((_, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded animate-pulse" />
                    </td>
                  ))}
                </tr>
              ))
            ) : workload.length === 0 ? (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-brand-textMuted dark:text-slate-400">Aucun enseignant</td></tr>
            ) : (
              workload.map((t: Teacher) => {
                // Le pourcentage n'est PAS borné ici : il l'était, et
                // `pct > 100` ne pouvait donc jamais être vrai — un enseignant
                // à 130 % s'affichait « OK » à 100 %. Seule la largeur de la
                // barre est plafonnée, plus bas.
                const pct = charge(t)
                const overloaded = pct != null && pct > 100
                const underloaded = pct != null && pct < 60
                return (
                  <tr key={t.idEnseignant} className="hover:bg-brand-bgSecondary/50 dark:hover:bg-slate-800/50 text-brand-text dark:text-slate-200">
                    <td className="px-4 py-3 font-medium">{t.nomComplet || `${t.prenom} ${t.nom}`}</td>
                    <td className="px-4 py-3 text-brand-textMuted dark:text-slate-400">{t.specialite || '—'}</td>
                    <td className="px-4 py-3 text-center">{t.nombreAffectations ?? 0}</td>
                    <td className="px-4 py-3">{t.totalHeures != null ? `${t.totalHeures}h` : '—'}</td>
                    <td className="px-4 py-3">{t.maxHeuresSemaine ? `${t.maxHeuresSemaine}h` : '—'}</td>
                    <td className="px-4 py-3 min-w-[120px]">
                      {pct != null ? (
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-1.5 bg-brand-bgSecondary dark:bg-slate-800 rounded-full overflow-hidden">
                            <div
                              className={cn('h-full rounded-full', overloaded ? 'bg-red-500' : underloaded ? 'bg-amber-400' : 'bg-emerald-500')}
                              style={{ width: `${Math.min(100, pct)}%` }}
                            />
                          </div>
                          <span className={cn('text-xs font-medium', overloaded ? 'text-red-600 dark:text-red-400' : underloaded ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400')}>
                            {pct}%
                          </span>
                        </div>
                      ) : '—'}
                    </td>
                    <td className="px-4 py-3">
                      {pct == null ? <Badge variant="default">Non planifié</Badge>
                        : overloaded ? <Badge variant="danger">Surchargé</Badge>
                        : underloaded ? <Badge variant="warning">Sous-chargé</Badge>
                        : <Badge variant="success">OK</Badge>}
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
    </Modal>
  )
}

// ─── Teacher Form Modal ────────────────────────────────────────────────────────

function TeacherFormModal({
  open, onClose, editing
}: {
  open: boolean
  onClose: () => void
  editing: Teacher | null
}) {
  const qc = useQueryClient()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof teacherSchema>, unknown, FormData>({
    resolver: zodResolver(teacherSchema),
  })

  // Sync editing → form
  const prevEditing = useRef<Teacher | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    if (editing) {
      reset({
        codeEnseignant: editing.codeEnseignant,
        numIdentite: editing.numIdentite,
        nom: editing.nom,
        prenom: editing.prenom,
        email: editing.email ?? '',
        telephone: editing.telephone ?? '',
        specialite: editing.specialite ?? '',
        maxHeuresSemaine: editing.maxHeuresSemaine,
        maxHeuresJour: editing.maxHeuresJour,
        minHeuresJour: editing.minHeuresJour,
        estEnPoste: editing.estEnPoste,
      })
    } else {
      reset({ estEnPoste: true })
    }
  }

  const saveMutation = useMutation({
    mutationFn: (data: FormData) => {
      const payload: TeacherRequest = {
        ...data,
        email: data.email || undefined,
        telephone: data.telephone || undefined,
        specialite: data.specialite || undefined,
      }
      return editing
        ? organisationApi.teachers.update(editing.idEnseignant, payload)
        : organisationApi.teachers.create(payload)
    },
    onSuccess: () => {
      toast.success(editing ? 'Enseignant mis à jour' : 'Enseignant créé')
      qc.invalidateQueries({ queryKey: ['teachers'] })
      onClose()
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur lors de l\'enregistrement')
    },
  })

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? `Modifier — ${editing.nomComplet || `${editing.prenom} ${editing.nom}`}` : 'Nouvel enseignant'}
      size="xl"
    >
      <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
        {/* Identité */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Identité</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Code enseignant" required placeholder="T001" error={errors.codeEnseignant?.message} {...register('codeEnseignant')} />
            <Input label="N° d'identité" required placeholder="12345678" error={errors.numIdentite?.message} {...register('numIdentite')} />
            <Input label="Nom" required placeholder="Ben Ali" error={errors.nom?.message} {...register('nom')} />
            <Input label="Prénom" required placeholder="Mohamed" error={errors.prenom?.message} {...register('prenom')} />
          </div>
        </div>

        {/* Contact */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Contact</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Email" type="email" placeholder="m.benali@ecole.tn" error={errors.email?.message} {...register('email')} />
            <Input label="Téléphone" placeholder="+216 20 000 000" error={errors.telephone?.message} {...register('telephone')} />
          </div>
        </div>

        {/* Pédagogie */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Pédagogie</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Spécialité"
              placeholder="— Choisir —"
              options={SPECIALITES.map(s => ({ value: s, label: s.replace(/_/g, ' ') }))}
              error={errors.specialite?.message}
              {...register('specialite')}
            />
            <label className="flex cursor-pointer items-start gap-3 self-end rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10">
              <input
                type="checkbox" id="estEnPoste" {...register('estEnPoste')}
                className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
              />
              <span className="min-w-0">
                <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                  <CheckCircle size={14} className="text-brand-textMuted dark:text-slate-400" />
                  En poste
                </span>
                <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                  Décoché, l'enseignant est considéré muté et sort de la génération de l'emploi du temps.
                </span>
              </span>
            </label>
          </div>
        </div>

        {/* Contraintes horaires */}
        <div>
          <p className="text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase tracking-wider mb-3">Contraintes horaires</p>
          <div className="grid gap-4 sm:grid-cols-3">
            <Input label="Max H/semaine" type="number" min={1} max={40} placeholder="20" error={errors.maxHeuresSemaine?.message} {...register('maxHeuresSemaine')} />
            <Input label="Max H/jour" type="number" min={1} max={10} placeholder="6" error={errors.maxHeuresJour?.message} {...register('maxHeuresJour')} />
            <Input label="Min H/jour" type="number" min={0} max={10} placeholder="2" error={errors.minHeuresJour?.message} {...register('minHeuresJour')} />
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-2 border-t border-brand-border dark:border-slate-700">
          <Button type="button" variant="outline" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={saveMutation.isPending}>
            {editing ? 'Mettre à jour' : 'Créer l\'enseignant'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

// ─── Main Page ────────────────────────────────────────────────────────────────

type FilterStatus = 'all' | 'actif' | 'mute'

export function GestionEnseignants() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState<FilterStatus>('all')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Teacher | null>(null)
  const [detailTeacher, setDetailTeacher] = useState<Teacher | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Teacher | null>(null)
  const [statusTarget, setStatusTarget] = useState<{ teacher: Teacher; action: 'deactivate' | 'reactivate' } | null>(null)
  const [showImport, setShowImport] = useState(false)
  const [showWorkload, setShowWorkload] = useState(false)
  const [accountTarget, setAccountTarget] = useState<Teacher | null>(null)
  const [createdAccount, setCreatedAccount] = useState<SchoolUser | null>(null)

  const { data: teachers = [], isLoading } = useQuery({
    queryKey: ['teachers'],
    queryFn: organisationApi.teachers.list,
  })

  // Comptes enseignants existants → pour savoir quelles fiches ont déjà un accès
  const { data: teacherAccounts = [] } = useQuery({
    queryKey: ['school-users', 'TEACHER'],
    queryFn: () => organisationApi.users.list('TEACHER'),
  })
  const hasAccount = (t: Teacher) =>
    teacherAccounts.some((u) => u.enseignantId === t.idEnseignant || (!!t.email && u.email === t.email))

  const createAccountMutation = useMutation({
    mutationFn: (t: Teacher) => organisationApi.users.create({ role: 'TEACHER', teacherId: t.idEnseignant }),
    onSuccess: (data) => {
      toast.success('Compte créé')
      qc.invalidateQueries({ queryKey: ['school-users'] })
      setAccountTarget(null)
      if (data.tempPassword) setCreatedAccount(data)
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Erreur lors de la création du compte'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.teachers.delete(id),
    onSuccess: () => { toast.success('Enseignant supprimé'); qc.invalidateQueries({ queryKey: ['teachers'] }); setDeleteTarget(null) },
    onError: () => toast.error('Erreur lors de la suppression'),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'deactivate' | 'reactivate' }) =>
      action === 'deactivate'
        ? organisationApi.teachers.deactivate(id)
        : organisationApi.teachers.reactivate(id),
    onSuccess: (_, vars) => {
      toast.success(vars.action === 'deactivate' ? 'Enseignant désactivé (muté)' : 'Enseignant réactivé')
      qc.invalidateQueries({ queryKey: ['teachers'] })
      setStatusTarget(null)
    },
    onError: () => toast.error('Erreur lors du changement de statut'),
  })

  // Stats
  const total = teachers.length
  const enPoste = teachers.filter((t: Teacher) => t.estEnPoste).length
  const mutes = total - enPoste

  // Filter + search
  const filtered = teachers.filter((t: Teacher) => {
    const matchSearch = !search ||
      `${t.nom} ${t.prenom} ${t.codeEnseignant} ${t.specialite ?? ''}`.toLowerCase().includes(search.toLowerCase())
    const matchStatus =
      filterStatus === 'all' ||
      (filterStatus === 'actif' && t.estEnPoste) ||
      (filterStatus === 'mute' && !t.estEnPoste)
    return matchSearch && matchStatus
  })

  const openCreate = () => { setEditing(null); setShowForm(true) }
  const openEdit = (t: Teacher) => { setEditing(t); setShowForm(true) }

  // Une fiche sans compte d'accès est un enseignant qui ne peut pas ouvrir
  // l'application : c'est l'anomalie la plus fréquente après un import, et elle
  // n'était comptée nulle part.
  const sansCompte = teachers.filter((t: Teacher) => t.estEnPoste && !hasAccount(t)).length

  return (
    <div className="space-y-6">
      <PageHero
        title="Enseignants"
        subtitle="Le corps enseignant de l'établissement, ses spécialités et ses accès"
        icon={Users}
        actions={
          <>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => setShowWorkload(true)}
            >
              <BarChart2 size={16} /> Charge horaire
            </Button>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => setShowImport(true)}
            >
              <Upload size={16} /> Importer
            </Button>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={openCreate}
            >
              <Plus size={16} /> Nouvel enseignant
            </Button>
          </>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Total enseignants" value={total} icon={Users} color="blue" />
        <StatCard title="En poste" value={enPoste} icon={CheckCircle} color="green" />
        <StatCard title="Mutés" value={mutes} icon={PowerOff} color="amber" />
        <StatCard title="Sans compte d'accès" value={sansCompte} icon={ShieldOff} color="red" />
      </div>

      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="relative min-w-[200px] max-w-xs flex-1">
          <Search size={16} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Nom, code, spécialité…"
            className="w-full rounded-lg border border-brand-border bg-white py-2 pe-9 ps-9 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          />
          {search && (
            <button
              onClick={() => setSearch('')}
              title="Effacer la recherche"
              className="absolute end-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-brand-textMuted hover:bg-brand-bgSecondary hover:text-brand-text dark:hover:bg-slate-800"
            >
              <X size={14} />
            </button>
          )}
        </div>

        {/* Segmenté teal plutôt que navy : c'est la couleur d'accent que les
            autres écrans de la section emploient pour l'option retenue. */}
        <div className="flex gap-1 overflow-hidden rounded-lg border border-brand-border dark:border-slate-700">
          {([['all', 'Tous'], ['actif', 'En poste'], ['mute', 'Mutés']] as const).map(([val, label]) => (
            <button
              key={val}
              onClick={() => setFilterStatus(val)}
              aria-pressed={filterStatus === val}
              className={cn(
                'px-3 py-1.5 text-sm font-medium transition-colors',
                filterStatus === val
                  ? 'bg-brand-teal text-white'
                  : 'text-brand-textMuted hover:bg-brand-bgSecondary dark:text-slate-400 dark:hover:bg-slate-800'
              )}
            >
              {label}
            </button>
          ))}
        </div>

        <span className="ms-auto text-sm text-brand-textMuted dark:text-slate-400">
          {filtered.length} résultat{filtered.length !== 1 ? 's' : ''}
        </span>
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-44 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && filtered.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <Users size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            {search || filterStatus !== 'all' ? 'Aucun enseignant ne correspond aux filtres' : 'Aucun enseignant enregistré'}
          </p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            {search || filterStatus !== 'all'
              ? 'Élargissez la recherche ou revenez à « Tous ».'
              : 'Importez votre liste, ou créez une première fiche.'}
          </p>
          <div className="mt-4 flex justify-center gap-2">
            <Button variant="outline" onClick={() => setShowImport(true)}><Upload size={16} /> Importer</Button>
            <Button variant="outline" onClick={openCreate}><Plus size={16} /> Nouvel enseignant</Button>
          </div>
        </div>
      )}

      {/* Une carte par enseignant : le tableau étalait sept colonnes et
          reléguait l'accès à l'application derrière une icône muette, alors
          que c'est la première chose qu'on vérifie après un import. */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {filtered.map((t: Teacher) => {
          const compte = hasAccount(t)
          return (
            <div
              key={t.idEnseignant}
              className={`flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
                t.estEnPoste ? '' : 'opacity-70'
              }`}
            >
              <div className="flex flex-1 flex-col gap-3 p-4">
                <div className="flex items-start gap-3">
                  <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                    t.estEnPoste
                      ? 'bg-teal-50 text-brand-teal dark:bg-teal-500/15 dark:text-teal-300'
                      : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
                  }`}>
                    {initiales(t.prenom, t.nom)}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">
                          {t.nomComplet || `${t.prenom} ${t.nom}`}
                        </h3>
                        <p className="mt-0.5 font-mono text-xs text-brand-textMuted dark:text-slate-400">
                          {t.codeEnseignant}
                        </p>
                      </div>
                      {!t.estEnPoste && <StatusBadge actif={false} />}
                    </div>
                  </div>
                </div>

                {t.specialite && <Badge variant="info">{t.specialite.replace(/_/g, ' ')}</Badge>}

                <div className="space-y-1">
                  {t.email && (
                    <p className="flex items-center gap-1.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                      <Mail size={12} className="shrink-0" /> {t.email}
                    </p>
                  )}
                  {t.telephone && (
                    <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                      <Phone size={12} className="shrink-0" /> {t.telephone}
                    </p>
                  )}
                </div>

                <div className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-brand-textMuted dark:text-slate-400">
                  <span className="inline-flex items-center gap-1.5">
                    <Clock size={13} />
                    {t.maxHeuresSemaine
                      ? <><strong className="font-semibold tabular-nums text-brand-text dark:text-slate-200">{t.maxHeuresSemaine}h</strong> max/sem.</>
                      : <span className="text-amber-600 dark:text-amber-400">Charge max non définie</span>}
                  </span>
                  {t.nombreAffectations !== undefined && (
                    <span className="inline-flex items-center gap-1.5">
                      <Link2 size={13} />
                      <strong className="font-semibold tabular-nums text-brand-text dark:text-slate-200">{t.nombreAffectations}</strong>
                      affectation{t.nombreAffectations > 1 ? 's' : ''}
                    </span>
                  )}
                </div>

                {/* L'accès à l'application, dit en toutes lettres. */}
                {compte ? (
                  <p className="flex items-center gap-1.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
                    <UserCheck size={13} className="shrink-0" /> Compte d'accès actif
                  </p>
                ) : t.email ? (
                  <button
                    onClick={() => setAccountTarget(t)}
                    className="flex items-center gap-1.5 self-start rounded-md text-xs font-medium text-brand-blue hover:underline"
                  >
                    <UserPlus size={13} className="shrink-0" /> Créer un compte d'accès
                  </button>
                ) : (
                  <p className="flex items-start gap-1.5 text-xs text-amber-600 dark:text-amber-400">
                    <ShieldOff size={13} className="mt-px shrink-0" />
                    Pas de compte : ajoutez un email à la fiche pour en créer un.
                  </p>
                )}
              </div>

              <div className="flex items-center justify-end gap-1 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                <button
                  title="Voir le détail" onClick={() => setDetailTeacher(t)}
                  className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-navy dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                >
                  <Eye size={15} />
                </button>
                <button
                  title="Modifier" onClick={() => openEdit(t)}
                  className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-navy dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                >
                  <Pencil size={15} />
                </button>
                {t.estEnPoste ? (
                  <button
                    title="Désactiver (muté)" onClick={() => setStatusTarget({ teacher: t, action: 'deactivate' })}
                    className="rounded-md p-1.5 text-brand-textMuted hover:bg-amber-50 hover:text-amber-600 dark:text-slate-400 dark:hover:bg-amber-500/10 dark:hover:text-amber-400"
                  >
                    <PowerOff size={15} />
                  </button>
                ) : (
                  <button
                    title="Réactiver" onClick={() => setStatusTarget({ teacher: t, action: 'reactivate' })}
                    className="rounded-md p-1.5 text-brand-textMuted hover:bg-emerald-50 hover:text-emerald-600 dark:text-slate-400 dark:hover:bg-emerald-500/10 dark:hover:text-emerald-400"
                  >
                    <Power size={15} />
                  </button>
                )}
                <button
                  title="Supprimer" onClick={() => setDeleteTarget(t)}
                  className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-500/10"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
          )
        })}
      </div>

      {/* Modals */}
      <TeacherFormModal open={showForm} onClose={() => setShowForm(false)} editing={editing} />

      <DetailModal
        teacher={detailTeacher}
        open={!!detailTeacher}
        onClose={() => setDetailTeacher(null)}
      />

      <ImportModal
        open={showImport}
        onClose={() => setShowImport(false)}
        titre="Importer des enseignants"
        entite={{ singulier: 'enseignant', pluriel: 'enseignants' }}
        onImport={organisationApi.teachers.importFile}
        onImported={() => qc.invalidateQueries({ queryKey: ['teachers'] })}
        templates={{
          csv: organisationApi.teachers.downloadTemplateCsv,
          excel: organisationApi.teachers.downloadTemplateExcel,
          basename: 'template_enseignants',
        }}
        colonnes={[
          'codeEnseignant', 'numIdentite', 'nom', 'prenom',
          'email', 'telephone', 'maxHeuresSemaine', 'specialite',
        ]}
      />

      <WorkloadModal open={showWorkload} onClose={() => setShowWorkload(false)} />

      <ConfirmDialog
        open={!!statusTarget}
        title={statusTarget?.action === 'deactivate' ? 'Désactiver l\'enseignant ?' : 'Réactiver l\'enseignant ?'}
        message={
          statusTarget?.action === 'deactivate'
            ? `"${statusTarget.teacher.nomComplet || `${statusTarget.teacher.prenom} ${statusTarget.teacher.nom}`}" sera marqué comme muté. Il ne sera plus disponible pour les affectations.`
            : `"${statusTarget?.teacher.nomComplet || `${statusTarget?.teacher.prenom} ${statusTarget?.teacher.nom}`}" sera remis en poste.`
        }
        confirmLabel={statusTarget?.action === 'deactivate' ? 'Désactiver' : 'Réactiver'}
        variant={statusTarget?.action === 'deactivate' ? 'danger' : 'primary'}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (!statusTarget) return
          statusMutation.mutate({ id: statusTarget.teacher.idEnseignant, action: statusTarget.action })
        }}
        onClose={() => setStatusTarget(null)}
      />

      <ConfirmDialog
        open={!!accountTarget}
        title="Créer un compte d'accès ?"
        message={`Un compte de connexion (rôle Enseignant) sera créé pour "${accountTarget?.nomComplet || `${accountTarget?.prenom} ${accountTarget?.nom}`}" avec l'email ${accountTarget?.email}. Un mot de passe temporaire vous sera affiché.`}
        confirmLabel="Créer le compte"
        variant="primary"
        loading={createAccountMutation.isPending}
        onConfirm={() => accountTarget && createAccountMutation.mutate(accountTarget)}
        onClose={() => setAccountTarget(null)}
      />

      {/* Mot de passe temporaire du compte créé */}
      <Modal open={!!createdAccount} onClose={() => setCreatedAccount(null)} title="Compte créé" size="sm">
        {createdAccount && (
          <div className="space-y-4">
            <div className="p-4 rounded-lg bg-green-50 dark:bg-emerald-500/10 border border-green-200 dark:border-emerald-500/20">
              <p className="text-sm font-semibold text-green-800 dark:text-emerald-300 mb-1">{createdAccount.nomComplet}</p>
              <p className="text-xs text-green-700 dark:text-emerald-400">{createdAccount.email}</p>
              <p className="text-xs text-green-700 dark:text-emerald-400 mt-1">Un email avec ces informations vient de lui être envoyé.</p>
            </div>
            <div className="p-4 rounded-lg bg-yellow-50 dark:bg-amber-500/10 border border-yellow-200 dark:border-amber-500/20">
              <div className="flex items-center gap-2 mb-1">
                <KeyRound size={14} className="text-yellow-700 dark:text-amber-400" />
                <p className="text-sm font-semibold text-yellow-800 dark:text-amber-300">Mot de passe temporaire</p>
              </div>
              <p className="font-mono font-bold text-yellow-900 dark:text-amber-200 text-lg">{createdAccount.tempPassword}</p>
              <p className="text-xs text-yellow-700 dark:text-amber-400 mt-1">
                En cas de non-réception de l'email, communiquez-lui ce mot de passe. Il devra le changer à la première connexion.
              </p>
            </div>
            <div className="flex justify-end">
              <Button onClick={() => setCreatedAccount(null)}>Fermer</Button>
            </div>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Supprimer l'enseignant ?"
        message={`Cette action est irréversible. Supprimer définitivement "${deleteTarget?.nomComplet || `${deleteTarget?.prenom} ${deleteTarget?.nom}`}" ?`}
        confirmLabel="Supprimer"
        variant="danger"
        loading={deleteMutation.isPending}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idEnseignant)}
        onClose={() => setDeleteTarget(null)}
      />
    </div>
  )
}

export default GestionEnseignants
