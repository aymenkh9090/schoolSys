import { AlertTriangle, CheckCircle2, HelpCircle, XCircle } from 'lucide-react'

import { cn } from '@/lib/utils'

/**
 * Une mesure technique, lue face à sa limite.
 *
 * <h4>Ce que cette tuile ajoute à un chiffre coloré</h4>
 *
 * « 62 % » en vert ne dit pas à quelle distance on est de l'incident. Le seuil
 * qui décide de la couleur restait invisible : il fallait connaître le code
 * pour savoir que l'alerte est à 75 %. La jauge le montre, et le texte sous
 * elle le chiffre — un ratio face à une limite se lit sur une piste, pas dans
 * une teinte.
 *
 * <h4>La couleur ne porte jamais l'information seule</h4>
 *
 * Chaque état arrive avec une icône **et** un mot. Un lecteur daltonien, une
 * impression en noir et blanc, un écran en `forced-colors` : dans les trois cas
 * la teinte disparaît, et « Alerte » reste lisible.
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

/**
 * La piste non remplie est un pas clair de la **même** rampe que le remplissage :
 * l'état se lit sur toute la largeur de la barre, pas seulement sur sa portion
 * pleine. Un gris neutre en fond ferait perdre cette lecture d'un coup d'œil.
 */
const TONS: Record<Severite, { texte: string; barre: string; piste: string; Icone: React.ElementType; mot: string }> = {
  normal: {
    texte: 'text-emerald-700 dark:text-emerald-400',
    barre: 'bg-emerald-600 dark:bg-emerald-500',
    piste: 'bg-emerald-100 dark:bg-emerald-500/20',
    Icone: CheckCircle2,
    mot: 'Normal',
  },
  alerte: {
    texte: 'text-amber-700 dark:text-amber-400',
    barre: 'bg-amber-500',
    piste: 'bg-amber-100 dark:bg-amber-500/20',
    Icone: AlertTriangle,
    mot: 'Alerte',
  },
  critique: {
    texte: 'text-red-700 dark:text-red-400',
    barre: 'bg-red-600 dark:bg-red-500',
    piste: 'bg-red-100 dark:bg-red-500/20',
    Icone: XCircle,
    mot: 'Critique',
  },
  inconnu: {
    texte: 'text-brand-textMuted dark:text-slate-400',
    barre: 'bg-slate-400',
    piste: 'bg-slate-100 dark:bg-slate-700/50',
    Icone: HelpCircle,
    mot: 'Indisponible',
  },
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
  const borne = max ?? seuils?.critique
  const remplissage =
    valeur === null || valeur === undefined || !borne
      ? 0
      : Math.min(100, Math.max(0, (valeur / borne) * 100))

  return (
    <div className="rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium text-brand-textMuted dark:text-slate-400">{label}</p>
        <Icon size={18} className="shrink-0 text-brand-textMuted dark:text-slate-500" />
      </div>

      {/* Chiffres proportionnels : `tabular-nums` donnerait à chaque chiffre la
          largeur d'un zéro, et « 121 » paraîtrait distendu à cette taille. */}
      <p className="mt-1 text-2xl font-semibold text-brand-text dark:text-slate-100">
        {valeur === null || valeur === undefined ? 'n/d' : valeur.toFixed(decimales)}
        {valeur !== null && valeur !== undefined && unite && (
          <span className="ms-1 text-base font-medium text-brand-textMuted dark:text-slate-400">{unite}</span>
        )}
      </p>

      {detail && <p className="text-xs text-brand-textMuted dark:text-slate-400">{detail}</p>}

      <div className={cn('mt-3 flex items-center gap-1.5 text-xs font-medium', ton.texte)}>
        <ton.Icone size={13} className="shrink-0" />
        {ton.mot}
      </div>

      {borne && (
        <>
          <div className={cn('relative mt-2 h-1.5 w-full overflow-hidden rounded-[4px]', ton.piste)}>
            <div
              className={cn('h-full rounded-r-[4px] transition-[width] duration-500', ton.barre)}
              style={{ width: `${remplissage}%` }}
            />
            {/* Les seuils, posés sur la piste : c'est ce qui transforme un
                chiffre en mesure — on voit la marge qui reste avant l'alerte.
                Traits pleins, jamais pointillés : un pointillé se lit comme une
                projection, pas comme une limite. */}
            {seuils &&
              [seuils.alerte, seuils.critique]
                // Un seuil qui vaut la borne se confond avec la fin de la
                // piste : le trait tomberait hors du cadre. La fin de la piste
                // dit déjà « à partir d'ici, c'est l'incident ».
                .filter((seuil) => seuil < borne)
                .map((seuil) => (
                  <span
                    key={seuil}
                    aria-hidden
                    className="absolute inset-y-0 w-px bg-white/80 dark:bg-slate-900/80"
                    style={{ left: `${(seuil / borne) * 100}%` }}
                  />
                ))}
          </div>

          {seuils && (
            <p className="mt-1.5 text-[11px] text-brand-textMuted dark:text-slate-500">
              Alerte à {seuils.alerte}
              {unite} · incident à {seuils.critique}
              {unite}
            </p>
          )}
        </>
      )}
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
    <div className="rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium text-brand-textMuted dark:text-slate-400">{label}</p>
        <Icon size={18} className="shrink-0 text-brand-textMuted dark:text-slate-500" />
      </div>
      <p className="mt-1 text-2xl font-semibold text-brand-text dark:text-slate-100">{statut}</p>
      <div className={cn('mt-3 flex items-center gap-1.5 text-xs font-medium', ton.texte)}>
        <ton.Icone size={13} className="shrink-0" />
        {statut === 'UP' ? 'Répond normalement' : statut === 'DOWN' ? 'Ne répond plus' : 'État inconnu'}
      </div>
    </div>
  )
}
