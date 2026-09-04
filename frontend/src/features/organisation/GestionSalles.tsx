import { useState, useRef, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, Upload, Download,
  Search, DoorOpen, FlaskConical, Dumbbell, Monitor, X, CheckCircle, AlertCircle
} from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { cn } from '@/lib/utils'
import { organisationApi, type Room, type RoomRequest, type RoomImportResult, type RoomType } from '@/api/organisation.api'

// ─── Constants ────────────────────────────────────────────────────────────────

const ROOM_TYPES: { value: RoomType; label: string }[] = [
  { value: 'NORMALE', label: 'Salle normale' },
  { value: 'LABSCIENCE', label: 'Labo Sciences' },
  { value: 'LABPHYSIQUE', label: 'Labo Physique' },
  { value: 'LABINFORMATIQUE', label: 'Salle Informatique' },
  { value: 'LABTECHNIQUE', label: 'Labo Technique' },
  { value: 'SALLESPORT', label: 'Salle de sport' },
  { value: 'SALLEDESSIN', label: 'Salle de dessin' },
  { value: 'SALLEMUSIQUE', label: 'Salle de musique' },
  { value: 'AMPHI', label: 'Amphithéâtre' },
  { value: 'BIBLIOTHEQUE', label: 'Bibliothèque' },
]

const EQUIPEMENTS = ['VIDEOPROJECTEUR', 'TABLEAU_BLANC', 'CLIMATISATION', 'ORDINATEURS', 'SONO'] as const
const EQUIPEMENT_LABELS: Record<string, string> = {
  VIDEOPROJECTEUR: 'Vidéoprojecteur',
  TABLEAU_BLANC: 'Tableau blanc',
  CLIMATISATION: 'Climatisation',
  ORDINATEURS: 'Ordinateurs',
  SONO: 'Sonorisation',
}

const TYPE_BADGE: Record<RoomType, { variant: 'default' | 'info' | 'success' | 'warning' | 'danger'; label: string }> = {
  NORMALE: { variant: 'default', label: 'Normale' },
  LABSCIENCE: { variant: 'success', label: 'Labo Sciences' },
  LABPHYSIQUE: { variant: 'info', label: 'Labo Physique' },
  LABINFORMATIQUE: { variant: 'warning', label: 'Informatique' },
  LABTECHNIQUE: { variant: 'warning', label: 'Technique' },
  SALLESPORT: { variant: 'danger', label: 'Sport' },
  SALLEDESSIN: { variant: 'info', label: 'Dessin' },
  SALLEMUSIQUE: { variant: 'info', label: 'Musique' },
  AMPHI: { variant: 'warning', label: 'Amphi' },
  BIBLIOTHEQUE: { variant: 'default', label: 'Bibliothèque' },
}

// ─── Schema ───────────────────────────────────────────────────────────────────

const schema = z.object({
  codeSalle: z.string().min(1, 'Obligatoire').max(20),
  typeSalle: z.enum([
    'NORMALE', 'LABSCIENCE', 'LABPHYSIQUE', 'LABINFORMATIQUE', 'LABTECHNIQUE', 'SALLESPORT',
    'SALLEDESSIN', 'SALLEMUSIQUE', 'AMPHI', 'BIBLIOTHEQUE',
  ]),
  capacite: z.coerce.number().min(1).max(500).optional(),
  codeBloc: z.string().max(10).optional().or(z.literal('')),
  numEtage: z.string().max(10).optional().or(z.literal('')),
  equipements: z.array(z.string()).optional(),
  estDisponible: z.boolean().optional(),
})

type FormData = z.infer<typeof schema>

// ─── Helpers ──────────────────────────────────────────────────────────────────

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

// ─── Import Modal ─────────────────────────────────────────────────────────────

function ImportModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [dragging, setDragging] = useState(false)
  const [result, setResult] = useState<RoomImportResult | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const importMutation = useMutation({
    mutationFn: organisationApi.rooms.importFile,
    onSuccess: (data) => { setResult(data); qc.invalidateQueries({ queryKey: ['rooms'] }) },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur lors de l\'import')
    },
  })

  const handleFile = (file: File) => {
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!['csv', 'xlsx', 'xls'].includes(ext ?? '')) {
      toast.error('Format non supporté. Utilisez .csv, .xlsx ou .xls')
      return
    }
    importMutation.mutate(file)
  }

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault(); setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }, [])

  const handleClose = () => { setResult(null); onClose() }

  return (
    <Modal open={open} onClose={handleClose} title="Importer des salles" size="lg">
      <div className="space-y-4">
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => organisationApi.rooms.downloadTemplateCsv().then((b) => downloadBlob(b, 'template_salles.csv'))}>
            <Download size={14} /> Template CSV
          </Button>
          <Button variant="outline" size="sm" onClick={() => organisationApi.rooms.downloadTemplateExcel().then((b) => downloadBlob(b, 'template_salles.xlsx'))}>
            <Download size={14} /> Template Excel
          </Button>
        </div>

        {!result ? (
          <div
            onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
            onClick={() => inputRef.current?.click()}
            className={cn(
              'border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-colors',
              dragging ? 'border-brand-blue bg-blue-50 dark:bg-blue-500/10' : 'border-brand-border dark:border-slate-700 hover:border-brand-blue hover:bg-blue-50/40 dark:hover:bg-blue-500/10'
            )}
          >
            <Upload size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
            <p className="text-sm text-brand-text dark:text-slate-200 font-medium">Glisser-déposer un fichier ici</p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-1">ou cliquer pour sélectionner — CSV, XLSX, XLS</p>
            <input ref={inputRef} type="file" className="hidden" accept=".csv,.xlsx,.xls" onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])} />
          </div>
        ) : (
          <div className="space-y-3">
            <div className="grid grid-cols-3 gap-3">
              <div className="bg-emerald-50 dark:bg-emerald-500/10 rounded-lg p-3 text-center">
                <CheckCircle size={20} className="mx-auto text-emerald-600 dark:text-emerald-400 mb-1" />
                <p className="text-xl font-bold text-emerald-700 dark:text-emerald-400">{result.importes}</p>
                <p className="text-xs text-emerald-600 dark:text-emerald-400">Importées</p>
              </div>
              <div className="bg-amber-50 dark:bg-amber-500/10 rounded-lg p-3 text-center">
                <X size={20} className="mx-auto text-amber-600 dark:text-amber-400 mb-1" />
                <p className="text-xl font-bold text-amber-700 dark:text-amber-400">{result.ignores}</p>
                <p className="text-xs text-amber-600 dark:text-amber-400">Ignorées</p>
              </div>
              <div className="bg-red-50 dark:bg-red-500/10 rounded-lg p-3 text-center">
                <AlertCircle size={20} className="mx-auto text-red-600 dark:text-red-400 mb-1" />
                <p className="text-xl font-bold text-red-700 dark:text-red-400">{result.erreurs?.length ?? 0}</p>
                <p className="text-xs text-red-600 dark:text-red-400">Erreurs</p>
              </div>
            </div>
            {result.erreurs?.length > 0 && (
              <div className="max-h-40 overflow-y-auto rounded-lg border border-red-200 dark:border-red-500/20 bg-red-50 dark:bg-red-500/10 p-3 space-y-1">
                {result.erreurs.map((e, i) => (
                  <p key={i} className="text-xs text-red-700 dark:text-red-400">Ligne {e.ligne} — {e.codeSalle}: {e.message}</p>
                ))}
              </div>
            )}
            <Button variant="outline" onClick={() => setResult(null)} className="w-full">Importer un autre fichier</Button>
          </div>
        )}

        {importMutation.isPending && (
          <p className="text-center text-sm text-brand-textMuted dark:text-slate-400 animate-pulse">Import en cours…</p>
        )}
      </div>
    </Modal>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function GestionSalles() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [editing, setEditing] = useState<Room | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Room | null>(null)
  const [search, setSearch] = useState('')
  const [filterType, setFilterType] = useState<RoomType | ''>('')

  const { data: rooms = [], isLoading } = useQuery({
    queryKey: ['rooms'],
    queryFn: organisationApi.rooms.list,
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: { typeSalle: 'NORMALE' },
  })

  const prevEditing = useRef<Room | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? {
          codeSalle: editing.codeSalle, typeSalle: editing.typeSalle, capacite: editing.capacite,
          codeBloc: editing.codeBloc ?? '', numEtage: editing.numEtage ?? '',
          equipements: editing.equipements ? editing.equipements.split(',') : [],
          estDisponible: editing.estDisponible ?? true,
        }
      : { codeSalle: '', typeSalle: 'NORMALE', capacite: undefined, codeBloc: '', numEtage: '', equipements: [], estDisponible: true }
    )
  }

  const saveMutation = useMutation({
    mutationFn: (d: FormData) => {
      const dto: RoomRequest = {
        codeSalle: d.codeSalle, typeSalle: d.typeSalle, capacite: d.capacite,
        codeBloc: d.codeBloc || undefined, numEtage: d.numEtage || undefined,
        equipements: d.equipements?.length ? d.equipements.join(',') : undefined,
        estDisponible: d.estDisponible ?? true,
      }
      return editing ? organisationApi.rooms.update(editing.idSalle, dto) : organisationApi.rooms.create(dto)
    },
    onSuccess: () => {
      toast.success(editing ? 'Salle mise à jour' : 'Salle créée')
      qc.invalidateQueries({ queryKey: ['rooms'] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.rooms.delete(id),
    onSuccess: () => {
      toast.success('Salle supprimée')
      qc.invalidateQueries({ queryKey: ['rooms'] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => {
      toast.error(e.response?.data?.message ?? 'Erreur')
    },
  })

  // Stats
  const stats = {
    total: rooms.length,
    labSci: rooms.filter((r) => r.typeSalle === 'LABSCIENCE').length,
    labPhy: rooms.filter((r) => r.typeSalle === 'LABPHYSIQUE').length,
    info: rooms.filter((r) => r.typeSalle === 'LABINFORMATIQUE').length,
    labTech: rooms.filter((r) => r.typeSalle === 'LABTECHNIQUE').length,
    sport: rooms.filter((r) => r.typeSalle === 'SALLESPORT').length,
  }

  const filtered = rooms.filter((r) => {
    const q = search.toLowerCase()
    const matchSearch = !q || r.codeSalle.toLowerCase().includes(q) || (r.codeBloc ?? '').toLowerCase().includes(q)
    const matchType = !filterType || r.typeSalle === filterType
    return matchSearch && matchType
  })

  const columns: Column<Room>[] = [
    { key: 'codeSalle', header: 'Code', render: (r) => <span className="font-mono font-medium">{r.codeSalle}</span> },
    {
      key: 'typeSalle', header: 'Type',
      render: (r) => {
        const t = TYPE_BADGE[r.typeSalle]
        return <Badge variant={t.variant}>{t.label}</Badge>
      }
    },
    { key: 'capacite', header: 'Capacité', render: (r) => r.capacite ? `${r.capacite} places` : '—' },
    { key: 'codeBloc', header: 'Bloc', render: (r) => r.codeBloc ?? '—' },
    { key: 'numEtage', header: 'Étage', render: (r) => r.numEtage ?? '—' },
    {
      key: 'estDisponible', header: 'Disponible',
      render: (r) => (
        <Badge variant={r.estDisponible === false ? 'danger' : 'success'}>
          {r.estDisponible === false ? 'Indisponible' : 'Disponible'}
        </Badge>
      ),
    },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (r) => (
        <div className="flex items-center gap-1 justify-end">
          <button title="Modifier" onClick={() => { setEditing(r); setOpen(true) }} className="p-1.5 rounded-md hover:bg-brand-bgSecondary text-brand-textMuted hover:text-brand-text">
            <Pencil size={15} />
          </button>
          <button title="Supprimer" onClick={() => setDeleteTarget(r)} className="p-1.5 rounded-md hover:bg-red-50 text-brand-textMuted hover:text-danger">
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Gestion des Salles"
        subtitle={`${rooms.length} salle${rooms.length > 1 ? 's' : ''} enregistrée${rooms.length > 1 ? 's' : ''}`}
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setImportOpen(true)}><Upload size={16} /> Importer</Button>
            <Button onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouvelle salle</Button>
          </div>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-6 gap-3">
        <StatCard title="Total" value={stats.total} icon={DoorOpen} color="blue" />
        <StatCard title="Labo Sciences" value={stats.labSci} icon={FlaskConical} color="green" />
        <StatCard title="Labo Physique" value={stats.labPhy} icon={FlaskConical} color="amber" />
        <StatCard title="Informatique" value={stats.info} icon={Monitor} color="red" />
        <StatCard title="Labo Technique" value={stats.labTech} icon={FlaskConical} color="purple" />
        <StatCard title="Sport" value={stats.sport} icon={Dumbbell} color="red" />
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <div className="relative flex-1 min-w-48">
          <Search size={16} className="absolute start-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Rechercher par code ou bloc…"
            className="w-full ps-9 pe-3 py-2 text-sm border border-brand-border dark:border-slate-700 rounded-lg bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
          />
        </div>
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value as RoomType | '')}
          className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
        >
          <option value="">Tous les types</option>
          {ROOM_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select>
      </div>

      <DataTable
        columns={columns}
        data={filtered}
        keyField="idSalle"
        loading={isLoading}
        emptyMessage="Aucune salle trouvée"
      />

      {/* Create / Edit modal */}
      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Modifier la salle' : 'Nouvelle salle'} size="md">
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input label="Code salle *" placeholder="S101" {...register('codeSalle')} error={errors.codeSalle?.message} />
            <Select label="Type *" options={ROOM_TYPES} {...register('typeSalle')} error={errors.typeSalle?.message} />
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Input label="Capacité" type="number" placeholder="30" {...register('capacite')} error={errors.capacite?.message} />
            <Input label="Code bloc" placeholder="A" {...register('codeBloc')} />
            <Input label="Étage" placeholder="1" {...register('numEtage')} />
          </div>
          <div>
            <p className="text-sm font-medium text-brand-text mb-2">Équipements</p>
            <div className="flex flex-wrap gap-x-4 gap-y-2">
              {EQUIPEMENTS.map((eq) => (
                <label key={eq} className="flex items-center gap-1.5 text-sm text-brand-text">
                  <input type="checkbox" value={eq} {...register('equipements')} className="rounded border-brand-border" />
                  {EQUIPEMENT_LABELS[eq]}
                </label>
              ))}
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-brand-text">
            <input type="checkbox" {...register('estDisponible')} className="rounded border-brand-border" />
            Salle disponible pour la planification
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>{editing ? 'Mettre à jour' : 'Créer'}</Button>
          </div>
        </form>
      </Modal>

      <ImportModal open={importOpen} onClose={() => setImportOpen(false)} />

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idSalle)}
        loading={deleteMutation.isPending}
        title="Supprimer la salle"
        message={`Supprimer la salle "${deleteTarget?.codeSalle}" ? Cette action est irréversible.`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
