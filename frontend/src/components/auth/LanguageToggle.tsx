import { Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'

const LANGS = ['fr', 'en', 'ar'] as const
const LANG_LABELS: Record<(typeof LANGS)[number], string> = {
  fr: 'Français',
  en: 'English',
  ar: 'العربية',
}

/** Bascule cyclique de langue (fr → en → ar) — partagée par les pages d'authentification. */
export function LanguageToggle({ className }: { className?: string }) {
  const { i18n } = useTranslation()
  const currentLangIndex = LANGS.indexOf(i18n.language as (typeof LANGS)[number])
  const nextLang = LANGS[(currentLangIndex + 1) % LANGS.length] ?? LANGS[0]

  return (
    <button
      type="button"
      onClick={() => i18n.changeLanguage(nextLang)}
      className={cn(
        'flex items-center gap-1.5 text-xs font-medium text-brand-textMuted transition-colors',
        'hover:text-brand-teal dark:text-slate-400 dark:hover:text-brand-tealLight',
        className
      )}
    >
      <Languages className="h-3.5 w-3.5" />
      {LANG_LABELS[nextLang]}
    </button>
  )
}
