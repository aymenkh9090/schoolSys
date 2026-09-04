import { useState, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Power, PowerOff, AlertTriangle, Link } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
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

  const actives = affectations.filter((a) => a.isActive).length

  const columns: Column<TeachingAssignmentFull>[] = [
    { key: 'teacherNomComplet', header: 'Enseignant', render: (a) => <span className="font-medium">{a.teacherNomComplet}</span> },
    { key: 'classGroupCode', header: 'Classe', render: (a) => <Badge variant="default">{a.classGroupCode}</Badge> },
    { key: 'subjectLib', header: 'Matière', render: (a) => (
      <div className="flex items-center gap-2">
        {a.subjectCouleur && <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: a.subjectCouleur }} />}
        <span className="text-sm">{a.subjectLib}</span>
      </div>
    )},
    { key: 'levelNom', header: 'Niveau', render: (a) => <span className="text-sm text-brand-textMuted dark:text-slate-400">{a.levelNom}</span> },
    { key: 'sessionDuration', header: 'Durée séance', render: (a) => `${a.sessionDuration}h` },
    { key: 'priority', header: 'Priorité', render: (a) => a.priority ?? 1 },
    { key: 'isActive', header: 'Statut', render: (a) => <Badge variant={a.isActive ? 'success' : 'danger'}>{a.isActive ? 'Active' : 'Inactive'}</Badge> },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (a) => (
        <div className="flex items-center gap-1 justify-end">
          <button onClick={() => toggleMutation.mutate({ id: a.idTeachingAssignment, isActive: !a.isActive })} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            {a.isActive ? <PowerOff size={15} /> : <Power size={15} />}
          </button>
          <button onClick={() => { setEditing(a); setOpen(true) }} className="p-1.5 rounded-md hover:bg-brand-bgSecondary dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200">
            <Pencil size={15} />
          </button>
          <button onClick={() => setDeleteTarget(a)} className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-500/10 text-brand-textMuted dark:text-slate-400 hover:text-danger">
            <Trash2 size={15} />
          </button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Affectations enseignants"
        subtitle="Association enseignant — classe — matière"
        actions={<Button onClick={openCreate}><Plus size={16} /> Nouvelle affectation</Button>}
      />

      {/* Filtre année */}
      <div className="flex items-center gap-2">
        <label className="text-sm font-medium text-brand-text dark:text-slate-200">Année scolaire :</label>
        <select
          value={selectedYearId ?? ''}
          onChange={(e) => setSelectedYearId(e.target.value ? Number(e.target.value) : null)}
          className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-1.5 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
        >
          <option value="">Toutes</option>
          {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
        </select>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatCard title="Total affectations" value={affectations.length} icon={Link} color="blue" />
        <StatCard title="Actives" value={actives} icon={Link} color="green" />
        <StatCard title="Manquantes" value={missing.length} icon={AlertTriangle} color="red" />
      </div>

      {missing.length > 0 && (
        <div className="bg-yellow-50 dark:bg-amber-500/10 border border-yellow-200 dark:border-amber-500/20 rounded-xl p-4">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle size={16} className="text-yellow-600 dark:text-amber-400" />
            <p className="text-sm font-semibold text-yellow-800 dark:text-amber-300">{missing.length} affectation(s) manquante(s)</p>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-32 overflow-y-auto">
            {missing.map((m, i) => (
              <div key={i} className="text-xs bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 border border-yellow-200 dark:border-amber-500/20 rounded px-2 py-1">
                <span className="font-medium">{m.classGroupCode}</span> — {m.subjectCode}
              </div>
            ))}
          </div>
        </div>
      )}

      <DataTable columns={columns} data={affectations} keyField="idTeachingAssignment" loading={isLoading} emptyMessage="Aucune affectation trouvée" />

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Modifier l\'affectation' : 'Nouvelle affectation'} size="md">
        <form onSubmit={handleSubmit((d) => saveMutation.mutate(d))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
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
          <div className="grid grid-cols-2 gap-4">
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
          <div className="grid grid-cols-2 gap-4">
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
          <label className="flex items-center gap-2 text-sm text-brand-text dark:text-slate-200">
            <input type="checkbox" {...register('isActive')} className="rounded" />
            Affectation active
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" loading={saveMutation.isPending}>{editing ? 'Mettre à jour' : 'Créer'}</Button>
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
