import { cn } from '@/lib/utils'

export interface AssistantInfo {
  icon: React.ElementType
  title: string
  text: string
}

/**
 * Les repères posés AVANT la conversation : ce que l'assistant lit, ce qu'il ne
 * fait pas, et sur quoi il s'appuie.
 *
 * Les répéter dans le fil de discussion serait du bruit ; les taire laisse
 * croire qu'il agit. D'où trois cartes, lues une fois, puis oubliées — c'est
 * exactement leur rôle.
 *
 * Extrait de la page Assistant emploi du temps quand le pilotage pédagogique a
 * eu besoin des mêmes : deux copies auraient divergé au premier ajustement de
 * la charte.
 */
export function AssistantInfoCards({ infos }: { infos: AssistantInfo[] }) {
  return (
    <div
      className={cn(
        'grid gap-3',
        infos.length === 3 ? 'sm:grid-cols-3' : 'sm:grid-cols-2'
      )}
    >
      {infos.map(({ icon: Icon, title, text }) => (
        <div
          key={title}
          className="rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-300">
            <Icon size={16} />
          </span>
          <p className="mt-2.5 text-sm font-semibold text-brand-text dark:text-slate-100">{title}</p>
          <p className="mt-1 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">{text}</p>
        </div>
      ))}
    </div>
  )
}
