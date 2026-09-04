import { PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'

interface SidebarFooterProps {
  collapsed: boolean
  onToggle: () => void
}

/** Bascule de réduction, visible uniquement en desktop (le tiroir mobile se ferme via l'overlay). */
export function SidebarFooter({ collapsed, onToggle }: SidebarFooterProps) {
  const { t } = useTranslation()

  return (
    <div className="hidden shrink-0 border-t border-sidebar-border px-3 py-3 dark:border-slate-800 lg:block">
      <button
        type="button"
        onClick={onToggle}
        title={collapsed ? t('common.expandSidebar') : t('common.collapseSidebar')}
        className={cn(
          'flex h-10 items-center gap-3 rounded-xl text-sidebar-textMuted transition-colors duration-200 hover:bg-sidebar-hover hover:text-sidebar-text dark:text-slate-400 dark:hover:bg-slate-800/60 dark:hover:text-slate-200',
          collapsed ? 'mx-auto w-10 justify-center' : 'w-full px-3'
        )}
      >
        {collapsed ? <PanelLeftOpen size={19} /> : <PanelLeftClose size={19} />}
        {!collapsed && <span className="text-[15px] font-medium">{t('common.collapseSidebar')}</span>}
      </button>
    </div>
  )
}
