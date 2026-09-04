import { useQuery } from '@tanstack/react-query'
import { Bot, Database, Eye, MessagesSquare } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { AssistantInfoCards } from '@/components/assistant/AssistantInfoCards'
import { planningApi, type ConstraintProfile } from '@/api/planning.api'
import { PlanningAssistantChat } from './constraints/PlanningAssistantChat'

/**
 * L'assistant emploi du temps, comme entrée à part entière du menu Planning.
 *
 * Il était auparavant un onglet de la configuration des contraintes, où on ne
 * le trouvait qu'en venant régler des règles. Or on l'ouvre pour comprendre un
 * planning déjà généré — pas pour en modifier la configuration : sa place est
 * à côté de la consultation et de la génération.
 *
 * La page reprend la bannière teal→ambre des pages mises en avant (tableaux de
 * bord, rapports) plutôt que l'en-tête sobre des écrans de gestion : on ne
 * vient pas ici remplir un formulaire.
 *
 * Le profil de contraintes n'est qu'un contexte de lecture : l'assistant s'en
 * sert pour savoir quelles règles sont actives quand on l'interroge. On prend
 * le profil actif, sans demander à l'utilisateur de le choisir.
 */
export default function AssistantPlanning() {
  const { data: profiles = [], isLoading } = useQuery({
    queryKey: ['constraint-profiles'],
    queryFn: planningApi.constraints.profiles.list,
  })

  const profile: ConstraintProfile | undefined = profiles.find((p) => p.active) ?? profiles[0]

  return (
    <div className="space-y-6">
      <PageHero
        title="Assistant emploi du temps"
        subtitle="Posez vos questions sur le planning de l’établissement, en français"
        icon={Bot}
      />

      <AssistantInfoCards
        infos={[
          {
            icon: MessagesSquare,
            title: 'En langage courant',
            text: 'Écrivez comme vous le diriez à un collègue. Aucun vocabulaire technique à connaître.',
          },
          {
            icon: Database,
            title: 'Sur vos données',
            text: 'Chaque réponse cite les sources consultées : contraintes actives, générations, séances.',
          },
          {
            icon: Eye,
            title: 'Sans rien modifier',
            text: 'L’assistant lit le planning, il ne le change pas. Les règles se créent depuis les contraintes.',
          },
        ]}
      />

      {isLoading ? (
        <div className="rounded-2xl border border-brand-border bg-white p-10 text-center text-sm text-brand-textMuted dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
          Chargement…
        </div>
      ) : (
        // L'assistant reste utile sans profil : les questions sur le planning
        // généré ne dépendent pas des contraintes configurées.
        <PlanningAssistantChat
          schoolYearId={profile?.academicYearId}
          profileId={profile?.idConstraintProfile}
        />
      )}
    </div>
  )
}
