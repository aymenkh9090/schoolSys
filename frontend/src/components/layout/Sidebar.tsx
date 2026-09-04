import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import {
  LayoutDashboard,
  BookOpen,
  Building2,
  CreditCard,
  GraduationCap,
  ClipboardList,
  BarChart3,
  Settings,
  UserCheck,
  TableProperties,
  CircleUser,
  FileBarChart,
  SlidersHorizontal,
  Activity,
} from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { tenantApi } from '@/api/tenant.api'
import { cn } from '@/lib/utils'
import { SidebarHeader } from './sidebar/SidebarHeader'
import { SidebarSection } from './sidebar/SidebarSection'
import { SidebarItem } from './sidebar/SidebarItem'
import { SidebarFooter } from './sidebar/SidebarFooter'

interface NavLinkItem {
  key: string
  label: string
  to: string
  /** Groupe parent (ex. "Absences"), pour le contexte affiché et la recherche dans la command palette. */
  group?: string
}

interface NavGroup {
  key: string
  label: string
  icon: React.ElementType
  children: NavLinkItem[]
}

interface NavSingle {
  key: string
  label: string
  to: string
  icon: React.ElementType
}

type NavEntry = NavGroup | NavSingle

function isGroup(entry: NavEntry): entry is NavGroup {
  return 'children' in entry
}

function buildSchoolAdminNav(t: TFunction): NavEntry[] {
  return [
    { key: 'dashboard', label: t('nav.dashboard'), to: '/ecole', icon: LayoutDashboard },
    {
      key: 'establishment',
      label: t('nav.establishment'),
      icon: Building2,
      children: [{ key: 'schoolYears', label: t('nav.schoolYears'), to: '/ecole/annees' }],
    },
    {
      key: 'configuration',
      label: t('nav.configuration'),
      icon: Settings,
      children: [
        { key: 'configOverview', label: t('nav.overview'), to: '/ecole/configuration' },
        { key: 'schedule', label: t('nav.schedule'), to: '/ecole/configuration/horaires' },
        { key: 'nationalProgram', label: t('nav.nationalProgram'), to: '/ecole/programme-national' },
        { key: 'schoolProgram', label: t('nav.schoolProgram'), to: '/ecole/programme-ecole' },
        { key: 'rooms', label: t('nav.rooms'), to: '/ecole/salles' },
        { key: 'constraints', label: t('nav.constraints'), to: '/ecole/planning/contraintes' },
      ],
    },
    { key: 'settings', label: t('nav.settings'), to: '/ecole/parametres', icon: SlidersHorizontal },
    {
      key: 'academic',
      label: t('nav.academic'),
      icon: GraduationCap,
      children: [
        { key: 'academicOverview', label: t('nav.overview'), to: '/ecole/academique' },
        { key: 'levels', label: t('nav.levels'), to: '/ecole/niveaux' },
        { key: 'subjects', label: t('nav.subjects'), to: '/ecole/matieres' },
        { key: 'classes', label: t('nav.classes'), to: '/ecole/classes' },
        { key: 'teachers', label: t('nav.teachers'), to: '/ecole/enseignants' },
        { key: 'students', label: t('nav.students'), to: '/ecole/eleves' },
        { key: 'assignments', label: t('nav.assignments'), to: '/ecole/affectations' },
      ],
    },
    { key: 'users', label: t('nav.users'), to: '/ecole/utilisateurs', icon: UserCheck },
    {
      key: 'planning',
      label: t('nav.planning'),
      icon: TableProperties,
      children: [
        { key: 'planningOverview', label: t('nav.overview'), to: '/ecole/planning' },
        { key: 'planningConsultation', label: t('nav.planningConsultation'), to: '/ecole/planning/consultation' },
        { key: 'planningGenerate', label: t('nav.planningGenerate'), to: '/ecole/planning/generer' },
        { key: 'planningAssistant', label: t('nav.planningAssistant'), to: '/ecole/planning/assistant' },
      ],
    },
    {
      key: 'absences',
      label: t('nav.absences'),
      icon: ClipboardList,
      children: [
        { key: 'rollcall', label: t('nav.rollcall'), to: '/ecole/absences/appel' },
        { key: 'absencesOfDay', label: t('nav.absencesOfDay'), to: '/ecole/absences/journee' },
        { key: 'classLog', label: t('nav.classLog'), to: '/ecole/absences/cahier' },
        { key: 'teachingProgress', label: t('nav.teachingProgress'), to: '/ecole/absences/pilotage' },
        { key: 'justifications', label: t('nav.justifications'), to: '/ecole/absences/justificatifs' },
        { key: 'absenceStats', label: t('nav.statistics'), to: '/ecole/absences/stats' },
      ],
    },
    { key: 'statistics', label: t('nav.statistics'), to: '/ecole/statistiques', icon: BarChart3 },
    { key: 'reports', label: t('nav.reports'), to: '/ecole/rapports', icon: FileBarChart },
  ]
}

function buildSuperAdminNav(t: TFunction): NavEntry[] {
  return [
    { key: 'dashboard', label: t('nav.dashboard'), to: '/super-admin', icon: LayoutDashboard },
    { key: 'establishments', label: t('nav.establishments'), to: '/super-admin/etablissements', icon: Building2 },
    { key: 'subscriptions', label: t('nav.subscriptions'), to: '/super-admin/abonnements', icon: CreditCard },
    { key: 'monitoring', label: t('nav.monitoring'), to: '/super-admin/monitoring', icon: Activity },
  ]
}

/**
 * Vie scolaire : suivi des absences élèves et consultation (en lecture
 * seule) de l'emploi du temps publié.
 */
function buildSurveillantNav(t: TFunction): NavEntry[] {
  return [
    { key: 'dashboard', label: t('nav.dashboard'), to: '/ecole', icon: LayoutDashboard },
    {
      key: 'absences',
      label: t('nav.absences'),
      icon: ClipboardList,
      children: [
        { key: 'absencesOfDay', label: t('nav.absencesOfDay'), to: '/ecole/absences/journee' },
        { key: 'justifications', label: t('nav.justifications'), to: '/ecole/absences/justificatifs' },
        { key: 'rollcall', label: t('nav.rollcall'), to: '/ecole/absences/appel' },
        { key: 'absenceStats', label: t('nav.statistics'), to: '/ecole/absences/stats' },
      ],
    },
    { key: 'planningView', label: t('nav.planningView'), to: '/ecole/planning/consultation', icon: TableProperties },
  ]
}

function buildTeacherNav(t: TFunction): NavEntry[] {
  return [
    { key: 'dashboard', label: t('nav.dashboard'), to: '/ecole', icon: LayoutDashboard },
    { key: 'mySpace', label: t('nav.mySpace'), to: '/ecole/mon-espace', icon: CircleUser },
    // Planning personnel de l'enseignant connecté, et non la consultation
    // globale (toutes classes / tous enseignants) réservée à l'administration.
    { key: 'teacherPlanning', label: t('nav.teacherPlanning'), to: '/ecole/mon-planning', icon: TableProperties },
    { key: 'myClasses', label: t('nav.myClasses'), to: '/ecole/mes-classes', icon: GraduationCap },
    { key: 'rollcall', label: t('nav.rollcall'), to: '/ecole/absences/appel', icon: ClipboardList },
    { key: 'myClassLog', label: t('nav.myClassLog'), to: '/ecole/mon-cahier', icon: BookOpen },
    { key: 'settings', label: t('nav.settings'), to: '/ecole/parametres', icon: SlidersHorizontal },
  ]
}

/** Aplatit la nav (toutes destinations, groupes et liens seuls confondus). Utilisé par la Sidebar et la CommandPalette. */
export function getFlatNavEntries(nav: NavEntry[]): NavLinkItem[] {
  return nav.flatMap((entry) =>
    isGroup(entry)
      ? entry.children.map((c) => ({ ...c, group: entry.label }))
      : [{ key: entry.key, label: entry.label, to: entry.to }]
  )
}

export function useSchoolNav(): NavEntry[] {
  const { t } = useTranslation()
  const { isSuperAdmin, isSchoolAdmin, isSurveillant } = useAuth()
  return isSuperAdmin
    ? buildSuperAdminNav(t)
    : isSchoolAdmin
    ? buildSchoolAdminNav(t)
    : isSurveillant
    ? buildSurveillantNav(t)
    : buildTeacherNav(t)
}

/**
 * Regroupement purement visuel des entrées sous des catégories (CONFIGURATION, GESTION SCOLAIRE...).
 * Ne modifie ni les routes ni la structure de données de la navigation — seulement son rendu par sections.
 */
const CATEGORY_BY_KEY: Record<string, string> = {
  dashboard: 'nav.categoryMain',
  mySpace: 'nav.categoryMain',
  establishments: 'nav.categoryMain',
  establishment: 'nav.categoryConfiguration',
  configuration: 'nav.categoryConfiguration',
  settings: 'nav.categoryConfiguration',
  academic: 'nav.categoryAcademic',
  users: 'nav.categoryAcademic',
  myClasses: 'nav.categoryAcademic',
  planning: 'nav.categoryPlanning',
  teacherPlanning: 'nav.categoryPlanning',
  planningView: 'nav.categoryPlanning',
  absences: 'nav.categoryFollowup',
  rollcall: 'nav.categoryFollowup',
  myClassLog: 'nav.categoryFollowup',
  statistics: 'nav.categoryAnalytics',
  reports: 'nav.categoryAnalytics',
}

interface NavSection {
  title?: string
  entries: NavEntry[]
}

function groupIntoSections(nav: NavEntry[], t: TFunction): NavSection[] {
  const sections: NavSection[] = []
  for (const entry of nav) {
    const categoryKey = CATEGORY_BY_KEY[entry.key]
    const title = categoryKey ? t(categoryKey) : undefined
    const last = sections[sections.length - 1]
    if (last && last.title === title) {
      last.entries.push(entry)
    } else {
      sections.push({ title, entries: [entry] })
    }
  }
  return sections
}

interface Props {
  open: boolean
  onClose: () => void
  collapsed: boolean
  onToggleCollapsed: () => void
}

export function Sidebar({ open, onClose, collapsed, onToggleCollapsed }: Props) {
  const { t } = useTranslation()
  const nav = useSchoolNav()
  const location = useLocation()
  const navigate = useNavigate()
  const { isSuperAdmin } = useAuth()

  const { data: tenant } = useQuery({
    queryKey: ['tenant-me'],
    queryFn: () => tenantApi.me(),
    enabled: !isSuperAdmin,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  const activeGroupKey = nav.find(
    (entry) => isGroup(entry) && entry.children.some((c) => location.pathname.startsWith(c.to))
  )?.key

  const [expanded, setExpanded] = useState<Set<string>>(new Set(activeGroupKey ? [activeGroupKey] : []))

  // Ré-ouvre automatiquement le groupe actif lors d'une navigation (ex. via la command palette).
  useEffect(() => {
    if (activeGroupKey) {
      setExpanded((prev) => (prev.has(activeGroupKey) ? prev : new Set(prev).add(activeGroupKey)))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeGroupKey])

  const toggleGroup = (key: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const sections = groupIntoSections(nav, t)

  return (
    <>
      {/* Overlay mobile, avec transition d'opacité pour une ouverture douce */}
      <div
        className={cn(
          'fixed inset-0 z-30 bg-black/40 transition-opacity duration-300 lg:hidden',
          open ? 'opacity-100' : 'pointer-events-none opacity-0'
        )}
        onClick={onClose}
      />

      {/* z-35 : au-dessus de l'overlay mobile (z-30) mais sous la topbar (z-40), qui doit rester cliquable en permanence. */}
      <aside
        className={cn(
          'fixed left-0 z-[35] flex flex-col bg-sidebar-bg transition-[transform,width] duration-300 ease-out dark:bg-slate-900',
          'border-r border-sidebar-border dark:border-slate-800',
          'rtl:left-auto rtl:right-0',
          open ? 'translate-x-0' : '-translate-x-full lg:translate-x-0 rtl:translate-x-full rtl:lg:translate-x-0'
        )}
        style={{
          width: collapsed ? 'var(--sidebar-width-collapsed)' : 'var(--sidebar-width)',
          top: 'var(--topbar-height)',
          height: 'calc(100% - var(--topbar-height))',
        }}
      >
        <SidebarHeader
          name={tenant?.name ?? 'SchoolSys'}
          subtitle={t('nav.establishment')}
          logo={tenant?.logo ?? undefined}
          collapsed={collapsed}
          showMenu={!isSuperAdmin}
        />

        <nav className="flex-1 space-y-8 overflow-y-auto overflow-x-hidden px-3 pb-4">
          {sections.map((section, i) => (
            <SidebarSection key={section.title ?? `section-${i}`} title={section.title} collapsed={collapsed}>
              {section.entries.map((entry) => {
                if (isGroup(entry)) {
                  const isOpen = expanded.has(entry.key)
                  const groupActive = entry.children.some((c) => location.pathname.startsWith(c.to))
                  return (
                    <div key={entry.key}>
                      <SidebarItem
                        icon={entry.icon}
                        label={entry.label}
                        collapsed={collapsed}
                        active={groupActive}
                        expandable
                        expanded={isOpen}
                        onClick={() => (collapsed ? navigate(entry.children[0].to) : toggleGroup(entry.key))}
                      />
                      {!collapsed && isOpen && (
                        <div className="mt-1 space-y-1">
                          {entry.children.map((child) => (
                            <SidebarItem
                              key={child.to}
                              to={child.to}
                              end
                              indent
                              label={child.label}
                              onClick={onClose}
                            />
                          ))}
                        </div>
                      )}
                    </div>
                  )
                }

                return (
                  <SidebarItem
                    key={entry.to}
                    to={entry.to}
                    end={entry.to === '/ecole' || entry.to === '/super-admin'}
                    icon={entry.icon}
                    label={entry.label}
                    collapsed={collapsed}
                    onClick={onClose}
                  />
                )
              })}
            </SidebarSection>
          ))}
        </nav>

        <SidebarFooter collapsed={collapsed} onToggle={onToggleCollapsed} />
      </aside>
    </>
  )
}
