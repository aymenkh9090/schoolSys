import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  Activity,
  BarChart3,
  BookOpen,
  Building2,
  CalendarCheck,
  Download,
  GraduationCap,
  LayoutGrid,
  PieChart as PieChartIcon,
  Target,
  TrendingUp,
  Users,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { SectionTitle } from '@/components/ui/SectionTitle'
import { StatCard } from '@/components/ui/StatCard'
import { Button } from '@/components/ui/Button'
import { Tabs } from '@/components/ui/Tabs'
import { organisationApi } from '@/api/organisation.api'
import { absenceApi } from '@/api/absence.api'
import { planningApi } from '@/api/planning.api'

const PALETTE = ['#0d9488', '#3b82f6', '#f59e0b', '#8b5cf6', '#ef4444', '#06b6d4', '#f97316', '#10b981']

type TabKey = 'overview' | 'students' | 'attendance' | 'academic'

const TABS = [
  { key: 'overview' as const, label: "Vue d'ensemble", icon: Activity },
  { key: 'students' as const, label: 'Élèves', icon: GraduationCap },
  { key: 'attendance' as const, label: 'Présence', icon: CalendarCheck },
  { key: 'academic' as const, label: 'Académique', icon: BookOpen },
]

const PERIODES = [
  { value: 'SEMAINE', label: 'Cette semaine' },
  { value: 'MOIS', label: 'Ce mois' },
  { value: 'TRIMESTRE', label: 'Ce trimestre' },
  { value: 'ANNEE', label: "Cette année" },
]

const cardClass =
  'bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5'

export default function RapportsAnalyses() {
  const [tab, setTab] = useState<TabKey>('overview')
  const [periode, setPeriode] = useState('MOIS')

  const { data: eleves = [] } = useQuery({ queryKey: ['eleves-all'], queryFn: organisationApi.eleves.list })
  const { data: teachers = [] } = useQuery({ queryKey: ['teachers'], queryFn: organisationApi.teachers.list })
  const { data: workload = [] } = useQuery({ queryKey: ['teachers-workload'], queryFn: organisationApi.teachers.getWorkload })
  const { data: classes = [] } = useQuery({ queryKey: ['classes', null], queryFn: organisationApi.classes.list })
  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: organisationApi.subjects.list })
  const { data: rooms = [] } = useQuery({ queryKey: ['rooms'], queryFn: organisationApi.rooms.list })
  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: absenceStats } = useQuery({
    queryKey: ['statistiques-absences', periode],
    queryFn: () => absenceApi.statistiques.dashboard(periode),
  })
  const { data: generated = [] } = useQuery({
    queryKey: ['generated-timetables'],
    queryFn: () => planningApi.timetable.generated.list(),
  })

  const currentYear = years.find((y) => y.estCourante)
  const elevesActifs = eleves.filter((e) => e.estActif).length
  const enseignantsActifs = teachers.filter((t) => t.estEnPoste).length
  const classesActives = classes.filter((c) => c.estActif)
  const tauxPresence = absenceStats ? Math.max(0, 100 - absenceStats.tauxAbsenteisme) : null

  // ── Répartitions ───────────────────────────────────────────────────────────
  const elevesParClasse = classesActives
    .map((c, i) => ({
      name: c.code,
      value: eleves.filter((e) => e.classeId === c.idClasse && e.estActif).length,
      fill: PALETTE[i % PALETTE.length],
    }))
    .filter((d) => d.value > 0)
    .sort((a, b) => b.value - a.value)

  const elevesParNiveau = levels
    .map((l, i) => {
      const classIds = classesActives.filter((c) => c.levelCode === l.code).map((c) => c.idClasse)
      return {
        name: l.code,
        libelle: l.nom,
        classes: classIds.length,
        eleves: eleves.filter((e) => e.estActif && classIds.includes(e.classeId)).length,
        fill: PALETTE[i % PALETTE.length],
      }
    })
    .filter((d) => d.classes > 0)

  const remplissage = classesActives
    .map((c) => {
      const inscrits = eleves.filter((e) => e.classeId === c.idClasse && e.estActif).length
      return {
        name: c.code,
        inscrits,
        capacite: c.nbEleve,
        taux: c.nbEleve > 0 ? Math.round((inscrits / c.nbEleve) * 100) : 0,
      }
    })
    .sort((a, b) => b.taux - a.taux)
    .slice(0, 12)

  const presenceData = absenceStats
    ? [
        { name: 'Justifiées', value: absenceStats.totalJustifiees, fill: '#3b82f6' },
        { name: 'Non justifiées', value: absenceStats.totalNonJustifiees, fill: '#ef4444' },
        { name: 'Retards', value: absenceStats.totalRetards, fill: '#f59e0b' },
        { name: 'Exclusions', value: absenceStats.totalExclusions, fill: '#8b5cf6' },
      ].filter((d) => d.value > 0)
    : []

  // Charge horaire des enseignants (top 12 par taux d'occupation)
  const chargeData = workload
    .filter((t) => t.estEnPoste)
    .map((t) => ({
      name: t.nomComplet ?? `${t.prenom} ${t.nom}`,
      heures: t.totalHeures ?? 0,
      max: t.maxHeuresSemaine ?? 0,
      taux: t.maxHeuresSemaine ? Math.round(((t.totalHeures ?? 0) / t.maxHeuresSemaine) * 100) : 0,
    }))
    .sort((a, b) => b.taux - a.taux)
    .slice(0, 12)

  const matieresParNiveau = levels
    .map((l, i) => ({ name: l.code, value: l.nombreMatieres ?? 0, fill: PALETTE[i % PALETTE.length] }))
    .filter((d) => d.value > 0)

  const kpis = [
    { label: 'Taux de remplissage moyen', value: remplissage.length ? `${Math.round(remplissage.reduce((s, r) => s + r.taux, 0) / remplissage.length)}%` : '—' },
    { label: 'Élèves par classe (moyenne)', value: classesActives.length ? (elevesActifs / classesActives.length).toFixed(1) : '—' },
    { label: 'Élèves par enseignant', value: enseignantsActifs ? (elevesActifs / enseignantsActifs).toFixed(1) : '—' },
    { label: 'Emplois du temps publiés', value: generated.filter((g) => g.status === 'PUBLISHED').length },
    { label: 'Conflits détectés', value: generated.filter((g) => g.status !== 'ARCHIVED').reduce((s, g) => s + g.hardViolations + g.mediumViolations, 0) },
    { label: 'Salles disponibles', value: `${rooms.filter((r) => r.estDisponible !== false).length}/${rooms.length}` },
  ]

  return (
    <div className="space-y-6">
      <PageHero
        title="Rapports & Analyses"
        subtitle="Tableaux de bord et statistiques détaillées de votre établissement"
        icon={BarChart3}
        actions={
          <>
            <select
              value={periode}
              onChange={(e) => setPeriode(e.target.value)}
              className="rounded-lg border border-white/30 bg-white/15 px-3 py-2 text-sm text-white backdrop-blur-sm focus:outline-none focus:ring-2 focus:ring-white/50 [&>option]:text-brand-text"
            >
              {PERIODES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
            <Button variant="outline" onClick={() => window.print()} className="border-white/30 bg-white/15 text-white hover:bg-white/25 dark:bg-white/15 dark:text-white">
              <Download size={15} /> Exporter PDF
            </Button>
          </>
        }
      />

      {/* KPIs de tête */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard title="Total élèves" value={elevesActifs} icon={GraduationCap} color="blue" />
        <StatCard title="Taux de présence" value={tauxPresence != null ? `${tauxPresence.toFixed(1)}%` : '—'} icon={CalendarCheck} color="green" />
        <StatCard title="Enseignants en poste" value={enseignantsActifs} icon={Users} color="amber" />
        <StatCard title="Classes actives" value={classesActives.length} icon={LayoutGrid} color="purple" />
      </div>

      <Tabs tabs={TABS} value={tab} onChange={setTab} />

      {/* ── Vue d'ensemble ─────────────────────────────────────────────────── */}
      {tab === 'overview' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <div className={cardClass}>
              <SectionTitle title="Effectif par niveau" icon={TrendingUp} accent="teal" />
              {elevesParNiveau.length > 0 ? (
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={elevesParNiveau}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-brand-border dark:stroke-slate-700" />
                    <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                    <Tooltip formatter={(v) => [`${Number(v)} élève(s)`, 'Effectif']} />
                    <Bar dataKey="eleves" radius={[6, 6, 0, 0]}>
                      {elevesParNiveau.map((d) => (
                        <Cell key={d.name} fill={d.fill} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <EmptyChart />
              )}
            </div>

            <div className={cardClass}>
              <SectionTitle title="Répartition par classe" icon={PieChartIcon} accent="purple" />
              {elevesParClasse.length > 0 ? (
                <ResponsiveContainer width="100%" height={260}>
                  <PieChart>
                    <Pie data={elevesParClasse} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={55} outerRadius={95} strokeWidth={2}>
                      {elevesParClasse.map((d) => (
                        <Cell key={d.name} fill={d.fill} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <EmptyChart />
              )}
            </div>
          </div>

          <div className={cardClass}>
            <SectionTitle title="Indicateurs clés de performance" icon={Target} accent="amber" />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
              {kpis.map((k) => (
                <div key={k.label} className="rounded-xl border border-brand-border p-4 text-center dark:border-slate-700">
                  <p className="text-2xl font-bold text-brand-teal dark:text-teal-400">{k.value}</p>
                  <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">{k.label}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ── Élèves ─────────────────────────────────────────────────────────── */}
      {tab === 'students' && (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard title="Élèves actifs" value={elevesActifs} icon={GraduationCap} color="blue" />
            <StatCard title="Élèves inactifs" value={eleves.length - elevesActifs} icon={Users} color="red" />
            <StatCard title="Classes" value={classesActives.length} icon={LayoutGrid} color="purple" />
            <StatCard title="Niveaux couverts" value={elevesParNiveau.length} icon={Building2} color="green" />
          </div>

          <div className={cardClass}>
            <SectionTitle title="Taux de remplissage des classes" icon={LayoutGrid} accent="teal" />
            {remplissage.length > 0 ? (
              <ResponsiveContainer width="100%" height={Math.max(240, remplissage.length * 28)}>
                <BarChart data={remplissage} layout="vertical" margin={{ left: 12, right: 24 }}>
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} className="stroke-brand-border dark:stroke-slate-700" />
                  <XAxis type="number" tick={{ fontSize: 12 }} />
                  <YAxis type="category" dataKey="name" width={70} tick={{ fontSize: 12 }} />
                  <Tooltip formatter={(v) => [Number(v), 'Inscrits']} />
                  <Bar dataKey="inscrits" fill="#0d9488" radius={[0, 6, 6, 0]} name="inscrits" />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <EmptyChart />
            )}
          </div>

          <div className={cardClass}>
            <SectionTitle title="Détail par niveau" icon={Building2} accent="emerald" />
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-brand-border text-left text-xs uppercase text-brand-textMuted dark:border-slate-700 dark:text-slate-400">
                    <th className="py-2">Niveau</th>
                    <th className="py-2">Classes</th>
                    <th className="py-2">Élèves</th>
                    <th className="py-2">Moyenne / classe</th>
                  </tr>
                </thead>
                <tbody>
                  {elevesParNiveau.map((l) => (
                    <tr key={l.name} className="border-b border-brand-border/60 dark:border-slate-800">
                      <td className="py-2 font-medium text-brand-text dark:text-slate-100">{l.libelle}</td>
                      <td className="py-2 text-brand-textMuted dark:text-slate-400">{l.classes}</td>
                      <td className="py-2 text-brand-textMuted dark:text-slate-400">{l.eleves}</td>
                      <td className="py-2 text-brand-textMuted dark:text-slate-400">
                        {l.classes ? (l.eleves / l.classes).toFixed(1) : '—'}
                      </td>
                    </tr>
                  ))}
                  {elevesParNiveau.length === 0 && (
                    <tr>
                      <td colSpan={4} className="py-8 text-center text-brand-textMuted dark:text-slate-400">
                        Aucune donnée
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ── Présence ───────────────────────────────────────────────────────── */}
      {tab === 'attendance' && (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard title="Absences (période)" value={absenceStats?.totalAbsences ?? '—'} icon={CalendarCheck} color="red" />
            <StatCard title="Justifiées" value={absenceStats?.totalJustifiees ?? '—'} icon={CalendarCheck} color="blue" />
            <StatCard title="Retards" value={absenceStats?.totalRetards ?? '—'} icon={CalendarCheck} color="amber" />
            <StatCard
              title="Taux d'absentéisme"
              value={absenceStats ? `${absenceStats.tauxAbsenteisme.toFixed(1)}%` : '—'}
              icon={TrendingUp}
              color="purple"
            />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <div className={cardClass}>
              <SectionTitle title="Répartition des absences élèves" icon={PieChartIcon} accent="red" />
              {presenceData.length > 0 ? (
                <ResponsiveContainer width="100%" height={260}>
                  <PieChart>
                    <Pie data={presenceData} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={55} outerRadius={95} strokeWidth={2}>
                      {presenceData.map((d) => (
                        <Cell key={d.name} fill={d.fill} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <EmptyChart />
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── Académique ─────────────────────────────────────────────────────── */}
      {tab === 'academic' && (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard title="Matières enseignées" value={subjects.filter((s) => s.estEnseignee).length} icon={BookOpen} color="blue" />
            <StatCard title="Enseignants en poste" value={enseignantsActifs} icon={Users} color="green" />
            <StatCard title="Salles" value={rooms.length} icon={Building2} color="purple" />
            <StatCard
              title="Année en cours"
              value={currentYear?.nom ?? '—'}
              icon={CalendarCheck}
              color="amber"
            />
          </div>

          <div className={cardClass}>
            <SectionTitle title="Charge horaire des enseignants" icon={Activity} accent="blue" />
            {chargeData.length > 0 ? (
              <ResponsiveContainer width="100%" height={Math.max(260, chargeData.length * 30)}>
                <BarChart data={chargeData} layout="vertical" margin={{ left: 12, right: 24 }}>
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} className="stroke-brand-border dark:stroke-slate-700" />
                  <XAxis type="number" tick={{ fontSize: 12 }} />
                  <YAxis type="category" dataKey="name" width={150} tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(v, n) => [Number(v), n === 'heures' ? 'Heures affectées' : 'Maximum']} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="heures" name="Heures affectées" fill="#3b82f6" radius={[0, 6, 6, 0]} />
                  <Bar dataKey="max" name="Maximum hebdo" fill="#cbd5e1" radius={[0, 6, 6, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <EmptyChart />
            )}
          </div>

          <div className={cardClass}>
            <SectionTitle title="Matières par niveau" icon={BookOpen} accent="emerald" />
            {matieresParNiveau.length > 0 ? (
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={matieresParNiveau}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-brand-border dark:stroke-slate-700" />
                  <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                  <Tooltip formatter={(v) => [`${Number(v)} matière(s)`, 'Programme']} />
                  <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                    {matieresParNiveau.map((d) => (
                      <Cell key={d.name} fill={d.fill} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <EmptyChart />
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function EmptyChart({ label = 'Aucune donnée disponible' }: { label?: string }) {
  return (
    <p className="py-16 text-center text-sm text-brand-textMuted dark:text-slate-400">{label}</p>
  )
}
