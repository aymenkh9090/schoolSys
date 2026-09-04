import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Power, PowerOff, Users, Upload } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { organisationApi, type Eleve } from '@/api/organisation.api'

const schema = z.object({
  codeEleve: z.string().min(1, 'Obligatoire'),
  nom: z.string().min(1, 'Obligatoire'),
  prenom: z.string().min(1, 'Obligatoire'),
  numIdentite: z.string().optional(),
  email: z.string().email('Email invalide').optional().or(z.literal('')),
  telephone: z.string().optional(),
  classeId: z.coerce.number().min(1, 'Obligatoire'),
})

type FormData = z.infer<typeof schema>

export default function GestionEleves() {
  const qc = useQueryClient()
  const [classeId, setClasseId] = useState<number | null>(null)
  const [classeInput, setClasseInput] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Eleve | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Eleve | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importResult, setImportResult] = useState<{ importes: number; ignores: number; erreurs: Array<{ ligne: number; message: string }> } | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const { data: classes = [] } = useQuery({ queryKey: ['classes'], queryFn: organisationApi.classes.list })

  const { data: eleves = [], isLoading } = useQuery({
    queryKey: ['eleves', classeId],
    queryFn: () => organisationApi.eleves.byClass(classeId!),
    enabled: classeId !== null,
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: { classeId: classeId ?? 0 },
  })

  const prevEditing = useRef<Eleve | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? { codeEleve: editing.codeEleve, nom: editing.nom, prenom: editing.prenom, numIdentite: editing.numIdentite, email: editing.email, telephone: editing.telephone, classeId: editing.classeId }
      : { codeEleve: '', nom: '', prenom: '', classeId: classeId ?? 0 }
    )
  }

  const saveMutation = useMutation({
    mutationFn: (d: FormData) =>
      editing
        ? organisationApi.eleves.update(editing.idEleve, d)
        : organisationApi.eleves.create(d),
    onSuccess: () => {
      toast.success(editing ? 'Élève mis à jour' : 'Élève créé')
      qc.invalidateQueries({ queryKey: ['eleves', classeId] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, estActif }: { id: number; estActif: boolean }) =>
      organisationApi.eleves.toggleStatut(id, estActif),
    onSuccess: () => {
      toast.success('Statut mis à jour')
      qc.invalidateQueries({ queryKey: ['eleves', classeId] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.eleves.delete(id),
    onSuccess: () => {
      toast.success('Élève supprimé')
      qc.invalidateQueries({ queryKey: ['eleves', classeId] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const importMutation = useMutation({
    mutationFn: (file: File) => organisationApi.eleves.importFile(file),
    onSuccess: (result) => {
      toast.success(`${result.importes} élève(s) importé(s)`)
      setImportResult(result)
      qc.invalidateQueries({ queryKey: ['eleves', classeId] })
    },
    onError: () => toast.error('Erreur lors de l\'import'),
  })

  const actifs = eleves.filter((e) => e.estActif).length

  const columns: Column<Eleve>[] = [
    { key: 'codeEleve', header: 'Code', render: (e) => <span className="font-mono font-semibold text-sm">{e.codeEleve}</span> },
    { key: 'nom', header: 'Nom', render: (e) => <span className="font-medium">{e.nom}</span> },
    { key: 'prenom', header: 'Prénom' },
    { key: 'numIdentite', header: 'N° Identité', render: (e) => <span className="text-sm text-brand-textMuted dark:text-slate-400">{e.numIdentite || '—'}</span> },
    { key: 'email', header: 'Email', render: (e) => <span className="text-sm text-brand-textMuted dark:text-slate-400">{e.email || '—'}</span> },
    { key: 'classeCode', header: 'Classe', render: (e) => <Badge variant="default">{e.classeCode}</Badge> },
    { key: 'estActif', header: 'Statut', render: (e) => <Badge variant={e.estActif ? 'success' : 'danger'}>{e.estActif ? 'Actif' : 'Inactif'}</Badge> },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (e) => (
        <div className="flex items-center gap-1 justify-end">
          <button onClick={() => toggleMutation.mutate({ id: e.idEleve, estActif: !e.estActif })} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            {e.estActif ? <PowerOff size={15} /> : <Power size={15} />}
          </button>
          <button onClick={() => { setEditing(e); setOpen(true) }} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            <Pencil size={15} />
          </button>
          <button onClick={() => setDeleteTarget(e)} className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-500/10 text-brand-textMuted dark:text-slate-400 hover:text-danger">
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Élèves"
        subtitle="Gestion des élèves par classe"
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setImportOpen(true)}><Upload size={16} /> Importer</Button>
            <Button onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouvel élève</Button>
          </div>
        }
      />

      {/* Filtre classe */}
      <div className="flex gap-2 items-end">
        <div>
          <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Classe</label>
          <select
            value={classeInput}
            onChange={(e) => { setClasseInput(e.target.value); setClasseId(e.target.value ? Number(e.target.value) : null) }}
            className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-48"
          >
            <option value="">Sélectionner une classe</option>
            {classes.map((c) => <option key={c.idClasse} value={c.idClasse}>{c.code} — {c.levelNom}</option>)}
          </select>
        </div>
        {classeId && (
          <div className="grid grid-cols-2 gap-3">
            <StatCard title="Total" value={eleves.length} icon={Users} color="blue" />
            <StatCard title="Actifs" value={actifs} icon={Users} color="green" />
          </div>
        )}
      </div>

      {classeId === null ? (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-12 text-center">
          <Users size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
          <p className="text-sm text-brand-textMuted dark:text-slate-400">Sélectionnez une classe pour afficher ses élèves.</p>
        </div>
      ) : (
        <DataTable columns={columns} data={eleves} keyField="idEleve" loading={isLoading} emptyMessage="Aucun élève dans cette classe" />
      )}

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Modifier l\'élève' : 'Nouvel élève'} size="md">
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input label="Code *" placeholder="EL001" {...register('codeEleve')} error={errors.codeEleve?.message} />
            <Input label="N° Identité" {...register('numIdentite')} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Nom *" {...register('nom')} error={errors.nom?.message} />
            <Input label="Prénom *" {...register('prenom')} error={errors.prenom?.message} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Email" type="email" {...register('email')} error={errors.email?.message} />
            <Input label="Téléphone" {...register('telephone')} />
          </div>
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Classe *</label>
            <select {...register('classeId')} className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200" defaultValue={classeId ?? ''}>
              <option value="">Sélectionner</option>
              {classes.map((c) => <option key={c.idClasse} value={c.idClasse}>{c.code} — {c.levelNom}</option>)}
            </select>
            {errors.classeId && <p className="text-xs text-red-500 mt-1">{errors.classeId.message}</p>}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>{editing ? 'Mettre à jour' : 'Créer'}</Button>
          </div>
        </form>
      </Modal>

      {/* Modal import */}
      <Modal open={importOpen} onClose={() => { setImportOpen(false); setImportResult(null) }} title="Importer des élèves" size="md">
        <div className="space-y-4">
          <div className="flex gap-2">
            <Button size="sm" variant="outline" onClick={() => organisationApi.eleves.downloadTemplateCsv().then((b) => {
              const url = URL.createObjectURL(b); const a = document.createElement('a'); a.href = url; a.download = 'template-eleves.csv'; a.click(); URL.revokeObjectURL(url)
            })}>CSV template</Button>
            <Button size="sm" variant="outline" onClick={() => organisationApi.eleves.downloadTemplateExcel().then((b) => {
              const url = URL.createObjectURL(b); const a = document.createElement('a'); a.href = url; a.download = 'template-eleves.xlsx'; a.click(); URL.revokeObjectURL(url)
            })}>Excel template</Button>
          </div>
          <div
            className="border-2 border-dashed border-brand-border dark:border-slate-700 rounded-xl p-8 text-center cursor-pointer hover:bg-brand-bgSecondary dark:hover:bg-slate-800"
            onClick={() => fileRef.current?.click()}
            onDrop={(e) => { e.preventDefault(); const f = e.dataTransfer.files[0]; if (f) importMutation.mutate(f) }}
            onDragOver={(e) => e.preventDefault()}
          >
            <Upload size={24} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-2" />
            <p className="text-sm text-brand-textMuted dark:text-slate-400">Glissez un fichier CSV ou Excel ici, ou cliquez</p>
            <input ref={fileRef} type="file" accept=".csv,.xlsx,.xls" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) importMutation.mutate(f) }} />
          </div>
          {importMutation.isPending && <p className="text-sm text-center text-brand-textMuted dark:text-slate-400">Import en cours…</p>}
          {importResult && (
            <div className="space-y-1 text-sm">
              <p className="text-green-600 dark:text-emerald-400 font-medium">{importResult.importes} élève(s) importé(s)</p>
              {importResult.ignores > 0 && <p className="text-yellow-600 dark:text-amber-400">{importResult.ignores} ligne(s) ignorée(s)</p>}
              {importResult.erreurs.length > 0 && (
                <div className="bg-red-50 dark:bg-red-500/10 p-3 rounded-lg">
                  {importResult.erreurs.slice(0, 5).map((e, i) => <p key={i} className="text-red-600 dark:text-red-400 text-xs">L.{e.ligne}: {e.message}</p>)}
                </div>
              )}
            </div>
          )}
          <div className="flex justify-end">
            <Button variant="outline" onClick={() => { setImportOpen(false); setImportResult(null) }}>Fermer</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idEleve)}
        loading={deleteMutation.isPending}
        title="Supprimer l'élève"
        message={`Supprimer ${deleteTarget?.prenom} ${deleteTarget?.nom} ?`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
