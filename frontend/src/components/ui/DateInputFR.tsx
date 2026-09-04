import { useState } from 'react'
import { cn } from '@/lib/utils'

// Champ date texte "jj/mm/aaaa" — évite le widget natif <input type="date">
// dont l'affichage (mm/dd/yyyy vs dd/mm/yyyy) dépend de la locale du
// navigateur/OS et n'est pas fiable partout (ex: Firefox ignore lang="fr-FR").
// La valeur externe (value / onChange) reste au format ISO "yyyy-mm-dd".
interface DateInputFRProps {
  value: string | undefined
  onChange: (value: string) => void
  label?: string
  error?: string
  className?: string
  id?: string
  placeholder?: string
  disabled?: boolean
}

function formatDateInput(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8)
  if (digits.length <= 2) return digits
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`
}

function clampDateToIso(raw: string): string {
  const digits = raw.replace(/\D/g, '')
  if (digits.length === 0) return ''
  const padded = digits.padEnd(8, '0').slice(0, 8)
  const day = Math.min(31, Math.max(1, parseInt(padded.slice(0, 2), 10) || 1))
  const month = Math.min(12, Math.max(1, parseInt(padded.slice(2, 4), 10) || 1))
  const year = parseInt(padded.slice(4, 8), 10) || new Date().getFullYear()
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

function isoToFr(iso: string | undefined): string {
  if (!iso) return ''
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return ''
  const [, y, mo, d] = m
  return `${d}/${mo}/${y}`
}

export function DateInputFR({ value, onChange, label, error, className, id, placeholder, disabled }: DateInputFRProps) {
  const [draft, setDraft] = useState<string | null>(null)
  const display = draft ?? isoToFr(value)
  const inputId = id ?? label?.toLowerCase().replace(/\s+/g, '-')

  const input = (
    <input
      type="text"
      inputMode="numeric"
      id={inputId}
      placeholder={placeholder ?? 'jj/mm/aaaa'}
      maxLength={10}
      value={display}
      disabled={disabled}
      onChange={(e) => setDraft(formatDateInput(e.target.value))}
      onBlur={() => {
        if (draft !== null) onChange(clampDateToIso(draft))
        setDraft(null)
      }}
      className={cn(
        'w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text',
        'placeholder:text-brand-textMuted',
        'focus:outline-none focus:ring-2 focus:ring-brand-blue focus:border-transparent',
        'disabled:opacity-50 disabled:bg-brand-bgSecondary',
        'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:placeholder:text-slate-500 dark:disabled:bg-slate-800',
        error && 'border-danger focus:ring-danger',
        className
      )}
    />
  )

  if (!label) return input

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-sm font-medium text-brand-text dark:text-slate-200">
        {label}
      </label>
      {input}
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  )
}
