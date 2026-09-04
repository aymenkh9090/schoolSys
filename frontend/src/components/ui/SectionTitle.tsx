import { cn } from '@/lib/utils'

type Accent = 'teal' | 'blue' | 'amber' | 'purple' | 'emerald' | 'red'

const accents: Record<Accent, string> = {
  teal: 'text-brand-teal dark:text-teal-400',
  blue: 'text-brand-blue dark:text-blue-400',
  amber: 'text-amber-600 dark:text-amber-400',
  purple: 'text-purple-600 dark:text-purple-400',
  emerald: 'text-emerald-600 dark:text-emerald-400',
  red: 'text-red-600 dark:text-red-400',
}

interface SectionTitleProps {
  title: string
  icon?: React.ElementType
  accent?: Accent
  /** Élément aligné à droite (bouton « Voir tout », filtre…). */
  action?: React.ReactNode
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const sizes = {
  sm: 'text-sm',
  md: 'text-base',
  lg: 'text-xl sm:text-2xl',
}

/** Titre de bloc coloré (icône + libellé accentué), commun aux dashboards et rapports. */
export function SectionTitle({ title, icon: Icon, accent = 'teal', action, size = 'md', className }: SectionTitleProps) {
  return (
    <div className={cn('mb-4 flex items-center justify-between gap-3', className)}>
      <h3 className={cn('flex items-center gap-2 font-bold', sizes[size], accents[accent])}>
        {Icon && <Icon size={size === 'lg' ? 22 : 17} className="shrink-0" />}
        <span className="truncate">{title}</span>
      </h3>
      {action}
    </div>
  )
}
