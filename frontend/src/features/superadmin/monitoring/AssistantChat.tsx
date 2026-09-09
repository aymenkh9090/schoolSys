import { useEffect, useRef, useState } from 'react'
import {
  Database,
  Eye,
  HelpCircle,
  MemoryStick,
  Send,
  ServerCog,
  ShieldAlert,
  Timer,
} from 'lucide-react'

import { aiAssistantApi } from '@/api/aiAssistant.api'
import { useAuth } from '@/hooks/useAuth'
import { cn } from '@/lib/utils'

interface Message {
  role: 'user' | 'assistant'
  text: string
  toolsUsed?: string[]
  durationMs?: number
  /** Vrai quand le service n'a pas répondu : la bulle prend l'habillage d'alerte. */
  failed?: boolean
}

// Les suggestions guident l'utilisateur vers les questions que le modèle sait
// traiter. Avec un modèle 3B, c'est déterminant pour une démo réussie. L'icône
// donne à chaque carte une silhouette reconnaissable — on retrouve « celle du
// bas à droite » sans relire les quatre.
const SUGGESTIONS: { icon: React.ElementType; text: string }[] = [
  { icon: ShieldAlert, text: "Est-ce qu'il y a un problème sur la plateforme ?" },
  { icon: MemoryStick, text: 'Quelle est la consommation mémoire ?' },
  { icon: Timer, text: 'Quels endpoints sont les plus lents ?' },
  { icon: Database, text: 'Comment va la base de données ?' },
]

/**
 * Conversation sur l'état technique de la plateforme.
 *
 * Même charte que l'assistant emploi du temps côté école, avec l'accent violet
 * de l'espace super-admin (celui de l'écran de connexion) au lieu du teal : la
 * structure se reconnaît d'un espace à l'autre, la couleur dit où l'on est.
 *
 * Trois signes constants, qui sont ce qui permet de lire la page sans la
 * déchiffrer :
 *   — le violet identifie l'assistant (avatar, points d'attente, bouton
 *     d'envoi), le bleu de marque identifie l'utilisateur. Deux couleurs,
 *     deux voix ;
 *   — chaque réponse porte ses sources : un chiffre juste et un chiffre inventé
 *     se présentent sinon exactement de la même façon ;
 *   — l'écran vide n'est jamais vide, il propose ce qu'on peut demander.
 */
export default function AssistantChat() {
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
      .join('') || 'SA'

  const send = async (text?: string) => {
    const question = (text ?? input).trim()
    if (!question || loading) return

    setInput('')
    setMessages((m) => [...m, { role: 'user', text: question }])
    setLoading(true)

    try {
      const res = await aiAssistantApi.ask(question)
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
          text: "L'assistant est injoignable. Vérifie que le service est démarré sur le port 8000.",
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
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-brand-border bg-gradient-to-r from-violet-50 via-white to-white px-5 py-3.5 dark:border-slate-700 dark:from-violet-500/10 dark:via-slate-900 dark:to-slate-900">
        <div className="flex items-center gap-3">
          <AssistantAvatar />
          <div>
            <p className="text-sm font-semibold text-brand-text dark:text-slate-100">
              Assistant technique
            </p>
            <p className="flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              Répond à partir des métriques réelles de la plateforme
            </p>
          </div>
        </div>

        {/* L'analyse est locale et en lecture seule : c'est une propriété du
            produit, pas une note de bas de page. Elle reste visible. */}
        <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-border bg-white px-2.5 py-1 text-xs font-medium text-brand-textMuted dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300">
          <Eye size={12} /> Analyse locale, lecture seule
        </span>
      </div>

      {/* ── Fil de la conversation ──────────────────────────────────────── */}
      {/* 30rem : même hauteur utile que l'assistant de contraintes, pour la
          raison qui y est documentée — en dessous, deux échanges suffisent à
          repousser la réponse hors du champ au moment même où on la lit.
          Hauteur fixe une fois la conversation lancée, et non `flex-1` comme
          là-bas : ce panneau est empilé dans le flux de la page, sans parent
          qui borne sa hauteur, donc c'est lui qui doit fixer la sienne. */}
      <div
        className={cn('space-y-4 overflow-y-auto px-5 py-5', empty ? 'min-h-[30rem]' : 'h-[30rem]')}
      >
        {empty ? (
          <EmptyState onPick={send} disabled={loading} />
        ) : (
          messages.map((message, i) => <Bubble key={i} message={message} initials={initials} />)
        )}

        {loading && <TypingBubble />}
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
              placeholder="Pose une question sur l'état de la plateforme…"
              maxLength={500}
              disabled={loading}
              aria-label="Ta question"
              className="w-full rounded-full border border-brand-border bg-white py-2.5 pl-4 pr-16 text-sm text-brand-text placeholder:text-brand-textMuted focus:border-transparent focus:outline-none focus:ring-2 focus:ring-violet-500 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-500"
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
              'bg-gradient-to-br from-violet-600 to-violet-500 hover:from-violet-700 hover:to-violet-600',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-900',
              'disabled:cursor-not-allowed disabled:from-slate-300 disabled:to-slate-300 dark:disabled:from-slate-700 dark:disabled:to-slate-700'
            )}
          >
            <Send size={16} className="rtl:-scale-x-100" />
          </button>
        </div>

        <p className="mt-2 flex items-start gap-1.5 text-xs leading-relaxed text-brand-textMuted dark:text-slate-500">
          <HelpCircle size={12} className="mt-0.5 shrink-0" />
          Les réponses s’appuient sur les métriques collectées par Prometheus. Pour les courbes
          détaillées, ouvre le tableau de bord Grafana depuis l’en-tête.
        </p>
      </div>
    </div>
  )
}

// ── Éléments d'identité ─────────────────────────────────────────────────────

/** Marque visuelle de l'assistant : le violet de l'espace super-admin. */
function AssistantAvatar({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const box = { sm: 'h-7 w-7', md: 'h-9 w-9', lg: 'h-14 w-14' }[size]
  const icon = { sm: 14, md: 18, lg: 26 }[size]
  return (
    <span
      className={cn(
        'flex shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-600 to-fuchsia-500 text-white shadow-sm',
        box
      )}
    >
      <ServerCog size={icon} />
    </span>
  )
}

function EmptyState({ onPick, disabled }: { onPick: (text: string) => void; disabled: boolean }) {
  return (
    <div className="animate-[fadeIn_0.35s_ease-out]">
      <div className="flex flex-col items-center text-center">
        <AssistantAvatar size="lg" />
        <p className="mt-3 text-base font-semibold text-brand-text dark:text-slate-100">
          Que veux-tu savoir sur la plateforme ?
        </p>
        <p className="mx-auto mt-1 max-w-md text-sm leading-relaxed text-brand-textMuted dark:text-slate-400">
          Pose ta question en français, ou pars d’une de celles-ci.
        </p>
      </div>

      <div className="mx-auto mt-5 grid max-w-2xl gap-2 sm:grid-cols-2">
        {SUGGESTIONS.map(({ icon: Icon, text }) => (
          <button
            key={text}
            type="button"
            onClick={() => onPick(text)}
            disabled={disabled}
            className={cn(
              'group flex items-start gap-2.5 rounded-xl border border-brand-border bg-white p-3 text-left transition-colors',
              'hover:border-violet-400/50 hover:bg-violet-50/50 disabled:opacity-50',
              'dark:border-slate-700 dark:bg-slate-800/40 dark:hover:border-violet-500/40 dark:hover:bg-violet-500/5'
            )}
          >
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-brand-bgSecondary text-violet-600 transition-colors group-hover:bg-white dark:bg-slate-700/60 dark:text-violet-300 dark:group-hover:bg-slate-700">
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
            s'appuie. Essentiel pour la confiance — sans cela, un chiffre juste
            et un chiffre inventé se présentent exactement de la même façon. */}
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
 * Sur CPU, la réponse prend 3 à 10 s. Sans signe de vie animé, l'utilisateur
 * pense que c'est cassé, relance, ou quitte la page.
 */
function TypingBubble() {
  return (
    <div className="flex animate-[bubbleIn_0.25s_ease-out] items-start gap-2.5">
      <AssistantAvatar />
      <div className="flex items-center gap-2 rounded-2xl rounded-tl-sm border border-brand-border bg-brand-bgSecondary/60 px-3.5 py-3 dark:border-slate-700 dark:bg-slate-800/60">
        <span className="flex items-end gap-1">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className="h-1.5 w-1.5 rounded-full bg-violet-500 dark:bg-violet-400"
              style={{
                animation: 'typingDot 1.4s ease-in-out infinite',
                animationDelay: `${i * 0.18}s`,
              }}
            />
          ))}
        </span>
        <span className="text-xs text-brand-textMuted dark:text-slate-400">
          Analyse des métriques…
        </span>
      </div>
    </div>
  )
}

/** « 820 ms » sous la seconde, « 12,4 s » au-delà : lisible dans les deux cas. */
function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1).replace('.', ',')} s`
}
