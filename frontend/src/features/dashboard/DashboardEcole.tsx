import { useQuery } from '@tanstack/react-query'
import { Users, BookOpen, Calendar, GraduationCap, Zap, ClipboardCheck, AlertTriangle, CheckCircle, PieChart as PieChartIcon } from 'lucide-react'
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'

import { PageHero } from '@/components/ui/PageHero'
import { SectionTitle } from '@/components/ui/SectionTitle'
import { StatCard } from '@/components/ui/StatCard'
import { Badge } from '@/components/ui/Badge'
import { QuickActions } from './QuickActions'
import { useAuth } from '@/hooks/useAuth'
import { organisationApi } from '@/api/organisation.api'
import { planningApi, type SolverStatus } from '@/api/planning.api'

const LEVEL_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#f97316', '#ec4899']

const JOB_STATUS_LABELS: Record<SolverStatus, string> = {
  PENDING: 'En attente',
  RUNNING: 'En cours',
  SOLVED: 'Résolu',
  INFEASIBLE: 'Infaisable',
  FAILED: 'Échoué',
  CANCELLED: 'Annulé',
}

const JOB_STATUS_COLORS: Record<SolverStatus, string> = {
  PENDING: '#94a3b8',
  RUNNING: '#3b82f6',
  SOLVED: '#10b981',
  INFEASIBLE: '#f59e0b',
  FAILED: '#ef4444',
  CANCELLED: '#64748b',
}

export default function DashboardEcole() {
  const { user } = useAuth()
  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: organisationApi.subjects.list })
  const { data: classes = [] } = useQuery({ queryKey: ['classes', null], queryFn: organisationApi.classes.list })
  const { data: teachers = [] } = useQuery({ queryKey: ['teachers'], queryFn: organisationApi.teachers.list })
  const { data: rooms = [] } = useQuery({ queryKey: ['rooms'], queryFn: organisationApi.rooms.list })
  const { data: jobs = [] } = useQuery({ queryKey: ['timetable-jobs'], queryFn: () => planningApi.timetable.jobs.list() })
  const { data: config } = useQuery({ queryKey: ['school-config'], queryFn: organisationApi.config.get })

  const currentYear = years.find((y) => y.estCourante)
  const activeClasses = classes.filter((c) => c.estActif).length
  const activeTeachers = teachers.filter((t) => t.estEnPoste).length
  const solvedJobs = jobs.filter((j) => j.status === 'SOLVED').length
  const runningJobs = jobs.filter((j) => j.status === 'RUNNING').length
  const assignments = teachers.reduce((s, t) => s + (t.nombreAffectations ?? 0), 0)

  // Readiness checks
  const checks = [
    { label: 'Année scolaire courante définie', ok: !!currentYear },
    { label: 'Niveaux configurés', ok: levels.length > 0 },
    { label: 'Matières configurées', ok: subjects.length > 0 },
    { label: 'Salles configurées', ok: rooms.length > 0 },
    { label: 'Classes actives', ok: activeClasses > 0 },
    { label: 'Enseignants en poste', ok: activeTeachers > 0 },
    { label: 'Horaires configurés', ok: !!config?.isReadyForGeneration },
    { label: 'Planning généré', ok: solvedJobs > 0 },
  ]

  const readyCount = checks.filter((c) => c.ok).length

  const levelsData = levels
    .map((l, i) => ({
      code: l.code,
      nom: l.nom,
      estActif: l.estActif,
      nombreMatieres: l.nombreMatieres ?? 0,
      count: classes.filter((c) => c.levelCode === l.code && c.estActif).length,
      couleur: LEVEL_COLORS[i % LEVEL_COLORS.length],
    }))
    .filter((l) => l.count > 0)

  const jobStatusData = (['PENDING', 'RUNNING', 'SOLVED', 'INFEASIBLE', 'FAILED', 'CANCELLED'] as SolverStatus[])
    .map((status) => ({ status, label: JOB_STATUS_LABELS[status], count: jobs.filter((j) => j.status === status).length }))
    .filter((s) => s.count > 0)

  return (
    <div className="space-y-8">
      <PageHero
        title={`Bienvenue ${user?.given_name ?? ''}`.trim()}
        subtitle={currentYear ? `Année en cours : ${currentYear.nom}` : 'Aucune année scolaire courante définie'}
      />

      <QuickActions />

      {/* KPIs principaux */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard title="Classes actives" value={activeClasses} icon={GraduationCap} color="blue" />
        <StatCard title="Enseignants" value={activeTeachers} icon={Users} color="green" />
        <StatCard title="Matières" value={subjects.length} icon={BookOpen} color="purple" />
        <StatCard title="Salles" value={rooms.length} icon={Calendar} color="yellow" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Checklist pré-génération */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <SectionTitle
            title="Préparation planning"
            icon={ClipboardCheck}
            accent="teal"
            action={<span className="text-sm font-bold text-brand-text dark:text-slate-100">{readyCount}/{checks.length}</span>}
          />
          <div className="w-full h-2 rounded-full bg-brand-bgSecondary dark:bg-slate-800 mb-4">
            <div
              className="h-2 rounded-full bg-brand-blue transition-all"
              style={{ width: `${(readyCount / checks.length) * 100}%` }}
            />
          </div>
          <div className="space-y-2">
            {checks.map((c, i) => (
              <div key={i} className="flex items-center gap-2">
                {c.ok
                  ? <CheckCircle size={14} className="text-green-500 shrink-0" />
                  : <AlertTriangle size={14} className="text-yellow-500 shrink-0" />
                }
                <span className={`text-xs ${c.ok ? 'text-brand-text dark:text-slate-200' : 'text-brand-textMuted dark:text-slate-400'}`}>{c.label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Planning & Solver */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <SectionTitle title="Génération planning" icon={Zap} accent="blue" />
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center"><Zap size={18} className="text-brand-blue" /></div>
              <div>
                <p className="text-xs text-brand-textMuted dark:text-slate-400">Jobs totaux</p>
                <p className="text-xl font-bold text-brand-text dark:text-slate-100">{jobs.length}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-green-50 dark:bg-emerald-500/10 flex items-center justify-center"><CheckCircle size={18} className="text-green-600 dark:text-emerald-400" /></div>
              <div>
                <p className="text-xs text-brand-textMuted dark:text-slate-400">Plannings résolus</p>
                <p className="text-xl font-bold text-green-600 dark:text-emerald-400">{solvedJobs}</p>
              </div>
            </div>
            {runningJobs > 0 && (
              <div className="p-3 rounded-lg bg-blue-50 dark:bg-blue-500/10 border border-blue-200 dark:border-blue-500/20">
                <p className="text-xs text-blue-800 dark:text-blue-300 font-medium">{runningJobs} job(s) en cours de résolution…</p>
              </div>
            )}
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-purple-50 dark:bg-purple-500/10 flex items-center justify-center"><ClipboardCheck size={18} className="text-purple-600 dark:text-purple-400" /></div>
              <div>
                <p className="text-xs text-brand-textMuted dark:text-slate-400">Affectations totales</p>
                <p className="text-xl font-bold text-brand-text dark:text-slate-100">{assignments}</p>
              </div>
            </div>
            {jobStatusData.length > 0 && (
              <div className="pt-2">
                <ResponsiveContainer width="100%" height={120}>
                  <BarChart data={jobStatusData} layout="vertical" margin={{ left: 0, right: 8, top: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="currentColor" className="text-brand-border dark:text-slate-700" />
                    <XAxis type="number" allowDecimals={false} hide />
                    <YAxis type="category" dataKey="label" width={70} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                      {jobStatusData.map((entry) => (
                        <Cell key={entry.status} fill={JOB_STATUS_COLORS[entry.status]} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Niveaux et classes */}
      {levels.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Donut classes par niveau */}
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
            <SectionTitle title="Classes par niveau" icon={PieChartIcon} accent="purple" />
            {levelsData.length > 0 ? (
              <>
                <ResponsiveContainer width="100%" height={180}>
                  <PieChart>
                    <Pie data={levelsData} dataKey="count" nameKey="nom" cx="50%" cy="50%" innerRadius={45} outerRadius={75} strokeWidth={2}>
                      {levelsData.map((entry) => <Cell key={entry.code} fill={entry.couleur} />)}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
                <div className="space-y-1.5 mt-3">
                  {levelsData.map((entry) => (
                    <div key={entry.code} className="flex items-center gap-2">
                      <span className="w-3 h-3 rounded-full shrink-0" style={{ background: entry.couleur }} />
                      <span className="text-xs text-brand-text dark:text-slate-200 truncate flex-1">{entry.nom}</span>
                      <span className="text-xs font-medium text-brand-text dark:text-slate-200">{entry.count}</span>
                    </div>
                  ))}
                </div>
              </>
            ) : (
              <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-12">Aucune classe active</p>
            )}
          </div>

          {/* Détail par niveau */}
          <div className="lg:col-span-2 bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
            <SectionTitle title="Détail par niveau" icon={GraduationCap} accent="emerald" />
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {levels.map((l) => {
                const levelClasses = classes.filter((c) => c.levelCode === l.code && c.estActif)
                return (
                  <div key={l.idNiveau} className="p-3 rounded-lg border border-brand-border dark:border-slate-700">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs font-mono font-bold text-brand-textMuted dark:text-slate-400">{l.code}</span>
                      <Badge variant={l.estActif ? 'success' : 'default'}>{l.estActif ? 'Actif' : 'Inactif'}</Badge>
                    </div>
                    <p className="text-sm font-medium text-brand-text dark:text-slate-100">{l.nom}</p>
                    <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-1">{levelClasses.length} classe(s) · {l.nombreMatieres ?? 0} matières</p>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
