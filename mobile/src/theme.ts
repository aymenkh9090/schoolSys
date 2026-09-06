/**
 * Identité SchoolSys mobile — violet.
 *
 * Le violet porte l'application : boutons, onglet actif, en-têtes, assistant.
 * Les couleurs de statut lui échappent volontairement. Vert « présent », rouge
 * « absent », ambre « retard » et magenta « exclu » ne sont pas des choix
 * graphiques mais du sens : les repeindre en violet pour l'harmonie rendrait la
 * feuille d'appel illisible d'un coup d'œil, et c'est le seul écran qu'on
 * consulte debout, en trente secondes, devant trente élèves.
 */
export const colors = {
  /** Violet principal — boutons, onglet actif, accents. */
  primary: '#7c3aed',
  primaryDark: '#6d28d9',
  primaryLight: '#a78bfa',
  /** Violet profond — texte sur aplat clair, surfaces sombres. */
  navy: '#3b0764',
  /** Teinte très claire pour les aplats et les pastilles d'icône. */
  primarySoft: '#f5f3ff',

  bg: '#f6f7fb',
  bgSecondary: '#f1f2f8',
  text: '#1e293b',
  textMuted: '#64748b',
  border: '#e6e8f0',
  white: '#ffffff',

  // Statuts de présence — sémantiques, pas décoratifs.
  success: '#10b981',
  warning: '#f59e0b',
  danger: '#ef4444',
  /**
   * Exclusion. Magenta, et non violet : depuis que le violet est la couleur de
   * l'application, un statut violet se confondrait avec un élément d'interface.
   */
  exclu: '#db2777',
  /** Accent de l'assistant. Alias du violet principal, gardé pour la lisibilité du code. */
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
