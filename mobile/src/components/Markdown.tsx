import { Fragment } from 'react'
import { StyleSheet, Text, View } from 'react-native'

import { colors } from '../theme'

/**
 * Rendu du sous-ensemble de markdown que le modèle produit réellement.
 *
 * Pas de bibliothèque : celles qui existent tirent un arbre de dépendances
 * entier pour couvrir des tableaux, des images et du HTML que ce service ne
 * génère jamais — le prompt interdit d'ailleurs les tableaux. Quatre-vingts
 * lignes couvrent titres, listes et gras, et la mise en forme reste sous notre
 * contrôle plutôt que sous celui d'un thème externe.
 *
 * Les délimiteurs LaTeX sont retirés : le modèle écrit volontiers
 * `\(BC^2 = AB^2\)`, illisible tel quel et hors de portée sans moteur de rendu
 * mathématique. Le contenu, lui, se lit très bien sans eux.
 */

function nettoyer(ligne: string): string {
  return ligne
    .replace(/\\[()[\]]/g, '')
    .replace(/\\times/g, '×')
    .replace(/\\sqrt/g, '√')
    .trimEnd()
}

/** Découpe une ligne sur les `**gras**`, en gardant l'ordre des morceaux. */
function Inline({ texte, style }: { texte: string; style?: object }) {
  const morceaux = texte.split(/(\*\*[^*]+\*\*)/g).filter(Boolean)
  return (
    <Text style={style}>
      {morceaux.map((m, i) =>
        m.startsWith('**') && m.endsWith('**') ? (
          <Text key={i} style={styles.gras}>
            {m.slice(2, -2)}
          </Text>
        ) : (
          <Fragment key={i}>{m}</Fragment>
        )
      )}
    </Text>
  )
}

export function Markdown({ texte, couleur }: { texte: string; couleur?: string }) {
  const base = couleur ? { color: couleur } : undefined
  const lignes = texte.split('\n')

  return (
    <View style={{ gap: 6 }}>
      {lignes.map((brute, i) => {
        const ligne = nettoyer(brute)
        if (!ligne.trim()) return <View key={i} style={{ height: 2 }} />

        const titre = ligne.match(/^(#{1,4})\s+(.*)$/)
        if (titre) {
          return (
            <Inline
              key={i}
              texte={titre[2]}
              style={[styles.titre, titre[1].length >= 3 && styles.titrePetit, base]}
            />
          )
        }

        // Une puce peut être indentée (options d'un QCM sous leur question).
        const puce = ligne.match(/^(\s*)[-*]\s+(.*)$/)
        if (puce) {
          return (
            <View key={i} style={[styles.item, { marginLeft: puce[1].length > 1 ? 14 : 0 }]}>
              <Text style={[styles.marque, base]}>•</Text>
              <Inline texte={puce[2]} style={[styles.corps, base]} />
            </View>
          )
        }

        const numero = ligne.match(/^(\s*)(\d+)\.\s+(.*)$/)
        if (numero) {
          return (
            <View key={i} style={[styles.item, { marginLeft: numero[1].length > 1 ? 14 : 0 }]}>
              <Text style={[styles.marque, styles.gras, base]}>{numero[2]}.</Text>
              <Inline texte={numero[3]} style={[styles.corps, base]} />
            </View>
          )
        }

        return <Inline key={i} texte={ligne} style={[styles.corps, base]} />
      })}
    </View>
  )
}

const styles = StyleSheet.create({
  titre: { fontSize: 15, fontWeight: '800', color: colors.text, marginTop: 4 },
  titrePetit: { fontSize: 14 },
  corps: { fontSize: 14, lineHeight: 21, color: colors.text },
  gras: { fontWeight: '700' },
  item: { flexDirection: 'row', gap: 8, alignItems: 'flex-start' },
  marque: { fontSize: 14, lineHeight: 21, color: colors.textMuted, minWidth: 14 },
})
