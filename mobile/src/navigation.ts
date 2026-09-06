/**
 * Deux niveaux : quatre onglets pour la journée d'un enseignant, une pile pour
 * ce qui s'ouvre et se referme.
 *
 * Les onglets suivent les trois gestes du métier — faire l'appel, remplir le
 * cahier, demander à l'assistant — précédés de l'accueil qui les rassemble.
 * L'emploi du temps et les classes ont quitté la barre : on les consulte une
 * fois par semaine, pas une fois par heure, et ils restent à un doigt depuis
 * l'accueil.
 */

import type { NavigatorScreenParams } from '@react-navigation/native'

export type TabParamList = {
  Accueil: undefined
  /** Choix d'une séance du jour, puis feuille d'appel. */
  Appel: undefined
  /** Choix d'une séance du jour, puis cahier de texte. */
  Cahier: undefined
  /** `question` : demande pré-remplie, envoyée depuis une carte de l'accueil. */
  Assistant: { question?: string } | undefined
}

export type RootStackParamList = {
  /** `NavigatorScreenParams` : c'est ce qui autorise à viser un onglet précis. */
  Tabs: NavigatorScreenParams<TabParamList> | undefined
  /** La feuille d'appel elle-même, une fois la séance choisie. */
  FeuilleAppel: { appelId: number; titre: string; groupeClasseId?: number }
  CahierSeance: { appelId: number; titre: string }
  Planning: undefined
  Classes: undefined
}
