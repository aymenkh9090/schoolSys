import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, BookOpen, Microscope } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { organisationApi, type Subject } from '@/api/organisation.api'

const schema = z.object({
  codeMatiere: z.string().min(1, 'Obligatoire').max(20),
  libMatiere: z.string().min(1, 'Obligatoire').max(100),
  description: z.string().optional(),
  abreviation: z.string().optional(),
  couleur: z.string().optional(),
  necessiteLab: z.boolean().optional(),
  necessiteSport: z.boolean().optional(),
  estPrincipale: z.boolean().optional(),
  estEnseignee: z.boolean().optional(),
})

type FormData = z.infer<typeof schema>

export default function GestionMatieres() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Subject | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Subject | null>(null)

  const { data: subjects = [], isLoading } = useQuery({
    queryKey: ['subjects'],
    queryFn: organisationApi.subjects.list,
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { estEnseignee: true, estPrincipale: false, necessiteLab: false, necessiteSport: false },
  })

  const prevEditing = useRef<Subject | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? {
        codeMatiere: editing.codeMatiere, libMatiere: editing.libMatiere, description: editing.description,
        abreviation: editing.abreviation, couleur: editing.couleur, necessiteLab: editing.necessiteLab,
        necessiteSport: editing.necessiteSport, estPrincipale: editing.estPrincipale, estEnseignee: editing.estEnseignee,
      }
      : { codeMatiere: '', libMatiere: '', estEnseignee: true, estPrincipale: false, necessiteLab: false, necessiteSport: false }
    )
  }

  const saveMutation = useMutation({
    mutationFn: (d: FormData) =>
      editing
        ? organisationApi.subjects.update(editing.idMatiere, d)
        : organisationApi.subjects.create(d),
    onSuccess: () => {
      toast.success(editing ? 'Matière mise à jour' : 'Matière créée')
      qc.invalidateQueries({ queryKey: ['subjects'] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.subjects.delete(id),
    onSuccess: () => {
      toast.success('Matière supprimée')
      qc.invalidateQueries({ queryKey: ['subjects'] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Impossible de supprimer'),
  })

  const principales = subjects.filter((s) => s.estPrincipale).length
  const labo = subjects.filter((s) => s.necessiteLab).length

  const columns: Column<Subject>[] = [
    {
      key: 'codeMatiere', header: 'Code',
      render: (s) => (
        <div className="flex items-center gap-2">
          {s.couleur && <span className="w-3 h-3 rounded-full shrink-0" style={{ background: s.couleur }} />}
          <span className="font-mono font-semibold text-sm">{s.codeMatiere}</span>
        </div>
      ),
    },
    { key: 'libMatiere', header: 'Libellé', render: (s) => <span className="font-medium">{s.libMatiere}</span> },
    { key: 'abreviation', header: 'Abrév.', render: (s) => <span className="text-sm text-brand-textMuted">{s.abreviation || '—'}</span> },
    {
      key: 'flags', header: 'Caractéristiques',
      render: (s) => (
        <div className="flex gap-1 flex-wrap">
          {s.estPrincipale && <Badge variant="info">Principale</Badge>}
          {s.necessiteLab && <Badge variant="warning">Labo</Badge>}
          {s.necessiteSport && <Badge variant="warning">Sport</Badge>}
          {!s.estEnseignee && <Badge variant="danger">Non enseignée</Badge>}
        </div>
      ),
    },
    { key: 'nombreNiveaux', header: 'Niveaux', render: (s) => s.nombreNiveaux ?? 0 },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (s) => (
        <div className="flex items-center gap-1 justify-end">
          <button title="Modifier" onClick={() => { setEditing(s); setOpen(true) }} className="p-1.5 rounded-md hover:bg-brand-bgSecondary text-brand-textMuted hover:text-brand-text">
            <Pencil size={15} />
          </button>
          <button title="Supprimer" onClick={() => setDeleteTarget(s)} className="p-1.5 rounded-md hover:bg-red-50 text-brand-textMuted hover:text-danger">
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Matières"
        subtitle={`${subjects.length} matière${subjects.length > 1 ? 's' : ''}`}
        actions={<Button onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouvelle matière</Button>}
      />

      <div className="grid grid-cols-3 gap-3">
        <StatCard title="Total matières" value={subjects.length} icon={BookOpen} color="blue" />
        <StatCard title="Principales" value={principales} icon={BookOpen} color="green" />
        <StatCard title="Nécessitent labo" value={labo} icon={Microscope} color="yellow" />
      </div>

      <DataTable columns={columns} data={subjects} keyField="idMatiere" loading={isLoading} emptyMessage="Aucune matière configurée" />

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Modifier la matière' : 'Nouvelle matière'} size="md">
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input label="Code *" placeholder="MATH" {...register('codeMatiere')} error={errors.codeMatiere?.message} />
            <Input label="Abréviation" placeholder="Math" {...register('abreviation')} />
          </div>
          <Input label="Libellé *" placeholder="Mathématiques" {...register('libMatiere')} error={errors.libMatiere?.message} />
          <Input label="Description" {...register('description')} />
          <div>
            <label className="text-sm font-medium text-brand-text block mb-1">Couleur</label>
            <input type="color" {...register('couleur')} defaultValue="#3b82f6" className="w-16 h-8 rounded cursor-pointer border border-brand-border" />
          </div>
          <div className="flex flex-wrap gap-4">
            {([['estPrincipale', 'Principale'], ['estEnseignee', 'Enseignée'], ['necessiteLab', 'Nécessite labo'], ['necessiteSport', 'Nécessite sport']] as const).map(([field, label]) => (
              <label key={field} className="flex items-center gap-2 text-sm text-brand-text">
                <input type="checkbox" {...register(field)} className="rounded" />
                {label}
              </label>
            ))}
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
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idMatiere)}
        loading={deleteMutation.isPending}
        title="Supprimer la matière"
        message={`Supprimer "${deleteTarget?.libMatiere}" ? Cette action échouera s'il existe des affectations liées.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
