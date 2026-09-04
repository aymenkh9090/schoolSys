import { CalendarDays, Printer } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { TimetableGrid } from '@/features/planning/TimetableGrid'
import { SeancesDuJour } from './SeancesDuJour'
import { useMonPlanning } from './useMonPlanning'

/**
 * Emploi du temps du seul enseignant connecté (contrairement à « Consultation
 * planning », qui balaie toutes les classes/enseignants et reste réservé à
 * l'administration).
 */
export default function MonPlanning() {
  const { me, meLoading, meError, meErrorMessage, published, timetable, timetableLoading } = useMonPlanning()

  if (meLoading) {
    return <div className="h-64 animate-pulse rounded-xl bg-brand-bgSecondary dark:bg-slate-800" />
  }

  if (meError || !me) {
    return (
      <div className="space-y-6">
        <PageHeader title="Mon planning" subtitle="Emploi du temps personnel" />
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-6 text-sm text-yellow-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300">
          {meErrorMessage ??
            "Aucune fiche enseignant n'est liée à votre compte. Demandez à l'administration de créer votre fiche dans Gestion des enseignants."}
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Mon planning"
        subtitle={`${me.nomComplet} — ${me.specialite || 'Enseignant'}`}
        actions={
          timetable && (
            <Button variant="outline" className="print:hidden" onClick={() => window.print()}>
              <Printer size={16} /> Imprimer
            </Button>
          )
        }
      />

      <div className="print:hidden">
        <SeancesDuJour />
      </div>

      <div className="rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900 print:border-0 print:p-0">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Semaine complète</h3>
          {published?.publishedAt && (
            <Badge variant="info" className="print:hidden">
              Publié le {new Date(published.publishedAt).toLocaleDateString('fr-FR')}
            </Badge>
          )}
        </div>

        {!published ? (
          <div className="p-8 text-center">
            <CalendarDays size={32} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-400" />
            <p className="text-sm text-brand-textMuted dark:text-slate-400">
              Aucun emploi du temps publié pour le moment. Il apparaîtra ici dès que l'administration l'aura publié.
            </p>
          </div>
        ) : timetableLoading ? (
          <div className="h-64 animate-pulse rounded-xl bg-brand-bgSecondary dark:bg-slate-800" />
        ) : timetable ? (
          <TimetableGrid jobId={published.jobId} view={timetable} canEdit={false} onChanged={() => {}} />
        ) : (
          <p className="p-4 text-sm text-brand-textMuted dark:text-slate-400">
            Aucune séance trouvée pour vous dans l'emploi du temps publié.
          </p>
        )}
      </div>
    </div>
  )
}
