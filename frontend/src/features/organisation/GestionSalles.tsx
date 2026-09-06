import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, Upload, Building2, Users, Ban,
  Search, DoorOpen, FlaskConical, Monitor, X, CheckCircle
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
import { organisationApi, type Room, type RoomRequest, type RoomType } from '@/api/organisation.api'

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

/** Liseré de la carte : la même famille de couleurs que le badge du type. */
const TYPE_ACCENT: Record<RoomType, string> = {
  NORMALE: 'bg-slate-300 dark:bg-slate-600',
  LABSCIENCE: 'bg-emerald-500',
  LABPHYSIQUE: 'bg-blue-500',
  LABINFORMATIQUE: 'bg-amber-500',
  LABTECHNIQUE: 'bg-amber-600',
  SALLESPORT: 'bg-red-500',
  SALLEDESSIN: 'bg-blue-400',
  SALLEMUSIQUE: 'bg-violet-500',
  AMPHI: 'bg-amber-500',
  BIBLIOTHEQUE: 'bg-slate-400',
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
    indisponibles: rooms.filter((r) => r.estDisponible === false).length,
  }

  const filtered = rooms.filter((r) => {
    const q = search.toLowerCase()
    const matchSearch = !q || r.codeSalle.toLowerCase().includes(q) || (r.codeBloc ?? '').toLowerCase().includes(q)
    const matchType = !filterType || r.typeSalle === filterType
    return matchSearch && matchType
  })

  /**
   * Les salles se lisent bloc par bloc — c'est ainsi qu'on circule dans un
   * établissement, et c'est le regroupement qu'un surveillant a en tête. Une
   * liste à plat mélangeait le bloc A et le gymnase sans rien pour les séparer.
   *
   * `bloc` à `null` rassemble les salles auxquelles personne n'a donné de bloc :
   * elles doivent rester visibles, c'est justement ce qu'il faut compléter.
   */
  const blocs = [...new Set(filtered.map((r) => r.codeBloc || ''))]
    .sort((a, b) => (a === '' ? 1 : b === '' ? -1 : a.localeCompare(b)))
    .map((code) => ({
      bloc: code || null,
      salles: filtered.filter((r) => (r.codeBloc || '') === code),
    }))

  return (
    <div className="space-y-6">
      <PageHero
        title="Salles"
        subtitle="Les salles de l'établissement, groupées par bloc"
        icon={DoorOpen}
        actions={
          <>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => setImportOpen(true)}
            >
              <Upload size={16} /> Importer
            </Button>
            <Button
              className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
              onClick={() => { setEditing(null); setOpen(true) }}
            >
              <Plus size={16} /> Nouvelle salle
            </Button>
          </>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Total salles" value={stats.total} icon={DoorOpen} color="blue" />
        <StatCard title="Laboratoires" value={stats.labSci + stats.labPhy + stats.labTech} icon={FlaskConical} color="green" />
        <StatCard title="Salles informatique" value={stats.info} icon={Monitor} color="amber" />
        <StatCard title="Indisponibles" value={stats.indisponibles} icon={Ban} color="red" />
      </div>

      <div className="flex flex-wrap gap-3 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="relative min-w-48 flex-1">
          <Search size={16} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Rechercher par code ou bloc…"
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
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value as RoomType | '')}
          className="rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
        >
          <option value="">Tous les types</option>
          {ROOM_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select>
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-32 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && filtered.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <DoorOpen size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            {search || filterType ? 'Aucune salle ne correspond aux filtres' : 'Aucune salle enregistrée'}
          </p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            {search || filterType
              ? 'Élargissez la recherche ou changez le type.'
              : 'Sans salle, la génération de l’emploi du temps n’a nulle part où placer les séances.'}
          </p>
          <div className="mt-4 flex justify-center gap-2">
            <Button variant="outline" onClick={() => setImportOpen(true)}><Upload size={16} /> Importer</Button>
            <Button variant="outline" onClick={() => { setEditing(null); setOpen(true) }}><Plus size={16} /> Nouvelle salle</Button>
          </div>
        </div>
      )}

      {/* Un bloc par section : c'est ainsi qu'on circule dans l'établissement. */}
      <div className="space-y-6">
        {blocs.map((g) => (
          <section key={g.bloc ?? '__sans_bloc'}>
            <div className="mb-3 flex items-center gap-3">
              <h2 className={`flex items-center gap-1.5 text-sm font-semibold ${
                g.bloc === null ? 'text-amber-600 dark:text-amber-400' : 'text-brand-text dark:text-slate-100'
              }`}>
                <Building2 size={14} />
                {g.bloc === null ? 'Sans bloc' : `Bloc ${g.bloc}`}
              </h2>
              <span className="rounded-full bg-brand-bgSecondary px-2 py-0.5 text-xs font-medium tabular-nums text-brand-textMuted dark:bg-slate-800 dark:text-slate-400">
                {g.salles.length} salle{g.salles.length > 1 ? 's' : ''}
              </span>
              <span className="h-px flex-1 bg-brand-border dark:bg-slate-700" />
            </div>

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {g.salles.map((r) => {
                const t = TYPE_BADGE[r.typeSalle]
                const equipements = (r.equipements ?? '').split(',').map((e) => e.trim()).filter(Boolean)
                const indisponible = r.estDisponible === false
                return (
                  <div
                    key={r.idSalle}
                    className={`relative flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
                      indisponible ? 'opacity-70' : ''
                    }`}
                  >
                    <span className={cn('absolute inset-x-0 top-0 h-1', TYPE_ACCENT[r.typeSalle])} />

                    <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <h3 className="truncate font-mono text-lg font-bold text-brand-text dark:text-slate-100">{r.codeSalle}</h3>
                          <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
                            {r.numEtage ? `Étage ${r.numEtage}` : 'Étage non renseigné'}
                          </p>
                        </div>
                        {indisponible
                          ? <Badge variant="danger">Indisponible</Badge>
                          : <Badge variant={t.variant}>{t.label}</Badge>}
                      </div>

                      {indisponible && (
                        <p className="flex items-start gap-1.5 rounded-lg bg-red-50 p-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">
                          <Ban size={13} className="mt-px shrink-0" />
                          Écartée de la génération de l'emploi du temps. Type : {t.label}.
                        </p>
                      )}

                      <div className="flex items-center gap-1.5 text-sm text-brand-textMuted dark:text-slate-400">
                        <Users size={14} />
                        {r.capacite
                          ? <><strong className="font-semibold tabular-nums text-brand-text dark:text-slate-200">{r.capacite}</strong> places</>
                          : <span className="text-amber-600 dark:text-amber-400">Capacité non renseignée</span>}
                      </div>

                      {equipements.length > 0 && (
                        <div className="mt-auto flex flex-wrap gap-1.5">
                          {equipements.map((eq) => (
                            <span
                              key={eq}
                              className="rounded-md border border-brand-border bg-brand-bgSecondary px-2 py-0.5 text-[11px] font-medium text-brand-textMuted dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400"
                            >
                              {EQUIPEMENT_LABELS[eq] ?? eq}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    <div className="flex items-center justify-end gap-1 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                      <button
                        title="Modifier" onClick={() => { setEditing(r); setOpen(true) }}
                        className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                      >
                        <Pencil size={15} />
                      </button>
                      <button
                        title="Supprimer" onClick={() => setDeleteTarget(r)}
                        className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-500/10"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          </section>
        ))}
      </div>

      {/* Create / Edit modal */}
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Modifier la salle' : 'Nouvelle salle'}
        size="xl"
      >
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Code salle *" placeholder="S101" {...register('codeSalle')} error={errors.codeSalle?.message} />
            <Select label="Type *" options={ROOM_TYPES} {...register('typeSalle')} error={errors.typeSalle?.message} />
          </div>

          {/* Bloc, étage et capacité décrivent où la salle se trouve et
              combien elle reçoit — les trois données que la génération de
              l'emploi du temps consulte avant d'y placer une classe. */}
          <div className="rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-4 dark:border-slate-700 dark:bg-slate-800/40">
            <p className="mb-3 flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
              <Building2 size={14} className="text-brand-textMuted dark:text-slate-400" />
              Emplacement et capacité
            </p>
            <div className="grid gap-4 sm:grid-cols-3">
              <Input label="Code bloc" placeholder="A" {...register('codeBloc')} />
              <Input label="Étage" placeholder="1" {...register('numEtage')} />
              <Input label="Capacité (places)" type="number" min="1" placeholder="30" {...register('capacite')} error={errors.capacite?.message} />
            </div>
          </div>

          {/* Cases à cocher alignées → tuiles : on coche vite et on relit
              d'un coup d'œil ce dont la salle dispose. */}
          <div>
            <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">Équipements</p>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {EQUIPEMENTS.map((eq) => (
                <label
                  key={eq}
                  className="flex cursor-pointer items-center gap-2.5 rounded-lg border border-brand-border bg-white p-2.5 text-sm text-brand-text transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10"
                >
                  <input
                    type="checkbox" value={eq} {...register('equipements')}
                    className="h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
                  />
                  {EQUIPEMENT_LABELS[eq]}
                </label>
              ))}
            </div>
          </div>

          <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10">
            <input
              type="checkbox" {...register('estDisponible')}
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
            />
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                <CheckCircle size={14} className="text-brand-textMuted dark:text-slate-400" />
                Disponible pour la planification
              </span>
              <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                Décochée, la salle reste enregistrée mais aucune séance n'y sera placée — travaux, salle prêtée…
              </span>
            </span>
          </label>

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>
              {editing ? 'Mettre à jour' : 'Créer la salle'}
            </Button>
          </div>
        </form>
      </Modal>

      <ImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        titre="Importer des salles"
        entite={{ singulier: 'salle', pluriel: 'salles' }}
        genre="f"
        onImport={organisationApi.rooms.importFile}
        onImported={() => qc.invalidateQueries({ queryKey: ['rooms'] })}
        templates={{
          csv: organisationApi.rooms.downloadTemplateCsv,
          excel: organisationApi.rooms.downloadTemplateExcel,
          basename: 'template_salles',
        }}
      />

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
