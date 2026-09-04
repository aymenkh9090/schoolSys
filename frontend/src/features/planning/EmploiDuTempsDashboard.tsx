import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Zap, Clock, CheckCircle, Send, AlertTriangle, ArrowRight, Play, Eye, Trash2 } from 'lucide-react'
import { useState } from 'react'

import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { planningApi, type GeneratedTimetable } from '@/api/planning.api'
import { hasTimetable, describeScore } from './jobStatus'
import { organisationApi } from '@/api/organisation.api'
import { cn } from '@/lib/utils'

interface StepInfo {
  key: string
  label: string
  done: boolean
}

export default function EmploiDuTempsDashboard() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [deleteJobId, setDeleteJobId] = useState<number | null>(null)

  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: jobs = [] } = useQuery({ queryKey: ['timetable-jobs'], queryFn: () => planningApi.timetable.jobs.list() })
  const { data: generated = [] } = useQuery({
    queryKey: ['generated-timetables'],
    queryFn: () => planningApi.timetable.generated.list(),
  })

  const publishMutation = useMutation({
    mutationFn: (id: number) => planningApi.timetable.generated.publish(id),
    onSuccess: () => {
      toast.success('Planning publié')
      qc.invalidateQueries({ queryKey: ['generated-timetables'] })
    },
    onError: () => toast.error('Erreur'),
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

  const runningJobs = jobs.filter((j) => j.status === 'RUNNING' || j.status === 'PENDING')
  const solvedJobs = jobs.filter((j) => hasTimetable(j.status))
  const activeGenerated = generated.filter((g) => g.status !== 'ARCHIVED')
  const publishedCount = generated.filter((g) => g.status === 'PUBLISHED').length
  const totalHardViolations = activeGenerated.reduce((s, g) => s + g.hardViolations, 0)
  const totalMediumViolations = activeGenerated.reduce((s, g) => s + g.mediumViolations, 0)
  const totalConflicts = totalHardViolations + totalMediumViolations

  const steps: StepInfo[] = [
    { key: 'generation', label: 'Génération', done: solvedJobs.length > 0 },
    { key: 'resolution', label: 'Résolution', done: activeGenerated.some((g) => g.hardViolations === 0) },
    { key: 'publication', label: 'Publication', done: publishedCount > 0 },
  ]

  const recent = [...generated].sort((a, b) => b.id - a.id).slice(0, 8)

  const columns: Column<GeneratedTimetable>[] = [
    { key: 'id', header: 'ID', render: (g) => <span className="font-mono font-semibold">#{g.id}</span> },
    {
      key: 'academicYearId', header: 'Année',
      render: (g) => <span className="text-sm">{years.find((y) => y.idAnnee === g.academicYearId)?.nom ?? `#${g.academicYearId}`}</span>,
    },
    {
      key: 'scoreAchieved', header: 'Résultat',
      render: (g) => (
        <span className="text-sm text-brand-textMuted dark:text-slate-400" title={g.scoreAchieved ?? undefined}>
          {describeScore(g.scoreAchieved)}
        </span>
      ),
    },
    { key: 'totalSessions', header: 'Séances', render: (g) => g.totalSessions },
    {
      key: 'violations', header: 'Violations',
      render: (g) => (
        <div className="flex gap-1">
          {g.hardViolations > 0 && <Badge variant="danger">{g.hardViolations} dure(s)</Badge>}
          {g.mediumViolations > 0 && <Badge variant="warning">{g.mediumViolations} moy.</Badge>}
          {g.hardViolations === 0 && g.mediumViolations === 0 && <Badge variant="success">Aucune</Badge>}
        </div>
      ),
    },
    {
      key: 'status', header: 'Statut',
      render: (g) => (
        <Badge variant={g.status === 'PUBLISHED' ? 'success' : g.status === 'ARCHIVED' ? 'default' : 'warning'}>
          {g.status === 'PUBLISHED' ? 'Publié' : g.status === 'ARCHIVED' ? 'Archivé' : 'Brouillon'}
        </Badge>
      ),
    },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (g) => (
        <div className="flex gap-1">
          {g.status === 'DRAFT' && g.hardViolations === 0 && (
            <Button size="sm" variant="outline" onClick={() => publishMutation.mutate(g.id)} loading={publishMutation.isPending}>
              <Send size={12} /> Publier
            </Button>
          )}
          {g.status !== 'PUBLISHED' && (
            <Button size="sm" variant="outline" onClick={() => setDeleteJobId(g.jobId)}>
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
        title="Emploi du temps"
        subtitle="Gérez et générez les emplois du temps de l'établissement"
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => navigate('/ecole/planning/consultation')}>
              <Eye size={16} /> Consultation
            </Button>
            <Button onClick={() => navigate('/ecole/planning/generer')}>
              <Play size={16} /> Générer
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard title="Jobs total" value={jobs.length} icon={Zap} color="blue" />
        <StatCard title="En cours" value={runningJobs.length} icon={Clock} color="amber" />
        <StatCard title="Résolus" value={solvedJobs.length} icon={CheckCircle} color="green" />
        <StatCard title="Publiés" value={publishedCount} icon={Send} color="purple" />
      </div>

      {totalConflicts > 0 && (
        <div className="flex items-center gap-3 p-4 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 rounded-xl">
          <AlertTriangle size={18} className="text-red-600 dark:text-red-400 shrink-0" />
          <p className="text-sm text-red-800 dark:text-red-300">
            {totalHardViolations > 0 && <strong>{totalHardViolations} violation(s) dure(s)</strong>}
            {totalHardViolations > 0 && totalMediumViolations > 0 && ' et '}
            {totalMediumViolations > 0 && <strong>{totalMediumViolations} violation(s) moyenne(s)</strong>}
            {' '}sur les plannings générés non archivés — voir « Générer » pour le détail par job.
          </p>
        </div>
      )}

      {/* Stepper */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
        <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">Workflow de génération</h3>
        <div className="flex items-center">
          {steps.map((s, i) => (
            <div key={s.key} className="flex items-center flex-1 last:flex-none">
              <button
                onClick={() => navigate('/ecole/planning/generer')}
                className="flex flex-col items-center gap-2 w-28 text-center group shrink-0"
              >
                <span
                  className={cn(
                    'w-10 h-10 rounded-full flex items-center justify-center transition-colors',
                    s.done
                      ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-400'
                      : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-500'
                  )}
                >
                  {s.done ? <CheckCircle size={18} /> : <span className="text-sm font-semibold">{i + 1}</span>}
                </span>
                <span className="text-xs font-medium text-brand-text dark:text-slate-200 group-hover:text-brand-blue transition-colors">
                  {s.label}
                </span>
              </button>
              {i < steps.length - 1 && (
                <div className={cn('h-0.5 flex-1 mt-5', s.done ? 'bg-emerald-300 dark:bg-emerald-500/40' : 'bg-brand-border dark:bg-slate-700')} />
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Dernières générations */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Dernières générations</h3>
          <button onClick={() => navigate('/ecole/planning/generer')} className="text-xs font-medium text-brand-blue hover:underline flex items-center gap-1">
            Voir tout <ArrowRight size={12} />
          </button>
        </div>
        <DataTable columns={columns} data={recent} keyField="id" emptyMessage="Aucun planning généré pour le moment" />
      </div>

      <ConfirmDialog
        open={deleteJobId !== null}
        onClose={() => setDeleteJobId(null)}
        onConfirm={() => deleteJobId !== null && deleteMutation.mutate(deleteJobId)}
        loading={deleteMutation.isPending}
        title="Supprimer ce planning ?"
        message="Cette action supprime définitivement le job, ses séances et son résultat. Un emploi du temps publié ne peut pas être supprimé — publiez-en un autre à sa place d'abord."
        confirmLabel="Supprimer définitivement"
        variant="danger"
      />
    </div>
  )
}
