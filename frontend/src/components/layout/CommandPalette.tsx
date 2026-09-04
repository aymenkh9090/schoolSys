import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, CornerDownLeft } from 'lucide-react'
import { getFlatNavEntries, useSchoolNav } from './Sidebar'

interface Props {
  open: boolean
  onClose: () => void
}

export function CommandPalette({ open, onClose }: Props) {
  const nav = useSchoolNav()
  const entries = useMemo(() => getFlatNavEntries(nav), [nav])
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  const filtered = useMemo(() => {
    const q = query.toLowerCase()
    return entries.filter((e) => e.label.toLowerCase().includes(q) || e.group?.toLowerCase().includes(q))
  }, [entries, query])

  useEffect(() => {
    if (open) {
      setQuery('')
      setActiveIndex(0)
      requestAnimationFrame(() => inputRef.current?.focus())
    }
  }, [open])

  useEffect(() => {
    setActiveIndex(0)
  }, [query])

  const go = (to: string) => {
    navigate(to)
    onClose()
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-24 p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative w-full max-w-lg bg-white dark:bg-slate-900 rounded-xl shadow-xl border border-brand-border dark:border-slate-700 overflow-hidden">
        <div className="flex items-center gap-2 px-4 py-3 border-b border-brand-border dark:border-slate-700">
          <Search size={18} className="text-brand-textMuted dark:text-slate-400 shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher une page..."
            className="flex-1 bg-transparent outline-none text-sm text-brand-text dark:text-slate-200 placeholder:text-brand-textMuted"
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActiveIndex((i) => Math.min(i + 1, filtered.length - 1))
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActiveIndex((i) => Math.max(i - 1, 0))
              } else if (e.key === 'Enter' && filtered[activeIndex]) {
                go(filtered[activeIndex].to)
              } else if (e.key === 'Escape') {
                onClose()
              }
            }}
          />
          <kbd className="text-[10px] px-1.5 py-0.5 rounded bg-brand-bgSecondary dark:bg-slate-800 text-brand-textMuted dark:text-slate-400">
            Esc
          </kbd>
        </div>
        <div className="max-h-80 overflow-y-auto py-2">
          {filtered.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-brand-textMuted dark:text-slate-400">
              Aucun résultat
            </p>
          ) : (
            filtered.map((entry, i) => (
              <button
                key={entry.to}
                onClick={() => go(entry.to)}
                onMouseEnter={() => setActiveIndex(i)}
                className={`w-full flex items-center justify-between px-4 py-2.5 text-sm text-left transition-colors ${
                  i === activeIndex
                    ? 'bg-brand-blue text-white'
                    : 'text-brand-text dark:text-slate-200 hover:bg-brand-bgSecondary dark:hover:bg-slate-800'
                }`}
              >
                <span>
                  {entry.group && <span className="opacity-60">{entry.group} › </span>}
                  {entry.label}
                </span>
                {i === activeIndex && <CornerDownLeft size={14} />}
              </button>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
