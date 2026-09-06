import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Play, StopCircle, RefreshCw, CheckCircle, XCircle, AlertTriangle, Clock, Zap, HelpCircle, Trash2, Stethoscope } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Badge } from '@/components/ui/Badge'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { planningApi, type TimetableJob, type SolverStatus, type ConstraintProfile, type PreflightReport } from '@/api/planning.api'
import { organisationApi } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'
import { ScoreExplanationPanel } from './ScoreExplanationPanel'
import { BusinessCard, groupFindings } from './businessFindings'
import { STATUS_LABELS, STATUS_VARIANTS, hasTimetable, describeScore } from './jobStatus'
import { useSolverProgress } from './useSolverProgress'

const STATUS_ICONS: Partial<Record<SolverStatus, React.ReactNode>> = {
  RUNNING: <RefreshCw size={12} className="inline me-1 animate-spin" />,
  SOLVED: <CheckCircle size={12} className="inline me-1" />,
  INFEASIBLE: <AlertTriangle size={12} className="inline me-1" />,
  FAILED: <XCircle size={12} className="inline me-1" />,
}

export default function GenerationPlanning() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [schoolYearId, setSchoolYearId] = useState('')
  const [profileId, setProfileId] = useState('')
  const [explainJobId, setExplainJobId] = useState<number | null>(null)
  const [publishJobId, setPublishJobId] = useState<number | null>(null)
  const [deleteJobId, setDeleteJobId] = useState<number | null>(null)
  const [preflight, setPreflight] = useState<PreflightReport | null>(null)

  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: profiles = [] } = useQuery({
    queryKey: ['constraint-profiles'],
    queryFn: planningApi.constraints.profiles.list,
  })

  // Le serveur pousse l'avancement du solveur sur WebSocket.
  const { connected: liveConnected, progress: liveProgress } = useSolverProgress()

  const { data: jobs = [], isLoading } = useQuery({
    queryKey: ['timetable-jobs'],
    queryFn: () => planningApi.timetable.jobs.list(),
    // Repli quand le socket est indisponible : on retombe sur le polling à 3 s.
    refetchInterval: (query) =>
      !liveConnected &&
      (query.state.data ?? []).some(
        (j: TimetableJob) => j.status === 'RUNNING' || j.status === 'PENDING'
      )
        ? 3000
        : false,
  })

  /**
   * Le contrôle des données, à la demande. Il ne lance rien : il dit ce qui
   * empêcherait la génération d'aboutir — une matière sans enseignant, un
   * pattern qui ne dit plus le même volume que le niveau, un service qui ne
   * tient pas dans la semaine — sans faire attendre le résultat d'un solve.
   */
  const preflightMutation = useMutation({
    mutationFn: () =>
      planningApi.timetable.preflight(
        Number(schoolYearId), profileId ? Number(profileId) : undefined),
    onSuccess: setPreflight,
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Vérification impossible'),
  })

  /** Un changement d'année ou de profil périme le verdict précédent. */
  const oublierLeControle = () => setPreflight(null)

  const generateMutation = useMutation({
    mutationFn: (dto: { schoolYearId: number; constraintProfileId?: number }) =>
      planningApi.timetable.generate(dto),
    onSuccess: (job) => {
      toast.success(`Job #${job.jobId} lancé`)
      qc.invalidateQueries({ queryKey: ['timetable-jobs'] })
      setOpen(false)
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const cancelMutation = useMutation({
    mutationFn: (jobId: number) => planningApi.timetable.jobs.cancel(jobId),
    onSuccess: () => {
      toast.success('Job annulé')
      qc.invalidateQueries({ queryKey: ['timetable-jobs'] })
    },
    onError: () => toast.error('Erreur'),
  })

  const publishMutation = useMutation({
    mutationFn: async (jobId: number) => {
      const result = await planningApi.timetable.jobs.result(jobId)
      return planningApi.timetable.generated.publish(result.id)
    },
    onSuccess: () => {
      toast.success('Planning publié')
      setPublishJobId(null)
      qc.invalidateQueries({ queryKey: ['timetable-jobs'] })
      qc.invalidateQueries({ queryKey: ['generated-timetables'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const deleteMutation = useMutation({
    mutationFn: (jobId: number) => planningApi.timetable.jobs.delete(jobId),
    onSuccess: () => {
      toast.success('Planning supprimé')
      setDeleteJobId(null)
      qc.invalidateQueries({ queryKey: ['timetable-jobs'] })
      qc.invalidateQueries({ queryKey: ['generated-timetables'] })
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e.response?.data?.message ?? 'Erreur'),
  })

  const runningJobs = (jobs as TimetableJob[]).filter((j) => j.status === 'RUNNING' || j.status === 'PENDING')
  const solvedJobs = (jobs as TimetableJob[]).filter((j) => hasTimetable(j.status))

  const columns: Column<TimetableJob>[] = [
    { key: 'jobId', header: 'ID', render: (j) => <span className="font-mono font-semibold">#{j.jobId}</span> },
    { key: 'schoolYearId', header: 'Année', render: (j) => {
      const year = years.find((y) => y.idAnnee === j.schoolYearId)
      return <span className="text-sm">{year?.nom ?? `#${j.schoolYearId}`}</span>
    }},
    { key: 'status', header: 'Statut', render: (j) => (
      <Badge variant={STATUS_VARIANTS[j.status]}>
        {STATUS_ICONS[j.status]}{STATUS_LABELS[j.status]}
      </Badge>
    )},
    {
      key: 'scoreAchieved', header: 'Résultat',
      render: (j) => {
        const live = liveProgress[j.jobId]
        // Job en cours : on montre l'état courant du solveur plutôt qu'un tiret.
        if (j.status === 'RUNNING' && live?.score) {
          return (
            <span className="text-sm text-brand-blue dark:text-blue-300" title={live.score}>
              {describeScore(live.score)}
              {live.totalSessions ? ` · ${live.placedSessions}/${live.totalSessions} séances` : ''}
            </span>
          )
        }
        return (
          <span className="text-sm text-brand-textMuted dark:text-slate-400" title={j.scoreAchieved ?? undefined}>
            {hasTimetable(j.status) ? describeScore(j.scoreAchieved) : '—'}
          </span>
        )
      },
    },
    { key: 'startedAt', header: 'Démarré', render: (j) => <span className="text-xs text-brand-textMuted dark:text-slate-400">{j.startedAt ? formatDate(j.startedAt) : '—'}</span> },
    { key: 'finishedAt', header: 'Terminé', render: (j) => <span className="text-xs text-brand-textMuted dark:text-slate-400">{j.finishedAt ? formatDate(j.finishedAt) : '—'}</span> },
    { key: 'errorMessage', header: 'Erreur', render: (j) => j.errorMessage ? <span className="text-xs text-red-500 truncate max-w-40 block">{j.errorMessage}</span> : null },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (j) => (
        <div className="flex gap-1">
          {(j.status === 'RUNNING' || j.status === 'PENDING') && (
            <Button size="sm" variant="outline" onClick={() => cancelMutation.mutate(j.jobId)}>
              <StopCircle size={12} /> Annuler
            </Button>
          )}
          {(hasTimetable(j.status) || j.status === 'FAILED') && (
            <Button size="sm" variant="outline" onClick={() => setExplainJobId(j.jobId)}>
              <HelpCircle size={12} /> {j.status === 'INFEASIBLE' ? 'Voir les conflits' : 'Pourquoi ?'}
            </Button>
          )}
          {hasTimetable(j.status) && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => (j.status === 'INFEASIBLE' ? setPublishJobId(j.jobId) : publishMutation.mutate(j.jobId))}
            >
              <CheckCircle size={12} /> Publier
            </Button>
          )}
          {j.status !== 'RUNNING' && j.status !== 'PENDING' && (
            <Button size="sm" variant="outline" onClick={() => setDeleteJobId(j.jobId)}>
              <Trash2 size={12} /> Supprimer
            </Button>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Génération du planning"
        subtitle="Lancez le solveur Timefold pour générer l'emploi du temps"
        actions={<Button onClick={() => setOpen(true)}><Play size={16} /> Lancer la génération</Button>}
      />

      <div className="grid grid-cols-3 gap-3">
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center"><Zap size={16} className="text-brand-blue" /></div>
          <div><p className="text-xs text-brand-textMuted dark:text-slate-400">Jobs total</p><p className="text-xl font-bold text-brand-text dark:text-slate-100">{(jobs as TimetableJob[]).length}</p></div>
        </div>
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-yellow-50 dark:bg-amber-500/10 flex items-center justify-center"><Clock size={16} className="text-yellow-500 dark:text-amber-400" /></div>
          <div><p className="text-xs text-brand-textMuted dark:text-slate-400">En cours</p><p className="text-xl font-bold text-brand-text dark:text-slate-100">{runningJobs.length}</p></div>
        </div>
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-green-50 dark:bg-emerald-500/10 flex items-center justify-center"><CheckCircle size={16} className="text-green-600 dark:text-emerald-400" /></div>
          <div><p className="text-xs text-brand-textMuted dark:text-slate-400">Résolus</p><p className="text-xl font-bold text-brand-text dark:text-slate-100">{solvedJobs.length}</p></div>
        </div>
      </div>

      {runningJobs.length > 0 && (
        <div className="flex items-start gap-3 p-4 bg-blue-50 dark:bg-blue-500/10 border border-blue-200 dark:border-blue-500/20 rounded-xl">
          <RefreshCw size={16} className="text-brand-blue animate-spin shrink-0 mt-0.5" />
          <div className="text-sm text-blue-800 dark:text-blue-300">
            <p>
              {runningJobs.length} job(s) en cours.{' '}
              {liveConnected
                ? "L'avancement s'affiche en direct."
                : 'Connexion temps réel indisponible — rafraîchissement toutes les 3 secondes.'}
            </p>
            {liveConnected && (
              <ul className="mt-1 space-y-0.5">
                {runningJobs.map((j) => {
                  const live = liveProgress[j.jobId]
                  return (
                    <li key={j.jobId} className="text-xs">
                      <span className="font-mono font-semibold">#{j.jobId}</span>{' '}
                      {live
                        ? `${live.placedSessions ?? 0}/${live.totalSessions ?? 0} séances placées — ${describeScore(live.score)}`
                        : 'initialisation du solveur…'}
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </div>
      )}

      <DataTable columns={columns} data={jobs as TimetableJob[]} keyField="jobId" loading={isLoading} emptyMessage="Aucun job de génération lancé" />

      <Modal open={open} onClose={() => { setOpen(false); oublierLeControle() }} title="Lancer la génération du planning" size="md">
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Année scolaire *</label>
            <select value={schoolYearId} onChange={(e) => { setSchoolYearId(e.target.value); oublierLeControle() }} className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200">
              <option value="">Sélectionner</option>
              {years.filter((y) => y.estActive).map((y) => <option key={y.idAnnee} value={y.idAnnee}>{y.nom}</option>)}
            </select>
          </div>
          <div>
            <label className="text-sm font-medium text-brand-text dark:text-slate-200 block mb-1">Profil de contraintes (optionnel)</label>
            <select value={profileId} onChange={(e) => { setProfileId(e.target.value); oublierLeControle() }} className="w-full border border-brand-border dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-white dark:bg-slate-900 text-brand-text dark:text-slate-200">
              {/* Sans choix explicite, le solveur prend le profil actif de l'année : on le nomme au lieu de dire « par défaut ». */}
              <option value="">Profil actif de l'année</option>
              {(profiles as ConstraintProfile[]).map((p) => <option key={p.idConstraintProfile} value={p.idConstraintProfile}>{p.name}{p.active ? ' (actif)' : ''}</option>)}
            </select>
          </div>
          {preflight && (
            <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
              {preflight.ready ? (
                <div className="p-3 rounded-lg bg-green-50 dark:bg-emerald-500/10 border border-green-200 dark:border-emerald-500/20 text-xs text-green-800 dark:text-emerald-300">
                  Aucune anomalie bloquante dans les données. La génération peut partir — sans que
                  ce soit pour autant la promesse d'un emploi du temps sans conflit : les
                  impossibilités croisées ne se voient qu'en cherchant.
                </div>
              ) : (
                <div className="p-3 rounded-lg bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 text-xs text-red-800 dark:text-red-300">
                  {preflight.blockingCount} anomalie(s) empêchent la génération. Elles viennent des
                  données du programme, pas du solveur : les corriger est la seule façon d'aboutir.
                </div>
              )}
              {groupFindings(preflight.findings).map(([code, findings]) => (
                <BusinessCard key={code} code={code} findings={findings} />
              ))}
            </div>
          )}
          <div className="p-3 rounded-lg bg-yellow-50 dark:bg-amber-500/10 border border-yellow-200 dark:border-amber-500/20 text-xs text-yellow-800 dark:text-amber-300">
            La génération peut prendre plusieurs minutes selon la complexité de l'emploi du temps. Vous pouvez fermer cette fenêtre, le job continuera en arrière-plan.
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => { setOpen(false); oublierLeControle() }}>Annuler</Button>
            <Button
              variant="outline"
              type="button"
              onClick={() => preflightMutation.mutate()}
              loading={preflightMutation.isPending}
              disabled={!schoolYearId}
            >
              <Stethoscope size={15} /> Vérifier les données
            </Button>
            <Button
              onClick={() => generateMutation.mutate({
                schoolYearId: Number(schoolYearId),
                constraintProfileId: profileId ? Number(profileId) : undefined,
              })}
              loading={generateMutation.isPending}
              disabled={!schoolYearId}
            >
              <Play size={15} /> Lancer
            </Button>
          </div>
        </div>
      </Modal>

      <ScoreExplanationPanel jobId={explainJobId} onClose={() => setExplainJobId(null)} />

      <ConfirmDialog
        open={publishJobId !== null}
        onClose={() => setPublishJobId(null)}
        onConfirm={() => publishJobId !== null && publishMutation.mutate(publishJobId)}
        loading={publishMutation.isPending}
        title="Publier un planning avec conflits ?"
        message="Ce planning contient des contraintes non respectées. Il reste exploitable, mais les conflits seront visibles par les enseignants et les classes. Vous pouvez d'abord les consulter via « Voir les conflits » et corriger les séances à la main."
        confirmLabel="Publier quand même"
        variant="primary"
      />

      <ConfirmDialog
        open={deleteJobId !== null}
        onClose={() => setDeleteJobId(null)}
        onConfirm={() => deleteJobId !== null && deleteMutation.mutate(deleteJobId)}
        loading={deleteMutation.isPending}
        title="Supprimer ce planning ?"
        message="Cette action supprime définitivement le job, ses séances et son résultat. Un emploi du temps encore publié ne peut pas être supprimé — publiez-en un autre à sa place d'abord."
        confirmLabel="Supprimer définitivement"
        variant="danger"
      />
    </div>
  )
}
