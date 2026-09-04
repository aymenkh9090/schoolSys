/**
 * Deux niveaux : quatre onglets pour ce qu'on consulte, une pile pour ce qu'on
 * fait. L'appel et le cahier se referment ; l'accueil, le planning, les classes
 * et l'assistant restent toujours à un doigt.
 */

import type { NavigatorScreenParams } from '@react-navigation/native'

export type TabParamList = {
  Accueil: undefined
  Planning: undefined
  Classes: undefined
  Assistant: undefined
}

export type RootStackParamList = {
  /** `NavigatorScreenParams` : c'est ce qui autorise à viser un onglet précis. */
  Tabs: NavigatorScreenParams<TabParamList> | undefined
  /** Choix d'une séance du jour, avant l'appel ou avant le cahier. */
  Seances: { destination: 'appel' | 'cahier' }
  Appel: { appelId: number; titre: string }
  Cahier: { appelId: number; titre: string }
}
