import { useMemo } from 'react'

import { cn } from '@/lib/utils'

/**
 * Rendu d'une réponse d'assistant.
 *
 * Les modèles répondent en markdown — titres, listes numérotées, gras — parce
 * que le prompt le leur demande : un QCM de cinq questions, un corrigé, une
 * progression par chapitre ne se lisent pas en un bloc de texte. Jusqu'ici
 * l'écran affichait ce markdown tel quel, dièses et astérisques compris, ce qui
 * annulait la structure au lieu de la rendre.
 *
 * Pas de bibliothèque : celles qui existent tirent un arbre de dépendances pour
 * couvrir des images, du HTML brut et des notes de bas de page qu'aucun de ces
 * services ne produit. Le sous-ensemble réellement émis tient en quatre-vingts
 * lignes, et la mise en forme reste sous notre contrôle plutôt que sous celui
 * d'un thème externe.
 *
 * Séparé de `TexteArticle` (ContraintesOfficielles), qui rend le CORPUS et non
 * les réponses : celui-ci retire le gras — la circulaire en abuse — là où une
 * réponse s'en sert pour désigner la bonne option d'un QCM. Deux règles
 * opposées sur le même caractère : les fusionner reviendrait à paramétrer un
 * composant pour deux usages qui n'ont en commun que leur syntaxe.
 */

type Bloc =
  | { type: 'titre'; niveau: number; texte: string }
  | { type: 'paragraphe'; texte: string }
  | { type: 'liste'; ordonnee: boolean; items: { marque: string; texte: string; indente: boolean }[] }
  | { type: 'tableau'; entetes: string[]; lignes: string[][] }

/**
 * Les modèles écrivent volontiers `\(BC^2 = AB^2\)`, illisible tel quel et hors
 * de portée sans moteur de rendu mathématique. Le contenu se lit très bien sans
 * les délimiteurs.
 */
function nettoyer(ligne: string): string {
  return ligne
    .replace(/\\[()[\]]/g, '')
    .replace(/\\times/g, '×')
    .replace(/\\sqrt/g, '√')
    .replace(/\\frac\{([^}]*)\}\{([^}]*)\}/g, '$1/$2')
    .trimEnd()
}

function cellules(ligne: string): string[] {
  return ligne
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((c) => c.trim())
}

function decouper(texte: string): Bloc[] {
  const lignes = texte.split('\n').map(nettoyer)
  const blocs: Bloc[] = []
  let paragraphe: string[] = []

  const viderParagraphe = () => {
    if (paragraphe.length) {
      blocs.push({ type: 'paragraphe', texte: paragraphe.join(' ') })
      paragraphe = []
    }
  }

  for (let i = 0; i < lignes.length; i++) {
    const ligne = lignes[i]

    if (!ligne.trim()) {
      viderParagraphe()
      continue
    }

    const titre = ligne.match(/^(#{1,4})\s+(.*)$/)
    if (titre) {
      viderParagraphe()
      blocs.push({ type: 'titre', niveau: titre[1].length, texte: titre[2] })
      continue
    }

    // Un tableau : une ligne de cellules suivie du séparateur |---|---|.
    if (ligne.trim().startsWith('|') && /^\s*\|[\s:|-]+\|\s*$/.test(lignes[i + 1] ?? '')) {
      viderParagraphe()
      const entetes = cellules(ligne)
      const corps: string[][] = []
      i += 2
      while (i < lignes.length && lignes[i].trim().startsWith('|')) {
        corps.push(cellules(lignes[i]))
        i++
      }
      i--
      blocs.push({ type: 'tableau', entetes, lignes: corps })
      continue
    }

    const puce = ligne.match(/^(\s*)[-*]\s+(.*)$/)
    const numero = ligne.match(/^(\s*)(\d+)[.)]\s+(.*)$/)
    if (puce || numero) {
      viderParagraphe()
      const ordonnee = !!numero
      const item = {
        marque: numero ? `${numero[2]}.` : '•',
        texte: (numero ? numero[3] : puce![2]).trim(),
        // Les options d'un QCM arrivent indentées sous leur question : garder
        // ce décalage est ce qui distingue une réponse possible d'une question.
        indente: (numero ? numero[1] : puce![1]).length > 1,
      }
      const dernier = blocs[blocs.length - 1]
      if (dernier?.type === 'liste' && dernier.ordonnee === ordonnee) dernier.items.push(item)
      else blocs.push({ type: 'liste', ordonnee, items: [item] })
      continue
    }

    paragraphe.push(ligne.trim())
  }

  viderParagraphe()
  return blocs
}

/** Découpe une ligne sur les `**gras**`, en gardant l'ordre des morceaux. */
function Gras({ texte }: { texte: string }) {
  const morceaux = texte.split(/(\*\*[^*]+\*\*)/g).filter(Boolean)
  return (
    <>
      {morceaux.map((m, i) =>
        m.startsWith('**') && m.endsWith('**') ? (
          <strong key={i} className="font-semibold text-brand-text dark:text-slate-100">
            {m.slice(2, -2)}
          </strong>
        ) : (
          <span key={i}>{m}</span>
        )
      )}
    </>
  )
}

export function ReponseMarkdown({ texte }: { texte: string }) {
  const blocs = useMemo(() => decouper(texte), [texte])

  return (
    <div className="space-y-2.5 text-sm leading-relaxed">
      {blocs.map((bloc, i) => {
        if (bloc.type === 'titre') {
          return (
            <p
              key={i}
              className={cn(
                'font-semibold text-brand-text dark:text-slate-100',
                bloc.niveau <= 2 ? 'text-[15px]' : 'text-sm',
                i > 0 && 'pt-1'
              )}
            >
              <Gras texte={bloc.texte} />
            </p>
          )
        }

        if (bloc.type === 'liste') {
          return (
            <ul key={i} className="space-y-1.5">
              {bloc.items.map((item, j) => (
                <li
                  key={j}
                  className={cn('flex gap-2', item.indente && 'ms-5')}
                >
                  <span
                    className={cn(
                      'shrink-0 tabular-nums',
                      bloc.ordonnee
                        ? 'font-semibold text-brand-teal dark:text-teal-300'
                        : 'text-brand-textMuted'
                    )}
                  >
                    {item.marque}
                  </span>
                  <span className="min-w-0">
                    <Gras texte={item.texte} />
                  </span>
                </li>
              ))}
            </ul>
          )
        }

        if (bloc.type === 'tableau') {
          return (
            <div key={i} className="overflow-x-auto">
              <table className="w-full border-collapse text-xs">
                <thead>
                  <tr>
                    {bloc.entetes.map((cellule, j) => (
                      <th
                        key={j}
                        className="border border-brand-border px-2 py-1 text-left font-semibold dark:border-slate-700"
                      >
                        <Gras texte={cellule} />
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {bloc.lignes.map((ligne, j) => (
                    <tr key={j} className="even:bg-brand-bgSecondary/50 dark:even:bg-slate-800/40">
                      {ligne.map((cellule, k) => (
                        <td
                          key={k}
                          className="border border-brand-border px-2 py-1 dark:border-slate-700"
                        >
                          <Gras texte={cellule} />
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        }

        return (
          <p key={i}>
            <Gras texte={bloc.texte} />
          </p>
        )
      })}
    </div>
  )
}
