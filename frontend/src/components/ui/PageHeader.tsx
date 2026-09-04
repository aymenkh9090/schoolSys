import { cn } from '@/lib/utils'

interface PageHeaderProps {
  title: string
  description?: string
  /** Alias de description (utilisé par certaines pages). */
  subtitle?: string
  actions?: React.ReactNode
  className?: string
}

export function PageHeader({ title, description, subtitle, actions, className }: PageHeaderProps) {
  description = description ?? subtitle
  return (
    <div className={cn('flex items-start justify-between mb-6', className)}>
      <div>
        <h1 className="text-xl font-bold text-brand-text dark:text-slate-100">{title}</h1>
        {description && (
          <p className="text-sm text-brand-textMuted dark:text-slate-400 mt-1">{description}</p>
        )}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0 ml-4">{actions}</div>}
    </div>
  )
}
