import { useState } from 'react'

// Champ heure texte "HH:mm" — évite le widget natif <input type="time">
// dont l'affichage (12h/24h, AM/PM) dépend de la locale du navigateur/OS.
interface TimeInput24Props {
  value: string | undefined
  onChange: (value: string) => void
  className?: string
}

function formatTimeInput(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 4)
  if (digits.length <= 2) return digits
  return `${digits.slice(0, 2)}:${digits.slice(2)}`
}

function clampTime(raw: string): string {
  const digits = raw.replace(/\D/g, '').padEnd(4, '0').slice(0, 4)
  const hour = Math.min(23, parseInt(digits.slice(0, 2), 10) || 0)
  const minute = Math.min(59, parseInt(digits.slice(2, 4), 10) || 0)
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

export function TimeInput24({ value, onChange, className }: TimeInput24Props) {
  const [draft, setDraft] = useState<string | null>(null)
  const display = draft ?? (value ?? '')

  return (
    <input
      type="text"
      inputMode="numeric"
      placeholder="HH:mm"
      maxLength={5}
      value={display}
      onChange={(e) => setDraft(formatTimeInput(e.target.value))}
      onBlur={() => {
        if (draft !== null && draft !== '') onChange(clampTime(draft))
        setDraft(null)
      }}
      className={className}
    />
  )
}
