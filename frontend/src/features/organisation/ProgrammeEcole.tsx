import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, EyeOff, Eye, Info } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import {
  organisationApi,
  PATTERN_ROOM_TYPES,
  type SubjectLevel,
  type SubjectLevelRequest,
  type SchoolPattern,
  type SchoolPatternRequest,
  type SchoolPatternDetailRequest,
  type SessionType,
  type WeekParity,
  type PatternType,
} from '@/api/organisation.api'

const SESSION_TYPE_OPTIONS: SessionType[] = ['COURSE', 'TD', 'TP', 'LAB', 'SPORT', 'EXAM']
const WEEK_PARITY_OPTIONS: WeekParity[] = ['ALL', 'ODD', 'EVEN', 'BIWEEKLY']
const PATTERN_TYPE_OPTIONS: PatternType[] = ['WEEKLY_IDENTICAL', 'ALTERNATING', 'BIWEEKLY']

interface DetailRow {
  duration: string
  type: SessionType
  weekParity: WeekParity | ''
  isSplit: boolean
  splitGroupIndex: string
  requiredRoomType: string
  subjectSessionTypeId: string
}

function emptyRow(): DetailRow {
  return { duration: '', type: 'COURSE', weekParity: '', isSplit: false, splitGroupIndex: '', requiredRoomType: '', subjectSessionTypeId: '' }
}

/** Transforme "2+1+1+1" en "2h + 1h + 1h + 1h", plus lisible pour un admin. */
function formatRepartition(repartition: string | null, sessionCount: number): string {
  if (!repartition) return `${sessionCount} séance${sessionCount > 1 ? 's' : ''}`
  return repartition.split('+').map((n) => `${n}h`).join(' + ')
}

export default function ProgrammeEcole() {
  const qc = useQueryClient()
  const [levelId, setLevelId] = useState<number | null>(null)
  const [editing, setEditing] = useState<{ subjectLevel: SubjectLevel; pattern: SchoolPattern | null } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<SchoolPattern | null>(null)
  const [name, setName] = useState('')
  const [patternType, setPatternType] = useState<PatternType>('WEEKLY_IDENTICAL')
  const [schoolYearId, setSchoolYearId] = useState('')
  const [rows, setRows] = useState<DetailRow[]>([emptyRow()])

  const [addSubjectOpen, setAddSubjectOpen] = useState(false)
  const [newSubjectId, setNewSubjectId] = useState('')
  const [newHeures, setNewHeures] = useState('')
  const [newObligatoire, setNewObligatoire] = useState(true)
  const [newCoefficient, setNewCoefficient] = useState('1')

  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })
  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: allSubjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: organisationApi.subjects.list })

  const { data: subjectLevels = [], isLoading } = useQuery({
    queryKey: ['subject-levels-by-level', levelId],
    queryFn: () => organisationApi.subjects.subjectLevels.byLevel(levelId!),
    enabled: levelId !== null,
  })

  const saveMutation = useMutation({
    mutationFn: (dto: SchoolPatternRequest) =>
      editing?.pattern
        ? organisationApi.patterns.update(editing.pattern.idPattern, dto)
        : organisationApi.patterns.create(dto),
    onSuccess: () => {
      toast.success(editing?.pattern ? 'Répartition mise à jour' : 'Répartition créée')
      qc.invalidateQueries({ queryKey: ['subject-levels-by-level', levelId] })
      setEditing(null)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => organisationApi.patterns.delete(id),
    onSuccess: () => {
      toast.success('Répartition supprimée')
      qc.invalidateQueries({ queryKey: ['subject-levels-by-level', levelId] })
      setDeleteTarget(null)
    },
    onError: () => toast.error('Erreur'),
  })

  const toggleActiveMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => organisationApi.patterns.toggleStatus(id, active),
    onSuccess: (_, vars) => {
      toast.success(vars.active ? 'Matière réactivée pour la génération' : "Matière désactivée — ignorée par la génération de l'emploi du temps")
      qc.invalidateQueries({ queryKey: ['subject-levels-by-level', levelId] })
    },
    onError: () => toast.error('Erreur'),
  })

  const addSubjectMutation = useMutation({
    mutationFn: (dto: SubjectLevelRequest) => organisationApi.subjects.subjectLevels.create(dto),
    onSuccess: () => {
      toast.success('Matière ajoutée au niveau')
      qc.invalidateQueries({ queryKey: ['subject-levels-by-level', levelId] })
      closeAddSubject()
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  function openCreate(sl: SubjectLevel) {
    setEditing({ subjectLevel: sl, pattern: null })
    setName(`${sl.subjectLib} — ${sl.levelNom}`)
    setPatternType('WEEKLY_IDENTICAL')
    setSchoolYearId('')
    setRows([emptyRow()])
  }

  function openEdit(sl: SubjectLevel, p: SchoolPattern) {
    setEditing({ subjectLevel: sl, pattern: p })
    setName(p.name)
    setPatternType(p.patternType ?? 'WEEKLY_IDENTICAL')
    setSchoolYearId(p.schoolYearId ? String(p.schoolYearId) : '')
    setRows(
      [...p.details]
        .sort((a, b) => a.sessionOrder - b.sessionOrder)
        .map((d) => ({
          duration: String(d.duration),
          type: d.type,
          weekParity: d.weekParity ?? '',
          isSplit: !!d.isSplit,
          splitGroupIndex: d.splitGroupIndex ? String(d.splitGroupIndex) : '',
          requiredRoomType: d.requiredRoomType ?? '',
          subjectSessionTypeId: d.subjectSessionTypeId ? String(d.subjectSessionTypeId) : '',
        }))
    )
  }

  function updateRow(i: number, patch: Partial<DetailRow>) {
    setRows((r) => r.map((row, idx) => (idx === i ? { ...row, ...patch } : row)))
  }

  function addRow() {
    setRows((r) => [...r, emptyRow()])
  }

  function removeRow(i: number) {
    setRows((r) => r.filter((_, idx) => idx !== i))
  }

  function closeAddSubject() {
    setAddSubjectOpen(false)
    setNewSubjectId('')
    setNewHeures('')
    setNewObligatoire(true)
    setNewCoefficient('1')
  }

  const totalHours = rows.reduce((sum, r) => sum + (Number(r.duration) || 0), 0)
  const repartition = rows.map((r) => r.duration || '0').join('+')

  // Matières du catalogue de l'école pas encore rattachées à ce niveau — permet
  // d'ajouter une matière absente du programme national (ex: option spécifique à l'école).
  const availableSubjects = allSubjects.filter((s) => !subjectLevels.some((sl) => sl.subjectId === s.idMatiere))

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!editing) return
    if (!name.trim()) {
      toast.error('Le nom est obligatoire')
      return
    }
    if (rows.length === 0 || rows.some((r) => !r.duration || Number(r.duration) <= 0)) {
      toast.error('Chaque séance doit avoir une durée valide')
      return
    }
    const dto: SchoolPatternRequest = {
      name,
      totalHours,
      sessionCount: rows.length,
      repartition,
      patternType,
      subjectLevelId: editing.subjectLevel.idNiveauMatiere,
      schoolYearId: schoolYearId ? Number(schoolYearId) : undefined,
      details: rows.map((r, i) => ({
        sessionOrder: i + 1,
        duration: Number(r.duration),
        weekParity: r.weekParity || undefined,
        isSplit: r.isSplit || undefined,
        splitGroupIndex: r.isSplit && r.splitGroupIndex ? Number(r.splitGroupIndex) : undefined,
        type: r.type,
        requiredRoomType: (r.requiredRoomType || undefined) as SchoolPatternDetailRequest['requiredRoomType'],
        subjectSessionTypeId: r.subjectSessionTypeId ? Number(r.subjectSessionTypeId) : undefined,
      })),
    }
    saveMutation.mutate(dto)
  }

  function handleAddSubject(e: React.FormEvent) {
    e.preventDefault()
    if (levelId === null) return
    if (!newSubjectId) {
      toast.error('Choisissez une matière')
      return
    }
    if (!newHeures || Number(newHeures) <= 0) {
      toast.error("Le nombre d'heures par semaine est obligatoire")
      return
    }
    addSubjectMutation.mutate({
      subjectId: Number(newSubjectId),
      levelId,
      heuresSemaine: Number(newHeures),
      estObligatoire: newObligatoire,
      coefficient: newCoefficient ? Number(newCoefficient) : undefined,
    })
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Programme de l'école"
        subtitle="Adaptez la répartition horaire par matière et niveau (copiée depuis le programme national)"
      />

      <div className="flex items-end justify-between gap-4 flex-wrap">
        <div>
          <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Niveau</label>
          <select
            value={levelId ?? ''}
            onChange={(e) => setLevelId(e.target.value ? Number(e.target.value) : null)}
            className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-64"
          >
            <option value="">Sélectionner un niveau</option>
            {levels.map((l) => <option key={l.idNiveau} value={l.idNiveau}>{l.nom}</option>)}
          </select>
        </div>

        {levelId !== null && (
          <Button variant="outline" onClick={() => setAddSubjectOpen(true)}>
            <Plus size={16} /> Ajouter une matière à ce niveau
          </Button>
        )}
      </div>

      {levelId !== null && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 overflow-hidden">
          <div className="divide-y divide-brand-border dark:divide-slate-700">
            {isLoading && <p className="p-4 text-sm text-brand-textMuted dark:text-slate-400">Chargement...</p>}
            {!isLoading && subjectLevels.length === 0 && (
              <p className="p-4 text-sm text-brand-textMuted dark:text-slate-400">
                Aucune matière associée à ce niveau. Utilisez « Ajouter une matière à ce niveau » ci-dessus, ou appliquez d'abord le programme national.
              </p>
            )}
            {subjectLevels.map((sl) => {
              const pattern = sl.patterns?.[0]
              const isDisabled = pattern?.active === false
              return (
                <div key={sl.idNiveauMatiere} className={`px-4 py-3 flex items-center justify-between gap-4 ${isDisabled ? 'opacity-60' : ''}`}>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      {sl.subjectCouleur && <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: sl.subjectCouleur }} />}
                      <span className="text-sm font-medium text-brand-text dark:text-slate-200">{sl.subjectLib}</span>
                      <Badge variant="default">{sl.heuresSemaine}h/sem officiel</Badge>
                      {sl.estObligatoire && <Badge variant="warning">Obligatoire</Badge>}
                      {isDisabled && <Badge variant="danger">Désactivée pour cet établissement</Badge>}
                    </div>
                    {pattern ? (
                      <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-0.5">
                        Répartition hebdomadaire : <strong className="font-medium text-brand-text dark:text-slate-300">{pattern.totalHours}h</strong>, en {pattern.sessionCount} séance{pattern.sessionCount > 1 ? 's' : ''} de {formatRepartition(pattern.repartition, pattern.sessionCount)}
                        {pattern.totalHours !== sl.heuresSemaine && (
                          <span className="text-amber-600 dark:text-amber-400 font-medium"> · différent des {sl.heuresSemaine}h officielles</span>
                        )}
                        {isDisabled && (
                          <span className="block mt-0.5">Cette matière n'est pas enseignée dans cet établissement : elle ne sera pas prise en compte lors de la génération de l'emploi du temps.</span>
                        )}
                      </p>
                    ) : (
                      <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-0.5">
                        Aucune répartition définie — indiquez comment ces {sl.heuresSemaine}h/semaine se répartissent en séances de cours (ex: 2h + 1h + 1h + 1h).
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    {pattern ? (
                      <>
                        <Button size="sm" variant="outline" onClick={() => openEdit(sl, pattern)}>
                          <Pencil size={12} /> Modifier
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          loading={toggleActiveMutation.isPending}
                          onClick={() => toggleActiveMutation.mutate({ id: pattern.idPattern, active: isDisabled })}
                          title={isDisabled ? 'Réactiver cette matière pour la génération' : "Désactiver — cette matière n'est pas enseignée ici"}
                        >
                          {isDisabled ? <><Eye size={12} /> Activer</> : <><EyeOff size={12} /> Désactiver</>}
                        </Button>
                        <button
                          onClick={() => setDeleteTarget(pattern)}
                          className="p-1.5 rounded-md hover:bg-red-50 text-brand-textMuted hover:text-danger"
                          title="Supprimer définitivement"
                        >
                          <Trash2 size={15} />
                        </button>
                      </>
                    ) : (
                      <Button size="sm" variant="outline" onClick={() => openCreate(sl)}>
                        <Plus size={12} /> Définir la répartition
                      </Button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.idPattern)}
        loading={deleteMutation.isPending}
        title="Supprimer la répartition"
        message={`Supprimer la répartition "${deleteTarget?.name}" ? Cette action est irréversible.`}
        variant="danger"
        confirmLabel="Supprimer"
      />

      {/* Modal : ajouter une matière hors programme national */}
      <Modal open={addSubjectOpen} onClose={closeAddSubject} title="Ajouter une matière à ce niveau">
        <form onSubmit={handleAddSubject} className="space-y-4">
          <p className="text-xs text-brand-textMuted dark:text-slate-400 flex items-start gap-1.5">
            <Info size={13} className="shrink-0 mt-0.5" />
            Pour une matière propre à votre établissement, absente du programme national officiel (ex: une option spécifique).
          </p>
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Matière *</label>
            <select
              value={newSubjectId}
              onChange={(e) => setNewSubjectId(e.target.value)}
              className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
            >
              <option value="">Sélectionner une matière</option>
              {availableSubjects.map((s) => <option key={s.idMatiere} value={s.idMatiere}>{s.libMatiere}</option>)}
            </select>
            {availableSubjects.length === 0 && (
              <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-1">
                Toutes les matières du catalogue sont déjà rattachées à ce niveau. Créez d'abord une nouvelle matière depuis la gestion des matières.
              </p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Heures / semaine *"
              type="number" step="0.5" min="0.5"
              value={newHeures}
              onChange={(e) => setNewHeures((e.target as HTMLInputElement).value)}
            />
            <Input
              label="Coefficient"
              type="number" step="0.5" min="0"
              value={newCoefficient}
              onChange={(e) => setNewCoefficient((e.target as HTMLInputElement).value)}
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-brand-text dark:text-slate-200">
            <input type="checkbox" checked={newObligatoire} onChange={(e) => setNewObligatoire(e.target.checked)} />
            Matière obligatoire
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={closeAddSubject}>Annuler</Button>
            <Button type="submit" loading={addSubjectMutation.isPending} disabled={availableSubjects.length === 0}>Ajouter</Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={editing?.pattern ? 'Modifier la répartition' : `Nouvelle répartition — ${editing?.subjectLevel.subjectLib ?? ''}`}
        size="lg"
      >
        {editing && (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <Input label="Nom *" value={name} onChange={(e) => setName((e.target as HTMLInputElement).value)} />
              <div>
                <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Année scolaire (optionnel)</label>
                <select
                  value={schoolYearId}
                  onChange={(e) => setSchoolYearId(e.target.value)}
                  className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
                >
                  <option value="">Toutes les années (par défaut)</option>
                  {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
                </select>
              </div>
            </div>

            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Stratégie hebdomadaire</label>
              <select
                value={patternType}
                onChange={(e) => setPatternType(e.target.value as PatternType)}
                className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
              >
                {PATTERN_TYPE_OPTIONS.map((o) => <option key={o} value={o}>{o}</option>)}
              </select>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <label className="text-sm font-medium text-brand-text dark:text-slate-200">Séances ({rows.length}) — total {totalHours}h</label>
                <Button type="button" size="sm" variant="outline" onClick={addRow}><Plus size={12} /> Ajouter une séance</Button>
              </div>
              <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
                {rows.map((row, i) => (
                  <div key={i} className="border border-brand-border dark:border-slate-700 rounded-lg p-3 grid grid-cols-6 gap-2 items-end">
                    <div className="col-span-1">
                      <label className="text-xs text-brand-textMuted dark:text-slate-400 block mb-1">Durée (h) *</label>
                      <input
                        type="number" step="0.5" min="0.5" max="4" value={row.duration}
                        onChange={(e) => updateRow(i, { duration: e.target.value })}
                        className="w-full border border-brand-border dark:border-slate-700 rounded px-2 py-1.5 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
                      />
                    </div>
                    <div className="col-span-1">
                      <label className="text-xs text-brand-textMuted dark:text-slate-400 block mb-1">Type *</label>
                      <select
                        value={row.type} onChange={(e) => updateRow(i, { type: e.target.value as SessionType })}
                        className="w-full border border-brand-border dark:border-slate-700 rounded px-2 py-1.5 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
                      >
                        {SESSION_TYPE_OPTIONS.map((o) => <option key={o} value={o}>{o}</option>)}
                      </select>
                    </div>
                    <div className="col-span-1">
                      <label className="text-xs text-brand-textMuted dark:text-slate-400 block mb-1">Parité</label>
                      <select
                        value={row.weekParity} onChange={(e) => updateRow(i, { weekParity: e.target.value as WeekParity | '' })}
                        className="w-full border border-brand-border dark:border-slate-700 rounded px-2 py-1.5 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
                      >
                        <option value="">ALL</option>
                        {WEEK_PARITY_OPTIONS.map((o) => <option key={o} value={o}>{o}</option>)}
                      </select>
                    </div>
                    <div className="col-span-1">
                      <label className="text-xs text-brand-textMuted dark:text-slate-400 block mb-1">Salle requise</label>
                      <select
                        value={row.requiredRoomType} onChange={(e) => updateRow(i, { requiredRoomType: e.target.value })}
                        className="w-full border border-brand-border dark:border-slate-700 rounded px-2 py-1.5 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
                      >
                        <option value="">—</option>
                        {PATTERN_ROOM_TYPES.map((o) => <option key={o} value={o}>{o}</option>)}
                      </select>
                    </div>
                    <div className="col-span-1 flex items-center gap-1.5 pb-1.5">
                      <input
                        type="checkbox" checked={row.isSplit}
                        onChange={(e) => updateRow(i, { isSplit: e.target.checked, splitGroupIndex: e.target.checked ? (row.splitGroupIndex || '1') : '' })}
                      />
                      <span className="text-xs text-brand-text dark:text-slate-200">Demi-groupe{row.isSplit && ` #${row.splitGroupIndex}`}</span>
                    </div>
                    <div className="col-span-1 flex justify-end">
                      <button type="button" onClick={() => removeRow(i)} className="p-1.5 rounded-md hover:bg-red-50 text-brand-textMuted hover:text-danger">
                        <Trash2 size={15} />
                      </button>
                    </div>
                    {editing.subjectLevel.sessionTypes && editing.subjectLevel.sessionTypes.length > 0 && (
                      <div className="col-span-6">
                        <label className="text-xs text-brand-textMuted dark:text-slate-400 block mb-1">Type de séance lié aux affectations (optionnel)</label>
                        <select
                          value={row.subjectSessionTypeId} onChange={(e) => updateRow(i, { subjectSessionTypeId: e.target.value })}
                          className="w-full border border-brand-border dark:border-slate-700 rounded px-2 py-1.5 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
                        >
                          <option value="">—</option>
                          {editing.subjectLevel.sessionTypes.map((st) => (
                            <option key={st.idSubjectSessionType} value={st.idSubjectSessionType}>{st.type} — {st.duration}h</option>
                          ))}
                        </select>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => setEditing(null)}>Annuler</Button>
              <Button type="submit" loading={saveMutation.isPending}>{editing.pattern ? 'Mettre à jour' : 'Créer'}</Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
