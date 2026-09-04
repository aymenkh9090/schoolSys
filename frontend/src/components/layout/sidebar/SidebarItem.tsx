import { NavLink } from 'react-router-dom'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'

interface SidebarItemProps {
  icon?: React.ElementType
  label: string
  to?: string
  end?: boolean
  onClick?: () => void
  /** État actif pour la variante bouton (groupe repliable) — ignoré si `to` est fourni (NavLink gère son propre état). */
  active?: boolean
  collapsed?: boolean
  /** Lien enfant d'un groupe : pas d'icône, texte indenté. */
  indent?: boolean
  expandable?: boolean
  expanded?: boolean
}

/** Ligne de navigation générique : rend un NavLink (destination réelle) ou un bouton (bascule de groupe). */
export function SidebarItem({
  icon: Icon,
  label,
  to,
  end,
  onClick,
  active = false,
  collapsed = false,
  indent = false,
  expandable = false,
  expanded = false,
}: SidebarItemProps) {
  const rowBase = cn(
    'group relative flex items-center h-11 rounded-xl transition-colors duration-200 w-full',
    collapsed ? 'w-11 mx-auto justify-center' : indent ? 'gap-3 pl-11 pr-3 rtl:pl-3 rtl:pr-11' : 'gap-3 px-3'
  )

  function renderContent(isActive: boolean) {
    return (
      <>
        {isActive && !collapsed && (
          <span className="absolute left-0 rtl:left-auto rtl:right-0 top-1.5 bottom-1.5 w-[3px] rounded-full bg-sidebar-activeBar dark:bg-blue-400" />
        )}
        {Icon && !indent && (
          <Icon
            size={19}
            strokeWidth={2}
            className={cn(
              'shrink-0 transition-colors duration-200',
              isActive
                ? 'text-sidebar-activeBar dark:text-blue-400'
                : 'text-sidebar-textMuted dark:text-slate-400 group-hover:text-sidebar-text dark:group-hover:text-slate-200'
            )}
          />
        )}
        {!collapsed && (
          <span
            className={cn(
              'flex-1 truncate text-left rtl:text-right text-[15px]',
              isActive ? 'font-semibold text-sidebar-activeText dark:text-slate-100' : 'font-medium text-sidebar-text dark:text-slate-300'
            )}
          >
            {label}
          </span>
        )}
        {expandable && !collapsed && (
          <ChevronDown
            size={15}
            strokeWidth={2}
            className={cn(
              'shrink-0 text-sidebar-textMuted dark:text-slate-500 transition-transform duration-200',
              expanded && 'rotate-180'
            )}
          />
        )}
      </>
    )
  }

  if (to) {
    return (
      <NavLink
        to={to}
        end={end}
        onClick={onClick}
        title={collapsed ? label : undefined}
        className={({ isActive }) =>
          cn(
            rowBase,
            isActive ? 'bg-sidebar-activeBg dark:bg-blue-500/15' : 'hover:bg-sidebar-hover dark:hover:bg-slate-800/60'
          )
        }
      >
        {({ isActive }) => renderContent(isActive)}
      </NavLink>
    )
  }

  return (
    <button
      type="button"
      onClick={onClick}
      title={collapsed ? label : undefined}
      className={cn(rowBase, 'hover:bg-sidebar-hover dark:hover:bg-slate-800/60')}
    >
      {renderContent(active)}
    </button>
  )
}
