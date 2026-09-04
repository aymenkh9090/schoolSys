import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { School, MoreVertical, Settings, CalendarRange } from 'lucide-react'

interface SidebarHeaderProps {
  name: string
  subtitle: string
  logo?: string
  collapsed?: boolean
  /** N'affiche le menu contextuel que si des raccourcis pertinents existent (ex. pas pour le super admin). */
  showMenu?: boolean
}

export function SidebarHeader({ name, subtitle, logo, collapsed, showMenu = true }: SidebarHeaderProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()

  if (collapsed) {
    return (
      <div className="px-3 pt-4 pb-3">
        <div
          className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl border border-sidebar-border bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800"
          title={name}
        >
          {logo ? (
            <img src={logo} alt={name} className="h-6 w-6 rounded object-cover" />
          ) : (
            <School size={19} className="text-sidebar-activeBar dark:text-blue-400" />
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="px-3 pt-4 pb-3">
      <div className="flex items-center gap-3 rounded-[14px] border border-sidebar-border bg-white px-3.5 py-3 shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-sidebar-activeBg dark:bg-blue-500/15">
          {logo ? (
            <img src={logo} alt={name} className="h-9 w-9 rounded-lg object-cover" />
          ) : (
            <School size={18} className="text-sidebar-activeBar dark:text-blue-400" />
          )}
        </div>

        <div className="min-w-0 flex-1">
          <p className="truncate text-[14px] font-semibold leading-tight text-sidebar-text dark:text-slate-100">{name}</p>
          <p className="truncate text-[13px] font-normal text-sidebar-textMuted dark:text-slate-400">{subtitle}</p>
        </div>

        {showMenu && (
          <DropdownMenu.Root>
            <DropdownMenu.Trigger asChild>
              <button
                type="button"
                title={t('common.moreOptions')}
                className="shrink-0 rounded-md p-1.5 text-sidebar-textMuted transition-colors hover:bg-sidebar-hover hover:text-sidebar-text dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-200"
              >
                <MoreVertical size={16} />
              </button>
            </DropdownMenu.Trigger>
            <DropdownMenu.Portal>
              <DropdownMenu.Content
                align="start"
                sideOffset={6}
                className="z-50 w-56 rounded-xl border border-sidebar-border bg-white py-1.5 shadow-xl dark:border-slate-700 dark:bg-slate-900"
              >
                <DropdownMenu.Item
                  onSelect={() => navigate('/ecole/configuration')}
                  className="mx-1 flex cursor-pointer items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-text outline-none hover:bg-sidebar-hover dark:text-slate-200 dark:hover:bg-slate-800"
                >
                  <Settings size={15} /> {t('nav.configuration')}
                </DropdownMenu.Item>
                <DropdownMenu.Item
                  onSelect={() => navigate('/ecole/annees')}
                  className="mx-1 flex cursor-pointer items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-text outline-none hover:bg-sidebar-hover dark:text-slate-200 dark:hover:bg-slate-800"
                >
                  <CalendarRange size={15} /> {t('nav.schoolYears')}
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
        )}
      </div>
    </div>
  )
}
