import { Badge } from '@/components/ui/Badge'
import { type BusinessFinding } from '@/api/planning.api'
import { cn } from '@/lib/utils'

/**
 * L'affichage des constats métier, partagé par les deux moments où l'on en
 * produit.
 *
 * <p>Le contrôle qui précède la génération et la validation qui suit disent la
 * même sorte de chose — « voici ce qui ne va pas, et où » — et il n'y a aucune
 * raison qu'un directeur les lise sous deux formes différentes. D'où ce module :
 * un libellé, une carte, un regroupement, écrits une fois.
 */

export const FINDING_TONE_CLASSES: Record<'danger' | 'warning' | 'info', string> = {
  danger: 'border-red-200 bg-red-50/60 dark:border-red-500/20 dark:bg-red-500/5',
  warning: 'border-amber-200 bg-amber-50/60 dark:border-amber-500/20 dark:bg-amber-500/5',
  info: 'border-blue-200 bg-blue-50/60 dark:border-blue-500/20 dark:bg-blue-500/5',
}

/**
 * Libellés des contrôles. Le backend renvoie un code stable ; on ne l'affiche
 * pas tel quel — « SEANCE_NON_PLACEE » ne veut rien dire pour un directeur
 * d'établissement. Un code inconnu retombe sur lui-même plutôt que de
 * disparaître : un contrôle ajouté côté serveur doit rester visible ici avant
 * même qu'on lui ait écrit un libellé.
 */
export const BUSINESS_LABELS: Record<string, string> = {
  // Validation d'après la génération
  SEANCE_NON_PLACEE: 'Séances jamais placées',
  SEANCE_DUPLIQUEE: 'Séances comptées deux fois',
  ENSEIGNANT_MANQUANT: 'Séances sans enseignant',
  DEMI_GROUPE_DESAPPARIE: 'Demi-groupes désappariés',
  VOLUME_HORAIRE: 'Volume horaire hors programme officiel',
  VOLUME_NON_VERIFIABLE: 'Volume officiel absent des données',
  MATIERE_ABSENTE: 'Matière du programme absente de l\'emploi du temps',
  CONFLIT_ENSEIGNANT: 'Enseignant attendu à deux endroits',
  CONFLIT_CLASSE: 'Classe à deux cours en même temps',
  CONFLIT_SALLE: 'Salle occupée deux fois',
  ENSEIGNANT_INDISPONIBLE: 'Cours un jour d\'indisponibilité',
  CAPACITE_SALLE: 'Salle trop petite',
  SALLE_INADAPTEE: 'Salle spécialisée manquante',
  COURS_PENDANT_LA_PAUSE: 'Cours sur la pause méridienne',
  SEANCE_DEBORDANTE: 'Séance débordant de la demi-journée',
  RAPPORT_ARCHIVE: 'Rapport archivé de la génération',

  // Contrôle avant la génération
  MATIERE_SANS_ENSEIGNANT: 'Matière déclarée sans enseignant affecté',
  VOLUME_INCOHERENT: 'Le découpage des séances contredit le volume déclaré',
  SEANCE_HORS_PROGRAMME: 'Séances hors du programme du niveau',
  TYPE_DE_SALLE_ABSENT: 'Type de salle exigé mais inexistant',
  AUCUNE_SALLE_ASSEZ_GRANDE: 'Aucune salle ne peut accueillir la classe',
  SERVICE_IMPOSSIBLE: 'Service impossible à tenir en une semaine',
  SEMAINE_TROP_COURTE: 'Programme plus long que la semaine ouverte',
  PROGRAMME_INCONNU: 'Aucun programme déclaré pour les classes',
}

/** Nombre d'exemples cités par famille — au-delà, on annonce le reste. */
const BUSINESS_EXAMPLES = 3

/**
 * Regroupe les constats par code, en conservant l'ordre d'arrivée : le backend
 * les émet contrôle par contrôle, et cet ordre est celui du diagnostic.
 */
export function groupFindings(findings: BusinessFinding[]) {
  const groups = new Map<string, BusinessFinding[]>()
  for (const f of findings) {
    const existing = groups.get(f.code)
    if (existing) existing.push(f)
    else groups.set(f.code, [f])
  }
  return [...groups.entries()]
}

export function BusinessCard({ code, findings }: { code: string; findings: BusinessFinding[] }) {
  const blocking = findings[0].severity === 'BLOQUANT'
  const tone = blocking ? 'danger' : 'warning'
  const rest = findings.length - BUSINESS_EXAMPLES
  return (
    <div className={cn('rounded-lg border p-3', FINDING_TONE_CLASSES[tone])}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium text-brand-text dark:text-slate-100">
          {BUSINESS_LABELS[code] ?? code}
        </p>
        <Badge variant={tone} className="shrink-0">
          {findings.length} {blocking ? 'bloquant(s)' : 'à vérifier'}
        </Badge>
      </div>
      <ul className="mt-2 space-y-1">
        {findings.slice(0, BUSINESS_EXAMPLES).map((f, i) => (
          <li key={i} className="text-xs text-brand-textMuted dark:text-slate-400 pl-3 border-l-2 border-brand-border dark:border-slate-700">
            {f.scope ? <strong className="font-medium">{f.scope}</strong> : null}
            {f.scope ? ' — ' : ''}{f.message}
          </li>
        ))}
      </ul>
      {rest > 0 && (
        <p className="mt-1.5 text-xs text-brand-textMuted dark:text-slate-500">
          … et {rest} autre{rest > 1 ? 's' : ''}
        </p>
      )}
    </div>
  )
}
