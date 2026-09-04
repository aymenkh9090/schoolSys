import { cn } from '@/lib/utils'

export interface TabItem<T extends string = string> {
  key: T
  label: string
  icon?: React.ElementType
}

interface TabsProps<T extends string> {
  tabs: TabItem<T>[]
  value: T
  onChange: (key: T) => void
  className?: string
}

/** Barre d'onglets segmentée (pastille blanche sur l'onglet actif). */
export function Tabs<T extends string>({ tabs, value, onChange, className }: TabsProps<T>) {
  return (
    <div
      className={cn(
        'flex gap-1 overflow-x-auto rounded-xl border border-brand-border bg-brand-bgSecondary p-1',
        'dark:border-slate-700 dark:bg-slate-800/60',
        className
      )}
      role="tablist"
    >
      {tabs.map((tab) => {
        const active = tab.key === value
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(tab.key)}
            className={cn(
              'flex flex-1 items-center justify-center gap-2 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              active
                ? 'bg-white text-brand-teal shadow-sm dark:bg-slate-900 dark:text-teal-300'
                : 'text-brand-textMuted hover:text-brand-text dark:text-slate-400 dark:hover:text-slate-200'
            )}
          >
            {tab.icon && <tab.icon size={15} className="shrink-0" />}
            {tab.label}
          </button>
        )
      })}
    </div>
  )
}
