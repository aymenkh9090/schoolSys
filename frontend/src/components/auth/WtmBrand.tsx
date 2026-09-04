import { cn } from '@/lib/utils'

/** Logo de l'éditeur — voir public/images/README.md pour le remplacer. */
const LOGO_WTM = '/images/logo-wtm.png'

/**
 * Logo Web Technology Masters (marque + mot-symbole), en tête de la page
 * super administrateur. Le lettrage est bleu nuit : en thème sombre, le logo
 * est posé sur une pastille blanche pour rester lisible.
 */
export function WtmBrand({ className }: { className?: string }) {
  return (
    <img
      src={LOGO_WTM}
      alt="Web Technology Masters"
      className={cn('h-10 w-auto dark:rounded-lg dark:bg-white dark:p-1.5', className)}
    />
  )
}
