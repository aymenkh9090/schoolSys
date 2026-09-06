import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, Power, PowerOff, GraduationCap, Users, CheckCircle2,
  Layers, Link2,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
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
  const [selectedLevelId, setSelectedLevelId] = useState<number | null>(null)
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

  // Le filtre par niveau se pose sur le résultat déjà filtré par année : le
  // backend expose les classes par année, le niveau n'est qu'une projection.
  const classesFiltrees = selectedLevelId === null
    ? classes
    : classes.filter((c) => c.levelId === selectedLevelId)

  const actives = classesFiltrees.filter((c) => c.estActif).length
  const totalEleves = classesFiltrees.reduce((s, c) => s + (c.nbEleve ?? 0), 0)

  /**
   * Les classes se lisent niveau par niveau — c'est ainsi qu'on les crée, qu'on
   * les dote d'un programme et qu'on leur génère un emploi du temps. Une liste
   * à plat mélangeait la 7ème et la terminale sans que rien ne les sépare.
   *
   * `titre` à `null` désigne le groupe des classes dont le niveau a disparu du
   * référentiel : elles seraient invisibles sans lui, et c'est justement le cas
   * qu'un administrateur doit voir.
   */
  const groupes: { id: number | 'orphelines'; titre: string | null; code: string; classes: SchoolClass[] }[] = [
    ...levels
      .map((l) => ({
        id: l.idNiveau,
        titre: l.nom,
        code: l.code,
        classes: classesFiltrees.filter((c) => c.levelId === l.idNiveau),
      }))
      .filter((g) => g.classes.length > 0),
  ]
  const orphelines = classesFiltrees.filter((c) => !levels.some((l) => l.idNiveau === c.levelId))
  if (orphelines.length > 0) {
    groupes.push({ id: 'orphelines', titre: null, code: '', classes: orphelines })
  }

  return (
    <div className="space-y-6">
      <PageHero
        title="Classes"
        subtitle="Les classes de l'établissement, groupées par niveau"
        icon={GraduationCap}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={() => { setEditing(null); setOpen(true) }}
          >
            <Plus size={16} /> Nouvelle classe
          </Button>
        }
      />

      {/* Deux filtres, deux natures : l'année interroge le backend, le niveau
          trie ce qui est déjà là. D'où le menu déroulant pour la première et
          des pastilles pour le second — la réponse est immédiate. */}
      <div className="space-y-4 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-wrap items-center gap-2">
          <label className="text-sm font-medium text-brand-text dark:text-slate-200">Année scolaire</label>
          <select
            value={selectedYearId ?? ''}
            onChange={(e) => setSelectedYearId(e.target.value ? Number(e.target.value) : null)}
            className="rounded-lg border border-brand-border bg-white px-3 py-1.5 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          >
            <option value="">Toutes les années</option>
            {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
          </select>
        </div>

        <div>
          <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">Niveau</p>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setSelectedLevelId(null)}
              aria-pressed={selectedLevelId === null}
              className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                selectedLevelId === null
                  ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                  : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
              }`}
            >
              <Layers size={14} />
              Tous les niveaux
              <span className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${
                selectedLevelId === null ? 'bg-white/20 text-white' : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
              }`}>
                {classes.length}
              </span>
            </button>

            {levels.map((l) => {
              const nb = classes.filter((c) => c.levelId === l.idNiveau).length
              const actif = l.idNiveau === selectedLevelId
              return (
                <button
                  key={l.idNiveau}
                  onClick={() => setSelectedLevelId(actif ? null : l.idNiveau)}
                  aria-pressed={actif}
                  // Un niveau sans classe pour l'année choisie reste cliquable :
                  // son état vide dit quelque chose — il faut créer ses classes.
                  className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                    actif
                      ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                      : nb === 0
                        ? 'border-dashed border-brand-border bg-white text-brand-textMuted hover:bg-brand-bgSecondary/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-500 dark:hover:bg-slate-800'
                        : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                  }`}
                >
                  {l.nom}
                  <span className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${
                    actif ? 'bg-white/20 text-white' : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
                  }`}>
                    {nb}
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      </div>

      {/* Les compteurs suivent les filtres : afficher le total de
          l'établissement au-dessus d'une liste filtrée serait un mensonge. */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Classes affichées" value={classesFiltrees.length} icon={GraduationCap} color="blue" />
        <StatCard title="Actives" value={actives} icon={CheckCircle2} color="green" />
        <StatCard title="Inactives" value={classesFiltrees.length - actives} icon={PowerOff} color="red" />
        <StatCard title="Élèves" value={totalEleves} icon={Users} color="purple" />
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-32 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && classesFiltrees.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <GraduationCap size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            {selectedLevelId !== null
              ? `Aucune classe pour ${levels.find((l) => l.idNiveau === selectedLevelId)?.nom ?? 'ce niveau'}`
              : 'Aucune classe trouvée'}
          </p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            {selectedLevelId !== null
              ? 'Ce niveau existe mais n’a pas encore de classe pour l’année sélectionnée.'
              : 'Créez une première classe, ou élargissez le filtre sur l’année.'}
          </p>
          <Button className="mt-4" variant="outline" onClick={() => { setEditing(null); setOpen(true) }}>
            <Plus size={16} /> Nouvelle classe
          </Button>
        </div>
      )}

      {/* Un bloc par niveau, dans l'ordre du référentiel. */}
      <div className="space-y-6">
        {groupes.map((g) => (
          <section key={g.id}>
            <div className="mb-3 flex items-center gap-3">
              <h2 className={`text-sm font-semibold ${
                g.titre === null ? 'text-amber-600 dark:text-amber-400' : 'text-brand-text dark:text-slate-100'
              }`}>
                {g.titre ?? 'Niveau introuvable'}
              </h2>
              {g.code && (
                <span className="font-mono text-xs text-brand-textMuted dark:text-slate-400">{g.code}</span>
              )}
              <span className="rounded-full bg-brand-bgSecondary px-2 py-0.5 text-xs font-medium tabular-nums text-brand-textMuted dark:bg-slate-800 dark:text-slate-400">
                {g.classes.length} classe{g.classes.length > 1 ? 's' : ''}
              </span>
              <span className="h-px flex-1 bg-brand-border dark:bg-slate-700" />
            </div>

            {g.titre === null && (
              <p className="mb-3 text-xs text-amber-600 dark:text-amber-400">
                Ces classes référencent un niveau absent du référentiel — rattachez-les à un niveau existant.
              </p>
            )}

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {g.classes.map((c) => (
                <div
                  key={c.idClasse}
                  className={`relative flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
                    c.estActif ? '' : 'opacity-70'
                  }`}
                >
                  <span className={`absolute inset-x-0 top-0 h-1 ${c.estActif ? 'bg-brand-teal' : 'bg-slate-300 dark:bg-slate-600'}`} />

                  <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="truncate font-mono text-lg font-bold text-brand-text dark:text-slate-100">{c.code}</h3>
                        <p className="mt-0.5 truncate text-xs text-brand-textMuted dark:text-slate-400">{c.schoolYearNom}</p>
                      </div>
                      {!c.estActif && <Badge variant="danger">Inactive</Badge>}
                    </div>

                    <Badge variant="default">{SPECIALITE_LABELS[c.codeSpecialite] ?? c.codeSpecialite}</Badge>

                    <div className="mt-auto flex items-center gap-4 text-xs text-brand-textMuted dark:text-slate-400">
                      <span className="inline-flex items-center gap-1.5">
                        <Users size={13} />
                        <strong className="font-semibold tabular-nums text-brand-text dark:text-slate-200">{c.nbEleve ?? '—'}</strong>
                        élève{(c.nbEleve ?? 0) > 1 ? 's' : ''}
                      </span>
                      <span className="inline-flex items-center gap-1.5">
                        <Link2 size={13} />
                        <strong className="font-semibold tabular-nums text-brand-text dark:text-slate-200">{c.nombreAffectations ?? 0}</strong>
                        affectation{(c.nombreAffectations ?? 0) > 1 ? 's' : ''}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center justify-end gap-1 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                    <button
                      title={c.estActif ? 'Désactiver cette classe' : 'Activer cette classe'}
                      onClick={() => toggleMutation.mutate({ id: c.idClasse, estActif: !c.estActif })}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                    >
                      {c.estActif ? <PowerOff size={15} /> : <Power size={15} />}
                    </button>
                    <button
                      title="Modifier" onClick={() => { setEditing(c); setOpen(true) }}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                    >
                      <Pencil size={15} />
                    </button>
                    <button
                      title="Supprimer" onClick={() => setDeleteTarget(c)}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:text-slate-400 dark:hover:bg-red-500/10"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </section>
        ))}
      </div>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Modifier la classe' : 'Nouvelle classe'}
        size="xl"
      >
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Code *" placeholder="7A" {...register('code')} error={errors.code?.message} />
            <div>
              <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Spécialité *</label>
              <select
                {...register('codeSpecialite')}
                className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              >
                {SPECIALITES.map((s) => <option key={s} value={s}>{SPECIALITE_LABELS[s]}</option>)}
              </select>
            </div>
          </div>

          {/* Année et niveau déterminent à eux deux où la classe apparaîtra
              dans la liste — ils méritent d'être groupés, et signalés comme
              structurants plutôt que noyés parmi les autres champs. */}
          <div className="rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-4 dark:border-slate-700 dark:bg-slate-800/40">
            <p className="mb-3 flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
              <Layers size={14} className="text-brand-textMuted dark:text-slate-400" />
              Rattachement
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Année scolaire *</label>
                <select
                  {...register('schoolYearId')}
                  className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                >
                  <option value="">Choisir…</option>
                  {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
                </select>
                {errors.schoolYearId && <p className="mt-1 text-xs text-danger">{errors.schoolYearId.message}</p>}
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Niveau *</label>
                <select
                  {...register('levelId')}
                  className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                >
                  <option value="">Choisir…</option>
                  {levels.filter((l) => l.estActif).map((l) => <option key={l.idNiveau} value={l.idNiveau}>{l.nom}</option>)}
                </select>
                {errors.levelId && <p className="mt-1 text-xs text-danger">{errors.levelId.message}</p>}
              </div>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Nombre d'élèves" type="number" min="0" {...register('nbEleve')} />
            <label className="flex cursor-pointer items-start gap-3 self-end rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10">
              <input
                type="checkbox" {...register('estActif')}
                className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
              />
              <span className="min-w-0">
                <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                  <CheckCircle2 size={14} className="text-brand-textMuted dark:text-slate-400" />
                  Classe active
                </span>
                <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                  Décochée, la classe est conservée mais sort de la génération de l'emploi du temps.
                </span>
              </span>
            </label>
          </div>

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>
              {editing ? 'Mettre à jour' : 'Créer la classe'}
            </Button>
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
