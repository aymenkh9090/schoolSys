import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueries, useQuery } from '@tanstack/react-query'
import { CheckCircle, Circle, Clock, AlertTriangle, ArrowRight, Settings2 } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { StatCard } from '@/components/ui/StatCard'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { organisationApi } from '@/api/organisation.api'
import { planningApi } from '@/api/planning.api'
import { cn } from '@/lib/utils'

interface ModuleStatus {
  key: string
  label: string
  to: string
  percent: number
  detail: string
}

function actionLabel(percent: number) {
  if (percent >= 100) return 'Voir'
  if (percent > 0) return 'Continuer'
  return 'Commencer'
}

const CORE_MODULE_KEYS = ['horaires', 'matieres', 'salles', 'programme-ecole', 'affectations']

export default function ConfigurationDashboard() {
  const navigate = useNavigate()

  const { data: config } = useQuery({ queryKey: ['school-config'], queryFn: organisationApi.config.get })
  const { data: nationalPatterns = [] } = useQuery({
    queryKey: ['national-patterns'],
    queryFn: () => organisationApi.nationalPatterns.list('TN'),
  })
  const { data: levels = [] } = useQuery({ queryKey: ['levels'], queryFn: organisationApi.levels.list })
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: organisationApi.subjects.list })
  const { data: rooms = [] } = useQuery({ queryKey: ['rooms'], queryFn: organisationApi.rooms.list })
  const { data: constraintProfiles = [] } = useQuery({
    queryKey: ['constraint-profiles'],
    queryFn: planningApi.constraints.profiles.list,
  })
  const { data: years = [] } = useQuery({ queryKey: ['school-years'], queryFn: organisationApi.schoolYears.list })
  const { data: inconsistants = [] } = useQuery({
    queryKey: ['patterns-inconsistants'],
    queryFn: organisationApi.patterns.inconsistants,
  })

  const currentYear = years.find((y) => y.estCourante)

  const { data: assignments = [] } = useQuery({
    queryKey: ['teaching-assignments-by-year', currentYear?.idAnnee],
    queryFn: () => organisationApi.teachingAssignments.byYear(currentYear!.idAnnee),
    enabled: !!currentYear,
  })
  const { data: missingAssignments = [] } = useQuery({
    queryKey: ['teaching-assignments-missing', currentYear?.idAnnee],
    queryFn: () => organisationApi.teachingAssignments.missing(currentYear!.idAnnee),
    enabled: !!currentYear,
  })

  const subjectLevelQueries = useQueries({
    queries: levels.map((l) => ({
      queryKey: ['subject-levels-by-level', l.idNiveau],
      queryFn: () => organisationApi.subjects.subjectLevels.byLevel(l.idNiveau),
    })),
  })
  const allSubjectLevels = subjectLevelQueries.flatMap((q) => q.data ?? [])
  const withPattern = allSubjectLevels.filter((sl) => (sl.patterns?.length ?? 0) > 0).length
  const schoolPatternPercent = allSubjectLevels.length === 0 ? 0 : Math.round((withPattern / allSubjectLevels.length) * 100)

  const horairesPercent = config?.isReadyForGeneration ? 100 : config?.workingDays?.some((d) => d.active) ? 50 : 0
  const nationalPercent = nationalPatterns.length > 0 ? 100 : 0
  const matieresEnseignees = subjects.filter((s) => s.estEnseignee)
  const matieresPercent = matieresEnseignees.length > 0 ? 100 : 0
  const sallesPercent = rooms.length > 0 ? 100 : 0
  const hasActiveProfile = constraintProfiles.some((p) => p.active)
  const contraintesPercent = hasActiveProfile ? 100 : constraintProfiles.length > 0 ? 50 : 0
  const affectationsPercent = !currentYear || assignments.length === 0 ? 0 : missingAssignments.length === 0 ? 100 : 50

  const modules: ModuleStatus[] = useMemo(
    () => [
      {
        key: 'horaires',
        label: 'Horaires',
        to: '/ecole/configuration/horaires',
        percent: horairesPercent,
        detail: config?.isReadyForGeneration
          ? 'Prêt pour la génération'
          : `${config?.totalSlotsPerWeek ?? 0} créneau${(config?.totalSlotsPerWeek ?? 0) > 1 ? 'x' : ''} par semaine`,
      },
      {
        key: 'programme-national',
        label: 'Programme national',
        to: '/ecole/programme-national',
        percent: nationalPercent,
        detail: `${nationalPatterns.length} programme${nationalPatterns.length > 1 ? 's' : ''} disponible${nationalPatterns.length > 1 ? 's' : ''}`,
      },
      {
        key: 'programme-ecole',
        label: 'Programme école',
        to: '/ecole/programme-ecole',
        percent: schoolPatternPercent,
        detail: `${withPattern} / ${allSubjectLevels.length} matière-niveau avec répartition`,
      },
      {
        key: 'matieres',
        label: 'Matières',
        to: '/ecole/matieres',
        percent: matieresPercent,
        detail: `${matieresEnseignees.length} matière${matieresEnseignees.length > 1 ? 's' : ''} enseignée${matieresEnseignees.length > 1 ? 's' : ''}`,
      },
      {
        key: 'salles',
        label: 'Salles',
        to: '/ecole/salles',
        percent: sallesPercent,
        detail: `${rooms.length} salle${rooms.length > 1 ? 's' : ''}`,
      },
      {
        key: 'contraintes',
        label: 'Contraintes',
        to: '/ecole/planning/contraintes',
        percent: contraintesPercent,
        detail: hasActiveProfile ? 'Profil actif' : constraintProfiles.length > 0 ? 'Aucun profil actif' : 'Aucun profil',
      },
      {
        key: 'affectations',
        label: 'Affectations',
        to: '/ecole/affectations',
        percent: affectationsPercent,
        detail: currentYear
          ? `${assignments.length} affectation${assignments.length > 1 ? 's' : ''}, ${missingAssignments.length} manquante${missingAssignments.length > 1 ? 's' : ''}`
          : 'Aucune année courante',
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [
      horairesPercent, nationalPercent, schoolPatternPercent, matieresPercent, sallesPercent, contraintesPercent, affectationsPercent,
      config, nationalPatterns.length, withPattern, allSubjectLevels.length, matieresEnseignees.length, rooms.length,
      hasActiveProfile, constraintProfiles.length, currentYear, assignments.length, missingAssignments.length,
    ]
  )

  const globalPercent = Math.round(modules.reduce((s, m) => s + m.percent, 0) / modules.length)
  const completedCount = modules.filter((m) => m.percent >= 100).length
  const blockingCount = modules.filter((m) => CORE_MODULE_KEYS.includes(m.key) && m.percent === 0).length
  const warningsCount = inconsistants.length + missingAssignments.length

  const priorities = useMemo(() => {
    const items: { label: string; to: string }[] = []
    if (missingAssignments.length > 0) {
      items.push({
        label: `${missingAssignments.length} affectation${missingAssignments.length > 1 ? 's' : ''} manquante${missingAssignments.length > 1 ? 's' : ''}`,
        to: '/ecole/affectations',
      })
    }
    if (inconsistants.length > 0) {
      items.push({
        label: `${inconsistants.length} répartition${inconsistants.length > 1 ? 's' : ''} avec un écart horaire`,
        to: '/ecole/programme-ecole',
      })
    }
    if (allSubjectLevels.length > 0 && withPattern < allSubjectLevels.length) {
      items.push({
        label: `${allSubjectLevels.length - withPattern} matière-niveau sans répartition`,
        to: '/ecole/programme-ecole',
      })
    }
    if (rooms.length === 0) items.push({ label: 'Aucune salle configurée', to: '/ecole/salles' })
    if (matieresEnseignees.length === 0) items.push({ label: 'Aucune matière enseignée configurée', to: '/ecole/matieres' })
    if (!config?.isReadyForGeneration) items.push({ label: 'Horaires non finalisés', to: '/ecole/configuration/horaires' })
    if (!hasActiveProfile) items.push({ label: 'Aucun profil de contraintes actif', to: '/ecole/planning/contraintes' })
    return items.slice(0, 5)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [missingAssignments.length, inconsistants.length, allSubjectLevels.length, withPattern, rooms.length, matieresEnseignees.length, config, hasActiveProfile])

  return (
    <div className="space-y-6">
      <PageHero
        title="Configuration"
        subtitle="Tout ce qui est nécessaire avant de générer un emploi du temps"
        icon={Settings2}
      />

      {/* Résumé */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard title="Configuration globale" value={`${globalPercent}%`} icon={Settings2} color="blue" />
        <StatCard title="Modules complétés" value={`${completedCount}/${modules.length}`} icon={CheckCircle} color="green" />
        <StatCard title="Bloquants" value={blockingCount} icon={AlertTriangle} color="red" />
        <StatCard title="Avertissements" value={warningsCount} icon={AlertTriangle} color="amber" />
      </div>

      {/* Stepper */}
      <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5 overflow-x-auto">
        <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">Workflow de configuration</h3>
        <div className="flex items-start min-w-max">
          {modules.map((m, i) => (
            <div key={m.key} className="flex items-start">
              <button
                onClick={() => navigate(m.to)}
                className="flex flex-col items-center gap-2 w-28 text-center group"
              >
                <span
                  className={cn(
                    'w-10 h-10 rounded-full flex items-center justify-center transition-colors',
                    m.percent >= 100
                      ? 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-400'
                      : m.percent > 0
                        ? 'bg-blue-100 text-brand-blue dark:bg-blue-500/15'
                        : 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-500'
                  )}
                >
                  {m.percent >= 100 ? <CheckCircle size={18} /> : m.percent > 0 ? <Clock size={18} /> : <Circle size={18} />}
                </span>
                <span className="text-xs font-medium text-brand-text dark:text-slate-200 group-hover:text-brand-blue transition-colors">
                  {m.label}
                </span>
              </button>
              {i < modules.length - 1 && (
                <div className={cn('h-0.5 w-8 shrink-0 mt-5', m.percent >= 100 ? 'bg-emerald-300 dark:bg-emerald-500/40' : 'bg-brand-border dark:bg-slate-700')} />
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* État des modules */}
        <div className="lg:col-span-2 bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">État des modules</h3>
          <div className="space-y-3">
            {modules.map((m) => (
              <div key={m.key} className="flex items-center gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm font-medium text-brand-text dark:text-slate-200">{m.label}</span>
                    <Badge variant={m.percent >= 100 ? 'success' : m.percent > 0 ? 'warning' : 'default'}>
                      {m.percent >= 100 ? 'Complété' : m.percent > 0 ? 'En cours' : 'À faire'}
                    </Badge>
                  </div>
                  <p className="text-xs text-brand-textMuted dark:text-slate-400 mb-1.5">{m.detail}</p>
                  <div className="h-1.5 rounded-full bg-brand-bgSecondary dark:bg-slate-800">
                    <div
                      className={cn('h-1.5 rounded-full', m.percent >= 100 ? 'bg-emerald-500' : 'bg-brand-blue')}
                      style={{ width: `${m.percent}%` }}
                    />
                  </div>
                </div>
                <Button size="sm" variant="outline" onClick={() => navigate(m.to)} className="shrink-0">
                  {actionLabel(m.percent)} <ArrowRight size={12} />
                </Button>
              </div>
            ))}
          </div>
        </div>

        {/* À faire en priorité */}
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-brand-border dark:border-slate-700 p-5">
          <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100 mb-4">À faire en priorité</h3>
          {priorities.length === 0 ? (
            <div className="text-center py-8">
              <CheckCircle size={28} className="mx-auto text-emerald-500 mb-2" />
              <p className="text-sm text-brand-textMuted dark:text-slate-400">Aucune action prioritaire</p>
            </div>
          ) : (
            <div className="space-y-2">
              {priorities.map((p, i) => (
                <button
                  key={i}
                  onClick={() => navigate(p.to)}
                  className="w-full flex items-center gap-2 p-2.5 rounded-lg border border-amber-200 dark:border-amber-500/20 bg-amber-50 dark:bg-amber-500/10 text-left hover:bg-amber-100 dark:hover:bg-amber-500/20 transition-colors"
                >
                  <AlertTriangle size={14} className="text-amber-600 dark:text-amber-400 shrink-0" />
                  <span className="text-xs text-amber-900 dark:text-amber-200 flex-1">{p.label}</span>
                  <ArrowRight size={12} className="text-amber-600 dark:text-amber-400 shrink-0" />
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
