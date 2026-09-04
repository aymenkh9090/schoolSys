import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Search, Printer, MousePointerClick, AlertTriangle, LayoutGrid } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Pagination } from '@/components/ui/Pagination'
import { planningApi, type ClassTimetableView, type TeacherTimetableView, type RoomTimetableView } from '@/api/planning.api'
import { organisationApi } from '@/api/organisation.api'
import { useAuth } from '@/hooks/useAuth'
import { cn } from '@/lib/utils'
import { TimetableGrid } from './TimetableGrid'
import { STATUS_LABELS, STATUS_VARIANTS, hasTimetable, describeScore } from './jobStatus'

type ViewMode = 'class' | 'teacher' | 'room'
const VIEW_MODES: { value: ViewMode; label: string }[] = [
  { value: 'class', label: 'Classe' },
  { value: 'teacher', label: 'Enseignant' },
  { value: 'room', label: 'Salle' },
]

/** Code interne de la requête « tout afficher » — jamais un code réel. */
const ALL = '*'

/** Grilles affichées par page en mode « toutes ». 0 = pas de pagination. */
const PAGE_SIZES = [6, 12, 24, 0]
const DEFAULT_PAGE_SIZE = 6

const ALL_LABELS: Record<ViewMode, string> = {
  class: 'Toutes les classes',
  teacher: 'Tous les enseignants',
  room: 'Toutes les salles',
}

type AnyTimetableView = ClassTimetableView | TeacherTimetableView | RoomTimetableView

function viewKey(view: AnyTimetableView): string {
  if ('classCode' in view) return view.classCode
  if ('teacherCode' in view) return view.teacherCode
  return view.roomCode
}

function viewTitle(view: AnyTimetableView): string {
  if ('classCode' in view) return `Classe ${view.classCode}`
  if ('teacherName' in view) return `Enseignant — ${view.teacherName}`
  return `Salle ${view.roomCode}`
}

export default function ConsultationPlanning() {
  const qc = useQueryClient()
  const { isSchoolAdmin } = useAuth()
  const [viewMode, setViewMode] = useState<ViewMode>('class')
  const [jobId, setJobId] = useState('')
  const [classCode, setClassCode] = useState('')
  const [teacherCode, setTeacherCode] = useState('')
  const [roomCode, setRoomCode] = useState('')
  const [query, setQuery] = useState<{ jobId: number; mode: ViewMode; code: string } | null>(null)
  const [yearId, setYearId] = useState('')
  const [levelId, setLevelId] = useState('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)

  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })
  const { data: classes = [] } = useQuery({
    queryKey: ['classes', yearId || null, levelId || null],
    queryFn: () => {
      if (levelId && yearId) return organisationApi.classes.byLevelAndYear(Number(levelId), Number(yearId))
      if (levelId) return organisationApi.classes.byLevel(Number(levelId))
      if (yearId) return organisationApi.classes.byYear(Number(yearId))
      return organisationApi.classes.list()
    },
  })
  const { data: teachers = [] } = useQuery({ queryKey: ['teachers-actifs'], queryFn: organisationApi.teachers.getActifs })
  const { data: rooms = [] } = useQuery({ queryKey: ['rooms'], queryFn: organisationApi.rooms.list })
  // Les emplois du temps générés (dont les brouillons) ne concernent que
  // l'administration ; les autres rôles ne consultent que le planning publié.
  const { data: jobs = [] } = useQuery({
    queryKey: ['timetable-jobs'],
    queryFn: () => planningApi.timetable.jobs.list(),
    enabled: isSchoolAdmin,
  })

  const { data: generated = [] } = useQuery({
    queryKey: ['timetables-generated'],
    queryFn: () => planningApi.timetable.generated.list(),
    enabled: !isSchoolAdmin,
  })

  const published = generated
    .filter((g) => g.status === 'PUBLISHED')
    .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))[0]

  // Hors administration, il n'y a rien à choisir : le planning consulté est
  // toujours le dernier publié.
  useEffect(() => {
    if (!isSchoolAdmin && published) setJobId(String(published.jobId))
  }, [isSchoolAdmin, published])

  // Une seule forme de résultat — liste — que l'on affiche une grille ou toutes.
  const { data: views = [], isLoading, isError, error } = useQuery<AnyTimetableView[]>({
    queryKey: ['timetable-view', query],
    queryFn: async () => {
      const { jobId, mode, code } = query!
      const v = planningApi.timetable.views
      if (mode === 'teacher') return code === ALL ? v.allTeachers(jobId) : [await v.byTeacher(jobId, code)]
      if (mode === 'room') return code === ALL ? v.allRooms(jobId) : [await v.byRoom(jobId, code)]
      return code === ALL ? v.allClasses(jobId) : [await v.byClass(jobId, code)]
    },
    enabled: query !== null,
  })

  // « Toutes les classes » respecte les filtres année/niveau : le backend renvoie
  // toutes les classes du job, on ne garde que celles de la sélection courante.
  const classCodesInScope = new Set(classes.map((c) => c.code))
  const shownViews =
    query?.mode === 'class' && query.code === ALL && (yearId || levelId)
      ? views.filter((v) => 'classCode' in v && classCodesInScope.has(v.classCode))
      : views

  // Pagination client : les grilles arrivent en une requête, on n'en monte qu'une
  // page à la fois — au-delà de quelques emplois du temps le DOM devient lourd.
  // pageSize 0 (« Tout ») désactive le découpage, notamment pour imprimer le lot.
  const effectivePageSize = pageSize || Math.max(shownViews.length, 1)
  const lastPage = Math.max(0, Math.ceil(shownViews.length / effectivePageSize) - 1)
  const safePage = Math.min(page, lastPage)
  const pagedViews = shownViews.slice(safePage * effectivePageSize, (safePage + 1) * effectivePageSize)
  const isPaginated = pagedViews.length < shownViews.length

  const currentCode = viewMode === 'class' ? classCode : viewMode === 'teacher' ? teacherCode : roomCode

  function handleSearch() {
    if (jobId && currentCode) {
      setQuery({ jobId: Number(jobId), mode: viewMode, code: currentCode })
      setPage(0)
    }
  }

  /** Toutes les grilles du mode courant, sans passer par une sélection unitaire. */
  function handleShowAll() {
    if (jobId) {
      setQuery({ jobId: Number(jobId), mode: viewMode, code: ALL })
      setPage(0)
    }
  }

  function switchMode(mode: ViewMode) {
    setViewMode(mode)
    setQuery(null)
    setPage(0)
  }

  // Année et niveau restreignent la liste des classes : la classe déjà choisie peut
  // ne plus en faire partie, on repart donc d'une sélection vide.
  function resetClassSelection() {
    setClassCode('')
    setPage(0)
    if (viewMode === 'class') setQuery(null)
  }

  const solvedJobs = jobs.filter((j) => hasTimetable(j.status))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Consultation planning"
        subtitle="Visualisez le planning généré par classe, enseignant ou salle"
      />

      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 print:hidden space-y-4">
        <div className="inline-flex rounded-lg border border-brand-border dark:border-slate-700 p-0.5 bg-brand-bgSecondary dark:bg-slate-800">
          {VIEW_MODES.map((m) => (
            <button
              key={m.value}
              type="button"
              onClick={() => switchMode(m.value)}
              className={cn(
                'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
                viewMode === m.value
                  ? 'bg-white dark:bg-slate-900 text-brand-blue shadow-sm'
                  : 'text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200'
              )}
            >
              {m.label}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap gap-4 items-end">
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Année</label>
            <select value={yearId} onChange={(e) => { setYearId(e.target.value); resetClassSelection() }} className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-40">
              <option value="">Toutes</option>
              {years.map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
            </select>
          </div>
          {isSchoolAdmin ? (
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Emploi du temps généré</label>
              <select value={jobId} onChange={(e) => setJobId(e.target.value)} className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-64">
                <option value="">Sélectionner</option>
                {solvedJobs.map((j) => (
                  <option key={j.jobId} value={j.jobId}>
                    #{j.jobId} — {j.finishedAt ? new Date(j.finishedAt).toLocaleDateString('fr-FR') : ''} — {describeScore(j.scoreAchieved)}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            published && (
              <div>
                <span className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Emploi du temps</span>
                <p className="rounded-lg border border-brand-border px-3 py-2 text-sm text-brand-textMuted dark:border-slate-700 dark:text-slate-400">
                  Planning publié
                  {published.publishedAt ? ` le ${new Date(published.publishedAt).toLocaleDateString('fr-FR')}` : ''}
                </p>
              </div>
            )
          )}
          {viewMode === 'class' && (
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Niveau</label>
              <select value={levelId} onChange={(e) => { setLevelId(e.target.value); resetClassSelection() }} className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-40">
                <option value="">Tous</option>
                {levels.map((l) => <option key={l.idNiveau} value={l.idNiveau}>{l.nom}</option>)}
              </select>
            </div>
          )}
          {viewMode === 'class' && (
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Classe</label>
              <select value={classCode} onChange={(e) => setClassCode(e.target.value)} className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-40">
                <option value="">Sélectionner</option>
                {classes.map((c) => <option key={c.idClasse} value={c.code}>{c.code}</option>)}
              </select>
            </div>
          )}
          {viewMode === 'teacher' && (
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Enseignant</label>
              <select value={teacherCode} onChange={(e) => setTeacherCode(e.target.value)} className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-56">
                <option value="">Sélectionner</option>
                {teachers.map((t) => <option key={t.idEnseignant} value={t.codeEnseignant}>{t.nomComplet}</option>)}
              </select>
            </div>
          )}
          {viewMode === 'room' && (
            <div>
              <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Salle</label>
              <select value={roomCode} onChange={(e) => setRoomCode(e.target.value)} className="border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200 w-40">
                <option value="">Sélectionner</option>
                {rooms.map((r) => <option key={r.idSalle} value={r.codeSalle}>{r.codeSalle}</option>)}
              </select>
            </div>
          )}
          <Button onClick={handleSearch} disabled={!jobId || !currentCode}><Search size={16} /> Afficher</Button>
          <Button variant="outline" onClick={handleShowAll} disabled={!jobId}>
            <LayoutGrid size={16} /> {ALL_LABELS[viewMode]}
          </Button>
        </div>
      </div>

      {(isSchoolAdmin ? solvedJobs.length === 0 : !published) && (
        <div className="p-4 bg-yellow-50 dark:bg-amber-500/10 border border-yellow-200 dark:border-amber-500/20 rounded-xl text-sm text-yellow-800 dark:text-amber-300 print:hidden">
          {isSchoolAdmin
            ? 'Aucun job résolu trouvé. Lancez une génération de planning depuis l\'onglet "Génération".'
            : "Aucun emploi du temps n'est publié pour le moment : l'administration doit d'abord en publier un."}
        </div>
      )}

      {query === null ? (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-12 text-center print:hidden">
          <CalendarDays size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
          <p className="text-sm text-brand-textMuted dark:text-slate-400">Sélectionnez un job résolu et {viewMode === 'class' ? 'une classe' : viewMode === 'teacher' ? 'un enseignant' : 'une salle'} pour afficher le planning.</p>
        </div>
      ) : isLoading ? (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-8 print:hidden">
          <div className="h-64 animate-pulse bg-brand-bgSecondary dark:bg-slate-800 rounded-xl" />
        </div>
      ) : isError ? (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-red-200 dark:border-red-500/30 p-8 text-center print:hidden">
          <AlertTriangle size={28} className="mx-auto mb-3 text-red-500" />
          <p className="text-sm text-brand-text dark:text-slate-200">Le planning n'a pas pu être chargé.</p>
          <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">
            {(error as { response?: { data?: { message?: string } } })?.response?.data?.message
              ?? "Le serveur a refusé la requête. Si l'API vient d'être mise à jour, elle doit être redémarrée."}
          </p>
        </div>
      ) : shownViews.length > 0 ? (
        <div className="space-y-6">
          <div className="flex items-center justify-between gap-3 flex-wrap print:hidden">
            <p className="text-sm text-brand-textMuted dark:text-slate-400">
              {shownViews.length > 1
                ? `${shownViews.length} plannings`
                : `Planning — ${viewTitle(shownViews[0])}`}
            </p>
            <div className="flex items-center gap-2">
              {shownViews.length > PAGE_SIZES[0] && (
                <label className="flex items-center gap-2 text-xs text-brand-textMuted dark:text-slate-400">
                  Par page
                  <select
                    value={pageSize}
                    onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0) }}
                    className="border border-brand-border dark:border-slate-700 rounded-lg px-2 py-1 text-xs bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200"
                  >
                    {PAGE_SIZES.map((n) => <option key={n} value={n}>{n === 0 ? 'Tout' : n}</option>)}
                  </select>
                </label>
              )}
              <Badge variant={STATUS_VARIANTS[shownViews[0].status]}>{STATUS_LABELS[shownViews[0].status]}</Badge>
              <Button variant="outline" size="sm" onClick={() => window.print()}>
                <Printer size={14} /> Imprimer / Export PDF
              </Button>
            </div>
          </div>

          {isPaginated && (
            <p className="text-xs text-brand-textMuted dark:text-slate-400 print:hidden">
              L'impression ne reprend que les grilles affichées — choisissez « Par page : Tout » pour
              imprimer les {shownViews.length} plannings.
            </p>
          )}

          {shownViews[0].status === 'INFEASIBLE' && (
            <div className="flex items-start gap-2 text-xs text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-lg px-3 py-2 print:hidden">
              <AlertTriangle size={14} className="shrink-0 mt-0.5" />
              <span>
                Ce planning est complet — toutes les séances sont placées — mais certaines contraintes
                restent violées. Le détail des conflits est consultable depuis <strong>Génération du
                planning → Voir les conflits</strong>.
                {isSchoolAdmin && ' Vous pouvez corriger les séances concernées directement ci-dessous.'}
              </span>
            </div>
          )}
          {isSchoolAdmin && hasTimetable(shownViews[0].status) && (
            <div className="flex items-center gap-2 text-xs text-brand-blue bg-blue-50 dark:bg-blue-500/10 border border-blue-100 dark:border-blue-500/20 rounded-lg px-3 py-2 print:hidden">
              <MousePointerClick size={14} className="shrink-0" />
              Glissez une séance vers un autre créneau pour la déplacer, ou survolez-la pour changer sa salle.
            </div>
          )}

          <Pagination
            page={safePage}
            pageSize={effectivePageSize}
            total={shownViews.length}
            onPageChange={setPage}
            className="print:hidden"
          />

          {pagedViews.map((v) => (
            <div
              key={viewKey(v)}
              /* une grille par page à l'impression, sauf la dernière */
              className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5 print:border-0 print:p-0 print:break-after-page print:last:break-after-auto"
            >
              <div className="mb-5">
                <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Planning — {viewTitle(v)}</h3>
                {v.scoreAchieved && <p className="text-xs text-brand-textMuted dark:text-slate-400 print:hidden">Score Timefold : {v.scoreAchieved}</p>}
                <p className="hidden print:block text-xs text-brand-textMuted mt-1">Généré le {new Date().toLocaleDateString('fr-FR')}</p>
              </div>
              <TimetableGrid
                jobId={query.jobId}
                view={v}
                canEdit={isSchoolAdmin && hasTimetable(v.status)}
                onChanged={() => qc.invalidateQueries({ queryKey: ['timetable-view', query] })}
              />
            </div>
          ))}

          <Pagination
            page={safePage}
            pageSize={effectivePageSize}
            total={shownViews.length}
            onPageChange={setPage}
            className="print:hidden"
          />
        </div>
      ) : (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-8 text-center print:hidden">
          <p className="text-sm text-brand-textMuted dark:text-slate-400">Aucun planning disponible pour cette combinaison.</p>
        </div>
      )}
    </div>
  )
}
