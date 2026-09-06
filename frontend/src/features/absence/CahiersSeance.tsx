import { useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import {
  BookOpen, ChevronLeft, ChevronRight, Search, NotebookPen, Lock,
  CalendarClock, MousePointerClick,
} from 'lucide-react'

import { PageHero } from '@/components/ui/PageHero'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
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

  return (
    <div className="space-y-6">
      <PageHero
        title="Cahier de séance"
        subtitle="Ce que les enseignants ont consigné, séance après séance — en lecture seule"
        icon={NotebookPen}
      />

      <div className="flex flex-wrap items-end gap-4 rounded-xl border border-brand-border bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
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
          <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Du</label>
          <input
            type="date"
            value={debut}
            onChange={(e) => { setDebut(e.target.value); setPage(0) }}
            className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          />
        </div>
        <div className="w-44">
          <label className="mb-1 block text-sm font-medium text-brand-text dark:text-slate-200">Au</label>
          <input
            type="date"
            value={fin}
            onChange={(e) => { setFin(e.target.value); setPage(0) }}
            className="w-full rounded-lg border border-brand-border bg-white px-3 py-2 text-sm text-brand-text focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-blue dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          />
        </div>
      </div>

      {enseignantIdNum === null ? (
        <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
            <MousePointerClick size={22} />
          </span>
          <p className="text-sm font-medium text-brand-text dark:text-slate-200">Choisissez un enseignant</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
            Ses cahiers de séance s'afficheront ici, du plus récent au plus ancien.
          </p>
        </div>
      ) : (
        <>
          {isLoading && (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {[0, 1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="h-40 animate-pulse rounded-xl border border-brand-border bg-white dark:border-slate-700 dark:bg-slate-900" />
              ))}
            </div>
          )}

          {!isLoading && cahiers.length === 0 && (
            <div className="rounded-xl border border-dashed border-brand-border bg-white px-6 py-14 text-center dark:border-slate-700 dark:bg-slate-900">
              <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-50 text-brand-teal dark:bg-teal-500/10 dark:text-teal-400">
                <Search size={22} />
              </span>
              <p className="text-sm font-medium text-brand-text dark:text-slate-200">
                Aucun cahier de séance pour ces filtres
              </p>
              <p className="mx-auto mt-1 max-w-md text-sm text-brand-textMuted dark:text-slate-400">
                Un cahier vide est en soi une information : la séance a pu ne pas être renseignée.
              </p>
            </div>
          )}

          {/* Une carte par cahier : le sujet et le chapitre sont du texte libre,
              ils ne tenaient pas dans une cellule de tableau. */}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {cahiers.map((c) => {
              const seance = seanceParId.get(c.seanceAppelId)
              return (
                <div
                  key={c.id}
                  className="flex flex-col overflow-hidden rounded-xl border border-brand-border bg-white transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-900"
                >
                  <div className="flex flex-1 flex-col gap-2.5 p-4">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold text-brand-text dark:text-slate-100">
                          {c.sujet || 'Sans sujet'}
                        </h3>
                        <p className="mt-0.5 truncate text-xs text-brand-textMuted dark:text-slate-400">
                          {seance
                            ? `${classeNom(classes, seance.groupeClasseId)} · ${formatDate(seance.ouvertureAt)}`
                            : 'Chargement de la séance…'}
                        </p>
                      </div>
                      {c.estVerrouille
                        ? <Badge variant="default"><Lock size={10} className="me-0.5 inline" />Verrouillé</Badge>
                        : <Badge variant="success">Modifiable</Badge>}
                    </div>

                    {c.chapitre && (
                      <p className="line-clamp-2 text-xs text-brand-textMuted dark:text-slate-400">
                        Chapitre : {c.chapitre}
                      </p>
                    )}

                    {c.dateEcheance && (
                      <p className="mt-auto inline-flex items-center gap-1.5 text-xs text-brand-textMuted dark:text-slate-400">
                        <CalendarClock size={13} /> Travail à rendre le {formatDate(c.dateEcheance)}
                      </p>
                    )}
                  </div>

                  <div className="flex justify-end border-t border-brand-border bg-brand-bgSecondary/40 px-4 py-2.5 dark:border-slate-700 dark:bg-slate-800/40">
                    <Button size="sm" variant="outline" onClick={() => setDetail(c)}>Voir le détail</Button>
                  </div>
                </div>
              )
            })}
          </div>

          {pageResult && pageResult.totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-brand-textMuted dark:text-slate-400">
                Page {pageResult.number + 1} / {pageResult.totalPages} —{' '}
                {pageResult.totalElements} cahier{pageResult.totalElements > 1 ? 's' : ''}
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

      <Modal open={!!detail} onClose={() => setDetail(null)} title="Cahier de séance" size="xl">
        {detail && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-brand-border bg-brand-bgSecondary/50 p-3 dark:border-slate-700 dark:bg-slate-800/40">
              <BookOpen size={16} className="shrink-0 text-brand-teal" />
              <span className="text-sm text-brand-textMuted dark:text-slate-400">
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
            <div className="flex justify-end border-t border-brand-border pt-4 dark:border-slate-700">
              <Button variant="outline" onClick={() => setDetail(null)}>Fermer</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
