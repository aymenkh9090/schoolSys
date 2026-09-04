import { useState } from 'react'
import { Lock, Eye, EyeOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'

interface PasswordFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: string
  error?: string
  /** Masque l'icône de cadenas à gauche (mise en page sans icônes). */
  hideIcon?: boolean
}

/** Champ mot de passe avec icône et bascule afficher/masquer — utilisé sur les pages d'authentification. */
export function PasswordField({ label, error, id, className, hideIcon, ...props }: PasswordFieldProps) {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)
  const inputId = id ?? label?.toLowerCase().replace(/\s+/g, '-')

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-brand-text dark:text-slate-200">
          {label}
        </label>
      )}
      <div className="relative">
        {!hideIcon && (
          <Lock size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-brand-textMuted dark:text-slate-500 rtl:left-auto rtl:right-3" />
        )}
        <input
          id={inputId}
          type={visible ? 'text' : 'password'}
          className={cn(
            'w-full rounded-lg border border-brand-border bg-white py-2.5 pr-10 text-sm text-brand-text rtl:pl-10',
            hideIcon ? 'pl-3 rtl:pr-3' : 'pl-9 rtl:pr-9',
            'placeholder:text-brand-textMuted',
            'focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent',
            'disabled:opacity-50 disabled:bg-brand-bgSecondary',
            'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:placeholder:text-slate-500',
            error && 'border-danger focus:ring-danger',
            className
          )}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          tabIndex={-1}
          title={visible ? t('common.hidePassword') : t('common.showPassword')}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-brand-textMuted hover:text-brand-text dark:text-slate-500 dark:hover:text-slate-300 rtl:left-3 rtl:right-auto"
        >
          {visible ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  )
}
