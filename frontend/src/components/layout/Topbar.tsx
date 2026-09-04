import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import { Bell, Globe, LogOut, Menu, Search, Sun, Moon, AlertTriangle, Loader2 } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { useTranslation } from 'react-i18next'
import { useThemeStore } from '@/store/themeStore'
import { planningApi } from '@/api/planning.api'
import { cn } from '@/lib/utils'

interface Props {
  onMenuClick: () => void
  onSearchClick: () => void
}

interface NotificationItem {
  id: string
  message: string
  severity: 'info' | 'warning' | 'danger'
  icon: React.ElementType
}

export function Topbar({ onMenuClick, onSearchClick }: Props) {
  const { user, logout, isSchoolAdmin } = useAuth()
  const { t, i18n } = useTranslation()
  const { theme, toggle } = useThemeStore()

  const { data: jobs = [] } = useQuery({
    queryKey: ['timetable-jobs'],
    queryFn: () => planningApi.timetable.jobs.list(),
    enabled: isSchoolAdmin,
  })
  const { data: generated = [] } = useQuery({
    queryKey: ['generated-timetables'],
    queryFn: () => planningApi.timetable.generated.list(),
    enabled: isSchoolAdmin,
  })
  const notifications: NotificationItem[] = useMemo(() => {
    const items: NotificationItem[] = []

    jobs
      .filter((j) => j.status === 'RUNNING')
      .forEach((j) =>
        items.push({ id: `job-${j.jobId}`, message: `Génération de planning en cours (job #${j.jobId})`, severity: 'info', icon: Loader2 })
      )

    generated
      .filter((g) => g.hardViolations > 0 || g.mediumViolations > 0)
      .forEach((g) =>
        items.push({
          id: `gen-${g.id}`,
          message: `${g.hardViolations} conflit(s) critique(s), ${g.mediumViolations} moyen(s) sur le planning #${g.id}`,
          severity: g.hardViolations > 0 ? 'danger' : 'warning',
          icon: AlertTriangle,
        })
      )

    return items
  }, [jobs, generated])

  const severityClasses: Record<NotificationItem['severity'], string> = {
    info: 'text-brand-blue',
    warning: 'text-amber-500',
    danger: 'text-red-500',
  }

  const langs = ['fr', 'ar', 'en']

  return (
    <header
      className="fixed top-0 right-0 left-0 z-40 flex items-center gap-3 justify-between
        bg-white dark:bg-slate-900 border-b border-brand-border dark:border-slate-700 px-4"
      style={{ height: 'var(--topbar-height)' }}
    >
      <button
        onClick={onMenuClick}
        className="p-2 rounded-md hover:bg-brand-bg dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 shrink-0"
      >
        <Menu size={20} />
      </button>

      {/* Recherche / command palette */}
      <button
        onClick={onSearchClick}
        className="hidden sm:flex items-center gap-2 flex-1 max-w-md px-3 py-2 rounded-lg border border-brand-border dark:border-slate-700 bg-brand-bg dark:bg-slate-800 text-brand-textMuted dark:text-slate-400 text-sm hover:border-brand-blue transition-colors"
      >
        <Search size={16} />
        <span className="flex-1 text-left">{t('common.searchPlaceholder')}</span>
        <kbd className="text-[10px] px-1.5 py-0.5 rounded bg-white dark:bg-slate-900 border border-brand-border dark:border-slate-700">
          Ctrl K
        </kbd>
      </button>

      <div className="flex items-center gap-2 ml-auto">
        {/* Language picker */}
        <div className="hidden md:flex items-center gap-1">
          <Globe size={16} className="text-brand-textMuted dark:text-slate-400" />
          {langs.map((l) => (
            <button
              key={l}
              onClick={() => i18n.changeLanguage(l)}
              className={cn(
                'text-xs px-2 py-1 rounded uppercase font-medium transition-colors',
                i18n.language === l
                  ? 'bg-brand-blue text-white'
                  : 'text-brand-textMuted dark:text-slate-400 hover:text-brand-text dark:hover:text-slate-200'
              )}
            >
              {l}
            </button>
          ))}
        </div>

        {/* Theme toggle */}
        <button
          onClick={toggle}
          className="p-2 rounded-md hover:bg-brand-bg dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400"
          title={theme === 'dark' ? t('common.lightMode') : t('common.darkMode')}
        >
          {theme === 'dark' ? <Sun size={20} /> : <Moon size={20} />}
        </button>

        {/* Notifications */}
        <DropdownMenu.Root>
          <DropdownMenu.Trigger asChild>
            <button className="p-2 rounded-md hover:bg-brand-bg dark:hover:bg-slate-800 text-brand-textMuted dark:text-slate-400 relative">
              <Bell size={20} />
              {notifications.length > 0 && (
                <span className="absolute top-1 right-1 min-w-[16px] h-4 px-1 rounded-full bg-danger text-white text-[10px] font-bold flex items-center justify-center">
                  {notifications.length}
                </span>
              )}
            </button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Portal>
            <DropdownMenu.Content
              align="end"
              sideOffset={8}
              className="w-80 max-h-96 overflow-y-auto rounded-xl border border-brand-border dark:border-slate-700 bg-white dark:bg-slate-900 shadow-xl py-2 z-50"
            >
              <div className="px-3 py-1.5 text-xs font-semibold text-brand-textMuted dark:text-slate-400 uppercase">
                {t('common.notifications')}
              </div>
              {notifications.length === 0 ? (
                <p className="px-3 py-6 text-center text-sm text-brand-textMuted dark:text-slate-400">
                  {t('common.noNotifications')}
                </p>
              ) : (
                notifications.map((n) => (
                  <DropdownMenu.Item
                    key={n.id}
                    className="flex items-start gap-2 px-3 py-2 text-sm text-brand-text dark:text-slate-200 outline-none hover:bg-brand-bgSecondary dark:hover:bg-slate-800 cursor-default"
                  >
                    <n.icon size={16} className={cn('mt-0.5 shrink-0', severityClasses[n.severity])} />
                    <span>{n.message}</span>
                  </DropdownMenu.Item>
                ))
              )}
            </DropdownMenu.Content>
          </DropdownMenu.Portal>
        </DropdownMenu.Root>

        {/* User menu */}
        <div className="flex items-center gap-2 pl-2 border-l border-brand-border dark:border-slate-700">
          <div className="text-right hidden sm:block">
            <p className="text-sm font-medium text-brand-text dark:text-slate-200 leading-none">
              {user?.given_name} {user?.family_name}
            </p>
            <p className="text-xs text-brand-textMuted dark:text-slate-400 mt-0.5">
              {(user as Record<string, unknown>)?.preferred_username as string}
            </p>
          </div>
          <div className="w-8 h-8 rounded-full bg-brand-navy text-white flex items-center justify-center text-sm font-semibold shrink-0">
            {((user?.given_name as string)?.[0] ?? '?').toUpperCase()}
          </div>
          <button
            onClick={logout}
            className="p-2 rounded-md hover:bg-red-50 dark:hover:bg-red-500/10 text-brand-textMuted dark:text-slate-400 hover:text-danger transition-colors"
            title={t('auth.logout')}
          >
            <LogOut size={18} />
          </button>
        </div>
      </div>
    </header>
  )
}
