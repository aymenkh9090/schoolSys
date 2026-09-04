import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { LanguageToggle } from './LanguageToggle'

interface AuthPageHeaderProps {
  icon: React.ElementType
  /** Dégradé de la pastille d'icône (ex. "from-brand-teal to-teal-700 shadow-teal-900/20"). */
  badgeClassName: string
  tagline: string
}

/** En-tête commun aux pages de connexion : pastille d'icône, marque et sélecteur de langue. */
export function AuthPageHeader({ icon: Icon, badgeClassName, tagline }: AuthPageHeaderProps) {
  const { t } = useTranslation()

  return (
    <>
      <div
        className={cn(
          'mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br shadow-lg',
          badgeClassName
        )}
      >
        <Icon className="h-8 w-8 text-white" strokeWidth={2} />
      </div>

      <span className="-rotate-1 rounded-md bg-amber-300 px-3 py-0.5 text-2xl font-bold italic text-brand-teal dark:bg-amber-400 dark:text-teal-900">
        {t('login.brand')}
      </span>

      <p className="mt-3 max-w-xs text-center text-sm text-brand-textMuted dark:text-slate-400">{tagline}</p>

      <LanguageToggle className="mt-4" />
    </>
  )
}
