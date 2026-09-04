/**
 * Identité SchoolSys — bleu.
 *
 * Le bleu porte l'application (en-têtes, actions, onglet actif) ; les couleurs
 * de statut lui échappent volontairement. Vert « présent » et rouge « absent »
 * ne sont pas des choix graphiques mais du sens : les repeindre en bleu pour
 * l'harmonie rendrait la feuille d'appel illisible d'un coup d'œil.
 */
export const colors = {
  /** Bleu principal — boutons, onglet actif, accents. */
  primary: '#2563eb',
  primaryDark: '#1d4ed8',
  primaryLight: '#60a5fa',
  /** Fond des surfaces bleues profondes (en-tête d'accueil). */
  navy: '#1e3a5f',
  /** Teinte très claire pour les aplats et les pastilles d'icône. */
  primarySoft: '#eff6ff',

  bg: '#f8fafc',
  bgSecondary: '#f1f5f9',
  text: '#1e293b',
  textMuted: '#64748b',
  border: '#e2e8f0',
  white: '#ffffff',

  // Statuts de présence — sémantiques, pas décoratifs.
  success: '#10b981',
  warning: '#f59e0b',
  danger: '#ef4444',
  violet: '#7c3aed',
}

export const radius = { sm: 8, md: 12, lg: 16, xl: 22 }

/**
 * Ombre portée, très basse.
 *
 * Sur un écran tenu à bout de bras dans une salle éclairée au néon, une bordure
 * grise à 1 px disparaît. Une ombre de 2 px, elle, tient encore. On garde la
 * bordure — elle porte le contraste en plein soleil — et l'ombre ne fait que
 * décoller la carte du fond, sans effet de relief.
 */
export const shadow = {
  shadowColor: '#0f172a',
  shadowOpacity: 0.06,
  shadowRadius: 8,
  shadowOffset: { width: 0, height: 2 },
  // Android n'utilise pas les `shadow*` : c'est `elevation` qui agit.
  elevation: 2,
} as const

/** Ombre franche, réservée à ce qui flotte au-dessus du contenu. */
export const shadowHaute = {
  shadowColor: '#0f172a',
  shadowOpacity: 0.14,
  shadowRadius: 16,
  shadowOffset: { width: 0, height: 6 },
  elevation: 8,
} as const

