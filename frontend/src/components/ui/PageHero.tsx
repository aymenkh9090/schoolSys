import { cn } from '@/lib/utils'

interface PageHeroProps {
  title: string
  subtitle?: string
  icon?: React.ElementType
  /** Boutons/filtres alignés à droite de la bannière. */
  actions?: React.ReactNode
  /** `gradient` : bannière teal→ambre pleine largeur. `soft` : fond dégradé pâle, texte teal. */
  variant?: 'gradient' | 'soft'
  className?: string
}

/**
 * En-tête de page mis en avant (bannière). Complète `PageHeader`, qui reste
 * l'en-tête sobre utilisé par les pages de gestion.
 */
export function PageHero({ title, subtitle, icon: Icon, actions, variant = 'gradient', className }: PageHeroProps) {
  const gradient = variant === 'gradient'

  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-2xl px-6 py-6 sm:px-8',
        gradient
          ? 'bg-gradient-to-r from-teal-600 via-teal-500 to-amber-400 text-white'
          : 'bg-gradient-to-r from-teal-50 to-amber-50 dark:from-slate-900 dark:to-slate-900',
        className
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4 min-w-0">
          {Icon && (
            <span
              className={cn(
                'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl',
                gradient ? 'bg-white/20 text-white' : 'bg-brand-teal text-white'
              )}
            >
              <Icon size={24} />
            </span>
          )}
          <div className="min-w-0">
            <h1
              className={cn(
                'truncate text-2xl font-bold sm:text-3xl',
                gradient ? 'text-white' : 'text-brand-teal dark:text-teal-300'
              )}
            >
              {title}
            </h1>
            {subtitle && (
              <p className={cn('mt-1 text-sm', gradient ? 'text-white/85' : 'text-brand-textMuted dark:text-slate-400')}>
                {subtitle}
              </p>
            )}
          </div>
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  )
}
