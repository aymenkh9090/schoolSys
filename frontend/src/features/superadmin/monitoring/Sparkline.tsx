/**
 * La forme récente d'une mesure, en vignette.
 *
 * <h4>Ce qu'une courbe ajoute à un chiffre</h4>
 *
 * « 78 % de mémoire » se lit tout autrement selon qu'on venait de 40 % ou de
 * 82 % : dans le premier cas quelque chose se passe, dans le second cela
 * redescend. Le chiffre seul ne permet pas de trancher, et c'est pourtant la
 * question qu'on se pose devant un écran de supervision.
 *
 * <h4>Une forme, pas un graphique</h4>
 *
 * Deux centimètres de large ne portent pas « la valeur à 14 h 07 ». Ni axes, ni
 * grille, ni étiquettes : douze points suffisent à dire « ça monte » ou « c'est
 * plat », et davantage rendrait la vignette illisible. Les valeurs précises
 * restent lisibles ailleurs — le chiffre courant est posé juste au-dessus, et
 * les bornes de la période sont dans l'infobulle du tracé.
 *
 * <h4>L'échelle est celle de la série, pas celle du seuil</h4>
 *
 * Le tracé remplit toute sa hauteur entre son minimum et son maximum. Il montre
 * la **variation**, quand la jauge en dessous montre le **niveau** face aux
 * seuils. Les deux répondent à des questions différentes : caler la courbe sur
 * l'échelle des seuils l'écraserait en ligne plate dès que tout va bien, et on
 * perdrait précisément ce qu'elle sert à voir.
 *
 * <h4>La projection, en pointillé et à la même échelle de temps</h4>
 *
 * Quand une prévision l'autorise, la courbe se prolonge en pointillé jusqu'à
 * l'horizon — le pointillé est réservé à cet usage sur tout l'écran. Passé et
 * futur partagent la même échelle : une heure d'historique et une heure de
 * projection prennent chacune la moitié de la largeur. Comprimer la projection
 * dans un coin la rendrait plus raide qu'elle n'est, et c'est sa pente qu'on
 * lit.
 */

const LARGEUR = 100
const HAUTEUR = 24
/** Marge verticale : sans elle, un extremum verrait son trait coupé au ras du cadre. */
const MARGE = 3

export interface Projection {
  /** Variation prévue d'ici l'horizon, dans l'unité de la série. */
  variation: number
  /** Durée de la projection, en minutes. */
  minutes: number
  /** Couleur du pointillé, quand il doit se distinguer du tracé. */
  className?: string
  /** Libellé de la durée, ex. « 1 h », pour l'infobulle. */
  libelle?: string
}

interface Props {
  points: number[]
  /** Unité affichée dans l'infobulle, pour que les bornes se lisent. */
  unite?: string
  /** Libellé de la période couverte, ex. « 1 h ». */
  periode?: string
  /** Durée couverte par `points`, en minutes — l'échelle de temps de la projection. */
  dureeMinutes?: number
  projection?: Projection
  className?: string
}

export function Sparkline({
  points, unite = '', periode = '1 h', dureeMinutes = 60, projection, className,
}: Props) {
  // Un point isolé n'est pas une tendance : il n'y a rien à relier.
  if (points.length < 2) return null

  const dernier = points[points.length - 1]
  const projete = projection && projection.minutes > 0 ? projection : undefined
  // Une ressource ne descend pas sous zéro, quelle que soit la pente de la
  // droite : sur un CPU au repos qui décroît, elle passerait sous l'axe.
  const arrivee = projete ? Math.max(0, dernier + projete.variation) : dernier
  // Part de la largeur occupée par le passé, à échelle de temps commune.
  const partPassee = projete ? dureeMinutes / (dureeMinutes + projete.minutes) : 1

  // L'arrivée de la projection entre dans l'échelle : une hausse prévue qui
  // sortirait du cadre serait coupée au moment précis où elle devient utile.
  const valeurs = projete ? [...points, arrivee] : points
  const min = Math.min(...valeurs)
  const max = Math.max(...valeurs)
  const amplitude = max - min

  const y = (valeur: number) =>
    amplitude === 0
      ? // Série parfaitement plate : la centrer. La ramener au bas du cadre
        // suggérerait un minimum, alors qu'il n'y a eu aucune variation.
        HAUTEUR / 2
      : MARGE + (1 - (valeur - min) / amplitude) * (HAUTEUR - 2 * MARGE)

  const x = (index: number) => (index / (points.length - 1)) * LARGEUR * partPassee

  const trace = points.map((valeur, i) => `${x(i)},${y(valeur)}`).join(' ')

  const bornes = Math.min(...points)
  const sommet = Math.max(...points)
  let legende = `Sur ${periode} — minimum ${bornes.toFixed(1)}${unite}, maximum ${sommet.toFixed(1)}${unite}`
  if (projete) {
    const signe = projete.variation >= 0 ? '+' : '−'
    legende +=
      `. Projection${projete.libelle ? ` sur ${projete.libelle}` : ''} : ` +
      `${signe}${Math.abs(projete.variation).toFixed(1)}${unite}`
  }

  return (
    <div className={`relative ${className ?? ''}`} role="img" aria-label={legende} title={legende}>
      <svg
        viewBox={`0 0 ${LARGEUR} ${HAUTEUR}`}
        // La vignette s'étire en largeur mais garde l'épaisseur de son trait :
        // sans `preserveAspectRatio="none"` elle ne remplirait pas la carte, et
        // sans `non-scaling-stroke` l'étirement épaissirait la courbe.
        preserveAspectRatio="none"
        className="h-full w-full"
        aria-hidden
      >
        <polyline
          points={trace}
          fill="none"
          stroke="currentColor"
          strokeWidth={1.5}
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
          // La courbe est le contexte, le chiffre au-dessus est le sujet : elle
          // reste en retrait pour ne pas lui disputer l'attention.
          opacity={0.55}
        />
        {projete && (
          <line
            x1={LARGEUR * partPassee}
            y1={y(dernier)}
            x2={LARGEUR}
            y2={y(arrivee)}
            className={projete.className}
            stroke="currentColor"
            strokeWidth={1.5}
            strokeDasharray="3 3"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
            opacity={0.8}
          />
        )}
      </svg>

      {/* Le point courant est posé en HTML, et non dans le SVG : l'étirement
          horizontal qui fait remplir la carte au tracé transformerait un cercle
          en ellipse. Il marque « maintenant » — la fin du tracé, et le départ
          du pointillé quand il y en a un. L'anneau à la couleur du fond le
          détache du tracé là où les deux se croisent. */}
      <span
        aria-hidden
        className="absolute size-[7px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-current ring-2 ring-white dark:ring-slate-900"
        style={{ left: `${partPassee * 100}%`, top: `${(y(dernier) / HAUTEUR) * 100}%` }}
      />
    </div>
  )
}
