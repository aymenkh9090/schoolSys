import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Power, PowerOff, Star, CalendarDays } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { DateInputFR } from '@/components/ui/DateInputFR'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { organisationApi, type SchoolYear } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'

const schema = z.object({
  nom: z.string().min(1, 'Obligatoire'),
  dateDebut: z.string().min(1, 'Obligatoire'),
  dateFin: z.string().min(1, 'Obligatoire'),
  estActive: z.boolean().optional(),
  estCourante: z.boolean().optional(),
})

type FormData = z.infer<typeof schema>

export default function GestionAnneesScolaires() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<SchoolYear | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<SchoolYear | null>(null)

  const { data: years = [], isLoading } = useQuery({
    queryKey: ['school-years'],
    queryFn: organisationApi.schoolYears.list,
  })

  const { register, control, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { estActive: true, estCourante: false },
  })

  const prevEditing = useRef<SchoolYear | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? { nom: editing.nom, dateDebut: editing.dateDebut, dateFin: editing.dateFin, estActive: editing.estActive, estCourante: editing.estCourante }
      : { nom: '', dateDebut: '', dateFin: '', estActive: true, estCourante: false }
    )
  }

  const saveMutation = useMutation({
    mutationFn: (d: FormData) =>
      editing
        ? organisationApi.schoolYears.update(editing.idAnnee, d)
        : organisationApi.schoolYears.create(d),
    onSuccess: () => {
      toast.success(editing ? 'Année mise à jour' : 'Année créée')
      qc.invalidateQueries({ queryKey: ['school-years'] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      organisationApi.schoolYears.toggleStatus(id, active),
    onSuccess: () => {
      toast.success('Statut mis à jour')
      qc.invalidateQueries({ queryKey: ['school-years'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.schoolYears.delete(id),
    onSuccess: () => {
      toast.success('Année supprimée')
      qc.invalidateQueries({ queryKey: ['school-years'] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Impossible de supprimer'),
  })

  const actives = years.filter((y) => y.estActive).length
  const courante = years.find((y) => y.estCourante)

  const columns: Column<SchoolYear>[] = [
    {
      key: 'nom', header: 'Nom',
      render: (y) => (
        <div className="flex items-center gap-2">
          <span className="font-semibold text-brand-text">{y.nom}</span>
          {y.estCourante && <Badge variant="info"><Star size={10} className="inline me-0.5" />Courante</Badge>}
        </div>
      ),
    },
    { key: 'dateDebut', header: 'Début', render: (y) => <span className="text-sm text-brand-textMuted">{formatDate(y.dateDebut)}</span> },
    { key: 'dateFin', header: 'Fin', render: (y) => <span className="text-sm text-brand-textMuted">{formatDate(y.dateFin)}</span> },
    { key: 'nombreClasses', header: 'Classes', render: (y) => y.nombreClasses ?? 0 },
    { key: 'nombreAffectations', header: 'Affectations', render: (y) => y.nombreAffectations ?? 0 },
    { key: 'estActive', header: 'Statut', render: (y) => <Badge variant={y.estActive ? 'success' : 'danger'}>{y.estActive ? 'Active' : 'Inactive'}</Badge> },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (y) => (
        <div className="flex items-center gap-1 justify-end">
          <button title={y.estActive ? 'Désactiver' : 'Activer'} onClick={() => toggleMutation.mutate({ id: y.idAnnee, active: !y.estActive })} className="p-1.5 rounded-md hover:bg-brand-bgSecondary text-brand-textMuted hover:text-brand-text">
            {y.estActive ? <PowerOff size={15} /> : <Power size={15} />}
          </button>
          <button title="Modifier" onClick={() => { setEditing(y); setOpen(true) }} className="p-1.5 rounded-md hover:bg-brand-bgSecondary text-brand-textMuted hover:text-brand-text">
            <Pencil size={15} />
          </button>
          <button title="Supprimer" onClick={() => setDeleteTarget(y)} className="p-1.5 rounded-md hover:bg-red-50 text-brand-textMuted hover:text-danger">
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Années scolaires"
        subtitle={`${years.length} année${years.length > 1 ? 's' : ''}`}
        actions={<Button onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouvelle année</Button>}
      />

      <div className="grid grid-cols-3 gap-3">
        <StatCard title="Total" value={years.length} icon={CalendarDays} color="blue" />
        <StatCard title="Actives" value={actives} icon={CalendarDays} color="green" />
        <StatCard title="Courante" value={courante?.nom ?? '—'} icon={Star} color="purple" />
      </div>

      <DataTable columns={columns} data={years} keyField="idAnnee" loading={isLoading} emptyMessage="Aucune année scolaire configurée" />

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Modifier l\'année' : 'Nouvelle année scolaire'} size="md">
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-4">
          <Input label="Nom *" placeholder="2024-2025" {...register('nom')} error={errors.nom?.message} />
          <div className="grid grid-cols-2 gap-4">
            <Controller
              name="dateDebut"
              control={control}
              render={({ field }) => (
                <DateInputFR label="Date début *" value={field.value} onChange={field.onChange} error={errors.dateDebut?.message} />
              )}
            />
            <Controller
              name="dateFin"
              control={control}
              render={({ field }) => (
                <DateInputFR label="Date fin *" value={field.value} onChange={field.onChange} error={errors.dateFin?.message} />
              )}
            />
          </div>
          <div className="flex gap-6">
            <label className="flex items-center gap-2 text-sm text-brand-text">
              <input type="checkbox" {...register('estActive')} className="rounded" />
              Active
            </label>
            <label className="flex items-center gap-2 text-sm text-brand-text">
              <input type="checkbox" {...register('estCourante')} className="rounded" />
              Année courante
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
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idAnnee)}
        loading={deleteMutation.isPending}
        title="Supprimer l'année scolaire"
        message={`Supprimer "${deleteTarget?.nom}" ? Cette action est irréversible.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
