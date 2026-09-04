import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { Users, GraduationCap, School, BookOpen, BookMarked, ClipboardList, ArrowRight, Layers } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { organisationApi } from '@/api/organisation.api'
import { cn } from '@/lib/utils'

const LEVEL_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#f97316', '#ec4899']

export default function AcademiqueDashboard() {
  const navigate = useNavigate()

  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: organisationApi.subjects.list })
  const { data: classes = [] } = useQuery({ queryKey: ['classes', null], queryFn: () => organisationApi.classes.list() })
  const { data: teachers = [] } = useQuery({ queryKey: ['teachers'], queryFn: organisationApi.teachers.list })
  const { data: eleves = [] } = useQuery({ queryKey: ['eleves-all'], queryFn: organisationApi.eleves.list })
  const { data: assignments = [] } = useQuery({
    queryKey: ['teaching-assignments'],
    queryFn: () => organisationApi.teachingAssignments.list(),
  })
  const { data: workload = [] } = useQuery({ queryKey: ['teachers-workload'], queryFn: organisationApi.teachers.getWorkload })

  const activeClasses = classes.filter((c) => c.estActif)
  const activeLevels = levels.filter((l) => l.estActif)
  const activeTeachers = teachers.filter((t) => t.estEnPoste)
  const matieresEnseignees = subjects.filter((s) => s.estEnseignee)
  const elevesActifs = eleves.filter((e) => e.estActif)
  const affectationsActives = assignments.filter((a) => a.isActive)

  const classById = useMemo(() => new Map(classes.map((c) => [c.idClasse, c])), [classes])

  const levelDistribution = useMemo(() => {
    const counts = new Map<string, { nom: string; count: number }>()
    for (const e of elevesActifs) {
      const cls = classById.get(e.classeId)
      if (!cls) continue
      const key = cls.levelCode
      const entry = counts.get(key) ?? { nom: cls.levelNom, count: 0 }
      entry.count += 1
      counts.set(key, entry)
    }
    return Array.from(counts.entries()).map(([code, v], i) => ({
      code, nom: v.nom, count: v.count, couleur: LEVEL_COLORS[i % LEVEL_COLORS.length],
    }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elevesActifs, classById])

  const sectionDistribution = useMemo(() => {
    const counts = new Map<string, number>()
    for (const e of elevesActifs) {
      const cls = classById.get(e.classeId)
      if (!cls) continue
      counts.set(cls.codeSpecialite, (counts.get(cls.codeSpecialite) ?? 0) + 1)
    }
    return Array.from(counts.entries()).map(([section, count]) => ({ section, count }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elevesActifs, classById])

  const sectionsUtilisees = new Set(activeClasses.map((c) => c.codeSpecialite)).size

  const synthese = [
    { label: 'Niveaux', value: activeLevels.length, to: '/ecole/niveaux', icon: BookMarked },
    { label: 'Sections', value: sectionsUtilisees, to: '/ecole/classes', icon: Layers },
    { label: 'Classes', value: activeClasses.length, to: '/ecole/classes', icon: School },
    { label: 'Matières', value: matieresEnseignees.length, to: '/ecole/matieres', icon: BookOpen },
    { label: 'Enseignants', value: activeTeachers.length, to: '/ecole/enseignants', icon: GraduationCap },
    { label: 'Élèves', value: elevesActifs.length, to: '/ecole/eleves', icon: Users },
    { label: 'Affectations', value: affectationsActives.length, to: '/ecole/affectations', icon: ClipboardList },
  ]

  const topWorkload = [...workload]
    .filter((t) => t.totalHeures != null && t.totalHeures > 0)
    .sort((a, b) => (b.totalHeures ?? 0) - (a.totalHeures ?? 0))
    .slice(0, 5)
  const maxHeures = Math.max(1, ...topWorkload.map((t) => t.totalHeures ?? 0))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Académique"
        subtitle="Vue d'ensemble des données académiques de l'établissement"
      />

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatCard title="Élèves actifs" value={elevesActifs.length} icon={Users} color="blue" />
        <StatCard title="Enseignants" value={activeTeachers.length} icon={GraduationCap} color="green" />
        <StatCard title="Classes" value={activeClasses.length} icon={School} color="purple" />
        <StatCard title="Matières" value={matieresEnseignees.length} icon={BookOpen} color="amber" />
        <StatCard title="Niveaux" value={activeLevels.length} icon={BookMarked} color="yellow" />
        <StatCard title="Affectations" value={affectationsActives.length} icon={ClipboardList} color="red" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Répartition par niveau */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">Élèves par niveau</h3>
          {levelDistribution.length > 0 ? (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie data={levelDistribution} dataKey="count" nameKey="nom" cx="50%" cy="50%" innerRadius={45} outerRadius={75} strokeWidth={2}>
                    {levelDistribution.map((entry) => <Cell key={entry.code} fill={entry.couleur} />)}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <div className="space-y-1.5 mt-3">
                {levelDistribution.map((entry) => (
                  <div key={entry.code} className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded-full shrink-0" style={{ background: entry.couleur }} />
                    <span className="text-xs text-brand-text dark:text-slate-200 truncate flex-1">{entry.nom}</span>
                    <span className="text-xs font-medium text-brand-text dark:text-slate-200">{entry.count}</span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-12">Aucun élève actif</p>
          )}
        </div>

        {/* Répartition par section */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">Élèves par section</h3>
          {sectionDistribution.length > 0 ? (
            <ResponsiveContainer width="100%" height={230}>
              <BarChart data={sectionDistribution}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="currentColor" className="text-brand-border dark:text-slate-700" />
                <XAxis dataKey="section" tick={{ fontSize: 10 }} interval={0} angle={-20} textAnchor="end" height={50} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="count" fill="#3b82f6" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-12">Aucun élève actif</p>
          )}
        </div>

        {/* Synthèse académique */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">Synthèse académique</h3>
          <div className="divide-y divide-brand-border dark:divide-slate-700">
            {synthese.map((s) => (
              <button
                key={s.label}
                onClick={() => navigate(s.to)}
                className="w-full flex items-center justify-between py-2.5 group"
              >
                <span className="flex items-center gap-2 text-sm text-brand-text dark:text-slate-200">
                  <s.icon size={15} className="text-brand-textMuted dark:text-slate-400" />
                  {s.label}
                </span>
                <span className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-brand-text dark:text-slate-100">{s.value}</span>
                  <ArrowRight size={13} className="text-brand-textMuted dark:text-slate-400 group-hover:text-brand-blue transition-colors" />
                </span>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Charge d'enseignement */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Charge d'enseignement</h3>
          <button onClick={() => navigate('/ecole/enseignants')} className="text-xs font-medium text-brand-blue hover:underline">
            Voir tous les enseignants
          </button>
        </div>
        {topWorkload.length === 0 ? (
          <p className="text-sm text-brand-textMuted dark:text-slate-400 text-center py-8">Aucune charge horaire calculée</p>
        ) : (
          <div className="space-y-3">
            {topWorkload.map((t) => {
              const heures = t.totalHeures ?? 0
              const overloaded = t.maxHeuresSemaine != null && heures > t.maxHeuresSemaine
              return (
                <div key={t.idEnseignant} className="flex items-center gap-3">
                  <span className="w-40 shrink-0 text-sm text-brand-text dark:text-slate-200 truncate">{t.nomComplet}</span>
                  <div className="flex-1 h-2 rounded-full bg-brand-bgSecondary dark:bg-slate-800">
                    <div
                      className={cn('h-2 rounded-full', overloaded ? 'bg-red-500' : 'bg-brand-blue')}
                      style={{ width: `${Math.min(100, (heures / maxHeures) * 100)}%` }}
                    />
                  </div>
                  <span className="w-14 shrink-0 text-xs text-right font-medium text-brand-text dark:text-slate-200">{heures}h</span>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
