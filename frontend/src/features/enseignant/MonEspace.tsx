import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Clock, ClipboardList, Mail, Phone, Printer, UserCircle } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { StatCard } from '@/components/ui/StatCard'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { organisationApi, type TeacherAssignment } from '@/api/organisation.api'
import { planningApi } from '@/api/planning.api'
import { TimetableGrid } from '@/features/planning/TimetableGrid'

const SESSION_TYPE_LABELS: Record<string, string> = {
  COURSE: 'Cours', TD: 'TD', TP: 'TP', LAB: 'Labo', SPORT: 'Sport', EXAM: 'Examen',
}

export default function MonEspace() {
  const { data: me, isLoading, isError, error } = useQuery({
    queryKey: ['teacher-me'],
    queryFn: organisationApi.teachers.me,
    retry: false,
  })

  // Dernier emploi du temps publié → vue enseignant
  const { data: generated = [] } = useQuery({
    queryKey: ['timetables-generated'],
    queryFn: () => planningApi.timetable.generated.list(),
    enabled: !!me,
  })
  const published = generated
    .filter((g) => g.status === 'PUBLISHED')
    .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))[0]

  const { data: timetable, isLoading: loadingTimetable } = useQuery({
    queryKey: ['timetable-me', published?.jobId, me?.codeEnseignant],
    queryFn: () => planningApi.timetable.views.byTeacher(published!.jobId, me!.codeEnseignant),
    enabled: !!published && !!me,
  })

  if (isLoading) {
    return <div className="h-64 animate-pulse bg-brand-bgSecondary dark:bg-slate-800 rounded-xl" />
  }

  if (isError || !me) {
    const message =
      (error as { response?: { data?: { message?: string } } } | null)?.response?.data?.message ??
      "Aucune fiche enseignant n'est liée à votre compte. Demandez à l'administration de créer votre fiche dans Gestion des enseignants."
    return (
      <div className="space-y-6">
        <PageHeader title="Mon espace" subtitle="Espace enseignant" />
        <div className="bg-yellow-50 dark:bg-amber-500/10 border border-yellow-200 dark:border-amber-500/20 rounded-xl p-6 text-sm text-yellow-800 dark:text-amber-300">
          {message}
        </div>
      </div>
    )
  }

  const affectations = me.affectations ?? []
  const affectationsActives = affectations.filter((a) => a.isActive)

  const columns: Column<TeacherAssignment>[] = [
    { key: 'nomClasse', header: 'Classe', render: (a) => <Badge variant="default">{a.nomClasse}</Badge> },
    { key: 'nomMatiere', header: 'Matière', render: (a) => <span className="text-sm">{a.nomMatiere}</span> },
    { key: 'typeSeance', header: 'Type de séance', render: (a) => SESSION_TYPE_LABELS[a.typeSeance] ?? a.typeSeance },
    { key: 'isActive', header: 'Statut', render: (a) => <Badge variant={a.isActive ? 'success' : 'danger'}>{a.isActive ? 'Active' : 'Inactive'}</Badge> },
  ]

  return (
    <div className="space-y-6">
      <PageHeader title="Mon espace" subtitle={`${me.nomComplet} — ${me.specialite || 'Enseignant'}`} />

      {/* Fiche */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5 print:hidden">
        <div className="flex flex-wrap items-center gap-x-8 gap-y-2">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-brand-bgSecondary dark:bg-slate-800 flex items-center justify-center">
              <UserCircle size={22} className="text-brand-textMuted dark:text-slate-400" />
            </div>
            <div>
              <p className="text-sm font-semibold text-brand-text dark:text-slate-100">{me.nomComplet}</p>
              <p className="text-xs text-brand-textMuted dark:text-slate-400">Code : {me.codeEnseignant}</p>
            </div>
          </div>
          {me.email && (
            <span className="flex items-center gap-1.5 text-sm text-brand-textMuted dark:text-slate-400">
              <Mail size={14} /> {me.email}
            </span>
          )}
          {me.telephone && (
            <span className="flex items-center gap-1.5 text-sm text-brand-textMuted dark:text-slate-400">
              <Phone size={14} /> {me.telephone}
            </span>
          )}
          <Badge variant={me.estEnPoste ? 'success' : 'danger'}>{me.estEnPoste ? 'En poste' : 'Hors poste'}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3 print:hidden">
        <StatCard title="Affectations actives" value={affectationsActives.length} icon={ClipboardList} color="blue" />
        <StatCard title="Heures / semaine" value={me.totalHeures ?? '—'} icon={Clock} color="green" />
        <StatCard title="Max heures / semaine" value={me.maxHeuresSemaine ?? '—'} icon={Clock} color="red" />
      </div>

      {/* Affectations */}
      <div className="space-y-2 print:hidden">
        <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Mes classes et matières</h3>
        <DataTable
          columns={columns}
          data={affectations}
          keyField="idTeachingAssignment"
          emptyMessage="Aucune affectation pour le moment — l'administration ne vous a pas encore affecté de classes."
        />
      </div>

      {/* Emploi du temps */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5 print:border-0 print:p-0">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Mon emploi du temps</h3>
          {timetable && (
            <Button variant="outline" size="sm" className="print:hidden" onClick={() => window.print()}>
              <Printer size={14} /> Imprimer
            </Button>
          )}
        </div>
        {!published ? (
          <div className="p-8 text-center">
            <CalendarDays size={32} className="mx-auto text-brand-textMuted dark:text-slate-400 mb-3" />
            <p className="text-sm text-brand-textMuted dark:text-slate-400">
              Aucun emploi du temps publié pour le moment. Il apparaîtra ici dès que l'administration l'aura publié.
            </p>
          </div>
        ) : loadingTimetable ? (
          <div className="h-64 animate-pulse bg-brand-bgSecondary dark:bg-slate-800 rounded-xl" />
        ) : timetable ? (
          <TimetableGrid jobId={published.jobId} view={timetable} canEdit={false} onChanged={() => {}} />
        ) : (
          <p className="text-sm text-brand-textMuted dark:text-slate-400 p-4">
            Aucune séance trouvée pour vous dans l'emploi du temps publié.
          </p>
        )}
      </div>
    </div>
  )
}
