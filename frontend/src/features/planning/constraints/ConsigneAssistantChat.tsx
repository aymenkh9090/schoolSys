import { BookMarked, CalendarDays, Clock, DoorOpen, Sigma } from 'lucide-react'

import { AssistantChat, type Suggestion } from '@/components/assistant/AssistantChat'
import { consigneApi } from '@/api/aiAssistant.api'

const SUGGESTIONS: Suggestion[] = [
  { icon: Sigma, text: "Combien d'heures de mathématiques en 8ᵉ année ?" },
  { icon: Clock, text: 'Un élève peut-il avoir 8 heures de cours dans la journée ?' },
  { icon: CalendarDays, text: "Puis-je confier 5 heures d'affilée à un enseignant le samedi ?" },
  { icon: DoorOpen, text: 'Où doivent se dérouler les travaux pratiques ?' },
]

/**
 * Poser une question à la circulaire ministérielle.
 *
 * Les quatre suggestions ne sont pas décoratives : elles montrent les deux
 * natures de question auxquelles le corpus répond — une règle d'organisation
 * (durée, alternance, salles) et un volume horaire chiffré. Un utilisateur qui
 * ne sait pas ce qu'un outil sait faire ne lui demande rien, ou lui demande
 * l'impossible et conclut qu'il ne marche pas.
 *
 * Toute la mécanique de conversation vient de `AssistantChat`, partagée avec
 * les assistants planning et cahier de séance. La seule adaptation est faite
 * dans `consigneApi.ask` : les citations d'articles alimentent l'emplacement
 * « sources » du composant, qui existe précisément pour dire sur quoi une
 * réponse s'appuie.
 */
export function ConsigneAssistantChat() {
  return (
    <AssistantChat
      titre="Poser une question à la circulaire"
      soustitre="Répond uniquement à partir du texte de la circulaire n°66/2024, article par article"
      suggestions={SUGGESTIONS}
      emptyTitre="Que dit la circulaire ?"
      emptyIndication="Posez votre question en français, ou partez d’une de celles-ci."
      attenteLabel="Lecture de la circulaire…"
      placeholder="Combien d’heures d’arabe en 7ᵉ année ?"
      hauteur="h-[22rem]"
      ask={consigneApi.ask}
      rappel={
        <>
          <BookMarked size={12} className="mt-0.5 shrink-0" />
          {/* Le contrat de cet assistant, dit en une phrase : il n'a pas d'autre
              source que le texte, et il le dit quand le texte ne répond pas.
              C'est ce qui distingue une réponse citée d'une réponse plausible. */}
          <span>
            Chaque réponse cite l’article dont elle vient. Quand la circulaire ne traite pas le
            sujet, l’assistant le dit plutôt que d’inventer une règle vraisemblable.
          </span>
        </>
      }
    />
  )
}
