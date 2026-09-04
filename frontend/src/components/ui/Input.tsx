import { forwardRef } from 'react'
import { cn } from '@/lib/utils'

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  /** Icône affichée à gauche du champ (ex. pour les formulaires d'authentification). */
  icon?: React.ElementType
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, id, icon: Icon, ...props }, ref) => {
    const inputId = id ?? label?.toLowerCase().replace(/\s+/g, '-')
    return (
      <div className="flex flex-col gap-1">
        {label && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium text-brand-text dark:text-slate-200"
          >
            {label}
          </label>
        )}
        <div className="relative">
          {Icon && (
            <Icon
              size={16}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500 rtl:left-auto rtl:right-3"
            />
          )}
          <input
            ref={ref}
            id={inputId}
            className={cn(
              'w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text',
              'placeholder:text-brand-textMuted',
              'focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent',
              'disabled:opacity-50 disabled:bg-brand-bgSecondary',
              'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:placeholder:text-slate-500 dark:disabled:bg-slate-800',
              Icon && 'pl-9 rtl:pl-3 rtl:pr-9',
              error && 'border-danger focus:ring-danger',
              className
            )}
            {...props}
          />
        </div>
        {error && <p className="text-xs text-danger">{error}</p>}
      </div>
    )
  }
)
Input.displayName = 'Input'
