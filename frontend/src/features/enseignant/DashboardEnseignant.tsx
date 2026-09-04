import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BarChart, Bar, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { BookOpen, CalendarDays, ClipboardList, Clock, GraduationCap, LayoutGrid, Users } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { SectionTitle } from '@/components/ui/SectionTitle'
import { StatCard } from '@/components/ui/StatCard'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { QuickActions } from '@/features/dashboard/QuickActions'
import { organisationApi } from '@/api/organisation.api'
import { absenceApi } from '@/api/absence.api'
import { useAuth } from '@/hooks/useAuth'
import { SeancesDuJour } from './SeancesDuJour'
import { DAY_LABELS, WEEK_ORDER, sessionsOfDay, todayIso, useMonPlanning } from './useMonPlanning'

function dateLongue(): string {
  return new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
}

/**
 * Tableau de bord de l'enseignant connecté : uniquement ses propres données
 * (ses cours du jour, ses classes, ses matières, son planning), et non les
 * statistiques de l'établissement réservées à l'administration.
 */
export default function DashboardEnseignant() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { me, meLoading, meError, meErrorMessage, published, timetable, todaySessions } = useMonPlanning()

  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const anneeCourante = years.find((y) => y.estCourante)

  // Appels déjà ouverts aujourd'hui par cet enseignant : indique ce qu'il reste à faire.
  const { data: appelsDuJour = [] } = useQuery({
    queryKey: ['appel-sessions', { date: todayIso(), enseignantId: me?.idEnseignant }],
    queryFn: () => absenceApi.appel.lister({ date: todayIso(), enseignantId: me!.idEnseignant }),
    enabled: !!me,
  })

  if (meLoading) {
    return <div className="h-64 animate-pulse rounded-xl bg-brand-bgSecondary dark:bg-slate-800" />
  }

  if (meError || !me) {
    return (
      <div className="space-y-6">
        <PageHero title={`Bienvenue ${user?.given_name ?? ''}`.trim()} subtitle="Espace enseignant" />
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-6 text-sm text-yellow-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300">
          {meErrorMessage ??
            "Aucune fiche enseignant n'est liée à votre compte. Demandez à l'administration de créer votre fiche dans Gestion des enseignants."}
        </div>
      </div>
    )
  }

  const affectations = (me.affectations ?? []).filter((a) => a.isActive)
  const mesClasses = Array.from(new Set(affectations.map((a) => a.nomClasse)))
  const mesMatieres = Array.from(new Set(affectations.map((a) => a.nomMatiere)))
  const classesDuJour = Array.from(new Set(todaySessions.map((s) => s.classCode).filter(Boolean)))

  const seancesParJour = WEEK_ORDER.map((day) => ({
    day,
    label: DAY_LABELS[day].slice(0, 3),
    count: sessionsOfDay(timetable, day).length,
  })).filter((d) => d.count > 0)

  const totalSeancesSemaine = seancesParJour.reduce((s, d) => s + d.count, 0)

  return (
    <div className="space-y-8">
      <PageHero
        title={`Bienvenue ${user?.given_name ?? me.nom}`.trim()}
        subtitle={`${dateLongue()}${anneeCourante ? ` · Année ${anneeCourante.nom}` : ''}`}
        actions={
          <Button variant="outline" onClick={() => navigate('/ecole/mon-planning')}>
            <CalendarDays size={16} /> Mon planning
          </Button>
        }
      />

      <QuickActions />

      {/* KPIs — strictement les chiffres de l'enseignant */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard
          title="Cours aujourd'hui"
          value={todaySessions.length}
          icon={CalendarDays}
          color="blue"
          trend={classesDuJour.length > 0 ? { value: classesDuJour.length, label: 'classe(s) vues' } : undefined}
        />
        <StatCard title="Mes classes" value={mesClasses.length} icon={Users} color="green" />
        <StatCard title="Mes matières" value={mesMatieres.length} icon={BookOpen} color="purple" />
        <StatCard title="Heures / semaine" value={me.totalHeures ?? '—'} icon={Clock} color="amber" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Cours du jour + appel web */}
        <div className="lg:col-span-2">
          <SeancesDuJour />
        </div>

        {/* Appels du jour */}
        <div className="rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <SectionTitle
            title="Appels du jour"
            icon={ClipboardList}
            accent="amber"
            action={
              <span className="text-sm font-bold text-brand-text dark:text-slate-100">
                {appelsDuJour.length}/{todaySessions.length}
              </span>
            }
          />
          {todaySessions.length === 0 ? (
            <p className="py-8 text-center text-sm text-brand-textMuted dark:text-slate-400">Aucun cours aujourd'hui.</p>
          ) : (
            <>
              <div className="mb-4 h-2 w-full rounded-full bg-brand-bgSecondary dark:bg-slate-800">
                <div
                  className="h-2 rounded-full bg-brand-blue transition-all"
                  style={{ width: `${Math.min(100, (appelsDuJour.length / todaySessions.length) * 100)}%` }}
                />
              </div>
              {appelsDuJour.length === 0 ? (
                <p className="text-sm text-brand-textMuted dark:text-slate-400">
                  Aucun appel ouvert pour l'instant. Utilisez « Faire l'appel » sur la séance concernée.
                </p>
              ) : (
                <div className="space-y-2">
                  {appelsDuJour.map((a) => {
                    const presents = a.lignesAppel?.filter((l) => l.statut === 'PRESENT').length ?? 0
                    const absents = a.lignesAppel?.filter((l) => l.statut === 'ABSENT').length ?? 0
                    return (
                      <div
                        key={a.id}
                        className="flex items-center justify-between gap-2 rounded-lg border border-brand-border p-2.5 dark:border-slate-700"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-xs font-medium text-brand-text dark:text-slate-100">
                            {new Date(a.ouvertureAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                            {' · '}
                            {a.lignesAppel?.length ?? 0} élève(s)
                          </p>
                          <p className="text-[11px] text-brand-textMuted dark:text-slate-400">
                            {presents} présent(s) · {absents} absent(s)
                          </p>
                        </div>
                        <Badge variant={a.estVerrouille ? 'default' : 'success'}>
                          {a.estVerrouille ? 'Clôturé' : 'Ouvert'}
                        </Badge>
                      </div>
                    )
                  })}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Charge hebdomadaire */}
        <div className="rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <SectionTitle
            title="Ma semaine"
            icon={LayoutGrid}
            accent="blue"
            action={<span className="text-xs text-brand-textMuted dark:text-slate-400">{totalSeancesSemaine} séance(s)</span>}
          />
          {!published ? (
            <p className="py-10 text-center text-sm text-brand-textMuted dark:text-slate-400">
              Emploi du temps non publié.
            </p>
          ) : seancesParJour.length === 0 ? (
            <p className="py-10 text-center text-sm text-brand-textMuted dark:text-slate-400">
              Aucune séance dans l'emploi du temps publié.
            </p>
          ) : (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={seancesParJour} margin={{ left: -20, right: 8, top: 8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="currentColor" className="text-brand-border dark:text-slate-700" />
                <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip formatter={(v) => [`${v} séance(s)`, 'Séances']} />
                <Bar dataKey="count" fill="#3b82f6" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Mes classes & matières */}
        <div className="rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900 lg:col-span-2">
          <SectionTitle
            title="Mes classes et matières"
            icon={GraduationCap}
            accent="emerald"
            action={
              <Button size="sm" variant="outline" onClick={() => navigate('/ecole/mes-classes')}>
                Voir le détail
              </Button>
            }
          />
          {affectations.length === 0 ? (
            <p className="py-10 text-center text-sm text-brand-textMuted dark:text-slate-400">
              Aucune affectation active — l'administration ne vous a pas encore affecté de classes.
            </p>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {mesClasses.map((classe) => {
                const matieres = affectations.filter((a) => a.nomClasse === classe).map((a) => a.nomMatiere)
                return (
                  <div key={classe} className="rounded-lg border border-brand-border p-3 dark:border-slate-700">
                    <p className="text-sm font-semibold text-brand-text dark:text-slate-100">{classe}</p>
                    <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">
                      {Array.from(new Set(matieres)).join(' · ')}
                    </p>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
