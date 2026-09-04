import { useState } from 'react'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { BookOpen, CalendarDays, ChevronLeft, ChevronRight, Lock, Pencil } from 'lucide-react'

import { PageHeader } from '@/components/ui/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { DataTable, type Column } from '@/components/ui/DataTable'
import {
  absenceApi,
  type AppelReponse,
  type CahierSeanceReponse,
  type EnregistrementCahierRequete,
} from '@/api/absence.api'
import { organisationApi, type SchoolClass } from '@/api/organisation.api'
import { formatDate } from '@/lib/utils'
import { CahierContenu } from '@/features/absence/CahierContenu'
import { CahierAssistantPanel } from './CahierAssistantPanel'
import { todayIso, useMonPlanning } from './useMonPlanning'

const PAGE_SIZE = 10

function classeNom(classes: SchoolClass[], id: number | undefined): string {
  if (!id) return '—'
  return classes.find((c) => c.idClasse === id)?.code ?? `Classe #${id}`
}

function heure(iso: string): string {
  return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
}

// ─── Formulaire de saisie ─────────────────────────────────────────────────────

function CahierFormModal({
  seanceAppelId,
  titre,
  cahier,
  onClose,
  onSaved,
}: {
  seanceAppelId: number
  titre: string
  cahier?: CahierSeanceReponse
  onClose: () => void
  onSaved: () => void
}) {
  const [form, setForm] = useState<EnregistrementCahierRequete>({
    sujet: cahier?.sujet ?? '',
    chapitre: cahier?.chapitre ?? '',
    activites: cahier?.activites ?? '',
    travailDemande: cahier?.travailDemande ?? '',
    dateEcheance: cahier?.dateEcheance ? cahier.dateEcheance.slice(0, 10) : '',
    remarques: cahier?.remarques ?? '',
  })

  const set = (key: keyof EnregistrementCahierRequete) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const saveMutation = useMutation({
    mutationFn: () =>
      absenceApi.cahier.enregistrer(seanceAppelId, {
        ...form,
        dateEcheance: form.dateEcheance || undefined,
      }),
    onSuccess: () => {
      toast.success('Cahier enregistré')
      onSaved()
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? "Erreur lors de l'enregistrement"),
  })

  const textarea =
    'w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200'

  return (
    <Modal open onClose={onClose} title={`Cahier de séance — ${titre}`} size="lg">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          saveMutation.mutate()
        }}
        className="space-y-4"
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input label="Sujet de la séance" value={form.sujet} onChange={set('sujet')} placeholder="Ex : Les fractions" />
          <Input label="Chapitre" value={form.chapitre} onChange={set('chapitre')} placeholder="Ex : Chapitre 3" />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Activités réalisées</label>
          <textarea rows={3} className={textarea} value={form.activites} onChange={set('activites')} />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Travail demandé</label>
          <textarea rows={2} className={textarea} value={form.travailDemande} onChange={set('travailDemande')} />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input label="Échéance du travail" type="date" value={form.dateEcheance} onChange={set('dateEcheance')} />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Remarques</label>
          <textarea rows={2} className={textarea} value={form.remarques} onChange={set('remarques')} />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" type="button" onClick={onClose}>Annuler</Button>
          <Button type="submit" loading={saveMutation.isPending}>Enregistrer</Button>
        </div>
      </form>
    </Modal>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

/** Cahier de classe de l'enseignant connecté : saisie par séance + historique. */
export default function MonCahier() {
  const qc = useQueryClient()
  const { me, meLoading, meError, meErrorMessage } = useMonPlanning()

  const [date, setDate] = useState(todayIso())
  const [page, setPage] = useState(0)
  const [edition, setEdition] = useState<{ seanceAppelId: number; titre: string; cahier?: CahierSeanceReponse } | null>(null)
  const [detail, setDetail] = useState<CahierSeanceReponse | null>(null)

  const { data: classes = [] } = useQuery({ queryKey: ['classes-all'], queryFn: organisationApi.classes.list })

  // Séances d'appel du jour → points d'entrée pour remplir le cahier.
  const { data: seances = [], isLoading: seancesLoading } = useQuery({
    queryKey: ['appel-sessions', { date, enseignantId: me?.idEnseignant }],
    queryFn: () => absenceApi.appel.lister({ date, enseignantId: me!.idEnseignant }),
    enabled: !!me,
  })

  const cahiersDuJour = useQueries({
    queries: seances.map((s) => ({
      queryKey: ['cahier', s.id],
      queryFn: () => absenceApi.cahier.recuperer(s.id),
      retry: false,
    })),
  })

  // Historique paginé de tous les cahiers de l'enseignant.
  const { data: historique } = useQuery({
    queryKey: ['cahiers-me', me?.idEnseignant, page],
    queryFn: () => absenceApi.cahier.lister({ enseignantId: me!.idEnseignant, page, size: PAGE_SIZE }),
    enabled: !!me,
  })
  const cahiers = historique?.content ?? []

  const seanceQueries = useQueries({
    queries: cahiers.map((c) => ({
      queryKey: ['appel', c.seanceAppelId],
      queryFn: () => absenceApi.appel.get(c.seanceAppelId),
      staleTime: 60_000,
    })),
  })
  const seanceParId = new Map<number, AppelReponse>()
  seanceQueries.forEach((q, i) => { if (q.data) seanceParId.set(cahiers[i].seanceAppelId, q.data) })

  if (meLoading) {
    return <div className="h-64 animate-pulse rounded-xl bg-brand-bgSecondary dark:bg-slate-800" />
  }

  if (meError || !me) {
    return (
      <div className="space-y-6">
        <PageHeader title="Cahier de classe" subtitle="Contenu de mes séances" />
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-6 text-sm text-yellow-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300">
          {meErrorMessage ??
            "Aucune fiche enseignant n'est liée à votre compte. Demandez à l'administration de créer votre fiche dans Gestion des enseignants."}
        </div>
      </div>
    )
  }

  const columns: Column<CahierSeanceReponse>[] = [
    {
      key: 'date',
      header: 'Date séance',
      render: (c) => {
        const s = seanceParId.get(c.seanceAppelId)
        return <span className="text-sm text-brand-textMuted">{s ? formatDate(s.ouvertureAt) : '…'}</span>
      },
    },
    {
      key: 'classe',
      header: 'Classe',
      render: (c) => <span className="text-sm">{classeNom(classes, seanceParId.get(c.seanceAppelId)?.groupeClasseId)}</span>,
    },
    { key: 'sujet', header: 'Sujet', render: (c) => <span className="text-sm">{c.sujet || '—'}</span> },
    { key: 'chapitre', header: 'Chapitre', render: (c) => <span className="text-sm text-brand-textMuted">{c.chapitre || '—'}</span> },
    {
      key: 'statut',
      header: 'Statut',
      render: (c) => (
        <Badge variant={c.estVerrouille ? 'default' : 'success'}>{c.estVerrouille ? 'Verrouillé' : 'Modifiable'}</Badge>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (c) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => setDetail(c)}>Voir</Button>
          {!c.estVerrouille && (
            <Button
              size="sm"
              variant="outline"
              onClick={() =>
                setEdition({
                  seanceAppelId: c.seanceAppelId,
                  titre: classeNom(classes, seanceParId.get(c.seanceAppelId)?.groupeClasseId),
                  cahier: c,
                })
              }
            >
              <Pencil size={13} /> Modifier
            </Button>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader title="Cahier de classe" subtitle="Contenu, activités et travail demandé pour mes séances" />

      {/* Séances d'une journée */}
      <div className="rounded-xl border border-brand-border bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h3 className="flex items-center gap-2 text-sm font-semibold text-brand-text dark:text-slate-100">
              <BookOpen size={16} className="text-brand-teal" /> Séances de la journée
            </h3>
            <p className="mt-0.5 text-xs text-brand-textMuted dark:text-slate-400">
              Le cahier se remplit sur une séance dont l'appel a été ouvert.
            </p>
          </div>
          <div className="flex items-end gap-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Jour</label>
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:outline-none focus:ring-2 focus:ring-brand-blue/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              />
            </div>
            <Button variant="outline" onClick={() => setDate(todayIso())}>
              <CalendarDays size={16} /> Aujourd'hui
            </Button>
          </div>
        </div>

        {seancesLoading ? (
          <div className="h-24 animate-pulse rounded-lg bg-brand-bgSecondary dark:bg-slate-800" />
        ) : seances.length === 0 ? (
          <p className="py-6 text-center text-sm text-brand-textMuted dark:text-slate-400">
            Aucune séance d'appel ce jour-là. Ouvrez l'appel depuis « Appel » ou votre tableau de bord.
          </p>
        ) : (
          <div className="space-y-2">
            {seances.map((s, i) => {
              const cahier = cahiersDuJour[i]?.data
              const titre = `${classeNom(classes, s.groupeClasseId)} · ${heure(s.ouvertureAt)}`
              return (
                <div
                  key={s.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand-border p-3 dark:border-slate-700"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-brand-text dark:text-slate-100">{titre}</p>
                    <p className="mt-0.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                      {cahier?.sujet ? cahier.sujet : 'Cahier non rempli'}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {cahier ? (
                      <Badge variant={cahier.estVerrouille ? 'default' : 'success'}>
                        {cahier.estVerrouille ? <><Lock size={11} className="me-1 inline" />Verrouillé</> : 'Rempli'}
                      </Badge>
                    ) : (
                      <Badge variant="warning">À remplir</Badge>
                    )}
                    <Button
                      size="sm"
                      variant={cahier ? 'outline' : 'primary'}
                      disabled={cahier?.estVerrouille}
                      onClick={() => setEdition({ seanceAppelId: s.id, titre, cahier })}
                    >
                      <Pencil size={13} /> {cahier ? 'Modifier' : 'Remplir'}
                    </Button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Recherche assistée.
          Placée entre la saisie du jour et l'historique : c'est exactement le
          moment où l'on se demande « où en étais-je avec cette classe ? ». La
          table d'historique répond quand on sait quelle séance chercher ; celle-ci
          répond quand on ne le sait pas. */}
      <CahierAssistantPanel />

      {/* Historique */}
      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-brand-text dark:text-slate-100">Historique de mes cahiers</h3>
        <DataTable
          columns={columns}
          data={cahiers}
          keyField="id"
          emptyMessage="Aucun cahier enregistré pour le moment."
        />
        {historique && historique.totalPages > 1 && (
          <div className="flex items-center justify-between">
            <p className="text-sm text-brand-textMuted dark:text-slate-400">
              Page {page + 1} / {historique.totalPages} — {historique.totalElements} cahier(s)
            </p>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft size={14} /> Précédent
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={page + 1 >= historique.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Suivant <ChevronRight size={14} />
              </Button>
            </div>
          </div>
        )}
      </div>

      {edition && (
        <CahierFormModal
          seanceAppelId={edition.seanceAppelId}
          titre={edition.titre}
          cahier={edition.cahier}
          onClose={() => setEdition(null)}
          onSaved={() => {
            setEdition(null)
            qc.invalidateQueries({ queryKey: ['cahier'] })
            qc.invalidateQueries({ queryKey: ['cahiers-me'] })
          }}
        />
      )}

      {detail && (
        <Modal open onClose={() => setDetail(null)} title="Cahier de séance" size="lg">
          <CahierContenu cahier={detail} />
        </Modal>
      )}
    </div>
  )
}
