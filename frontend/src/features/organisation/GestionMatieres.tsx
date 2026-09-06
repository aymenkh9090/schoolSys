import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, BookOpen, Microscope, Search, Star, Dumbbell, EyeOff, Layers3, X,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { organisationApi, type Subject } from '@/api/organisation.api'

/**
 * Teintes proposées d'office. Le sélecteur natif du navigateur reste
 * disponible, mais une palette évite le rose fluo et donne au catalogue une
 * cohérence que huit choix libres ne produisent jamais — ces couleurs se
 * retrouvent ensuite sur la grille d'emploi du temps.
 */
const COULEURS = [
  '#3b82f6', '#0f766e', '#8b5cf6', '#ec4899', '#ef4444',
  '#f59e0b', '#10b981', '#0ea5e9', '#6366f1', '#64748b',
]

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
  const [recherche, setRecherche] = useState('')

  const { data: subjects = [], isLoading } = useQuery({
    queryKey: ['subjects'],
    queryFn: organisationApi.subjects.list,
  })

  const { register, handleSubmit, reset, watch, setValue, formState: { errors } } = useForm<FormData>({
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
  const nonEnseignees = subjects.filter((s) => !s.estEnseignee).length

  // Le catalogue d'un établissement dépasse vite la trentaine d'entrées et le
  // tableau n'offrait aucun moyen d'y retrouver une matière autrement qu'à l'œil.
  const q = recherche.trim().toLowerCase()
  const matieres = q
    ? subjects.filter((s) =>
        [s.libMatiere, s.codeMatiere, s.abreviation, s.description]
          .some((v) => v?.toLowerCase().includes(q)))
    : subjects

  const couleurCourante = watch('couleur') || '#3b82f6'
  const codeCourant = watch('codeMatiere')
  const libCourant = watch('libMatiere')

  return (
    <div className="space-y-6">
      <PageHero
        title="Matières"
        subtitle="Le catalogue de l'établissement — chaque matière et ses contraintes de salle"
        icon={BookOpen}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={() => { setEditing(null); setOpen(true) }}
          >
            <Plus size={16} /> Nouvelle matière
          </Button>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Total matières" value={subjects.length} icon={BookOpen} color="blue" />
        <StatCard title="Principales" value={principales} icon={Star} color="green" />
        <StatCard title="Nécessitent un labo" value={labo} icon={Microscope} color="yellow" />
        <StatCard title="Non enseignées" value={nonEnseignees} icon={EyeOff} color="red" />
      </div>

      <div className="relative max-w-sm">
        <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500" />
        <input
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
          placeholder="Rechercher une matière…"
          className="w-full rounded-lg border border-brand-border bg-white py-2 pl-9 pr-9 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
        />
        {recherche && (
          <button
            onClick={() => setRecherche('')}
            title="Effacer la recherche"
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-brand-textMuted hover:bg-brand-bgSecondary hover:text-brand-text dark:hover:bg-slate-800"
          >
            <X size={14} />
          </button>
        )}
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-36 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && matieres.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <BookOpen size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            {q ? `Aucune matière ne correspond à « ${recherche} »` : 'Aucune matière configurée'}
          </p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            {q
              ? 'Vérifiez l’orthographe, ou créez cette matière.'
              : 'Le catalogue est la première brique : les niveaux y puisent leur programme.'}
          </p>
          <Button className="mt-4" variant="outline" onClick={() => { setEditing(null); setOpen(true) }}>
            <Plus size={16} /> Nouvelle matière
          </Button>
        </div>
      )}

      {/* Une carte par matière : la couleur est l'identité de la matière sur
          toute la plateforme (grille d'emploi du temps, programme de l'école),
          et une pastille de douze pixels dans une cellule ne la portait pas. */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {matieres.map((s) => (
          <div
            key={s.idMatiere}
            className={`group relative flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
              s.estEnseignee ? '' : 'opacity-70'
            }`}
          >
            <span className="absolute inset-x-0 top-0 h-1" style={{ background: s.couleur || '#94a3b8' }} />

            <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
              <div className="flex items-start gap-3">
                {/* Le carré de couleur porte l'abréviation : c'est exactement
                    ce que l'utilisateur retrouvera dans une case d'emploi du temps. */}
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-xs font-bold uppercase text-white"
                  style={{ background: s.couleur || '#94a3b8' }}
                >
                  {(s.abreviation || s.codeMatiere || '?').slice(0, 4)}
                </span>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">{s.libMatiere}</h3>
                  <p className="mt-0.5 font-mono text-xs text-brand-textMuted dark:text-slate-400">{s.codeMatiere}</p>
                </div>
              </div>

              {s.description && (
                <p className="line-clamp-2 text-xs text-brand-textMuted dark:text-slate-400">{s.description}</p>
              )}

              <div className="flex flex-wrap gap-1.5">
                {s.estPrincipale && <Badge variant="info">Principale</Badge>}
                {s.necessiteLab && <Badge variant="warning">Labo requis</Badge>}
                {s.necessiteSport && <Badge variant="warning">Salle de sport</Badge>}
                {!s.estEnseignee && <Badge variant="danger">Non enseignée</Badge>}
              </div>
            </div>

            <div className="flex items-center justify-between gap-2 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
              <span className="inline-flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                <Layers3 size={13} />
                {s.nombreNiveaux ?? 0} niveau{(s.nombreNiveaux ?? 0) > 1 ? 'x' : ''}
              </span>
              <div className="flex items-center gap-1">
                <button
                  title="Modifier" onClick={() => { setEditing(s); setOpen(true) }}
                  className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:hover:bg-slate-700"
                >
                  <Pencil size={15} />
                </button>
                <button
                  title="Supprimer" onClick={() => setDeleteTarget(s)}
                  className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:hover:bg-red-500/10"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Modifier la matière' : 'Nouvelle matière'}
        size="xl"
      >
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
          {/* La matière telle qu'elle apparaîtra ailleurs. Choisir une couleur
              sans voir le résultat revenait à choisir à l'aveugle. */}
          <div className="flex items-center gap-3 rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40">
            <span
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-xs font-bold uppercase text-white"
              style={{ background: couleurCourante }}
            >
              {(watch('abreviation') || codeCourant || '?').slice(0, 4)}
            </span>
            <div className="min-w-0">
              <p className="truncate font-semibold text-brand-text dark:text-slate-100">
                {libCourant || 'Nom de la matière'}
              </p>
              <p className="font-mono text-xs text-brand-textMuted dark:text-slate-400">
                {codeCourant || 'CODE'}
              </p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <Input label="Code *" placeholder="MATH" {...register('codeMatiere')} error={errors.codeMatiere?.message} />
            <Input label="Abréviation" placeholder="Math" {...register('abreviation')} />
            <Input label="Libellé *" placeholder="Mathématiques" {...register('libMatiere')} error={errors.libMatiere?.message} />
          </div>

          <Input label="Description" placeholder="Optionnel — précise ce que recouvre la matière" {...register('description')} />

          <div>
            <label className="mb-2 block text-sm font-medium text-brand-text dark:text-slate-200">Couleur</label>
            <div className="flex flex-wrap items-center gap-2">
              {COULEURS.map((c) => (
                <button
                  key={c} type="button"
                  onClick={() => setValue('couleur', c, { shouldDirty: true })}
                  aria-label={`Couleur ${c}`} aria-pressed={couleurCourante.toLowerCase() === c}
                  className={`h-8 w-8 rounded-lg transition-transform hover:scale-110 ${
                    couleurCourante.toLowerCase() === c
                      ? 'ring-2 ring-brand-teal ring-offset-2 dark:ring-offset-slate-900'
                      : ''
                  }`}
                  style={{ background: c }}
                />
              ))}
              <span className="mx-1 h-6 w-px bg-brand-border dark:bg-slate-700" />
              <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-brand-textMuted dark:text-slate-400">
                <input
                  type="color" {...register('couleur')} defaultValue="#3b82f6"
                  className="h-8 w-10 cursor-pointer rounded border border-brand-border bg-transparent dark:border-slate-700"
                />
                Autre
              </label>
            </div>
          </div>

          {/* Quatre cases à cocher alignées ne disaient pas ce qu'elles
              déclenchent. Chaque caractéristique porte désormais sa conséquence :
              trois d'entre elles pèsent sur la génération de l'emploi du temps. */}
          <div>
            <label className="mb-2 block text-sm font-medium text-brand-text dark:text-slate-200">Caractéristiques</label>
            <div className="grid gap-2 sm:grid-cols-2">
              {([
                ['estPrincipale', Star, 'Matière principale', 'Comptée dans les indicateurs de suivi et priorisée à la génération.'],
                ['estEnseignee', BookOpen, 'Enseignée', 'Décochée, la matière reste au catalogue mais sort des programmes.'],
                ['necessiteLab', Microscope, 'Nécessite un laboratoire', 'Ses séances ne seront placées que dans une salle de labo.'],
                ['necessiteSport', Dumbbell, 'Nécessite une salle de sport', 'Ses séances ne seront placées qu’en salle de sport.'],
              ] as const).map(([field, Icon, titre, aide]) => (
                <label
                  key={field}
                  className="flex cursor-pointer items-start gap-3 rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10"
                >
                  <input
                    type="checkbox" {...register(field)}
                    className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
                  />
                  <span className="min-w-0">
                    <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                      <Icon size={14} className="text-brand-textMuted dark:text-slate-400" />
                      {titre}
                    </span>
                    <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">{aide}</span>
                  </span>
                </label>
              ))}
            </div>
          </div>

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>
              {editing ? 'Mettre à jour' : 'Créer la matière'}
            </Button>
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
