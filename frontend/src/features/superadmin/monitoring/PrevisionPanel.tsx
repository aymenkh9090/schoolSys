import { useState } from 'react'
import {
  AlertTriangle, ArrowRight, CheckCircle2, CircleDashed, Cpu, Database, HardDrive, HelpCircle,
  MemoryStick, Monitor, Server, TrendingDown, TrendingUp, XCircle,
} from 'lucide-react'
import {
  CartesianGrid, ComposedChart, Line, ReferenceArea, ReferenceDot, ReferenceLine, ResponsiveContainer,
  Tooltip, XAxis, YAxis, type TooltipContentProps,
} from 'recharts'

import type {
  CleRessource, HealthSnapshot, ResourceForecast, ResourceForecasts,
} from '@/api/aiAssistant.api'
import { cn } from '@/lib/utils'
import { severite, type Severite } from './KpiCard'
import {
  formatDuree, prochainSeuil, recommandation, type Recommandation, type TonPrevision,
} from './prevision'

/**
 * Prévision de la saturation des ressources — la régression, mise à plat.
 *
 * <h4>Ce que ce panneau ajoute aux tuiles</h4>
 *
 * La tuile dit en une ligne « Alerte dans ~45 min » et prolonge sa vignette d'un
 * pointillé. Ici, la même prévision se lit en entier : la série régressée sur un
 * vrai axe du temps, la droite prolongée jusqu'à l'horizon, les deux seuils, et
 * le point où la droite les croise — posé à l'heure même que le texte annonce.
 *
 * <h4>Aucun calcul ici</h4>
 *
 * Verdict, pente, R², échéances : tout vient de `/api/monitoring/forecast`. Le
 * panneau trace et met des mots, comme la tuile. La recommandation elle-même est
 * déduite des verdicts par `prevision.ts`, jamais rédigée par le modèle.
 *
 * <h4>Ce qui n'est pas soutenu ne se dessine pas</h4>
 *
 * Sous R² 0,5, le service ne rend pas `at_horizon` : pas de droite prolongée,
 * pas de durée, et la légende dit pourquoi. La moitié droite du graphique reste
 * vide — c'est la forme honnête de « on ne sait pas ».
 */

type Cle = CleRessource

interface Ressource {
  libelle: string
  /** En milieu de phrase, pour la recommandation. */
  nom: string
  actuel: string
  Icone: React.ElementType
  titreGraphe: string
  serie: string
  /** Quand la série tracée n'est pas le chiffre de la tuile, on le dit. */
  note?: string
  /**
   * La valeur actuelle, lue dans l'instantané quand une tuile l'affiche — le
   * panneau et la tuile doivent dire le même chiffre. Absente pour les
   * ressources sans tuile : on prend alors le dernier point de la série.
   */
  valeur?: (s: HealthSnapshot) => number | null
}

const RESSOURCES: Record<Cle, Ressource> = {
  cpu: {
    libelle: 'CPU (API)',
    nom: 'la charge CPU',
    actuel: 'CPU actuel',
    Icone: Cpu,
    titreGraphe: 'Évolution de la charge CPU',
    serie: 'Charge mesurée',
    valeur: (s) => s.cpu_percent,
  },
  memory: {
    libelle: 'Mémoire (heap)',
    nom: 'la mémoire',
    actuel: 'Mémoire actuelle',
    Icone: MemoryStick,
    titreGraphe: 'Évolution du plancher de la heap',
    serie: 'Plancher de la heap',
    note:
      "Minimum sur 5 min, après ramasse-miettes — c'est lui qui monte lors d'une fuite. " +
      'La tuile, elle, affiche la heap brute : ses pics dépassent ce tracé.',
    valeur: (s) => s.heap_percent,
  },
  system_cpu: {
    libelle: 'CPU (machine)',
    nom: 'la charge CPU de la machine',
    actuel: 'CPU machine',
    Icone: Monitor,
    titreGraphe: 'Évolution de la charge CPU de la machine',
    serie: 'Charge mesurée',
    note: "Tout le serveur, et non la seule API : Ollama, PostgreSQL et Keycloak comptent aussi.",
  },
  disk: {
    libelle: 'Disque',
    nom: 'le disque',
    actuel: 'Disque occupé',
    Icone: HardDrive,
    titreGraphe: "Évolution de l'occupation du disque",
    serie: 'Espace occupé',
    note: "Le volume où tourne l'API. Plein, il arrête tout — la base comprise.",
  },
  db_pool: {
    libelle: 'Pool de connexions',
    nom: 'le pool de connexions',
    actuel: 'Pool occupé',
    Icone: Database,
    titreGraphe: 'Évolution du pool de connexions à la base',
    serie: 'Connexions actives',
    note: 'Connexions actives sur la taille maximale du pool.',
  },
}

/** L'ordre d'affichage : les deux ressources des tuiles d'abord. */
const ORDRE: Cle[] = ['cpu', 'memory', 'system_cpu', 'disk', 'db_pool']

const NOMS = Object.fromEntries(ORDRE.map((c) => [c, RESSOURCES[c].nom])) as Record<Cle, string>

function valeurActuelle(cle: Cle, sante: HealthSnapshot, p?: ResourceForecast): number | null {
  const lire = RESSOURCES[cle].valeur
  if (lire) return lire(sante)
  return p && p.history.length > 0 ? p.history[p.history.length - 1][1] : null
}

/**
 * Couleurs du tracé, en valeurs et non en classes : recharts les pose en
 * attributs SVG. Choisies pour se lire sur fond clair comme sur fond sombre.
 */
const COULEUR = {
  mesure: '#10b981',
  alerte: '#f59e0b',
  critique: '#ef4444',
  neutre: '#94a3b8',
}

/** La projection prend la couleur du seuil annoncé, comme le pointillé des tuiles. */
const COULEUR_PROJECTION: Record<TonPrevision, string> = {
  neutre: COULEUR.neutre,
  alerte: COULEUR.alerte,
  critique: COULEUR.critique,
}

const TEXTE_TON: Record<TonPrevision | 'inconnu', string> = {
  neutre: 'text-brand-textMuted dark:text-slate-400',
  inconnu: 'text-brand-textMuted dark:text-slate-400',
  alerte: 'text-amber-600 dark:text-amber-400',
  critique: 'text-red-600 dark:text-red-400',
}

/** « 5,0 » — la virgule décimale, comme dans l'infobulle des tuiles. */
function nombre(v: number, decimales = 1): string {
  return v.toFixed(decimales).replace('.', ',')
}

interface Props {
  previsions?: ResourceForecasts['forecasts']
  sante: HealthSnapshot
  /** La requête de prévision a échoué : le panneau le dit plutôt que de disparaître. */
  indisponible?: boolean
  /** La fenêtre demandée à `/forecast`, en minutes : l'étendue du passé sur l'axe. */
  fenetreMinutes?: number
}

export function PrevisionPanel({ previsions, sante, indisponible, fenetreMinutes = 60 }: Props) {
  // Tant que l'utilisateur n'a rien choisi, le panneau montre la ressource la
  // plus pressante : c'est elle qu'il serait venu chercher.
  const [choix, setChoix] = useState<Cle | null>(null)
  const cle = choix ?? plusPressante(previsions)
  const prevision = previsions?.[cle]
  const ressource = RESSOURCES[cle]

  return (
    <section className="overflow-hidden rounded-2xl border border-brand-border bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <header className="flex items-center gap-3 border-b border-brand-border px-5 py-4 dark:border-slate-700">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400">
          <Server size={22} />
        </span>
        <div className="min-w-0">
          <h2 className="text-[15px] font-semibold leading-tight text-brand-text dark:text-slate-100">
            Prévision de la saturation des ressources
          </h2>
          <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
            Analyse des métriques système (Prometheus)
          </p>
        </div>
      </header>

      {!previsions ? (
        <p className="p-5 text-sm text-brand-textMuted dark:text-slate-400">
          {indisponible
            ? "Prévision indisponible : le service d'observabilité n'a pas répondu."
            : "Lecture de l'historique…"}
        </p>
      ) : (
        <div className="space-y-5 p-5">
          {prevision && (
            <>
              <Synthese prevision={prevision} ressource={ressource} valeur={valeurActuelle(cle, sante, prevision)} />
              <Graphe prevision={prevision} ressource={ressource} fenetreMinutes={fenetreMinutes} />
            </>
          )}
          <Details previsions={previsions} sante={sante} choisie={cle} onChoisir={setChoix} />
          <BlocRecommandation reco={recommandation(previsions, NOMS)} />
        </div>
      )}
    </section>
  )
}

/** La première échéance annoncée, incidents d'abord ; le CPU de l'API sinon. */
function plusPressante(previsions?: ResourceForecasts['forecasts']): Cle {
  const rang = (p?: ResourceForecast) => {
    const s = p ? prochainSeuil(p) : undefined
    return !s ? Infinity : s.ton === 'critique' ? s.minutes : 100_000 + s.minutes
  }
  return ORDRE.reduce((meilleure, cle) => (rang(previsions?.[cle]) < rang(previsions?.[meilleure]) ? cle : meilleure), 'cpu')
}

// ── Synthèse : où l'on est, où l'on va, quand ────────────────────────────────

/**
 * Le fond de la synthèse suit ce qu'on sait : vert quand aucun seuil n'est à
 * portée, gris quand on ne peut pas le dire — un fond vert sur « non
 * estimable » rassurerait sans raison.
 */
const TON_SYNTHESE: Record<TonPrevision | 'inconnu', string> = {
  neutre: 'bg-emerald-50/60 dark:bg-emerald-500/[0.06]',
  inconnu: 'bg-slate-50 dark:bg-slate-800/40',
  alerte: 'bg-amber-50 dark:bg-amber-500/[0.08]',
  critique: 'bg-red-50 dark:bg-red-500/[0.08]',
}

function tonSynthese(p: ResourceForecast): TonPrevision | 'inconnu' {
  const seuil = prochainSeuil(p)
  if (seuil) return seuil.ton
  if (p.verdict === 'hausse') return p.minutes_to_critical === 0 ? 'critique' : 'alerte'
  return p.verdict === 'incertain' || p.verdict === 'insuffisant' ? 'inconnu' : 'neutre'
}

function Synthese({
  prevision, ressource, valeur,
}: {
  prevision: ResourceForecast
  ressource: Ressource
  valeur: number | null
}) {
  const seuils = { alerte: prevision.warning_threshold, critique: prevision.critical_threshold }

  return (
    <div
      className={cn(
        'flex flex-col gap-4 rounded-xl p-4 sm:flex-row sm:items-center',
        TON_SYNTHESE[tonSynthese(prevision)]
      )}
    >
      <Jauge valeur={valeur} seuils={seuils} legende={ressource.actuel} />

      <div className="grid flex-1 grid-cols-2 divide-x divide-black/5 dark:divide-white/10">
        <div className="pe-3">
          <p className="text-xs text-brand-textMuted dark:text-slate-400">Tendance</p>
          <Tendance prevision={prevision} />
        </div>
        <div className="ps-4">
          <p className="text-xs text-brand-textMuted dark:text-slate-400">Prévision de dépassement</p>
          <Depassement prevision={prevision} />
        </div>
      </div>
    </div>
  )
}

const JAUGE_TON: Record<Severite, { arc: string; piste: string }> = {
  normal: { arc: 'text-emerald-500', piste: 'text-emerald-100 dark:text-emerald-500/20' },
  alerte: { arc: 'text-amber-500', piste: 'text-amber-100 dark:text-amber-500/20' },
  critique: { arc: 'text-red-500', piste: 'text-red-100 dark:text-red-500/20' },
  inconnu: { arc: 'text-slate-400', piste: 'text-slate-200 dark:text-slate-700' },
}

/**
 * Un arc de 270°, ouvert en bas, face à ses deux seuils.
 *
 * Les seuils y sont des encoches, comme sur la piste des tuiles : la jauge dit
 * alors la marge qui reste, pas seulement le niveau.
 */
function Jauge({
  valeur, seuils, legende,
}: {
  valeur: number | null
  seuils: { alerte: number; critique: number }
  legende: string
}) {
  const R = 44
  const C = 56
  const OUVERTURE = 270
  const longueur = (2 * Math.PI * R * OUVERTURE) / 360
  const point = (fraction: number, r = R) => {
    const angle = ((135 + OUVERTURE * fraction) * Math.PI) / 180
    return [C + r * Math.cos(angle), C + r * Math.sin(angle)]
  }
  const [x0, y0] = point(0)
  const [x1, y1] = point(1)
  const arc = `M ${x0} ${y0} A ${R} ${R} 0 1 1 ${x1} ${y1}`

  const ton = JAUGE_TON[severite(valeur, seuils)]
  const part = valeur === null ? 0 : Math.min(1, Math.max(0, valeur / 100))

  return (
    <div className="relative mx-auto size-[7.5rem] shrink-0 sm:mx-0" role="img" aria-label={`${legende} : ${valeur === null ? 'n/d' : `${Math.round(valeur)} %`}`}>
      <svg viewBox="0 0 112 112" className="size-full" aria-hidden>
        <path d={arc} fill="none" stroke="currentColor" strokeWidth={9} strokeLinecap="round" className={ton.piste} />
        <path
          d={arc}
          fill="none"
          stroke="currentColor"
          strokeWidth={9}
          strokeLinecap="round"
          strokeDasharray={`${longueur * part} ${longueur}`}
          className={cn('transition-[stroke-dasharray] duration-700 ease-out', ton.arc)}
          // Un arc vide garderait sinon la pastille ronde de son extrémité.
          opacity={part === 0 ? 0 : 1}
        />
        {[seuils.alerte, seuils.critique].map((s) => {
          const [xa, ya] = point(s / 100, R - 6)
          const [xb, yb] = point(s / 100, R + 6)
          return (
            <line
              key={s}
              x1={xa} y1={ya} x2={xb} y2={yb}
              strokeWidth={2}
              className="stroke-white dark:stroke-slate-900"
            />
          )
        })}
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center pb-1">
        <span className="text-2xl font-semibold leading-none text-brand-text dark:text-slate-100">
          {valeur === null ? 'n/d' : Math.round(valeur)}
          {valeur !== null && <span className="ms-0.5 text-sm font-medium">%</span>}
        </span>
        <span className="mt-1 max-w-[4.5rem] text-center text-[11px] leading-tight text-brand-textMuted dark:text-slate-400">
          {legende}
        </span>
      </div>
    </div>
  )
}

const SENS: Record<ResourceForecast['verdict'], { mot: string; Icone: React.ElementType }> = {
  hausse: { mot: 'En hausse', Icone: TrendingUp },
  stable: { mot: 'Stable', Icone: ArrowRight },
  baisse: { mot: 'En baisse', Icone: TrendingDown },
  incertain: { mot: 'Incertaine', Icone: CircleDashed },
  insuffisant: { mot: 'Inconnue', Icone: CircleDashed },
}

function Tendance({ prevision }: { prevision: ResourceForecast }) {
  const { mot, Icone } = SENS[prevision.verdict]
  const ton = prochainSeuil(prevision)?.ton ?? (prevision.verdict === 'hausse' ? 'alerte' : 'neutre')
  // La pente ne se cite que lorsque la droite explique la série — sa présence
  // est ce que `at_horizon` signale, comme pour le pointillé.
  const pente = prevision.at_horizon !== null ? prevision.slope_per_hour : null

  return (
    <>
      <p className={cn('mt-1 flex items-center gap-1 text-base font-semibold', ton === 'neutre' ? 'text-brand-text dark:text-slate-200' : TEXTE_TON[ton])}>
        <Icone size={16} className="shrink-0" aria-hidden />
        {mot}
      </p>
      <p className="mt-1 text-[11px] leading-snug text-brand-textMuted dark:text-slate-500">
        {pente !== null && prevision.r2 !== null
          ? `${pente >= 0 ? '+' : '−'}${nombre(Math.abs(pente))} %/h · R² ${nombre(prevision.r2, 2)}`
          : prevision.verdict === 'insuffisant'
            ? 'Historique trop court'
            : 'Aucune droite fiable'}
      </p>
    </>
  )
}

function Depassement({ prevision }: { prevision: ResourceForecast }) {
  const seuil = prochainSeuil(prevision)
  const horizon = prevision.horizon_minutes ? formatDuree(prevision.horizon_minutes) : '1 h'

  if (seuil) {
    return (
      <p className={cn('mt-1 font-semibold leading-tight', TEXTE_TON[seuil.ton])}>
        <span className="text-lg">&gt; {seuil.seuil} %</span>{' '}
        <span className="text-xs font-medium">dans</span>
        <span className="block text-xl">~{formatDuree(seuil.minutes)}</span>
        <span className="block text-[11px] font-normal text-brand-textMuted dark:text-slate-500">
          seuil {seuil.ton === 'alerte' ? "d'alerte" : "d'incident"}
        </span>
      </p>
    )
  }

  const [titre, detail] =
    prevision.verdict === 'hausse' && prevision.minutes_to_critical === 0
      ? [`Au-delà de ${prevision.critical_threshold} %`, "seuil d'incident franchi"]
      : prevision.verdict === 'hausse'
        ? ["Pas d'incident", `prévu sur ${horizon}`]
        : prevision.verdict === 'incertain'
          ? ['Non estimable', 'tendance incertaine']
          : prevision.verdict === 'insuffisant'
            ? ['Non estimable', 'historique insuffisant']
            : ['Aucun seuil', `atteint sur ${horizon}`]

  return (
    <p className="mt-1 leading-tight">
      <span className={cn('block text-base font-semibold', prevision.minutes_to_critical === 0 && prevision.verdict === 'hausse' ? TEXTE_TON.critique : 'text-brand-text dark:text-slate-200')}>
        {titre}
      </span>
      <span className="mt-1 block text-[11px] text-brand-textMuted dark:text-slate-500">{detail}</span>
    </p>
  )
}

// ── Le graphique : la série régressée, et la droite prolongée ────────────────

interface Ligne {
  t: number
  mesure?: number | null
  projection?: number
}

interface DonneesGraphe {
  lignes: Ligne[]
  debut: number
  maintenant: number
  fin: number
  dernier: number
  projetee: boolean
}

function donneesGraphe(p: ResourceForecast, fenetreMinutes: number): DonneesGraphe | null {
  const h = p.history
  if (h.length < 2) return null

  // Un redémarrage laisse un trou dans la série. Une ligne tirée par-dessus
  // raconterait une mesure qui n'a pas eu lieu : on la coupe là où deux points
  // sont bien plus espacés que le pas habituel.
  const ecarts = h.slice(1).map(([t], i) => t - h[i][0]).sort((a, b) => a - b)
  const pas = ecarts[Math.floor(ecarts.length / 2)]
  const lignes: Ligne[] = []
  h.forEach(([t, v], i) => {
    if (i > 0 && t - h[i - 1][0] > 2.5 * pas) lignes.push({ t: (t + h[i - 1][0]) / 2, mesure: null })
    lignes.push({ t, mesure: v })
  })

  const [maintenant, dernier] = h[h.length - 1]
  // L'axe couvre toujours la fenêtre entière, passé comme avenir, quelle que
  // soit la part d'historique réellement disponible : après un redémarrage, la
  // gauche du graphique reste vide, et c'est ainsi qu'on voit qu'il manque.
  const debut = Math.min(h[0][0], maintenant - fenetreMinutes * 60)
  const horizonS = (p.horizon_minutes ?? fenetreMinutes) * 60
  const projetee = p.at_horizon !== null && p.current !== null && p.horizon_minutes !== null

  // La droite part de SA valeur à l'instant présent, pas du dernier point
  // mesuré (à l'inverse de la vignette des tuiles, qui reporte une variation).
  // Ici les seuils sont tracés : c'est la droite elle-même qui doit les croiser
  // à l'heure que le texte annonce, sans quoi l'œil lirait une autre échéance.
  // Une ressource ne descend pas sous 0 % ni ne dépasse 100 %, quelle que soit
  // la pente : un disque plein ne se remplit pas davantage.
  const borner = (v: number) => Math.min(100, Math.max(0, v))
  if (projetee) {
    lignes[lignes.length - 1].projection = borner(p.current!)
    lignes.push({ t: maintenant + horizonS, projection: borner(p.at_horizon!) })
  }

  return { lignes, debut, maintenant, fin: maintenant + horizonS, dernier, projetee }
}

/** Des graduations d'horloge — 11:00, 11:30 — plutôt que des instants quelconques. */
function graduations(debut: number, fin: number): number[] {
  const minutes = (fin - debut) / 60
  const pas = (minutes <= 60 ? 15 : minutes <= 150 ? 30 : 60) * 60
  const ticks: number[] = []
  for (let t = Math.ceil(debut / pas) * pas; t <= fin; t += pas) ticks.push(t)
  return ticks
}

const heure = (t: number) =>
  new Date(t * 1000).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })

function Graphe({
  prevision, ressource, fenetreMinutes,
}: {
  prevision: ResourceForecast
  ressource: Ressource
  fenetreMinutes: number
}) {
  const donnees = donneesGraphe(prevision, fenetreMinutes)
  const seuil = prochainSeuil(prevision)
  const tonProjection: TonPrevision = seuil?.ton ?? 'neutre'
  const couleurProjection = COULEUR_PROJECTION[tonProjection]

  return (
    <div>
      <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">
        {ressource.titreGraphe} <span className="font-normal text-brand-textMuted dark:text-slate-400">(prévision)</span>
      </h3>
      {ressource.note && (
        <p className="mt-0.5 text-[11px] leading-snug text-brand-textMuted dark:text-slate-500">{ressource.note}</p>
      )}

      {!donnees ? (
        <div className="mt-3 flex h-52 items-center justify-center rounded-xl border border-dashed border-brand-border text-sm text-brand-textMuted dark:border-slate-700 dark:text-slate-400">
          Pas encore d'historique à tracer.
        </div>
      ) : (
        <div className="mt-3 h-56 text-brand-textMuted dark:text-slate-500">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={donnees.lignes} margin={{ top: 8, right: 12, bottom: 0, left: -8 }}>
              <CartesianGrid stroke="currentColor" strokeOpacity={0.15} vertical={false} />
              {/* Le futur sur fond légèrement grisé : le passé est mesuré, le
                  reste n'est qu'une droite prolongée. */}
              <ReferenceArea
                x1={donnees.maintenant}
                x2={donnees.fin}
                fill="currentColor"
                fillOpacity={0.06}
                strokeOpacity={0}
                ifOverflow="hidden"
              />
              <XAxis
                dataKey="t"
                type="number"
                domain={[donnees.debut, donnees.fin]}
                ticks={graduations(donnees.debut, donnees.fin)}
                tickFormatter={heure}
                tick={{ fontSize: 11, fill: 'currentColor' }}
                stroke="currentColor"
                strokeOpacity={0.3}
                tickLine={false}
              />
              <YAxis
                domain={[0, 100]}
                ticks={[0, 25, 50, 75, 100]}
                // Espace insécable : « 100 » et « % » ne doivent pas se séparer.
                tickFormatter={(v: number) => `${v}\u00a0%`}
                tick={{ fontSize: 11, fill: 'currentColor' }}
                width={48}
                axisLine={false}
                tickLine={false}
              />
              {/* Les seuils en traits pleins, jamais pointillés : sur tout
                  l'écran, le pointillé est réservé à la projection. */}
              <ReferenceLine y={prevision.warning_threshold} stroke={COULEUR.alerte} strokeWidth={1} strokeOpacity={0.8} />
              <ReferenceLine y={prevision.critical_threshold} stroke={COULEUR.critique} strokeWidth={1} strokeOpacity={0.8} />
              <ReferenceLine
                x={donnees.maintenant}
                stroke="currentColor"
                strokeOpacity={0.35}
                label={{ value: 'maintenant', position: 'insideTopLeft', fontSize: 10, fill: 'currentColor', dx: 4 }}
              />
              <Tooltip content={Infobulle} cursor={{ stroke: 'currentColor', strokeOpacity: 0.3 }} />
              <Line
                dataKey="mesure"
                name={ressource.serie}
                stroke={COULEUR.mesure}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 3 }}
                connectNulls={false}
                isAnimationActive={false}
              />
              {donnees.projetee && (
                <Line
                  dataKey="projection"
                  name="Projection"
                  stroke={couleurProjection}
                  strokeWidth={2}
                  strokeDasharray="6 5"
                  dot={false}
                  activeDot={false}
                  connectNulls
                  isAnimationActive={false}
                />
              )}
              <ReferenceDot x={donnees.maintenant} y={donnees.dernier} r={4} fill={COULEUR.mesure} stroke="white" strokeWidth={2} />
              {/* Là où la droite touche le seuil annoncé : le point du graphique
                  et la durée écrite au-dessus disent la même heure. */}
              {seuil && donnees.projetee && (
                <ReferenceDot
                  x={donnees.maintenant + seuil.minutes * 60}
                  y={seuil.seuil}
                  r={5}
                  fill={couleurProjection}
                  stroke="white"
                  strokeWidth={2}
                />
              )}
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}

      <Legende prevision={prevision} serie={ressource.serie} projetee={!!donnees?.projetee} couleurProjection={couleurProjection} />
    </div>
  )
}

function Infobulle({ active, payload, label }: TooltipContentProps) {
  if (!active || !payload?.length) return null
  const lignes = payload.filter((e) => typeof e.value === 'number')
  if (!lignes.length) return null

  return (
    <div className="rounded-lg border border-brand-border bg-white px-2.5 py-1.5 text-xs shadow-md dark:border-slate-700 dark:bg-slate-800">
      <p className="font-medium text-brand-text dark:text-slate-100">{heure(Number(label))}</p>
      {lignes.map((e) => (
        <p key={String(e.dataKey)} className="text-brand-textMuted dark:text-slate-300">
          {e.name} : {nombre(e.value as number)} %
        </p>
      ))}
    </div>
  )
}

function Trait({ couleur, pointille }: { couleur: string; pointille?: boolean }) {
  return (
    <svg width="22" height="6" aria-hidden className="shrink-0">
      <line x1="1" y1="3" x2="21" y2="3" stroke={couleur} strokeWidth={2} strokeLinecap="round" strokeDasharray={pointille ? '4 3' : undefined} />
    </svg>
  )
}

function Legende({
  prevision, serie, projetee, couleurProjection,
}: {
  prevision: ResourceForecast
  serie: string
  projetee: boolean
  couleurProjection: string
}) {
  const r2 = prevision.r2 !== null ? `R² ${nombre(prevision.r2, 2)}` : null
  // Pourquoi il n'y a pas de pointillé, dit en clair : un graphique qui
  // s'arrête à « maintenant » sans explication se lirait comme une panne.
  const absence =
    prevision.verdict === 'insuffisant'
      ? `Pas de projection : historique insuffisant (${prevision.points} points).`
      : `Pas de projection : la droite n'explique pas la série${r2 ? ` (${r2})` : ''}.` +
        (prevision.verdict === 'stable' ? ' Aucun seuil à portée pour autant.' : '')

  return (
    <div className="mt-2 space-y-1.5">
      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-brand-textMuted dark:text-slate-400">
        <li className="flex items-center gap-1.5"><Trait couleur={COULEUR.mesure} />{serie}</li>
        {projetee && (
          <li className="flex items-center gap-1.5"><Trait couleur={couleurProjection} pointille />Projection</li>
        )}
        <li className="flex items-center gap-1.5">
          <Trait couleur={COULEUR.alerte} />Seuil d'alerte ({prevision.warning_threshold} %)
        </li>
        <li className="flex items-center gap-1.5">
          <Trait couleur={COULEUR.critique} />Seuil d'incident ({prevision.critical_threshold} %)
        </li>
      </ul>
      <p className="text-[11px] leading-snug text-brand-textMuted dark:text-slate-500">
        {projetee
          ? `Régression linéaire sur ${prevision.points} points${r2 ? ` · ${r2}` : ''} — recalculée toutes les 2 min, jamais projetée plus loin que l'heure observée.`
          : absence}
      </p>
    </div>
  )
}

// ── Détails : chaque ressource, et de quoi changer de graphique ──────────────

function Details({
  previsions, sante, choisie, onChoisir,
}: {
  previsions: ResourceForecasts['forecasts']
  sante: HealthSnapshot
  choisie: Cle
  onChoisir: (cle: Cle) => void
}) {
  return (
    <div className="rounded-xl bg-slate-50/80 p-4 dark:bg-slate-800/40">
      <div className="flex items-baseline justify-between gap-2">
        <h3 className="text-sm font-semibold text-emerald-700 dark:text-emerald-400">Détails des métriques</h3>
        <span className="text-[11px] text-brand-textMuted dark:text-slate-500">Cliquer pour tracer</span>
      </div>
      <ul className="mt-2 space-y-1">
        {ORDRE.filter((cle) => previsions[cle]).map((cle) => {
          const r = RESSOURCES[cle]
          const p = previsions[cle]
          const valeur = valeurActuelle(cle, sante, p)
          return (
            <li key={cle}>
              <button
                type="button"
                onClick={() => onChoisir(cle)}
                aria-pressed={cle === choisie}
                className={cn(
                  'grid w-full grid-cols-[auto_1fr_auto_auto] items-center gap-3 rounded-lg px-2 py-1.5 text-left text-sm transition-colors',
                  cle === choisie
                    ? 'bg-white shadow-sm ring-1 ring-brand-border dark:bg-slate-900 dark:ring-slate-700'
                    : 'hover:bg-white/70 dark:hover:bg-slate-900/50'
                )}
              >
                <span className="flex size-7 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400">
                  <r.Icone size={15} />
                </span>
                <span className="text-brand-text dark:text-slate-200">{r.libelle}</span>
                <span className="font-medium text-brand-text dark:text-slate-100">
                  {valeur === null ? 'n/d' : `${Math.round(valeur)} %`}
                </span>
                <span className="w-24 text-right">{p ? <Variation prevision={p} /> : null}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** La pente par heure si la droite la soutient ; sinon le verdict, en mots. */
function Variation({ prevision }: { prevision: ResourceForecast }) {
  const pente = prevision.at_horizon !== null ? prevision.slope_per_hour : null
  const { mot, Icone } = SENS[prevision.verdict]

  if (pente === null) {
    return (
      <span className="inline-flex items-center gap-1 text-xs text-brand-textMuted dark:text-slate-500">
        <Icone size={13} aria-hidden />
        {mot}
      </span>
    )
  }

  const ton = prochainSeuil(prevision)?.ton
  const couleur =
    ton ? TEXTE_TON[ton] : prevision.verdict === 'baisse' ? 'text-emerald-600 dark:text-emerald-400' : TEXTE_TON.neutre
  const IconeSens = pente > 0 ? TrendingUp : pente < 0 ? TrendingDown : ArrowRight

  return (
    <span className={cn('inline-flex items-center gap-1 text-xs font-medium', couleur)} title="Pente de la droite, par heure">
      <IconeSens size={13} aria-hidden />
      {pente >= 0 ? '+' : '−'}
      {nombre(Math.abs(pente))} %/h
    </span>
  )
}

// ── Recommandation ───────────────────────────────────────────────────────────

const TON_RECO: Record<Recommandation['ton'], { boite: string; titre: string; Icone: React.ElementType }> = {
  neutre: {
    boite: 'bg-emerald-50/60 dark:bg-emerald-500/[0.06]',
    titre: 'text-emerald-700 dark:text-emerald-400',
    Icone: CheckCircle2,
  },
  alerte: {
    boite: 'bg-amber-50 dark:bg-amber-500/[0.08]',
    titre: 'text-amber-700 dark:text-amber-400',
    Icone: AlertTriangle,
  },
  critique: {
    boite: 'bg-red-50 dark:bg-red-500/[0.08]',
    titre: 'text-red-700 dark:text-red-400',
    Icone: XCircle,
  },
  inconnu: {
    boite: 'bg-slate-50 dark:bg-slate-800/40',
    titre: 'text-slate-700 dark:text-slate-300',
    Icone: HelpCircle,
  },
}

function BlocRecommandation({ reco }: { reco: Recommandation }) {
  const ton = TON_RECO[reco.ton]
  return (
    <div className={cn('flex gap-3 rounded-xl p-4', ton.boite)}>
      <ton.Icone size={20} className={cn('mt-0.5 shrink-0', ton.titre)} aria-hidden />
      <div>
        <p className={cn('text-sm font-semibold', ton.titre)}>Recommandation — {reco.titre}</p>
        <p className="mt-1 text-[13px] leading-relaxed text-brand-text/80 dark:text-slate-300">{reco.texte}</p>
      </div>
    </div>
  )
}
