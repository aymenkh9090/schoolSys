import { useState, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, Power, PowerOff, AlertTriangle, Link, Search, X,
  CheckCircle2, Clock, User, Info,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { organisationApi, type TeachingAssignmentFull } from '@/api/organisation.api'

const SESSION_TYPE_LABELS: Record<string, string> = {
  COURSE: 'Cours', TD: 'TD', TP: 'TP', LAB: 'Labo', SPORT: 'Sport', EXAM: 'Examen',
}

const OBLIGATOIRE = 'Ce champ est obligatoire'
const champObligatoire = () => z.coerce.number({ error: OBLIGATOIRE }).min(1, OBLIGATOIRE)

const schema = z.object({
  schoolYearId: champObligatoire(),
  teacherId: champObligatoire(),
  classGroupId: champObligatoire(),
  subjectLevelId: champObligatoire(),
  subjectSessionTypeId: champObligatoire(),
  priority: z.coerce.number().optional(),
  isActive: z.boolean().optional(),
})

type FormData = z.infer<typeof schema>

export default function GestionAffectations() {
  const qc = useQueryClient()
  const [selectedYearId, setSelectedYearId] = useState<number | null>(null)
  const [recherche, setRecherche] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<TeachingAssignmentFull | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<TeachingAssignmentFull | null>(null)

  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: teachers = [] } = useQuery({ queryKey: ['teachers-actifs'], queryFn: organisationApi.teachers.getActifs })

  const { data: affectations = [], isLoading } = useQuery({
    queryKey: ['teaching-assignments', selectedYearId],
    queryFn: () => selectedYearId ? organisationApi.teachingAssignments.byYear(selectedYearId) : organisationApi.teachingAssignments.list(),
  })

  const { data: missing = [] } = useQuery({
    queryKey: ['teaching-assignments-missing', selectedYearId],
    queryFn: () => organisationApi.teachingAssignments.missing(selectedYearId!),
    enabled: selectedYearId !== null,
  })

  const { register, handleSubmit, reset, resetField, watch, setValue, formState: { errors } } = useForm<z.input<typeof schema>, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: { isActive: true, priority: 1 },
  })

  const prevEditing = useRef<TeachingAssignmentFull | null>(null)
  if (editing !== prevEditing.current) {
    prevEditing.current = editing
    reset(editing
      ? { schoolYearId: editing.schoolYearId, teacherId: editing.teacherId, classGroupId: editing.classGroupId, subjectLevelId: editing.subjectLevelId, subjectSessionTypeId: editing.subjectSessionTypeId, priority: editing.priority, isActive: editing.isActive }
      : { schoolYearId: selectedYearId ?? undefined, isActive: true, priority: 1 }
    )
  }

  // ── Cascade : année → classe → matière-niveau → type de séance ─────────────
  const watchedSchoolYearId = Number(watch('schoolYearId')) || undefined
  const watchedClassGroupId = Number(watch('classGroupId')) || undefined
  const watchedSubjectLevelId = Number(watch('subjectLevelId')) || undefined

  const { data: formClasses = [] } = useQuery({
    queryKey: ['classes-by-year', watchedSchoolYearId],
    queryFn: () => organisationApi.classes.byYear(watchedSchoolYearId!),
    enabled: !!watchedSchoolYearId,
  })
  const selectedClass = formClasses.find((c) => c.idClasse === watchedClassGroupId)

  const { data: formSubjectLevels = [] } = useQuery({
    queryKey: ['subject-levels-by-level', selectedClass?.levelId],
    queryFn: () => organisationApi.subjects.subjectLevels.byLevel(selectedClass!.levelId),
    enabled: !!selectedClass,
  })
  const selectedSubjectLevel = formSubjectLevels.find((sl) => sl.idNiveauMatiere === watchedSubjectLevelId)
  const sessionTypeOptions = selectedSubjectLevel?.sessionTypes ?? []

  // Réapplique la valeur sélectionnée une fois les options en cascade chargées (mode édition)
  useEffect(() => {
    if (editing && formClasses.some((c) => c.idClasse === editing.classGroupId)) {
      setValue('classGroupId', editing.classGroupId)
    }
  }, [editing, formClasses, setValue])
  useEffect(() => {
    if (editing && formSubjectLevels.some((sl) => sl.idNiveauMatiere === editing.subjectLevelId)) {
      setValue('subjectLevelId', editing.subjectLevelId)
    }
  }, [editing, formSubjectLevels, setValue])
  useEffect(() => {
    if (editing && sessionTypeOptions.some((st) => st.idSubjectSessionType === editing.subjectSessionTypeId)) {
      setValue('subjectSessionTypeId', editing.subjectSessionTypeId)
    }
  }, [editing, sessionTypeOptions, setValue])

  const saveMutation = useMutation({
    mutationFn: (d: FormData) =>
      editing
        ? organisationApi.teachingAssignments.update(editing.idTeachingAssignment, d)
        : organisationApi.teachingAssignments.create(d),
    onSuccess: () => {
      toast.success(editing ? 'Affectation mise à jour' : 'Affectation créée')
      qc.invalidateQueries({ queryKey: ['teaching-assignments'] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, isActive }: { id: number; isActive: boolean }) =>
      organisationApi.teachingAssignments.toggleStatus(id, isActive),
    onSuccess: () => {
      toast.success('Statut mis à jour')
      qc.invalidateQueries({ queryKey: ['teaching-assignments'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.teachingAssignments.delete(id),
    onSuccess: () => {
      toast.success('Affectation supprimée')
      qc.invalidateQueries({ queryKey: ['teaching-assignments'] })
      setDeleteTarget(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const openCreate = () => {
    setEditing(null)
    prevEditing.current = null
    reset({ schoolYearId: selectedYearId ?? undefined, isActive: true, priority: 1 })
    setOpen(true)
  }

  const q = recherche.trim().toLowerCase()
  const affectationsFiltrees = q
    ? affectations.filter((a) =>
        [a.teacherNomComplet, a.classGroupCode, a.subjectLib, a.levelNom]
          .some((v) => v?.toLowerCase().includes(q)))
    : affectations

  const actives = affectationsFiltrees.filter((a) => a.isActive).length

  /**
   * Regroupées par enseignant : une affectation ne se lit jamais seule. Ce
   * qu'on vérifie sur cet écran, c'est ce qu'un enseignant donné assure — la
   * liste à plat obligeait à rassembler ses lignes de l'œil.
   */
  const parEnseignant = [...new Map(
    affectationsFiltrees.map((a) => [a.teacherId, a.teacherNomComplet])
  )]
    .sort((a, b) => (a[1] ?? '').localeCompare(b[1] ?? ''))
    .map(([teacherId, nom]) => {
      const lignes = affectationsFiltrees.filter((a) => a.teacherId === teacherId)
      return {
        teacherId,
        nom,
        lignes,
        heures: lignes.filter((a) => a.isActive).reduce((sum, a) => sum + (a.sessionDuration ?? 0), 0),
      }
    })

  return (
    <div className="space-y-6">
      <PageHero
        title="Affectations"
        subtitle="Qui enseigne quoi, à quelle classe — la base de la génération de l'emploi du temps"
        icon={Link}
        actions={
          <Button
            className="bg-white/15 text-white hover:bg-white/25 backdrop-blur"
            onClick={openCreate}
          >
            <Plus size={16} /> Nouvelle affectation
          </Button>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Affectations affichées" value={affectationsFiltrees.length} icon={Link} color="blue" />
        <StatCard title="Actives" value={actives} icon={CheckCircle2} color="green" />
        <StatCard title="Enseignants concernés" value={parEnseignant.length} icon={User} color="purple" />
        <StatCard title="Manquantes" value={missing.length} icon={AlertTriangle} color="red" />
      </div>

      {/* Ce qui manque passe avant ce qui existe : sans ces affectations, la
          génération de l'emploi du temps laissera des créneaux vides. */}
      {missing.length > 0 && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 dark:border-amber-500/20 dark:bg-amber-500/10">
          <div className="mb-2 flex items-center gap-2">
            <AlertTriangle size={16} className="text-amber-600 dark:text-amber-400" />
            <p className="text-sm font-semibold text-amber-800 dark:text-amber-300">
              {missing.length} affectation{missing.length > 1 ? 's' : ''} manquante{missing.length > 1 ? 's' : ''}
            </p>
          </div>
          <p className="mb-3 text-xs text-amber-700 dark:text-amber-400">
            Ces couples classe — matière sont au programme mais n'ont aucun enseignant :
            leurs séances ne pourront pas être placées.
          </p>
          <div className="flex max-h-32 flex-wrap gap-1.5 overflow-y-auto">
            {missing.map((m, i) => (
              <span
                key={i}
                className="rounded-md border border-amber-200 bg-white px-2 py-1 text-xs text-brand-text dark:border-amber-500/20 dark:bg-slate-900 dark:text-slate-200"
              >
                <span className="font-semibold">{m.classGroupCode}</span> — {m.subjectCode}
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-center gap-2">
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

        <div className="relative min-w-[200px] max-w-xs flex-1">
          <Search size={16} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500" />
          <input
            value={recherche}
            onChange={(e) => setRecherche(e.target.value)}
            placeholder="Enseignant, classe, matière…"
            className="w-full rounded-lg border border-brand-border bg-white py-2 pe-9 ps-9 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          />
          {recherche && (
            <button
              onClick={() => setRecherche('')}
              title="Effacer la recherche"
              className="absolute end-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-brand-textMuted hover:bg-brand-bgSecondary hover:text-brand-text dark:hover:bg-slate-800"
            >
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-32 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
          ))}
        </div>
      )}

      {!isLoading && affectationsFiltrees.length === 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <Link size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">
            {q ? `Aucune affectation ne correspond à « ${recherche} »` : 'Aucune affectation'}
          </p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            {q
              ? 'Vérifiez l’orthographe, ou élargissez le filtre sur l’année.'
              : 'Une affectation relie un enseignant à une classe pour une matière donnée.'}
          </p>
          {!q && (
            <Button className="mt-4" variant="outline" onClick={openCreate}>
              <Plus size={16} /> Nouvelle affectation
            </Button>
          )}
        </div>
      )}

      {/* Un bloc par enseignant, avec le total d'heures qu'il assure. */}
      <div className="space-y-6">
        {parEnseignant.map((g) => (
          <section key={g.teacherId}>
            <div className="mb-3 flex items-center gap-3">
              <h2 className="flex items-center gap-1.5 text-sm font-semibold text-brand-text dark:text-slate-100">
                <User size={14} /> {g.nom}
              </h2>
              <span className="rounded-full bg-brand-bgSecondary px-2 py-0.5 text-xs font-medium tabular-nums text-brand-textMuted dark:bg-slate-800 dark:text-slate-400">
                {g.lignes.length} affectation{g.lignes.length > 1 ? 's' : ''}
              </span>
              {g.heures > 0 && (
                <span className="inline-flex items-center gap-1 rounded-full bg-teal-50 px-2 py-0.5 text-xs font-medium tabular-nums text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
                  <Clock size={11} /> {g.heures}h/sem
                </span>
              )}
              <span className="h-px flex-1 bg-brand-border dark:bg-slate-700" />
            </div>

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {g.lignes.map((a) => (
                <div
                  key={a.idTeachingAssignment}
                  className={`relative flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900 ${
                    a.isActive ? '' : 'opacity-70'
                  }`}
                >
                  <span
                    className="absolute inset-x-0 top-0 h-1"
                    style={{ background: a.subjectCouleur || '#94a3b8' }}
                  />

                  <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">{a.subjectLib}</h3>
                        <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">{a.levelNom}</p>
                      </div>
                      {!a.isActive && <Badge variant="danger">Inactive</Badge>}
                    </div>

                    <div className="flex flex-wrap items-center gap-1.5">
                      <Badge variant="info">{a.classGroupCode}</Badge>
                      <Badge variant="default">{a.sessionDuration}h / séance</Badge>
                      {(a.priority ?? 1) !== 1 && <Badge variant="warning">Priorité {a.priority}</Badge>}
                    </div>
                  </div>

                  <div className="flex items-center justify-end gap-1 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                    <button
                      title={a.isActive ? 'Désactiver cette affectation' : 'Réactiver cette affectation'}
                      onClick={() => toggleMutation.mutate({ id: a.idTeachingAssignment, isActive: !a.isActive })}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                    >
                      {a.isActive ? <PowerOff size={15} /> : <Power size={15} />}
                    </button>
                    <button
                      title="Modifier" onClick={() => { setEditing(a); setOpen(true) }}
                      className="rounded-md p-1.5 text-brand-textMuted hover:bg-white hover:text-brand-text dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                    >
                      <Pencil size={15} />
                    </button>
                    <button
                      title="Supprimer" onClick={() => setDeleteTarget(a)}
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
        title={editing ? "Modifier l'affectation" : 'Nouvelle affectation'}
        size="xl"
      >
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-5">
          {/* Les quatre premiers champs se déverrouillent l'un après l'autre :
              le dire évite de croire à un formulaire cassé quand « Matière »
              reste grisée. */}
          <p className="flex items-start gap-1.5 rounded-lg bg-blue-50 p-3 text-xs text-blue-700 dark:bg-blue-500/10 dark:text-blue-400">
            <Info size={13} className="mt-px shrink-0" />
            Les champs se remplissent en cascade : l'année ouvre les classes, la classe ouvre
            les matières de son niveau, la matière ouvre ses types de séance.
          </p>

          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Année scolaire *"
              placeholder="Sélectionner une année"
              options={years.map((y) => ({ value: y.idAnnee, label: y.nom }))}
              error={errors.schoolYearId?.message}
              {...register('schoolYearId', {
                onChange: () => { resetField('classGroupId'); resetField('subjectLevelId'); resetField('subjectSessionTypeId') },
              })}
            />
            <Select
              label="Enseignant *"
              placeholder="Sélectionner un enseignant"
              options={teachers.map((t) => ({ value: t.idEnseignant, label: `${t.nomComplet} — ${t.specialite}` }))}
              error={errors.teacherId?.message}
              {...register('teacherId')}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Groupe classe *"
              placeholder={watchedSchoolYearId ? 'Sélectionner une classe' : "Choisissez d'abord l'année"}
              disabled={!watchedSchoolYearId}
              options={formClasses.map((c) => ({ value: c.idClasse, label: `${c.code} — ${c.levelNom}` }))}
              error={errors.classGroupId?.message}
              {...register('classGroupId', {
                onChange: () => { resetField('subjectLevelId'); resetField('subjectSessionTypeId') },
              })}
            />
            <Select
              label="Matière (niveau) *"
              placeholder={selectedClass ? 'Sélectionner une matière' : "Choisissez d'abord la classe"}
              disabled={!selectedClass}
              options={formSubjectLevels.map((sl) => ({ value: sl.idNiveauMatiere, label: `${sl.subjectLib} — ${sl.levelNom}` }))}
              error={errors.subjectLevelId?.message}
              {...register('subjectLevelId', { onChange: () => resetField('subjectSessionTypeId') })}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Type de séance *"
              placeholder={
                !selectedSubjectLevel
                  ? "Choisissez d'abord la matière"
                  : sessionTypeOptions.length === 0
                    ? 'Aucun type de séance disponible'
                    : 'Sélectionner un type'
              }
              disabled={!selectedSubjectLevel || sessionTypeOptions.length === 0}
              options={sessionTypeOptions.map((st) => ({
                value: st.idSubjectSessionType,
                label: `${SESSION_TYPE_LABELS[st.type] ?? st.type} (${st.duration}h)${st.requiresSplit ? ' — demi-groupe' : ''}`,
              }))}
              error={errors.subjectSessionTypeId?.message}
              {...register('subjectSessionTypeId')}
            />
            <Input label="Priorité" type="number" {...register('priority')} />
          </div>
          <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-brand-border bg-white p-3 transition-colors hover:bg-brand-bgSecondary/60 has-[:checked]:border-brand-teal has-[:checked]:bg-teal-50/70 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800 dark:has-[:checked]:bg-teal-500/10">
            <input
              type="checkbox" {...register('isActive')}
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
            />
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 text-sm font-medium text-brand-text dark:text-slate-200">
                <CheckCircle2 size={14} className="text-brand-textMuted dark:text-slate-400" />
                Affectation active
              </span>
              <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                Décochée, elle est conservée mais ignorée par la génération de l'emploi du temps.
              </span>
            </span>
          </label>

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>
              {editing ? 'Mettre à jour' : "Créer l'affectation"}
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idTeachingAssignment)}
        loading={deleteMutation.isPending}
        title="Supprimer l'affectation"
        message={`Supprimer l'affectation de ${deleteTarget?.teacherNomComplet} → ${deleteTarget?.classGroupCode} — ${deleteTarget?.subjectLib} ?`}
        variant="danger"
        confirmLabel="Supprimer"
      />
    </div>
  )
}
