import { forwardRef } from 'react'
import { cn } from '@/lib/utils'

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
  options: { value: string | number; label: string }[]
  placeholder?: string
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, options, placeholder, id, ...props }, ref) => {
    const selectId = id ?? label?.toLowerCase().replace(/\s+/g, '-')
    return (
      <div className="flex flex-col gap-1">
        {label && (
          <label htmlFor={selectId} className="text-sm font-medium text-brand-text dark:text-slate-200">
            {label}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          // Sans defaultValue, le navigateur sélectionne la 1re option non-disabled au lieu du placeholder
          defaultValue={placeholder && props.value === undefined && props.defaultValue === undefined ? '' : undefined}
          className={cn(
            'w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text',
            'focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent',
            'disabled:opacity-50 disabled:bg-brand-bgSecondary',
            'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:disabled:bg-slate-800',
            error && 'border-danger focus:ring-danger',
            className
          )}
          {...props}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        {error && <p className="text-xs text-danger">{error}</p>}
      </div>
    )
  }
)
Select.displayName = 'Select'
