import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { absenceApi, type TypeJustificatif } from '@/api/absence.api'
import { TYPE_JUSTIFICATIF_LABELS } from './absenceLabels'

interface Props {
  /** Absence à justifier : la ligne d'appel vient de la liste, jamais d'une saisie. */
  cible: { ligneAppelId: number; eleveNom: string; contexte: string } | null
  onClose: () => void
  onSubmitted?: () => void
}

/**
 * Dépôt d'un justificatif sur une absence déjà identifiée. L'auteur du dépôt est
 * le compte connecté (résolu par l'API) : rien à saisir ici en dehors du document.
 */
export function SoumettreJustificatifModal({ cible, onClose, onSubmitted }: Props) {
  const qc = useQueryClient()
  const [typeDocument, setTypeDocument] = useState<TypeJustificatif>('MEDICAL')
  const [referenceDocument, setReferenceDocument] = useState('')

  useEffect(() => {
    if (cible) {
      setTypeDocument('MEDICAL')
      setReferenceDocument('')
    }
  }, [cible])

  const soumettre = useMutation({
    mutationFn: () =>
      absenceApi.justificatifs.soumettre({
        ligneAppelId: cible!.ligneAppelId,
        typeDocument,
        referenceDocument: referenceDocument.trim() || undefined,
      }),
    onSuccess: () => {
      toast.success('Justificatif déposé — en attente de décision')
      qc.invalidateQueries({ queryKey: ['justificatifs'] })
      qc.invalidateQueries({ queryKey: ['absences-eleve'] })
      qc.invalidateQueries({ queryKey: ['appel-sessions'] })
      onSubmitted?.()
      onClose()
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Le dépôt du justificatif a échoué'),
  })

  return (
    <Modal open={cible !== null} onClose={onClose} title="Déposer un justificatif" size="md">
      {cible && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            soumettre.mutate()
          }}
          className="space-y-4"
        >
          <div className="rounded-lg border border-brand-border bg-brand-bgSecondary p-3 text-sm dark:border-slate-700 dark:bg-slate-800">
            <p className="font-medium text-brand-text dark:text-slate-100">{cible.eleveNom}</p>
            <p className="text-brand-textMuted dark:text-slate-400">{cible.contexte}</p>
          </div>

          <Select
            label="Type de document *"
            value={typeDocument}
            onChange={(e) => setTypeDocument(e.target.value as TypeJustificatif)}
            options={(Object.keys(TYPE_JUSTIFICATIF_LABELS) as TypeJustificatif[]).map((t) => ({
              value: t,
              label: TYPE_JUSTIFICATIF_LABELS[t],
            }))}
          />

          <Input
            label="Référence du document"
            placeholder="N° du certificat, nom du signataire…"
            value={referenceDocument}
            onChange={(e) => setReferenceDocument(e.target.value)}
          />

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" loading={soumettre.isPending}>
              Déposer
            </Button>
          </div>
        </form>
      )}
    </Modal>
  )
}
