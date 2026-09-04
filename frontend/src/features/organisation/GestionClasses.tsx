import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Power, PowerOff, GraduationCap } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { organisationApi, type SchoolClass, type Specialite } from '@/api/organisation.api'

const SPECIALITES: Specialite[] = ['TCOM', 'SCIE', 'MATH', 'LETR', 'TECH', 'ECON', 'INFO']
const SPECIALITE_LABELS: Record<Specialite, string> = {
  TCOM: 'Tronc commun',
  SCIE: 'Sciences',
  MATH: 'Mathématiques',
  LETR: 'Lettres',
  TECH: 'Technique',
  ECON: 'Économie et gestion',
  INFO: 'Informatique',
}

const schema = z.object({
  code: z.string().min(1, 'Obligatoire'),
  codeSpecialite: z.enum(['TCOM', 'SCIE', 'MATH', 'LETR', 'TECH', 'ECON', 'INFO']),
  nbEleve: z.coerce.number().optional(),
  estActif: z.boolean().optional(),
  schoolYearId: z.coerce.number().min(1, 'Obligatoire'),
  levelId: z.coerce.number().min(1, 'Obligatoire'),
})

type FormData = z.infer<typeof schema>

export default function GestionClasses() {
  const qc = useQueryClient()
  const [selectedYearId, setSelectedYearId] = useState<number | null>(null)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<SchoolClass | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<SchoolClass | null>(null)

  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })

  const { data: classes = [], isLoading } = useQuery({
    queryKey: ['classes', selectedYearId],
    queryFn: () => selectedYearId ? organisationApi.classes.byYear(selectedYearId) : organisationApi.classes.list(),
    enabled: true,
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: { codeSpecialite: 'TCOM', estActif: true, nbEleve: 30 },
  })

  const prevEditing = useRef<SchoolClass | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? { code: editing.code, codeSpecialite: editing.codeSpecialite, nbEleve: editing.nbEleve, estActif: editing.estActif, schoolYearId: editing.schoolYearId, levelId: editing.levelId }
      : { code: '', codeSpecialite: 'TCOM', estActif: true, nbEleve: 30, schoolYearId: selectedYearId ?? 0, levelId: 0 }
    )
  }

  const saveMutation = useMutation({
    mutationFn: (d: FormData) =>
      editing
        ? organisationApi.classes.update(editing.idClasse, d)
        : organisationApi.classes.create(d),
    onSuccess: () => {
      toast.success(editing ? 'Classe mise à jour' : 'Classe créée')
      qc.invalidateQueries({ queryKey: ['classes'] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, estActif }: { id: number; estActif: boolean }) =>
      organisationApi.classes.toggleStatus(id, estActif),
    onSuccess: () => {
      toast.success('Statut mis à jour')
      qc.invalidateQueries({ queryKey: ['classes'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.classes.delete(id),
    onSuccess: () => {
      toast.success('Classe supprimée')
      qc.invalidateQueries({ queryKey: ['classes'] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Impossible de supprimer'),
  })

  const actives = classes.filter((c) => c.estActif).length
  const totalEleves = classes.reduce((s, c) => s + (c.nbEleve ?? 0), 0)

  const columns: Column<SchoolClass>[] = [
    { key: 'code', header: 'Code', render: (c) => <span className="font-semibold font-mono">{c.code}</span> },
    { key: 'levelNom', header: 'Niveau', render: (c) => <span className="text-sm">{c.levelNom}</span> },
    { key: 'schoolYearNom', header: 'Année', render: (c) => <span className="text-sm text-brand-textMuted dark:text-slate-400">{c.schoolYearNom}</span> },
    { key: 'codeSpecialite', header: 'Spécialité', render: (c) => <Badge variant="default">{SPECIALITE_LABELS[c.codeSpecialite]}</Badge> },
    { key: 'nbEleve', header: 'Élèves', render: (c) => c.nbEleve ?? '—' },
    { key: 'nombreAffectations', header: 'Affectations', render: (c) => c.nombreAffectations ?? 0 },
    { key: 'estActif', header: 'Statut', render: (c) => <Badge variant={c.estActif ? 'success' : 'danger'}>{c.estActif ? 'Active' : 'Inactive'}</Badge> },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (c) => (
        <div className="flex items-center gap-1 justify-end">
          <button onClick={() => toggleMutation.mutate({ id: c.idClasse, estActif: !c.estActif })} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            {c.estActif ? <PowerOff size={15} /> : <Power size={15} />}
          </button>
          <button onClick={() => { setEditing(c); setOpen(true) }} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            <Pencil size={15} />
          </button>
          <button onClick={() => setDeleteTarget(c)} className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-500/10 text-brand-textMuted dark:text-slate-400 hover:text-danger">
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Classes"
        subtitle={`${classes.length} classe${classes.length > 1 ? 's' : ''}`}
        actions={<Button onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouvelle classe</Button>}
      />

      <div className="flex items-center gap-2">
        <label className="text-sm font-medium text-brand-text dark:text-slate-200">Filtrer par année :</label>
        <select
          value={selectedYearId ?? ''}
          onChange={(e) => setSelectedYearId(e.target.value ? Number(e.target.value) : null)}
          className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-1.5 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
        >
          <option value="">Toutes</option>
          {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
        </select>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatCard title="Total classes" value={classes.length} icon={GraduationCap} color="blue" />
        <StatCard title="Actives" value={actives} icon={GraduationCap} color="green" />
        <StatCard title="Total élèves" value={totalEleves} icon={GraduationCap} color="purple" />
      </div>

      <DataTable columns={columns} data={classes} keyField="idClasse" loading={isLoading} emptyMessage="Aucune classe trouvée" />

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Modifier la classe' : 'Nouvelle classe'} size="md">
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input label="Code *" placeholder="7A" {...register('code')} error={errors.code?.message} />
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Spécialité *</label>
              <select {...register('codeSpecialite')} className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200">
                {SPECIALITES.map((s) => <option key={s} value={s}>{SPECIALITE_LABELS[s]}</option>)}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Année scolaire *</label>
              <select {...register('schoolYearId')} className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200">
                <option value="">Choisir...</option>
                {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
              </select>
              {errors.schoolYearId && <p className="text-xs text-danger mt-1">{errors.schoolYearId.message}</p>}
            </div>
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Niveau *</label>
              <select {...register('levelId')} className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200">
                <option value="">Choisir...</option>
                {levels.filter((l) => l.estActif).map((l) => <option key={l.idNiveau} value={l.idNiveau}>{l.nom}</option>)}
              </select>
              {errors.levelId && <p className="text-xs text-danger mt-1">{errors.levelId.message}</p>}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Nombre d'élèves" type="number" {...register('nbEleve')} />
            <label className="flex items-center gap-2 text-sm text-brand-text dark:text-slate-200 mt-6">
              <input type="checkbox" {...register('estActif')} className="rounded" />
              Classe active
            </label>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>{editing ? 'Mettre à jour' : 'Créer'}</Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idClasse)}
        loading={deleteMutation.isPending}
        title="Supprimer la classe"
        message={`Supprimer la classe "${deleteTarget?.code}" ? Cette action est irréversible.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
