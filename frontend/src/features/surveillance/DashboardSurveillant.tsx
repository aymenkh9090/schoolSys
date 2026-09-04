import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  CalendarDays,
  ClipboardList,
  Clock,
  FileText,
  Inbox,
  ShieldCheck,
  UserCheck,
  XCircle,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { SectionTitle } from '@/components/ui/SectionTitle'
import { StatCard } from '@/components/ui/StatCard'
import { Badge } from '@/components/ui/Badge'
import { QuickActions } from '@/features/dashboard/QuickActions'
import { useAuth } from '@/hooks/useAuth'
import { absenceApi } from '@/api/absence.api'
import { formatDate } from '@/lib/utils'
import {
  TYPE_JUSTIFICATIF_LABELS,
  formatJour,
  todayIso,
  useReferentielScolaire,
} from '@/features/absence/absenceLabels'

/** Les 5 dernières lignes suffisent en aperçu : le détail vit sur sa page. */
const APERCU = 5

/**
 * Tableau de bord de la vie scolaire : l'état de la journée en un écran —
 * absences saisies, appels encore ouverts et justificatifs à traiter
 * du personnel.
 */
export default function DashboardSurveillant() {
  const { user } = useAuth()
  const ref = useReferentielScolaire()
  const jour = todayIso()

  const { data: appels = [] } = useQuery({
    queryKey: ['appel-sessions', { date: jour }],
    queryFn: () => absenceApi.appel.lister({ date: jour }),
  })

  const { data: justificatifs = [] } = useQuery({
    queryKey: ['justificatifs', 'file', 'EN_ATTENTE'],
    queryFn: () => absenceApi.justificatifs.list({ statut: 'EN_ATTENTE' }),
  })

  const lignes = appels.flatMap((a) => a.lignesAppel ?? [])
  const absents = lignes.filter((l) => l.statut === 'ABSENT')
  const retards = lignes.filter((l) => l.statut === 'RETARD').length
  const nonJustifiees = absents.filter((l) => !l.estJustifie).length
  const appelsOuverts = appels.filter((a) => !a.estVerrouille)

  return (
    <div className="space-y-8">
      <PageHero
        title={`Bonjour ${user?.given_name ?? user?.name ?? ''}`.trim()}
        subtitle={`Vie scolaire — journée du ${formatDate(jour)}`}
        icon={ShieldCheck}
      />

      <section>
        <SectionTitle title="La journée en cours" icon={CalendarDays} size="lg" />
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard title="Absences élèves" value={absents.length} icon={XCircle} color="red" />
          <StatCard title="Retards" value={retards} icon={Clock} color="yellow" />
          <StatCard title="Absences non justifiées" value={nonJustifiees} icon={FileText} color="amber" />
          <StatCard title="Appels encore ouverts" value={appelsOuverts.length} icon={UserCheck} color="blue" />
        </div>
      </section>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        {/* Appels du jour */}
        <section className="rounded-2xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <SectionTitle
            title="Séances d'appel du jour"
            icon={ClipboardList}
            accent="teal"
            action={
              <Link
                to="/ecole/absences/journee"
                className="flex items-center gap-1 text-xs font-medium text-brand-blue hover:underline"
              >
                Voir les absences <ArrowRight size={13} />
              </Link>
            }
          />

          {appels.length === 0 ? (
            <p className="py-6 text-center text-sm text-brand-textMuted dark:text-slate-400">
              Aucune séance d'appel ouverte aujourd'hui.
            </p>
          ) : (
            <>
              <p className="mb-3 text-sm text-brand-textMuted dark:text-slate-400">
                {appels.length} séance{appels.length > 1 ? 's' : ''} appelée{appels.length > 1 ? 's' : ''} ·{' '}
                {appelsOuverts.length} encore ouverte{appelsOuverts.length > 1 ? 's' : ''}
              </p>
              <ul className="space-y-2">
                {appels.slice(0, APERCU).map((a) => {
                  const absentsSeance = (a.lignesAppel ?? []).filter((l) => l.statut === 'ABSENT').length
                  return (
                    <li
                      key={a.id}
                      className="flex items-center justify-between gap-3 rounded-xl border border-brand-border px-3 py-2.5 dark:border-slate-700"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-brand-text dark:text-slate-100">
                          {ref.nomClasse(a.groupeClasseId)} · {ref.nomMatiere(a.matiereId)}
                        </p>
                        <p className="truncate text-xs text-brand-textMuted dark:text-slate-400">
                          {ref.nomEnseignant(a.enseignantId)} · {absentsSeance} absent{absentsSeance > 1 ? 's' : ''}
                        </p>
                      </div>
                      <Badge variant={a.estVerrouille ? 'default' : 'warning'}>
                        {a.estVerrouille ? 'Clôturée' : 'Ouverte'}
                      </Badge>
                    </li>
                  )
                })}
              </ul>
            </>
          )}
        </section>

        {/* Justificatifs à traiter */}
        <section className="rounded-2xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <SectionTitle
            title="Justificatifs à traiter"
            icon={Inbox}
            accent="amber"
            action={
              <Link
                to="/ecole/absences/justificatifs"
                className="flex items-center gap-1 text-xs font-medium text-brand-blue hover:underline"
              >
                Tout traiter <ArrowRight size={13} />
              </Link>
            }
          />

          {justificatifs.length === 0 ? (
            <p className="py-6 text-center text-sm text-brand-textMuted dark:text-slate-400">
              Aucun justificatif élève en attente.
            </p>
          ) : (
            <ul className="space-y-2">
              {justificatifs.slice(0, APERCU).map((j) => (
                <li
                  key={j.id}
                  className="flex items-center justify-between gap-3 rounded-xl border border-brand-border px-3 py-2.5 dark:border-slate-700"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-brand-text dark:text-slate-100">
                      {ref.nomEleve(j.eleveId)}
                    </p>
                    <p className="truncate text-xs text-brand-textMuted dark:text-slate-400">
                      {formatJour(j.dateSeance)} · {TYPE_JUSTIFICATIF_LABELS[j.typeDocument]}
                    </p>
                  </div>
                  <Badge variant="warning">En attente</Badge>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <QuickActions />
    </div>
  )
}
