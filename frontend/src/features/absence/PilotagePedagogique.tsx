import {
  AlertTriangle,
  BookMarked,
  Eye,
  GraduationCap,
  Info,
  NotebookPen,
  Search,
  TrendingUp,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { AssistantChat, type Suggestion } from '@/components/assistant/AssistantChat'
import { AssistantInfoCards } from '@/components/assistant/AssistantInfoCards'
import { cahierAssistantApi } from '@/api/aiAssistant.api'

// Deux questions d'AVANCEMENT (comptage exact) et deux de CONTENU (recherche
// sémantique) : les suggestions donnent à voir les deux capacités réelles de
// l'assistant. La dernière est celle qui justifie la page à elle seule — les
// remarques des enseignants sont saisies séance après séance et ne sont
// aujourd'hui relues par personne.
const SUGGESTIONS: Suggestion[] = [
  { icon: TrendingUp, text: 'Où en est chaque classe dans son programme ?' },
  { icon: AlertTriangle, text: 'Quelles classes ont pris du retard ?' },
  { icon: BookMarked, text: "Qu'est-ce qui a été traité en mathématiques ce mois-ci ?" },
  { icon: NotebookPen, text: 'Quelles difficultés les enseignants ont-ils signalées ?' },
]

/**
 * Pilotage pédagogique : ce qui est réellement enseigné dans l'établissement.
 *
 * La donnée existe depuis toujours — chaque enseignant renseigne le sujet, le
 * chapitre, les activités, ses remarques et le travail donné, séance après
 * séance. Elle n'était simplement lisible que séance par séance : un directeur
 * ne parcourt pas quatre cents cahiers. La recherche sémantique la rend
 * interrogeable d'un bloc, et c'est le seul moyen d'y arriver — « avoir pris du
 * retard » n'est pas une colonne, c'est une notion qui vit dans du texte.
 *
 * La page est en lecture seule et le restera. Le cahier de séance fait foi sur
 * ce qui a été enseigné, il se verrouille, et sa plume appartient à
 * l'enseignant : aucun outil de l'assistant ne sait y écrire.
 *
 * Même composant de conversation, même index et mêmes outils que l'assistant du
 * cahier côté enseignant. Seul le PÉRIMÈTRE change, et ce n'est pas cette page
 * qui en décide : le backend construit le corpus à partir du compte porté par
 * le jeton. Un enseignant qui atteindrait cette URL n'y verrait que ses propres
 * séances — d'où la restriction de route, qui évite la confusion, pas une fuite.
 */
export default function PilotagePedagogique() {
  return (
    <div className="space-y-6">
      <PageHero
        title="Suivi pédagogique"
        subtitle="Ce qui est réellement enseigné dans l’établissement, d’après les cahiers de séance"
        icon={GraduationCap}
      />

      <AssistantInfoCards
        infos={[
          {
            icon: Search,
            title: 'Sur les cahiers de séance',
            text: 'Sujets, chapitres, activités, remarques et travail donné, saisis par vos enseignants.',
          },
          {
            icon: Eye,
            title: 'Chaque réponse est traçable',
            text: 'Un extrait cité porte sa date, sa classe et sa matière : vous pouvez toujours remonter à la séance.',
          },
          {
            icon: Info,
            title: 'Un constat, pas un jugement',
            text: 'Un écart d’avancement appelle une vérification auprès de l’enseignant, pas une conclusion.',
          },
        ]}
      />

      <AssistantChat
        titre="Suivi pédagogique"
        soustitre="Répond à partir des cahiers de séance de votre établissement"
        suggestions={SUGGESTIONS}
        emptyTitre="Que voulez-vous savoir de l’avancement des classes ?"
        emptyIndication="Une classe, une matière, un chapitre — écrivez-le en français, ou partez d’une de ces questions."
        attenteLabel="Lecture des cahiers de séance…"
        placeholder="Ex : la 7B a-t-elle pris du retard en maths ?"
        ask={cahierAssistantApi.ask}
        rappel={
          <>
            <Info size={12} className="mt-0.5 shrink-0" />
            <span>
              Seules les séances déjà renseignées sont consultables : une classe absente des
              réponses peut simplement avoir un cahier vide, ce qui est en soi une information.
            </span>
          </>
        }
      />
    </div>
  )
}
