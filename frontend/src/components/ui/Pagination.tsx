import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

interface PaginationProps {
  page: number
  pageSize: number
  total: number
  onPageChange: (page: number) => void
  className?: string
}

/** Pagination client, 0-indexée. Ne s'affiche pas s'il n'y a qu'une seule page. */
export function Pagination({ page, pageSize, total, onPageChange, className }: PaginationProps) {
  const nbPages = Math.max(1, Math.ceil(total / pageSize))
  if (total <= pageSize) return null

  const debut = page * pageSize + 1
  const fin = Math.min((page + 1) * pageSize, total)

  return (
    <div className={cn('flex items-center justify-between gap-3 flex-wrap', className)}>
      <p className="text-xs text-brand-textMuted dark:text-slate-400">
        {debut}–{fin} sur {total}
      </p>
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          className="p-1.5 rounded-md border border-brand-border dark:border-slate-700 text-brand-textMuted dark:text-slate-400 hover:bg-brand-bgSecondary dark:hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed"
          aria-label="Page précédente"
        >
          <ChevronLeft size={15} />
        </button>
        <span className="px-3 text-xs font-medium text-brand-text dark:text-slate-200">
          {page + 1} / {nbPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= nbPages - 1}
          className="p-1.5 rounded-md border border-brand-border dark:border-slate-700 text-brand-textMuted dark:text-slate-400 hover:bg-brand-bgSecondary dark:hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed"
          aria-label="Page suivante"
        >
          <ChevronRight size={15} />
        </button>
      </div>
    </div>
  )
}
