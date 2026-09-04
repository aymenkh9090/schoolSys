import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts'
import { Users, AlertTriangle, CheckCircle, TrendingDown } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { absenceApi } from '@/api/absence.api'

type Periode = 'JOUR' | 'SEMAINE' | 'MOIS' | 'ANNEE'

const PERIODES: { value: Periode; label: string }[] = [
  { value: 'JOUR', label: 'Aujourd\'hui' },
  { value: 'SEMAINE', label: 'Cette semaine' },
  { value: 'MOIS', label: 'Ce mois' },
  { value: 'ANNEE', label: 'Cette année' },
]

export default function StatistiquesAbsences() {
  const [periode, setPeriode] = useState<Periode>('MOIS')

  const { data, isLoading } = useQuery({
    queryKey: ['statistiques-absences', periode],
    queryFn: () => absenceApi.statistiques.dashboard(periode),
  })

  const tauxPresence =
    data?.tauxAbsenteisme != null ? 100 - data.tauxAbsenteisme : undefined

  const chartData = data
    ? [
        {
          name: data.periode || periode,
          absences: data.totalAbsences,
          retards: data.totalRetards,
          justifiees: data.totalJustifiees,
          nonJustifiees: data.totalNonJustifiees,
          exclusions: data.totalExclusions,
        },
      ]
    : []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Statistiques des absences"
        subtitle="Vue d'ensemble des présences et absences"
        actions={
          <div className="flex gap-1 bg-brand-bgSecondary p-1 rounded-lg">
            {PERIODES.map((p) => (
              <button
                key={p.value}
                onClick={() => setPeriode(p.value)}
                className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
                  periode === p.value
                    ? 'bg-white shadow-sm font-semibold text-brand-blue'
                    : 'text-brand-textMuted hover:text-brand-text'
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        }
      />

      {isLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-24 bg-brand-bgSecondary rounded-xl animate-pulse" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <StatCard title="Total séances" value={data?.totalSeances ?? 0} icon={Users} color="blue" />
          <StatCard title="Total absences" value={data?.totalAbsences ?? 0} icon={AlertTriangle} color="red" />
          <StatCard title="Justifiées" value={data?.totalJustifiees ?? 0} icon={CheckCircle} color="green" />
          <StatCard
            title="Taux présence"
            value={`${tauxPresence?.toFixed(1) ?? '—'}%`}
            icon={TrendingDown}
            color="purple"
          />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Répartition des absences */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-brand-border p-5">
          <h3 className="text-sm font-semibold text-brand-text mb-4">
            Répartition sur la période
          </h3>
          {chartData.length > 0 && (data?.totalAbsences ?? 0) > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={chartData} margin={{ top: 0, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Legend iconSize={10} wrapperStyle={{ fontSize: 11 }} />
                <Bar dataKey="absences" name="Absences" fill="#ef4444" radius={[4, 4, 0, 0]} />
                <Bar dataKey="retards" name="Retards" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                <Bar dataKey="justifiees" name="Justifiées" fill="#22c55e" radius={[4, 4, 0, 0]} />
                <Bar dataKey="nonJustifiees" name="Non justifiées" fill="#6366f1" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-64 flex items-center justify-center text-sm text-brand-textMuted">
              Aucune donnée pour cette période
            </div>
          )}
        </div>

        {/* Détail période */}
        <div className="bg-white rounded-xl border border-brand-border p-5">
          <h3 className="text-sm font-semibold text-brand-text mb-4">Détails</h3>
          {data ? (
            <div className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-brand-textMuted">Période</span>
                <span className="font-medium">{data.debut} → {data.fin}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brand-textMuted">Non justifiées</span>
                <span className="font-medium text-red-600">{data.totalNonJustifiees}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brand-textMuted">Retards</span>
                <span className="font-medium text-amber-600">{data.totalRetards}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brand-textMuted">Exclusions</span>
                <span className="font-medium">{data.totalExclusions}</span>
              </div>
              <div className="flex justify-between border-t border-brand-border pt-3">
                <span className="text-brand-textMuted">Taux d'absentéisme</span>
                <span className="font-bold">{data.tauxAbsenteisme?.toFixed(1)}%</span>
              </div>
            </div>
          ) : (
            <p className="text-sm text-brand-textMuted text-center mt-8">Aucune donnée</p>
          )}
        </div>
      </div>
    </div>
  )
}
