import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Power, PowerOff, Layers } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { organisationApi, type Level } from '@/api/organisation.api'

const schema = z.object({
  nom: z.string().min(1, 'Obligatoire').max(100),
  code: z.string().min(1, 'Obligatoire').max(100),
  description: z.string().min(1, 'Obligatoire').max(100),
  estActif: z.boolean().optional(),
})

type FormData = z.infer<typeof schema>

export default function GestionNiveaux() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Level | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Level | null>(null)

  const { data: levels = [], isLoading } = useQuery({
    queryKey: ['levels'],
    queryFn: organisationApi.levels.list,
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { estActif: true },
  })

  const prevEditing = useRef<Level | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? { nom: editing.nom, code: editing.code, description: editing.description, estActif: editing.estActif }
      : { nom: '', code: '', description: '', estActif: true }
    )
  }

  const saveMutation = useMutation({
    mutationFn: (d: FormData) =>
      editing
        ? organisationApi.levels.update(editing.idNiveau, d)
        : organisationApi.levels.create(d),
    onSuccess: () => {
      toast.success(editing ? 'Niveau mis à jour' : 'Niveau créé')
      qc.invalidateQueries({ queryKey: ['levels'] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur')
    },
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, estActif }: { id: number; estActif: boolean }) =>
      organisationApi.levels.toggleStatus(id, estActif),
    onSuccess: () => {
      toast.success('Statut mis à jour')
      qc.invalidateQueries({ queryKey: ['levels'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.levels.delete(id),
    onSuccess: () => {
      toast.success('Niveau supprimé')
      qc.invalidateQueries({ queryKey: ['levels'] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Impossible de supprimer ce niveau (classes actives ?)')
    },
  })

  const actifs = levels.filter((l) => l.estActif).length

  const columns: Column<Level>[] = [
    { key: 'code', header: 'Code', render: (l) => <span className="font-mono font-semibold">{l.code}</span> },
    { key: 'nom', header: 'Nom' },
    { key: 'description', header: 'Description' },
    { key: 'nombreClasses', header: 'Classes', render: (l) => l.nombreClasses ?? 0 },
    { key: 'nombreMatieres', header: 'Matières', render: (l) => l.nombreMatieres ?? 0 },
    {
      key: 'estActif', header: 'Statut',
      render: (l) => <Badge variant={l.estActif ? 'success' : 'danger'}>{l.estActif ? 'Actif' : 'Inactif'}</Badge>
    },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (l) => (
        <div className="flex items-center gap-1 justify-end">
          <button title={l.estActif ? 'Désactiver' : 'Activer'} onClick={() => toggleMutation.mutate({ id: l.idNiveau, estActif: !l.estActif })} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            {l.estActif ? <PowerOff size={15} /> : <Power size={15} />}
          </button>
          <button title="Modifier" onClick={() => { setEditing(l); setOpen(true) }} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            <Pencil size={15} />
          </button>
          <button title="Supprimer" onClick={() => setDeleteTarget(l)} className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-500/10 text-brand-textMuted dark:text-slate-400 hover:text-danger">
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Niveaux scolaires"
        subtitle={`${levels.length} niveau${levels.length > 1 ? 'x' : ''}`}
        actions={<Button onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouveau niveau</Button>}
      />

      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <StatCard title="Total niveaux" value={levels.length} icon={Layers} color="blue" />
        <StatCard title="Actifs" value={actifs} icon={Layers} color="green" />
        <StatCard title="Inactifs" value={levels.length - actifs} icon={Layers} color="red" />
      </div>

      <DataTable
        columns={columns}
        data={levels}
        keyField="idNiveau"
        loading={isLoading}
        emptyMessage="Aucun niveau configuré"
      />

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Modifier le niveau' : 'Nouveau niveau'} size="md">
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input label="Nom *" placeholder="7ème Année de Base" {...register('nom')} error={errors.nom?.message} />
            <Input label="Code *" placeholder="7EME" {...register('code')} error={errors.code?.message} />
          </div>
          <Input label="Description *" placeholder="Première année du collège" {...register('description')} error={errors.description?.message} />
          <div className="flex items-center gap-2">
            <input type="checkbox" id="estActif" {...register('estActif')} className="rounded" />
            <label htmlFor="estActif" className="text-sm text-brand-text dark:text-slate-200">Niveau actif</label>
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
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idNiveau)}
        loading={deleteMutation.isPending}
        title="Supprimer le niveau"
        message={`Supprimer "${deleteTarget?.nom}" ? Cette action échouera s'il existe des classes actives pour ce niveau.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
