import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, Power, PowerOff, Star, CalendarDays,
  GraduationCap, Link2, CheckCircle2, ArrowRight,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { DateInputFR } from '@/components/ui/DateInputFR'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
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

  return (
    <div className="space-y-6">
      <PageHero
        title="Années scolaires"
        subtitle="Le calendrier de l'établissement — classes, affectations et emplois du temps s'y rattachent"
        icon={CalendarDays}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={() => { setEditing(null); setOpen(true) }}
          >
            <Plus size={16} /> Nouvelle année
          </Button>
        }
      />

      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard title="Total années" value={years.length} icon={CalendarDays} color="blue" />
        <StatCard title="Actives" value={actives} icon={CheckCircle2} color="green" />
        <StatCard title="Année courante" value={courante?.nom ?? '—'} icon={Star} color="purple" />
      </div>

      {/* Aucune année courante : rien ne le signalait, et c'est pourtant ce qui
          détermine l'année proposée par défaut dans tout le reste de l'application. */}
      {!isLoading && years.length > 0 && !courante && (
        <p className="flex items-start gap-1.5 rounded-lg bg-amber-50 p-3 text-sm text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
          <Star size={14} className="mt-0.5 shrink-0" />
          Aucune année n'est marquée « courante ». Les écrans qui en proposent une par défaut
          resteront vides tant qu'une année ne sera pas désignée.
        </p>
      )}

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-44 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && years.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <CalendarDays size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">Aucune année scolaire configurée</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            C'est le point de départ : une classe, une affectation et un emploi du temps se
            rattachent tous à une année.
          </p>
          <Button className="mt-4" variant="outline" onClick={() => { setEditing(null); setOpen(true) }}>
            <Plus size={16} /> Nouvelle année
          </Button>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {years.map((y) => (
          <div
            key={y.idAnnee}
            className={`relative flex flex-col overflow-hidden rounded-xl border bg-white transition-shadow hover:shadow-md dark:bg-slate-900 ${
              y.estCourante
                ? 'border-brand-teal ring-1 ring-brand-teal/30'
                : 'border-brand-border dark:border-slate-700'
            } ${y.estActive ? '' : 'opacity-70'}`}
          >
            <span className={`absolute inset-x-0 top-0 h-1 ${
              y.estCourante ? 'bg-brand-teal' : y.estActive ? 'bg-brand-blue' : 'bg-slate-300 dark:bg-slate-600'
            }`} />

            <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="truncate text-lg font-bold text-brand-text dark:text-slate-100">{y.nom}</h3>
                  {/* Les deux dates lues comme une période, pas comme deux colonnes. */}
                  <p className="mt-0.5 flex items-center gap-1.5 text-xs tabular-nums text-brand-textMuted dark:text-slate-400">
                    {formatDate(y.dateDebut)}
                    <ArrowRight size={11} className="shrink-0" />
                    {formatDate(y.dateFin)}
                  </p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {y.estCourante && (
                    <Badge variant="info"><Star size={10} className="me-0.5 inline" />Courante</Badge>
                  )}
                  {!y.estActive && <Badge variant="danger">Inactive</Badge>}
                </div>
              </div>

              <div className="mt-auto grid grid-cols-2 gap-2">
                <div className="rounded-lg bg-brand-bgSecondary/60 p-2.5 dark:bg-slate-800/50">
                  <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                    <GraduationCap size={13} /> Classes
                  </p>
                  <p className="mt-0.5 text-lg font-bold tabular-nums text-brand-text dark:text-slate-100">
                    {y.nombreClasses ?? 0}
                  </p>
                </div>
                <div className="rounded-lg bg-brand-bgSecondary/60 p-2.5 dark:bg-slate-800/50">
                  <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                    <Link2 size={13} /> Affectations
                  </p>
                  <p className="mt-0.5 text-lg font-bold tabular-nums text-brand-text dark:text-slate-100">
                    {y.nombreAffectations ?? 0}
                  </p>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-1 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
              <button
                title={y.estActive ? 'Désactiver cette année' : 'Activer cette année'}
                onClick={() => toggleMutation.mutate({ id: y.idAnnee, active: !y.estActive })}
                className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
              >
                {y.estActive ? <PowerOff size={15} /> : <Power size={15} />}
              </button>
              <button
                title="Modifier" onClick={() => { setEditing(y); setOpen(true) }}
                className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
              >
                <Pencil size={15} />
              </button>
              <button
                title="Supprimer" onClick={() => setDeleteTarget(y)}
                className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-500/10"
              >
                <Trash2 size={15} />
              </button>
            </div>
          </div>
        ))}
      </div>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? "Modifier l'année scolaire" : 'Nouvelle année scolaire'}
        size="xl"
      >
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
          <Input label="Nom *" placeholder="2024-2025" {...register('nom')} error={errors.nom?.message} />
          <div className="grid gap-4 sm:grid-cols-2">
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
          {/* « Active » et « courante » se ressemblent et ne font pas la même
              chose : deux cases nues laissaient deviner laquelle choisir. */}
          <div className="grid gap-2 sm:grid-cols-2">
            <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10">
              <input
                type="checkbox" {...register('estActive')}
                className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
              />
              <span className="min-w-0">
                <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                  <CheckCircle2 size={14} className="text-brand-textMuted dark:text-slate-400" />
                  Année active
                </span>
                <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                  Elle reste proposée à la création d'une classe ou d'une affectation.
                </span>
              </span>
            </label>

            <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10">
              <input
                type="checkbox" {...register('estCourante')}
                className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
              />
              <span className="min-w-0">
                <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                  <Star size={14} className="text-brand-textMuted dark:text-slate-400" />
                  Année courante
                </span>
                <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                  Celle que l'application propose par défaut. Une seule à la fois.
                </span>
              </span>
            </label>
          </div>

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>
              {editing ? 'Mettre à jour' : "Créer l'année"}
            </Button>
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
