import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  BookOpen,
  CalendarDays,
  ClipboardList,
  FileBarChart,
  GraduationCap,
  SlidersHorizontal,
  UserPlus,
  Users,
  Zap,
} from 'lucide-react'

import { SectionTitle } from '@/components/ui/SectionTitle'
import { useAuth } from '@/hooks/useAuth'
import { cn } from '@/lib/utils'

type Tone = 'teal' | 'blue' | 'amber' | 'purple' | 'emerald'

const tones: Record<Tone, string> = {
  teal: 'bg-teal-600 text-white',
  blue: 'bg-blue-500 text-white',
  amber: 'bg-amber-500 text-white',
  purple: 'bg-purple-500 text-white',
  emerald: 'bg-emerald-500 text-white',
}

interface QuickAction {
  key: string
  label: string
  description: string
  to: string
  icon: React.ElementType
  tone: Tone
}

const ADMIN_ACTIONS: QuickAction[] = [
  { key: 'newStudent', label: 'Nouvel élève', description: 'Ajouter un élève au système', to: '/ecole/eleves', icon: UserPlus, tone: 'teal' },
  { key: 'classes', label: 'Gestion des classes', description: 'Organiser classes et affectations', to: '/ecole/classes', icon: BookOpen, tone: 'teal' },
  { key: 'timetable', label: 'Emploi du temps', description: 'Consulter et modifier les emplois du temps', to: '/ecole/planning', icon: CalendarDays, tone: 'amber' },
  { key: 'teachers', label: 'Enseignants', description: 'Gérer le personnel enseignant', to: '/ecole/enseignants', icon: Users, tone: 'teal' },
  { key: 'reports', label: 'Rapports', description: 'Consulter rapports et statistiques', to: '/ecole/rapports', icon: FileBarChart, tone: 'purple' },
  { key: 'rollcall', label: 'Appel & absences', description: "Ouvrir les sessions d'appel", to: '/ecole/absences/appel', icon: ClipboardList, tone: 'amber' },
  { key: 'settings', label: 'Paramètres', description: "Configuration de l'école", to: '/ecole/parametres', icon: SlidersHorizontal, tone: 'emerald' },
]

const SURVEILLANT_ACTIONS: QuickAction[] = [
  { key: 'absencesOfDay', label: 'Absences du jour', description: 'Absences, retards et exclusions', to: '/ecole/absences/journee', icon: ClipboardList, tone: 'teal' },
  { key: 'justifications', label: 'Justificatifs', description: 'Traiter les justificatifs élèves', to: '/ecole/absences/justificatifs', icon: GraduationCap, tone: 'amber' },
  { key: 'planningView', label: 'Emploi du temps', description: 'Classes, enseignants et salles', to: '/ecole/planning/consultation', icon: CalendarDays, tone: 'purple' },
  { key: 'rollcall', label: "Sessions d'appel", description: "Parcourir et clôturer les appels", to: '/ecole/absences/appel', icon: ClipboardList, tone: 'emerald' },
  { key: 'absenceStats', label: 'Statistiques', description: "Taux d'absentéisme de l'établissement", to: '/ecole/absences/stats', icon: FileBarChart, tone: 'purple' },
]

const TEACHER_ACTIONS: QuickAction[] = [
  { key: 'mySpace', label: 'Mon espace', description: 'Ma fiche et mes affectations', to: '/ecole/mon-espace', icon: GraduationCap, tone: 'teal' },
  { key: 'myPlanning', label: 'Mon planning', description: 'Mon emploi du temps personnel', to: '/ecole/mon-planning', icon: CalendarDays, tone: 'amber' },
  { key: 'rollcall', label: 'Appel', description: "Faire l'appel de mes séances", to: '/ecole/absences/appel', icon: ClipboardList, tone: 'blue' },
  { key: 'myClasses', label: 'Mes classes', description: 'Mes classes et leurs élèves', to: '/ecole/mes-classes', icon: Users, tone: 'emerald' },
  { key: 'myClassLog', label: 'Cahier de classe', description: 'Contenu et travail demandé', to: '/ecole/mon-cahier', icon: BookOpen, tone: 'purple' },
]

/** Nombre d'actions affichées tant que « Voir tout » n'a pas été activé. */
const PREVIEW_COUNT = 4

export function QuickActions() {
  const navigate = useNavigate()
  const { isSchoolAdmin, isSurveillant } = useAuth()
  const [expanded, setExpanded] = useState(false)

  const actions = isSchoolAdmin ? ADMIN_ACTIONS : isSurveillant ? SURVEILLANT_ACTIONS : TEACHER_ACTIONS
  const visible = expanded ? actions : actions.slice(0, PREVIEW_COUNT)
  const hasMore = actions.length > PREVIEW_COUNT

  return (
    <section>
      <SectionTitle
        title="Actions rapides"
        icon={Zap}
        size="lg"
        action={
          hasMore && (
            <button
              onClick={() => setExpanded((v) => !v)}
              className="flex items-center gap-1.5 rounded-lg border border-brand-border bg-white px-3 py-1.5 text-xs font-medium text-brand-text transition-colors hover:bg-brand-bgSecondary dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {expanded ? 'Réduire' : 'Voir tout'}
              <ArrowRight size={13} className={cn('transition-transform', expanded && 'rotate-90')} />
            </button>
          )
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {visible.map((action) => (
          <button
            key={action.key}
            onClick={() => navigate(action.to)}
            className="group flex flex-col items-center gap-3 rounded-2xl border border-brand-border bg-white p-6 text-center transition-all hover:-translate-y-0.5 hover:border-teal-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-900 dark:hover:border-teal-500/40"
          >
            <span className={cn('flex h-14 w-14 items-center justify-center rounded-2xl transition-transform group-hover:scale-105', tones[action.tone])}>
              <action.icon size={24} />
            </span>
            <span className="text-base font-semibold text-brand-text dark:text-slate-100">{action.label}</span>
            <span className="text-xs text-brand-textMuted dark:text-slate-400">{action.description}</span>
          </button>
        ))}
      </div>
    </section>
  )
}
