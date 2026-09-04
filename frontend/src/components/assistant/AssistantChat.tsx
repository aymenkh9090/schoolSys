import { useEffect, useRef, useState } from 'react'
import { Bot, Database, Eye, Send, Timer } from 'lucide-react'

import type { ChatResponse } from '@/api/aiAssistant.api'
import { useAuth } from '@/hooks/useAuth'
import { cn } from '@/lib/utils'

export interface Suggestion {
  icon: React.ElementType
  text: string
}

interface Props {
  /** Nom affiché dans le bandeau. Identifie l'assistant, pas la page. */
  titre: string
  /** Une ligne sous le titre : sur quoi cet assistant s'appuie. */
  soustitre: string
  /** Questions proposées à vide. Voir le commentaire de `EmptyState`. */
  suggestions: Suggestion[]
  emptyTitre: string
  emptyIndication: string
  /** Texte affiché pendant l'attente : dit ce qui est en train d'être consulté. */
  attenteLabel: string
  /** Rappel sous la zone de saisie — ce que l'assistant ne fait pas. */
  rappel: React.ReactNode
  placeholder?: string
  /** L'appel réseau. Le composant ne sait pas quel assistant il interroge. */
  ask: (question: string) => Promise<ChatResponse>
  /** Hauteur du fil une fois la conversation entamée. */
  hauteur?: string
}

interface Message {
  role: 'user' | 'assistant'
  text: string
  toolsUsed?: string[]
  durationMs?: number
  /** Vrai quand le service n'a pas répondu : la bulle prend l'habillage d'alerte. */
  failed?: boolean
}

/**
 * Conversation avec un assistant en lecture seule.
 *
 * Extrait de l'assistant emploi du temps pour servir aussi le cahier de séance.
 * Les deux posent exactement le même problème d'interface — une attente de 5 à
 * 30 secondes à rendre supportable, des sources à montrer, un écran vide à ne
 * pas laisser vide — et ces réponses ont coûté assez cher à mettre au point pour
 * ne pas être réécrites en double, où elles divergeraient au premier ajustement.
 *
 * Le parti pris graphique tient en trois signes constants, qui sont ce qui
 * permet de lire la page sans la déchiffrer :
 *   — le teal identifie l'assistant (avatar, points d'attente, bouton d'envoi),
 *     le bleu de marque identifie l'utilisateur. Deux couleurs, deux voix ;
 *   — chaque réponse porte ses sources : un chiffre juste et un chiffre inventé
 *     se présentent sinon exactement de la même façon ;
 *   — l'écran vide n'est jamais vide, il propose ce qu'on peut demander.
 *
 * Ce qui reste propre à chaque assistant — son nom, ses suggestions, son rappel,
 * son appel réseau — passe en props. Rien d'autre ne devrait avoir à changer.
 */
export function AssistantChat({
  titre,
  soustitre,
  suggestions,
  emptyTitre,
  emptyIndication,
  attenteLabel,
  rappel,
  placeholder = 'Posez votre question…',
  ask,
  hauteur = 'h-[26rem]',
}: Props) {
  const { user } = useAuth()
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  const initials =
    [user?.given_name, user?.family_name]
      .filter(Boolean)
      .map((part) => String(part).charAt(0).toUpperCase())
      .join('') || 'MOI'

  const send = async (text?: string) => {
    const question = (text ?? input).trim()
    if (!question || loading) return

    setInput('')
    setMessages((m) => [...m, { role: 'user', text: question }])
    setLoading(true)

    try {
      const res = await ask(question)
      setMessages((m) => [
        ...m,
        {
          role: 'assistant',
          text: res.answer,
          toolsUsed: res.tools_used,
          durationMs: res.duration_ms,
        },
      ])
    } catch {
      setMessages((m) => [
        ...m,
        {
          role: 'assistant',
          failed: true,
          text: "L'assistant est injoignable. Vérifiez que le service IA est démarré sur le port 8000.",
        },
      ])
    } finally {
      setLoading(false)
      inputRef.current?.focus()
    }
  }

  const empty = messages.length === 0

  return (
    <div className="overflow-hidden rounded-2xl border border-brand-border bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
      {/* ── Bandeau d'identité ──────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-brand-border bg-gradient-to-r from-teal-50 via-white to-white px-5 py-3.5 dark:border-slate-700 dark:from-teal-500/10 dark:via-slate-900 dark:to-slate-900">
        <div className="flex items-center gap-3">
          <AssistantAvatar />
          <div>
            <p className="text-sm font-semibold text-brand-text dark:text-slate-100">{titre}</p>
            <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              {soustitre}
            </p>
          </div>
        </div>

        {/* La lecture seule est une propriété du produit, pas une note de bas
            de page : elle reste visible en permanence, pas seulement à vide. */}
        <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-border bg-white px-2.5 py-1 text-xs font-medium text-brand-textMuted dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300">
          <Eye size={12} /> Consultation seule
        </span>
      </div>

      {/* ── Fil de la conversation ──────────────────────────────────────── */}
      <div
        className={cn(
          'space-y-4 overflow-y-auto px-5 py-5',
          empty ? 'min-h-[22rem]' : hauteur
        )}
      >
        {empty ? (
          <EmptyState
            titre={emptyTitre}
            indication={emptyIndication}
            suggestions={suggestions}
            onPick={send}
            disabled={loading}
          />
        ) : (
          messages.map((message, i) => (
            <Bubble key={i} message={message} initials={initials} />
          ))
        )}

        {loading && <TypingBubble label={attenteLabel} />}
        <div ref={bottomRef} />
      </div>

      {/* ── Zone de saisie ──────────────────────────────────────────────── */}
      <div className="border-t border-brand-border bg-brand-bgSecondary/50 px-5 py-4 dark:border-slate-700 dark:bg-slate-800/40">
        <div className="flex items-end gap-2">
          <div className="relative flex-1">
            <input
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send()}
              placeholder={placeholder}
              maxLength={500}
              disabled={loading}
              aria-label="Votre question"
              className="w-full rounded-full border border-brand-border bg-white py-2.5 pl-4 pr-16 text-sm text-brand-text placeholder:text-brand-textMuted focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-teal disabled:opacity-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-500"
            />
            {/* Le compteur n'apparaît qu'à l'approche de la limite : affiché en
                permanence, il ne fait que rappeler une contrainte qu'on n'a
                aucune raison d'atteindre. */}
            {input.length > 400 && (
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[11px] tabular-nums text-brand-textMuted dark:text-slate-500">
                {input.length}/500
              </span>
            )}
          </div>

          <button
            type="button"
            onClick={() => send()}
            disabled={loading || !input.trim()}
            aria-label="Envoyer la question"
            className={cn(
              'flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-white transition-colors',
              'bg-gradient-to-br from-teal-600 to-teal-500 hover:from-teal-700 hover:to-teal-600',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-900',
              'disabled:cursor-not-allowed disabled:from-slate-300 disabled:to-slate-300 dark:disabled:from-slate-700 dark:disabled:to-slate-700'
            )}
          >
            <Send size={16} className="rtl:-scale-x-100" />
          </button>
        </div>

        <div className="mt-2 flex items-start gap-1.5 text-xs leading-relaxed text-brand-textMuted dark:text-slate-500">
          {rappel}
        </div>
      </div>
    </div>
  )
}

// ── Éléments d'identité ─────────────────────────────────────────────────────

/** Marque visuelle de l'assistant : le teal de la charte, en dégradé. */
export function AssistantAvatar({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const box = { sm: 'h-7 w-7', md: 'h-9 w-9', lg: 'h-14 w-14' }[size]
  const icon = { sm: 14, md: 18, lg: 26 }[size]
  return (
    <span
      className={cn(
        'flex shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-teal-600 to-teal-400 text-white shadow-sm',
        box
      )}
    >
      <Bot size={icon} />
    </span>
  )
}

/**
 * Écran vide.
 *
 * Les suggestions montrent les questions que l'assistant sait réellement
 * traiter. Sans elles, l'utilisateur pose une question hors périmètre, obtient
 * un refus, et en conclut que la fonction ne marche pas. L'icône donne à chaque
 * carte une silhouette reconnaissable — on retrouve « la question du bas à
 * droite » sans relire les quatre.
 */
function EmptyState({
  titre,
  indication,
  suggestions,
  onPick,
  disabled,
}: {
  titre: string
  indication: string
  suggestions: Suggestion[]
  onPick: (text: string) => void
  disabled: boolean
}) {
  return (
    <div className="animate-[fadeIn_0.35s_ease-out]">
      <div className="flex flex-col items-center text-center">
        <AssistantAvatar size="lg" />
        <p className="mt-3 text-base font-semibold text-brand-text dark:text-slate-100">{titre}</p>
        <p className="mx-auto mt-1 max-w-md text-sm leading-relaxed text-brand-textMuted dark:text-slate-400">
          {indication}
        </p>
      </div>

      <div className="mx-auto mt-5 grid max-w-2xl gap-2 sm:grid-cols-2">
        {suggestions.map(({ icon: Icon, text }) => (
          <button
            key={text}
            type="button"
            onClick={() => onPick(text)}
            disabled={disabled}
            className={cn(
              'group flex items-start gap-2.5 rounded-xl border border-brand-border bg-white p-3 text-left transition-colors',
              'hover:border-brand-teal/40 hover:bg-teal-50/50 disabled:opacity-50',
              'dark:border-slate-700 dark:bg-slate-800/40 dark:hover:border-teal-500/40 dark:hover:bg-teal-500/5'
            )}
          >
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-brand-bgSecondary text-brand-teal transition-colors group-hover:bg-white dark:bg-slate-700/60 dark:text-teal-300 dark:group-hover:bg-slate-700">
              <Icon size={14} />
            </span>
            <span className="text-sm leading-snug text-brand-text dark:text-slate-200">{text}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

// ── Messages ────────────────────────────────────────────────────────────────

function Bubble({ message, initials }: { message: Message; initials: string }) {
  const mine = message.role === 'user'

  return (
    <div
      className={cn(
        'flex animate-[bubbleIn_0.25s_ease-out] items-start gap-2.5',
        mine && 'flex-row-reverse'
      )}
    >
      {mine ? (
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-blue/10 text-[11px] font-semibold text-brand-blue dark:bg-blue-500/15 dark:text-blue-300">
          {initials}
        </span>
      ) : (
        <AssistantAvatar />
      )}

      <div className={cn('flex min-w-0 max-w-[80%] flex-col gap-1', mine && 'items-end')}>
        <div
          className={cn(
            'whitespace-pre-line rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed',
            mine
              ? 'rounded-tr-sm bg-brand-blue text-white'
              : message.failed
                ? 'rounded-tl-sm border border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-500/25 dark:bg-amber-500/10 dark:text-amber-200'
                : 'rounded-tl-sm border border-brand-border bg-brand-bgSecondary/60 text-brand-text dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-100'
          )}
        >
          {message.text}
        </div>

        {/* Transparence : l'utilisateur voit sur quelles données la réponse
            s'appuie. Sans cela, un chiffre juste et un chiffre inventé se
            présentent exactement de la même façon. */}
        {message.toolsUsed && message.toolsUsed.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            {message.toolsUsed.map((tool) => (
              <span
                key={tool}
                className="inline-flex items-center gap-1 rounded-full bg-brand-bgSecondary px-2 py-0.5 text-[11px] font-medium text-brand-textMuted dark:bg-slate-800 dark:text-slate-400"
              >
                <Database size={10} /> {tool}
              </span>
            ))}
            {message.durationMs !== undefined && (
              <span className="inline-flex items-center gap-1 text-[11px] tabular-nums text-brand-textMuted dark:text-slate-500">
                <Timer size={10} /> {formatDuration(message.durationMs)}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * Attente en cours.
 *
 * Le modèle tourne sur CPU : la réponse peut demander de 5 à 30 secondes. Sans
 * signe de vie animé, cette attente passe pour une panne et l'utilisateur
 * relance ou quitte la page.
 */
function TypingBubble({ label }: { label: string }) {
  return (
    <div className="flex animate-[bubbleIn_0.25s_ease-out] items-start gap-2.5">
      <AssistantAvatar />
      <div className="flex items-center gap-2 rounded-2xl rounded-tl-sm border border-brand-border bg-brand-bgSecondary/60 px-3.5 py-3 dark:border-slate-700 dark:bg-slate-800/60">
        <span className="flex items-end gap-1">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className="h-1.5 w-1.5 rounded-full bg-brand-teal dark:bg-teal-400"
              style={{ animation: 'typingDot 1.4s ease-in-out infinite', animationDelay: `${i * 0.18}s` }}
            />
          ))}
        </span>
        <span className="text-xs text-brand-textMuted dark:text-slate-400">{label}</span>
      </div>
    </div>
  )
}

/** « 820 ms » sous la seconde, « 12,4 s » au-delà : lisible dans les deux cas. */
function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1).replace('.', ',')} s`
}
