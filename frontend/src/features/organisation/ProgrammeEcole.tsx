import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  Plus, Pencil, Trash2, EyeOff, Eye, Info, Library, Clock, Layers,
  AlertTriangle, BookOpen, MousePointerClick,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
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

// Les constantes du backend s'affichaient telles quelles — `WEEKLY_IDENTICAL`,
// `LABINFORMATIQUE`, `ODD`. Un directeur d'établissement n'a pas à traduire le
// vocabulaire du modèle de données pour remplir un formulaire.
const SESSION_TYPE_LABELS: Record<SessionType, string> = {
  COURSE: 'Cours', TD: 'Travaux dirigés', TP: 'Travaux pratiques',
  LAB: 'Laboratoire', SPORT: 'Sport', EXAM: 'Devoir surveillé',
}
const WEEK_PARITY_LABELS: Record<WeekParity, string> = {
  ALL: 'Toutes les semaines', ODD: 'Semaines impaires',
  EVEN: 'Semaines paires', BIWEEKLY: 'Une semaine sur deux',
}
const PATTERN_TYPE_LABELS: Record<PatternType, { titre: string; aide: string }> = {
  WEEKLY_IDENTICAL: { titre: 'Identique chaque semaine', aide: 'Les mêmes séances reviennent toutes les semaines.' },
  ALTERNATING: { titre: 'Alternée', aide: 'Les séances changent selon la parité de la semaine.' },
  BIWEEKLY: { titre: 'Une semaine sur deux', aide: 'La matière n’est enseignée qu’une semaine sur deux.' },
}
const ROOM_TYPE_LABELS: Record<string, string> = {
  NORMALE: 'Salle ordinaire', LABSCIENCE: 'Labo de sciences', LABPHYSIQUE: 'Labo de physique',
  LABINFORMATIQUE: 'Salle informatique', LABTECHNIQUE: 'Atelier technique', SALLESPORT: 'Salle de sport',
}

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

/**
 * Les séances d'une répartition, dessinées une par une plutôt qu'écrites
 * « 2h + 1h + 1h ». La semaine d'une matière se lit alors d'un coup d'œil, et
 * chaque pastille porte ce que la phrase ne disait pas : le type de séance, la
 * semaine concernée quand elle alterne, le dédoublement en demi-groupe.
 */
function SeanceChips({ pattern }: { pattern: SchoolPattern }) {
  const details = [...pattern.details].sort((a, b) => a.sessionOrder - b.sessionOrder)
  const seances = details.length > 0
    ? details
    : (pattern.repartition ?? '').split('+').filter(Boolean).map((d, i) => ({
        idPatternDetail: -i, sessionOrder: i + 1, duration: Number(d), type: 'COURSE' as SessionType,
        weekParity: null, isSplit: null, splitGroupIndex: null, requiredRoomType: null, subjectSessionTypeId: null,
      }))

  if (seances.length === 0) return null

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {seances.map((d) => (
        <span
          key={d.idPatternDetail}
          title={`Séance ${d.sessionOrder} — ${d.duration}h, ${d.type}${d.weekParity && d.weekParity !== 'ALL' ? `, semaines ${d.weekParity}` : ''}${d.isSplit ? ` — demi-groupe${d.splitGroupIndex ? ` #${d.splitGroupIndex}` : ''}` : ''}`}
          className="inline-flex items-center gap-1 rounded-md border border-brand-border bg-brand-bgSecondary px-2 py-1 text-xs font-medium text-brand-text dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
        >
          <span className="tabular-nums">{d.duration}h</span>
          {d.type !== 'COURSE' && (
            <span className="text-[10px] font-semibold uppercase tracking-wide text-brand-teal dark:text-teal-400">{d.type}</span>
          )}
          {d.weekParity && d.weekParity !== 'ALL' && (
            <span className="text-[10px] font-semibold uppercase tracking-wide text-brand-textMuted dark:text-slate-400">{d.weekParity}</span>
          )}
          {d.isSplit && <Layers size={11} className="text-brand-textMuted dark:text-slate-400" />}
        </span>
      ))}
    </div>
  )
}

/** Compteur du bandeau de synthèse. Muet quand il vaut zéro et que c'est une bonne nouvelle. */
function Synthese({ icon: Icon, valeur, libelle, ton = 'neutre' }: {
  icon: React.ElementType; valeur: string | number; libelle: string; ton?: 'neutre' | 'alerte'
}) {
  const alerte = ton === 'alerte' && Number(valeur) > 0
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${
        alerte ? 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-400'
               : 'bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400'}`}>
        <Icon size={17} />
      </span>
      <div className="min-w-0">
        <p className={`text-lg font-bold leading-tight tabular-nums ${
          alerte ? 'text-amber-600 dark:text-amber-400' : 'text-brand-text dark:text-slate-100'}`}>{valeur}</p>
        <p className="truncate text-xs text-brand-textMuted dark:text-slate-400">{libelle}</p>
      </div>
    </div>
  )
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

  const selectedLevel = levels.find((l) => l.idNiveau === levelId) ?? null

  // Chiffres du bandeau : ce qu'un directeur vérifie avant de lancer une
  // génération d'emploi du temps — le volume réellement planifié, et surtout ce
  // qui manque encore.
  const actifs = subjectLevels.filter((sl) => sl.patterns?.[0]?.active !== false)
  const sansRepartition = subjectLevels.filter((sl) => !sl.patterns?.[0]).length
  const desactivees = subjectLevels.filter((sl) => sl.patterns?.[0]?.active === false).length
  const heuresPlanifiees = actifs.reduce((sum, sl) => sum + (sl.patterns?.[0]?.totalHours ?? 0), 0)

  return (
    <div className="space-y-6">
      <PageHero
        title="Programme de l'école"
        subtitle="Adaptez la répartition horaire par matière et niveau (copiée depuis le programme national)"
        icon={Library}
      />

      {/* Le niveau se choisit en pastilles, pas dans une liste déroulante : ils
          sont une dizaine, ils tiennent sur une ligne, et le choix reste visible
          pendant qu'on lit les matières — un menu refermé, non. */}
      <div className="rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0 flex-1">
            <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">Niveau</p>
            {levels.length === 0 ? (
              <p className="text-sm text-brand-textMuted dark:text-slate-400">
                Aucun niveau créé. Commencez par en définir depuis la gestion des niveaux.
              </p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {levels.map((l) => {
                  const actif = l.idNiveau === levelId
                  return (
                    <button
                      key={l.idNiveau}
                      onClick={() => setLevelId(actif ? null : l.idNiveau)}
                      aria-pressed={actif}
                      className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                        actif
                          ? 'border-brand-teal bg-brand-teal text-white shadow-sm'
                          : 'border-brand-border bg-white text-brand-text hover:border-brand-teal/40 hover:bg-teal-50/60 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'
                      }`}
                    >
                      {l.nom}
                      {l.nombreMatieres > 0 && (
                        <span className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${
                          actif ? 'bg-white/20 text-white' : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'
                        }`}>
                          {l.nombreMatieres}
                        </span>
                      )}
                    </button>
                  )
                })}
              </div>
            )}
          </div>

          {levelId !== null && (
            <Button variant="outline" onClick={() => setAddSubjectOpen(true)}>
              <Plus size={16} /> Ajouter une matière
            </Button>
          )}
        </div>
      </div>

      {/* Rien de choisi : la page invite au lieu de rester vide. */}
      {levelId === null && levels.length > 0 && (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <MousePointerClick size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">Choisissez un niveau</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            Ses matières et la répartition hebdomadaire de chacune s'afficheront ici.
          </p>
        </div>
      )}

      {levelId !== null && (
        <>
          {/* Synthèse : le volume planifié et, surtout, ce qui manque encore
              avant qu'une génération d'emploi du temps soit possible. */}
          {!isLoading && subjectLevels.length > 0 && (
            <div className="grid grid-cols-2 divide-brand-border rounded-xl border border-brand-border bg-white sm:grid-cols-4 sm:divide-x dark:divide-slate-700 dark:border-slate-700 dark:bg-slate-900">
              <Synthese icon={BookOpen} valeur={subjectLevels.length} libelle="matières au programme" />
              <Synthese icon={Clock} valeur={`${heuresPlanifiees}h`} libelle="planifiées par semaine" />
              <Synthese icon={AlertTriangle} valeur={sansRepartition} libelle="sans répartition" ton="alerte" />
              <Synthese icon={EyeOff} valeur={desactivees} libelle="désactivées ici" ton="alerte" />
            </div>
          )}

          {isLoading && (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {[0, 1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="h-40 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
              ))}
            </div>
          )}

          {!isLoading && subjectLevels.length === 0 && (
            <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
              <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
                <BookOpen size={22} />
              </span>
              <p className="text-sm font-medium text-brand-text dark:text-slate-200">
                Aucune matière pour {selectedLevel?.nom ?? 'ce niveau'}
              </p>
              <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
                Appliquez d'abord le programme national, ou ajoutez une matière propre à votre établissement.
              </p>
              <Button className="mt-4" variant="outline" onClick={() => setAddSubjectOpen(true)}>
                <Plus size={16} /> Ajouter une matière
              </Button>
            </div>
          )}

          {/* Une carte par matière plutôt qu'une ligne de tableau : la
              répartition hebdomadaire est l'information centrale de cet écran,
              et elle ne tient pas dans une cellule. */}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {subjectLevels.map((sl) => {
              const pattern = sl.patterns?.[0]
              const isDisabled = pattern?.active === false
              const ecart = pattern && pattern.totalHours !== sl.heuresSemaine
              return (
                <div
                  key={sl.idNiveauMatiere}
                  className={`group relative flex flex-col overflow-hidden rounded-xl border bg-white transition-shadow hover:shadow-md dark:bg-slate-900 ${
                    isDisabled
                      ? 'border-brand-border opacity-70 dark:border-slate-700'
                      : 'border-brand-border dark:border-slate-700'
                  }`}
                >
                  {/* Le liseré porte la couleur de la matière — le même repère
                      que sur la grille d'emploi du temps. */}
                  <span
                    className="absolute inset-x-0 top-0 h-1"
                    style={{ background: sl.subjectCouleur || '#0f766e' }}
                  />

                  <div className="flex flex-1 flex-col gap-3 p-4 pt-5">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">{sl.subjectLib}</h3>
                        <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
                          {sl.heuresSemaine}h/semaine au programme officiel
                          {sl.coefficient ? ` · coefficient ${sl.coefficient}` : ''}
                        </p>
                      </div>
                      {sl.estObligatoire && <Badge variant="warning">Obligatoire</Badge>}
                    </div>

                    {pattern ? (
                      <div className="space-y-2">
                        <div className="flex items-baseline gap-1.5">
                          <span className="text-2xl font-bold tabular-nums text-brand-text dark:text-slate-100">{pattern.totalHours}h</span>
                          <span className="text-xs text-brand-textMuted dark:text-slate-400">
                            en {pattern.sessionCount} séance{pattern.sessionCount > 1 ? 's' : ''}
                          </span>
                        </div>
                        <SeanceChips pattern={pattern} />
                        {ecart && (
                          <p className="flex items-start gap-1.5 text-xs font-medium text-amber-600 dark:text-amber-400">
                            <AlertTriangle size={13} className="mt-px shrink-0" />
                            Différent des {sl.heuresSemaine}h officielles
                          </p>
                        )}
                      </div>
                    ) : (
                      <div className="rounded-lg border border-dashed border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40">
                        <p className="text-xs text-brand-textMuted dark:text-slate-400">
                          Aucune répartition — indiquez comment ces {sl.heuresSemaine}h se répartissent
                          en séances (ex : 2h + 1h + 1h).
                        </p>
                      </div>
                    )}

                    {isDisabled && (
                      <p className="flex items-start gap-1.5 rounded-lg bg-red-50 p-2 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">
                        <EyeOff size={13} className="mt-px shrink-0" />
                        Non enseignée ici : ignorée par la génération de l'emploi du temps.
                      </p>
                    )}
                  </div>

                  <div className="flex items-center justify-end gap-2 border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                    {pattern ? (
                      <>
                        <Button
                          size="sm"
                          variant="ghost"
                          loading={toggleActiveMutation.isPending}
                          onClick={() => toggleActiveMutation.mutate({ id: pattern.idPattern, active: isDisabled })}
                          title={isDisabled ? 'Réactiver cette matière pour la génération' : "Désactiver — cette matière n'est pas enseignée ici"}
                        >
                          {isDisabled ? <><Eye size={13} /> Activer</> : <><EyeOff size={13} /> Désactiver</>}
                        </Button>
                        <Button size="sm" variant="outline" onClick={() => openEdit(sl, pattern)}>
                          <Pencil size={13} /> Modifier
                        </Button>
                        <button
                          onClick={() => setDeleteTarget(pattern)}
                          className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger dark:hover:bg-red-500/10"
                          title="Supprimer définitivement"
                        >
                          <Trash2 size={15} />
                        </button>
                      </>
                    ) : (
                      <Button size="sm" onClick={() => openCreate(sl)}>
                        <Plus size={13} /> Définir la répartition
                      </Button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </>
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
        title={editing?.pattern ? 'Modifier la répartition' : 'Nouvelle répartition'}
        size="xl"
      >
        {editing && (() => {
          const officielles = editing.subjectLevel.heuresSemaine
          const ecart = Number((totalHours - officielles).toFixed(2))
          return (
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* De quelle matière parle-t-on, et quel volume vise-t-on. Le titre
                seul ne le disait plus une fois la modale ouverte. */}
            <div className="flex items-center gap-3 rounded-xl border border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40">
              <span
                className="h-9 w-1.5 shrink-0 rounded-full"
                style={{ background: editing.subjectLevel.subjectCouleur || '#0f766e' }}
              />
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-brand-text dark:text-slate-100">
                  {editing.subjectLevel.subjectLib}
                </p>
                <p className="text-xs text-brand-textMuted dark:text-slate-400">
                  {editing.subjectLevel.levelNom} · {officielles}h/semaine au programme officiel
                </p>
              </div>
              {/* Le total vit pendant la saisie : c'est la seule chose que
                  l'utilisateur vérifie vraiment en remplissant ce formulaire. */}
              <div className={`shrink-0 rounded-lg px-3 py-2 text-right ${
                ecart === 0
                  ? 'bg-emerald-50 dark:bg-emerald-500/10'
                  : 'bg-amber-50 dark:bg-amber-500/10'
              }`}>
                <p className={`text-lg font-bold leading-none tabular-nums ${
                  ecart === 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
                }`}>
                  {totalHours}h
                </p>
                <p className={`mt-1 text-[11px] font-medium ${
                  ecart === 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
                }`}>
                  {ecart === 0 ? 'conforme' : `${ecart > 0 ? '+' : ''}${ecart}h`}
                </p>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="Nom *" value={name} onChange={(e) => setName((e.target as HTMLInputElement).value)} />
              <div>
                <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Année scolaire</label>
                <select
                  value={schoolYearId}
                  onChange={(e) => setSchoolYearId(e.target.value)}
                  className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                >
                  <option value="">Toutes les années (par défaut)</option>
                  {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
                </select>
              </div>
            </div>

            {/* Trois stratégies seulement : des cartes cliquables portent leur
                explication, là où un menu déroulant obligeait à deviner ce que
                « ALTERNATING » recouvrait. */}
            <div>
              <label className="mb-2 block text-sm font-medium text-brand-text dark:text-slate-200">Stratégie hebdomadaire</label>
              <div className="grid gap-2 sm:grid-cols-3">
                {PATTERN_TYPE_OPTIONS.map((o) => {
                  const actif = patternType === o
                  return (
                    <button
                      key={o} type="button" onClick={() => setPatternType(o)} aria-pressed={actif}
                      className={`rounded-lg border p-3 text-left transition-colors ${
                        actif
                          ? 'border-brand-teal bg-teal-50/70 ring-1 ring-brand-teal dark:bg-teal-500/10'
                          : 'border-brand-border bg-white hover:bg-brand-bgSecondary/60 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800'
                      }`}
                    >
                      <span className={`block text-sm font-medium ${
                        actif ? 'text-brand-teal dark:text-teal-300' : 'text-brand-text dark:text-slate-200'
                      }`}>
                        {PATTERN_TYPE_LABELS[o].titre}
                      </span>
                      <span className="mt-0.5 block text-xs text-brand-textMuted dark:text-slate-400">
                        {PATTERN_TYPE_LABELS[o].aide}
                      </span>
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <label className="text-sm font-medium text-brand-text dark:text-slate-200">
                  Séances de la semaine
                  <span className="ml-2 text-xs font-normal text-brand-textMuted dark:text-slate-400">
                    {rows.length} séance{rows.length > 1 ? 's' : ''}
                  </span>
                </label>
                <Button type="button" size="sm" variant="outline" onClick={addRow}>
                  <Plus size={13} /> Ajouter une séance
                </Button>
              </div>

              {/* Une carte numérotée par séance, au lieu d'une grille de six
                  colonnes où chaque champ recevait quarante pixels. */}
              <div className="max-h-[22rem] space-y-3 overflow-y-auto pr-1">
                {rows.map((row, i) => (
                  <div key={i} className="rounded-xl border border-brand-border bg-white p-3 dark:border-slate-700 dark:bg-slate-900">
                    <div className="mb-3 flex items-center justify-between">
                      <span className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-brand-teal dark:text-teal-400">
                        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-teal-50 text-[11px] tabular-nums dark:bg-teal-500/10">
                          {i + 1}
                        </span>
                        Séance {i + 1}
                      </span>
                      <button
                        type="button" onClick={() => removeRow(i)} disabled={rows.length === 1}
                        title={rows.length === 1 ? 'Une répartition compte au moins une séance' : 'Retirer cette séance'}
                        className="rounded-md p-1.5 text-brand-textMuted hover:bg-red-50 hover:text-danger disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-brand-textMuted dark:hover:bg-red-500/10"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                      <div>
                        <label className="mb-1 block text-xs font-medium text-brand-textMuted dark:text-slate-400">Durée *</label>
                        <div className="relative">
                          <input
                            type="number" step="0.5" min="0.5" max="4" value={row.duration}
                            onChange={(e) => updateRow(i, { duration: e.target.value })}
                            className="w-full rounded-lg border border-brand-border bg-white py-2 pl-3 pr-7 text-sm tabular-nums text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                          />
                          <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-xs text-brand-textMuted dark:text-slate-500">h</span>
                        </div>
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-medium text-brand-textMuted dark:text-slate-400">Type *</label>
                        <select
                          value={row.type} onChange={(e) => updateRow(i, { type: e.target.value as SessionType })}
                          className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                        >
                          {SESSION_TYPE_OPTIONS.map((o) => <option key={o} value={o}>{SESSION_TYPE_LABELS[o]}</option>)}
                        </select>
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-medium text-brand-textMuted dark:text-slate-400">Semaines</label>
                        <select
                          value={row.weekParity} onChange={(e) => updateRow(i, { weekParity: e.target.value as WeekParity | '' })}
                          className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                        >
                          <option value="">{WEEK_PARITY_LABELS.ALL}</option>
                          {WEEK_PARITY_OPTIONS.filter((o) => o !== 'ALL').map((o) => (
                            <option key={o} value={o}>{WEEK_PARITY_LABELS[o]}</option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-medium text-brand-textMuted dark:text-slate-400">Salle requise</label>
                        <select
                          value={row.requiredRoomType} onChange={(e) => updateRow(i, { requiredRoomType: e.target.value })}
                          className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                        >
                          <option value="">Aucune contrainte</option>
                          {PATTERN_ROOM_TYPES.map((o) => <option key={o} value={o}>{ROOM_TYPE_LABELS[o] ?? o}</option>)}
                        </select>
                      </div>
                    </div>

                    <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-brand-border pt-3 dark:border-slate-700">
                      <label className="inline-flex cursor-pointer items-center gap-2 text-sm text-brand-text dark:text-slate-200">
                        <input
                          type="checkbox" checked={row.isSplit}
                          onChange={(e) => updateRow(i, { isSplit: e.target.checked, splitGroupIndex: e.target.checked ? (row.splitGroupIndex || '1') : '' })}
                          className="h-4 w-4 rounded border-brand-border text-brand-teal focus:ring-brand-teal dark:border-slate-600"
                        />
                        <Layers size={14} className="text-brand-textMuted dark:text-slate-400" />
                        Classe dédoublée en demi-groupes
                      </label>
                      {row.isSplit && (
                        <div className="inline-flex items-center gap-2">
                          <span className="text-xs text-brand-textMuted dark:text-slate-400">Groupe n°</span>
                          <input
                            type="number" min="1" max="4" value={row.splitGroupIndex}
                            onChange={(e) => updateRow(i, { splitGroupIndex: e.target.value })}
                            className="w-16 rounded-lg border border-brand-border bg-white px-2 py-1 text-sm tabular-nums text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                          />
                        </div>
                      )}
                    </div>

                    {editing.subjectLevel.sessionTypes && editing.subjectLevel.sessionTypes.length > 0 && (
                      <div className="mt-3">
                        <label className="mb-1 block text-xs font-medium text-brand-textMuted dark:text-slate-400">
                          Type de séance lié aux affectations d'enseignants (optionnel)
                        </label>
                        <select
                          value={row.subjectSessionTypeId} onChange={(e) => updateRow(i, { subjectSessionTypeId: e.target.value })}
                          className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                        >
                          <option value="">Aucun</option>
                          {editing.subjectLevel.sessionTypes.map((st) => (
                            <option key={st.idSubjectSessionType} value={st.idSubjectSessionType}>
                              {SESSION_TYPE_LABELS[st.type as SessionType] ?? st.type} — {st.duration}h
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {ecart !== 0 && rows.length > 0 && (
                <p className="flex items-start gap-1.5 rounded-lg bg-amber-50 p-2.5 text-xs text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
                  <AlertTriangle size={13} className="mt-px shrink-0" />
                  Le total ({totalHours}h) ne correspond pas aux {officielles}h du programme officiel.
                  L'enregistrement reste possible — l'écart est parfois voulu.
                </p>
              )}
            </div>

            <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
              <Button type="button" variant="outline" onClick={() => setEditing(null)}>Annuler</Button>
              <Button type="submit" loading={saveMutation.isPending}>
                {editing.pattern ? 'Mettre à jour' : 'Créer la répartition'}
              </Button>
            </div>
          </form>
          )
        })()}
      </Modal>
    </div>
  )
}
