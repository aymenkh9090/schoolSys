import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, Power, PowerOff, Layers, CheckCircle2, GraduationCap, BookOpen,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
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
  const totalClasses = levels.reduce((sum, l) => sum + (l.nombreClasses ?? 0), 0)

  return (
    <div className="space-y-6">
      <PageHero
        title="Niveaux scolaires"
        subtitle="La colonne vertébrale de l'établissement — chaque niveau porte ses classes et son programme"
        icon={Layers}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={() => { setEditing(null); setOpen(true) }}
          >
            <Plus size={16} /> Nouveau niveau
          </Button>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Total niveaux" value={levels.length} icon={Layers} color="blue" />
        <StatCard title="Actifs" value={actifs} icon={CheckCircle2} color="green" />
        <StatCard title="Inactifs" value={levels.length - actifs} icon={PowerOff} color="red" />
        <StatCard title="Classes rattachées" value={totalClasses} icon={GraduationCap} color="purple" />
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-40 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && levels.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <Layers size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">Aucun niveau configuré</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            Tout part d'ici : les classes, le programme et l'emploi du temps se rattachent à un niveau.
          </p>
          <Button className="mt-4" variant="outline" onClick={() => { setEditing(null); setOpen(true) }}>
            <Plus size={16} /> Nouveau niveau
          </Button>
        </div>
      )}

      {/* Une carte par niveau : le tableau alignait « Classes » et « Matières »
          en deux colonnes de chiffres nus, alors que ce sont les deux mesures
          qui disent si un niveau est prêt à recevoir un emploi du temps. */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {levels.map((l) => (
          <div
            key={l.idNiveau}
            className={`relative flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
              l.estActif ? '' : 'opacity-70'
            }`}
          >
            <span className={`absolute inset-x-0 top-0 h-1 ${l.estActif ? 'bg-brand-teal' : 'bg-slate-300 dark:bg-slate-600'}`} />

            <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">{l.nom}</h3>
                  <p className="mt-0.5 font-mono text-xs text-brand-textMuted dark:text-slate-400">{l.code}</p>
                </div>
                <Badge variant={l.estActif ? 'success' : 'danger'}>{l.estActif ? 'Actif' : 'Inactif'}</Badge>
              </div>

              {l.description && (
                <p className="line-clamp-2 text-xs text-brand-textMuted dark:text-slate-400">{l.description}</p>
              )}

              {/* Les deux chiffres qui comptent, lisibles au lieu d'alignés. */}
              <div className="mt-auto grid grid-cols-2 gap-2">
                <div className="rounded-lg bg-brand-bgSecondary/60 p-2.5 dark:bg-slate-800/50">
                  <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                    <GraduationCap size={13} /> Classes
                  </p>
                  <p className="mt-0.5 text-lg font-bold tabular-nums text-brand-text dark:text-slate-100">
                    {l.nombreClasses ?? 0}
                  </p>
                </div>
                <div className="rounded-lg bg-brand-bgSecondary/60 p-2.5 dark:bg-slate-800/50">
                  <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                    <BookOpen size={13} /> Matières
                  </p>
                  <p className={`mt-0.5 text-lg font-bold tabular-nums ${
                    (l.nombreMatieres ?? 0) === 0
                      ? 'text-amber-600 dark:text-amber-400'
                      : 'text-brand-text dark:text-slate-100'
                  }`}>
                    {l.nombreMatieres ?? 0}
                  </p>
                </div>
              </div>

              {(l.nombreMatieres ?? 0) === 0 && (
                <p className="text-xs text-amber-600 dark:text-amber-400">
                  Aucune matière au programme : ce niveau ne peut pas encore recevoir d'emploi du temps.
                </p>
              )}
            </div>

            <div className="flex items-center justify-end gap-1 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
              <button
                title={l.estActif ? 'Désactiver ce niveau' : 'Activer ce niveau'}
                onClick={() => toggleMutation.mutate({ id: l.idNiveau, estActif: !l.estActif })}
                className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
              >
                {l.estActif ? <PowerOff size={15} /> : <Power size={15} />}
              </button>
              <button
                title="Modifier" onClick={() => { setEditing(l); setOpen(true) }}
                className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
              >
                <Pencil size={15} />
              </button>
              <button
                title="Supprimer" onClick={() => setDeleteTarget(l)}
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
        title={editing ? 'Modifier le niveau' : 'Nouveau niveau'}
        size="xl"
      >
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Nom *" placeholder="7ème Année de Base" {...register('nom')} error={errors.nom?.message} />
            <Input label="Code *" placeholder="7EME" {...register('code')} error={errors.code?.message} />
          </div>

          <Input
            label="Description *" placeholder="Première année du collège"
            {...register('description')} error={errors.description?.message}
          />

          {/* Une case à cocher nue ne disait pas ce qu'elle déclenche : un
              niveau inactif disparaît des formulaires de création de classe. */}
          <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10">
            <input
              type="checkbox" id="estActif" {...register('estActif')}
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
            />
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                <CheckCircle2 size={14} className="text-brand-textMuted dark:text-slate-400" />
                Niveau actif
              </span>
              <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                Décoché, le niveau reste en base mais n'est plus proposé à la création d'une classe.
              </span>
            </span>
          </label>

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>
              {editing ? 'Mettre à jour' : 'Créer le niveau'}
            </Button>
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
