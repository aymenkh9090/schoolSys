import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts'
import { Users, GraduationCap, TrendingDown, TableProperties, ClipboardList, ArrowRight } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { organisationApi } from '@/api/organisation.api'
import { absenceApi } from '@/api/absence.api'
import { planningApi } from '@/api/planning.api'

const WORKLOAD_COLORS = { sousCharge: '#f59e0b', normal: '#10b981', surcharge: '#ef4444', nonPlanifie: '#94a3b8' }

export default function DashboardStatistiques() {
  const navigate = useNavigate()

  const { data: eleves = [] } = useQuery({ queryKey: ['eleves-all'], queryFn: organisationApi.eleves.list })
  const { data: teachers = [] } = useQuery({ queryKey: ['teachers'], queryFn: organisationApi.teachers.list })
  const { data: workload = [] } = useQuery({ queryKey: ['teachers-workload'], queryFn: organisationApi.teachers.getWorkload })
  const { data: absenceStats } = useQuery({
    queryKey: ['statistiques-absences', 'MOIS'],
    queryFn: () => absenceApi.statistiques.dashboard('MOIS'),
  })
  const { data: jobs = [] } = useQuery({ queryKey: ['timetable-jobs'], queryFn: () => planningApi.timetable.jobs.list() })
  const { data: generated = [] } = useQuery({
    queryKey: ['generated-timetables'],
    queryFn: () => planningApi.timetable.generated.list(),
  })

  const elevesActifs = eleves.filter((e) => e.estActif).length
  const enseignantsActifs = teachers.filter((t) => t.estEnPoste).length

  const workloadBuckets = workload.reduce(
    (acc, t) => {
      if (t.maxHeuresSemaine && t.totalHeures != null) {
        const pct = (t.totalHeures / t.maxHeuresSemaine) * 100
        if (pct > 100) acc.surcharge += 1
        else if (pct < 60) acc.sousCharge += 1
        else acc.normal += 1
      } else {
        acc.nonPlanifie += 1
      }
      return acc
    },
    { sousCharge: 0, normal: 0, surcharge: 0, nonPlanifie: 0 }
  )
  const workloadData = [
    { key: 'sousCharge', label: 'Sous-chargés', count: workloadBuckets.sousCharge },
    { key: 'normal', label: 'Normal', count: workloadBuckets.normal },
    { key: 'surcharge', label: 'Surchargés', count: workloadBuckets.surcharge },
    { key: 'nonPlanifie', label: 'Non planifiés', count: workloadBuckets.nonPlanifie },
  ].filter((d) => d.count > 0)

  const solvedJobs = jobs.filter((j) => j.status === 'SOLVED').length
  const publishedCount = generated.filter((g) => g.status === 'PUBLISHED').length
  const activeGenerated = generated.filter((g) => g.status !== 'ARCHIVED')
  const conflicts = activeGenerated.reduce((s, g) => s + g.hardViolations + g.mediumViolations, 0)

  const domainCards = [
    {
      key: 'planning',
      icon: TableProperties,
      title: 'Emploi du temps',
      to: '/ecole/planning',
      lines: [
        { label: 'Jobs résolus', value: solvedJobs },
        { label: 'Publiés', value: publishedCount },
        { label: 'Conflits', value: conflicts },
      ],
    },
    {
      key: 'absences',
      icon: ClipboardList,
      title: 'Absences (mois en cours)',
      to: '/ecole/absences/stats',
      lines: absenceStats
        ? [
            { label: 'Absences', value: absenceStats.totalAbsences },
            { label: 'Retards', value: absenceStats.totalRetards },
            { label: "Taux d'absentéisme", value: `${absenceStats.tauxAbsenteisme.toFixed(1)}%` },
          ]
        : [{ label: 'Aucune donnée', value: '—' }],
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Statistiques"
        subtitle="Synthèse des indicateurs clés de l'établissement"
      />

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard title="Élèves actifs" value={elevesActifs} icon={Users} color="blue" />
        <StatCard title="Enseignants en poste" value={enseignantsActifs} icon={GraduationCap} color="green" />
        <StatCard
          title="Taux d'absentéisme (mois)"
          value={absenceStats ? `${absenceStats.tauxAbsenteisme.toFixed(1)}%` : '—'}
          icon={TrendingDown}
          color="amber"
        />
        <StatCard
          title="Plannings publiés"
          value={publishedCount}
          icon={TableProperties}
          color="purple"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Charge des enseignants */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">Charge des enseignants</h3>
          {workloadData.length > 0 ? (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie data={workloadData} dataKey="count" nameKey="label" cx="50%" cy="50%" innerRadius={45} outerRadius={75} strokeWidth={2}>
                    {workloadData.map((entry) => (
                      <Cell key={entry.key} fill={WORKLOAD_COLORS[entry.key as keyof typeof WORKLOAD_COLORS]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <div className="space-y-1.5 mt-3">
                {workloadData.map((entry) => (
                  <div key={entry.key} className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded-full shrink-0" style={{ background: WORKLOAD_COLORS[entry.key as keyof typeof WORKLOAD_COLORS] }} />
                    <span className="text-xs text-brand-text dark:text-slate-200 truncate flex-1">{entry.label}</span>
                    <span className="text-xs font-medium text-brand-text dark:text-slate-200">{entry.count}</span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-12">Aucune donnée de charge horaire</p>
          )}
        </div>

        {/* Cartes domaines, à côté de la charge des enseignants */}
        {domainCards.map((card) => (
          <DomainCard key={card.key} card={card} onNavigate={navigate} />
        ))}
      </div>
    </div>
  )
}

interface DomainCardData {
  key: string
  icon: React.ElementType
  title: string
  to: string
  lines: { label: string; value: string | number }[]
}

function DomainCard({ card, onNavigate }: { card: DomainCardData; onNavigate: (to: string) => void }) {
  return (
    <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5 flex flex-col">
      <div className="flex items-center gap-2 mb-4">
        <card.icon size={16} className="text-brand-textMuted dark:text-slate-400" />
        <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">{card.title}</h3>
      </div>
      <div className="space-y-2 flex-1">
        {card.lines.map((l) => (
          <div key={l.label} className="flex justify-between items-center text-sm">
            <span className="text-brand-textMuted dark:text-slate-400">{l.label}</span>
            <span className="font-semibold text-brand-text dark:text-slate-100">{l.value}</span>
          </div>
        ))}
      </div>
      <button
        onClick={() => onNavigate(card.to)}
        className="mt-4 text-xs font-medium text-brand-blue hover:underline flex items-center gap-1 self-start"
      >
        Voir le détail <ArrowRight size={12} />
      </button>
    </div>
  )
}
