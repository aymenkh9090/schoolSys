import { useQuery } from '@tanstack/react-query'
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Cpu,
  Database,
  ExternalLink,
  Gauge,
  HelpCircle,
  MemoryStick,
  ShieldAlert,
  Timer,
  XCircle,
} from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { cn } from '@/lib/utils'
import { KpiCard, StatutCard } from './KpiCard'
import { aiAssistantApi, type HealthSnapshot } from '@/api/aiAssistant.api'
import AssistantChat from './AssistantChat'
import { PrevisionPanel } from './PrevisionPanel'

// Une heure : assez long pour qu'une dérive se voie, assez court pour que la
// vignette parle de maintenant. La légende du tracé la nomme, sans quoi une
// courbe sans échelle de temps ne veut rien dire.
const TENDANCE_FENETRE = '1h' as const
const TENDANCE_LIBELLE = '1 h'
const TENDANCE_MINUTES = 60

const GRAFANA_URL = import.meta.env.VITE_GRAFANA_URL ?? 'http://localhost:3001'
// Dashboard provisionné par fichier (monitoring/dashboards/jvm-micrometer.json),
// donc son uid est stable : pas d'import manuel à refaire après un down -v.
const GRAFANA_DASHBOARD = `${GRAFANA_URL}/d/smartschool-jvm/jvm`

export default function MonitoringPage() {
  const { data, isLoading, isError, isFetching } = useQuery({
    queryKey: ['ai', 'health'],
    queryFn: () => aiAssistantApi.getHealth('5m'),
    // 15 s : aligné sur le scrape_interval de Prometheus.
    // Plus fréquent serait inutile — il n'y aurait pas de nouvelle donnée.
    refetchInterval: 15_000,
  })

  // Requête distincte, et bien plus lente : une tendance sur une heure ne change
  // pas d'un quart de minute. La rafraîchir au rythme des chiffres ferait
  // réévaluer cinq requêtes de plage toutes les quinze secondes pour un tracé
  // identique. Son échec n'empêche rien : les tuiles s'affichent sans courbe.
  const { data: tendances } = useQuery({
    queryKey: ['ai', 'trends', TENDANCE_FENETRE],
    queryFn: () => aiAssistantApi.getTrends(TENDANCE_FENETRE),
    refetchInterval: 120_000,
  })

  // La prévision suit le rythme de la tendance, pour la même raison : une pente
  // calculée sur une heure ne change pas d'un quart de minute. Même fenêtre
  // aussi, pour que le pointillé prolonge la courbe qu'il continue. Son échec
  // n'empêche rien : les tuiles s'affichent sans ligne d'échéance.
  const { data: previsions, isError: previsionEnEchec } = useQuery({
    queryKey: ['ai', 'forecast', TENDANCE_FENETRE],
    queryFn: () => aiAssistantApi.getForecast(TENDANCE_FENETRE),
    refetchInterval: 120_000,
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
        // Rafraîchi toutes les 15 s. On garde le rendu précédent, simplement
        // atténué, plutôt que de repasser par un squelette : un écran qui
        // clignote quatre fois par minute devient impossible à surveiller.
        <div className={cn('space-y-6 transition-opacity', isFetching && 'opacity-60')}>
          <StatusBanner snapshot={data} />

          {/* L'assistant et la prévision en tête, juste sous le bandeau : c'est
              là que l'écran répond à « où va-t-on ? », les tuiles en dessous
              disent le détail de maintenant. Côte à côte sur grand écran, l'un
              répond à « quand la mémoire va-t-elle saturer ? », l'autre le
              montre ; empilés en dessous — un graphique gradué en heures ne
              tient pas dans une demi-largeur de portable. */}
          <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-2">
            <AssistantChat />
            <PrevisionPanel
              previsions={previsions?.forecasts}
              sante={data}
              indisponible={previsionEnEchec}
              fenetreMinutes={TENDANCE_MINUTES}
            />
          </div>

          {/* Les mesures qui ont une limite : chacune se lit face à la sienne.
              Les seuils reprennent ceux de THRESHOLDS côté Python — l'écran et
              le service doivent qualifier un incident de la même façon, sans
              quoi le bandeau dirait « critique » pendant qu'une tuile reste
              verte. */}
          <section className="space-y-3">
            <SectionTitre titre="Charge et performance" indication="Chaque mesure face à son seuil" />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <KpiCard
                label="Mémoire (heap)"
                tendance={tendances?.trends.memory}
                periode={TENDANCE_LIBELLE}
                periodeMinutes={TENDANCE_MINUTES}
                prevision={previsions?.forecasts.memory}
                // La heap brute est une dent de scie que le ramasse-miettes
                // redescend sans cesse : la régression porte sur son plancher.
                serieRegressee="le plancher de la heap (minimum sur 5 min, après ramasse-miettes)"
                valeur={data.heap_percent}
                unite=" %"
                icon={MemoryStick}
                seuils={{ alerte: 75, critique: 90 }}
                max={100}
                detail={
                  data.heap_used_mb !== null && data.heap_max_mb !== null
                    ? `${data.heap_used_mb.toFixed(0)} Mo sur ${data.heap_max_mb.toFixed(0)}`
                    : undefined
                }
              />
              <KpiCard
                label="CPU"
                tendance={tendances?.trends.cpu}
                periode={TENDANCE_LIBELLE}
                periodeMinutes={TENDANCE_MINUTES}
                prevision={previsions?.forecasts.cpu}
                serieRegressee="la charge CPU"
                valeur={data.cpu_percent}
                unite=" %"
                icon={Cpu}
                seuils={{ alerte: 70, critique: 85 }}
                max={100}
              />
              <KpiCard
                label="Latence p95"
                tendance={tendances?.trends.latency}
                periode={TENDANCE_LIBELLE}
                valeur={data.latency_p95_ms}
                unite=" ms"
                decimales={0}
                icon={Timer}
                seuils={{ alerte: 1000, critique: 3000 }}
                detail="95 % des requêtes sont plus rapides"
              />
              <KpiCard
                label="Taux d'erreur"
                tendance={tendances?.trends.errors}
                periode={TENDANCE_LIBELLE}
                valeur={data.error_rate_percent}
                unite=" %"
                decimales={2}
                icon={ShieldAlert}
                seuils={{ alerte: 1, critique: 5 }}
              />
            </div>
          </section>

          <section className="space-y-3">
            <SectionTitre titre="Disponibilité" indication="Ce qui répond, et ce qui attend" />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {/* Pas de jauge ici : un débit n'a pas de limite au-delà de laquelle
                  il irait mal. Une piste sans seuil n'aurait rien à montrer. */}
              <KpiCard
                label="Débit"
                tendance={tendances?.trends.throughput}
                periode={TENDANCE_LIBELLE}
                valeur={data.requests_per_second}
                unite=" req/s"
                decimales={2}
                icon={Activity}
              />
              <KpiCard
                label="Connexions en attente"
                valeur={data.db_connections_pending}
                decimales={0}
                icon={Database}
                seuils={{ alerte: 1, critique: 5 }}
                detail={
                  data.db_connections_active !== null
                    ? `${data.db_connections_active.toFixed(0)} connexions actives`
                    : undefined
                }
              />
              <StatutCard label="Application" statut={data.app_status} icon={Gauge} />
              <StatutCard label="Base de données" statut={data.db_status} icon={Database} />
            </div>
          </section>
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

  // Une icône dans les quatre cas, y compris celui qui va bien. Sans elle,
  // « tout fonctionne » ne se distinguait de « incident » que par la teinte du
  // bandeau — la même faiblesse que les tuiles avaient, et elle porte ici sur
  // la phrase la plus importante de l'écran.
  const Icone = {
    HEALTHY: CheckCircle2,
    WARNING: AlertTriangle,
    CRITICAL: XCircle,
    UNKNOWN: HelpCircle,
  }[snapshot.status]

  return (
    <div className={`flex flex-col gap-4 rounded-2xl border p-5 sm:flex-row sm:items-center ${style}`}>
      <span className="inline-flex shrink-0 items-center justify-center rounded-2xl bg-white/70 p-2.5 dark:bg-white/10">
        <Icone size={22} />
      </span>

      <div className="min-w-0 flex-1">
        <p className="text-base font-semibold leading-tight">{label}</p>
        {snapshot.issues.length === 0 ? (
          <p className="mt-0.5 text-sm opacity-80">Aucune mesure au-dessus de son seuil.</p>
        ) : (
          /* Les constats en pastilles plutôt qu'en liste à puces : ils sont courts,
             rarement plus de trois, et une puce par ligne étirait le bandeau sur toute
             la largeur pour trois mots. */
          <ul className="mt-1.5 flex flex-wrap gap-1.5">
            {snapshot.issues.map((issue) => (
              <li key={issue} className="rounded-full bg-white/70 px-2.5 py-0.5 text-xs font-medium dark:bg-white/10">
                {issue}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

/**
 * Un intitulé de rangée.
 *
 * Huit tuiles alignées sans rien pour les séparer se lisent comme une liste ; en
 * deux groupes nommés, elles se lisent comme un tableau de bord. Même contenu, lu
 * en deux temps au lieu d'un balayage.
 */
function SectionTitre({ titre, indication }: { titre: string; indication: string }) {
  return (
    <div className="flex items-baseline gap-2">
      <h2 className="text-sm font-semibold text-brand-text dark:text-slate-100">{titre}</h2>
      <span className="text-xs text-brand-textMuted dark:text-slate-500">{indication}</span>
    </div>
  )
}
