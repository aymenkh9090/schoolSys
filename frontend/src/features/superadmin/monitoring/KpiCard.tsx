import { AlertTriangle, CheckCircle2, HelpCircle, XCircle } from 'lucide-react'

import { cn } from '@/lib/utils'

/**
 * Une mesure technique, lue face à sa limite.
 *
 * <h4>Ce que cette tuile ajoute à un chiffre coloré</h4>
 *
 * « 62 % » en vert ne dit pas à quelle distance on est de l'incident. Le seuil
 * qui décide de la couleur restait invisible : il fallait connaître le code
 * pour savoir que l'alerte est à 75 %. La jauge le montre, et la légende sous
 * elle le chiffre — un ratio face à une limite se lit sur une piste, pas dans
 * une teinte.
 *
 * <h4>La couleur ne porte jamais l'information seule</h4>
 *
 * Chaque état arrive avec une icône **et** un mot. Un lecteur daltonien, une
 * impression en noir et blanc, un écran en `forced-colors` : dans les trois cas
 * la teinte disparaît, et « Alerte » reste lisible.
 *
 * <h4>Ce qui va bien reste discret</h4>
 *
 * Seules les tuiles en alerte ou en incident reçoivent un fond teinté et un
 * liseré. Une rangée où tout crie ne dit rien : si les huit mesures étaient
 * colorées à égalité, l'œil devrait les lire une par une pour trouver celle qui
 * pose problème. En laissant le normal calme, la seule tuile teintée de la page
 * se voit avant d'être lue.
 *
 * <h4>Absent n'est pas zéro</h4>
 *
 * Une métrique manquante affiche « n/d », une piste vide et l'état
 * « Indisponible ». Afficher 0 % d'erreurs quand Prometheus est injoignable
 * serait un mensonge visuel, et c'est celui qui coûte le plus cher en
 * supervision : on ne va pas voir.
 */

export type Severite = 'normal' | 'alerte' | 'critique' | 'inconnu'

/** Au-delà d'`alerte` on surveille, au-delà de `critique` on intervient. */
export interface Seuils {
  alerte: number
  critique: number
}

export function severite(valeur: number | null | undefined, seuils?: Seuils): Severite {
  if (valeur === null || valeur === undefined) return 'inconnu'
  if (!seuils) return 'normal'
  if (valeur >= seuils.critique) return 'critique'
  if (valeur >= seuils.alerte) return 'alerte'
  return 'normal'
}

interface Ton {
  /** Fond et liseré de la carte — vides pour l'état normal, qui doit se taire. */
  carte: string
  /** Pastille de l'icône : la seule touche de couleur d'une tuile qui va bien. */
  pastille: string
  pilule: string
  barre: string
  /** Piste : un pas clair de la MÊME rampe que la barre, jamais un gris neutre —
   *  l'état se lit alors sur toute la largeur, pas seulement sur la part pleine. */
  piste: string
  Icone: React.ElementType
  mot: string
}

const TONS: Record<Severite, Ton> = {
  normal: {
    carte: 'border-brand-border dark:border-slate-700',
    pastille: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400',
    pilule: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400',
    barre: 'bg-emerald-500',
    piste: 'bg-emerald-100 dark:bg-emerald-500/20',
    Icone: CheckCircle2,
    mot: 'Normal',
  },
  alerte: {
    carte: 'border-amber-200 bg-amber-50/50 dark:border-amber-500/30 dark:bg-amber-500/[0.07]',
    pastille: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400',
    pilule: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300',
    barre: 'bg-amber-500',
    piste: 'bg-amber-200/70 dark:bg-amber-500/25',
    Icone: AlertTriangle,
    mot: 'Alerte',
  },
  critique: {
    carte: 'border-red-200 bg-red-50/50 dark:border-red-500/30 dark:bg-red-500/[0.07]',
    pastille: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-400',
    pilule: 'bg-red-100 text-red-800 dark:bg-red-500/15 dark:text-red-300',
    barre: 'bg-red-600 dark:bg-red-500',
    piste: 'bg-red-200/70 dark:bg-red-500/25',
    Icone: XCircle,
    mot: 'Critique',
  },
  inconnu: {
    carte: 'border-brand-border dark:border-slate-700',
    pastille: 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400',
    pilule: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400',
    barre: 'bg-slate-400',
    piste: 'bg-slate-200 dark:bg-slate-700',
    Icone: HelpCircle,
    mot: 'Indisponible',
  },
}

/** Le socle commun : mêmes proportions pour toutes les tuiles de la rangée. */
const CARTE =
  'group relative flex h-full flex-col rounded-2xl border bg-white p-5 shadow-sm ' +
  'transition-shadow hover:shadow-md dark:bg-slate-900'

function Entete({ label, ton, Icon }: { label: string; ton: Ton; Icon: React.ElementType }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <p className="pt-1 text-[13px] font-medium leading-tight text-brand-textMuted dark:text-slate-400">
        {label}
      </p>
      <span className={cn('shrink-0 rounded-xl p-2 transition-transform group-hover:scale-105', ton.pastille)}>
        <Icon size={18} />
      </span>
    </div>
  )
}

function Pilule({ ton }: { ton: Ton }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 self-start rounded-full px-2 py-0.5 text-[11px] font-semibold',
        ton.pilule
      )}
    >
      <ton.Icone size={12} className="shrink-0" />
      {ton.mot}
    </span>
  )
}

interface Props {
  /** Phrase, sans deux-points final. */
  label: string
  valeur: number | null
  unite?: string
  decimales?: number
  icon: React.ElementType
  /** Absents pour une mesure qui n'a pas de limite — le débit, par exemple. */
  seuils?: Seuils
  /**
   * Borne haute de la jauge. Un pourcentage se lit sur 0–100 ; une latence n'a
   * pas de maximum naturel, on la borne alors au seuil d'incident.
   */
  max?: number
  /** Une précision sous la valeur, quand le chiffre seul ne suffit pas. */
  detail?: string
}

export function KpiCard({ label, valeur, unite = '', decimales = 1, icon: Icon, seuils, max, detail }: Props) {
  const etat = severite(valeur, seuils)
  const ton = TONS[etat]
  const absente = valeur === null || valeur === undefined
  const borne = max ?? seuils?.critique
  const remplissage = absente || !borne ? 0 : Math.min(100, Math.max(0, (valeur / borne) * 100))

  return (
    <div className={cn(CARTE, ton.carte)}>
      <Entete label={label} ton={ton} Icon={Icon} />

      {/* Chiffres proportionnels : `tabular-nums` donnerait à chaque chiffre la
          largeur d'un zéro, et « 121 » paraîtrait distendu à cette taille. */}
      <p className="mt-3 text-[28px] font-semibold leading-none tracking-tight text-brand-text dark:text-slate-100">
        {absente ? 'n/d' : valeur.toFixed(decimales)}
        {!absente && unite && (
          <span className="ms-1 text-sm font-medium text-brand-textMuted dark:text-slate-400">{unite}</span>
        )}
      </p>

      {detail && (
        <p className="mt-1.5 text-[11px] leading-snug text-brand-textMuted dark:text-slate-500">{detail}</p>
      )}

      {/* `mt-auto` cale le bas de toutes les tuiles sur la même ligne, que la
          précision tienne sur une ligne ou deux. Une rangée dont les jauges
          ondulent se lit comme une rangée mal faite. */}
      <div className="mt-auto pt-4">
        <Pilule ton={ton} />

        {borne && (
          <>
            <div className={cn('relative mt-2.5 h-1.5 w-full overflow-hidden rounded-full', ton.piste)}>
              <div
                className={cn('h-full rounded-full transition-[width] duration-700 ease-out', ton.barre)}
                style={{ width: `${remplissage}%` }}
              />
              {/* Les seuils, posés sur la piste : c'est ce qui transforme un
                  chiffre en mesure — on voit la marge qui reste avant l'alerte.
                  Traits pleins, jamais pointillés : un pointillé se lit comme
                  une projection, pas comme une limite. */}
              {seuils &&
                [seuils.alerte, seuils.critique]
                  // Un seuil qui vaut la borne se confond avec la fin de la
                  // piste : le trait tomberait hors du cadre. La fin de la
                  // piste dit déjà « à partir d'ici, c'est l'incident ».
                  .filter((seuil) => seuil < borne)
                  .map((seuil) => (
                    <span
                      key={seuil}
                      aria-hidden
                      className="absolute inset-y-0 w-px bg-white/90 dark:bg-slate-900/90"
                      style={{ left: `${(seuil / borne) * 100}%` }}
                    />
                  ))}
            </div>

            {seuils && (
              <p className="mt-1.5 text-[10.5px] leading-none text-brand-textMuted dark:text-slate-500">
                Alerte {seuils.alerte}
                {unite} · incident {seuils.critique}
                {unite}
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )
}

/**
 * Un état binaire — l'application répond, ou elle ne répond pas.
 *
 * Pas de jauge : il n'y a rien à doser. Icône et mot, pour la même raison que
 * partout ailleurs sur cet écran.
 */
export function StatutCard({
  label, statut, icon: Icon,
}: {
  label: string
  statut: string
  icon: React.ElementType
}) {
  const etat: Severite = statut === 'UP' ? 'normal' : statut === 'DOWN' ? 'critique' : 'inconnu'
  const ton = TONS[etat]

  return (
    <div className={cn(CARTE, ton.carte)}>
      <Entete label={label} ton={ton} Icon={Icon} />

      <p className="mt-3 text-[28px] font-semibold leading-none tracking-tight text-brand-text dark:text-slate-100">
        {statut}
      </p>
      <p className="mt-1.5 text-[11px] leading-snug text-brand-textMuted dark:text-slate-500">
        {statut === 'UP' ? 'Répond normalement' : statut === 'DOWN' ? 'Ne répond plus' : 'État inconnu'}
      </p>

      <div className="mt-auto pt-4">
        <Pilule ton={ton} />
      </div>
    </div>
  )
}
