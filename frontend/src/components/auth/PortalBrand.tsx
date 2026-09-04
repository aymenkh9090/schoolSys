import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'

/** Emblème de marque — voir public/images/README.md pour le remplacer. */
const LOGO_IMAGE = '/images/logo-schoolsys.png'

/** Emblème + mot-symbole SchoolSys, en tête des pages de connexion. */
export function PortalBrand({ className }: { className?: string }) {
  const { t } = useTranslation()

  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <img
        src={LOGO_IMAGE}
        alt=""
        aria-hidden="true"
        className="h-11 w-14 rounded-lg bg-[#f4f8f9] object-contain"
      />
      <span className="font-serif text-2xl font-bold tracking-tight text-[#0d2644] dark:text-slate-100">
        {t('login.brand')}
      </span>
    </div>
  )
}
