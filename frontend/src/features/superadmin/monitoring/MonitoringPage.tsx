import { useQuery } from '@tanstack/react-query'
import {
  Activity,
  AlertTriangle,
  Cpu,
  Database,
  ExternalLink,
  Gauge,
  MemoryStick,
  ShieldAlert,
  Timer,
} from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { StatCard } from '@/components/ui/StatCard'
import { aiAssistantApi, type HealthSnapshot } from '@/api/aiAssistant.api'
import AssistantChat from './AssistantChat'

const GRAFANA_URL = import.meta.env.VITE_GRAFANA_URL ?? 'http://localhost:3001'
// Dashboard provisionné par fichier (monitoring/dashboards/jvm-micrometer.json),
// donc son uid est stable : pas d'import manuel à refaire après un down -v.
const GRAFANA_DASHBOARD = `${GRAFANA_URL}/d/smartschool-jvm/jvm`

export default function MonitoringPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['ai', 'health'],
    queryFn: () => aiAssistantApi.getHealth('5m'),
    // 15 s : aligné sur le scrape_interval de Prometheus.
    // Plus fréquent serait inutile — il n'y aurait pas de nouvelle donnée.
    refetchInterval: 15_000,
  })

  return (
    <div>
      <PageHeader
        title="Supervision technique"
        description="État de la plateforme en temps réel — analyse locale, aucune donnée ne sort du serveur."
        actions={
          <a
            href={GRAFANA_DASHBOARD}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1.5 text-sm text-brand-blue hover:underline"
          >
            Graphiques détaillés (Grafana)
            <ExternalLink size={14} />
          </a>
        }
      />

      {isLoading && (
        <div className="rounded-xl border border-brand-border dark:border-slate-700 bg-white dark:bg-slate-900 p-6 text-sm text-brand-textMuted dark:text-slate-400">
          Lecture des métriques…
        </div>
      )}

      {isError && (
        <div className="rounded-xl border border-red-200 dark:border-red-500/30 bg-red-50 dark:bg-red-500/10 p-4 text-sm text-red-700 dark:text-red-300">
          Service de supervision injoignable. Vérifie que le service AI Assistant
          est démarré sur le port 8000.
        </div>
      )}

      {data && (
        <div className="space-y-6">
          <StatusBanner snapshot={data} />

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Mémoire (heap)"
              value={fmt(data.heap_percent, ' %')}
              icon={MemoryStick}
              color={colorFor(data.heap_percent, 75, 90)}
            />
            <StatCard
              title="CPU"
              value={fmt(data.cpu_percent, ' %')}
              icon={Cpu}
              color={colorFor(data.cpu_percent, 70, 85)}
            />
            <StatCard
              title="Latence p95"
              value={fmt(data.latency_p95_ms, ' ms', 0)}
              icon={Timer}
              color={colorFor(data.latency_p95_ms, 1000, 3000)}
            />
            <StatCard
              title="Taux d'erreur"
              value={fmt(data.error_rate_percent, ' %', 2)}
              icon={ShieldAlert}
              color={colorFor(data.error_rate_percent, 1, 5)}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Débit"
              value={fmt(data.requests_per_second, ' req/s', 2)}
              icon={Activity}
              color="blue"
            />
            <StatCard
              title="Application"
              value={data.app_status}
              icon={Gauge}
              color={data.app_status === 'UP' ? 'green' : 'red'}
            />
            <StatCard
              title="Base de données"
              value={data.db_status}
              icon={Database}
              color={data.db_status === 'UP' ? 'green' : 'red'}
            />
            <StatCard
              title="Connexions DB"
              value={`${fmt(data.db_connections_active, '', 0)} actives · ${fmt(
                data.db_connections_pending,
                '',
                0
              )} en attente`}
              icon={Database}
              color={colorFor(data.db_connections_pending, 1, 5)}
            />
          </div>

          <AssistantChat />
        </div>
      )}
    </div>
  )
}

function StatusBanner({ snapshot }: { snapshot: HealthSnapshot }) {
  const style = {
    HEALTHY: 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300',
    WARNING: 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300',
    CRITICAL: 'border-red-200 bg-red-50 text-red-800 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300',
    UNKNOWN: 'border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300',
  }[snapshot.status]

  const label = {
    HEALTHY: 'Tout fonctionne normalement',
    WARNING: 'Points de vigilance détectés',
    CRITICAL: 'Incident en cours',
    UNKNOWN: 'État indéterminé',
  }[snapshot.status]

  return (
    <div className={`rounded-xl border p-4 ${style}`}>
      <div className="flex items-center gap-2 font-semibold">
        {snapshot.status !== 'HEALTHY' && <AlertTriangle size={18} />}
        {label}
      </div>
      {snapshot.issues.length > 0 && (
        <ul className="mt-2 list-disc pl-5 text-sm space-y-0.5">
          {snapshot.issues.map((issue) => (
            <li key={issue}>{issue}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Affiche explicitement « n/d » quand la donnée manque.
 * Ne JAMAIS afficher 0 pour une valeur absente : ce serait un mensonge visuel.
 */
function fmt(value: number | null, unit = '', digits = 1): string {
  return value === null || value === undefined ? 'n/d' : `${value.toFixed(digits)}${unit}`
}

/** Même logique de seuils que THRESHOLDS côté Python, pour un affichage cohérent. */
function colorFor(value: number | null, warning: number, critical: number) {
  if (value === null || value === undefined) return 'blue' as const
  if (value >= critical) return 'red' as const
  if (value >= warning) return 'amber' as const
  return 'green' as const
}
