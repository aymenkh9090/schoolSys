import { useState } from 'react'
import {
  BookMarked,
  CalendarSearch,
  ChevronDown,
  ClipboardList,
  Info,
  Sparkles,
  TrendingUp,
} from 'lucide-react'

import { AssistantChat, type Suggestion } from '@/components/assistant/AssistantChat'
import { cahierAssistantApi } from '@/api/aiAssistant.api'
import { cn } from '@/lib/utils'

// Les suggestions décrivent les deux capacités réelles de l'assistant : le
// CONTENU d'une séance (recherche sémantique) et l'ÉTAT d'avancement
// (comptage exact). Elles ne sont pas décoratives — elles évitent la question
// hors périmètre, dont le refus fait conclure que la fonction ne marche pas.
const SUGGESTIONS: Suggestion[] = [
  { icon: CalendarSearch, text: "Qu'ai-je fait lors de ma dernière séance avec cette classe ?" },
  { icon: BookMarked, text: 'Quand ai-je traité ce chapitre, et dans quelles classes ?' },
  { icon: ClipboardList, text: "Quel travail ai-je donné, et pour quelle date ?" },
  { icon: TrendingUp, text: 'Où en est chacune de mes classes dans le programme ?' },
]

/**
 * Recherche en langage naturel dans ses propres cahiers de séance.
 *
 * **Replié par défaut, et c'est délibéré.** Cette page sert d'abord à REMPLIR le
 * cahier du jour ; un panneau de conversation ouvert en permanence repousserait
 * la saisie et l'historique sous la ligne de flottaison. Une fois déplié, il le
 * reste : celui qui l'ouvre s'en sert plusieurs fois d'affilée, typiquement pour
 * retrouver où il s'était arrêté avec chaque classe avant de saisir la séance
 * du jour.
 *
 * L'assistant ne voit QUE les séances de l'enseignant connecté. Ce n'est pas ce
 * composant qui l'assure — aucun identifiant n'est envoyé — mais le backend, qui
 * construit le corpus à partir du compte porté par le jeton.
 */
export function CahierAssistantPanel() {
  const [ouvert, setOuvert] = useState(false)

  return (
    <div className="overflow-hidden rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900">
      <button
        type="button"
        onClick={() => setOuvert((o) => !o)}
        aria-expanded={ouvert}
        className={cn(
          'flex w-full items-center justify-between gap-3 px-5 py-4 text-left transition-colors',
          'hover:bg-brand-bgSecondary/50 dark:hover:bg-slate-800/40'
        )}
      >
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-teal-600 to-teal-400 text-white">
            <Sparkles size={16} />
          </span>
          <div className="min-w-0">
            <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">
              Retrouver ce que j'ai fait
            </h3>
            <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
              Posez la question en français : l'assistant cherche dans vos cahiers et cite
              la séance d'où vient sa réponse.
            </p>
          </div>
        </div>
        <ChevronDown
          size={18}
          className={cn(
            'shrink-0 text-brand-textMuted transition-transform dark:text-slate-400',
            ouvert && 'rotate-180'
          )}
        />
      </button>

      {ouvert && (
        <div className="border-t border-brand-border px-5 pb-5 pt-4 dark:border-slate-700">
          <AssistantChat
            titre="Assistant cahier de séance"
            soustitre="Répond uniquement à partir de vos propres séances"
            suggestions={SUGGESTIONS}
            emptyTitre="Que cherchez-vous dans vos cahiers ?"
            emptyIndication="Une séance, un chapitre, un devoir donné — écrivez-le comme vous le diriez."
            attenteLabel="Recherche dans vos cahiers…"
            placeholder="Ex : quand ai-je traité les fractions en 7B ?"
            hauteur="h-[22rem]"
            ask={cahierAssistantApi.ask}
            rappel={
              <>
                <Info size={12} className="mt-0.5 shrink-0" />
                <span>
                  Seules les séances déjà renseignées sont consultables — un cahier vide
                  n'est pas indexé. L'assistant lit, il n'écrit jamais dans le cahier.
                </span>
              </>
            }
          />
        </div>
      )}
    </div>
  )
}
