import { useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { BookOpen, ChevronLeft, ChevronRight, Search } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { absenceApi, type AppelReponse, type CahierSeanceReponse } from '@/api/absence.api'
import { organisationApi, type SchoolClass } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'
import { CahierContenu } from './CahierContenu'

const PAGE_SIZE = 10

function classeNom(classes: SchoolClass[], id: number | undefined): string {
  if (!id) return '—'
  const c = classes.find((x) => x.idClasse === id)
  return c ? c.code : `Classe #${id}`
}

export default function CahiersSeance() {
  const [enseignantId, setEnseignantId] = useState('')
  const [debut, setDebut] = useState('')
  const [fin, setFin] = useState('')
  const [page, setPage] = useState(0)
  const [detail, setDetail] = useState<CahierSeanceReponse | null>(null)

  const { data: teachers = [] } = useQuery({ queryKey: ['teachers-all'], queryFn: organisationApi.teachers.list })
  const { data: classes = [] } = useQuery({ queryKey: ['classes-all'], queryFn: organisationApi.classes.list })

  const enseignantIdNum = enseignantId ? Number(enseignantId) : null

  const { data: pageResult, isLoading } = useQuery({
    queryKey: ['cahiers', enseignantIdNum, debut, fin, page],
    queryFn: () => absenceApi.cahier.lister({
      enseignantId: enseignantIdNum!,
      debut: debut ? `${debut}T00:00:00` : undefined,
      fin: fin ? `${fin}T23:59:59` : undefined,
      page,
      size: PAGE_SIZE,
    }),
    enabled: enseignantIdNum !== null,
  })

  const cahiers = pageResult?.content ?? []

  // Enrichissement : date/classe de la séance liée à chaque cahier
  const seanceQueries = useQueries({
    queries: cahiers.map((c) => ({
      queryKey: ['appel', c.seanceAppelId],
      queryFn: () => absenceApi.appel.get(c.seanceAppelId),
      staleTime: 60_000,
    })),
  })
  const seanceParId = new Map<number, AppelReponse>()
  seanceQueries.forEach((q, i) => { if (q.data) seanceParId.set(cahiers[i].seanceAppelId, q.data) })

  const columns: Column<CahierSeanceReponse>[] = [
    {
      key: 'date', header: 'Date séance',
      render: (c) => {
        const s = seanceParId.get(c.seanceAppelId)
        return <span className="text-sm text-brand-textMuted">{s ? formatDate(s.ouvertureAt) : '…'}</span>
      },
    },
    {
      key: 'classe', header: 'Classe',
      render: (c) => <span className="text-sm">{classeNom(classes, seanceParId.get(c.seanceAppelId)?.groupeClasseId)}</span>,
    },
    { key: 'sujet', header: 'Sujet', render: (c) => <span className="text-sm">{c.sujet || '—'}</span> },
    { key: 'chapitre', header: 'Chapitre', render: (c) => <span className="text-sm text-brand-textMuted">{c.chapitre || '—'}</span> },
    {
      key: 'dateEcheance', header: 'Échéance',
      render: (c) => <span className="text-sm text-brand-textMuted">{c.dateEcheance ? formatDate(c.dateEcheance) : '—'}</span>,
    },
    {
      key: 'statut', header: 'Statut',
      render: (c) => <Badge variant={c.estVerrouille ? 'default' : 'success'}>{c.estVerrouille ? 'Verrouillé' : 'Modifiable'}</Badge>,
    },
    {
      key: 'actions', header: '', className: 'w-px',
      render: (c) => <Button size="sm" variant="outline" onClick={() => setDetail(c)}>Voir détail</Button>,
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Cahier de séance"
        subtitle="Contenu des cahiers de séance remplis par les enseignants — lecture seule"
      />

      <div className="bg-white rounded-xl border border-brand-border p-4 flex flex-wrap gap-4 items-end">
        <div className="w-64">
          <Select
            label="Enseignant *"
            placeholder="Sélectionner un enseignant"
            value={enseignantId}
            onChange={(e) => { setEnseignantId(e.target.value); setPage(0) }}
            options={teachers.map((t) => ({ value: t.idEnseignant, label: t.nomComplet }))}
          />
        </div>
        <div className="w-44">
          <label className="text-sm font-medium text-brand-text block mb-1">Du</label>
          <input
            type="date"
            value={debut}
            onChange={(e) => { setDebut(e.target.value); setPage(0) }}
            className="w-full border border-brand-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
          />
        </div>
        <div className="w-44">
          <label className="text-sm font-medium text-brand-text block mb-1">Au</label>
          <input
            type="date"
            value={fin}
            onChange={(e) => { setFin(e.target.value); setPage(0) }}
            className="w-full border border-brand-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-blue/30"
          />
        </div>
      </div>

      {enseignantIdNum === null ? (
        <div className="bg-white rounded-xl border border-brand-border p-12 text-center">
          <Search size={32} className="mx-auto text-brand-textMuted mb-3" />
          <p className="text-sm text-brand-textMuted">Sélectionnez un enseignant pour afficher ses cahiers de séance.</p>
        </div>
      ) : (
        <>
          <DataTable columns={columns} data={cahiers} keyField="id" loading={isLoading} emptyMessage="Aucun cahier de séance pour ces filtres" />

          {pageResult && pageResult.totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-brand-textMuted">
                Page {pageResult.number + 1} / {pageResult.totalPages} — {pageResult.totalElements} cahier(s)
              </p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  <ChevronLeft size={14} /> Précédent
                </Button>
                <Button variant="outline" size="sm" disabled={page + 1 >= pageResult.totalPages} onClick={() => setPage((p) => p + 1)}>
                  Suivant <ChevronRight size={14} />
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <Modal open={!!detail} onClose={() => setDetail(null)} title="Cahier de séance" size="lg">
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <BookOpen size={16} className="text-brand-blue" />
              <span className="text-sm text-brand-textMuted">
                Séance #{detail.seanceAppelId}
                {seanceParId.get(detail.seanceAppelId) && (
                  <> · {classeNom(classes, seanceParId.get(detail.seanceAppelId)?.groupeClasseId)} · {formatDate(seanceParId.get(detail.seanceAppelId)!.ouvertureAt)}</>
                )}
              </span>
              <Badge variant={detail.estVerrouille ? 'default' : 'success'} className="ms-auto">
                {detail.estVerrouille ? 'Verrouillé' : 'Modifiable'}
              </Badge>
            </div>
            <CahierContenu cahier={detail} />
            <div className="flex justify-end pt-2">
              <Button variant="outline" onClick={() => setDetail(null)}>Fermer</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
