import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle, FileText, Inbox, XCircle } from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Select } from '@/components/ui/Select'
import { DataTable, type Column } from '@/components/ui/DataTable'
import {
  pointageApi,
  type JustificatifPointage,
  type PresencePersonnel,
  type TypeJustificatifPointage,
} from '@/api/pointage.api'
import { formatDate } from '@/lib/utils'
import { todayIso } from '@/features/absence/absenceLabels'

const TYPE_LABELS: Record<TypeJustificatifPointage, string> = {
  CERTIFICAT_MEDICAL: 'Certificat médical',
  DOCUMENT_ADMINISTRATIF: 'Document administratif',
  AUTRE: 'Autre',
}

const PERIODE_LABELS = { MATIN: 'Matin', APRES_MIDI: 'Après-midi' } as const

/** Le serveur n'accepte un justificatif que sur ces statuts de pointage. */
const STATUTS_JUSTIFIABLES = ['ABSENT', 'ABSENCE_NON_JUSTIFIEE'] as const

/**
 * Justificatifs d'absence du personnel : dépôt sur une absence pointée, puis
 * décision. Valider un justificatif bascule le pointage en « absence justifiée ».
 */
export default function JustificatifsPointage() {
  const qc = useQueryClient()

  const [depotOuvert, setDepotOuvert] = useState(false)
  const [jour, setJour] = useState(todayIso())
  const [presenceId, setPresenceId] = useState('')
  const [typeDocument, setTypeDocument] = useState<TypeJustificatifPointage>('CERTIFICAT_MEDICAL')
  const [description, setDescription] = useState('')
  const [decision, setDecision] = useState<{ justif: JustificatifPointage; approuver: boolean } | null>(null)
  const [motif, setMotif] = useState('')

  const { data: enAttente = [], isLoading } = useQuery({
    queryKey: ['justificatifs-pointage', 'en-attente'],
    queryFn: pointageApi.justificatifs.enAttente,
  })

  // Les absences du jour choisi alimentent la liste déroulante du dépôt : on ne
  // saisit jamais un identifiant de pointage à la main.
  const { data: rapport } = useQuery({
    queryKey: ['pointage-rapport', jour],
    queryFn: () => pointageApi.rapport(jour),
    enabled: depotOuvert,
  })

  const absencesDuJour = useMemo<PresencePersonnel[]>(
    () =>
      (rapport?.enregistrements ?? []).filter((p) =>
        (STATUTS_JUSTIFIABLES as readonly string[]).includes(p.statut)
      ),
    [rapport]
  )

  // Changer de jour rend la sélection précédente caduque.
  useEffect(() => setPresenceId(''), [jour])

  const soumettre = useMutation({
    mutationFn: () =>
      pointageApi.justificatifs.soumettre({
        presencePersonnelId: Number(presenceId),
        typeDocument,
        description: description.trim(),
      }),
    onSuccess: () => {
      toast.success('Justificatif déposé — en attente de décision')
      qc.invalidateQueries({ queryKey: ['justificatifs-pointage'] })
      setDepotOuvert(false)
      setPresenceId('')
      setDescription('')
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Le dépôt a échoué'),
  })

  const traiter = useMutation({
    mutationFn: ({ justif, approuver }: { justif: JustificatifPointage; approuver: boolean }) =>
      pointageApi.justificatifs.traiter(justif.id, {
        approuve: approuver,
        motifRejet: approuver ? undefined : motif.trim(),
      }),
    onSuccess: (_, { approuver }) => {
      toast.success(approuver ? 'Justificatif approuvé — absence justifiée' : 'Justificatif rejeté')
      qc.invalidateQueries({ queryKey: ['justificatifs-pointage'] })
      qc.invalidateQueries({ queryKey: ['pointage-rapport'] })
      setDecision(null)
      setMotif('')
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Le traitement a échoué'),
  })

  const columns: Column<JustificatifPointage>[] = [
    {
      key: 'membre',
      header: 'Membre',
      render: (j) => (
        <div>
          <p className="font-medium text-brand-text dark:text-slate-200">{j.nomMembre ?? `Membre #${j.membrePersonnelId}`}</p>
          {j.typePersonnel && <p className="text-xs text-brand-textMuted dark:text-slate-400">{j.typePersonnel}</p>}
        </div>
      ),
    },
    {
      key: 'pointage',
      header: 'Absence',
      render: (j) => (
        <span className="text-sm text-brand-text dark:text-slate-200">
          {j.datePointage ? formatDate(j.datePointage) : '—'}
          {j.periode ? ` · ${PERIODE_LABELS[j.periode]}` : ''}
        </span>
      ),
    },
    {
      key: 'document',
      header: 'Document',
      render: (j) => (
        <div>
          <p className="text-sm text-brand-text dark:text-slate-200">{TYPE_LABELS[j.typeDocument]}</p>
          <p className="text-xs text-brand-textMuted dark:text-slate-400">{j.description}</p>
        </div>
      ),
    },
    {
      key: 'soumisA',
      header: 'Déposé le',
      render: (j) => <span className="text-sm text-brand-textMuted dark:text-slate-400">{formatDate(j.soumisA)}</span>,
    },
    {
      key: 'statut',
      header: 'Statut',
      render: () => <Badge variant="warning">En attente</Badge>,
    },
    {
      key: 'actions',
      header: '',
      className: 'w-px whitespace-nowrap',
      render: (j) => (
        <div className="flex gap-1">
          <Button size="sm" variant="outline" onClick={() => { setDecision({ justif: j, approuver: true }); setMotif('') }}>
            <CheckCircle size={13} /> Approuver
          </Button>
          <Button size="sm" variant="outline" onClick={() => { setDecision({ justif: j, approuver: false }); setMotif('') }}>
            <XCircle size={13} /> Rejeter
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHero
        title="Justificatifs du personnel"
        subtitle="Absences du personnel à justifier : dépôt du document et décision"
        icon={FileText}
        actions={
          <Button variant="outline" className="border-white/30 bg-white/15 text-white backdrop-blur hover:bg-white/25" onClick={() => setDepotOuvert(true)}>
            <FileText size={16} /> Déposer un justificatif
          </Button>
        }
      />

      <div className="flex items-start gap-3 rounded-xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-500/20 dark:bg-blue-500/10">
        <Inbox size={18} className="mt-0.5 shrink-0 text-brand-blue" />
        <p className="text-sm text-blue-800 dark:text-blue-300">
          Cette file ne montre que les justificatifs en attente. Une fois la décision prise, le pointage
          concerné bascule en « absence justifiée » et se consulte depuis l'historique de pointage.
        </p>
      </div>

      <DataTable
        columns={columns}
        data={enAttente}
        keyField="id"
        loading={isLoading}
        emptyMessage="Aucun justificatif du personnel en attente."
      />

      {/* Dépôt : l'absence est choisie dans la liste des pointages du jour */}
      <Modal open={depotOuvert} onClose={() => setDepotOuvert(false)} title="Déposer un justificatif" size="md">
        <form
          onSubmit={(e) => {
            e.preventDefault()
            soumettre.mutate()
          }}
          className="space-y-4"
        >
          <div>
            <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Jour du pointage</label>
            <input
              type="date"
              value={jour}
              max={todayIso()}
              onChange={(e) => setJour(e.target.value)}
              className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            />
          </div>

          <Select
            label="Absence à justifier *"
            placeholder={absencesDuJour.length ? 'Sélectionner une absence' : 'Aucune absence pointée ce jour'}
            value={presenceId}
            onChange={(e) => setPresenceId(e.target.value)}
            disabled={absencesDuJour.length === 0}
            options={absencesDuJour.map((p) => ({
              value: p.id,
              label: `${p.nomMembre ?? `Membre #${p.membrePersonnelId}`} — ${PERIODE_LABELS[p.periode]}`,
            }))}
          />

          <Select
            label="Type de document *"
            value={typeDocument}
            onChange={(e) => setTypeDocument(e.target.value as TypeJustificatifPointage)}
            options={(Object.keys(TYPE_LABELS) as TypeJustificatifPointage[]).map((t) => ({
              value: t,
              label: TYPE_LABELS[t],
            }))}
          />

          <Input
            label="Description *"
            placeholder="Nature du document, référence, dates couvertes…"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
            <Button variant="outline" type="button" onClick={() => setDepotOuvert(false)}>
              Annuler
            </Button>
            <Button type="submit" loading={soumettre.isPending} disabled={!presenceId || !description.trim()}>
              Déposer
            </Button>
          </div>
        </form>
      </Modal>

      {/* Décision */}
      <Modal
        open={decision !== null}
        onClose={() => { setDecision(null); setMotif('') }}
        title={decision?.approuver ? 'Approuver le justificatif' : 'Rejeter le justificatif'}
        size="md"
      >
        {decision && (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              traiter.mutate(decision)
            }}
            className="space-y-4"
          >
            <div className="rounded-lg border border-brand-border bg-brand-bgSecondary p-3 text-sm dark:border-slate-700 dark:bg-slate-800">
              <p className="font-medium text-brand-text dark:text-slate-100">
                {decision.justif.nomMembre ?? `Membre #${decision.justif.membrePersonnelId}`}
              </p>
              <p className="text-brand-textMuted dark:text-slate-400">
                {decision.justif.datePointage ? formatDate(decision.justif.datePointage) : '—'} ·{' '}
                {TYPE_LABELS[decision.justif.typeDocument]} · {decision.justif.description}
              </p>
            </div>

            {decision.approuver ? (
              <p className="text-sm text-brand-textMuted dark:text-slate-400">
                L'approbation bascule le pointage concerné en « absence justifiée ».
              </p>
            ) : (
              <Input label="Motif du rejet *" value={motif} onChange={(e) => setMotif(e.target.value)} autoFocus />
            )}

            <div className="flex justify-end gap-2 border-t border-brand-border pt-4 dark:border-slate-700">
              <Button variant="outline" type="button" onClick={() => { setDecision(null); setMotif('') }}>
                Annuler
              </Button>
              <Button
                type="submit"
                variant={decision.approuver ? 'primary' : 'danger'}
                loading={traiter.isPending}
                disabled={!decision.approuver && !motif.trim()}
              >
                {decision.approuver ? 'Approuver' : 'Rejeter'}
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
