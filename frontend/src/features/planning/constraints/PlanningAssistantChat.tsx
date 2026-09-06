import {
  CalendarClock,
  Gauge,
  HelpCircle,
  Lightbulb,
  SlidersHorizontal,
} from 'lucide-react'

import { AssistantChat, type Suggestion } from '@/components/assistant/AssistantChat'
import { planningAssistantApi } from '@/api/aiAssistant.api'

interface Props {
  schoolYearId?: number
  profileId?: number
}

const SUGGESTIONS: Suggestion[] = [
  { icon: Gauge, text: "Pourquoi cet emploi du temps n'est-il pas optimal ?" },
  { icon: SlidersHorizontal, text: 'Quelles contraintes sont actives en ce moment ?' },
  { icon: Lightbulb, text: 'Que peut-on améliorer dans ce planning ?' },
  { icon: CalendarClock, text: 'Où en est la dernière génération ?' },
]

/**
 * Conversation sur l'emploi du temps de l'établissement.
 *
 * L'assistant est en lecture seule : les outils dont il dispose ne savent que
 * lire. Il ne peut ni créer de contrainte, ni relancer une génération — pour
 * cela, l'utilisateur passe par la page des contraintes, où il voit et confirme
 * ce qui sera enregistré. Le rappel le dit explicitement, parce qu'un assistant
 * qui répond bien donne facilement l'impression qu'il agit aussi.
 *
 * Toute la mécanique d'affichage vit dans `AssistantChat`, partagée avec
 * l'assistant du cahier de séance. Ne restent ici que les décisions propres au
 * planning : ce qu'on suggère de demander, et où l'on renvoie pour agir.
 */
export function PlanningAssistantChat({ schoolYearId, profileId }: Props) {
  return (
    <AssistantChat
      titre="Assistant emploi du temps"
      soustitre="Répond à partir des données réelles de votre établissement"
      suggestions={SUGGESTIONS}
      emptyTitre="Que souhaitez-vous savoir sur le planning ?"
      emptyIndication="Écrivez votre question en français, ou partez d’une de celles-ci."
      attenteLabel="Consultation du planning…"
      ask={(question) => planningAssistantApi.ask(question, { schoolYearId, profileId })}
      socle={
        <>
          <span>lit l’emploi du temps et les contraintes de l’établissement</span>
          <span aria-hidden>·</span>
          <span>chaque réponse indique les outils consultés</span>
        </>
      }
      rappel={
        <>
          <HelpCircle size={12} className="mt-0.5 shrink-0" />
          <span>
            L’assistant consulte, mais ne modifie rien. Pour ajouter une règle, passez par{' '}
            <strong className="font-medium text-brand-text dark:text-slate-300">
              Planning › Contraintes
            </strong>
            .
          </span>
        </>
      }
    />
  )
}
