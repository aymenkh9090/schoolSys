import { useCallback, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Upload, Download, CheckCircle2, AlertCircle, FileSpreadsheet, X } from 'lucide-react'

import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/utils'

/**
 * Ce que renvoient les endpoints d'import de la plateforme. Les trois écrans
 * concernés (salles, élèves, enseignants) partagent cette forme à un champ
 * près : `codeSalle` n'existe que pour les salles.
 */
export interface ImportResultLike {
  totalLignes?: number
  importes: number
  ignores: number
  erreurs?: Array<{ ligne: number; message: string; codeSalle?: string }>
}

interface ImportModalProps {
  open: boolean
  onClose: () => void
  titre: string
  /** « salle » / « salles » : accorde les libellés du récapitulatif. */
  entite: { singulier: string; pluriel: string }
  /** Accord des participes — « 12 importées » contre « 12 importés ». */
  genre?: 'f' | 'm'
  onImport: (file: File) => Promise<ImportResultLike>
  /** Appelé après un import réussi, pour invalider les requêtes concernées. */
  onImported?: () => void
  templates?: {
    csv?: () => Promise<Blob>
    excel?: () => Promise<Blob>
    /** Nom du fichier proposé au téléchargement, sans extension. */
    basename: string
  }
  /**
   * En-têtes attendus par l'import. Seule la modale des enseignants les
   * affichait ; c'est pourtant ce qui évite d'ouvrir le modèle pour vérifier
   * qu'on a bien nommé une colonne.
   */
  colonnes?: string[]
}

const EXTENSIONS = ['csv', 'xlsx', 'xls']

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** Tuile du récapitulatif. Reste grise à zéro : un zéro n'est pas une alerte. */
function Tuile({ valeur, libelle, ton }: { valeur: number; libelle: string; ton: 'neutre' | 'succes' | 'alerte' | 'erreur' }) {
  const vif = valeur > 0
  const couleurs = {
    neutre: 'bg-brand-bgSecondary text-brand-text dark:bg-slate-800 dark:text-slate-200',
    succes: vif ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' : '',
    alerte: vif ? 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400' : '',
    erreur: vif ? 'bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400' : '',
  }[ton] || 'bg-brand-bgSecondary text-brand-textMuted dark:bg-slate-800 dark:text-slate-400'

  return (
    <div className={cn('rounded-lg p-3 text-center', couleurs)}>
      <p className="text-2xl font-bold tabular-nums leading-none">{valeur}</p>
      <p className="mt-1.5 text-xs font-medium">{libelle}</p>
    </div>
  )
}

/**
 * Import d'un fichier CSV/Excel, avec son récapitulatif.
 *
 * Mutualisé parce que les trois écrans qui importent posaient exactement le
 * même geste — télécharger un modèle, le remplir, le déposer, lire ce qui est
 * passé — dans trois mises en page différentes, dont deux ne montraient pas les
 * mêmes informations. Le pire écart n'était pas visuel : l'un tronquait la
 * liste d'erreurs à cinq lignes sans dire qu'il en restait.
 */
export function ImportModal({
  open, onClose, titre, entite, genre = 'm', onImport, onImported, templates, colonnes,
}: ImportModalProps) {
  const [dragging, setDragging] = useState(false)
  const [pending, setPending] = useState(false)
  const [nomFichier, setNomFichier] = useState<string | null>(null)
  const [result, setResult] = useState<ImportResultLike | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const e = genre === 'f' ? 'e' : ''

  const lancerImport = useCallback(async (file: File) => {
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!EXTENSIONS.includes(ext ?? '')) {
      toast.error('Format non supporté. Utilisez .csv, .xlsx ou .xls')
      return
    }
    setNomFichier(file.name)
    setPending(true)
    try {
      const data = await onImport(file)
      setResult(data)
      onImported?.()
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(message ?? "Erreur lors de l'import")
      setNomFichier(null)
    } finally {
      setPending(false)
    }
  }, [onImport, onImported])

  const onDrop = useCallback((ev: React.DragEvent) => {
    ev.preventDefault()
    setDragging(false)
    // Un import déjà en cours : le second fichier écraserait le récapitulatif
    // du premier sans que rien ne le dise.
    if (pending) return
    const file = ev.dataTransfer.files[0]
    if (file) void lancerImport(file)
  }, [lancerImport, pending])

  const reinitialiser = () => { setResult(null); setNomFichier(null) }
  const fermer = () => { reinitialiser(); onClose() }

  const erreurs = result?.erreurs ?? []
  const sansErreur = result !== null && erreurs.length === 0

  return (
    <Modal open={open} onClose={fermer} title={titre} size="xl">
      <div className="space-y-5">
        {!result && (
          <>
            {/* Le geste n'était écrit nulle part : on découvrait les modèles
                après coup, une fois le fichier refusé. */}
            <ol className="grid gap-2 sm:grid-cols-3">
              {[
                'Téléchargez le modèle',
                `Remplissez une ligne par ${entite.singulier}`,
                'Déposez le fichier ci-dessous',
              ].map((etape, i) => (
                <li
                  key={i}
                  className="flex items-start gap-2.5 rounded-lg border border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40"
                >
                  <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-teal text-[11px] font-bold text-white">
                    {i + 1}
                  </span>
                  <span className="text-xs text-brand-text dark:text-slate-300">{etape}</span>
                </li>
              ))}
            </ol>

            {colonnes && colonnes.length > 0 && (
              <div>
                <p className="mb-1.5 text-xs font-medium text-brand-text dark:text-slate-300">
                  Colonnes attendues
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {colonnes.map((c) => (
                    <span
                      key={c}
                      className="rounded-md border border-brand-border bg-brand-bgSecondary px-2 py-0.5 font-mono text-[11px] text-brand-textMuted dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400"
                    >
                      {c}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {templates && (
              <div className="flex flex-wrap gap-2">
                {templates.csv && (
                  <Button
                    variant="outline" size="sm"
                    onClick={() => templates.csv!().then((b) => downloadBlob(b, `${templates.basename}.csv`))}
                  >
                    <Download size={14} /> Modèle CSV
                  </Button>
                )}
                {templates.excel && (
                  <Button
                    variant="outline" size="sm"
                    onClick={() => templates.excel!().then((b) => downloadBlob(b, `${templates.basename}.xlsx`))}
                  >
                    <Download size={14} /> Modèle Excel
                  </Button>
                )}
              </div>
            )}

            <div
              onDragOver={(ev) => { ev.preventDefault(); if (!pending) setDragging(true) }}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              onClick={() => !pending && inputRef.current?.click()}
              className={cn(
                'rounded-xl border-2 border-dashed p-10 text-center transition-colors',
                pending
                  ? 'cursor-wait border-brand-border bg-brand-bgSecondary/50 dark:border-slate-700 dark:bg-slate-800/40'
                  : dragging
                    ? 'cursor-pointer border-brand-blue bg-blue-50 dark:bg-blue-500/10'
                    : 'cursor-pointer border-brand-border hover:border-brand-blue hover:bg-blue-50/40 dark:border-slate-700 dark:hover:bg-blue-500/10'
              )}
            >
              {pending ? (
                <>
                  <span className="mx-auto mb-3 block h-8 w-8 animate-spin rounded-full border-2 border-brand-blue border-t-transparent" />
                  <p className="text-sm font-medium text-brand-text dark:text-slate-200">Import en cours…</p>
                  {nomFichier && (
                    <p className="mt-1 truncate text-xs text-brand-textMuted dark:text-slate-400">{nomFichier}</p>
                  )}
                </>
              ) : (
                <>
                  <Upload size={32} className="mx-auto mb-3 text-brand-textMuted dark:text-slate-400" />
                  <p className="text-sm font-medium text-brand-text dark:text-slate-200">Glisser-déposer un fichier ici</p>
                  <p className="mt-1 text-xs text-brand-textMuted dark:text-slate-400">
                    ou cliquer pour sélectionner — CSV, XLSX, XLS
                  </p>
                </>
              )}
              <input
                ref={inputRef} type="file" className="hidden" accept=".csv,.xlsx,.xls"
                onChange={(ev) => {
                  const f = ev.target.files?.[0]
                  // Réinitialisé pour que redéposer le MÊME fichier relance l'import.
                  ev.target.value = ''
                  if (f) void lancerImport(f)
                }}
              />
            </div>
          </>
        )}

        {result && (
          <div className="space-y-4">
            <div className={cn(
              'flex items-start gap-3 rounded-xl p-3',
              sansErreur
                ? 'bg-emerald-50 dark:bg-emerald-500/10'
                : 'bg-amber-50 dark:bg-amber-500/10'
            )}>
              {sansErreur
                ? <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" />
                : <AlertCircle size={18} className="mt-0.5 shrink-0 text-amber-600 dark:text-amber-400" />}
              <div className="min-w-0">
                <p className={cn(
                  'text-sm font-medium',
                  sansErreur ? 'text-emerald-700 dark:text-emerald-400' : 'text-amber-700 dark:text-amber-400'
                )}>
                  {sansErreur
                    ? `Import terminé — ${result.importes} ${result.importes > 1 ? entite.pluriel : entite.singulier} ajouté${e}${result.importes > 1 ? 's' : ''}`
                    : `Import partiel — ${erreurs.length} ligne${erreurs.length > 1 ? 's' : ''} en erreur`}
                </p>
                {nomFichier && (
                  <p className="mt-0.5 flex items-center gap-1.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                    <FileSpreadsheet size={12} className="shrink-0" /> {nomFichier}
                  </p>
                )}
              </div>
            </div>

            {/* `totalLignes` était renvoyé par l'API et affiché nulle part —
                c'est pourtant le chiffre qui dit si le fichier a été lu en entier. */}
            <div className={cn('grid gap-3', result.totalLignes === undefined ? 'grid-cols-3' : 'grid-cols-2 sm:grid-cols-4')}>
              {result.totalLignes !== undefined && (
                <Tuile valeur={result.totalLignes} libelle="lignes lues" ton="neutre" />
              )}
              <Tuile valeur={result.importes} libelle={`importé${e}${result.importes > 1 ? 's' : ''}`} ton="succes" />
              <Tuile valeur={result.ignores} libelle={`ignoré${e}${result.ignores > 1 ? 's' : ''}`} ton="alerte" />
              <Tuile valeur={erreurs.length} libelle={`erreur${erreurs.length > 1 ? 's' : ''}`} ton="erreur" />
            </div>

            {erreurs.length > 0 && (
              <div>
                <p className="mb-2 text-sm font-medium text-brand-text dark:text-slate-200">
                  Lignes rejetées
                </p>
                {/* Toutes les erreurs, pas les cinq premières : une ligne
                    invisible est une ligne qui ne sera jamais corrigée. */}
                <div className="max-h-56 space-y-1 overflow-y-auto rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-500/20 dark:bg-red-500/10">
                  {erreurs.map((err, i) => (
                    <p key={i} className="text-xs text-red-700 dark:text-red-400">
                      <span className="font-semibold tabular-nums">Ligne {err.ligne}</span>
                      {err.codeSalle ? ` — ${err.codeSalle}` : ''} : {err.message}
                    </p>
                  ))}
                </div>
              </div>
            )}

            <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
              <Button variant="outline" onClick={reinitialiser}>
                <Upload size={14} /> Importer un autre fichier
              </Button>
              <Button onClick={fermer}>
                <X size={14} /> Fermer
              </Button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}
