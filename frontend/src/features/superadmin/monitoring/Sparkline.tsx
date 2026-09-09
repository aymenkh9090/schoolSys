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
 */

const LARGEUR = 100
const HAUTEUR = 24
/** Marge verticale : sans elle, un extremum verrait son trait coupé au ras du cadre. */
const MARGE = 3

interface Props {
  points: number[]
  /** Unité affichée dans l'infobulle, pour que les bornes se lisent. */
  unite?: string
  /** Libellé de la période couverte, ex. « 1 h ». */
  periode?: string
  className?: string
}

export function Sparkline({ points, unite = '', periode = '1 h', className }: Props) {
  // Un point isolé n'est pas une tendance : il n'y a rien à relier.
  if (points.length < 2) return null

  const min = Math.min(...points)
  const max = Math.max(...points)
  const amplitude = max - min

  const y = (valeur: number) =>
    amplitude === 0
      ? // Série parfaitement plate : la centrer. La ramener au bas du cadre
        // suggérerait un minimum, alors qu'il n'y a eu aucune variation.
        HAUTEUR / 2
      : MARGE + (1 - (valeur - min) / amplitude) * (HAUTEUR - 2 * MARGE)

  const x = (index: number) => (index / (points.length - 1)) * LARGEUR

  const trace = points.map((valeur, i) => `${x(i)},${y(valeur)}`).join(' ')
  const dernier = points[points.length - 1]

  const legende = `Sur ${periode} — minimum ${min.toFixed(1)}${unite}, maximum ${max.toFixed(1)}${unite}`

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
      </svg>

      {/* Le point courant est posé en HTML, et non dans le SVG : l'étirement
          horizontal qui fait remplir la carte au tracé transformerait un cercle
          en ellipse. Il tombe toujours à droite — c'est le dernier point — donc
          seule son ordonnée est à calculer. L'anneau à la couleur du fond le
          détache du tracé là où les deux se croisent. */}
      <span
        aria-hidden
        className="absolute size-[7px] -translate-y-1/2 translate-x-1/2 rounded-full bg-current ring-2 ring-white dark:ring-slate-900"
        style={{ right: 0, top: `${(y(dernier) / HAUTEUR) * 100}%` }}
      />
    </div>
  )
}
