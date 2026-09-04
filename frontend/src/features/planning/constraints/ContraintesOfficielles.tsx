import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertCircle, BookMarked, FileText, Languages, Loader2, Wand2 } from 'lucide-react'

import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { consigneApi, type ConsigneArticle } from '@/api/aiAssistant.api'
import type { DslScope, DslSeverity } from '@/api/planning.api'
import { cn } from '@/lib/utils'
import { SCOPE_LABELS, SEVERITY_LABELS, SEVERITY_VARIANTS } from './dslLabels'

interface Props {
  /** Ouvre l'assistant pré-rempli avec le texte de l'article. */
  onActivate: (article: ConsigneArticle) => void
}

/**
 * Les règles que le ministère a déjà écrites, lisibles et activables.
 *
 * C'est le renversement que cet écran opère : jusqu'ici, un directeur qui
 * voulait une règle conforme devait la connaître par cœur, puis la formuler.
 * Les quinze recommandations de la circulaire n°66/2024 vivaient dans sa tête —
 * ni consultables, ni traçables, ni reliées aux contraintes réellement activées
 * dans le solveur. Ici il les lit, et il en active une d'un clic.
 *
 * Trois partis pris d'affichage, qui sont des partis pris de fond :
 *
 *  - **le texte de l'article est mis en avant, notre analyse est repliée.** La
 *    première est la parole du ministère, la seconde notre lecture du
 *    rattachement au solveur. Les présenter au même niveau laisserait croire
 *    que le ministère a écrit « CLASS_DAY » ;
 *  - **l'arabe d'origine est à un clic.** Une traduction qu'on ne peut pas
 *    confronter à sa source demande d'être crue sur parole. Le corpus étant
 *    transcrit à la main, c'est précisément ce qu'il faut pouvoir vérifier ;
 *  - **les articles non traduisibles restent affichés**, sans bouton. Les
 *    masquer donnerait une circulaire amputée ; leur donner un bouton qui
 *    n'active rien serait pire.
 */
export function ContraintesOfficielles({ onActivate }: Props) {
  // Corpus statique, versionné avec le code : il ne change qu'à un déploiement.
  // Un staleTime infini évite de le retélécharger à chaque retour sur l'onglet.
  const { data, isLoading, isError } = useQuery({
    queryKey: ['consigne-articles'],
    queryFn: consigneApi.listArticles,
    staleTime: Infinity,
  })

  const groupes = useMemo(() => grouperParSection(data?.articles ?? []), [data])

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-2 py-16 text-sm text-brand-textMuted dark:text-slate-400">
        <Loader2 size={16} className="animate-spin" />
        Chargement de la circulaire…
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="rounded-xl border border-brand-border bg-white p-8 text-center dark:border-slate-700 dark:bg-slate-900">
        <AlertCircle size={28} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-400" />
        <p className="text-sm text-brand-textMuted dark:text-slate-400">
          La circulaire n’a pas pu être chargée. Vérifiez que le service IA est démarré.
        </p>
      </div>
    )
  }

  const activables = data.articles.filter((a) => a.portee).length

  return (
    <div className="space-y-5">
      <header className="rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-blue/10">
            <BookMarked size={17} className="text-brand-blue" />
          </span>
          <div className="min-w-0">
            <h2 className="text-sm font-semibold text-brand-text dark:text-slate-100">
              {data.reference}
            </h2>
            <p className="mt-1 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
              {data.articles.length} articles, dont{' '}
              <strong className="text-brand-text dark:text-slate-200">{activables}</strong> se
              traduisent en contrainte de génération. Chaque article porte sa page et son texte
              arabe d’origine : la traduction se vérifie.
            </p>
            {/* Dire la méthode plutôt que la laisser deviner. Le document source
                est un scan sans couche texte : la transcription manuelle est une
                limite assumée, pas un détail à taire. */}
            <p className="mt-1.5 text-[11px] leading-relaxed text-brand-textMuted dark:text-slate-500">
              Le document officiel est un scan en arabe, sans couche texte exploitable. Le corpus
              a été transcrit et relu article par article, puis versionné avec l’application.
            </p>
          </div>
        </div>
      </header>

      {groupes.map(({ section, articles }) => (
        <section key={section} className="space-y-3">
          <h3 className="px-0.5 text-xs font-semibold uppercase tracking-wide text-brand-textMuted dark:text-slate-500">
            {section}
          </h3>
          <div className="space-y-3">
            {articles.map((article) => (
              <CarteArticle key={article.id} article={article} onActivate={onActivate} />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────

function CarteArticle({
  article,
  onActivate,
}: {
  article: ConsigneArticle
  onActivate: (article: ConsigneArticle) => void
}) {
  const [arabeVisible, setArabeVisible] = useState(false)
  const activable = Boolean(article.portee)

  return (
    <article
      className={cn(
        'rounded-xl border bg-white p-4 transition-colors dark:bg-slate-900',
        activable
          ? 'border-brand-border dark:border-slate-700'
          : 'border-dashed border-brand-border/70 dark:border-slate-700/70'
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-md bg-brand-bgSecondary px-2 py-0.5 font-mono text-xs font-semibold text-brand-text dark:bg-slate-800 dark:text-slate-200">
          § {article.id}
        </span>
        <span className="text-xs text-brand-textMuted dark:text-slate-400">{article.citation}</span>
        <span className="ml-auto">
          {article.severite ? (
            <Badge variant={SEVERITY_VARIANTS[article.severite as DslSeverity]}>
              {SEVERITY_LABELS[article.severite as DslSeverity]}
            </Badge>
          ) : (
            <Badge variant="info">Pour information</Badge>
          )}
        </span>
      </div>

      <TexteArticle texte={article.texte} />

      {activable && (
        <p className="mt-3 text-xs text-brand-textMuted dark:text-slate-400">
          S’applique à :{' '}
          <span className="font-medium text-brand-text dark:text-slate-200">
            {SCOPE_LABELS[article.portee as DslScope] ?? article.portee}
          </span>
        </p>
      )}

      {/* Notre lecture, repliée et annoncée comme telle. La circulaire ne dit
          ni « CLASS_DAY » ni « obligatoire » : ce rattachement est le nôtre,
          il pré-remplit l'écran de confirmation, et c'est un humain qui tranche. */}
      {article.commentaire && (
        <details className="mt-3 rounded-lg border border-brand-border p-2.5 dark:border-slate-700">
          <summary className="cursor-pointer text-xs font-medium text-brand-textMuted dark:text-slate-400">
            Comment cette règle est rattachée au générateur — lecture de l’équipe, pas du ministère
          </summary>
          <div className="mt-2 space-y-2 text-xs leading-relaxed text-brand-textMuted dark:text-slate-400">
            {article.commentaire.split('\n\n').map((para, i) => (
              <p key={i}>{enleverGras(para)}</p>
            ))}
          </div>
        </details>
      )}

      {arabeVisible && (
        <p
          dir="rtl"
          lang="ar"
          className="mt-3 rounded-lg bg-brand-bgSecondary p-3 text-sm leading-loose text-brand-text dark:bg-slate-800 dark:text-slate-200"
        >
          {article.texte_ar}
        </p>
      )}

      <div className="mt-3 flex flex-wrap justify-end gap-2">
        {article.texte_ar && (
          <Button size="sm" variant="outline" onClick={() => setArabeVisible((v) => !v)}>
            <Languages size={13} /> {arabeVisible ? 'Masquer' : 'Voir'} le texte arabe
          </Button>
        )}
        {activable ? (
          <Button size="sm" onClick={() => onActivate(article)}>
            <Wand2 size={13} /> Activer la règle
          </Button>
        ) : (
          <span
            className="inline-flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-500"
            title="Cet article encadre l’affectation ou les volumes horaires : il ne se traduit pas en contrainte de génération."
          >
            <FileText size={13} /> Consultable, non activable
          </span>
        )}
      </div>
    </article>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Rendu du texte
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Affiche le corps d'un article : des paragraphes, et de vrais tableaux.
 *
 * Les trois chunks de volumes horaires portent leurs données en tableau
 * markdown. Les rendre en texte brut donnerait une bouillie de barres
 * verticales, alors que ce sont exactement les chiffres qu'un directeur vient
 * chercher — combien d'heures d'arabe en 8ᵉ. D'où ce rendu minimal : un
 * paragraphe reste un paragraphe, une suite de lignes commençant par « | »
 * devient un vrai tableau.
 *
 * Volontairement limité au markdown que le corpus contient réellement
 * (paragraphes, tableaux, gras) : embarquer une bibliothèque markdown pour
 * trois fichiers écrits par nous serait payer une dépendance pour un besoin
 * qu'on maîtrise.
 */
function TexteArticle({ texte }: { texte: string }) {
  const blocs = useMemo(() => decouperEnBlocs(texte), [texte])

  return (
    <div className="mt-2.5 space-y-2.5">
      {blocs.map((bloc, i) =>
        bloc.type === 'tableau' ? (
          <div key={i} className="overflow-x-auto">
            <table className="w-full border-collapse text-xs">
              <thead>
                <tr>
                  {bloc.entetes.map((cellule, j) => (
                    <th
                      key={j}
                      className="border border-brand-border px-2 py-1 text-left font-semibold text-brand-text dark:border-slate-700 dark:text-slate-200"
                    >
                      {enleverGras(cellule)}
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
                        className={cn(
                          'border border-brand-border px-2 py-1 dark:border-slate-700',
                          k === 0
                            ? 'font-medium text-brand-text dark:text-slate-200'
                            : 'text-brand-textMuted dark:text-slate-400'
                        )}
                      >
                        {enleverGras(cellule)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p
            key={i}
            className="text-sm leading-relaxed text-brand-text dark:text-slate-200"
          >
            {enleverGras(bloc.texte)}
          </p>
        )
      )}
    </div>
  )
}

type Bloc =
  | { type: 'paragraphe'; texte: string }
  | { type: 'tableau'; entetes: string[]; lignes: string[][] }

/**
 * Découpe le corps en paragraphes et tableaux markdown.
 *
 * Exportée bien que l'écran soit son seul appelant : c'est une fonction pure
 * sur laquelle repose l'affichage des trois tableaux de volumes horaires, et
 * l'exporter permet de la vérifier sans monter React.
 */
export function decouperEnBlocs(texte: string): Bloc[] {
  const blocs: Bloc[] = []
  const lignes = texte.split('\n')
  let paragraphe: string[] = []
  let tableau: string[] = []

  const viderParagraphe = () => {
    const contenu = paragraphe.join(' ').trim()
    if (contenu) blocs.push({ type: 'paragraphe', texte: contenu })
    paragraphe = []
  }

  const viderTableau = () => {
    if (tableau.length >= 2) {
      const cellules = tableau.map(decouperLigne)
      // La deuxième ligne d'un tableau markdown est le séparateur (|---|---|) :
      // elle porte la mise en forme, jamais des données.
      blocs.push({ type: 'tableau', entetes: cellules[0], lignes: cellules.slice(2) })
    } else {
      // Une seule ligne « | » n'est pas un tableau : on la rend telle quelle
      // plutôt que de la perdre.
      tableau.forEach((l) => blocs.push({ type: 'paragraphe', texte: l }))
    }
    tableau = []
  }

  for (const ligne of lignes) {
    const nette = ligne.trim()
    if (nette.startsWith('|')) {
      viderParagraphe()
      tableau.push(nette)
    } else if (!nette) {
      viderTableau()
      viderParagraphe()
    } else {
      viderTableau()
      paragraphe.push(nette)
    }
  }
  viderTableau()
  viderParagraphe()

  return blocs
}

function decouperLigne(ligne: string): string[] {
  return ligne
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((c) => c.trim())
}

/** Retire les marqueurs de gras du markdown : le corpus en contient, pas l'écran. */
export function enleverGras(texte: string): string {
  return texte.replace(/\*\*(.+?)\*\*/g, '$1')
}

/**
 * Regroupe par section, dans l'ordre du document.
 *
 * L'ordre des articles est celui de la circulaire, et il doit le rester : un
 * directeur qui a le PDF sous les yeux doit retrouver le § II.3 entre le II.2
 * et le II.4. Un tri par sévérité ou par portée serait plus « logique » pour un
 * développeur et illisible pour lui.
 */
function grouperParSection(
  articles: ConsigneArticle[]
): { section: string; articles: ConsigneArticle[] }[] {
  const groupes: { section: string; articles: ConsigneArticle[] }[] = []
  for (const article of articles) {
    const dernier = groupes[groupes.length - 1]
    if (dernier && dernier.section === article.section) {
      dernier.articles.push(article)
    } else {
      groupes.push({ section: article.section, articles: [article] })
    }
  }
  return groupes
}
